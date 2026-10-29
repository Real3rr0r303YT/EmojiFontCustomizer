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

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Output of a shell command executed through Shizuku. */
data class ShellResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Backend C: tracks the Shizuku binder / permission state and runs privileged
 * shell commands (e.g. `cmd overlay ...`) when Shizuku is active.
 */
class ShizukuBackend {

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { _state.value = ShizukuState.NotRunning }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }

    /** Registers Shizuku listeners. Call once; pair with [stop]. */
    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    /** Re-reads binder and permission status. */
    fun refresh() {
        _state.value = detect()
    }

    /** Asks the user to grant this app access in the Shizuku dialog. */
    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Runs [command] via `sh -c` in the Shizuku server process.
     *
     * Fails if Shizuku is not [ShizukuState.Ready].
     */
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        check(detect().isReady) { "Shizuku is not ready." }
        val process = newProcess(arrayOf("sh", "-c", command))
        var stderr = ""
        val stderrReader = Thread {
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        }.apply { start() }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        stderrReader.join()
        ShellResult(command, exit, stdout.trim(), stderr.trim())
    }

    private fun detect(): ShizukuState {
        if (!Shizuku.pingBinder()) return ShizukuState.NotRunning
        return try {
            if (Shizuku.isPreV11()) return ShizukuState.Unsupported
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.Ready(serverVersion = Shizuku.getVersion(), uid = Shizuku.getUid())
            } else {
                ShizukuState.PermissionRequired(
                    permanentlyDenied = Shizuku.shouldShowRequestPermissionRationale(),
                )
            }
        } catch (e: RuntimeException) {
            // Binder died between ping and the actual call.
            ShizukuState.NotRunning
        }
    }

    /**
     * `Shizuku.newProcess` is not part of the public API surface in v13, but it is
     * still the lightest way to run a one-shot shell command without a UserService.
     */
    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 0x5E1F
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
