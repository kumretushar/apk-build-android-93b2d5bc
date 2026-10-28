package com.tusharx.friday

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings

class PhoneControl(private val ctx: Context) {

    private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    /** Runs one action and returns a short human-readable result. */
    fun run(a: Action): String {
        return try {
            when (a.type.lowercase().trim()) {
                "open_app" -> openApp(a.arg)
                "youtube" -> youtube(a.arg)
                "web_search" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(a.arg)))); "Search: ${a.arg}" }
                "open_url" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse(if (a.arg.startsWith("http")) a.arg else "https://${a.arg}"))); "Opened ${a.arg}" }
                "call" -> { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + resolveNumber(a.arg)))); "Dialer: ${a.arg}" }
                "sms" -> {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + resolveNumber(a.arg))).putExtra("sms_body", a.arg2)
                    launch(i); "SMS draft: ${a.arg}"
                }
                "maps" -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.arg)))); "Maps: ${a.arg}" }
                "flashlight" -> flashlight(a.arg)
                "volume" -> volume(a.arg)
                "alarm" -> alarm(a.arg, a.arg2)
                "timer" -> {
                    val s = a.arg.filter { it.isDigit() }.toIntOrNull() ?: return "Timer: invalid time"
                    launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "Timer ${s}s set"
                }
                "settings" -> settings(a.arg)
                "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
                "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
                "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents")
                "notifications" -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications")
                "screenshot" -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Screenshot") else "Screenshot needs Android 9+"
                "lock_screen" -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Lock screen") else "Lock needs Android 9+"
                else -> "Unknown action: ${a.type}"
            }
        } catch (e: Exception) {
            "${a.type} failed: ${e.message}"
        }
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val q = name.lowercase().trim()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "App not found: $name"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Cannot open $name"
        launch(i)
        return "Opened ${hit.loadLabel(pm)}"
    }

    private fun youtube(q: String): String {
        return try {
            launch(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q))
            "YouTube: $q"
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
            "YouTube (web): $q"
        }
    }

    private fun resolveNumber(s: String): String {
        if (s.any { it.isDigit() } && s.count { it.isLetter() } == 0) return Uri.encode(s)
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val c = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$s%"), null
            )
            c?.use { if (it.moveToFirst()) return Uri.encode(it.getString(0)) }
        }
        return Uri.encode(s)
    }

    private fun flashlight(arg: String): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "No flashlight found"
        val on = !arg.lowercase().contains("off")
        cm.setTorchMode(id, on)
        return if (on) "Flashlight ON" else "Flashlight OFF"
    }

    private fun volume(arg: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val a = arg.lowercase()
        val target = when {
            a.contains("max") -> max
            a.contains("mute") -> 0
            a.contains("down") || a.contains("kam") -> (cur - max / 5).coerceAtLeast(0)
            a.contains("up") || a.contains("badha") -> (cur + max / 5).coerceAtMost(max)
            a.filter { it.isDigit() }.isNotEmpty() -> (a.filter { it.isDigit() }.toInt().coerceIn(0, 100) * max / 100)
            else -> cur
        }
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return "Volume ${target * 100 / max}%"
    }

    private fun alarm(time: String, label: String): String {
        val p = time.split(":")
        val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: return "Alarm: invalid time"
        val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Friday alarm" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "Alarm %02d:%02d".format(h, m)
    }

    private fun settings(arg: String): String {
        val action = when (arg.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        launch(Intent(action))
        return "Settings: $arg"
    }

    private fun global(action: Int, label: String): String {
        val s = FridayAccessibilityService.instance ?: return "Accessibility service OFF (Settings se on karo)"
        return if (s.performGlobalAction(action)) label else "$label failed"
    }
}
