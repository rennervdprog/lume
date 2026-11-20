package app.lume

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
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

    // O estado da permissão de câmera ("já pedida") vive em
    // SharedPreferences e SOBREVIVE entre classes de teste na
    // mesma execução: se algum teste anterior pediu a permissão,
    // a tela da câmera abre no estado negado em vez da explicação
    // e este teste expira esperando o texto (runs #36–#38). Limpa
    // antes de cada execução para o teste ser hermético.
    @Before
    fun limpaEstadoDaPermissaoDeCamera() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("lume_camera_permission", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

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
        // A rolagem mínima deixa o atalho (último da fileira) só
        // parcialmente visível na borda: o clique no centro do nó
        // cai no atalho vizinho e abre o seletor de fotos — a
        // Activity pausa atrás dele e a explicação nunca aparece
        // (runs #36–#39; o logcat mostra PAUSED/STOPPED e o
        // DocumentsUI abrindo). Desliza até o atalho caber inteiro
        // na tela antes de clicar.
        val displayWidth =
            composeRule.activity.resources.displayMetrics.widthPixels.toFloat()
        // O botão + ("Nova edição") fica fixo no canto inferior
        // direito POR CIMA da fileira e cobre o centro do atalho
        // Câmera (último da fileira): o toque no centro abre o
        // seletor de fotos em vez da câmera (runs #36–#42; o print
        // 06_comecar mostra o + cobrindo o atalho). Deslizar a
        // fileira não resolve — ela já está no fim do curso e o
        // atalho não anda mais para a esquerda (run #42: 10
        // deslizes sem sair do lugar). O toque vai no TOPO do
        // atalho, acima do +, que é área clicável do mesmo atalho.
        val fabBounds = composeRule.onAllNodesWithTag("fab_new_edit")
            .fetchSemanticsNodes().first().boundsInRoot
        for (i in 0 until 10) {
            val bounds = composeRule.onAllNodesWithTag("start_camera")
                .fetchSemanticsNodes().first().boundsInRoot
            if (bounds.left >= 0f && bounds.right <= displayWidth) break
            startRow.performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("start_camera").assertIsDisplayed()
        composeRule.onNodeWithText("Câmera").assertIsDisplayed()
        val tileBounds = composeRule.onAllNodesWithTag("start_camera")
            .fetchSemanticsNodes().first().boundsInRoot
        val tapX = (tileBounds.left + tileBounds.right) / 2f
        var tapY = tileBounds.top + tileBounds.height * 0.10f
        if (fabBounds.contains(Offset(tapX, tapY))) {
            tapY = fabBounds.top - 8f
        }
        // Toque por coordenada (o clique no centro do nó cai no +):
        // down/up são os primitivos do escopo de toque, sem import
        // novo — o ponto é relativo à raiz, mesma base do boundsInRoot.
        composeRule.onRoot().performTouchInput {
            down(0, Offset(tapX, tapY))
            up(0)
        }
        try {
            composeRule.waitUntil(timeoutMillis = 10000) {
                nodesWith("Fotografe dentro do Solarize")
            }
        } catch (e: ComposeTimeoutException) {
            // Diagnóstico: despeja a árvore de semântica no logcat
            // (o artefato de relatórios guarda o logcat por teste) —
            // mostra se o clique não navegou ou se a tela abriu em
            // outro estado (negado/visor). Sem hierarquia (activity
            // parada atrás do seletor, por exemplo), o despejo
            // lança também — não pode mascarar a expiração original.
            try {
                Log.d("LumeCameraUiTest", "Árvore na expiração:\n" + composeRule.onRoot().printToString())
            } catch (_: Exception) {
                Log.d("LumeCameraUiTest", "Sem hierarquia na expiração (activity parada?).")
            }
            throw e
        }
        // A explicação é uma coluna rolável e o conteúdo é alto
        // (foto 220dp + título + texto + 3 marcadores + botões):
        // no emulador o botão "Permitir" fica ABAIXO da dobra —
        // existe na árvore, mas assertIsDisplayed falha porque não
        // está visível (run #43: o título passou, o botão não).
        // Rola até cada botão antes de conferir.
        composeRule.onNodeWithText("Fotografe dentro do Solarize").assertIsDisplayed()
        composeRule.onNodeWithText("Permitir acesso à câmera").performScrollTo()
        composeRule.onNodeWithText("Permitir acesso à câmera").assertIsDisplayed()
        composeRule.onNodeWithText("Agora não").performScrollTo()
        composeRule.onNodeWithText("Agora não").assertIsDisplayed()
        uitestShot("21_camera_permissao")
    }
}
