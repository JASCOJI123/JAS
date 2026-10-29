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
