package app.lume

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes da Home v5 (Começar, estado vazio, grade por data).
 *
 * A permissão de fotos é concedida pela regra — declarada ANTES
 * da regra do Compose, para a concessão acontecer antes da
 * Activity abrir e a aba Fotos nascer na grade, não na tela de
 * explicação. A regra revoga depois de cada teste, então os
 * testes de [LumeAppUiTest] (que esperam a permissão nunca
 * pedida) não são afetados. Nenhum teste cria dado: a galeria do
 * emulador está vazia e não há rascunhos.
 */
@RunWith(AndroidJUnit4::class)
class LumeHomeV5UiTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun nodesWith(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun homeMostraComecarEEstadoVazio() {
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Começar")
        }
        composeRule.onNodeWithText("Começar").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir foto").assertIsDisplayed()
        composeRule.onNodeWithText("Aplicar preset").assertIsDisplayed()
        composeRule.onNodeWithText("Cortar").assertIsDisplayed()
        composeRule.onNodeWithText("Ajustar").assertIsDisplayed()
        // O 5º atalho nasce fora da tela: rola a fileira até ele.
        composeRule.onNodeWithTag("start_row")
            .performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("LUT")
        }
        composeRule.onNodeWithText("LUT").assertIsDisplayed()
        // Sem rascunhos: o cartão de estado vazio no lugar da tira.
        composeRule.onNodeWithText("Suas edições").assertIsDisplayed()
        composeRule.onNodeWithText("Nenhuma edição em andamento").assertExists()
        composeRule.onNodeWithText("Editar foto").assertExists()
        // Galeria do emulador vazia: a grade nasce sem fotos.
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Nenhuma foto encontrada neste aparelho.")
        }
        composeRule.onNodeWithTag("fab_new_edit").assertIsDisplayed()
        uitestShot("06_comecar")
    }
}
