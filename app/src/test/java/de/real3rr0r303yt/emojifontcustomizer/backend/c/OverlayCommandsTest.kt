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
package de.real3rr0r303yt.emojifontcustomizer.backend.c

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayCommandsTest {

    @Test
    fun buildsOverlayCommands() {
        assertEquals("cmd overlay list", OverlayCommands.list())
        assertEquals("cmd overlay list --user 0", OverlayCommands.list(user = 0))
        assertEquals("cmd overlay enable com.example.font", OverlayCommands.enable("com.example.font"))
        assertEquals(
            "cmd overlay disable --user 10 com.example.font",
            OverlayCommands.disable("com.example.font", user = 10),
        )
        assertEquals(
            "cmd overlay enable-exclusive --category com.android.theme.font.notoserifsource",
            OverlayCommands.enableExclusive("com.android.theme.font.notoserifsource"),
        )
    }

    @Test
    fun validatesPackageNames() {
        assertTrue(OverlayCommands.isValidPackageName("com.android.theme.font.notoserifsource"))
        assertFalse(OverlayCommands.isValidPackageName("nodots"))
        assertFalse(OverlayCommands.isValidPackageName("com.example; reboot"))
        assertFalse(OverlayCommands.isValidPackageName("com.example && rm -rf /"))
        assertFalse(OverlayCommands.isValidPackageName("\$(id).x"))
    }

    @Test
    fun emojiDiagnosticsIsReadOnly() {
        val cmd = OverlayCommands.emojiDiagnostics()
        assertTrue(cmd.contains("ls -l /system/fonts"))
        assertTrue(cmd.contains("cmd font dump"))
        listOf("rm ", "mount", "cp ", "mv ", " > ").forEach { assertFalse("contains $it", cmd.contains(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShellInjection() {
        OverlayCommands.enable("com.example;reboot")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeUser() {
        OverlayCommands.list(user = -1)
    }

    @Test
    fun readyStateReportsPrivilege() {
        assertEquals("root", ShizukuState.Ready(13, 0).privilege)
        assertEquals("shell (adb)", ShizukuState.Ready(13, 2000).privilege)
    }
}
