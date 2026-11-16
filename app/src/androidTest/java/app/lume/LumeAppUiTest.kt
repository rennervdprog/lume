package app.lume

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de UI instrumentados do app de verdade: abrem a
 * MainActivity no emulador e tocam nos fluxos (biblioteca, conta,
 * paywall). O editor em si é coberto pelos testes de painel
 * ([LumePanelsUiTest]) — abrir uma foto exigiria o seletor do
 * sistema, que o teste automatizado não controla.
 *
 * Roda no CI via `:app:connectedDebugAndroidTest` (job "UI tests").
 * Banco e DataStore são os reais do aparelho de teste; nenhum teste
 * aqui cria dado (preset ou rascunho), então a ordem não importa e
 * o banner da Home nasce sempre na versão "sem rascunhos"
 * (gradiente, sem foto).
 */
@RunWith(AndroidJUnit4::class)
class LumeAppUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun nodesWith(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun tagExists(tag: String) =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Rola a lista indicada até o texto existir. */
    private fun awaitText(text: String, listTag: String, maxSwipes: Int = 8) {
        var tries = 0
        while (!nodesWith(text) && tries < maxSwipes) {
            composeRule.onNodeWithTag(listTag)
                .performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            tries++
        }
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith(text) }
    }

    @Test
    fun bibliotecaAbreComHeroEPresets() {
        composeRule.onNodeWithTag("home_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_edit").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_presets").assertIsDisplayed()
        composeRule.onNodeWithTag("fn_light").assertExists()
        composeRule.onNodeWithTag("fn_qr").assertExists()
        awaitText("Luz da Manhã", "library_list")
        composeRule.onNodeWithTag("presets_title").assertExists()
        composeRule.onNodeWithText("Luz da Manhã").assertExists()
        composeRule.onNodeWithText("Cinza Elegante").assertExists()
        uitestShot("01_biblioteca")
    }

    @Test
    fun homeFixaTiraAoRolar() {
        var tries = 0
        while (!tagExists("pinned_strip") && tries < 4) {
            composeRule.onNodeWithTag("library_list")
                .performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            tries++
        }
        composeRule.waitUntil(timeoutMillis = 8000) { tagExists("pinned_strip") }
        composeRule.onNodeWithTag("strip_light").assertExists()
        composeRule.onNodeWithTag("strip_crop").assertExists()
        uitestShot("04_home_tira")
    }

    @Test
    fun contaAbrePelaBarraEVolta() {
        composeRule.onNodeWithContentDescription("Conta").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Você está como visitante")
        }
        composeRule.onNodeWithText("Entrar com Google").assertIsDisplayed()
        uitestShot("02_conta")
        composeRule.onNodeWithContentDescription("Voltar").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Editar foto")
        }
    }

    @Test
    fun paywallAbrePelaEstrela() {
        composeRule.onNodeWithContentDescription("Lume Pro").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Grátis de verdade. Pro para quem vive de foto.")
        }
        // Os botões ficam abaixo da tabela: rola até eles serem compostos.
        awaitText("Assinar o Pro", "paywall_list")
        composeRule.onNodeWithText("Assinar o Pro").assertExists()
        composeRule.onNodeWithText("Continuar grátis").assertExists()
        uitestShot("03_paywall")
    }
}
