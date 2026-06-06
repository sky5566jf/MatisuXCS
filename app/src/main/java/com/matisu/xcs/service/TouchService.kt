package com.matisu.xcs.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

class TouchService : AccessibilityService() {

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private var serverCallback: ServerCallback? = null

    interface ServerCallback {
        fun onServiceConnected()
    }

    fun setServerCallback(callback: ServerCallback?) {
        serverCallback = callback
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        screenWidth = bounds.width()
        screenHeight = bounds.height()
        serverCallback?.onServiceConnected()
    }

    fun dispatchTap(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun dispatchLongPress(x: Int, y: Int, durationMs: Long = 800) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun dispatchGesture(x: Int, y: Int, action: Long) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val startTime = if (action == 0L) 0L else 100L
        val duration = if (action == 0L) 1L else 100L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, startTime, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun dispatchSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 200) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performGlobalAction(action: String) {
        val actionId = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent", "overview" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "qs" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock_screen", "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            "screenshot", "take_screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            "power_dialog", "power" -> GLOBAL_ACTION_POWER_DIALOG
            "split_screen" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            else -> return
        }
        performGlobalAction(actionId)
    }

    fun inputText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = findFocusedNode(root)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedNode(child)
            if (found != null) return found
        }
        return null
    }

    fun getScreenSize(): Pair<Int, Int> = Pair(screenWidth, screenHeight)

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
