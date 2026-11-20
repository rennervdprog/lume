package app.lume

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lume.core.BuiltInPresets
import app.lume.core.Geometry
import app.lume.core.Recipe
import app.lume.ui.editor.AdjustPanel
import app.lume.ui.editor.BlendPanel
import app.lume.ui.editor.CropPanel
import app.lume.ui.editor.CropTab
import app.lume.ui.editor.FilterPanel
import app.lume.ui.editor.HistoryPanel
import app.lume.ui.theme.LumeTheme
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de UI instrumentados dos painéis do Editor, com estado
 * controlado pelo teste (sem banco, sem foto): provam que cada
 * painel renderiza, que a sub-ferramenta troca o controle exibido e
 * que o slider fino responde ao arrasto de verdade.
 *
 * Roda no CI via `:app:connectedDebugAndroidTest` (job "UI tests"),
 * junto com [LumeAppUiTest].
 */
@RunWith(AndroidJUnit4::class)
class LumePanelsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun painelAjustar_trocaSubFerramenta() {
        var selectedSub by mutableStateOf("exposure")
        composeRule.setContent {
            LumeTheme {
                AdjustPanel(
                    recipe = Recipe(exposure = 0.5f, contrast = 10f),
                    histogram = emptyList(),
                    isPro = false,
                    selectedSub = selectedSub,
                    onSubSelect = { selectedSub = it },
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = "hue",
                    onHslAttrSelect = {},
                    onDraft = {},
                    onCommit = {},
                    onRequestPaywall = {},
                    onCompareChange = {}
                )
            }
        }
        composeRule.onNodeWithTag("sub_exposure").assertIsDisplayed()
        composeRule.onNodeWithTag("slim_slider").assertIsDisplayed()
        // A bolha mostra o valor do controle selecionado.
        composeRule.onNodeWithText("+0.5").assertIsDisplayed()
        // A tira rola: o último item pode nascer fora da viewport e a
        // LazyRow só o compõe depois do scroll (falha da run #12).
        var tries = 0
        while (composeRule.onAllNodesWithTag("sub_contrast")
            .fetchSemanticsNodes().isEmpty() && tries < 4
        ) {
            composeRule.onNodeWithTag("sub_strip")
                .performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            tries++
        }
        composeRule.onNodeWithTag("sub_contrast").performClick()
        composeRule.onNodeWithText("+10").assertIsDisplayed()
        uitestShot("10_ajustar_troca")
    }

    @Test
    fun painelAjustar_mostraCurva() {
        composeRule.setContent {
            LumeTheme {
                AdjustPanel(
                    recipe = Recipe(),
                    histogram = emptyList(),
                    isPro = false,
                    selectedSub = "curve",
                    onSubSelect = {},
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = "hue",
                    onHslAttrSelect = {},
                    onDraft = {},
                    onCommit = {},
                    onRequestPaywall = {},
                    onCompareChange = {}
                )
            }
        }
        composeRule.onNodeWithText("Linear").assertIsDisplayed()
        // "Contraste" é chip da curva e rótulo do trilho: a tira rola
        // até a Curva sozinha, então a contagem depende do scroll —
        // basta existir.
        Assert.assertTrue(
            "chip Contraste da curva não apareceu",
            composeRule.onAllNodesWithText("Contraste")
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithText("Fade").assertIsDisplayed()
        composeRule.onNodeWithText("Suave").assertIsDisplayed()
        uitestShot("11_ajustar_curva")
    }

    @Test
    fun sliderFino_arrastarMudaOValorEConfirma() {
        val recorded = mutableListOf<Float>()
        var finished = 0
        composeRule.setContent {
            LumeTheme {
                AdjustPanel(
                    recipe = Recipe(),
                    histogram = emptyList(),
                    isPro = false,
                    selectedSub = "exposure",
                    onSubSelect = {},
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = "hue",
                    onHslAttrSelect = {},
                    onDraft = { recorded.add(it.exposure) },
                    onCommit = { finished++ },
                    onRequestPaywall = {},
                    onCompareChange = {}
                )
            }
        }
        // Arrasto da esquerda para a direita sobre o slider fino.
        composeRule.onNodeWithTag("slim_slider")
            .performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        Assert.assertTrue("arrasto não gerou valores", recorded.isNotEmpty())
        Assert.assertTrue(
            "arrasto p/ direita não subiu o valor: $recorded",
            recorded.any { it > 1f }
        )
        Assert.assertEquals("arrasto não confirmou ao soltar", 1, finished)
        uitestShot("12_slider_arrasto")
    }

    @Test
    fun painelAjustar_hslMostraPontosAtributoESelo() {
        var hslAttr by mutableStateOf("hue")
        var paywallRequested = false
        composeRule.setContent {
            LumeTheme {
                AdjustPanel(
                    recipe = Recipe(),
                    histogram = emptyList(),
                    isPro = false,
                    selectedSub = "hsl",
                    onSubSelect = {},
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = hslAttr,
                    onHslAttrSelect = { hslAttr = it },
                    onDraft = {},
                    onCommit = {},
                    onRequestPaywall = { paywallRequested = true },
                    onCompareChange = {}
                )
            }
        }
        // Legenda do slider = canal + atributo.
        composeRule.onNodeWithText("Vermelho · Matiz").assertIsDisplayed()
        // Grátis libera os 4 primeiros canais; os outros 4 têm cadeado.
        composeRule.onAllNodesWithContentDescription("Canal Pro")
            .assertCountEquals(4)
        // Tocar num canal travado pede o paywall.
        composeRule.onAllNodesWithContentDescription("Canal Pro")[0]
            .performClick()
        Assert.assertTrue(paywallRequested)
        // O chip de atributo troca o slider. O chip vem antes do
        // trilho na árvore, então o primeiro "Saturação" é o chip
        // (o rótulo do trilho nasce fora da viewport).
        composeRule.onAllNodesWithText("Saturação").onFirst().performClick()
        composeRule.onNodeWithText("Vermelho · Saturação").assertIsDisplayed()
        // Sem seletor (0.14.1): o HSL leva o selo ★ no trilho porque
        // 4 canais dentro são Pro — mas o toque ABRE a seção, não o
        // paywall (os 4 canais grátis funcionam). Se o selo travasse
        // como os Pro de verdade, este toque pediria o paywall.
        // (O toque no canal travado acima já pediu o paywall uma
        // vez — zera o contador para isolar o toque no trilho.)
        paywallRequested = false
        composeRule.onNodeWithTag("sub_strip")
            .performScrollToNode(hasTestTag("sub_hsl"))
        composeRule.onNodeWithTag("sub_hsl").performClick()
        Assert.assertFalse(paywallRequested)
        composeRule.onNodeWithText("Vermelho · Saturação").assertIsDisplayed()
        uitestShot("13_ajustar_hsl")
    }

    @Test
    fun painelAjustar_efeitosTrocaSubFerramenta() {
        var selectedSub by mutableStateOf("clarity")
        composeRule.setContent {
            LumeTheme {
                AdjustPanel(
                    recipe = Recipe(clarity = 10f, vignette = 20f),
                    histogram = emptyList(),
                    isPro = false,
                    selectedSub = selectedSub,
                    onSubSelect = { selectedSub = it },
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = "hue",
                    onHslAttrSelect = {},
                    onDraft = {},
                    onCommit = {},
                    onRequestPaywall = {},
                    onCompareChange = {}
                )
            }
        }
        composeRule.onNodeWithText("+10").assertIsDisplayed()
        var tries = 0
        while (composeRule.onAllNodesWithTag("sub_vignette")
            .fetchSemanticsNodes().isEmpty() && tries < 4
        ) {
            composeRule.onNodeWithTag("sub_strip")
                .performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            tries++
        }
        composeRule.onNodeWithTag("sub_vignette").performClick()
        composeRule.onNodeWithText("+20").assertIsDisplayed()
        uitestShot("14_ajustar_efeitos")
    }

    @Test
    fun painelFiltro_mostraMiniaturasEIntensidade() {
        var appliedName: String? by mutableStateOf(null)
        composeRule.setContent {
            LumeTheme {
                FilterPanel(
                    presets = BuiltInPresets.all,
                    preview = null,
                    appliedPresetName = appliedName,
                    presetIntensity = 1f,
                    onApplyPreset = { appliedName = it.name },
                    onClearPreset = { appliedName = null },
                    onIntensityChange = {},
                    onIntensityFinished = {},
                    onSavePresetClick = {},
                    onSharePresetClick = {},
                    onShuffleClick = {},
                    onCompareChange = {}
                )
            }
        }
        composeRule.onNodeWithText("Original").assertIsDisplayed()
        composeRule.onNodeWithText("Luz da Manhã").assertIsDisplayed()
        composeRule.onNodeWithText("Embaralhar").assertIsDisplayed()
        composeRule.onNodeWithText("Todos").assertIsDisplayed()
        // Aplicar um preset mostra o slider de intensidade.
        composeRule.onNodeWithText("Luz da Manhã").performClick()
        composeRule.onNodeWithText("Intensidade").assertIsDisplayed()
        uitestShot("15_filtro")
    }

    @Test
    fun painelFiltro_mostraAbasLutEIntensidade() {
        var selectedLut: String? by mutableStateOf(null)
        composeRule.setContent {
            LumeTheme {
                FilterPanel(
                    presets = BuiltInPresets.all,
                    preview = null,
                    appliedPresetName = null,
                    presetIntensity = 1f,
                    onApplyPreset = {},
                    onClearPreset = {},
                    onIntensityChange = {},
                    onIntensityFinished = {},
                    onSavePresetClick = {},
                    onSharePresetClick = {},
                    onShuffleClick = {},
                    onCompareChange = {},
                    selectedLutId = selectedLut,
                    lutIntensity = 0.8f,
                    onApplyLut = { selectedLut = it }
                )
            }
        }
        composeRule.onNodeWithText("LUTs").assertIsDisplayed()
        composeRule.onNodeWithText("Meus LUTs").assertIsDisplayed()
        composeRule.onNodeWithText("LUTs").performClick()
        composeRule.onNodeWithText("Sem LUT").assertIsDisplayed()
        composeRule.onNodeWithText("Importar").assertIsDisplayed()
        // O primeiro LUT está visível na tira sem rolar.
        composeRule.onNodeWithText("Laranja & Verde-azulado").assertIsDisplayed()
        // Aplicar um LUT mostra o slider de intensidade do LUT.
        composeRule.onNodeWithText("Laranja & Verde-azulado").performClick()
        composeRule.onNodeWithText("Intensidade do LUT").assertIsDisplayed()
        uitestShot("17_filtro_luts")
    }

    @Test
    fun painelHistorico_mostraPassosERemoveUm() {
        val cleared = mutableListOf<Recipe>()
        composeRule.setContent {
            LumeTheme {
                HistoryPanel(
                    recipe = Recipe(exposure = 0.5f, contrast = 10f),
                    geometry = Geometry.IDENTITY,
                    photoWidth = 1000,
                    photoHeight = 2000,
                    appliedPresetName = null,
                    presetIntensity = 1f,
                    onClearField = { cleared.add(it) },
                    onClearPreset = {},
                    onClearGeometry = {},
                    onCopyEdit = {},
                    onReset = {}
                )
            }
        }
        composeRule.onNodeWithText("Histórico").assertIsDisplayed()
        composeRule.onNodeWithText("Exposição").assertIsDisplayed()
        composeRule.onNodeWithText("Contraste").assertIsDisplayed()
        composeRule.onNodeWithText("Copiar edição").assertIsDisplayed()
        composeRule.onNodeWithText("Limpar tudo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remover Exposição")
            .performClick()
        Assert.assertEquals(1, cleared.size)
        Assert.assertEquals(0f, cleared.single().exposure, 0f)
        Assert.assertEquals(10f, cleared.single().contrast, 0f)
        uitestShot("16_historico")
    }

    @Test
    fun painelAjustar_niveisESeloAbrePaywall() {
        var selectedSub by mutableStateOf("levels")
        var paywallRequests = 0
        composeRule.setContent {
            LumeTheme {
                AdjustPanel(
                    recipe = Recipe(levelsBlack = 20f),
                    histogram = emptyList(),
                    isPro = false,
                    selectedSub = selectedSub,
                    onSubSelect = { selectedSub = it },
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = "hue",
                    onHslAttrSelect = {},
                    onDraft = {},
                    onCommit = {},
                    onRequestPaywall = { paywallRequests++ },
                    onCompareChange = {}
                )
            }
        }
        // A seção Níveis: os três sliders com legenda.
        composeRule.onNodeWithText("Ponto preto").assertIsDisplayed()
        composeRule.onNodeWithText("Meio-tom").assertIsDisplayed()
        composeRule.onNodeWithText("Ponto branco").assertIsDisplayed()
        // Desfoque é Pro no plano grátis: o toque abre o paywall
        // em vez de selecionar.
        // Rolagem determinística até o item (o gesto de arrastar
        // andava pouco por vez e nem sempre alcançava o fim da fila).
        composeRule.onNodeWithTag("sub_strip")
            .performScrollToNode(hasTestTag("sub_blur"))
        composeRule.onNodeWithTag("sub_blur").performClick()
        Assert.assertEquals(1, paywallRequests)
        // RGB é grátis: o toque seleciona de verdade, sem paywall.
        composeRule.onNodeWithTag("sub_strip")
            .performScrollToNode(hasTestTag("sub_rgb"))
        composeRule.onNodeWithTag("sub_rgb").performClick()
        Assert.assertEquals(1, paywallRequests)
        composeRule.onNodeWithText("Vermelho").assertIsDisplayed()
        uitestShot("18_ajustar_niveis")
    }

    @Test
    fun painelFiltro_mostraAbaEfeitos() {
        var selectedEffect: String? by mutableStateOf(null)
        composeRule.setContent {
            LumeTheme {
                FilterPanel(
                    presets = BuiltInPresets.all,
                    preview = null,
                    appliedPresetName = null,
                    presetIntensity = 1f,
                    onApplyPreset = {},
                    onClearPreset = {},
                    onIntensityChange = {},
                    onIntensityFinished = {},
                    onSavePresetClick = {},
                    onSharePresetClick = {},
                    onShuffleClick = {},
                    onCompareChange = {},
                    isPro = true,
                    selectedArtisticId = selectedEffect,
                    artisticIntensity = 0.8f,
                    onApplyArtistic = { selectedEffect = it }
                )
            }
        }
        composeRule.onNodeWithText("Efeitos").assertIsDisplayed()
        composeRule.onNodeWithText("Efeitos").performClick()
        composeRule.onNodeWithText("Sem efeito").assertIsDisplayed()
        composeRule.onNodeWithText("Toon").assertIsDisplayed()
        // Aplicar um efeito mostra o slider de intensidade do efeito.
        composeRule.onNodeWithText("Toon").performClick()
        composeRule.onNodeWithText("Intensidade do efeito").assertIsDisplayed()
        uitestShot("19_filtro_efeitos")
    }

    @Test
    fun painelFiltro_efeitoTravadoAbrePaywall() {
        var paywallRequested = false
        composeRule.setContent {
            LumeTheme {
                FilterPanel(
                    presets = BuiltInPresets.all,
                    preview = null,
                    appliedPresetName = null,
                    presetIntensity = 1f,
                    onApplyPreset = {},
                    onClearPreset = {},
                    onIntensityChange = {},
                    onIntensityFinished = {},
                    onSavePresetClick = {},
                    onSharePresetClick = {},
                    onShuffleClick = {},
                    onCompareChange = {},
                    isPro = false,
                    selectedArtisticId = null,
                    onApplyArtistic = {},
                    onRequestPaywall = { paywallRequested = true }
                )
            }
        }
        composeRule.onNodeWithText("Efeitos").performClick()
        composeRule.onNodeWithText("Toon").performClick()
        Assert.assertTrue(paywallRequested)
        // Nada foi aplicado: o slider de intensidade não aparece.
        Assert.assertTrue(
            composeRule.onAllNodesWithText("Intensidade do efeito")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun painelMistura_mostraModosEOpacidade() {
        composeRule.setContent {
            LumeTheme {
                BlendPanel(
                    recipe = Recipe(blendMode = "multiply", blendOpacity = 0.8f),
                    isPro = true,
                    hasBlendPhoto = true,
                    onPickBlendPhoto = {},
                    onClearBlendPhoto = {},
                    onApplyBlendMode = {},
                    onBlendOpacityChange = {},
                    onBlendOpacityFinished = {},
                    onRequestPaywall = {},
                    onCompareChange = {}
                )
            }
        }
        composeRule.onNodeWithText("Mistura").assertIsDisplayed()
        composeRule.onNodeWithText("Trocar segunda foto").assertIsDisplayed()
        composeRule.onNodeWithText("Multiplicar").assertIsDisplayed()
        composeRule.onNodeWithText("Opacidade").assertIsDisplayed()
        uitestShot("20_mistura")
    }

    @Test
    fun painelMistura_travadoAbrePaywall() {
        var paywallRequested = false
        composeRule.setContent {
            LumeTheme {
                BlendPanel(
                    recipe = Recipe(),
                    isPro = false,
                    hasBlendPhoto = false,
                    onPickBlendPhoto = {},
                    onClearBlendPhoto = {},
                    onApplyBlendMode = {},
                    onBlendOpacityChange = {},
                    onBlendOpacityFinished = {},
                    onRequestPaywall = { paywallRequested = true },
                    onCompareChange = {}
                )
            }
        }
        composeRule.onNodeWithText("Escolher segunda foto").performClick()
        Assert.assertTrue(paywallRequested)
    }

    @Test
    fun painelCortar_mostraAbasEGirar() {
        var cropTab by mutableStateOf(CropTab.CROP)
        composeRule.setContent {
            LumeTheme {
                CropPanel(
                    geometry = Geometry.IDENTITY,
                    photoWidth = 1000,
                    photoHeight = 2000,
                    cropTab = cropTab,
                    onCropTabChange = { cropTab = it },
                    onGeometryChange = {},
                    onRatioSelect = {},
                    onRotate = {},
                    onFlip = {},
                    onCompareChange = {}
                )
            }
        }
        // "Cortar" aparece 2x: no título do painel e na aba.
        composeRule.onAllNodesWithText("Cortar").assertCountEquals(2)
        composeRule.onNodeWithText("Corrigir").assertIsDisplayed()
        composeRule.onNodeWithText("Girar").assertIsDisplayed()
        composeRule.onNodeWithText("1:1").assertIsDisplayed()
        composeRule.onNodeWithText("Girar").performClick()
        composeRule.onNodeWithText("Direita 90°").assertIsDisplayed()
        composeRule.onNodeWithText("Corrigir").performClick()
        composeRule.onNodeWithText("Endireitar").assertIsDisplayed()
        uitestShot("17_cortar")
    }
}
