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

actual suspend fun httpPostMultipart(
    url: String,
    headers: Map<String, String>,
    fields: Map<String, String>,
    fileField: String,
    filePath: String,
    fileName: String,
    mimeType: String,
): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val boundary = "----Montaj${System.currentTimeMillis()}"
    val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 300_000
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        headers.forEach { (key, value) -> setRequestProperty(key, value) }
        setChunkedStreamingMode(64 * 1024)
    }
    try {
        connection.outputStream.buffered().use { out ->
            fields.forEach { (key, value) ->
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n".toByteArray())
            }
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray())
            File(filePath).inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        stream?.bufferedReader()?.use { it.readText() } ?: ""
    } finally {
        connection.disconnect()
    }
}
