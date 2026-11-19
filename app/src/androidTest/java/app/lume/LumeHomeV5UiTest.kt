package app.lume

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Testes da Home v5 (Começar, estado vazio, grade por data).
 *
 * A permissão de fotos é concedida pela regra — e a concessão tem
 * que acontecer ANTES da Activity abrir, senão a aba Fotos nasce
 * na tela de explicação e nunca revalida (o refresh só roda na
 * entrada e no ON_RESUME). O JUnit aplica a ÚLTIMA regra
 * declarada por fora, então declarar a permissão antes do Compose
 * NÃO garante a ordem (foi o que derrubou a run 32: a Activity
 * abriu antes da concessão e o "Começar" nunca apareceu) — por
 * isso a cadeia explícita. A regra revoga depois de cada teste,
 * então os testes de [LumeAppUiTest] (que esperam a permissão
 * nunca pedida) não são afetados. Nenhum teste cria dado: a
 * galeria do emulador está vazia e não há rascunhos.
 */
@RunWith(AndroidJUnit4::class)
class LumeHomeV5UiTest {

    private val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    private fun nodesWith(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun homeMostraComecarEEstadoVazio() {
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Começar")
        }
        composeRule.onNodeWithText("Começar").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir foto").assertIsDisplayed()
        // A fileira rola na horizontal e a largura visível muda por
        // aparelho: só o 1º atalho tem a visibilidade garantida. Os
        // demais são trazidos para a tela com a rolagem da própria
        // fileira antes de afirmar que aparecem (a run 33 caiu
        // aqui: o 4º atalho nascia fora da tela do emulador).
        val startRow = composeRule.onNodeWithTag("start_row")
        startRow.performScrollToNode(hasText("Aplicar preset"))
        composeRule.onNodeWithText("Aplicar preset").assertIsDisplayed()
        startRow.performScrollToNode(hasText("Cortar"))
        composeRule.onNodeWithText("Cortar").assertIsDisplayed()
        startRow.performScrollToNode(hasText("Ajustar"))
        composeRule.onNodeWithText("Ajustar").assertIsDisplayed()
        startRow.performScrollToNode(hasText("LUT"))
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
