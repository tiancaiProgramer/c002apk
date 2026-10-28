package com.example.c001apk.compose.ui.component

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.c001apk.compose.R
import com.example.c001apk.compose.ThemeType
import com.example.c001apk.compose.constant.Constants.seedColors
import com.example.c001apk.compose.util.CookieUtil
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.elevation.SurfaceColors

/** Shared with the existing dynamic/reply panel rather than independent article styling. */
internal object EditorAppearance {
    const val PANEL_CORNER_DP = 16
    const val CONTROL_CORNER_DP = 12

    // These are the existing ReplyActivity formulas, shared without changing its appearance.
    fun primary(context: Context): Int = if (CookieUtil.materialYou)
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0)
    else (seedColors.getOrNull(ThemeType.entries.indexOf(CookieUtil.themeType))
        ?: "FF${CookieUtil.seedColor}".toLongOrNull(16) ?: seedColors[0]).toInt()

    fun panel(context: Context): Int = if (CookieUtil.materialYou) SurfaceColors.SURFACE_1.getColor(context)
    else ColorUtils.blendARGB(ContextCompat.getColor(context, R.color.color_surface), primary(context), 0.08f)

    fun surface(context: Context): Int = if (CookieUtil.materialYou)
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0)
    else ContextCompat.getColor(context, R.color.color_surface)

    fun control(context: Context): Int = MaterialColors.getColor(context,
        com.google.android.material.R.attr.colorControlNormal, 0)

    fun divider(context: Context): Int = MaterialColors.getColor(context,
        com.google.android.material.R.attr.colorSurfaceVariant, 0)

    fun primaryDark(context: Context, primary: Int = primary(context)): Int = if (CookieUtil.materialYou)
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryDark, primary)
    else ColorUtils.blendARGB(primary, android.graphics.Color.BLACK, 0.2f)
}

@Composable
internal fun rememberEditorViewContext(): Context {
    val context = LocalContext.current
    return remember(context, CookieUtil.isDarkMode, CookieUtil.materialYou) {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (CookieUtil.isDarkMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val wrapper = ContextThemeWrapper(context, R.style.Theme_C001apkCompose).apply { applyOverrideConfiguration(configuration) }
        if (CookieUtil.materialYou) DynamicColors.wrapContextIfAvailable(wrapper) else wrapper
    }
}
