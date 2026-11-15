// Top-level build file. Versões copiadas do projeto de referência que já
// compila no CI do Renner (entregador); ver gradle/libs.versions.toml.
// CI ainda não existe para este projeto (sem repo): a primeira build
// real será no workflow do repo, quando ele for criado.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
