package com.tusharx.friday

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Ears (SpeechRecognizer) + voice (phone TTS, or optional ElevenLabs).
 * Call everything from the main thread.
 */
class VoiceEngine(private val app: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var player: MediaPlayer? = null
    private var token = 0

    var onPartial: (String) -> Unit = {}
    var onFinal: (String) -> Unit = {}
    var onListenError: (Int) -> Unit = {}
    var onSpeakDone: () -> Unit = {}
    var onLevel: (Float) -> Unit = {}
    var onNote: (String) -> Unit = {}

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        applyVoice()
    }

    private fun ttsLocale(): Locale = if (prefs.language == "Hindi") Locale("hi", "IN") else Locale("en", "IN")
    private fun sttLang(): String = if (prefs.language == "English") "en-IN" else "hi-IN"

    /** Voices installed on this phone (Hindi + English), Hindi first. */
    fun availableVoices(): List<Voice> {
        val t = tts ?: return emptyList()
        if (!ttsReady) return emptyList()
        return try {
            (t.voices ?: emptySet<Voice>())
                .filter { it.locale.language == "hi" || it.locale.language == "en" }
                .sortedWith(compareBy({ it.locale.language != "hi" }, { it.locale.toString() }, { it.name }))
        } catch (e: Exception) { emptyList() }
    }

    fun applyVoice() {
        val t = tts ?: return
        if (!ttsReady) return
        var set = false
        if (prefs.voiceName.isNotBlank()) {
            val v = try { t.voices?.firstOrNull { it.name == prefs.voiceName } } catch (e: Exception) { null }
            if (v != null) { t.setVoice(v); set = true }
        }
        if (!set) {
            val r = t.setLanguage(ttsLocale())
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.ENGLISH)
        }
        t.setSpeechRate(prefs.rate)
        t.setPitch(prefs.pitch)
    }

    // ---------------------------------------------------------------- speaking

    fun speak(text: String) {
        stopSpeaking()
        val my = token
        if (prefs.useEleven && prefs.elevenKey.isNotBlank() && prefs.elevenVoiceId.isNotBlank()) {
            speakEleven(text, my)
        } else {
            speakSystem(text)
        }
    }

    private fun speakSystem(text: String) {
        val t = tts
        if (t == null || !ttsReady) { onSpeakDone(); return }
        applyVoice()
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { onSpeakDone() } }
            override fun onError(utteranceId: String?) { main.post { onSpeakDone() } }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
    }

    private fun speakEleven(text: String, my: Int) {
        Thread {
            try {
                val url = URL("https://api.elevenlabs.io/v1/text-to-speech/" + prefs.elevenVoiceId.trim())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 40000
                conn.doOutput = true
                conn.setRequestProperty("xi-api-key", prefs.elevenKey.trim())
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "audio/mpeg")
                conn.outputStream.use { it.write(JSONObject().put("text", text).toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(160) ?: ""
                    throw RuntimeException("ElevenLabs $code: $err")
                }
                val f = File(app.cacheDir, "tts_" + System.nanoTime() + ".mp3")
                conn.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                main.post {
                    if (token != my) { f.delete(); return@post }
                    playFile(f, text, my)
                }
            } catch (e: Exception) {
                main.post {
                    if (token != my) return@post
                    onNote("ElevenLabs fail hua, phone ki voice use ho rahi hai. (${e.message})")
                    speakSystem(text)
                }
            }
        }.start()
    }

    private fun playFile(f: File, fallbackText: String, my: Int) {
        try {
            releasePlayer()
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(f.absolutePath)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { f.delete(); releasePlayer(); if (token == my) onSpeakDone() }
            mp.setOnErrorListener { _, _, _ -> f.delete(); releasePlayer(); if (token == my) speakSystem(fallbackText); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            f.delete()
            speakSystem(fallbackText)
        }
    }

    private fun releasePlayer() {
        try { player?.release() } catch (e: Exception) {}
        player = null
    }

    fun stopSpeaking() {
        token++
        tts?.stop()
        releasePlayer()
    }

    // ---------------------------------------------------------------- listening

    fun listen() {
        main.post {
            if (!SpeechRecognizer.isRecognitionAvailable(app)) { onListenError(-1); return@post }
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(app)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) { onListenError(error) }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) onListenError(SpeechRecognizer.ERROR_NO_MATCH) else onFinal(t)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) onPartial(t)
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLang())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            r.startListening(i)
        }
    }

    fun stopListening() { main.post { recognizer?.cancel() }; onLevel(0f) }

    fun shutdown() {
        recognizer?.destroy()
        releasePlayer()
        tts?.stop()
        tts?.shutdown()
    }
}
