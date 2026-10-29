/*
 * Emoji Font Customizer
 * Copyright (C) 2026 Real3rr0r303YT
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package de.real3rr0r303yt.emojifontcustomizer.backend.b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagiskModuleTemplateTest {

    @Test
    fun moduleIdIsSanitized() {
        assertEquals("emojifontcustomizer_ios_style_emoji_17", MagiskModuleTemplate.moduleId("iOS Style Emoji 17!"))
        assertEquals("emojifontcustomizer_emoji", MagiskModuleTemplate.moduleId("???"))
    }

    @Test
    fun modulePropHasRequiredKeys() {
        val prop = MagiskModuleTemplate.moduleProp("Noto\nColor", "0.2.0").lines()
        listOf("id=", "name=", "version=", "versionCode=", "author=", "description=").forEach { key ->
            assertTrue("missing $key", prop.any { it.startsWith(key) })
        }
        assertTrue(prop.contains("name=Emoji: Noto Color"))
    }

    @Test
    fun installerScriptsAreMagiskCompatible() {
        assertEquals("#MAGISK\n", MagiskModuleTemplate.UPDATER_SCRIPT)
        assertTrue(MagiskModuleTemplate.updateBinary.contains("install_module"))
        assertTrue(MagiskModuleTemplate.customizeSh.contains("SamsungColorEmoji.ttf"))
        assertTrue(MagiskModuleTemplate.customizeSh.contains("\$MODPATH/system/fonts/NotoColorEmoji.ttf"))
    }

    @Test
    fun bootScriptsHandleUpdatedEmojiFonts() {
        assertTrue(MagiskModuleTemplate.postFsDataSh.contains("find /data/fonts/files"))
        assertTrue(MagiskModuleTemplate.customizeSh.contains("find /data/fonts/files"))
        // Must never wipe the updatable-font root directory itself.
        assertTrue(MagiskModuleTemplate.postFsDataSh.contains("if [ \"\$d\" = /data/fonts/files ]"))
        assertTrue(MagiskModuleTemplate.serviceSh.contains("sys.boot_completed"))
        assertTrue(MagiskModuleTemplate.serviceSh.contains(MagiskModuleTemplate.GMS_FONT_CACHE))
    }
}
