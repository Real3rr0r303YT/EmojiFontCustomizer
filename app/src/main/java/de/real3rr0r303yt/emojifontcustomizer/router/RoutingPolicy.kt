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

/** The three backends the router can dispatch to. */
enum class BackendId(val label: String) {
    A_RENDERER("Backend A · In-App Renderer"),
    B_EXPORT("Backend B · OEM / Theme Export"),
    C_SHIZUKU("Backend C · Shizuku & Shell Overlays"),
}

/** What the user wants to do with the selected font. */
enum class RouteRequest {
    /** Render the font inside the app (always possible). */
    PREVIEW,

    /** Produce a font pack file for OEM theme stores / manual import. */
    EXPORT_PACK,

    /** Apply a font or overlay system-wide. */
    APPLY_SYSTEM,

    /** Replace the system emoji font. */
    APPLY_EMOJI_SYSTEM,
}

data class RouteDecision(
    val backend: BackendId,
    val reason: String,
    /** True when the preferred backend was unavailable and the router fell back. */
    val fallback: Boolean = false,
)

/** Pure routing rules, separated from [BackendRouter] so they can be unit-tested. */
object RoutingPolicy {

    fun decide(request: RouteRequest, shizuku: ShizukuState): RouteDecision = when (request) {
        RouteRequest.PREVIEW ->
            RouteDecision(BackendId.A_RENDERER, "Live preview is rendered in-app.")

        RouteRequest.EXPORT_PACK ->
            RouteDecision(BackendId.B_EXPORT, "Font packs are built from app storage.")

        // Overlays cannot swap the emoji font and Shizuku's shell user cannot write
        // /system, so system emoji always go through a root module built by Backend B.
        RouteRequest.APPLY_EMOJI_SYSTEM ->
            RouteDecision(
                BackendId.B_EXPORT,
                "System emoji are replaced with a Magisk/KernelSU module (root required).",
            )

        RouteRequest.APPLY_SYSTEM -> when (shizuku) {
            is ShizukuState.Ready ->
                RouteDecision(BackendId.C_SHIZUKU, "Shizuku is active (${shizuku.privilege}).")

            is ShizukuState.PermissionRequired ->
                RouteDecision(
                    BackendId.B_EXPORT,
                    if (shizuku.permanentlyDenied) {
                        "Shizuku permission was denied; enable it in the Shizuku app. Exporting a pack instead."
                    } else {
                        "Shizuku permission not granted yet. Exporting a pack instead."
                    },
                    fallback = true,
                )

            ShizukuState.Unsupported ->
                RouteDecision(
                    BackendId.B_EXPORT,
                    "Shizuku is too old (needs API v11+). Exporting a pack instead.",
                    fallback = true,
                )

            ShizukuState.NotRunning ->
                RouteDecision(
                    BackendId.B_EXPORT,
                    "Shizuku is not running. Exporting a pack instead.",
                    fallback = true,
                )
        }
    }
}
