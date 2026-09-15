package com.example.videoparser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/** 系统栏图标根据当前页面背景调整，不直接跟随设备主题。 */
@Composable
internal fun PageSystemBars(statusBackground: Color, navigationBackground: Color = statusBackground) {
    val view = LocalView.current
    val darkStatusIcons = statusBackground.luminance() > .5f
    val darkNavigationIcons = navigationBackground.luminance() > .5f
    DisposableEffect(view, darkStatusIcons, darkNavigationIcons) {
        val window = view.context.hostActivity()?.window
        fun applyAppearance() {
            if (window == null) return
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkStatusIcons
                isAppearanceLightNavigationBars = darkNavigationIcons
            }
            if (Build.VERSION.SDK_INT >= 29) {
                // The page already draws an opaque background behind both system bars.
                window.isNavigationBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
            }
        }
        applyAppearance()
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) applyAppearance()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
}
