package app.lume.core

import kotlinx.serialization.Serializable

/**
 * Preset = receita nomeada + metadados. Nunca é uma imagem "congelada":
 * ao aplicar, o usuário pode ajustar todos os parâmetros depois.
 *
 * [createdAtMillis]/[updatedAtMillis] usam relógio do aparelho
 * (System.currentTimeMillis) — nunca java.time (minSdk 24).
 */
@Serializable
data class Preset(
    val id: String,
    val name: String,
    val collection: String,
    val recipe: Recipe,
    val isBuiltIn: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

/**
 * Índices dos canais HSL, na ordem fixa de [HslChannels].
 */
private const val CH_RED = 0
private const val CH_ORANGE = 1
private const val CH_YELLOW = 2
private const val CH_GREEN = 3
private const val CH_CYAN = 4
private const val CH_BLUE = 5
private const val CH_PURPLE = 6
private const val CH_MAGENTA = 7

/** Monta a lista de 8 canais com só os ajustes informados; o resto fica identidade. */
private fun hslChannels(vararg edits: Pair<Int, HslChannel>): List<HslChannel> {
    val channels = MutableList(HslChannels.COUNT) { HslChannel() }
    edits.forEach { (index, channel) -> channels[index] = channel.clamped() }
    return channels
}

/**
 * Presets iniciais ORIGINAIS, criados como receitas para este app.
 * Nomes, coleções e valores foram inventados aqui — não copiam presets
 * de nenhum concorrente nem de nenhum pack comercial.
 *
 * As 30 receitas novas (16/11) seguem a mesma regra: valores inventados
 * aqui, aprovados em preview. A prova de teste em pele e luz brasileiras
 * (diferencial P0 do estudo) continua pendente — os presets ainda não
 * foram validados em fotos reais de pele/luz do Brasil.
 */
object BuiltInPresets {

    val all: List<Preset> = listOf(
        Preset(
            id = "builtin-luz-da-manha",
            name = "Luz da Manhã",
            collection = "Essenciais",
            recipe = Recipe(
                exposure = 0.3f,
                highlights = -15f,
                shadows = 20f,
                temperature = 12f,
                vibrance = 12f
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-tarde-dourada",
            name = "Tarde Dourada",
            collection = "Essenciais",
            recipe = Recipe(
                exposure = 0.1f,
                contrast = 8f,
                highlights = -25f,
                shadows = 10f,
                temperature = 30f,
                saturation = 8f
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-sombra-fresca",
            name = "Sombra Fresca",
            collection = "Essenciais",
            recipe = Recipe(
                exposure = 0.2f,
                shadows = 25f,
                temperature = -20f,
                tint = -6f,
                vibrance = 8f
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-vivo-sem-exagero",
            name = "Vivo sem Exagero",
            collection = "Essenciais",
            recipe = Recipe(
                contrast = 8f,
                vibrance = 25f,
                saturation = 5f,
                blacks = -8f
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-noite-calma",
            name = "Noite Calma",
            collection = "Essenciais",
            recipe = Recipe(
                exposure = -0.4f,
                contrast = 10f,
                blacks = -15f,
                temperature = -10f,
                saturation = -5f
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-cinza-elegante",
            name = "Cinza Elegante",
            collection = "Preto e Branco",
            recipe = Recipe(
                contrast = 15f,
                highlights = -10f,
                shadows = 10f,
                blacks = -10f,
                saturation = -100f
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-pele-dourada",
            name = "Pele Dourada",
            collection = "Pele e Retrato",
            recipe = Recipe(
                shadows = 15f,
                temperature = 18f,
                vibrance = 10f,
                clarity = -8f,
                fade = 8f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(luminance = 10f, saturation = 12f),
                    CH_YELLOW to HslChannel(saturation = 6f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-retrato-macio",
            name = "Retrato Macio",
            collection = "Pele e Retrato",
            recipe = Recipe(
                exposure = 0.2f,
                contrast = -12f,
                highlights = -20f,
                clarity = -15f,
                vignette = 10f,
                fade = 12f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(luminance = 12f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-tom-quente",
            name = "Tom Quente",
            collection = "Pele e Retrato",
            recipe = Recipe(
                contrast = 5f,
                temperature = 28f,
                tint = 8f,
                saturation = 6f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(hue = -8f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-pele-neutra",
            name = "Pele Neutra",
            collection = "Pele e Retrato",
            recipe = Recipe(
                shadows = 10f,
                temperature = 5f,
                tint = 2f,
                vibrance = 6f,
                clarity = -5f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(saturation = -8f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-rosado-suave",
            name = "Rosado Suave",
            collection = "Pele e Retrato",
            recipe = Recipe(
                highlights = -10f,
                temperature = 10f,
                tint = 14f,
                fade = 10f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(saturation = 8f),
                    CH_MAGENTA to HslChannel(saturation = 10f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-retrato-noturno",
            name = "Retrato Noturno",
            collection = "Pele e Retrato",
            recipe = Recipe(
                exposure = -0.3f,
                contrast = 12f,
                blacks = -12f,
                temperature = -8f,
                clarity = 10f,
                vignette = 18f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(luminance = -6f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-mar-azul",
            name = "Mar Azul",
            collection = "Praia e Verão",
            recipe = Recipe(
                highlights = -15f,
                temperature = -8f,
                vibrance = 15f,
                clarity = 8f,
                hsl = hslChannels(
                    CH_CYAN to HslChannel(saturation = 20f),
                    CH_BLUE to HslChannel(luminance = -8f, saturation = 25f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-areia-clara",
            name = "Areia Clara",
            collection = "Praia e Verão",
            recipe = Recipe(
                exposure = 0.35f,
                highlights = -20f,
                whites = 15f,
                temperature = 15f,
                saturation = -8f,
                fade = 15f,
                hsl = hslChannels(
                    CH_YELLOW to HslChannel(saturation = -10f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-por-do-sol",
            name = "Pôr do Sol",
            collection = "Praia e Verão",
            recipe = Recipe(
                contrast = 8f,
                highlights = -30f,
                temperature = 35f,
                tint = 10f,
                vignette = 12f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(saturation = 18f),
                    CH_YELLOW to HslChannel(hue = -12f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-tropical",
            name = "Tropical",
            collection = "Praia e Verão",
            recipe = Recipe(
                contrast = 10f,
                temperature = 8f,
                vibrance = 20f,
                saturation = 18f,
                hsl = hslChannels(
                    CH_GREEN to HslChannel(saturation = 22f),
                    CH_BLUE to HslChannel(saturation = 15f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-agua-fresca",
            name = "Água Fresca",
            collection = "Praia e Verão",
            recipe = Recipe(
                exposure = 0.15f,
                temperature = -18f,
                tint = -8f,
                clarity = 12f,
                hsl = hslChannels(
                    CH_CYAN to HslChannel(saturation = 15f),
                    CH_BLUE to HslChannel(luminance = 8f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-sol-de-meio-dia",
            name = "Sol de Meio-Dia",
            collection = "Praia e Verão",
            recipe = Recipe(
                exposure = -0.2f,
                contrast = 12f,
                highlights = -35f,
                whites = -10f,
                saturation = 5f,
                clarity = 10f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-urbano-frio",
            name = "Urbano Frio",
            collection = "Cidade e Rua",
            recipe = Recipe(
                contrast = 14f,
                temperature = -22f,
                tint = -4f,
                saturation = -12f,
                fade = 6f,
                hsl = hslChannels(
                    CH_BLUE to HslChannel(saturation = 8f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-cinza-urbano",
            name = "Cinza Urbano",
            collection = "Cidade e Rua",
            recipe = Recipe(
                contrast = 10f,
                blacks = -8f,
                saturation = -35f,
                clarity = 8f,
                grain = 15f,
                fade = 18f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-neon-da-noite",
            name = "Neon da Noite",
            collection = "Cidade e Rua",
            recipe = Recipe(
                exposure = -0.2f,
                contrast = 18f,
                blacks = -18f,
                saturation = 15f,
                clarity = 12f,
                vignette = 15f,
                hsl = hslChannels(
                    CH_BLUE to HslChannel(saturation = 20f),
                    CH_MAGENTA to HslChannel(saturation = 15f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-concreto",
            name = "Concreto",
            collection = "Cidade e Rua",
            recipe = Recipe(
                contrast = 8f,
                temperature = -6f,
                saturation = -20f,
                clarity = 18f,
                grain = 20f,
                fade = 8f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-rua-dourada",
            name = "Rua Dourada",
            collection = "Cidade e Rua",
            recipe = Recipe(
                contrast = 10f,
                highlights = -20f,
                temperature = 22f,
                vignette = 10f,
                fade = 10f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(saturation = 12f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-mesa-clara",
            name = "Mesa Clara",
            collection = "Comida e Produto",
            recipe = Recipe(
                exposure = 0.3f,
                highlights = -15f,
                whites = 20f,
                temperature = 8f,
                vibrance = 12f,
                clarity = 8f,
                fade = 8f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-doce-quente",
            name = "Doce Quente",
            collection = "Comida e Produto",
            recipe = Recipe(
                contrast = 6f,
                temperature = 20f,
                saturation = 10f,
                vignette = 8f,
                hsl = hslChannels(
                    CH_RED to HslChannel(saturation = 10f),
                    CH_ORANGE to HslChannel(saturation = 14f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-fresco-verde",
            name = "Fresco Verde",
            collection = "Comida e Produto",
            recipe = Recipe(
                exposure = 0.1f,
                temperature = -6f,
                vibrance = 15f,
                clarity = 12f,
                hsl = hslChannels(
                    CH_YELLOW to HslChannel(saturation = 10f),
                    CH_GREEN to HslChannel(saturation = 25f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-cafe-aconchego",
            name = "Café Aconchego",
            collection = "Comida e Produto",
            recipe = Recipe(
                contrast = 8f,
                temperature = 25f,
                tint = 6f,
                vignette = 12f,
                grain = 10f,
                fade = 12f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(saturation = 10f),
                ),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-preto-profundo",
            name = "Preto Profundo",
            collection = "Preto e Branco",
            recipe = Recipe(
                contrast = 25f,
                whites = 10f,
                blacks = -25f,
                saturation = -100f,
                clarity = 12f,
                curve = ToneCurve(master = ToneCurve.PRESET_CONTRAST),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-filme-antigo",
            name = "Filme Antigo",
            collection = "Preto e Branco",
            recipe = Recipe(
                contrast = -5f,
                saturation = -100f,
                vignette = 15f,
                grain = 30f,
                fade = 30f,
                curve = ToneCurve(master = ToneCurve.PRESET_FADE),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-cinza-azulado",
            name = "Cinza Azulado",
            collection = "Preto e Branco",
            recipe = Recipe(
                contrast = 12f,
                blacks = -10f,
                temperature = -25f,
                saturation = -100f,
                clarity = 8f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-filme-400",
            name = "Filme 400",
            collection = "Filme e Vintage",
            recipe = Recipe(
                contrast = -8f,
                temperature = 8f,
                saturation = -8f,
                grain = 22f,
                fade = 20f,
                curve = ToneCurve(master = ToneCurve.PRESET_GENTLE),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-desbotado-90s",
            name = "Desbotado 90s",
            collection = "Filme e Vintage",
            recipe = Recipe(
                contrast = -12f,
                highlights = -10f,
                temperature = 5f,
                saturation = -15f,
                grain = 18f,
                fade = 28f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-sepia-leve",
            name = "Sépia Leve",
            collection = "Filme e Vintage",
            recipe = Recipe(
                contrast = -5f,
                temperature = 30f,
                tint = 8f,
                saturation = -25f,
                vignette = 10f,
                grain = 12f,
                fade = 15f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-polaroid",
            name = "Polaroid",
            collection = "Filme e Vintage",
            recipe = Recipe(
                exposure = 0.2f,
                contrast = -10f,
                whites = 10f,
                temperature = 12f,
                saturation = -10f,
                vignette = 18f,
                fade = 25f,
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-analogico-quente",
            name = "Analógico Quente",
            collection = "Filme e Vintage",
            recipe = Recipe(
                contrast = -6f,
                temperature = 18f,
                saturation = -5f,
                grain = 25f,
                fade = 15f,
                hsl = hslChannels(
                    CH_ORANGE to HslChannel(saturation = 8f),
                ),
                curve = ToneCurve(master = ToneCurve.PRESET_GENTLE),
            ),
            isBuiltIn = true
        ),
        Preset(
            id = "builtin-fade-cinza",
            name = "Fade Cinza",
            collection = "Filme e Vintage",
            recipe = Recipe(
                contrast = -15f,
                blacks = 10f,
                saturation = -30f,
                grain = 10f,
                fade = 35f,
                curve = ToneCurve(master = ToneCurve.PRESET_FADE),
            ),
            isBuiltIn = true
        )
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
