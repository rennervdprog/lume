package app.lume.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Vetores próprios do Solarize (0.15.3): trilho do editor (5),
 * Ajustar (23) e o brilho Pro (1), desenhados no mesmo traço do
 * dock — linha 2, cantos redondos, uma cor só.
 *
 * A cor embutida é preta de propósito: o [androidx.compose.material3.Icon]
 * aplica o tint por cima (azul selecionado / cinza fora), igual ao
 * mecanismo que já existia no trilho. O sol dividido em cores fica
 * só nos 3 ícones do dock (vetores XML com cores fixas).
 *
 * Gerado por ~/workspace/solarize-icon/gerar_solarize_icons.py a
 * partir dos SVGs do preview — não editar à mão, regerar.
 */
object SolarizeIcons {

    val Crop: ImageVector by lazy {
        ImageVector.Builder("Crop", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M4 9 V6 a2 2 0 0 1 2-2 H9").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M15 4 H18 a2 2 0 0 1 2 2 V9").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M20 15 V18 a2 2 0 0 1-2 2 H15").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M9 20 H6 a2 2 0 0 1-2-2 V15").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.2,12 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Filter: ImageVector by lazy {
        ImageVector.Builder("Filter", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M7 3.5 H17 a3 3 0 0 1 3 3 V8").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M7 8 H14 a3 3 0 0 1 3 3 V18 a3 3 0 0 1-3 3 H7 a3 3 0 0 1-3-3 V11 a3 3 0 0 1 3-3 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M8.5,14.5 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M6.5 18.5 L9.5 15.8 L12 18 L14 16.2 L15.5 18.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Adjust: ImageVector by lazy {
        ImageVector.Builder("Adjust", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M4 7 H20 M4 12 H20 M4 17 H20").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M6.8,7 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12.8,12 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M4.8,17 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M14,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Blend: ImageVector by lazy {
        ImageVector.Builder("Blend", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M4,12 a5.5,5.5 0 1,0 11,0 a5.5,5.5 0 1,0 -11,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M9,12 a5.5,5.5 0 1,0 11,0 a5.5,5.5 0 1,0 -11,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val History: ImageVector by lazy {
        ImageVector.Builder("History", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M4.5,12.5 a7.5,7.5 0 1,0 15,0 a7.5,7.5 0 1,0 -15,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 8.5 V12.5 L15 14").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M5.5 8.5 A7.5 7.5 0 0 1 9.5 5.7").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M9.8 3.2 L7.4 5.6 L10.2 6.6 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Exposure: ImageVector by lazy {
        ImageVector.Builder("Exposure", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.5,12 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 3.5 V5.5 M12 18.5 V20.5 M3.5 12 H5.5 M18.5 12 H20.5 M6 6 L7.4 7.4 M18 6 L16.6 7.4 M6 18 L7.4 16.6 M18 18 L16.6 16.6").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Contrast: ImageVector by lazy {
        ImageVector.Builder("Contrast", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 3.5 A8.5 8.5 0 0 0 12 20.5 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Highlights: ImageVector by lazy {
        ImageVector.Builder("Highlights", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M8,14.5 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 4 V8 M9.5 6.5 L12 4 L14.5 6.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M4.5 14.5 H6.5 M17.5 14.5 H19.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Shadows: ImageVector by lazy {
        ImageVector.Builder("Shadows", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M8,9.5 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 16 V20 M9.5 17.5 L12 20 L14.5 17.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M4.5 9.5 H6.5 M17.5 9.5 H19.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Whites: ImageVector by lazy {
        ImageVector.Builder("Whites", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M6 4 H18 a2 2 0 0 1 2 2 V18 a2 2 0 0 1-2 2 H6 a2 2 0 0 1-2-2 V6 a2 2 0 0 1 2-2 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 4 H18 a2 2 0 0 1 2 2 V12 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Blacks: ImageVector by lazy {
        ImageVector.Builder("Blacks", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M6 4 H18 a2 2 0 0 1 2 2 V18 a2 2 0 0 1-2 2 H6 a2 2 0 0 1-2-2 V6 a2 2 0 0 1 2-2 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 20 H6 a2 2 0 0 1-2-2 V12 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Temperature: ImageVector by lazy {
        ImageVector.Builder("Temperature", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M12 4 a1.5 1.5 0 0 1 1.5 1.5 V14 a3.5 3.5 0 1 1-3 0 V5.5 A1.5 1.5 0 0 1 12 4 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.4,17 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M12 8 V11").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Tint: ImageVector by lazy {
        ImageVector.Builder("Tint", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M12 3.5 C12 3.5 6 10 6 14 a6 6 0 0 0 12 0 C18 10 12 3.5 12 3.5 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.2,14 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Vibrance: ImageVector by lazy {
        ImageVector.Builder("Vibrance", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M12 4 C12 4 6.5 10 6.5 13.5 a5.5 5.5 0 0 0 11 0 C17.5 10 12 4 12 4 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M18.5 3.5 L19.2 5.8 L21.5 6.5 L19.2 7.2 L18.5 9.5 L17.8 7.2 L15.5 6.5 L17.8 5.8 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Saturation: ImageVector by lazy {
        ImageVector.Builder("Saturation", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Clarity: ImageVector by lazy {
        ImageVector.Builder("Clarity", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M7.5,12 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Vignette: ImageVector by lazy {
        ImageVector.Builder("Vignette", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M6.5,4 H17.5 A3.5,3.5 0 0 1 21,7.5 V16.5 A3.5,3.5 0 0 1 17.5,20 H6.5 A3.5,3.5 0 0 1 3,16.5 V7.5 A3.5,3.5 0 0 1 6.5,4 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M9.5,8 H14.5 A2,2 0 0 1 16.5,10 V14 A2,2 0 0 1 14.5,16 H9.5 A2,2 0 0 1 7.5,14 V10 A2,2 0 0 1 9.5,8 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.5,12 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Fade: ImageVector by lazy {
        ImageVector.Builder("Fade", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M5.5,6 H18.5 A2,2 0 0 1 20.5,8 V16 A2,2 0 0 1 18.5,18 H5.5 A2,2 0 0 1 3.5,16 V8 A2,2 0 0 1 5.5,6 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M6.5 10 H17.5 M8.5 12.5 H17.5 M10.5 15 H17.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Hsl: ImageVector by lazy {
        ImageVector.Builder("Hsl", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.4,4.2 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M17.2,16 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M3.6,16 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Curve: ImageVector by lazy {
        ImageVector.Builder("Curve", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M4 12 H20 M12 4 V20").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M5 19 C9 19 9 12 12 12 C15 12 15 5 19 5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Levels: ImageVector by lazy {
        ImageVector.Builder("Levels", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M6 16 V10 M12 16 V6 M18 16 V12 M4 18.5 H20").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Balance: ImageVector by lazy {
        ImageVector.Builder("Balance", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M12 5 V19 M5 8 H19 M8 19 H16").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M5 8 L2.5 13 H7.5 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M19 8 L16.5 13 H21.5 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Rgb: ImageVector by lazy {
        ImageVector.Builder("Rgb", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M5,10 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10,10 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M7.5,15 a4.5,4.5 0 1,0 9,0 a4.5,4.5 0 1,0 -9,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val Haze: ImageVector by lazy {
        ImageVector.Builder("Haze", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M7 18 a3.5 3.5 0 0 1 -0.5-7 A5 5 0 0 1 16 9.5 A3.5 3.5 0 0 1 17 18 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.5,5.5 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Gamma: ImageVector by lazy {
        ImageVector.Builder("Gamma", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M5 19 Q12 19 12 12 Q12 5 19 5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M10.4,12 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val HueShift: ImageVector by lazy {
        ImageVector.Builder("HueShift", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M20 12 a8 8 0 1 1 -2.3 -5.6").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M17.7 3.2 L18.6 6.8 L15.1 6 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M10,12 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
    val Blur: ImageVector by lazy {
        ImageVector.Builder("Blur", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M7,12 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
            )
            .build()
    }
    val Smooth: ImageVector by lazy {
        ImageVector.Builder("Smooth", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M3.5,12 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0 Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ),
            addPath(
                pathData = PathParser().parsePathString("M8.2,10.5 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M13.2,10.5 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 Z").toNodes(),
                fill = SolidColor(Color.Black)
            ),
            addPath(
                pathData = PathParser().parsePathString("M9 14.5 Q12 17 15 14.5").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .build()
    }
    val ProSparkle: ImageVector by lazy {
        ImageVector.Builder("ProSparkle", 24f, 24f, 24f, 24f)
            addPath(
                pathData = PathParser().parsePathString("M12,2 Q13.8,10.2 22,12 Q13.8,13.8 12,22 Q10.2,13.8 2,12 Q10.2,10.2 12,2 Z").toNodes(),
                fill = SolidColor(Color.Black)
            )
            .build()
    }
}
