# Google Play publishing notes

Everything needed to fill in the Play Console for **The Great Quran / القرآن العظيم** (`com.readqurantoday.quran`).

## Store art

Made by `python tools/render_store_art.py` into `store/`:

- `icon-512.png` — app icon (512×512)
- `feature-graphic-1024x500.png` — feature graphic

Screenshots: take at least 2 phone screenshots on the device (menu, reader, player, dark mode, downloads).

### Taking screenshots

The set lives in `store/screenshots/ar/` and `store/screenshots/en/`, 2–8 per language, numbered in upload order. Play takes exactly 9:16 (or 16:9), each side 320–3840 px, so a tall phone (1440×3120) is shot at 1080×1920 instead:

```bash
adb shell wm size 1080x1920
adb shell wm density 420
```

Then, with the `.dev` build installed:

```bash
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png
```

Put the phone back afterwards; a restart also undoes both:

```bash
adb shell wm size reset
adb shell wm density reset
```

Play takes PNG only without transparency, and `screencap` writes it with an alpha channel: save each one again as 24-bit before uploading.

Tips: a tap opens the menus but they close themselves a moment later, so open them and capture straight away. A recitation started mid-page puts the lit word, with its phonetic label, in the middle of the shot.

### Promo video (optional)

Play takes a YouTube link, not a file: **Grow users → Store presence → Main store listing → Video**. It plays above the screenshots, from the feature graphic.

- Upload to YouTube as **Public** or **Unlisted**, never Private.
- **Monetisation off** (no ads), not age-restricted, embedding allowed.
- Paste the plain watch link, e.g. `https://www.youtube.com/watch?v=…`, not a Shorts or playlist link.
- 30 seconds to 2 minutes; the first seconds matter most, so open on the recited word lighting up.
- Landscape (16:9) fills the player best. A portrait phone recording works, but plays boxed with bars on the sides.
- One video serves every language. Captions in both Arabic and English, or a separate video per custom store listing.

## Store listing

**App name:** The Great Quran — القرآن العظيم

**Short description (≤ 80 chars)**

- en: The Madinah Mushaf as printed, with each word lit as it is recited.
- ar: مصحف المدينة كما هو مطبوع، وتُضاء كل كلمة لحظة تلاوتها.

**Full description**

en:

> The Great Quran shows the Madinah Mushaf exactly as printed, page by page, in clear sharp type on any screen.
>
> - Pinch to zoom up to three times, and the text stays sharp at every size
> - Each word lights up as it is recited, with its pronunciation in Latin letters above it
> - Hear any word on its own to learn how it is said
> - Hold any word to start the recitation from it
> - Repeat an ayah, a page or a surah to memorise, and set the recitation speed
> - Five recitations: Maher al-Muaiqly (two), Mishari al-Afasy, Yasser Ad-Dussary, Saad al-Ghamdi
> - Download for offline listening, or save the recitation to your phone
> - Keeps playing with the screen off, with controls on the lock screen and in quick settings
> - Search the Quran by word, surah or page
> - Save pages and continue where you left off
> - Light, dark or follow the phone, with your own colours and text weight
> - Turn pages by sliding or with a paper page-turn
> - Arabic and English interface

ar:

> يعرض «القرآن العظيم» مصحف المدينة كما هو مطبوع، صفحةً صفحة، بخط واضح على كل الشاشات.
>
> - كبّر الصفحة بإصبعين حتى ثلاثة أضعاف، ويبقى الخط واضحًا حادًّا في كل حجم
> - تُضاء كل كلمة لحظة تلاوتها، ويظهر نطقها بالحروف اللاتينية فوقها
> - استمع إلى أي كلمة وحدها لتتعلّم نطقها
> - اضغط مطوّلًا على أي كلمة لتبدأ التلاوة منها
> - تكرار الآية أو الصفحة أو السورة للحفظ، مع التحكّم في سرعة التلاوة
> - خمس تلاوات: ماهر المعيقلي (تلاوتان)، مشاري العفاسي، ياسر الدوسري، سعد الغامدي
> - التنزيل للاستماع دون إنترنت، أو حفظ التلاوة في الهاتف
> - تستمر التلاوة والشاشة مغلقة، مع أزرار التحكّم في شاشة القفل ولوحة الإعدادات السريعة
> - البحث في القرآن بالكلمة أو السورة أو الصفحة
> - حفظ الصفحات والمتابعة من حيث توقفت
> - مظهر فاتح وداكن وحسب النظام، مع تخصيص الألوان وسماكة الخط
> - تقليب الصفحات بالسحب أو بحركة تقليب الورق
> - واجهة عربية وإنجليزية

**Category:** Books & Reference  **Contact email:** readqurantoday@outlook.com  **Website:** https://readqurantoday.com

**Privacy policy URL:** https://readqurantoday.com/privacy/

## App content answers

**Ads:** No ads.

**App access:** All features available without login.

**Target audience:** 13+ (not designed for children, so the Families policy does not apply). Religious text, suitable for everyone.

**Content rating questionnaire:** Category *Reference, News, or Educational*. Answer **No** to violence, sexual content, language, drugs, gambling, user-to-user interaction, location sharing, and purchases. Expected result: Everyone / PEGI 3.

**Government app / financial / health features:** No.

### Data safety

Data collected: **Yes**. Shared with third parties: **No**. Encrypted in transit: **Yes** (HTTPS). Users can request deletion: **Yes** (by email).

Only collected when the user sends a report from *Settings → Report an issue*. Sending one is optional; purpose is **App functionality** only:

| Data type (Play name) | Collected | Required? | Purpose |
|---|---|---|---|
| Personal info → Email address | Yes | Optional | Reply to the report |
| App activity → Other user-generated content (the message) | Yes | Optional (only if user sends) | App functionality |
| App info and performance → Diagnostics (app version, device model, Android version, theme, reciter, screen size, last page) | Yes | Optional (only if user sends) | App functionality |
| Device or other IDs | No | | |
| Location, contacts, files, photos, financial, health | No | | |

The IP address is kept with a report only to block spam and is not used to find location, so declare **No** for location.

Reading position, bookmarks, settings and downloads stay on the device and are not "collected".

### Foreground service declaration

Type **Media playback** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`).

> The app plays Quran recitation audio chosen by the user. Playback continues when the screen is off or the user leaves the app, with a media notification to pause, skip, or stop. Without the foreground service, audio would stop when the app goes to the background.

Video of the feature (if asked): screen recording showing tapping play in the reader, going to the home screen, and the notification controls.

### Permissions

- `INTERNET` — stream and download recitations, send reports
- `POST_NOTIFICATIONS` — declared but never asked for: the player's lock-screen and quick-settings controls come from its media session, which needs no permission, and download progress is shown by Android's own downloads
- `WRITE_EXTERNAL_STORAGE` (maxSdk 28) — save audio to Download on Android 9 and older

## Content rights checklist

Before publishing confirm, and be ready to show if Play asks:

- [ ] **KFGQPC Madinah Mushaf fonts** (King Fahd Glorious Quran Printing Complex) — free for distribution unmodified; keep their credit in the app.
- [ ] **Reciter audio** — permission or licence from each audio source used on the CDN.
- [ ] **Tanzil Quran text** — CC BY 3.0, used unmodified, credited in *Settings → Credits*.
- [ ] Cairo font (OFL 1.1), AndroidX / Media3 / ColorPicker (Apache 2.0) — credited in the app.

## Releasing

Build and upload steps, versioning and the upload key: see [RELEASE.md](RELEASE.md).
