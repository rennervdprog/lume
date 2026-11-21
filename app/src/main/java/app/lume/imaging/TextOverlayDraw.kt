package app.lume.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.lume.core.TextFont
import app.lume.core.TextOverlay
import app.lume.core.centeredBaselineOffset
import app.lume.core.fitScale

/**
 * Texto na foto, na exportação (Fase 1): desenha o [TextOverlay]
 * sobre o bitmap final com Canvas + o Typeface da fonte escolhida.
 * As frações são da foto final (geometria já aplicada) — as mesmas
 * da tela — então o que o usuário vê é o que sai no arquivo.
 * Sem texto, não faz nada.
 */
object TextOverlayDraw {

    fun draw(context: Context, bitmap: Bitmap, overlay: TextOverlay) {
        if (overlay.isEmpty) return
        val typeface = resolveTypeface(context, overlay.font) ?: Typeface.DEFAULT
        val baseSizePx = overlay.sizeFraction * bitmap.width
        if (baseSizePx <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = overlay.colorArgb
            textSize = baseSizePx
            textAlign = Paint.Align.CENTER
            // Sombra leve, igual à da tela: o texto lê sobre foto clara.
            setShadowLayer(baseSizePx * 0.08f, 0f, baseSizePx * 0.04f, 0xB3000000)
        }
        // Texto longo demais encolhe até caber (mesma regra da tela).
        val scale = fitScale(
            paint.measureText(overlay.text),
            TextOverlay.MAX_WIDTH_FRACTION * bitmap.width
        )
        if (scale < 1f) paint.textSize = baseSizePx * scale
        val centerXPx = overlay.centerX * bitmap.width
        val centerYPx = overlay.centerY * bitmap.height
        val baseline = centerYPx + centeredBaselineOffset(
            paint.fontMetrics.ascent,
            paint.fontMetrics.descent
        )
        Canvas(bitmap).drawText(overlay.text, centerXPx, baseline, paint)
    }

    /**
     * O Typeface da fonte embutida em res/font. Null quando o
     * recurso não existe (nunca deveria acontecer — as 5 fontes
     * são embutidas no APK); o chamador cai para o DEFAULT.
     * Fonte variável (Playfair, Dancing, Roboto Condensed,
     * Manrope) rende a instância padrão em API < 26; no resto,
     * a instância padrão também — sem variação de peso.
     */
    fun resolveTypeface(context: Context, font: TextFont): Typeface? {
        return try {
            ResourcesCompat.getFont(context, font.fontRes)
        } catch (e: Exception) {
            null
        }
    }
}
