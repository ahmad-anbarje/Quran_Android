"""Screenshot every main screen of the debug app on the connected phone, posed as other devices.

    python tools/screens.py              # all profiles
    python tools/screens.py tablet car   # just these

The phone is resized with `wm size` / `wm density` and the font scaled, one profile at a time,
then put back as it was, even on failure. One sheet per profile lands in build/screens/.
Wireless debugging works too; set ANDROID_SERIAL when more than one device is attached.
"""
import os
import re
import subprocess
import sys
import time

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(ROOT, 'build', 'screens')
APK = os.path.join(ROOT, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
APP = 'com.readqurantoday.quran.dev'
SDK = os.environ.get('ANDROID_HOME') or os.path.join(os.environ.get('LOCALAPPDATA', ''), 'Android', 'Sdk')
ADB = os.path.join(SDK, 'platform-tools', 'adb')

# name: (size in px or None for the phone's own, density or None, font scale); sizes in dp are what layouts see
# Landscape poses squeeze a portrait panel, so their top gap is the phone's camera cutout, not the app
PROFILES = {
    'phone': (None, None, 1.0),
    'small-big-font': (None, 'narrow', 1.5),   # a 320dp phone with the largest font
    'foldable': ('1768x2208', 420, 1.0),       # a book-style foldable opened, about 670x840dp
    'tablet': ('1600x2560', 320, 1.0),         # 800x1280dp
    'tablet-landscape': ('2560x1600', 320, 1.0),
}


def adb(*args, binary=False):
    out = subprocess.run([ADB, *args], capture_output=True, check=False).stdout
    return out if binary else out.decode('utf-8', 'replace')


def shell(*args):
    return adb('shell', *args)


def focused():
    return APP in shell('dumpsys', 'window').split('mCurrentFocus', 1)[-1].split('\n', 1)[0]


def tap(resource_id):
    dump = adb('exec-out', 'uiautomator', 'dump', '/dev/tty')
    m = re.search(rf'id/{resource_id}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', dump)
    if not m:
        print(f'  not on screen: {resource_id}')
        return False
    x1, y1, x2, y2 = map(int, m.groups())
    # Taps only land in the app; a stray tap elsewhere could act on another app
    if focused():
        shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(2)
    return True


def shot(frames):
    frames.append(adb('exec-out', 'screencap', '-p', binary=True))


def walk():
    """Each main screen, in order."""
    frames = []
    shell('am', 'force-stop', APP)
    shell('monkey', '-p', APP, '-c', 'android.intent.category.LAUNCHER', '1')
    time.sleep(3)
    shot(frames)
    tap('seg_juz'); shot(frames)
    tap('nav_marks'); shot(frames)
    tap('nav_stats'); shot(frames)
    tap('nav_settings'); shot(frames)
    tap('nav_surahs')
    # Short screens hide the resume strip, so the first surah opens the reader instead
    if not tap('card_resume'):
        tap('num')
    time.sleep(1); shot(frames)
    if focused():
        w, h = screen_px()
        shell('input', 'tap', str(w // 2), str(h // 2))
    time.sleep(2); shot(frames)
    return frames


def screen_px():
    size = shell('wm', 'size')
    m = re.search(r'Override size: (\d+)x(\d+)', size) or re.search(r'Physical size: (\d+)x(\d+)', size)
    return int(m.group(1)), int(m.group(2))


def sheet(frames, path):
    """Frames side by side, wrapped into rows so a landscape set stays readable."""
    import io
    tall = 1000
    ims = [Image.open(io.BytesIO(f)).convert('RGB') for f in frames if f]
    ims = [i.resize((int(i.width * tall / i.height), tall)) for i in ims]
    per_row = max(1, 4000 // (ims[0].width + 16)) if ims else 1
    rows = [ims[k:k + per_row] for k in range(0, len(ims), per_row)]
    wide = max(sum(i.width + 16 for i in r) for r in rows)
    out = Image.new('RGB', (wide, len(rows) * (tall + 16)), (200, 40, 40))
    for n, r in enumerate(rows):
        x = 0
        for i in r:
            out.paste(i, (x, n * (tall + 16)))
            x += i.width + 16
    out.save(path)


def pose(size, density, font):
    w, _ = map(int, re.search(r'Physical size: (\d+)x(\d+)', shell('wm', 'size')).groups())
    own = int(re.search(r'Physical density: (\d+)', shell('wm', 'density')).group(1))
    if density == 'narrow':
        density = w * 160 // 320
    if size:
        shell('wm', 'size', size)
    shell('wm', 'density', str(density or own))
    shell('settings', 'put', 'system', 'font_scale', str(font))
    time.sleep(2)


def restore(font):
    shell('wm', 'size', 'reset')
    shell('wm', 'density', 'reset')
    shell('settings', 'put', 'system', 'font_scale', font)


def main():
    wanted = sys.argv[1:] or list(PROFILES)
    os.makedirs(OUT, exist_ok=True)
    if os.path.exists(APK):
        print('installing', adb('install', '-r', APK).strip().splitlines()[-1])
    font = shell('settings', 'get', 'system', 'font_scale').strip() or '1.0'
    try:
        for name in wanted:
            print(name)
            pose(*PROFILES[name])
            sheet(walk(), os.path.join(OUT, f'{name}.png'))
    finally:
        restore(font)
    print('sheets in', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
