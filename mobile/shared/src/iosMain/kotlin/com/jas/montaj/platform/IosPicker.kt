@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.jas.montaj.platform

import platform.CoreGraphics.*
import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.jas.montaj.core.newId
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify

@Composable
actual fun rememberMediaPicker(kind: PickKind, multiple: Boolean, onPicked: (List<String>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    // ObjC delegate'ni kuchsiz ushlaydi — shuning uchun o'zimiz saqlab turamiz.
    val delegates = remember { PickerDelegates(kind) { paths -> callback.value(paths) } }
    return remember(kind, multiple) { { delegates.present(multiple) } }
}

private class PickerDelegates(private val kind: PickKind, private val deliver: (List<String>) -> Unit) {
    private val photoDelegate = PhotoPickerDelegate(kind, ::finish)
    private val documentDelegate = DocumentPickerDelegate(::finish)

    fun present(multiple: Boolean) {
        val top = topViewController() ?: return
        if (kind == PickKind.AUDIO) {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio), asCopy = true)
            picker.delegate = documentDelegate
            picker.allowsMultipleSelection = false
            top.presentViewController(picker, animated = true, completion = null)
            return
        }
        val configuration = PHPickerConfiguration()
        configuration.filter = if (kind == PickKind.VIDEO) PHPickerFilter.videosFilter() else PHPickerFilter.imagesFilter()
        configuration.selectionLimit = if (multiple) 0 else 1
        val picker = PHPickerViewController(configuration = configuration)
        picker.delegate = photoDelegate
        top.presentViewController(picker, animated = true, completion = null)
    }

    private fun finish(paths: List<String>) {
        dispatch_async(dispatch_get_main_queue()) { if (paths.isNotEmpty()) deliver(paths) }
    }
}

private class PhotoPickerDelegate(
    private val kind: PickKind,
    private val finish: (List<String>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return
        val typeIdentifier = if (kind == PickKind.VIDEO) "public.movie" else "public.image"
        val paths = arrayOfNulls<String>(results.size)
        val group = dispatch_group_create()
        results.forEachIndexed { index, result ->
            dispatch_group_enter(group)
            result.itemProvider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, _ ->
                // Vaqtinchalik fayl faqat shu callback ichida mavjud — darhol nusxalaymiz.
                paths[index] = url?.let { copyIntoMediaDir(it, kind) }
                dispatch_group_leave(group)
            }
        }
        dispatch_group_notify(group, dispatch_get_main_queue()) { finish(paths.filterNotNull()) }
    }
}

private class DocumentPickerDelegate(private val finish: (List<String>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val paths = didPickDocumentsAtURLs.filterIsInstance<NSURL>().mapNotNull { copyIntoMediaDir(it, PickKind.AUDIO) }
        finish(paths)
    }
}

/** Faylni ilovaning doimiy papkasiga nusxalaydi (rasmlar 2048 px gacha kichraytirilib PNG qilinadi). */
private fun copyIntoMediaDir(url: NSURL, kind: PickKind): String? {
    val sourcePath = url.path ?: return null
    if (kind == PickKind.IMAGE) return importImage(sourcePath)
    val extension = url.pathExtension?.takeIf { it.isNotEmpty() } ?: if (kind == PickKind.VIDEO) "mov" else "m4a"
    val destination = "${IosMediaBackend.mediaDir}/${newId()}.$extension"
    val copied = NSFileManager.defaultManager.copyItemAtPath(sourcePath, toPath = destination, error = null)
    return destination.takeIf { copied }
}

private fun importImage(sourcePath: String, maxDimension: Double = 2048.0): String? {
    val image = UIImage.imageWithContentsOfFile(sourcePath) ?: return null
    var width = 0.0
    var height = 0.0
    image.size.useContents {
        width = this.width
        height = this.height
    }
    val scale = minOf(1.0, maxDimension / maxOf(width, height))
    val targetWidth = (width * scale).toInt().toDouble()
    val targetHeight = (height * scale).toInt().toDouble()
    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = 1.0
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    val resized = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight)) }
    val data = UIImagePNGRepresentation(resized) ?: return null
    val destination = "${IosMediaBackend.mediaDir}/${newId()}.png"
    return destination.takeIf { data.writeToFile(destination, atomically = true) }
}
