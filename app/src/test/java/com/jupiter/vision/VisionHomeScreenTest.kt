package com.jupiter.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

data class PlaceholderApp(val id: String, val name: String, val iconRes: Int)
data class SettingStatusItem(val id: String, val label: String, val iconRes: Int, val isInteractive: Boolean = false)

/**
 * Unit tests for Jupitervision OS VisionHomeScreen data models and logic.
 */
class VisionHomeScreenTest {

    @Test
    fun testPlaceholderAppsCountAndContent() {
        val apps = listOf(
            PlaceholderApp("spotify", "Spotify", R.drawable.ic_app_spotify),
            PlaceholderApp("whatsapp", "WhatsApp", R.drawable.ic_app_whatsapp),
            PlaceholderApp("maps", "Maps", R.drawable.ic_app_maps),
            PlaceholderApp("gmail", "Gmail", R.drawable.ic_app_gmail),
            PlaceholderApp("calendar", "Calendar", R.drawable.ic_app_calendar)
        )

        assertEquals("Must have exactly 5 placeholder apps", 5, apps.size)
        assertEquals("Spotify", apps[0].name)
        assertEquals("WhatsApp", apps[1].name)
        assertEquals("Maps", apps[2].name)
        assertEquals("Gmail", apps[3].name)
        assertEquals("Calendar", apps[4].name)
    }

    @Test
    fun testSettingsGridItemsAndScreenLockInteractivity() {
        val settingItems = listOf(
            SettingStatusItem("sound", "RING", R.drawable.ic_setting_sound),
            SettingStatusItem("internet", "WIFI", R.drawable.ic_setting_wifi),
            SettingStatusItem("battery", "78%", R.drawable.ic_setting_battery),
            SettingStatusItem("storage", "64%", R.drawable.ic_setting_storage),
            SettingStatusItem("lock", "LOCK", R.drawable.ic_setting_lock, isInteractive = true),
            null // Reserved cell (empty)
        )

        assertEquals("Must have 6 slots for the 2x3 grid", 6, settingItems.size)

        // Status items validation
        assertEquals("RING", settingItems[0]?.label)
        assertFalse(settingItems[0]?.isInteractive == true)

        assertEquals("WIFI", settingItems[1]?.label)
        assertFalse(settingItems[1]?.isInteractive == true)

        assertEquals("78%", settingItems[2]?.label)
        assertFalse(settingItems[2]?.isInteractive == true)

        assertEquals("64%", settingItems[3]?.label)
        assertFalse(settingItems[3]?.isInteractive == true)

        // Screen lock cell must be interactive
        val lockItem = settingItems[4]
        assertNotNull(lockItem)
        assertEquals("LOCK", lockItem?.label)
        assertTrue("Screen Lock must be interactive", lockItem?.isInteractive == true)

        // Slot 6 must be reserved/empty
        assertNull("Slot 6 must be reserved/empty", settingItems[5])
    }
}
