# Checks that no letter or mark of any page comes near the page frame or a surah band.
# Repeats MushafPageView's layout (layOut, drawLine, basmalah) and measures each glyph's ink, colour layers included,
# against the frame's fine inner rule and each surah band's edges, for several screens, in both editions.
# Usage: python tools/frame_clearance.py [--near DP]   (lists pages closer than DP, default 2; exits 1 if any ink touches)
import json, os, sys
from fontTools.ttLib import TTFont
from fontTools.pens.boundsPen import BoundsPen

MAIN = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main')
NEAR = float(sys.argv[sys.argv.index('--near') + 1]) if '--near' in sys.argv else 2.0

# MushafPageView's constants
PAD_X, PAD_TOP, PAD_BOTTOM, HEAD_BAND = 14, 8, 32, 30
GRID, TALL_FROM, READING_ROW, MIN_FIT_BODY = 15, 0.6, 0.45, 20
BAND, BAND_TALL, FRAME_EDGE = 0.7, 0.86, 1.2
CENTRE_GAP, INNER_SPACE = 0.32, 0.04
# The boldest ink setting's nudge, as a share of the type size
SPREAD = 0.018

# Width, height in px and density: the test phone, a common phone, a small one, a foldable, a tablet both ways
SCREENS = {
    'phone 1440x3120': (1440, 3120, 3.75),
    'phone 1080x2400': (1080, 2400, 2.625),
    'small 720x1280': (720, 1280, 2.0),
    'fold 2208x1840': (2208, 1840, 2.625),
    'tablet 1600x2560': (1600, 2560, 2.0),
    'tablet 2560x1600': (2560, 1600, 2.0),
}

EDITIONS = [
    ('plain', 'data/mushaf.json', 'v4', 'fonts-qcf4', lambda n: f'q{n}.ttf', lambda n: f'gq{n}.txt'),
    ('tajweed', 'data/mushaf-tajweed.json', 'v2', 'fonts-tajweed', lambda n: f'tj{n}.otf', lambda n: f'gt{n}.txt'),
]


class Face:
    """One page font: its glyph table, its line metrics as Paint reads them, and each glyph's ink box."""

    def __init__(self, font_path, table_path):
        rows = open(table_path, encoding='ascii').read().split()
        self.upem = float(rows[0])
        self.gid, self.adv = {}, {}
        for i in range(1, len(rows), 3):
            cp = int(rows[i])
            self.gid[cp], self.adv[cp] = int(rows[i + 1]), int(rows[i + 2])
        f = TTFont(font_path, lazy=True)
        os2 = f['OS/2']
        if os2.fsSelection & (1 << 7):
            self.ascent, self.descent = os2.sTypoAscender, os2.sTypoDescender
        else:
            self.ascent, self.descent = f['hhea'].ascent, f['hhea'].descent
        order = f.getGlyphOrder()
        layers = f['COLR'].ColorLayers if 'COLR' in f else {}
        glyf = f['glyf'] if 'glyf' in f else None
        glyphset = None if glyf else f.getGlyphSet()
        hmtx = f['hmtx']

        def bounds(part):
            if glyf:
                g = glyf[part]
                if not g.numberOfContours: return None
                # Placed as the renderer places it: its left edge at its side bearing, not at its raw points
                lsb = hmtx[part][1]
                return lsb, g.yMin, lsb + g.xMax - g.xMin, g.yMax
            pen = BoundsPen(glyphset)
            glyphset[part].draw(pen)
            return pen.bounds

        self.box = {}
        for gid in set(self.gid.values()):
            name = order[gid]
            parts = [b for b in map(bounds, [name] + [l.name for l in layers.get(name, [])]) if b]
            self.box[gid] = (min(b[0] for b in parts), min(b[1] for b in parts),
                             max(b[2] for b in parts), max(b[3] for b in parts)) if parts else None

    def width(self, word, size):
        return sum(INNER_SPACE * size if cp not in self.gid else self.adv[cp] * size / self.upem for cp in map(ord, word))

    # Ink of [word] drawn right to left from [right] on baseline [y]: (left, top, right, bottom) in px, y down
    def ink(self, word, right, y, size):
        s, pen, out = size / self.upem, right, None
        for cp in map(ord, word):
            if cp not in self.gid:
                pen -= INNER_SPACE * size
                continue
            pen -= self.adv[cp] * s
            b = self.box[self.gid[cp]]
            if b:
                g = (pen + b[0] * s, y - b[3] * s, pen + b[2] * s, y - b[1] * s)
                out = g if out is None else (min(out[0], g[0]), min(out[1], g[1]), max(out[2], g[2]), max(out[3], g[3]))
        return out, pen


# Each page's ink boxes in units of its type size, as (row, left, top, right, bottom): x from the text's right edge,
# y from the row's middle. The measure is always em type sizes wide, so these hold on every screen.
def page_inks(data, key, em, centre_below, face, bas_face):
    pages = {}
    for page in range(1, len(data['fonts']) + 1):
        f = face(data['fonts'][page - 1])
        centre = (f.ascent + f.descent) / f.upem / 2
        inks, bands = [], []
        for row, line in enumerate(data['pages'][str(page)][:GRID]):
            text = line.get(key, '')
            if line['t'] == 'surah':
                bands.append(row)
            elif line['t'] == 'basmalah' and text:
                words = text.split('|')
                pen = em / 2 + sum(bas_face.width(wd, 1) for wd in words) / 2
                for wd in words:
                    box, pen = bas_face.ink(wd, pen, centre, 1)
                    if box: inks.append((row,) + box)
            elif text:
                words = text.split('|')
                size = 1
                natural = sum(f.width(wd, size) for wd in words)
                if natural > em:
                    size = em / natural * 0.995
                    natural = sum(f.width(wd, size) for wd in words)
                gaps = len(words) - 1
                spare = max(em - natural, 0)
                short = natural < em * centre_below
                gap = 0 if gaps <= 0 else (min(CENTRE_GAP * size, spare / gaps) if short else spare / gaps)
                x = (em + natural + gap * gaps) / 2 if short else em
                for wd in words:
                    box, pen = f.ink(wd, x, centre, size)
                    if box: inks.append((row,) + box)
                    x = pen - gap
        pages[page] = (inks, bands)
    return pages


def edition(name, asset, key, folder, font_name, table_name):
    data = json.load(open(os.path.join(MAIN, 'assets', asset), encoding='utf-8'))
    em, centre_below = data['fit']['body'][key], data['fit']['centreBelow'][key]
    faces = {}

    def face(n):
        if n not in faces:
            faces[n] = Face(os.path.join(MAIN, folder, font_name(n)), os.path.join(MAIN, folder, table_name(n)))
        return faces[n]

    bas_font = data['basmalah'].get('font', 1)
    pages = page_inks(data, key, em, centre_below, face, face(bas_font))
    touches = 0
    print(f'\n=== {name} ({len(pages)} pages) ===')
    for screen, (w, h, d) in SCREENS.items():
        pad_x, top = PAD_X * d, (PAD_TOP + HEAD_BAND) * d
        body, side = (w - 2 * pad_x) / em, pad_x
        fit = (h - top - PAD_BOTTOM * d) / GRID
        if fit > 0 and body > fit * TALL_FROM and fit * TALL_FROM >= MIN_FIT_BODY * d:
            body = fit * TALL_FROM
            side = (w - body * em) / 2
        step = fit if fit > 0 and body / fit <= TALL_FROM else body / READING_ROW
        # The frame's fine rule: the head band's foot, the foot band's head, and each side band's inner edge
        rule_top, rule_bottom = top, top + step * GRID
        rule_side = pad_x * (1 - BAND)
        band_half = step * BAND_TALL / 2
        spread = body * SPREAD

        worst = {k: (1e9, 0) for k in ('top', 'bottom', 'sides', 'surah band')}
        near = []
        for page, (inks, bands) in pages.items():
            for row, l, t, r, b in inks:
                mid = top + step * row + step / 2
                l, t, r, b = body * l - spread, mid + body * t - spread, body * r + spread, mid + body * b + spread
                gaps = [('top', t - rule_top), ('bottom', rule_bottom - b), ('sides', rule_side + min(l, body * em - r))]
                for brow in bands:
                    if abs(brow - row) == 1:
                        bmid = top + step * brow + step / 2
                        gaps.append(('surah band', bmid - band_half - b if row < brow else t - bmid - band_half))
                for k, gap in gaps:
                    if gap < worst[k][0]: worst[k] = (gap, page)
                    if gap / d < NEAR: near.append((page, k, row + 1, gap / d))
        print(f'{screen}: ' + ', '.join(f'{k} {g / d:.1f}dp (p{p})' for k, (g, p) in worst.items()))
        for page, k, row, gap in sorted(set(near), key=lambda n: n[3])[:20]:
            print(f'    page {page} row {row}: {k} {gap:.1f}dp' + ('  TOUCHES' if gap <= 0 else ''))
        touches += sum(1 for n in near if n[3] <= 0)
    return touches


sys.exit(1 if sum(edition(*e) for e in EDITIONS) else 0)
