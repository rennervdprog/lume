package app.lume.data

import app.lume.core.BuiltInPresets
import app.lume.core.Preset
import app.lume.core.ProPolicy
import app.lume.core.Recipe
import app.lume.core.RecipeJson
import app.lume.data.local.PresetDao
import app.lume.data.local.PresetEntity
import app.lume.data.pro.EntitlementRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Presets do usuário (Room) + presets embutidos (código).
 *
 * Regra de limite do plano grátis (estudo §4.4): até 10 presets próprios
 * sem Pro. A verificação vive aqui — a UI só reage ao resultado.
 * Presets embutidos não contam para o limite e não podem ser apagados.
 */
interface PresetRepository {
    /** Embutidos + do usuário, ordenados: embutidos primeiro, depois nome. */
    val presets: Flow<List<Preset>>
    val userPresetCount: Flow<Int>

    /** Salva um preset próprio. Falha com [PresetLimitReached] sem Pro. */
    suspend fun saveUserPreset(name: String, collection: String, recipe: Recipe): Result<Preset>

    suspend fun deleteUserPreset(id: String)

    suspend fun getById(id: String): Preset?
}

class PresetLimitReached(val limit: Int) : Exception(
    "Limite de $limit presets no plano grátis."
)

class LocalPresetRepository(
    private val presetDao: PresetDao,
    private val entitlements: EntitlementRepository
) : PresetRepository {

    override val presets: Flow<List<Preset>> = presetDao.observeAll()
        .map { entities ->
            val stored = entities.mapNotNull { it.toPreset() }
            val storedIds = stored.map { it.id }.toSet()
            // Embutidos vivem no código; se um dia forem persistidos, a
            // versão do banco vence (evita duplicar o mesmo id).
            val missingBuiltIns = BuiltInPresets.all.filter { it.id !in storedIds }
            (missingBuiltIns + stored).sortedWith(
                compareByDescending<Preset> { it.isBuiltIn }.thenBy { it.name }
            )
        }

    override val userPresetCount: Flow<Int> = presetDao.observeUserPresetCount()

    override suspend fun saveUserPreset(
        name: String,
        collection: String,
        recipe: Recipe
    ): Result<Preset> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("O preset precisa de um nome."))
        }
        val count = presetDao.observeUserPresetCount().first()
        val limit = ProPolicy.ownPresetLimit(entitlements.isPro.value)
        if (count >= limit) {
            return Result.failure(PresetLimitReached(limit))
        }
        val now = System.currentTimeMillis()
        val preset = Preset(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            collection = collection.trim().ifBlank { "Meus presets" },
            recipe = recipe.clamped(),
            isBuiltIn = false,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        presetDao.upsert(preset.toEntity())
        return Result.success(preset)
    }

    override suspend fun deleteUserPreset(id: String) {
        presetDao.deleteUserPreset(id)
    }

    override suspend fun getById(id: String): Preset? {
        BuiltInPresets.byId(id)?.let { return it }
        return presetDao.getById(id)?.toPreset()
    }

    private fun PresetEntity.toPreset(): Preset? {
        val recipe = RecipeJson.decodeRecipe(recipeJson) ?: return null
        return Preset(
            id = id,
            name = name,
            collection = collection,
            recipe = recipe,
            isBuiltIn = isBuiltIn,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun Preset.toEntity(): PresetEntity = PresetEntity(
        id = id,
        name = name,
        collection = collection,
        recipeJson = RecipeJson.encodeRecipe(recipe),
        isBuiltIn = isBuiltIn,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

/** Combina presets + estado Pro para a UI decidir o que exibir. */
fun PresetRepository.presetsWithPro(
    entitlements: EntitlementRepository
): Flow<Pair<List<Preset>, Boolean>> =
    combine(presets, entitlements.isPro) { list, isPro -> list to isPro }
