package app.lume

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lume.core.BuiltInPresets
import app.lume.core.Geometry
import app.lume.core.Recipe
import app.lume.ui.editor.ColorPanel
import app.lume.ui.editor.CropPanel
import app.lume.ui.editor.CropTab
import app.lume.ui.editor.EffectsPanel
import app.lume.ui.editor.HistoryPanel
import app.lume.ui.editor.LightPanel
import app.lume.ui.editor.PresetsPanel
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
    fun painelLuz_simplesTrocaSubFerramenta() {
        var selectedSub by mutableStateOf("exposure")
        composeRule.setContent {
            LumeTheme {
                LightPanel(
                    recipe = Recipe(),
                    histogram = emptyList(),
                    simpleMode = true,
                    onSimpleModeChange = {},
                    selectedSub = selectedSub,
                    onSubSelect = { selectedSub = it },
                    onDraft = {},
                    onCommit = {}
                )
            }
        }
        composeRule.onNodeWithTag("sub_exposure").assertIsDisplayed()
        composeRule.onNodeWithTag("slim_slider").assertIsDisplayed()
        // O rótulo aparece 2x: na tira de sub-ferramentas e no slider.
        composeRule.onAllNodesWithText("Luz").assertCountEquals(2)
        composeRule.onNodeWithTag("sub_contrast").performClick()
        composeRule.onAllNodesWithText("Clima").assertCountEquals(2)
        uitestShot("10_luz_simples")
    }

    @Test
    fun painelLuz_proMostraCurva() {
        composeRule.setContent {
            LumeTheme {
                LightPanel(
                    recipe = Recipe(),
                    histogram = emptyList(),
                    simpleMode = false,
                    onSimpleModeChange = {},
                    selectedSub = "curve",
                    onSubSelect = {},
                    onDraft = {},
                    onCommit = {}
                )
            }
        }
        composeRule.onNodeWithText("Linear").assertIsDisplayed()
        composeRule.onNodeWithText("Contraste").assertIsDisplayed()
        composeRule.onNodeWithText("Fade").assertIsDisplayed()
        composeRule.onNodeWithText("Suave").assertIsDisplayed()
        uitestShot("11_luz_curva")
    }

    @Test
    fun sliderFino_arrastarMudaOValorEConfirma() {
        val recorded = mutableListOf<Float>()
        var finished = 0
        composeRule.setContent {
            LumeTheme {
                LightPanel(
                    recipe = Recipe(),
                    histogram = emptyList(),
                    simpleMode = true,
                    onSimpleModeChange = {},
                    selectedSub = "exposure",
                    onSubSelect = {},
                    onDraft = { recorded.add(it.exposure) },
                    onCommit = { finished++ }
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
    fun painelCor_hslMostraPontosAtributoECadeado() {
        var hslAttr by mutableStateOf("hue")
        var paywallRequested = false
        composeRule.setContent {
            LumeTheme {
                ColorPanel(
                    recipe = Recipe(),
                    isPro = false,
                    selectedSub = "hsl",
                    onSubSelect = {},
                    hslChannel = 0,
                    onHslChannelSelect = {},
                    hslAttr = hslAttr,
                    onHslAttrSelect = { hslAttr = it },
                    onDraft = {},
                    onCommit = {},
                    onRequestPaywall = { paywallRequested = true }
                )
            }
        }
        // Rótulo do slider = canal + atributo.
        composeRule.onNodeWithText("Vermelho · Matiz").assertIsDisplayed()
        // Grátis libera os 4 primeiros canais; os outros 4 têm cadeado.
        composeRule.onAllNodesWithContentDescription("Canal Pro")
            .assertCountEquals(4)
        // Tocar num canal travado pede o paywall.
        composeRule.onAllNodesWithContentDescription("Canal Pro")[0]
            .performClick()
        Assert.assertTrue(paywallRequested)
        // O chip de atributo troca o slider. O chip vem antes da tira
        // na árvore, então o primeiro "Saturação" é o chip.
        composeRule.onAllNodesWithText("Saturação").onFirst().performClick()
        composeRule.onNodeWithText("Vermelho · Saturação").assertIsDisplayed()
        uitestShot("13_cor_hsl")
    }

    @Test
    fun painelEfeitos_trocaSubFerramenta() {
        var selectedSub by mutableStateOf("clarity")
        composeRule.setContent {
            LumeTheme {
                EffectsPanel(
                    recipe = Recipe(),
                    selectedSub = selectedSub,
                    onSubSelect = { selectedSub = it },
                    onDraft = {},
                    onCommit = {}
                )
            }
        }
        composeRule.onAllNodesWithText("Claridade").assertCountEquals(2)
        composeRule.onNodeWithTag("sub_vignette").performClick()
        composeRule.onAllNodesWithText("Vinheta").assertCountEquals(2)
        uitestShot("14_efeitos")
    }

    @Test
    fun painelPresets_mostraMiniaturasEIntensidade() {
        var appliedName: String? by mutableStateOf(null)
        composeRule.setContent {
            LumeTheme {
                PresetsPanel(
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
                    onShuffleClick = {}
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
        uitestShot("15_presets")
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
        composeRule.onNodeWithText("Exposição").assertIsDisplayed()
        composeRule.onNodeWithText("Contraste").assertIsDisplayed()
        composeRule.onNodeWithText("Copiar edição").assertIsDisplayed()
        composeRule.onNodeWithText("Redefinir tudo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remover Exposição")
            .performClick()
        Assert.assertEquals(1, cleared.size)
        Assert.assertEquals(0f, cleared.single().exposure, 0f)
        Assert.assertEquals(10f, cleared.single().contrast, 0f)
        uitestShot("16_historico")
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
                    onFlip = {}
                )
            }
        }
        composeRule.onNodeWithText("CORTAR").assertIsDisplayed()
        composeRule.onNodeWithText("CORRIGIR").assertIsDisplayed()
        composeRule.onNodeWithText("GIRAR").assertIsDisplayed()
        composeRule.onNodeWithText("1:1").assertIsDisplayed()
        composeRule.onNodeWithText("GIRAR").performClick()
        composeRule.onNodeWithText("Direita 90°").assertIsDisplayed()
        composeRule.onNodeWithText("CORRIGIR").performClick()
        composeRule.onNodeWithText("Endireitar").assertIsDisplayed()
        uitestShot("17_cortar")
    }
}
