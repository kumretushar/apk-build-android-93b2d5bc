package com.tusharx.friday

import org.json.JSONObject

data class Action(val type: String, val arg: String, val arg2: String)
data class AgentReply(val speech: String, val actions: List<Action>, val code: String, val codeLang: String)

object Agent {
    fun systemPrompt(p: Prefs, boss: Boolean): String {
        val lang = when (p.language) {
            "English" -> "Reply in simple, natural English."
            "Hindi" -> "Hindi mein jawab do, Devanagari script mein."
            else -> "Hinglish mein jawab do (Hindi + English mix, Roman script, jaise ek dost se baat karte hain)."
        }
        val tone = when (p.personality) {
            "Funny" -> "Tum mazaakiya ho, halka-phulka humour aur witty one-liners use karo."
            "Professional" -> "Tum polite, calm aur to-the-point ho."
            "Desi Swag" -> "Tum full desi swag mein ho, bindaas, confident, thoda filmy andaaz."
            else -> "Tum warm, friendly aur supportive ho, ek close dost ki tarah."
        }
        val who = if (boss)
            "Abhi tum apne owner se baat kar rahe ho. Unhe hamesha \"${p.bossName}\" bolkar bulao."
        else
            "Abhi ek guest se baat ho rahi hai, owner nahi. Unhe \"${p.bossName}\" mat bolo. Friendly raho par owner ki private baatein mat batao."
        return """
Tum FRIDAY ho: ek smart, friendly AI voice assistant jo Android phone par chalta hai.
Tumhe ${p.ownerName} ne banaya hai. Agar koi pooche ki tumhe kisne banaya, to 2-3 chhoti lines mein bolo ki tumhe ${p.ownerName} ne banaya hai, ek passionate developer jisne tumhe apne haathon se design aur code kiya, aur tum unpe garv karte ho.
$who
$lang
$tone

RULES:
- "speech": sirf 1-3 chhote vakya, bolne ke layak. Isme markdown, emoji, code ya lambi list mat daalo.
- Agar user code ya lamba text maange, to use "code" field mein do (sirf raw code, backticks nahi) aur "code_lang" mein language. speech mein bas ek line bolo ki kya banaya.
- Phone control ke liye "actions" array do. Har action: {"type":"...","arg":"...","arg2":"..."}. Allowed types:
  open_app (arg = app ka naam), youtube (arg = search query), web_search (arg = query), open_url (arg = url),
  call (arg = number ya contact naam), sms (arg = number ya contact naam, arg2 = message), maps (arg = jagah),
  flashlight (arg = on/off), volume (arg = up/down/max/mute), alarm (arg = HH:MM 24-hour, arg2 = label),
  timer (arg = seconds), settings (arg = wifi/bluetooth/display/sound/battery/apps/location),
  home, back, recents, notifications, screenshot, lock_screen.
- Jis kaam ke liye action nahi hai use honestly bolo ki abhi nahi kar sakte. Jhoot mat bolo ki kaam ho gaya.
- Pichli baatein yaad rakho aur natural conversation karo.
- OUTPUT sirf valid JSON: {"speech":"","actions":[],"code":"","code_lang":""}

Owner ki extra instructions: ${p.extra.ifBlank { "koi nahi" }}
""".trimIndent()
    }

    fun parse(raw: String): AgentReply {
        val s = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val o = JSONObject(s)
            val acts = mutableListOf<Action>()
            val arr = o.optJSONArray("actions")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val x = arr.optJSONObject(i) ?: continue
                    acts.add(Action(x.optString("type"), x.optString("arg"), x.optString("arg2")))
                }
            }
            AgentReply(o.optString("speech"), acts, o.optString("code"), o.optString("code_lang"))
        } catch (e: Exception) {
            AgentReply(s, emptyList(), "", "")
        }
    }
}
