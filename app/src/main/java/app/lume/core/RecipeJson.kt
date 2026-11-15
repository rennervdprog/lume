package app.lume.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serialização JSON da receita/preset. Ponto único de encode/decode:
 * Room, exportação/importação de presets e (futuro) sync passam aqui.
 *
 * Regras: nunca estourar em JSON corrompido — decode inválido retorna
 * null e o chamador usa a receita identidade. Sem `!!` neste caminho.
 */
object RecipeJson {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encodeRecipe(recipe: Recipe): String = json.encodeToString(recipe)

    fun decodeRecipe(raw: String?): Recipe? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<Recipe>(raw).clamped()
        } catch (e: Exception) {
            null
        }
    }

    fun encodePreset(preset: Preset): String = json.encodeToString(preset)

    fun decodePreset(raw: String?): Preset? {
        if (raw.isNullOrBlank()) return null
        return try {
            val decoded = json.decodeFromString<Preset>(raw)
            decoded.copy(recipe = decoded.recipe.clamped())
        } catch (e: Exception) {
            null
        }
    }

    fun encodePresetList(presets: List<Preset>): String =
        json.encodeToString(presets)

    fun decodePresetList(raw: String?): List<Preset>? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<List<Preset>>(raw)
                .map { it.copy(recipe = it.recipe.clamped()) }
        } catch (e: Exception) {
            null
        }
    }
}
