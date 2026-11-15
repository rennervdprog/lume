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
 * Presets iniciais ORIGINAIS, criados como receitas para este app.
 * Nomes, coleções e valores foram inventados aqui — não copiam presets
 * de nenhum concorrente nem de nenhum pack comercial.
 *
 * TODO (Fase 1, diferencial P0 do estudo): coleções testadas em pele e
 * luz brasileiras, com prova de teste (tons/luzes) na ficha do preset.
 * Estes 6 são o ponto de partida honesto, não a coleção final.
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
        )
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
