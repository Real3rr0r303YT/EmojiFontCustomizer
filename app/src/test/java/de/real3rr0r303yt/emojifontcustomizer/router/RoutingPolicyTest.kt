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
package de.real3rr0r303yt.emojifontcustomizer.router

import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyTest {

    private val notReadyStates = listOf(
        ShizukuState.NotRunning,
        ShizukuState.Unsupported,
        ShizukuState.PermissionRequired(permanentlyDenied = false),
        ShizukuState.PermissionRequired(permanentlyDenied = true),
    )

    @Test
    fun previewAlwaysUsesRenderer() {
        (notReadyStates + ShizukuState.Ready(13, 2000)).forEach { state ->
            assertEquals(BackendId.A_RENDERER, RoutingPolicy.decide(RouteRequest.PREVIEW, state).backend)
        }
    }

    @Test
    fun exportAlwaysUsesThemeExport() {
        val decision = RoutingPolicy.decide(RouteRequest.EXPORT_PACK, ShizukuState.Ready(13, 0))
        assertEquals(BackendId.B_EXPORT, decision.backend)
        assertFalse(decision.fallback)
    }

    @Test
    fun applySystemUsesShizukuWhenReady() {
        val decision = RoutingPolicy.decide(RouteRequest.APPLY_SYSTEM, ShizukuState.Ready(13, 2000))
        assertEquals(BackendId.C_SHIZUKU, decision.backend)
        assertFalse(decision.fallback)
        assertTrue(decision.reason.contains("shell"))
    }

    @Test
    fun applySystemFallsBackToExportWithoutShizuku() {
        notReadyStates.forEach { state ->
            val decision = RoutingPolicy.decide(RouteRequest.APPLY_SYSTEM, state)
            assertEquals(BackendId.B_EXPORT, decision.backend)
            assertTrue(decision.fallback)
        }
    }

    @Test
    fun systemEmojiAlwaysUsesKeyboard() {
        (notReadyStates + ShizukuState.Ready(13, 0)).forEach { state ->
            val decision = RoutingPolicy.decide(RouteRequest.APPLY_EMOJI_SYSTEM, state)
            assertEquals(BackendId.D_KEYBOARD, decision.backend)
            assertFalse(decision.fallback)
        }
    }
}
