package com.jas.montaj.platform

import java.io.File

actual fun storage(): Storage = AndroidStorage

private object AndroidStorage : Storage {
    override val projectsDir: String
        get() = File(AndroidPlatform.context.filesDir, "projects").apply { mkdirs() }.absolutePath

    override fun read(path: String): String? = File(path).takeIf { it.exists() }?.readText()

    override fun write(path: String, text: String) {
        // Avval vaqtinchalik faylga yozib, keyin almashtiramiz — yozish yarim qolsa loyiha buzilmaydi.
        val target = File(path)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            target.delete()
            temporary.renameTo(target)
        }
    }

    override fun delete(path: String) {
        File(path).delete()
    }

    override fun listFiles(dir: String): List<String> = File(dir).listFiles()?.map { it.absolutePath } ?: emptyList()

    override fun nowMillis(): Long = System.currentTimeMillis()
}
