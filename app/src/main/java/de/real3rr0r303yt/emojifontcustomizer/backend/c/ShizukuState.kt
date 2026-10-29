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

/**
 * Snapshot of what Backend C can currently do with Shizuku.
 *
 * Kept free of Android/Shizuku types so the router policy stays unit-testable.
 */
sealed interface ShizukuState {
    /** Shizuku is not installed or its server is not running (no binder). */
    data object NotRunning : ShizukuState

    /** Shizuku is running but older than API v11, which this app does not support. */
    data object Unsupported : ShizukuState

    /** Binder is alive but the user has not granted access yet. */
    data class PermissionRequired(val permanentlyDenied: Boolean) : ShizukuState

    /** Binder is alive and permission is granted: shell commands can be executed. */
    data class Ready(val serverVersion: Int, val uid: Int) : ShizukuState {
        /** `0` means Shizuku runs as root, `2000` as the adb shell user. */
        val privilege: String
            get() = when (uid) {
                0 -> "root"
                2000 -> "shell (adb)"
                else -> "uid $uid"
            }
    }
}

val ShizukuState.isReady: Boolean get() = this is ShizukuState.Ready
