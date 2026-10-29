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

actual suspend fun httpPostMultipart(
    url: String,
    headers: Map<String, String>,
    fields: Map<String, String>,
    fileField: String,
    filePath: String,
    fileName: String,
    mimeType: String,
): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val boundary = "----Montaj${(NSDate().timeIntervalSince1970 * 1000).toLong()}"
    val body = NSMutableData()
    fun append(text: String) {
        NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)?.let { body.appendData(it) }
    }
    fields.forEach { (key, value) -> append("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n") }
    append("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\nContent-Type: $mimeType\r\n\r\n")
    NSData.dataWithContentsOfFile(filePath)?.let { body.appendData(it) }
    append("\r\n--$boundary--\r\n")

    val request = NSMutableURLRequest.requestWithURL(NSURL.URLWithString(url)!!)
    request.setHTTPMethod("POST")
    request.setTimeoutInterval(300.0)
    request.setValue("multipart/form-data; boundary=$boundary", forHTTPHeaderField = "Content-Type")
    headers.forEach { (key, value) -> request.setValue(value, forHTTPHeaderField = key) }
    request.setHTTPBody(body)

    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
        if (error != null && data == null) {
            continuation.resumeWith(Result.failure(IllegalStateException(error.localizedDescription)))
        } else {
            val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() } ?: ""
            continuation.resumeWith(Result.success(text))
        }
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}
