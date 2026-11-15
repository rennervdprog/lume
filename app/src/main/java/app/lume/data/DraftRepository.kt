package app.lume.data

import app.lume.core.Recipe
import app.lume.core.RecipeJson
import app.lume.data.local.DraftDao
import app.lume.data.local.DraftEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Rascunho = URI da foto + receita. A foto original fica na galeria. */
data class Draft(
    val id: String,
    val photoUri: String,
    val recipe: Recipe,
    val updatedAtMillis: Long
)

interface DraftRepository {
    val drafts: Flow<List<Draft>>
    suspend fun saveDraft(photoUri: String, recipe: Recipe): Draft
    suspend fun getDraft(id: String): Draft?
    suspend fun deleteDraft(id: String)
    suspend fun deleteAllDrafts()
}

class LocalDraftRepository(
    private val draftDao: DraftDao
) : DraftRepository {

    override val drafts: Flow<List<Draft>> = draftDao.observeAll()
        .map { entities -> entities.mapNotNull { it.toDraft() } }

    override suspend fun saveDraft(photoUri: String, recipe: Recipe): Draft {
        val existing = draftDao.getByPhotoUri(photoUri)
        val now = System.currentTimeMillis()
        val entity = DraftEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            photoUri = photoUri,
            recipeJson = RecipeJson.encodeRecipe(recipe.clamped()),
            updatedAtMillis = now
        )
        draftDao.upsert(entity)
        return Draft(
            id = entity.id,
            photoUri = photoUri,
            recipe = recipe.clamped(),
            updatedAtMillis = now
        )
    }

    override suspend fun getDraft(id: String): Draft? =
        draftDao.getById(id)?.toDraft()

    override suspend fun deleteDraft(id: String) {
        draftDao.delete(id)
    }

    override suspend fun deleteAllDrafts() {
        draftDao.deleteAll()
    }

    private fun DraftEntity.toDraft(): Draft? {
        val recipe = RecipeJson.decodeRecipe(recipeJson) ?: return null
        return Draft(
            id = id,
            photoUri = photoUri,
            recipe = recipe,
            updatedAtMillis = updatedAtMillis
        )
    }
}
