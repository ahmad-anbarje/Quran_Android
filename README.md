# القرآن العظيم · The Great Quran

The Madinah Mushaf on Android, drawn page by page as it is printed, with recitation that follows the words.

Native Kotlin and Android Views, no web view. The pages are drawn from the mushaf's own fonts, so the type is as sharp as the screen allows and nothing reflows. A companion of [readqurantoday.com](https://readqurantoday.com).

## What it does

**Reading**
- All 604 pages exactly as printed, one page per screen.
- Turn by sliding the page, or by tipping it over like paper — your choice in settings.
- Pinch to zoom in on a line; let go part-way back and the page settles to full size.
- Tap once for the menus, tap again to send them away; they also leave when you turn the page.
- Saved pages, recently read surahs, and a card that takes you back where you left off.

**Recitation**
- Five reciters, with the word being recited lit on the page as it is read.
- Hold a word to start from it, or play a whole surah or juz.
- Repeat an ayah, a page, or a surah.
- Keeps playing with the screen off, with full controls in the notification shade and on the lock screen: previous and next ayah, seek, pause, close.

**Finding your place**
- Search by surah name, page number, or a word from an ayah.
- The full surah list, and the thirty juz, each opening — or reciting — from its first ayah.

**Making it yours**
- Light and dark, or follow the phone.
- Page colour, text colour, and the colour of the ayah and page numbers.
- Text weight for the words, the numbers and the highlight.
- Arabic and English interface.

**Offline**
- The whole mushaf is inside the app: no download on first open, and no network to read.
- Recitations stream, or download for listening offline, and can be copied to the phone's Download folder to play anywhere.

## Built from

- **Text and glyph layout**: the KFGQPC Madinah Mushaf fonts, one per page, laid out line by line and fitted to the sheet.
- **Ayah text and search**: the Tanzil Quran text (CC BY 3.0), used unmodified.
- **Audio**: recitations served from the project's own CDN, with word timings per reciter.
- **Libraries**: AndroidX, Media3 (ExoPlayer) for playback, and a colour picker. No analytics, no ads, no accounts.

## Recitation data

Each recitation is one recording per surah plus a timing file that says where every ayah and word falls in that recording. A timing fits only the recording it was made against, so the two always go as a pair: the timing file names its audio.

### Where to get them

For surah `<n>` (`<nnn>` is the same number padded to three digits) and reciter `<id>`:

| | Address |
|---|---|
| Audio | `https://audio.readqurantoday.com/<id>/<nnn>.mp3` |
| Timing, online | `https://readqurantoday.com/surah/<n>/<nnn>.<id>.timing.json` |
| Timing, in this repo | `app/src/main/assets/surah/<n>/<nnn>.<id>.timing.json` |

The app and the website use the same timing files: one per surah per recitation, 114 × 5.

### The recitations

| `id` | Reciter | Recording | Timings from | Al-Fatihah: audio · timing |
|---|---|---|---|---|
| `maher-al-muaiqly-qul` | Maher al-Muaiqly | Alharamain (default) | QUL | [001.mp3](https://audio.readqurantoday.com/maher-al-muaiqly-qul/001.mp3) · [timing](https://readqurantoday.com/surah/1/001.maher-al-muaiqly-qul.timing.json) |
| `maher-al-muaiqly` | Maher al-Muaiqly | 1440 AH | quran.com | [001.mp3](https://audio.readqurantoday.com/maher-al-muaiqly/001.mp3) · [timing](https://readqurantoday.com/surah/1/001.maher-al-muaiqly.timing.json) |
| `mishari-rashid-al-afasy` | Mishari Rashid al-Afasy | Murattal | QUL | [001.mp3](https://audio.readqurantoday.com/mishari-rashid-al-afasy/001.mp3) · [timing](https://readqurantoday.com/surah/1/001.mishari-rashid-al-afasy.timing.json) |
| `yasser-ad-dussary` | Yasser Ad-Dussary | Murattal | QUL | [001.mp3](https://audio.readqurantoday.com/yasser-ad-dussary/001.mp3) · [timing](https://readqurantoday.com/surah/1/001.yasser-ad-dussary.timing.json) |
| `saad-al-ghamdi` | Saad al-Ghamdi | Murattal | QUL | [001.mp3](https://audio.readqurantoday.com/saad-al-ghamdi/001.mp3) · [timing](https://readqurantoday.com/surah/1/001.saad-al-ghamdi.timing.json) |

For any other surah, change the number. Al-Baqarah with Mishari al-Afasy, for example, is [002.mp3](https://audio.readqurantoday.com/mishari-rashid-al-afasy/002.mp3) with [002.mishari-rashid-al-afasy.timing.json](https://readqurantoday.com/surah/2/002.mishari-rashid-al-afasy.timing.json). The list the app offers is `app/src/main/assets/data/recitations.json`.

The two Maher recordings are different recitations, not two copies of one: each has its own audio folder and its own timings, and neither fits the other.

### Inside a timing file

Al-Fatihah with Mishari al-Afasy, shortened to its first two ayahs:

```json
{
  "surah": 1,
  "reciter": "Mishari Rashid al-Afasy",
  "source": "qul.tarteel.ai",
  "sourceAudio": "https://audio-cdn.tarteel.ai/quran/surah/alafasy/murattal/mp3/001.mp3",
  "audioPath": "mishari-rashid-al-afasy/001.mp3",
  "duration": 46.524,
  "ayah": [[0, 5735], [5935, 11289]],
  "word": [[0, 0, 800, 1, 1600, 2, 2760, 3], [585, 0, 1345, 1, 2225, 2, 2865, 3]]
}
```

- `audioPath`: the recording this timing belongs to, under `https://audio.readqurantoday.com/`. The app plays exactly this file.
- `sourceAudio`: where that recording first came from; ours is a copy of it.
- `duration`: the recording's length in seconds.
- `ayah`: one `[start, end]` per ayah, in milliseconds from the start of the recording.
- `word`: one list per ayah of `time, word` pairs. The time is in milliseconds from that ayah's start, and the word counts from 0 in the order the page prints them. In ayah 2 above, word 0 starts 585 ms into the ayah, word 1 at 1345 ms, and so on.

### Where the timings come from

The timings are built in the web project from QUL exports (`qul/<id>/surah.json` and `segments.json`, via `scripts/import-qul-timing.js`), then copied here unchanged. `surah.json` is what ties each timing to its recording: it names the audio the words were segmented against. One recitation, `maher-al-muaiqly`, has no QUL segmentation and is timed from quran.com instead.

The mushaf prints بَعْدَ مَا (2:181, 8:6, 13:37) as one slot, but it is recited as two words, so both readers number it as two and light each half on its own. The list lives in `Mushaf.kt` and in the web's `mushaf.js`; keep them the same.

## Building

Open the project in Android Studio and run. Everything the app reads is in `app/src/main/assets`, so there is nothing to fetch first.

- minSdk 24, target 36.
- Release steps, signing and versioning: [docs/RELEASE.md](docs/RELEASE.md).
- Play Console answers and store text: [docs/PLAY_STORE.md](docs/PLAY_STORE.md).
- House rules for the code and the design: [CLAUDE.md](CLAUDE.md).

Store art is generated from the app's own icon paths:

```bash
python tools/render_store_art.py
```

## Privacy

Nothing leaves the phone unless you send it. Reading position, bookmarks and settings stay on the device; a report you choose to send from Settings carries your message and the app and device details, and nothing else. Full text: [readqurantoday.com/privacy](https://readqurantoday.com/privacy/).
