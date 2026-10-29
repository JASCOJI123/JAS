package com.jas.montaj.platform

/** Oddiy fayl amallari (loyihalarni saqlash uchun). */
interface Storage {
    /** Ilovaning doimiy papkasidagi loyihalar katalogi. */
    val projectsDir: String
    fun read(path: String): String?
    fun write(path: String, text: String)
    fun delete(path: String)
    fun listFiles(dir: String): List<String>
    fun nowMillis(): Long
}

expect fun storage(): Storage

/**
 * multipart/form-data POST (fayl yuklash bilan). Javob matnini qaytaradi;
 * HTTP xatosida ham javob tanasini qaytaradi (xabarni ko'rsatish uchun).
 */
expect suspend fun httpPostMultipart(
    url: String,
    headers: Map<String, String>,
    fields: Map<String, String>,
    fileField: String,
    filePath: String,
    fileName: String,
    mimeType: String,
): String
