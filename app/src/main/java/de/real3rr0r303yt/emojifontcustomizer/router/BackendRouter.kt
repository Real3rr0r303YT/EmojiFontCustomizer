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

import android.content.Context
import de.real3rr0r303yt.emojifontcustomizer.backend.a.InAppRendererBackend
import de.real3rr0r303yt.emojifontcustomizer.backend.b.ThemeExportBackend
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuBackend
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuState
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point the UI talks to. Owns the three backends and decides, based on
 * the live Shizuku state, which one handles a request (see [RoutingPolicy]).
 */
class BackendRouter(context: Context) {

    val renderer = InAppRendererBackend(context.applicationContext)
    val exporter = ThemeExportBackend(context.applicationContext, renderer)
    val shizuku = ShizukuBackend()

    val shizukuState: StateFlow<ShizukuState> get() = shizuku.state

    fun start() = shizuku.start()

    fun stop() = shizuku.stop()

    fun route(request: RouteRequest): RouteDecision =
        RoutingPolicy.decide(request, shizuku.state.value)
}
