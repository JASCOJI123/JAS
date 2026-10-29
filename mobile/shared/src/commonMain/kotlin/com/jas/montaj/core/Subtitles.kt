package com.jas.montaj.core

import com.jas.montaj.platform.Storage
import com.jas.montaj.platform.httpPostMultipart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ilova sozlamalari (faqat shu telefonda saqlanadi). */
@Serializable
data class AppSettings(
    /** Avto-subtitr uchun foydalanuvchining o'z OpenAI API kaliti. */
    val openAiKey: String = "",
    val subtitleLanguage: String = "uz",
)

class SettingsStore(private val storage: Storage) {
    private val json = Json { ignoreUnknownKeys = true }
    private val path get() = "${storage.projectsDir}/settings.json"

    fun load(): AppSettings = storage.read(path)?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()

    fun save(settings: AppSettings) = storage.write(path, json.encodeToString(AppSettings.serializer(), settings))
}

data class SpeechSegment(val start: Double, val end: Double, val text: String)

/** Nutqni matnga aylantirish (OpenAI Whisper, segment vaqtlari bilan). */
object Transcriber {
    private val json = Json { ignoreUnknownKeys = true }

    val languages = listOf("uz" to "O'zbek", "ru" to "Rus", "en" to "Ingliz", "" to "Avto")

    @Serializable
    private data class Response(val segments: List<Segment> = emptyList(), val text: String = "")

    @Serializable
    private data class Segment(val start: Double, val end: Double, val text: String)

    suspend fun transcribe(audioPath: String, apiKey: String, language: String): List<SpeechSegment> {
        val fields = buildMap {
            put("model", "whisper-1")
            put("response_format", "verbose_json")
            if (language.isNotBlank()) put("language", language)
        }
        val body = httpPostMultipart(
            url = "https://api.openai.com/v1/audio/transcriptions",
            headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}"),
            fields = fields,
            fileField = "file",
            filePath = audioPath,
            fileName = "audio.m4a",
            mimeType = "audio/mp4",
        )
        val response = runCatching { json.decodeFromString<Response>(body) }.getOrElse {
            error(errorMessage(body))
        }
        return response.segments.filter { it.text.isNotBlank() }.map { SpeechSegment(it.start, it.end, it.text.trim()) }
    }

    private fun errorMessage(body: String): String {
        val message = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        return message ?: "Server javobini tushunib bo'lmadi"
    }
}
