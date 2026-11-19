package app.lume

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Testes do atalho Câmera (0.14.0): o atalho existe na fileira
 * "Começar" e abre a tela de explicação da permissão de câmera
 * (a permissão NÃO é concedida aqui de propósito — o teste prova
 * o caminho do pedido, não o visor, que precisa de câmera real).
 *
 * A permissão de fotos É concedida pela regra (mesmo motivo da
 * Home v5: a grade precisa dela para mostrar o "Começar").
 */
@RunWith(AndroidJUnit4::class)
class LumeCameraUiTest {

    private val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    // Sem hierarquia ainda (Activity não subiu), o fetch lança —
    // sem o try, o waitUntil morre na primeira avaliação em vez
    // de esperar a Activity.
    private fun nodesWith(text: String) = try {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    @Test
    fun atalhoCameraAbreExplicacaoDaPermissao() {
        composeRule.waitUntil(timeoutMillis = 8000) {
            nodesWith("Começar")
        }
        val startRow = composeRule.onNodeWithTag("start_row")
        startRow.performScrollToNode(hasTestTag("start_camera"))
        composeRule.onNodeWithTag("start_camera").assertIsDisplayed()
        composeRule.onNodeWithText("Câmera").assertIsDisplayed()
        // Clica no tile (a tag), não no texto: o clique no rótulo
        // não navegava no emulador (run #37) e a tag acerta o
        // mesmo clickable do tile.
        composeRule.onNodeWithTag("start_camera").performClick()
        composeRule.waitUntil(timeoutMillis = 10000) {
            nodesWith("Fotografe dentro do Lume")
        }
        composeRule.onNodeWithText("Fotografe dentro do Lume").assertIsDisplayed()
        composeRule.onNodeWithText("Permitir acesso à câmera").assertIsDisplayed()
        composeRule.onNodeWithText("Agora não").assertIsDisplayed()
        uitestShot("21_camera_permissao")
    }
}
