# Emoji Font Customizer

[![Android CI](https://github.com/Real3rr0r303YT/EmojiFontCustomizer/actions/workflows/android.yml/badge.svg)](https://github.com/Real3rr0r303YT/EmojiFontCustomizer/actions/workflows/android.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

An Android app for previewing, packaging and applying custom fonts and emoji packs.
It is written in Kotlin with Jetpack Compose.

**Features**
- Text fonts and emoji packs are managed separately. Emoji fonts are detected
  automatically from their color tables (`CBDT`, `COLR`, `sbix`, `SVG`).
- Live preview that combines the chosen text font with the chosen emoji pack (Android 10+).
- Export of font packs and emoji packs, plus a **Magisk / KernelSU / APatch module**
  that replaces the system emoji font (root).
- System font overlays through **Shizuku** (`cmd overlay`).
- An in-app *Open Source / GPL-3.0* page with the full license text, and an optional donate button.

- **Package:** `de.real3rr0r303yt.emojifontcustomizer`
- **minSdk:** 26 (Android 8.0) · **targetSdk:** 35

## Architecture

The UI talks to a single `BackendRouter`. The router owns three backends and picks
one for each request based on the live Shizuku state (`RoutingPolicy`):

| Backend | Package | Purpose |
|---|---|---|
| **A · In-App Renderer** | `backend.a` | Imports `.ttf` / `.otf` / `.ttc` files and sorts them into text fonts and emoji packs (`FontInspector`). Renders them in Compose through `Typeface` → `FontFamily`, chaining text font and emoji pack with `Typeface.CustomFallbackBuilder`. |
| **B · OEM / Theme Export** | `backend.b` | Builds portable font/emoji packs (`font-pack.json` plus `fonts/`, zipped) and imports them again. Also writes Magisk modules for emoji packs (`system/fonts/NotoColorEmoji.ttf`, plus OEM emoji files if the device has them). |
| **C · Shizuku & Shell Overlays** | `backend.c` | Tracks the Shizuku binder and permission state. When Shizuku is active it runs validated shell commands such as `cmd overlay enable-exclusive --category …`, `cmd overlay disable …` and `cmd font dump`. |

| Request | Shizuku ready | Shizuku not ready |
|---|---|---|
| Preview | A | A |
| Export pack | B | B |
| Apply font system-wide | **C** | B (fallback, with reason shown) |
| Apply emoji system-wide | B (root module) | B (root module) |

Overlays cannot replace the emoji font, and the Shizuku shell user cannot write to
`/system`, so system emoji always go through a root module. The module also:

- removes emoji fonts that Google Play system updates install in `/data/fonts`. On
  Android 12+ these override `/system/fonts`, which is the most common reason an emoji
  module seems to do nothing. This runs at install time and again on every boot
  (`post-fs-data.sh`).
- refreshes Google Play services' downloadable emoji cache, which Gboard and many
  messengers use (`service.sh`).
- warns on KernelSU/APatch that a mount (overlay) module is needed for `/system` files.

The **System → Diagnose emoji** button (Shizuku, read-only) shows whether the module
font is mounted and whether an updated emoji font is overriding it.

## iOS-style emoji

Apple Color Emoji is proprietary (© Apple), so this GPL-3.0 project does **not**
bundle or download it. Apple's original `.ttc` also uses the `sbix` format, which
Android cannot render; the app detects such files and shows a warning. Any
iOS-style emoji font in an Android format (CBDT/COLR `.ttf`) can be imported,
previewed and applied like any other emoji pack. Free alternatives are linked in the
app: Noto Color Emoji (OFL-1.1), Twemoji (CC-BY-4.0) and OpenMoji (CC-BY-SA-4.0).

## Donations

Support development on [Buy Me a Coffee](https://buymeacoffee.com/Real3rr0r303YT) ☕

Forks can set `donationUrl` in `gradle.properties` (PayPal.me, Buy Me a Coffee, Ko-fi, …) to
show the ❤ button in the top bar and on the About page. If it is empty, the button is hidden.

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
