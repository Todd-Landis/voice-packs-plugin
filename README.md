# Voice Packs

A RuneLite plugin that plays pre-recorded voice lines when things happen in game: level-ups, quests, diaries,
combat tasks, clues, collection log slots, boss kills, pets, big drops, deaths and logins. The lines come from
**voice packs**, which you install separately. The plugin ships with no voices of its own.

## Installing a pack

1. Open the **Voice Packs** panel in the sidebar (the speaker icon).
2. Click **Import pack (.zip)…** and pick the pack's zip.

The pack starts playing straight away. Installed packs are listed in the panel, each with a **Remove** button.
They live in `.runelite/plugin-data/voice_packs/packs/<pack id>/`.

## Settings

Volume, which packs are enabled, what happens when a line is already playing, the minimum gap between lines, and
subtitles in the chat box.

Lines never play during boss fights or raids. A line for something that happens there waits until you leave.

## Pack format

A pack is a folder (zipped for sharing) named after its id:

```
<id>/voicepack.json     id, name, version, the voice's provenance, and triggers
<id>/audio/*.wav        one clip per line: 16-bit PCM WAV, up to about 30 s at 24 kHz
```

Each trigger names an event (`level_up`, `quest_complete`, `boss_kc`, `pet`, …) and optional conditions
(`skill`, `level`, `boss`, `kc`, first time, account type, time of day, …), plus its lines. When several triggers
match, the most specific one with the highest priority plays. Packs are data only: no code, no network access.

`voice.likeness` and `voice.distribution` in the manifest record where a voice came from. The panel marks packs
whose distribution is `private`, so they don't get passed around.

## Chat commands

`::vp list` shows the loaded packs. `::vp demo` steps through a set of sample events, and
`::vp <event> field=value …` fakes one event (for example `::vp level_up skill=attack level=40`), so you can hear a
pack without grinding.
