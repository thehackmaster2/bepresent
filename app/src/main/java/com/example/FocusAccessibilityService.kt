package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        var isFocusActive = false
        
        val DISTRACTING_APPS = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.google.android.youtube",
            "com.twitter.android",
            "com.facebook.katana"
        )

        fun isServiceEnabled(context: Context): Boolean {
            val expectedId = "${context.packageName}/${FocusAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedId, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isFocusActive) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Do not block ourselves
            if (packageName == this.packageName) return

            if (DISTRACTING_APPS.contains(packageName)) {
                Log.d("FocusBlocker", "Redirecting from $packageName to BePresent focus lobby")
                
                // Fire intent to redirect user back to BePresent App
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("BLOCKED_FROM_PACKAGE", packageName)
                }
                startActivity(intent)

                Toast.makeText(
                    this,
                    "BePresent Shield Activated: Distracting apps are blocked during focus! 🔒",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onInterrupt() {
        Log.d("FocusBlocker", "Accessibility Service interrupted")
    }
}
