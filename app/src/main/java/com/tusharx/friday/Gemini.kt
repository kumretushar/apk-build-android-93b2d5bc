package com.tusharx.friday

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    data class Turn(val role: String, val text: String) // role: "user" or "model"

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private fun isBusy(e: Exception): Boolean {
        val m = e.message ?: ""
        return m.startsWith("Gemini error 503") || m.startsWith("Gemini error 429") ||
            m.startsWith("Gemini error 500") || m.startsWith("Gemini error 504") ||
            e is java.io.IOException
    }

    /**
     * 1) Tries your chosen model, retrying a few times when Google is busy.
     * 2) If it is still busy, automatically asks Google which other "flash" models exist
     *    for your key and tries them, so one overloaded model never blocks Friday.
     */
    suspend fun generate(prefs: Prefs, system: String, turns: List<Turn>, json: Boolean = true): String {
        if (prefs.apiKey.isBlank()) throw RuntimeException("Gemini API key missing. Settings mein key daalo.")
        var last: Exception
        try {
            return withRetry(prefs, prefs.model, system, turns, json, 4)
        } catch (e: Exception) {
            if (!isBusy(e)) throw e
            last = e
        }
        for (m in listFlashModels(prefs).filter { it != prefs.model }.take(4)) {
            try {
                return withRetry(prefs, m, system, turns, json, 1)
            } catch (e: Exception) {
                last = e
            }
        }
        throw RuntimeException("Google ke servers abhi bahut busy hain. Thodi der baad try karo. (${last.message})")
    }

    private suspend fun withRetry(prefs: Prefs, model: String, system: String, turns: List<Turn>, json: Boolean, tries: Int): String {
        var attempt = 0
        while (true) {
            try {
                return once(prefs, model, system, turns, json)
            } catch (e: Exception) {
                attempt++
                if (!isBusy(e) || attempt >= tries) throw e
                delay(1500L * attempt)
            }
        }
    }

    private suspend fun once(prefs: Prefs, model: String, system: String, turns: List<Turn>, json: Boolean): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            for (t in turns) {
                contents.put(
                    JSONObject().put("role", t.role)
                        .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
                )
            }
            val gen = JSONObject().put("temperature", 0.9)
            if (json) gen.put("responseMimeType", "application/json")
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", gen)

            val conn = URL("$BASE/models/$model:generateContent").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", prefs.apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { text.take(200) }
                throw RuntimeException("Gemini error $code: $msg")
            }
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            sb.toString()
        }

    private suspend fun listFlashModels(prefs: Prefs): List<String> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$BASE/models?pageSize=100").openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("x-goog-api-key", prefs.apiKey)
            if (conn.responseCode !in 200..299) return@withContext emptyList<String>()
            val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("models")
            val skip = listOf("image", "tts", "live", "audio", "embed", "robotics", "computer", "preview-")
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.getString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                var ok = false
                for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
                if (ok && name.contains("flash") && skip.none { name.contains(it) }) out.add(name)
            }
            out.sortedDescending()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
