package com.tusharx.friday

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Used only to press system buttons (Home/Back/Recents/...) when the user asks by voice. */
class FridayAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    companion object {
        @Volatile var instance: FridayAccessibilityService? = null
    }
}
