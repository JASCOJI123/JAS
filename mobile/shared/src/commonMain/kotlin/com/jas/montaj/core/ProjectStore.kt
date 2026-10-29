package com.jas.montaj.core

import com.jas.montaj.montaj.MontajState
import com.jas.montaj.motion.LayerContent
import com.jas.montaj.motion.MotionProject
import com.jas.montaj.platform.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ProjectKind { MONTAJ, MOTION }

/** Ro'yxatda ko'rsatish uchun qisqa ma'lumot. */
@Serializable
data class ProjectInfo(
    val id: String,
    val name: String,
    val kind: ProjectKind,
    val updatedAt: Long,
    val durationSec: Double = 0.0,
    /** Bosh ekrandagi kartochka rasmi uchun video yoki rasm yo'li. */
    val coverPath: String? = null,
    val coverIsVideo: Boolean = true,
)

@Serializable
private data class StoredProject(
    val info: ProjectInfo,
    val montaj: MontajState? = null,
    val motion: MotionProject? = null,
)

@Serializable
private data class StoreIndex(val lastOpened: Map<ProjectKind, String> = emptyMap())

/**
 * Loyihalarni JSON fayllar sifatida ilovaning doimiy papkasida saqlaydi.
 * Media fayllar (video, rasm, musiqa) import paytida shu papkaga nusxalangan bo'ladi.
 */
class ProjectStore(private val storage: Storage) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val dir get() = storage.projectsDir
    private val indexPath get() = "$dir/index.json"

    fun list(kind: ProjectKind): List<ProjectInfo> = storage.listFiles(dir)
        .filter { it.endsWith(".json") && !it.endsWith("index.json") }
        .mapNotNull { path -> runCatching { json.decodeFromString<StoredProject>(storage.read(path) ?: return@mapNotNull null).info }.getOrNull() }
        .filter { it.kind == kind }
        .sortedByDescending { it.updatedAt }

    fun loadMontaj(id: String): MontajState? = load(id)?.montaj

    fun loadMotion(id: String): MotionProject? = load(id)?.motion

    fun saveMontaj(info: ProjectInfo, state: MontajState) = save(
        StoredProject(
            info.copy(updatedAt = storage.nowMillis(), durationSec = state.totalDuration, coverPath = state.clips.firstOrNull()?.path, coverIsVideo = true),
            montaj = state,
        ),
    )

    fun saveMotion(info: ProjectInfo, project: MotionProject): Unit {
        val cover = project.layers.firstNotNullOfOrNull { layer ->
            when (val content = layer.content) {
                is LayerContent.Video -> content.path to true
                is LayerContent.Image -> content.path to false
                else -> null
            }
        }
        save(
            StoredProject(
                info.copy(updatedAt = storage.nowMillis(), durationSec = project.duration, coverPath = cover?.first, coverIsVideo = cover?.second ?: true),
                motion = project,
            ),
        )
    }

    /** Bosh ekran uchun ikkala turdagi loyihalar. */
    fun listAll(): List<ProjectInfo> = (list(ProjectKind.MONTAJ) + list(ProjectKind.MOTION)).sortedByDescending { it.updatedAt }

    fun rename(id: String, name: String) {
        val stored = load(id) ?: return
        save(stored.copy(info = stored.info.copy(name = name)))
    }

    fun delete(id: String) {
        storage.delete(path(id))
    }

    fun lastOpened(kind: ProjectKind): String? = readIndex().lastOpened[kind]?.takeIf { load(it) != null }

    fun setLastOpened(kind: ProjectKind, id: String) {
        val index = readIndex()
        storage.write(indexPath, json.encodeToString(StoreIndex.serializer(), index.copy(lastOpened = index.lastOpened + (kind to id))))
    }

    fun newInfo(kind: ProjectKind): ProjectInfo {
        val prefix = if (kind == ProjectKind.MONTAJ) "Montaj" else "Motion"
        return ProjectInfo(newId(), "$prefix ${list(kind).size + 1}", kind, storage.nowMillis())
    }

    private fun load(id: String): StoredProject? =
        storage.read(path(id))?.let { text -> runCatching { json.decodeFromString<StoredProject>(text) }.getOrNull() }

    private fun save(project: StoredProject) {
        storage.write(path(project.info.id), json.encodeToString(StoredProject.serializer(), project))
    }

    private fun readIndex(): StoreIndex =
        storage.read(indexPath)?.let { runCatching { json.decodeFromString<StoreIndex>(it) }.getOrNull() } ?: StoreIndex()

    private fun path(id: String) = "$dir/$id.json"
}
