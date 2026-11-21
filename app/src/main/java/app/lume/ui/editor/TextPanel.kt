package app.lume.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lume.core.TextFont
import app.lume.core.TextOverlay
import app.lume.ui.theme.LumeSpacing
import kotlin.math.roundToInt

/**
 * Painel Texto (Fase 1): o texto, a fonte (5 do Google Fonts), o
 * tamanho (3..20% da largura da foto) e a cor (paleta da foto +
 * branco e preto). O texto aparece sobre a foto e sai na
 * exportação; arrastar na foto move o texto. Sem texto, o painel
 * mostra só a dica — nada é desenhado.
 */
@Composable
internal fun TextPanel(
    overlay: TextOverlay,
    paletteColors: List<Int>,
    onTextChange: (String) -> Unit,
    onFontChange: (TextFont) -> Unit,
    onSizeChange: (Float) -> Unit,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        PanelHeader(
            title = "Texto",
            trailing = {
                TextButton(onClick = { onTextChange("") }) {
                    Text("Limpar")
                }
            }
        )
        OutlinedTextField(
            value = overlay.text,
            onValueChange = onTextChange,
            label = { Text("Texto na foto") },
            placeholder = { Text("Escreva aqui") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (overlay.text.isBlank()) {
            Text(
                text = "Escreva um texto para vê-lo na foto. Arraste " +
                    "na foto para mover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            items(TextFont.entries) { font ->
                val selected = font == overlay.font
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onFontChange(font) }
                        .padding(horizontal = LumeSpacing.sm, vertical = LumeSpacing.xs)
                ) {
                    Text(
                        text = "Aa",
                        fontFamily = font.fontFamily(),
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = font.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        SlimSliderRow(
            caption = "Tamanho",
            value = overlay.sizeFraction * 100f,
            valueRange = TextOverlay.MIN_SIZE_FRACTION * 100f..
                TextOverlay.MAX_SIZE_FRACTION * 100f,
            onValueChange = { onSizeChange((it / 100f).coerceIn(
                TextOverlay.MIN_SIZE_FRACTION,
                TextOverlay.MAX_SIZE_FRACTION
            )) },
            onValueChangeFinished = {},
            formatValue = { "${it.roundToInt()}%" },
            onReset = { onSizeChange(TextOverlay.DEFAULT_SIZE_FRACTION) }
        )
        Text(
            text = "Cor — da foto",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            val colors = paletteColors + listOf(
                TextOverlay.DEFAULT_COLOR_ARGB,
                0xFF000000.toInt()
            )
            colors.forEach { argb ->
                val selected = argb == overlay.colorArgb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable { onColorChange(argb) }
                )
            }
        }
    }
}
