**Title:** I got the Titan 2 Elite's side key to do things other than launch apps — SideKey, free and open source

---

The Func1 key is the reason I bought this phone and it drove me up the wall.
Unihertz gives you three presses — short, long, double — and every one of them
can only point at an app. I wanted a torch. I wanted play/pause. I wanted the
notification shade. Nope: pick an app.

So I spent a while trying to intercept the key properly, and I want to save
somebody else the trouble: **you can't.** The key belongs to
`com.agui.shortcutsettings`, a privileged system app in `/system_ext/priv-app`,
and it eats the press before any launcher, app or accessibility service ever
sees it. I gave an accessibility service `flagRequestFilterKeyEvents` and
logged every key it was allowed to see. Func1 never showed up once. That
absence *is* the answer.

But the shortcut settings will happily point the key at an app. So the trick is
to stop fighting it and make apps that aren't apps.

SideKey installs as ten launcher entries — `SideKey 1` through `SideKey 10`.
They all show up in Settings → Shortcut keys → All apps like any other app, but
each one is an invisible activity that works out which name you opened and runs
whatever you mapped it to. No root, nothing patched, no key interception.

In the app you tap a slot and pick what it does:

torch · play/pause · next/previous · ringer cycle · do not disturb · timer ·
alarm · camera · voice assistant · Wi-Fi panel · back · home · recents ·
notification shade · quick settings · lock · screenshot · power menu · call a
number · a webhook · any custom intent URI · any broadcast

Then you point short press, long press and double click at three different
slots. Ten slots, so the Sym and Fn keys can have some too.

**Deep links** work as well, which is the part I'm happiest with. "Open
something inside an app" lists every app offering a shortcut picker — a
specific WhatsApp chat, direct dial a contact, Maps directions, a Gmail label,
any Settings page. And because only about a dozen apps offer that, you can also
share any link into SideKey from any app's share sheet and it lands on a key.
Spotify playlist on the side key, that kind of thing.

Framework only — no AndroidX, no Compose, no libraries — so it's a 670 KB APK
and there's nothing running when you're not looking at it. Nothing is requested
at startup. The accessibility service is only needed for back/shade/screenshot/
lock, it's off until you turn it on, and it reads nothing: no window content,
no key filtering, no events.

**Download:** https://github.com/jakevibes/SideKey/releases/latest
**Source:** https://github.com/jakevibes/SideKey

Built and tested on a Titan 2 Elite on Android 16 and nothing else. Other
Unihertz models might be fine — the mechanism isn't Elite-specific — but I have
no way to check, so if you try one I'd genuinely like to know either way.

---

While I'm here: I've also been building **SquareOS**, a text-only launcher for
this phone that's aimed at actually getting less use out of it — type-to-launch
instead of an icon grid, a single command line for notes, to-dos, timers and
alarms, and a screen-time budget with real teeth. It's the daily driver on my
own Titan already. Coming soon.
