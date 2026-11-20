package app.lume

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de UI instrumentados do app de verdade: abrem a
 * MainActivity no emulador e tocam nos fluxos (abas, permissão,
 * conta, paywall). O editor em si é coberto pelos testes de painel
 * ([LumePanelsUiTest]) — abrir uma foto exigiria o seletor do
 * sistema, que o teste automatizado não controla.
 *
 * Roda no CI via `:app:connectedDebugAndroidTest` (job "UI tests").
 * Banco e DataStore são os reais do aparelho de teste; nenhum teste
 * aqui cria dado (preset ou rascunho) nem concede a permissão de
 * fotos, então a ordem não importa e a aba Fotos nasce sempre na
 * tela de explicação da permissão (nunca pedida, aparelho limpo).
 */
@RunWith(AndroidJUnit4::class)
class LumeAppUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun nodesWith(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

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
    fun homeAbreComNavegacaoEExplicacaoDaPermissao() {
        composeRule.onNodeWithTag("nav_fotos").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_presets").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_conta").assertIsDisplayed()
        composeRule.onNodeWithTag("home_wordmark").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Suas fotos, aqui dentro")
        }
        composeRule.onNodeWithText("Permitir acesso às fotos").assertExists()
        composeRule.onNodeWithTag("fab_new_edit").assertIsDisplayed()
        uitestShot("01_fotos")
    }

    @Test
    fun agoraNaoMostraEstadoNegadoComVolta() {
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Agora não")
        }
        composeRule.onNodeWithText("Agora não")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Sem acesso às suas fotos")
        }
        // Nunca pedida: o sistema ainda mostraria a janela, então o
        // botão pede de novo em vez de ir às Configurações.
        composeRule.onNodeWithText("Pedir permissão de novo").assertExists()
        composeRule.onNodeWithTag("fab_new_edit").assertIsDisplayed()
        uitestShot("02_negado")
    }

    @Test
    fun abaPresetsMostraDemonstracaoEPresets() {
        composeRule.onNodeWithTag("nav_presets").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Pele Dourada")
        }
        composeRule.onNodeWithText("Usar este preset").assertExists()
        awaitText("Luz da Manhã", "presets_list")
        composeRule.onNodeWithTag("presets_title").assertExists()
        composeRule.onNodeWithText("Luz da Manhã").assertExists()
        composeRule.onNodeWithText("Cinza Elegante").assertExists()
        uitestShot("03_presets")
    }

    @Test
    fun contaAbrePelaAbaEVolta() {
        composeRule.onNodeWithTag("nav_conta").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Você está como visitante")
        }
        composeRule.onNodeWithText("Entrar com Google").assertIsDisplayed()
        uitestShot("04_conta")
        composeRule.onNodeWithTag("nav_fotos").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Suas fotos, aqui dentro")
        }
    }

    @Test
    fun paywallAbrePelaEstrela() {
        composeRule.onNodeWithContentDescription("Solarize Pro").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Grátis de verdade. Pro para quem vive de foto.")
        }
        // Os botões ficam abaixo da tabela: rola até eles serem compostos.
        awaitText("Assinar o Pro", "paywall_list")
        composeRule.onNodeWithText("Assinar o Pro").assertExists()
        composeRule.onNodeWithText("Continuar grátis").assertExists()
        uitestShot("05_paywall")
    }
}
