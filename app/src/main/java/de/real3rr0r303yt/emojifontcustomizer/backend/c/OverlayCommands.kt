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
 * Builds the shell commands Backend C sends through Shizuku.
 *
 * Every argument that ends up in the command line is validated first, because
 * the commands run with shell (or root) privileges.
 */
object OverlayCommands {

    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val CATEGORY = Regex("^[A-Za-z0-9_.]+$")

    /** Overlay category used by AOSP and most OEMs for system font overlays. */
    const val FONT_CATEGORY = "android.theme.customization.font"

    fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

    fun list(user: Int? = null): String = "cmd overlay list" + userArg(user)

    fun listFontOverlays(user: Int? = null): String =
        "cmd overlay list" + userArg(user) + " | grep -i font"

    fun enable(overlayPackage: String, user: Int? = null): String =
        "cmd overlay enable" + userArg(user) + " " + requirePackage(overlayPackage)

    fun disable(overlayPackage: String, user: Int? = null): String =
        "cmd overlay disable" + userArg(user) + " " + requirePackage(overlayPackage)

    /**
     * Enables [overlayPackage] and disables every other overlay of the same
     * [category] for the target package, e.g. to switch the system font.
     */
    fun enableExclusive(
        overlayPackage: String,
        category: String = FONT_CATEGORY,
        user: Int? = null,
    ): String {
        require(CATEGORY.matches(category)) { "Invalid overlay category: $category" }
        return "cmd overlay enable-exclusive" + userArg(user) +
            " --category " + requirePackage(overlayPackage)
    }

    /** Dumps the updatable-font state (Android 12+). Read-only. */
    fun fontDump(): String = "cmd font dump"

    private fun userArg(user: Int?): String {
        if (user == null) return ""
        require(user >= 0) { "Invalid user id: $user" }
        return " --user $user"
    }

    private fun requirePackage(name: String): String {
        require(isValidPackageName(name)) { "Invalid overlay package name: $name" }
        return name
    }
}
