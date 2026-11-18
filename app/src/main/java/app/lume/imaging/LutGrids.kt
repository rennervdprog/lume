package app.lume.imaging

import android.content.Context
import android.graphics.Bitmap
import app.lume.core.BuiltInLuts
import app.lume.data.UserLutStore

/**
 * Resolve um id de LUT para a grade (CPU, miniaturas) ou o bitmap
 * 512×512 (GPU, preview/exportação), com cache: gerar a grade de um
 * LUT próprio custa uma passada nos 262 mil pontos, e decodificar o
 * PNG de um importado custa IO — nenhum dos dois pode acontecer a
 * cada gesto de slider.
 *
 * Os bitmaps do cache NUNCA são reciclados aqui: o filtro da GPU
 * copia a textura e o cache é a única referência. Apagar um LUT
 * importado ([evict]) tira o bitmap do cache e o recicla.
 *
 * Chamadas vêm das threads Default/IO (ViewModel) — nunca da thread
 * GL, que só recebe o bitmap pronto. O acesso é sincronizado.
 */
object LutGrids {

    private const val MAX_CACHED_IMPORTED = 4

    private val gridCache = HashMap<String, LutGrid>()
    private val bitmapCache = HashMap<String, Bitmap>()
    private val importedOrder = ArrayDeque<String>()

    /** A grade (CPU) de um LUT próprio ou importado. Null se desconhecido. */
    @Synchronized
    fun gridFor(context: Context, lutId: String?): LutGrid? {
        if (lutId == null) return null
        gridCache[lutId]?.let { return it }
        val grid = BuiltInLuts.definition(lutId)?.let { definition ->
            LutGrid.fromTransform(definition.transform)
        } ?: UserLutStore.loadGrid(context, lutId)
        if (grid != null) {
            rememberImported(lutId)
            gridCache[lutId] = grid
        }
        return grid
    }

    /** O bitmap 512×512 (GPU) de um LUT. Null se desconhecido. */
    @Synchronized
    fun bitmapFor(context: Context, lutId: String?): Bitmap? {
        if (lutId == null) return null
        bitmapCache[lutId]?.let { return it }
        val grid = gridFor(context, lutId) ?: return null
        val bitmap = grid.toBitmap()
        rememberImported(lutId)
        bitmapCache[lutId] = bitmap
        return bitmap
    }

    /** Tira um LUT do cache (após apagar o importado). */
    @Synchronized
    fun evict(lutId: String) {
        gridCache.remove(lutId)
        bitmapCache.remove(lutId)?.recycle()
        importedOrder.remove(lutId)
    }

    private fun rememberImported(lutId: String) {
        // LUTs próprios ficam para sempre (são 6, 1 MB cada). Os
        // importados rodam num LRU pequeno: o usuário pode importar
        // muitos, e cada grade + bitmap custa 2 MB.
        if (BuiltInLuts.definition(lutId) != null) return
        importedOrder.remove(lutId)
        importedOrder.addLast(lutId)
        while (importedOrder.size > MAX_CACHED_IMPORTED) {
            val oldest = importedOrder.removeFirst()
            gridCache.remove(oldest)
            bitmapCache.remove(oldest)?.recycle()
        }
    }
}
