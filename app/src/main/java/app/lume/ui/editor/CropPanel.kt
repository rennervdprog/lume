package app.lume.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import app.lume.core.CropRatio
import app.lume.core.Geometry
import app.lume.core.formatDegrees
import app.lume.ui.theme.LumeSpacing

enum class CropTab {
    CROP, STRAIGHTEN, ROTATE
}

/**
 * Cortar, com 3 abas: CORTAR (proporções + moldura na foto),
 * CORRIGIR (endireitar) e GIRAR (giro 90° + espelhos).
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        TabRow(selectedTabIndex = cropTab.ordinal) {
            Tab(
                selected = cropTab == CropTab.CROP,
                onClick = { onCropTabChange(CropTab.CROP) },
                text = { Text("CORTAR") }
            )
            Tab(
                selected = cropTab == CropTab.STRAIGHTEN,
                onClick = { onCropTabChange(CropTab.STRAIGHTEN) },
                text = { Text("CORRIGIR") }
            )
            Tab(
                selected = cropTab == CropTab.ROTATE,
                onClick = { onCropTabChange(CropTab.ROTATE) },
                text = { Text("GIRAR") }
            )
        }
        when (cropTab) {
            CropTab.CROP -> {
                LazyRow(
                    contentPadding = PaddingValues(vertical = LumeSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                ) {
                    items(CropRatio.entries) { ratio ->
                        FilterChip(
                            selected = ratio.matches(
                                geometry.crop, photoWidth, photoHeight
                            ),
                            onClick = { onRatioSelect(ratio) },
                            label = { Text(ratio.label) }
                        )
                    }
                }
                // A medida do corte (ex.: 899 × 1599) vive na pílula
                // sobre a foto, não aqui.
            }
            CropTab.STRAIGHTEN -> {
                SlimSliderRow(
                    label = "Endireitar",
                    value = geometry.straightenDegrees,
                    valueRange = -Geometry.MAX_STRAIGHTEN..Geometry.MAX_STRAIGHTEN,
                    onValueChange = {
                        onGeometryChange(geometry.copy(straightenDegrees = it))
                    },
                    onValueChangeFinished = {},
                    formatValue = ::formatDegrees,
                    onReset = { onGeometryChange(geometry.copy(straightenDegrees = 0f)) }
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
