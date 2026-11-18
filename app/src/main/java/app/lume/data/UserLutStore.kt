package app.lume.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.lume.imaging.LutGrid
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Um LUT importado de um arquivo .cube pelo usuário: a grade
 * convertida vive como PNG 512×512 em arquivos do app, com um JSON
 * de metadados ao lado. Sem Room, sem migração — o LUT é um
 * arquivo, igual à miniatura de rascunho.
 *
 * O [id] é o SHA-256 do conteúdo do .cube: importar o mesmo arquivo
 * duas vezes dá o mesmo cartão, sem duplicar.
 */
@Serializable
data class UserLut(
    val id: String,
    val name: String,
    val fileName: String,
    val cubeSize: Int,
    val addedAtMillis: Long
)

/**
 * Guarda os LUTs importados em `filesDir/user_luts/`: `<id>.png` (a
 * grade, PNG sem perda — a grade precisa ser exata) + `<id>.json`
 * (metadados). Tudo melhor esforço: falhar aqui nunca quebra a
 * edição — o pior caso é o LUT não aparecer na lista.
 */
object UserLutStore {

    private const val DIR_NAME = "user_luts"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Todos os LUTs importados, do mais recente ao mais antigo. */
    fun list(context: Context): List<UserLut> {
        return try {
            val dir = dir(context)
            if (!dir.isDirectory) return emptyList()
            dir.listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.mapNotNull { file ->
                    try {
                        json.decodeFromString<UserLut>(file.readText())
                    } catch (e: Exception) {
                        null
                    }
                }
                // Sem o PNG, o cartão não serve: ignora o órfão.
                ?.filter { lut -> File(dir, "${lut.id}.png").isFile }
                ?.sortedByDescending { it.addedAtMillis }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Salva a grade convertida de um .cube. Se um LUT com o mesmo
     * [id] já existir, só atualiza os metadados (mesma grade).
     */
    fun save(
        context: Context,
        id: String,
        name: String,
        fileName: String,
        cubeSize: Int,
        grid: LutGrid
    ): UserLut? {
        return try {
            val dir = dir(context)
            if (!dir.exists()) dir.mkdirs()
            val pngFile = File(dir, "$id.png")
            val bitmap = grid.toBitmap()
            try {
                pngFile.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            } finally {
                bitmap.recycle()
            }
            val lut = UserLut(
                id = id,
                name = name,
                fileName = fileName,
                cubeSize = cubeSize,
                addedAtMillis = System.currentTimeMillis()
            )
            File(dir, "$id.json").writeText(json.encodeToString(lut))
            lut
        } catch (e: Exception) {
            null
        }
    }

    /** Carrega a grade de um LUT importado. Null se o arquivo sumiu. */
    fun loadGrid(context: Context, id: String): LutGrid? {
        return try {
            val pngFile = File(dir(context), "$id.png")
            if (!pngFile.isFile) return null
            val bitmap = BitmapFactory.decodeFile(pngFile.absolutePath)
                ?: return null
            try {
                LutGrid.fromBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Apaga o LUT importado (grade + metadados). */
    fun delete(context: Context, id: String): Boolean {
        return try {
            val dir = dir(context)
            val pngDeleted = File(dir, "$id.png").delete()
            val jsonDeleted = File(dir, "$id.json").delete()
            pngDeleted || jsonDeleted
        } catch (e: Exception) {
            false
        }
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)
}
