package com.example.c001apk.compose

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Operates normal app controls only; each step is selected and reviewed separately. */
@RunWith(AndroidJUnit4::class)
class ArticleProductionUiStepTest {
    @Test fun reviewedStep() {
        val inst = InstrumentationRegistry.getInstrumentation()
        check(inst.targetContext.packageName.endsWith(".articletest"))
        val args = InstrumentationRegistry.getArguments()
        val action = requireNotNull(args.getString("ui_action"))
        val label = args.getString("ui_label").orEmpty()
        val automation = inst.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        if (action == "back") {
            assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            SystemClock.sleep(500)
            return
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            val all = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                all.add(node)
                for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
            }
            automation.windows.sortedByDescending { it.layer }.filter {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION ||
                    it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
                .forEach { it.root?.let(::visit) }
            inst.uiAutomation.rootInActiveWindow?.let(::visit)
            return all
        }
        if (action == "open-main") {
            inst.uiAutomation.executeShellCommand("am start --display 0 -n com.example.c001apk.replyfix/com.example.c001apk.compose.ui.main.MainActivity").use {
                java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
            }
            SystemClock.sleep(1000)
            assertTrue(nodes().any { it.packageName?.toString() == "com.example.c001apk.replyfix" })
            return
        }
        val deadline = SystemClock.elapsedRealtime() + 6000
        var candidate: AccessibilityNodeInfo? = null
        while (SystemClock.elapsedRealtime() < deadline && candidate == null) {
            candidate = nodes().firstOrNull { node -> when (action) {
                "click-text", "assert-text" -> node.text?.toString() == label
                "click-description" -> node.contentDescription?.toString() == label
                "click-bounds" -> android.graphics.Rect().also { node.getBoundsInScreen(it) }.flattenToString() == label && node.isClickable
                "set-text", "set-selection" -> node.isEditable && (node.text?.toString() == label || node.hintText?.toString() == label)
                "scroll" -> node.isScrollable && (label.isEmpty() ||
                    android.graphics.Rect().also { node.getBoundsInScreen(it) }.flattenToString() == label)
                else -> error("Unsupported reviewed action")
            } }
            if (candidate == null) SystemClock.sleep(100)
        }
        var node = requireNotNull(candidate) { "Reviewed UI target unavailable: $action / $label" }
        if (action == "assert-text") return
        if (action == "set-selection") {
            assertTrue(node.packageName?.toString() == "com.example.c001apk.replyfix")
            val offset = requireNotNull(args.getString("ui_value")).toInt()
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, offset)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, offset)
            }))
        } else if (action == "set-text") {
            assertTrue(node.packageName?.toString() == "com.example.c001apk.replyfix")
            val value = requireNotNull(args.getString("ui_value"))
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        } else if (action == "scroll") {
            assertTrue(node.performAction(if (args.getString("ui_value") == "backward")
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        } else {
            repeat(8) {
                if (node.isClickable) return@repeat
                node.parent?.let { node = it }
            }
            // Submission must be guarded by the explicit private confirmation on screen.
            if (label == "发布" && nodes().any { it.text?.toString()?.contains("是否继续？") == true }) {
                check(args.getString("allow_private_publish") == "true")
                check(nodes().any { it.text?.toString() == "发布后仅自己可见，是否继续？" })
            }
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        SystemClock.sleep(700)
    }
}
