package com.tusharx.friday

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class StrP(val sp: SharedPreferences, val k: String, val d: String) : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String = sp.getString(k, d) ?: d
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp.edit().putString(k, value).apply() }
}
private class IntP(val sp: SharedPreferences, val k: String, val d: Int) : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = sp.getInt(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp.edit().putInt(k, value).apply() }
}
private class FloatP(val sp: SharedPreferences, val k: String, val d: Float) : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Float = sp.getFloat(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { sp.edit().putFloat(k, value).apply() }
}
private class BoolP(val sp: SharedPreferences, val k: String, val d: Boolean) : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp.edit().putBoolean(k, value).apply() }
}

/** All user settings. The Gemini API key lives ONLY here (entered in the Settings screen), never in source code. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

    var apiKey by StrP(sp, "apiKey", "")
    var model by StrP(sp, "model", "gemini-3.8-flash")
    var bossName by StrP(sp, "bossName", "Boss")
    var ownerName by StrP(sp, "ownerName", "GM Tusharx")
    var passphrase by StrP(sp, "passphrase", "")          // blank = everyone is treated as the owner
    var guestControl by BoolP(sp, "guestControl", false)   // can guests control the phone?
    var personality by StrP(sp, "personality", "Friendly") // Friendly / Funny / Professional / Desi Swag
    var language by StrP(sp, "language", "Hinglish")       // Hinglish / Hindi / English
    var extra by StrP(sp, "extra", "")                     // extra instructions for the AI
    var rate by FloatP(sp, "rate", 0.95f)
    var pitch by FloatP(sp, "pitch", 0.8f)
    var silenceSec by IntP(sp, "silenceSec", 60)
    var proactive by BoolP(sp, "proactive", true)
    var autoSpeak by BoolP(sp, "autoSpeak", true)
    var voiceName by StrP(sp, "voiceName", "")             // chosen phone TTS voice ("" = auto)
    var useEleven by BoolP(sp, "useEleven", false)         // optional ElevenLabs voice
    var elevenKey by StrP(sp, "elevenKey", "")
    var elevenVoiceId by StrP(sp, "elevenVoiceId", "")
}
