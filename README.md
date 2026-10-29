# Emoji Font Customizer

[![Android CI](https://github.com/Real3rr0r303YT/EmojiFontCustomizer/actions/workflows/android.yml/badge.svg)](https://github.com/Real3rr0r303YT/EmojiFontCustomizer/actions/workflows/android.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

An Android app for previewing, packaging and applying custom fonts and emoji fonts.
It is written in Kotlin with Jetpack Compose.

- **Package:** `de.real3rr0r303yt.emojifontcustomizer`
- **minSdk:** 26 (Android 8.0) · **targetSdk:** 35

## Architecture

The UI talks to a single `BackendRouter`. The router owns three backends and picks
one for each request based on the live Shizuku state (`RoutingPolicy`):

| Backend | Package | Purpose |
|---|---|---|
| **A · In-App Renderer** | `backend.a` | Imports `.ttf` / `.otf` files into app storage and renders them in Compose through `Typeface` → `FontFamily` for the live preview and emoji grid. |
| **B · OEM / Theme Export** | `backend.b` | Builds portable font packs (`font-pack.json` plus `fonts/`, zipped) for OEM theme stores or overlay templates, and imports them again. |
| **C · Shizuku & Shell Overlays** | `backend.c` | Tracks the Shizuku binder and permission state. When Shizuku is active it runs validated shell commands such as `cmd overlay enable-exclusive --category …`, `cmd overlay disable …` and `cmd font dump`. |

| Request | Shizuku ready | Shizuku not ready |
|---|---|---|
| Preview | A | A |
| Export pack | B | B |
| Apply system-wide | **C** | B (fallback, with reason shown) |

## Building

```sh
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds every push and pull request. Pushing a `v*` tag attaches
the debug APK to a GitHub release.

## Shizuku

System-wide changes need [Shizuku](https://shizuku.rikka.app/), started either
through wireless debugging/adb or with root. Open the **System** tab and grant
access. Commands then run as the `shell` user (adb) or as `root`.

## License

Copyright (C) 2026 Real3rr0r303YT

This program is free software: you can redistribute it and/or modify it under
the terms of the **GNU General Public License v3.0** as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any
later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for the full text.

SPDX-License-Identifier: `GPL-3.0-or-later`
