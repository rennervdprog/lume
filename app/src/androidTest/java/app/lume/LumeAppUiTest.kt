package app.lume

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * o herói da Biblioteca nasce sempre na versão "sem rascunhos".
 */
@RunWith(AndroidJUnit4::class)
class LumeAppUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun nodesWith(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Rola a lista da Biblioteca até o texto existir. */
    private fun awaitText(text: String, maxSwipes: Int = 6) {
        var tries = 0
        while (!nodesWith(text) && tries < maxSwipes) {
            composeRule.onNodeWithTag("library_list")
                .performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            tries++
        }
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith(text) }
    }

    @Test
    fun bibliotecaAbreComHeroEPresets() {
        composeRule.onNodeWithText("Editar uma foto").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir foto").assertIsDisplayed()
        awaitText("Presets")
        composeRule.onNodeWithText("Luz da Manhã").assertExists()
        composeRule.onNodeWithText("Cinza Elegante").assertExists()
        uitestShot("01_biblioteca")
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
            nodesWith("Editar uma foto")
        }
    }

    @Test
    fun paywallAbrePelaEstrela() {
        composeRule.onNodeWithContentDescription("Lume Pro").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Grátis de verdade. Pro para quem vive de foto.")
        }
        composeRule.onNodeWithText("Assinar o Pro").assertExists()
        composeRule.onNodeWithText("Continuar grátis").assertExists()
        uitestShot("03_paywall")
    }
}
