package app.lume

/**
 * Screenshot de teste de UI: sinaliza via logcat e o script do CI
 * captura a tela com `adb exec-out screencap` (mesmo mecanismo do
 * Constância). A pausa dá tempo do monitor do CI ver o marcador
 * antes da UI mudar.
 */
fun uitestShot(name: String) {
    android.util.Log.d("UITEST_SHOT", name)
    try {
        Thread.sleep(1500)
    } catch (_: InterruptedException) {
    }
}
