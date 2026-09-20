# SideKey

Make the **Unihertz Titan 2 Elite**'s programmable keys do anything, not just
launch an app.

Built and tested on a Titan 2 Elite running Android 16, and nothing else. It
relies on how that phone's Shortcut keys settings work, so other Unihertz
models may or may not behave the same way — if you try one, an issue saying
what happened is welcome.

## Install

[**Download the latest APK**](https://github.com/jakevibes/SideKey/releases/latest)
— open that page on the phone, tap `SideKey-1.1.apk` under **Assets**, and
open it when the download finishes.

Android will say your browser is not allowed to install unknown apps; tap the
prompt, turn the switch on for that browser, and press back. That is the normal
sideloading dance, not something this app asks for.

Or over a cable:

```bash
adb install SideKey-1.1.apk
```

Every release is signed with the same key, so updates install over the top. A
build you compiled yourself is signed with a different key and will not, so
pick one and stay with it.

Needs Android 8.0 or newer.

## How it works

The side key is not an ordinary key. It belongs to `com.agui.shortcutsettings`
(`/system_ext/priv-app/AguiShortcutKey`), a privileged system app that consumes
the press before any launcher, app or accessibility service can see it. It
cannot be intercepted, and no permission changes that.

What the shortcut settings *will* do is point a key press at an app. So SideKey
is ten apps: `SideKey 1` … `SideKey 10`, each an `activity-alias` with its own
launcher entry, all of them aliases of one invisible activity that reads which
name was opened and runs whatever you mapped to it.

Nothing is intercepted, nothing is patched, no root.

## Using it

1. Open **SideKey**, tap a slot, pick what it should do.
2. Open the phone's shortcut settings — there is a button for it, and it goes
   straight to the Func1 page. On the Titan 2 Elite that screen lives at
   **Settings → Shortcut keys → Func1 key → Shortcut settings**, and it gives
   you **short press, long press and double click**. Point each at whichever
   slot you want; all ten are listed under **All apps**.

Hold a slot in SideKey to try it without pressing the key.

## Deep links

Three ways to put something *inside* an app on a key, in order of how little
you have to know:

1. **Open something inside an app.** Lists every app that offers a deep link of
   its own and opens that app's picker, so you never type a URI. This is
   `ACTION_CREATE_SHORTCUT`, the mechanism launchers use for "add shortcut" —
   any app may call it, no launcher privilege needed. On a Titan 2 Elite that
   is about a dozen apps: WhatsApp (a chat, the camera), Contacts (direct dial,
   direct message, a contact), Maps (directions, driving mode, traffic, a
   friend's location), Gmail (a label), Settings (any page), Drive, Docs,
   Sheets, Outlook, OneDrive, Sound Search.

   That list is everything the phone has, not a subset — it is populated by
   querying the system. Most modern apps publish shortcuts through
   `ShortcutManager` instead, and `LauncherApps.getShortcuts` refuses any
   caller that is not the default launcher, so those are out of reach.

2. **Share a link into SideKey.** Which is why this exists: almost every app
   has a Share or Copy link even when it offers no shortcut picker. Share a
   Spotify playlist, a YouTube video, a Slack channel, a browser page — SideKey
   appears in the share sheet, asks which slot, and that link is now on a key.

3. **Type it.** "Open a link or deep link" takes any scheme — `spotify:…`,
   `googlemaps://`, `wa.me/…` — and "Custom intent" takes a full
   `intent:#Intent;…;end` URI for anything else, Tasker and Home Assistant
   included.

## What a slot can do

Open an app · a link · a web search · the camera · the voice assistant · the
Wi-Fi panel · torch · ringer cycle · do not disturb · a timer · an alarm ·
play/pause · next · previous · back · home · recents · notification shade ·
quick settings · lock · screenshot · power menu · call a number · message a
number · dictate into any text box · a webhook (GET or POST) · any custom
intent URI · any broadcast.

**One-press dialling** places the call the moment the key is pressed, so it
needs `CALL_PHONE`. SideKey asks for it when you map a slot to a call, not when
you press the key — a permission dialog under your thumb would be worse than
one extra tap while you are setting it up. Refuse it and the dialer opens with
the number filled in instead; the same is true of a Contacts "direct dial"
shortcut, which hands back an `ACTION_CALL` intent.

The last three are the escape hatch: an `intent:` URI reaches anything else on
the phone that exposes one, Tasker and Home Assistant included.

## Dictation

A slot can be set to **Dictate**: press the key to start listening, press it
again to stop. What you said is transcribed on the phone and typed into
whatever text box is focused, in any app.

Speech recognition is [whisper.cpp](https://github.com/ggerganov/whisper.cpp)
running as a native binary on the phone. **Nothing is sent anywhere** — there
is no speech API behind this and no network call in the path. The model is
`ggml-base.en.bin`, 142 MB, downloaded once over Wi-Fi from whisper.cpp's own
published weights; the app is 2 MB without it. It is English-only.

It is not live dictation. Transcription takes a few seconds after you stop
talking — roughly 6 seconds for 11 seconds of speech on a Titan 2 Elite.

Recording holds the microphone while you are looking at another app, so it
runs as a foreground service with a notification you cannot dismiss while it
is listening. That notification is the point: something recording you in the
background should be impossible to miss. Recording stops at the second press,
and also if the room goes quiet for a couple of seconds, so a forgotten
recording cannot run forever.

## The two accessibility services, and what each can see

SideKey declares two, separately switched on, because they need very different
amounts of trust:

**SideKey** — drives back, home, recents, the notification shade, quick
settings, lock, screenshot and the power menu. It is **content-blind**: it
declares no ability to read window content, handles no events, and filters no
keys. It exists only to call `performGlobalAction`.

**SideKey Dictation** — types transcripts into the focused text box. This one
**can read screen content**, and Android will warn you about it in the
strongest terms it has, because writing into a field means first finding it.
It handles no events and does nothing on its own; it looks at the screen only
at the moment a finished transcript needs somewhere to go.

Keeping them apart is deliberate. Everything except dictation works with the
content-blind one alone, and if you never want an app that can see your screen
you never have to enable the other. Everything else in SideKey — torch, media,
apps, deep links, webhooks — needs neither.

## Where the mapping lives

The phone keeps it in `Settings.System`, as `func1_short_press_package` /
`func1_short_press_activity` and the `_long_` and `_double_` equivalents.
Reading them needs no permission; writing them from an app is impossible —
SettingsProvider refuses any `Settings.System` name outside its own public list
from an app targeting newer than Lollipop MR1, and the exemption is by uid, so
neither `WRITE_SETTINGS` nor `WRITE_SECURE_SETTINGS` helps. Both were tried.

adb is the shell uid, so it is exempt, if you would rather not tap:

```bash
adb shell settings put system func1_short_press_package com.snflist.sidekey
adb shell settings put system func1_short_press_activity com.snflist.sidekey.Slot1
```

## Building it yourself

Jetpack Compose and Material 3 (`material3` 1.4.0, via the Compose BOM). The
UI follows Material You: on Android 12 and up the palette is derived from your
wallpaper, and it follows the system light/dark setting.

R8 and resource shrinking are on for release builds — without them Compose
roughly doubles the APK and most of what it ships is never called. With them
it is about 1.3 MB.

Note that `MaterialExpressiveTheme` is internal as of material3 1.4.0:
expressive graduated into `MaterialTheme` itself when it went stable, so
`MaterialTheme` is the expressive one. Most tutorials still show the old entry
point and will not compile.

Built against AGP 9.4.0, Gradle 9.6.0, Kotlin 2.2.10 on Android Studio's
bundled JBR 25. Gradle 8.x will not run on JDK 25, so keep this toolchain or
supply a JDK 21.

```bash
cd ~/Documents/SideKey && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
```

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Signed with the debug key, which is fine for your own phone. Add
`keystore.properties` (storeFile / storePassword / keyAlias / keyPassword) next
to `app/build.gradle.kts` to sign it properly before giving it to anyone.

## Adding an action

One entry in `Catalogue.ALL` and one branch in `Act.dispatch`. That is the
whole extension point.
