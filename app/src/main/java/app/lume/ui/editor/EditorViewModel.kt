package app.lume.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lume.core.CopiedEditStore
import app.lume.core.CropRatio
import app.lume.core.CubeParseResult
import app.lume.core.CubeParser
import app.lume.core.EditHistory
import app.lume.core.Geometry
import app.lume.core.Preset
import app.lume.data.PresetLimitReached
import app.lume.core.Recipe
import app.lume.core.TextOverlay
import app.lume.data.DraftRepository
import app.lume.data.DraftThumbnails
import app.lume.data.PresetRepository
import app.lume.data.UserLut
import app.lume.data.UserLutStore
import app.lume.data.pro.EntitlementRepository
import app.lume.imaging.BitmapLoader
import app.lume.imaging.GeometryTransform
import app.lume.imaging.GlEngine
import app.lume.imaging.Histogram
import app.lume.imaging.LutGrid
import app.lume.imaging.LutGrids
import app.lume.imaging.PhotoExporter
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val photoUri: Uri? = null,
    val preview: ImageBitmap? = null,
    val editedPreview: ImageBitmap? = null,
    val recipe: Recipe = Recipe.IDENTITY,
    val geometry: Geometry = Geometry.IDENTITY,
    val presets: List<Preset> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLoadingPhoto: Boolean = false,
    val isExporting: Boolean = false,
    val exportedUri: Uri? = null,
    val message: String? = null,
    val paywallRequested: Boolean = false,
    val appliedPresetName: String? = null,
    val presetIntensity: Float = 1f,
    val histogram: List<Float> = emptyList(),
    val isPro: Boolean = false,
    /** LUTs .cube importados pelo usuário (aba Meus LUTs). */
    val userLuts: List<UserLut> = emptyList(),
    /** A segunda foto da mistura foi escolhida (vive só na sessão). */
    val hasBlendPhoto: Boolean = false,
    /** Cores da foto (Fase 1): extraídas da foto atual, no aparelho. */
    val paletteColors: List<Int> = emptyList()
)

class EditorViewModel(
    private val appContext: Context,
    private val presetRepository: PresetRepository,
    private val draftRepository: DraftRepository,
    private val entitlementRepository: EntitlementRepository
) : ViewModel() {

    private val history = EditHistory()
    private var previewBitmap: Bitmap? = null
    private var renderJob: Job? = null
    private var presetBaseRecipe: Recipe? = null
    private var appliedPreset: Preset? = null

    /**
     * Geometria atual. Vive só na sessão: rascunhos guardam só a
     * receita (a persistência da geometria chega com a migração do
     * banco). A exportação aplica a geometria da sessão.
     */
    private var currentGeometry: Geometry = Geometry.IDENTITY

    /**
     * Segunda foto da mistura (dupla exposição). Vive só na sessão,
     * como a geometria: o rascunho guarda o modo e a opacidade, mas
     * não a foto. Decodificada uma vez, em tamanho de preview — a
     * textura é amostrada por UV, então o tamanho não precisa casar
     * com a foto base.
     */
    private var blendBitmap: Bitmap? = null

    /**
     * No modo de prévia do Cortar, o render aplica giro/espelho mas
     * NÃO o corte — a moldura sobre a foto cheia é que mostra o corte.
     * Fora desse modo, o render aplica a geometria completa.
     */
    private var cropPreviewMode: Boolean = false

    /** Receita + geometria de quando a ferramenta atual foi aberta (X/✓). */
    private var panelBaseline: Pair<Recipe, Geometry>? = null

    /**
     * Últimos 3 bitmaps renderizados. A tela pode estar mostrando o
     * atual ou o anterior (uma recomposição em voo pode ler o estado
     * antigo) — reciclar só o de três renders atrás é seguro. Reciclar
     * o anterior imediatamente correria o risco de desenhar um bitmap
     * reciclado.
     */
    private val renderedRing = ArrayDeque<Bitmap>()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            entitlementRepository.isPro.collect { isPro ->
                _uiState.update { it.copy(isPro = isPro) }
            }
        }
        viewModelScope.launch {
            presetRepository.presets.collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
        refreshUserLuts()
    }

    // ---- LUTs ----

    /** Aplica um LUT (ou tira, com null). Toque discreto: entra no histórico. */
    fun applyLut(lutId: String?) {
        clearAppliedPresetTracking()
        val current = history.current
        val intensity = if (lutId != null && lutId == current.lutId &&
            current.lutIntensity > 0f
        ) {
            current.lutIntensity
        } else {
            1f
        }
        val updated = current.copy(
            lutId = lutId,
            lutIntensity = if (lutId == null) 0f else intensity
        )
        history.commit(updated)
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = updated,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        refreshRender()
    }

    /** Arrasto do slider de intensidade do LUT: preview sem histórico. */
    fun updateLutIntensity(intensity: Float) {
        clearAppliedPresetTracking()
        val blended = history.current.copy(lutIntensity = intensity.coerceIn(0f, 1f))
        history.preview(blended)
        _uiState.update { it.copy(recipe = blended) }
        refreshRender()
    }

    /** Fim do gesto de intensidade do LUT: confirma no histórico. */
    fun commitLutIntensity() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
        refreshRender()
    }

    /**
     * Importa um arquivo .cube: lê, converte para a grade 64³, salva
     * em arquivos do app e aplica na hora. O motivo de qualquer
     * recusa (formato, tamanho, leitura) vira mensagem na tela.
     */
    fun importLut(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
            if (bytes == null || bytes.isEmpty() || bytes.size > MAX_CUBE_BYTES) {
                _uiState.update { it.copy(message = "Não consegui ler este arquivo.") }
                return@launch
            }
            when (val parsed = CubeParser.parse(String(bytes, Charsets.UTF_8))) {
                is CubeParseResult.Fail -> {
                    _uiState.update { it.copy(message = parsed.reason) }
                }
                is CubeParseResult.Ok -> {
                    val grid = LutGrid.fromCube(parsed.cube)
                    val id = sha256Hex(bytes)
                    val fileName = displayNameFor(uri) ?: "arquivo.cube"
                    val name = parsed.cube.title
                        ?: fileName.substringBeforeLast(".").ifBlank { "LUT importado" }
                    val saved = UserLutStore.save(
                        appContext, id, name, fileName, parsed.cube.size, grid
                    )
                    if (saved == null) {
                        _uiState.update {
                            it.copy(message = "Não consegui salvar este LUT.")
                        }
                    } else {
                        refreshUserLuts()
                        withContext(Dispatchers.Main) { applyLut(saved.id) }
                        _uiState.update {
                            it.copy(message = "LUT \"${saved.name}\" importado.")
                        }
                    }
                }
            }
        }
    }

    /** Apaga um LUT importado. Se ele estava aplicado, tira da foto. */
    fun deleteUserLut(lutId: String) {
        if (history.current.lutId == lutId) {
            applyLut(null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            UserLutStore.delete(appContext, lutId)
            LutGrids.evict(lutId)
            refreshUserLuts()
        }
        _uiState.update { it.copy(message = "LUT apagado.") }
    }

    private fun refreshUserLuts() {
        viewModelScope.launch(Dispatchers.IO) {
            val luts = UserLutStore.list(appContext)
            _uiState.update { it.copy(userLuts = luts) }
        }
    }

    private fun displayNameFor(uri: Uri): String? {
        return try {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        /** Um .cube 64³ tem ~800 KB; 8 MB é folga sem abrir a porta. */
        private const val MAX_CUBE_BYTES: Int = 8 * 1024 * 1024
    }

    // ---- Efeitos artísticos ----

    /** Aplica um efeito artístico (ou tira, com null). Toque discreto: entra no histórico. */
    fun applyArtistic(effectId: String?) {
        clearAppliedPresetTracking()
        val current = history.current
        val intensity = if (effectId != null && effectId == current.artisticEffect &&
            current.artisticIntensity > 0f
        ) {
            current.artisticIntensity
        } else {
            1f
        }
        val updated = current.copy(
            artisticEffect = effectId,
            artisticIntensity = if (effectId == null) 1f else intensity
        )
        history.commit(updated)
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = updated,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        refreshRender()
    }

    /** Arrasto do slider de intensidade do efeito: preview sem histórico. */
    fun updateArtisticIntensity(intensity: Float) {
        clearAppliedPresetTracking()
        val blended = history.current.copy(artisticIntensity = intensity.coerceIn(0f, 1f))
        history.preview(blended)
        _uiState.update { it.copy(recipe = blended) }
        refreshRender()
    }

    /** Fim do gesto de intensidade do efeito: confirma no histórico. */
    fun commitArtisticIntensity() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
        refreshRender()
    }

    // ---- Mistura (dupla exposição) ----

    /** Aplica um modo de mistura (ou tira, com null). Toque discreto: entra no histórico. */
    fun applyBlendMode(modeId: String?) {
        clearAppliedPresetTracking()
        val current = history.current
        val opacity = if (modeId != null && modeId == current.blendMode &&
            current.blendOpacity > 0f
        ) {
            current.blendOpacity
        } else {
            1f
        }
        val updated = current.copy(
            blendMode = modeId,
            blendOpacity = if (modeId == null) 0f else opacity
        )
        history.commit(updated)
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = updated,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        refreshRender()
    }

    /** Arrasto do slider de opacidade da mistura: preview sem histórico. */
    fun updateBlendOpacity(opacity: Float) {
        clearAppliedPresetTracking()
        val blended = history.current.copy(blendOpacity = opacity.coerceIn(0f, 1f))
        history.preview(blended)
        _uiState.update { it.copy(recipe = blended) }
        refreshRender()
    }

    /** Fim do gesto de opacidade da mistura: confirma no histórico. */
    fun commitBlendOpacity() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
        refreshRender()
    }

    /**
     * Escolhe a segunda foto da mistura: decodifica em tamanho de
     * preview e, se nenhum modo estava aplicado, aplica Multiplicar
     * na hora (igual ao LUT importado, que aplica ao importar).
     */
    fun pickBlendPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            val decoded = BitmapLoader.decodePreview(appContext, uri)
            if (decoded == null) {
                _uiState.update { it.copy(message = "Não foi possível abrir esta foto.") }
                return@launch
            }
            blendBitmap?.recycle()
            blendBitmap = decoded
            _uiState.update { it.copy(hasBlendPhoto = true) }
            if (history.current.blendMode == null) {
                withContext(Dispatchers.Main) { applyBlendMode("multiply") }
            } else {
                refreshRender()
            }
        }
    }

    /** Tira a segunda foto — e o modo junto, para o Histórico não mentir. */
    fun clearBlendPhoto() {
        if (history.current.blendMode != null) {
            applyBlendMode(null)
        }
        clearBlendBitmap()
        _uiState.update { it.copy(hasBlendPhoto = false) }
    }

    private fun clearBlendBitmap() {
        blendBitmap?.recycle()
        blendBitmap = null
    }

    fun openPhoto(uri: Uri) {
        val currentUri = _uiState.value.photoUri
        if (currentUri == uri && _uiState.value.preview != null) return
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Nem todo provedor concede permissão persistível; a edição da
            // sessão atual continua funcionando com a permissão temporária.
        }
        renderJob?.cancel()
        recyclePreview()
        history.reset()
        appliedPreset = null
        presetBaseRecipe = null
        currentGeometry = Geometry.IDENTITY
        cropPreviewMode = false
        panelBaseline = null
        _uiState.update {
            it.copy(
                photoUri = uri,
                preview = null,
                editedPreview = null,
                recipe = Recipe.IDENTITY,
                geometry = Geometry.IDENTITY,
                canUndo = false,
                canRedo = false,
                isLoadingPhoto = true,
                exportedUri = null,
                appliedPresetName = null,
                presetIntensity = 1f,
                hasBlendPhoto = false,
                paletteColors = emptyList()
            )
        }
        viewModelScope.launch {
            val draftRecipe = draftRepository.drafts.first()
                .firstOrNull { it.photoUri == uri.toString() }
                ?.recipe
            val bitmap = withContext(Dispatchers.Default) {
                BitmapLoader.decodePreview(appContext, uri)
            }
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        isLoadingPhoto = false,
                        message = "Não foi possível abrir esta foto."
                    )
                }
                return@launch
            }
            previewBitmap = bitmap
            val startRecipe = (draftRecipe ?: Recipe.IDENTITY).clamped()
            history.reset(startRecipe)
            _uiState.update {
                it.copy(
                    preview = bitmap.asImageBitmap(),
                    recipe = startRecipe,
                    isLoadingPhoto = false
                )
            }
            // Cores da foto (Fase 1): extração fora da thread de UI.
            // Se outra foto abrir no meio, o resultado velho é
            // descartado — nunca pode pintar a faixa com a foto errada.
            viewModelScope.launch(Dispatchers.Default) {
                val colors = try {
                    if (bitmap.isRecycled || previewBitmap !== bitmap) {
                        emptyList()
                    } else {
                        PhotoPalette.extract(bitmap)
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                if (previewBitmap === bitmap) {
                    _uiState.update { it.copy(paletteColors = colors) }
                }
            }
            refreshRender()
        }
    }

    /** Arrasto de slider: atualiza o preview sem entrar no histórico. */
    fun updateRecipeDraft(recipe: Recipe) {
        history.preview(recipe)
        clearAppliedPresetTracking()
        _uiState.update { it.copy(recipe = history.current) }
        refreshRender()
    }

    /** Fim do gesto: confirma a edição no histórico e salva o rascunho. */
    fun commitRecipe() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
        refreshRender()
    }

    fun undo() {
        val recipe = history.undo() ?: return
        clearAppliedPresetTracking()
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = recipe,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        refreshRender()
    }

    fun redo() {
        val recipe = history.redo() ?: return
        clearAppliedPresetTracking()
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = recipe,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        refreshRender()
    }

    fun resetAdjustments() {
        history.commit(Recipe.IDENTITY)
        clearAppliedPresetTracking()
        currentGeometry = Geometry.IDENTITY
        clearBlendBitmap()
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = Recipe.IDENTITY,
                geometry = Geometry.IDENTITY,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                appliedPresetName = null,
                presetIntensity = 1f,
                hasBlendPhoto = false
            )
        }
        refreshRender()
    }

    // ---- Geometria (sessão; sem histórico próprio) ----

    /** Atualiza a geometria e re-renderiza. Vale para arrasto e botão. */
    fun updateGeometry(geometry: Geometry) {
        currentGeometry = geometry.clamped()
        _uiState.update { it.copy(geometry = currentGeometry) }
        refreshRender()
    }

    /** Proporção pronta: corte centralizado de maior área sobre a foto girada. */
    fun setCropRatio(ratio: CropRatio) {
        val bitmap = previewBitmap ?: return
        val (width, height) = currentGeometry.rotatedSize(bitmap.width, bitmap.height)
        val crop = if (ratio == CropRatio.FREE) {
            null
        } else {
            ratio.centeredRect(width, height)
        }
        updateGeometry(currentGeometry.copy(crop = crop))
    }

    /** Gira em passos de 90° (positivo = horário). */
    fun rotateGeometry(degrees: Int) {
        updateGeometry(
            currentGeometry.copy(rotationDegrees = currentGeometry.rotationDegrees + degrees)
        )
    }

    /** Espelha na horizontal (espelho) ou na vertical (inverte). */
    fun flipGeometry(horizontal: Boolean) {
        updateGeometry(
            if (horizontal) {
                currentGeometry.copy(flipHorizontal = !currentGeometry.flipHorizontal)
            } else {
                currentGeometry.copy(flipVertical = !currentGeometry.flipVertical)
            }
        )
    }

    /** Endireitar, em graus (-10..10). */
    fun updateStraighten(degrees: Float) {
        updateGeometry(currentGeometry.copy(straightenDegrees = degrees))
    }

    /** Zera só a geometria (a receita de cor fica). */
    fun resetGeometry() {
        updateGeometry(Geometry.IDENTITY)
    }

    /**
     * Liga/desliga o modo de prévia do Cortar (render sem o corte, com
     * a moldura mostrando o corte sobre a foto cheia).
     */
    fun setCropPreviewMode(enabled: Boolean) {
        if (cropPreviewMode == enabled) return
        cropPreviewMode = enabled
        refreshRender()
    }

    // ---- Sessão de ferramenta (X/✓) ----

    /** Guarda a receita + geometria de quando a ferramenta foi aberta. */
    fun beginPanelSession() {
        panelBaseline = history.current to currentGeometry
    }

    /**
     * ✕ — volta à receita + geometria da abertura da ferramenta. A
     * volta da receita entra no desfazer (é reversível); a geometria
     * não tem histórico próprio.
     */
    fun discardPanelSession() {
        val baseline = panelBaseline ?: return
        val (baselineRecipe, baselineGeometry) = baseline
        if (baselineRecipe != history.current) {
            history.commit(baselineRecipe)
            clearAppliedPresetTracking()
            persistDraft()
        }
        currentGeometry = baselineGeometry
        _uiState.update {
            it.copy(
                recipe = history.current,
                geometry = baselineGeometry,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        refreshRender()
    }

    /** ✓ — confirma: a base da ferramenta passa a ser o estado atual. */
    fun confirmPanelSession() {
        panelBaseline = history.current to currentGeometry
    }

    fun applyPreset(preset: Preset) {
        presetBaseRecipe = history.current
        appliedPreset = preset
        val blended = history.current.interpolatedTo(preset.recipe, 1f)
        history.commit(blended)
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = blended,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                appliedPresetName = preset.name,
                presetIntensity = 1f
            )
        }
        refreshRender()
    }

    /** Volta à receita de antes do preset (cartão "Original" da tira). */
    fun clearPreset() {
        val base = presetBaseRecipe ?: return
        appliedPreset = null
        presetBaseRecipe = null
        history.commit(base)
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = base,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                appliedPresetName = null,
                presetIntensity = 1f
            )
        }
        refreshRender()
    }

    /** Copia a edição atual para colar em rascunhos na Biblioteca. */
    fun copyCurrentEdit() {
        if (_uiState.value.photoUri == null) return
        CopiedEditStore.save(history.current, _uiState.value.appliedPresetName)
        _uiState.update {
            it.copy(message = "Edição copiada. Cole em rascunhos na Biblioteca.")
        }
    }

    /** Intensidade do preset (0..1): interpola da base ao preset. */
    fun updatePresetIntensity(intensity: Float) {
        val preset = appliedPreset ?: return
        val base = presetBaseRecipe ?: Recipe.IDENTITY
        val blended = base.interpolatedTo(preset.recipe, intensity)
        history.preview(blended)
        _uiState.update { it.copy(recipe = blended, presetIntensity = intensity) }
        refreshRender()
    }

    fun commitPresetIntensity() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
        refreshRender()
    }

    fun savePreset(name: String, collection: String) {
        viewModelScope.launch {
            val result = presetRepository.saveUserPreset(
                name = name,
                collection = collection,
                recipe = history.current
            )
            result.fold(
                onSuccess = { preset ->
                    _uiState.update {
                        it.copy(message = "Preset \"${preset.name}\" salvo.")
                    }
                },
                onFailure = { error ->
                    if (error is PresetLimitReached) {
                        _uiState.update {
                            it.copy(
                                message = "Limite de ${error.limit} presets no plano grátis.",
                                paywallRequested = true
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(message = error.message ?: "Não foi possível salvar o preset.")
                        }
                    }
                }
            )
        }
    }

    fun exportPhoto(textOverlay: TextOverlay? = null) {
        val uri = _uiState.value.photoUri ?: return
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true, exportedUri = null) }
        val recipe = history.current
        val geometry = currentGeometry
        viewModelScope.launch {
            val lutGrid = lutGridFor(recipe)
            val blend = blendBitmapFor(recipe)
            val result = PhotoExporter.exportJpeg(
                appContext, uri, recipe, geometry, lutGrid, blend,
                textOverlay = textOverlay
            )
            result.fold(
                onSuccess = { savedUri ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportedUri = savedUri,
                            message = "Foto exportada para a galeria."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            message = error.message ?: "Não foi possível exportar a foto."
                        )
                    }
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumePaywallRequest() {
        _uiState.update { it.copy(paywallRequested = false) }
    }

    /** Recurso Pro tocado no plano grátis: abre o paywall. */
    fun requestPaywall() {
        _uiState.update { it.copy(paywallRequested = true) }
    }

    fun clearExportedUri() {
        _uiState.update { it.copy(exportedUri = null) }
    }

    private fun clearAppliedPresetTracking() {
        // Ajuste manual após preset: a intensidade perde o sentido, porque
        // a receita já não é mais "base + preset". O nome some do painel.
        if (appliedPreset != null) {
            appliedPreset = null
            presetBaseRecipe = null
            _uiState.update { it.copy(appliedPresetName = null, presetIntensity = 1f) }
        }
    }

    private fun persistDraft() {
        val uri = _uiState.value.photoUri ?: return
        val recipe = history.current
        val bitmap = previewBitmap
        viewModelScope.launch {
            try {
                draftRepository.saveDraft(uri.toString(), recipe)
            } catch (e: Exception) {
                // Rascunho é conveniência: falhar aqui nunca pode quebrar
                // a edição. A exportação não depende do rascunho.
            }
            // A miniatura em arquivo é o que faz o cartão do rascunho
            // mostrar a foto sempre: a permissão do seletor expira e
            // a URI deixa de abrir horas depois.
            if (bitmap != null) {
                withContext(Dispatchers.IO) {
                    DraftThumbnails.save(appContext, uri.toString(), bitmap)
                }
            }
        }
    }

    /**
     * Renderiza o preview editado com o motor e recalcula o histograma
     * sobre o bitmap renderizado. O render anterior é cancelado:
     * durante o arrasto de slider, só o último vale. Receita identidade
     * + geometria identidade não renderiza — mostra o original direto,
     * pixel a pixel. No modo de prévia do Cortar, o corte fica fora do
     * render (a moldura mostra o corte sobre a foto cheia).
     */
    private fun refreshRender() {
        val bitmap = previewBitmap ?: return
        val recipe = history.current
        val effectiveGeometry = if (cropPreviewMode) {
            currentGeometry.withoutCrop()
        } else {
            currentGeometry
        }
        renderJob?.cancel()
        if (recipe.isIdentity() && effectiveGeometry.isIdentity()) {
            _uiState.update { it.copy(editedPreview = bitmap.asImageBitmap()) }
            renderJob = viewModelScope.launch(Dispatchers.Default) {
                val values = Histogram.compute(bitmap)
                _uiState.update { it.copy(histogram = values) }
            }
            return
        }
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                if (bitmap.isRecycled) return@launch
                // Receita identidade: a geometria roda sobre o original.
                // Se não, o motor de cor renderiza primeiro.
                val base = if (recipe.isIdentity()) {
                    bitmap
                } else {
                    PhotoExporter.renderWithEngine(
                        bitmap, recipe, lutGridFor(recipe), blendBitmapFor(recipe)
                    )
                }
                val final = GeometryTransform.apply(base, effectiveGeometry)
                if (final !== base && base !== bitmap) {
                    // Intermediário do motor: o final já o substitui.
                    base.recycle()
                }
                val values = Histogram.compute(final)
                renderedRing.addLast(final)
                while (renderedRing.size > 3) {
                    renderedRing.removeFirst().recycle()
                }
                _uiState.update {
                    it.copy(editedPreview = final.asImageBitmap(), histogram = values)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Foto trocada (bitmap reciclado) no meio do render: ignora
                // — o próximo refresh renderiza a foto atual. Nunca pode
                // estourar para o usuário.
            }
        }
    }

    /**
     * A grade do LUT da receita, pronta para a GPU — ou null (sem
     * LUT, intensidade 0, ou LUT importado que foi apagado). Resolve
     * fora da thread GL, com cache ([LutGrids]).
     */
    private fun lutGridFor(recipe: Recipe): Bitmap? {
        if (recipe.lutId == null || recipe.lutIntensity <= 0f) return null
        return LutGrids.bitmapFor(appContext, recipe.lutId)
    }

    /**
     * A segunda foto da mistura, pronta para a GPU — ou null (sem
     * modo, opacidade 0, ou sem foto escolhida). É a MESMA instância
     * entre renders: o motor só reenvia a textura quando ela muda.
     */
    private fun blendBitmapFor(recipe: Recipe): Bitmap? {
        if (recipe.blendMode == null || recipe.blendOpacity <= 0f) return null
        val bitmap = blendBitmap ?: return null
        if (bitmap.isRecycled) return null
        return bitmap
    }

    private fun recyclePreview() {
        previewBitmap?.recycle()
        previewBitmap = null
        clearBlendBitmap()
        while (renderedRing.isNotEmpty()) {
            renderedRing.removeFirst().recycle()
        }
    }

    override fun onCleared() {
        renderJob?.cancel()
        recyclePreview()
        GlEngine.release()
        super.onCleared()
    }
}
