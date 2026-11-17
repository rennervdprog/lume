package app.lume.ui.editor

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lume.core.CropRatio
import app.lume.core.Geometry
import app.lume.core.formatDegrees
import app.lume.ui.theme.LumeSpacing

enum class CropTab {
    CROP, STRAIGHTEN, ROTATE
}

/**
 * Cortar (v4), com 3 abas de texto: Cortar (proporções + moldura
 * na foto), Corrigir (endireitar) e Girar (giro 90° + espelhos).
 * As 6 proporções ficam todas visíveis, com o desenho do formato.
 *
 * [photoWidth]/[photoHeight] são as dimensões da foto JÁ girada — o
 * espaço onde o corte vive. A perspectiva (inclinar) não está aqui:
 * chega depois, sem controle falso.
 */
@Composable
internal fun CropPanel(
    geometry: Geometry,
    photoWidth: Int,
    photoHeight: Int,
    cropTab: CropTab,
    onCropTabChange: (CropTab) -> Unit,
    onGeometryChange: (Geometry) -> Unit,
    onRatioSelect: (CropRatio) -> Unit,
    onRotate: (Int) -> Unit,
    onFlip: (Boolean) -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        PanelHeader(title = "Cortar")
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.md)
        ) {
            TextTab(
                label = "Cortar",
                selected = cropTab == CropTab.CROP,
                onClick = { onCropTabChange(CropTab.CROP) }
            )
            TextTab(
                label = "Corrigir",
                selected = cropTab == CropTab.STRAIGHTEN,
                onClick = { onCropTabChange(CropTab.STRAIGHTEN) }
            )
            TextTab(
                label = "Girar",
                selected = cropTab == CropTab.ROTATE,
                onClick = { onCropTabChange(CropTab.ROTATE) }
            )
        }
        when (cropTab) {
            CropTab.CROP -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CropRatio.entries.forEach { ratio ->
                        RatioButton(
                            ratio = ratio,
                            selected = ratio.matches(
                                geometry.crop, photoWidth, photoHeight
                            ),
                            onClick = { onRatioSelect(ratio) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // A medida do corte (ex.: 899 × 1599) vive na pílula
                // sobre a foto, não aqui.
            }
            CropTab.STRAIGHTEN -> {
                SlimSliderRow(
                    caption = "Endireitar",
                    value = geometry.straightenDegrees,
                    valueRange = -Geometry.MAX_STRAIGHTEN..Geometry.MAX_STRAIGHTEN,
                    onValueChange = {
                        onGeometryChange(geometry.copy(straightenDegrees = it))
                    },
                    onValueChangeFinished = {},
                    formatValue = ::formatDegrees,
                    onReset = { onGeometryChange(geometry.copy(straightenDegrees = 0f)) },
                    onCompareChange = onCompareChange
                )
                Text(
                    text = "Corta as bordas para não deixar cantos vazios.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CropTab.ROTATE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RotateAction(
                        label = "Direita 90°",
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        onClick = { onRotate(90) }
                    )
                    RotateAction(
                        label = "Esquerda 90°",
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        onClick = { onRotate(-90) }
                    )
                    RotateAction(
                        label = "Espelhar",
                        icon = Icons.Filled.Flip,
                        active = geometry.flipHorizontal,
                        onClick = { onFlip(true) }
                    )
                    RotateAction(
                        label = "Inverter",
                        icon = Icons.Filled.Flip,
                        active = geometry.flipVertical,
                        iconModifier = Modifier.graphicsLayer { rotationZ = 90f },
                        onClick = { onFlip(false) }
                    )
                }
                val rotation = ((geometry.rotationDegrees % 360) + 360) % 360
                if (rotation != 0) {
                    Text(
                        text = "Giro atual: $rotation°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Desenho do formato da proporção, na proporção real (limitada). */
private fun ratioIconSize(aspect: Float?): Pair<Dp, Dp> {
    if (aspect == null) return 24.dp to 18.dp
    val height = 20.dp
    val width = (20f * aspect).dp.coerceIn(13.dp, 28.dp)
    return width to height
}

@Composable
private fun RatioButton(
    ratio: CropRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (iconWidth, iconHeight) = ratioIconSize(ratio.aspect)
    val tint = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(iconWidth)
                .height(iconHeight)
                .border(
                    1.5.dp,
                    tint,
                    RoundedCornerShape(2.dp)
                )
        )
        Text(
            text = ratio.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RotateAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
    iconModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xxs),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = LumeSpacing.xs, vertical = LumeSpacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = iconModifier
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
    }
}
