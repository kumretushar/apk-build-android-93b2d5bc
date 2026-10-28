package com.tusharx.friday

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Status { IDLE, LISTENING, THINKING, SPEAKING }

data class Msg(
    val fromUser: Boolean,
    val text: String,
    val code: String = "",
    val codeLang: String = "",
    val note: String = ""
)

class FridayViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val phone = PhoneControl(app)
    val voice = VoiceEngine(app, prefs)

    val messages = mutableStateListOf<Msg>()
    var status by mutableStateOf(Status.IDLE); private set
    var partial by mutableStateOf(""); private set
    var live by mutableStateOf(false); private set
    var unlocked by mutableStateOf(false); private set
    var apiTest by mutableStateOf(""); private set
    var level by mutableStateOf(0f); private set
    var voiceNote by mutableStateOf(""); private set
    var foreground = false

    val isBoss: Boolean get() = prefs.passphrase.isBlank() || unlocked

    private val history = mutableListOf<Gemini.Turn>()
    private var lastActivity = SystemClock.elapsedRealtime()
    private var proactiveCount = 0

    init {
        voice.onPartial = { partial = it; touch() }
        voice.onFinal = { partial = ""; send(it) }
        voice.onSpeakDone = { afterSpeak() }
        voice.onLevel = { level = level * 0.5f + it * 0.5f }
        voice.onNote = { voiceNote = it }
        voice.onListenError = { code ->
            when {
                code == 9 || code == -1 -> {
                    live = false; status = Status.IDLE
                    messages.add(Msg(false, "Mic permission ya speech service nahi mili. Permission allow karo aur Google app/Speech services check karo."))
                }
                live && status == Status.LISTENING -> viewModelScope.launch {
                    delay(500)
                    if (live && status == Status.LISTENING) voice.listen()
                }
                status == Status.LISTENING -> status = Status.IDLE
            }
        }
        proactiveLoop()
    }

    private fun touch() { lastActivity = SystemClock.elapsedRealtime() }

    /** Called for typed text and for recognised speech. */
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        touch(); proactiveCount = 0
        voice.stopSpeaking()
        voice.stopListening()
        status = Status.THINKING
        messages.add(Msg(true, t))
        val low = t.lowercase()

        if (prefs.passphrase.isNotBlank() && low.contains(prefs.passphrase.lowercase())) {
            unlocked = true
            val r = "Verified. Welcome back, ${prefs.bossName}!"
            messages.add(Msg(false, r)); deliver(r); return
        }
        if (low.contains("friday lock") || low.contains("lock friday")) {
            unlocked = false
            val r = "Locked. Ab main sirf friendly guest mode mein hoon."
            messages.add(Msg(false, r)); deliver(r); return
        }
        viewModelScope.launch { think(t) }
    }

    private suspend fun think(userText: String) {
        status = Status.THINKING
        history.add(Gemini.Turn("user", userText))
        try {
            val raw = Gemini.generate(prefs, Agent.systemPrompt(prefs, isBoss), trimmed())
            history.add(Gemini.Turn("model", raw))
            val r = Agent.parse(raw)
            val notes = mutableListOf<String>()
            if (r.actions.isNotEmpty()) {
                if (isBoss || prefs.guestControl) r.actions.forEach { notes.add(phone.run(it)) }
                else notes.add("Guest mode: phone control band hai")
            }
            messages.add(Msg(false, r.speech, r.code, r.codeLang, notes.joinToString("  •  ")))
            deliver(r.speech)
        } catch (e: Exception) {
            history.removeLastOrNull()
            live = false
            status = Status.IDLE
            messages.add(Msg(false, "⚠️ ${e.message ?: "Error"}"))
        }
    }

    private fun trimmed(): List<Gemini.Turn> = history.takeLast(20).dropWhile { it.role == "model" }

    private fun deliver(speech: String) {
        if (prefs.autoSpeak && speech.isNotBlank()) {
            status = Status.SPEAKING
            voice.speak(speech)
        } else afterSpeak()
    }

    private fun afterSpeak() {
        touch()
        if (live) startListen() else status = Status.IDLE
    }

    private fun startListen() { status = Status.LISTENING; partial = ""; voice.listen() }

    fun toggleLive() {
        if (live) {
            live = false; voice.stopListening(); voice.stopSpeaking(); status = Status.IDLE; partial = ""
        } else {
            live = true; touch(); voice.stopSpeaking(); startListen()
        }
    }

    fun pauseVoice() {
        live = false; voice.stopListening(); voice.stopSpeaking()
        if (status != Status.THINKING) status = Status.IDLE
    }

    /** Speaks up by itself after the user stays silent for the configured time. */
    private fun proactiveLoop() = viewModelScope.launch {
        while (true) {
            delay(3000)
            if (!foreground || !prefs.proactive || prefs.apiKey.isBlank()) continue
            if (status == Status.THINKING || status == Status.SPEAKING) continue
            val limit = prefs.silenceSec * 1000L * (1 + proactiveCount)   // backs off if you keep ignoring it
            if (SystemClock.elapsedRealtime() - lastActivity >= limit) {
                proactiveCount++; touch()
                proactiveSpeak()
            }
        }
    }

    private suspend fun proactiveSpeak() {
        voice.stopListening()
        status = Status.THINKING
        try {
            val nudge = "[SYSTEM: User ${prefs.silenceSec} second se chup hai. Ab tum khud se ek chhoti friendly baat shuru karo " +
                "(check-in, halka mazaak, suggestion ya koi interesting fact). Pehle jaisi baat repeat mat karo. actions khali rakho.]"
            history.add(Gemini.Turn("user", nudge))
            val raw = Gemini.generate(prefs, Agent.systemPrompt(prefs, isBoss), trimmed())
            history.add(Gemini.Turn("model", raw))
            val r = Agent.parse(raw)
            messages.add(Msg(false, r.speech))
            deliver(r.speech)
        } catch (e: Exception) {
            history.removeLastOrNull()
            if (live) startListen() else status = Status.IDLE
        }
    }

    fun testVoice() {
        voice.stopListening()
        voiceNote = ""
        status = Status.SPEAKING
        voice.speak("Hello ${prefs.bossName}, main Friday hoon. Meri awaaz kaisi lag rahi hai?")
    }

    fun applyVoiceSettings() = voice.applyVoice()

    fun testApi() {
        apiTest = "Testing..."
        viewModelScope.launch {
            apiTest = try {
                val r = Gemini.generate(prefs, "Reply with one short friendly word.", listOf(Gemini.Turn("user", "hi")), json = false)
                "✅ Working: ${r.trim().take(60)}"
            } catch (e: Exception) { "❌ ${e.message}" }
        }
    }

    fun clearChat() { messages.clear(); history.clear() }

    override fun onCleared() { voice.shutdown() }
}
