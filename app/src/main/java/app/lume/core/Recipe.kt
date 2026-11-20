package app.lume.core

import kotlinx.serialization.Serializable

/**
 * Receita paramétrica versionada — a ÚNICA fonte de verdade da edição.
 *
 * A foto original nunca é alterada. A receita descreve ajustes; o preview
 * e a exportação aplicam a MESMA receita (ver [app.lume.imaging.GlEngine],
 * com fallback em [RecipeColorMatrix]). Presets são receitas nomeadas.
 * O histórico é uma lista de receitas.
 *
 * Intervalos (iguais aos sliders da UI):
 * - exposure: -2..+2 (passos de exposição)
 * - demais escalares: -100..+100
 * - hsl: 8 canais (ver [HslChannels]), cada um com matiz/saturação/
 *   luminância em -100..+100
 * - curve: pontos de controle em 0..1 (ver [ToneCurve])
 *
 * Versões: v1 tinha só luz/cor global. A v2 (Fase 1) adiciona HSL,
 * curva e efeitos — todos com default = identidade, então JSON v1
 * decodifica direto (ver [RecipeJson]) e [clamped] normaliza a versão.
 * A v3 adiciona o LUT de acabamento ([lutId]/[lutIntensity]), também
 * com default identidade. A v4 (0.14.0) adiciona o Ajustar Pro do
 * motor GPUImage — níveis, balanço de cor, névoa, gama, giro de cor,
 * RGB, desfoque, suavizar pele — e as camadas de efeito artístico e
 * mistura (dupla exposição), todas com default identidade. Ao adicionar
 * um campo, incremente [CURRENT_VERSION], dê default identidade e estenda
 * [interpolatedTo] e [clamped]. Nunca quebre receitas/presets antigos sem migração.
 */
@Serializable
data class Recipe(
    val version: Int = CURRENT_VERSION,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val hsl: List<HslChannel> = List(HslChannels.COUNT) { HslChannel() },
    val curve: ToneCurve = ToneCurve(),
    val clarity: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f,
    /**
     * LUT de acabamento (v3): id de um LUT próprio ("lut-…") ou de
     * um importado (SHA-256 do .cube). Null = sem LUT. O LUT aplica
     * por último na cadeia de cor, depois da curva — e é ignorado
     * pelo fallback de matriz (ver [RecipeColorMatrix]).
     */
    val lutId: String? = null,
    /** Intensidade do LUT, 0..1. Só vale quando [lutId] != null. */
    val lutIntensity: Float = 0f,
    // ---- Ajustar Pro (v4, motor GPUImage) ----
    /** Níveis: ponto preto, 0..100 (0 = sem corte de sombra). */
    val levelsBlack: Float = 0f,
    /** Níveis: meio-tom, -100..100 (0 = meio-tom neutro). */
    val levelsMid: Float = 0f,
    /** Níveis: ponto branco, 0..100 (0 = sem corte de realce). */
    val levelsWhite: Float = 0f,
    /** Balanço de cor: sombras, -100..100 (quente + / frio -). */
    val cbShadows: Float = 0f,
    /** Balanço de cor: meios-tons, -100..100 (quente + / frio -). */
    val cbMidtones: Float = 0f,
    /** Balanço de cor: realces, -100..100 (quente + / frio -). */
    val cbHighlights: Float = 0f,
    /**
     * Névoa, -100..100: positivo = desembaça (tira a névoa),
     * negativo = adiciona névoa. 0 = sem efeito.
     */
    val haze: Float = 0f,
    /** Gama, -100..100 (0 = gama 1.0, sem mudança). */
    val gamma: Float = 0f,
    /** Giro de cor global, -100..100 (0 = sem giro; ±100 = ±180°). */
    val hueShift: Float = 0f,
    /** Ganho do canal vermelho, -100..100 (0 = ganho 1.0). */
    val rgbR: Float = 0f,
    /** Ganho do canal verde, -100..100 (0 = ganho 1.0). */
    val rgbG: Float = 0f,
    /** Ganho do canal azul, -100..100 (0 = ganho 1.0). */
    val rgbB: Float = 0f,
    /** Desfoque gaussiano, 0..100 (0 = nítido). */
    val blur: Float = 0f,
    /** Suavizar pele (bilateral), 0..100 (0 = sem suavizar). */
    val smoothSkin: Float = 0f,
    /**
     * Efeito artístico (v4): id de [app.lume.imaging.ArtisticEffect]
     * ("toon", "sketch"...) ou null = sem efeito. Discreto, como o LUT.
     */
    val artisticEffect: String? = null,
    /** Intensidade do efeito artístico, 0..1. Só vale com [artisticEffect]. */
    val artisticIntensity: Float = 1f,
    /**
     * Modo de mistura / dupla exposição (v4): id de
     * [app.lume.imaging.BlendMode] ("multiply", "screen"...) ou null
     * = sem mistura. A segunda foto vive só na sessão do editor.
     */
    val blendMode: String? = null,
    /** Opacidade da mistura, 0..1. Só vale com [blendMode]. */
    val blendOpacity: Float = 1f
) {
    fun isIdentity(): Boolean = this == Recipe(version = version)

    /** Interpola campo a campo entre esta receita e [other]. Usado pela
     * intensidade de preset (0..1) e por qualquer transição de looks.
     *
     * Preset parcial (0.15.2): o destino só move o campo quando o
     * define (diferente da identidade). Um preset que não define
     * vinheta, por exemplo, deixa a vinheta como está — aplicar um
     * preset não apaga mais os ajustes manuais. LUT, efeito
     * artístico e mistura são discretos e acoplados à intensidade:
     * sem id no destino, o conjunto inteiro fica. */
    fun interpolatedTo(other: Recipe, fraction: Float): Recipe {
        val f = fraction.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float): Float = a + (b - a) * f
        // Identidade dos escalares é 0 — exceto as intensidades
        // acopladas (LUT/efeito/mistura), que seguem o id.
        fun lerpIfSet(a: Float, b: Float): Float = if (b == 0f) a else lerp(a, b)
        val ownHsl = HslChannels.normalize(hsl)
        val otherHsl = HslChannels.normalize(other.hsl)
        return Recipe(
            version = CURRENT_VERSION,
            exposure = lerpIfSet(exposure, other.exposure),
            contrast = lerpIfSet(contrast, other.contrast),
            highlights = lerpIfSet(highlights, other.highlights),
            shadows = lerpIfSet(shadows, other.shadows),
            whites = lerpIfSet(whites, other.whites),
            blacks = lerpIfSet(blacks, other.blacks),
            temperature = lerpIfSet(temperature, other.temperature),
            tint = lerpIfSet(tint, other.tint),
            vibrance = lerpIfSet(vibrance, other.vibrance),
            saturation = lerpIfSet(saturation, other.saturation),
            hsl = ownHsl.zip(otherHsl) { a, b -> a.interpolatedTo(b, f) },
            curve = if (other.curve.isEffectiveIdentity()) curve else curve.interpolatedTo(other.curve, f),
            clarity = lerpIfSet(clarity, other.clarity),
            vignette = lerpIfSet(vignette, other.vignette),
            grain = lerpIfSet(grain, other.grain),
            fade = lerpIfSet(fade, other.fade),
            // O LUT é discreto e acoplado: em fração 0 a receita é
            // exatamente esta (o LUT atual fica); acima de 0, vale o
            // LUT do destino, com a intensidade interpolada — e sem
            // LUT no destino, o LUT atual fica, com a intensidade.
            lutId = if (f <= 0f || other.lutId == null) lutId else other.lutId,
            lutIntensity = if (other.lutId == null) lutIntensity else lerp(lutIntensity, other.lutIntensity),
            levelsBlack = lerpIfSet(levelsBlack, other.levelsBlack),
            levelsMid = lerpIfSet(levelsMid, other.levelsMid),
            levelsWhite = lerpIfSet(levelsWhite, other.levelsWhite),
            cbShadows = lerpIfSet(cbShadows, other.cbShadows),
            cbMidtones = lerpIfSet(cbMidtones, other.cbMidtones),
            cbHighlights = lerpIfSet(cbHighlights, other.cbHighlights),
            haze = lerpIfSet(haze, other.haze),
            gamma = lerpIfSet(gamma, other.gamma),
            hueShift = lerpIfSet(hueShift, other.hueShift),
            rgbR = lerpIfSet(rgbR, other.rgbR),
            rgbG = lerpIfSet(rgbG, other.rgbG),
            rgbB = lerpIfSet(rgbB, other.rgbB),
            blur = lerpIfSet(blur, other.blur),
            smoothSkin = lerpIfSet(smoothSkin, other.smoothSkin),
            // Efeito artístico e mistura são discretos, como o LUT.
            artisticEffect = if (f <= 0f || other.artisticEffect == null) artisticEffect else other.artisticEffect,
            artisticIntensity = if (other.artisticEffect == null) artisticIntensity else lerp(artisticIntensity, other.artisticIntensity),
            blendMode = if (f <= 0f || other.blendMode == null) blendMode else other.blendMode,
            blendOpacity = if (other.blendMode == null) blendOpacity else lerp(blendOpacity, other.blendOpacity)
        )
    }

    /** Garante intervalos válidos. Receitas vindas de JSON/importação
     * passam sempre por aqui antes de entrar no editor. */
    fun clamped(): Recipe = Recipe(
        version = CURRENT_VERSION,
        exposure = exposure.coerceIn(-2f, 2f),
        contrast = contrast.coerceIn(-100f, 100f),
        highlights = highlights.coerceIn(-100f, 100f),
        shadows = shadows.coerceIn(-100f, 100f),
        whites = whites.coerceIn(-100f, 100f),
        blacks = blacks.coerceIn(-100f, 100f),
        temperature = temperature.coerceIn(-100f, 100f),
        tint = tint.coerceIn(-100f, 100f),
        vibrance = vibrance.coerceIn(-100f, 100f),
        saturation = saturation.coerceIn(-100f, 100f),
        hsl = HslChannels.normalize(hsl),
        curve = curve.clamped(),
        clarity = clarity.coerceIn(-100f, 100f),
        vignette = vignette.coerceIn(-100f, 100f),
        grain = grain.coerceIn(-100f, 100f),
        fade = fade.coerceIn(-100f, 100f),
        lutId = lutId?.ifBlank { null },
        lutIntensity = lutIntensity.coerceIn(0f, 1f),
        levelsBlack = levelsBlack.coerceIn(0f, 100f),
        levelsMid = levelsMid.coerceIn(-100f, 100f),
        levelsWhite = levelsWhite.coerceIn(0f, 100f),
        cbShadows = cbShadows.coerceIn(-100f, 100f),
        cbMidtones = cbMidtones.coerceIn(-100f, 100f),
        cbHighlights = cbHighlights.coerceIn(-100f, 100f),
        haze = haze.coerceIn(-100f, 100f),
        gamma = gamma.coerceIn(-100f, 100f),
        hueShift = hueShift.coerceIn(-100f, 100f),
        rgbR = rgbR.coerceIn(-100f, 100f),
        rgbG = rgbG.coerceIn(-100f, 100f),
        rgbB = rgbB.coerceIn(-100f, 100f),
        blur = blur.coerceIn(0f, 100f),
        smoothSkin = smoothSkin.coerceIn(0f, 100f),
        artisticEffect = artisticEffect?.ifBlank { null },
        artisticIntensity = artisticIntensity.coerceIn(0f, 1f),
        blendMode = blendMode?.ifBlank { null },
        blendOpacity = blendOpacity.coerceIn(0f, 1f)
    )

    companion object {
        const val CURRENT_VERSION: Int = 4
        val IDENTITY: Recipe = Recipe()
    }
}
