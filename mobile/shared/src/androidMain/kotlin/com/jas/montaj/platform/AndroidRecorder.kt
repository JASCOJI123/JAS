package com.jas.montaj.platform

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import com.jas.montaj.core.newId
import java.io.File

internal class AndroidRecorder : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override fun start(): Boolean = try {
        val output = File(AndroidPlatform.mediaDir, "voice_${newId()}.m4a")
        recorder = MediaRecorder(AndroidPlatform.context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
        file = output
        true
    } catch (error: Exception) {
        recorder?.release()
        recorder = null
        false
    }

    override fun stop(): String? {
        val current = recorder ?: return null
        recorder = null
        return try {
            current.stop()
            file?.absolutePath
        } catch (error: RuntimeException) {
            // Juda qisqa yozuv: fayl yaroqsiz.
            file?.delete()
            null
        } finally {
            current.release()
        }
    }
}

@Composable
actual fun rememberMicrophonePermission(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> callback.value(granted) }
    val request: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(AndroidPlatform.context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) callback.value(true) else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    return request
}
