# Emoji Font Customizer

[![Android CI](https://github.com/Real3rr0r303YT/EmojiFontCustomizer/actions/workflows/android.yml/badge.svg)](https://github.com/Real3rr0r303YT/EmojiFontCustomizer/actions/workflows/android.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

An Android app for previewing, packaging and using custom fonts and emoji packs.
It is written in Kotlin with Jetpack Compose.

**It works without root and with a locked bootloader** (Verified Boot stays intact).

**Features**
- Text fonts and emoji packs are managed separately. Emoji fonts are detected
  automatically from their color tables (`CBDT`, `COLR`, `sbix`, `SVG`).
- Live preview that combines the chosen text font with the chosen emoji pack (Android 10+).
- A **complete, offline keyboard** that can replace Gboard:
  - German QWERTZ and English/international QWERTY. 🌐 switches the layout;
    long-press 🌐 opens the system keyboard picker.
  - Umlauts, accents, ß and digits on long-press. Number/symbol pages (`?123`, `=\<`),
    Shift with caps lock (double-tap), auto-capitalisation, repeating backspace, and an
    Enter key that follows the field's action (send, search, next …).
  - The ⌨ / 😀 / 📋 tabs switch between letters, emoji and clipboard.
  - **Emoji** come from your pack and are sent as images in apps that accept them, so
    recipients see your style. Everywhere else the normal emoji is inserted.
  - **Clipboard**: recent clips appear as chips and in their own panel, and one tap
    pastes them. They are kept in memory only, sensitive clips (passwords) are masked
    and expire after 60 s, and 🗑 clears the clipboard.
  - **Privacy**: no suggestions, no dictionary, no learning, and no network. Nothing you
    type, paste or pick is written to disk. Only settings (layout language, emoji pack)
    are stored.
- Export and import of font packs and emoji packs.
- Optional switching between the system's built-in font overlays through **Shizuku**
  (ADB, no root).
- No internet permission. The keyboard never reads what you type.
- An in-app *Open Source / GPL-3.0* page with the full license text, and an optional donate button.

- **Package:** `de.real3rr0r303yt.emojifontcustomizer`
- **minSdk:** 26 (Android 8.0) · **targetSdk:** 35

## Architecture

The UI talks to a single `BackendRouter`. The router owns four backends and picks
one for each request based on the live Shizuku state (`RoutingPolicy`):

| Backend | Package | Purpose |
|---|---|---|
| **A · In-App Renderer** | `backend.a` | Imports `.ttf` / `.otf` / `.ttc` files and sorts them into text fonts and emoji packs (`FontInspector`). Renders them in Compose through `Typeface` → `FontFamily`, chaining text font and emoji pack with `Typeface.CustomFallbackBuilder`. |
| **B · OEM / Theme Export** | `backend.b` | Builds portable font/emoji packs (`font-pack.json` plus `fonts/`, zipped) and imports them again. |
| **C · Shizuku & Shell Overlays** | `backend.c` | Tracks the Shizuku binder and permission state. When Shizuku is active it runs validated shell commands such as `cmd overlay enable-exclusive --category …`, `cmd overlay disable …` and `cmd font dump`. |
| **D · Keyboard** | `backend.d` | A full `InputMethodService`: text layouts (`KeyboardLayouts`, `ShiftController`), an emoji panel using the selected emoji pack, and the clipboard (`ClipboardHistory`). Emoji are sent as PNGs through `commitContent` and a `FileProvider` where the app accepts them, otherwise as Unicode. |

| Request | Shizuku ready | Shizuku not ready |
|---|---|---|
| Preview | A | A |
| Export pack | B | B |
| Apply font system-wide | **C** | B (fallback, with reason shown) |
| Use emoji pack everywhere | D (keyboard) | D (keyboard) |

### Why not system-wide emoji?

Without root, Android gives apps no way to replace the system emoji font:

- Font updates in `/data/fonts` must be signed with a key the platform trusts.
- Overlays can only switch between fonts that already exist on the system.
- The Shizuku/ADB shell user cannot write to `/system` or `/data/fonts`.
- Apps' downloadable emoji (EmojiCompat) come from Google Play services' signed font provider.

Some OEM theme stores (Xiaomi, Oppo, Vivo, Huawei) can install emoji themes, but
stock-like systems such as Pixel and Nothing OS cannot. The emoji keyboard (Backend D)
is the method that works on every locked, unrooted device. One limit: the recipient
sees your style only where the app accepts images; plain text emoji are drawn with
the recipient's own font.

## iOS-style emoji

Apple Color Emoji is proprietary (© Apple), so this GPL-3.0 project does **not**
bundle or download it. Apple's original `.ttc` also uses the `sbix` format, which
Android cannot render; the app detects such files and shows a warning. Any
iOS-style emoji font in an Android format (CBDT/COLR `.ttf`) can be imported from
the device, previewed and used in the emoji keyboard like any other emoji pack. Free alternatives are linked in the
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

GitHub Actions builds and tests every push and pull request. **Every successful
build on `main` publishes a GitHub release** tagged `v<versionName>-build.<run>`, with
the APK and its SHA-256 checksum attached. Pushing a `v*` tag yourself creates a
release with that name.

### Release signing (recommended)

Without signing secrets, releases contain the debug APK. Its key changes on every CI
run, so a new release will not install over an older one. To fix this, create a key
once and keep it safe and offline:

```sh
keytool -genkeypair -v -keystore release.jks -alias emojifont \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.b64
```

Then add these repository secrets (Settings → Secrets and variables → Actions):
`SIGNING_KEYSTORE_BASE64` (contents of `release.jks.b64`), `SIGNING_KEYSTORE_PASSWORD`,
`SIGNING_KEY_ALIAS` (`emojifont`) and `SIGNING_KEY_PASSWORD`. From then on,
releases contain the signed `release` APK. Never commit the keystore.

## Shizuku

Switching built-in system font overlays needs [Shizuku](https://shizuku.rikka.app/), started
through wireless debugging (no root needed). Open the **System** tab and grant
access. Commands then run as the `shell` user.

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
