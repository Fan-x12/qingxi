package com.example.videoparser

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun profileSelectionDoesNotActivateAndUnsavedChangesAreProtected() {
        val base = compose.activity
        val folder = File(base.cacheDir, "settings-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext() = this
            override fun getNoBackupFilesDir() = folder
        }
        val store = ImageServiceStore(isolated)
        store.saveProfile(ImageServiceConfig("服务一", "https://example.com/v1", "image-one", "one"), "")
        store.saveProfile(ImageServiceConfig("服务二", "https://example.org/v1", "image-two", "two"), "", activate = false)
        var closed = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides isolated) {
                MaterialTheme { ImageServiceSettings { closed = true } }
            }
        }
        screenshot("image-service-redesign")
        compose.onNodeWithText("服务二").performClick()
        assertEquals("one", store.load().id)
        screenshot("image-service-editor")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("修改后的服务")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("修改后的服务").assertExists()
        assertFalse(closed)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        assertFalse(closed)
        compose.onNodeWithText("添加新接口").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        assertTrue(closed)
        assertEquals("服务二", store.loadAll().first { it.id == "two" }.name)
    }

    @Test fun settingsShowsServicesBeforeConversationActions() {
        compose.setContent { MaterialTheme {
            SettingsPage({}, {}, {}, "自动路由", {})
        } }
        compose.onNodeWithText("解析接口").assertIsDisplayed()
        compose.onNodeWithText("生图接口").assertIsDisplayed()
        compose.onNodeWithText("第三方接口说明").assertExists()
        compose.onNodeWithText("新建会话").assertDoesNotExist()
        screenshot("settings-redesign")
    }
}
