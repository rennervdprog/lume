package app.lume.ui.editor

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.lume.core.TextFont

/**
 * Saver por nome para [TextFont]: enum não é Bundle-saveable, e o
 * rememberSaveable sem Saver explícito estoura na hora de salvar
 * (mesma lição da 0.15.4 com EditorTool e CropTab). Sem isso, a
 * fonte escolhida morria ao ir ao paywall e voltar.
 */
internal val textFontSaver = Saver<TextFont, String>(
    save = { it.name },
    restore = { runCatching { TextFont.valueOf(it) }.getOrDefault(TextFont.DANCING_SCRIPT) }
)

/** A fonte do Compose para o preview do texto sobre a foto. */
internal fun TextFont.fontFamily(): FontFamily = FontFamily(Font(fontRes))
