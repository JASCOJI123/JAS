@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.jas.montaj.platform

import platform.Foundation.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi

actual fun storage(): Storage = IosStorage

private object IosStorage : Storage {
    override val projectsDir: String by lazy {
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        "$documents/projects".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    override fun read(path: String): String? =
        NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)

    override fun write(path: String, text: String) {
        // atomically = true: yozish yarim qolsa ham eski fayl buzilmaydi.
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    override fun listFiles(dir: String): List<String> =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)?.map { "$dir/$it" } ?: emptyList()

    override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
}
