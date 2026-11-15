package app.lume.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Banco local (Room). Guarda receitas e rascunhos — nunca a foto
 * original (ela fica na galeria do usuário; o rascunho guarda só a URI).
 *
 * Versão 1. Migrações futuras devem ser escritas explicitamente; o
 * fallback destrutivo usado na Fase 0 (ver AppContainer) é aceitável
 * APENAS antes do lançamento e deve sair antes da Play Store.
 */

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val collection: String,
    val recipeJson: String,
    val isBuiltIn: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val photoUri: String,
    val recipeJson: String,
    val updatedAtMillis: Long
)

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PresetEntity?

    @Query("SELECT COUNT(*) FROM presets WHERE isBuiltIn = 0")
    fun observeUserPresetCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteUserPreset(id: String)

    @Query("DELETE FROM presets WHERE isBuiltIn = 0")
    suspend fun deleteAllUserPresets()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DraftEntity?

    @Query("SELECT * FROM drafts WHERE photoUri = :photoUri LIMIT 1")
    suspend fun getByPhotoUri(photoUri: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM drafts")
    suspend fun deleteAll()
}

@Database(
    entities = [PresetEntity::class, DraftEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LumeDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun draftDao(): DraftDao
}
