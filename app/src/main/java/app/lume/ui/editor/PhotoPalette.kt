package app.lume.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import app.lume.ui.theme.LumeSpacing

/**
 * Cores da foto (Fase 1): a paleta da foto atual, extraída no
 * aparelho com o AndroidX Palette — sem rede, sem custo, offline.
 * A extração roda fora da thread de UI (o ViewModel chama em
 * Dispatchers.Default); a lista vai para o estado e a faixa
 * mostra as cores no Ajustar e como escolha de cor no Texto.
 */
object PhotoPalette {

    /** Seis amostras, como o preview aprovado. */
    const val SWATCH_COUNT: Int = 6

    /**
     * Extrai até [SWATCH_COUNT] cores do [bitmap]. Primeiro os 6
     * perfis do Palette (vibrante/claro/escuro, suave/claro/escuro);
     * o que faltar, completa com as amostras mais populosas.
     * Chamar fora da thread de UI.
     */
    fun extract(bitmap: Bitmap): List<Int> {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val profiles = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch
        ).map { it.rgb }
        val byPopulation = palette.swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
        return selectSwatchColors(profiles, byPopulation)
    }

    /**
     * Regra pura de escolha das amostras (testável sem Bitmap):
     * os perfis na ordem, completando com as mais populosas, sem
     * repetir cor, até [count].
     */
    fun selectSwatchColors(
        profiles: List<Int>,
        byPopulation: List<Int>,
        count: Int = SWATCH_COUNT
    ): List<Int> {
        return (profiles + byPopulation).distinct().take(count)
    }
}

/** "#rrggbb" em minúsculas, como o preview (sem o alfa). */
internal fun hexString(argb: Int): String {
    return "#%06x".format(argb and 0xFFFFFF)
}

/**
 * Faixa "Cores da foto": as amostras com o hex embaixo. O toque
 * chama [onColorSelect] — no Ajustar, copiar o hex e deixar a cor
 * como cor do Texto; no Texto, só trocar a cor. A cor selecionada
 * leva a borda azul.
 */
@Composable
internal fun PhotoPaletteStrip(
    title: String,
    colors: List<Int>,
    selectedColor: Int?,
    onColorSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (colors.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { argb ->
                val selected = argb == selectedColor
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(argb))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(10.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onColorSelect(argb) }
                    )
                    Text(
                        text = hexString(argb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
