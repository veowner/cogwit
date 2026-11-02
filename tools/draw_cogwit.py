"""Draws Cogwit, the copper clockwork companion, as 16x16 item sprites in three body variants and several moods.

Usage: draw_cogwit.py <preview_png> [<texture_dir> <variant>]
  Without a texture dir it only renders the comparison sheet. With one, it writes the talisman textures
  (cogwit_talisman*.png) for the chosen variant.

Variants:
  A "Cog"     the body is a gear with eight teeth and one round lens in the middle, two piston legs
  B "Boiler"  a small riveted boiler with a lens window, a cog turning on its lid, thin arms and stubby legs
  C "Owl"     a clockwork owl: gear-tooth ear tufts, two goggle lenses, a brass beak, folded plate wings
Palette: copper with a teal patina, the lens glows cyan (violet in Overdrive).
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUTLINE = (66, 36, 22, 255)
SHADOW = (128, 68, 38, 255)
BASE = (186, 112, 62, 255)
LIGHT = (224, 156, 98, 255)
SHINE = (250, 212, 162, 255)
PATINA = (64, 148, 132, 255)
PATINA_LIGHT = (118, 196, 176, 255)
RING = (40, 30, 30, 255)
GLASS = {"calm": (96, 222, 226, 255), "overdrive": (196, 96, 255, 255), "sleep": (40, 92, 96, 255)}
GLASS_HI = {"calm": (214, 255, 250, 255), "overdrive": (246, 210, 255, 255), "sleep": (70, 130, 130, 255)}
PUPIL = (18, 34, 44, 255)
BRASS = (232, 186, 70, 255)

MOODS = ["calm", "blink", "happy", "scared", "think", "sleep", "overdrive"]


def shade_body(px, mask, cx, cy):
    """Fills a body mask in copper, lit from the top left, with a dark outline where it meets empty space."""
    for (x, y) in mask:
        t = (x - cx) + (y - cy)
        px[x, y] = LIGHT if t < -3 else SHADOW if t > 3 else BASE
    for (x, y) in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in mask:
                px[x, y] = OUTLINE
                break


def lens(px, cx, cy, r, mood, small=False):
    """A round lens: dark ring, glowing glass, and a pupil that carries the mood (one pixel on a small lens)."""
    glass_key = "overdrive" if mood == "overdrive" else "sleep" if mood == "sleep" else "calm"
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                px[x, y] = RING if d > r - 0.9 else GLASS[glass_key]
    ix, iy = int(cx - 0.5), int(cy - 0.5)
    if small:
        if mood in ("calm", "scared", "think"):
            px[ix, iy] = PUPIL
            px[ix - 1 if mood != "think" else ix, iy - 1] = GLASS_HI["calm"]
        elif mood == "overdrive":
            px[ix, iy] = GLASS_HI["overdrive"]
        elif mood == "happy":
            px[ix - 1, iy] = px[ix, iy - 1] = px[ix + 1, iy] = PUPIL
        else:
            px[ix - 1, iy] = px[ix, iy] = px[ix + 1, iy] = PUPIL
        return
    if mood == "calm" or mood == "overdrive":
        for x, y in ((ix, iy), (ix + 1, iy), (ix, iy + 1), (ix + 1, iy + 1)):
            px[x, y] = PUPIL if mood == "calm" else GLASS_HI["overdrive"]
        px[ix - 1, iy - 1] = GLASS_HI[glass_key]
    elif mood == "blink":
        for x in range(int(cx - r + 1), int(cx + r)):
            for y in range(int(cy - r + 1), int(cy + r)):
                if px[x, y] in (GLASS["calm"],):
                    px[x, y] = SHADOW
        for x in range(int(cx - r + 1), int(cx + r)):
            px[x, iy + 1] = RING
    elif mood == "happy":
        for x, y in ((ix - 1, iy + 1), (ix, iy), (ix + 1, iy), (ix + 2, iy + 1)):
            px[x, y] = PUPIL
    elif mood == "scared":
        px[ix, iy] = PUPIL
        for x, y in ((ix - 1, iy - 1), (ix + 1, iy - 1)):
            px[x, y] = GLASS_HI["calm"]
    elif mood == "think":
        px[ix - 1, iy - 1] = PUPIL
        px[ix, iy - 1] = PUPIL
    elif mood == "sleep":
        for x in range(ix - 1, ix + 3):
            px[x, iy + 1] = PUPIL


def variant_a(mood):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 8.0, 7.0
    mask = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            ang = (math.degrees(math.atan2(dy, dx)) + 360) % 45
            tooth = min(ang, 45 - ang) < 9
            if d <= 5.3 or (tooth and d <= 7.0 and y <= 12):
                mask.add((x, y))
    shade_body(px, mask, cx, cy)
    for x, y in ((10, 10), (11, 9), (10, 11)):
        px[x, y] = PATINA
    px[11, 10] = PATINA_LIGHT
    if mood == "overdrive":
        for (x, y) in mask:
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > 5.6 and px[x, y] != OUTLINE:
                px[x, y] = GLASS["overdrive"]
    lens(px, cx, cy, 3.3, mood)
    for lx in (5, 10):
        px[lx, 13] = SHADOW
        px[lx, 14] = SHADOW
        px[lx, 15] = OUTLINE
        px[lx + (1 if lx > 8 else -1), 15] = OUTLINE
    return img


def variant_b(mood):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    mask = set()
    for y in range(4, 14):
        for x in range(3, 13):
            corner = (y == 4 and x in (3, 4, 11, 12)) or (y == 5 and x in (3, 12)) or (y == 13 and x in (3, 12))
            if not corner:
                mask.add((x, y))
    shade_body(px, mask, 7.5, 8.5)
    for x in range(4, 12):
        px[x, 11] = SHADOW
    for x in (4, 6, 9, 11):
        px[x, 11] = SHINE
    for x, y in ((4, 12), (5, 12), (4, 13)):
        px[x, y] = PATINA
    px[5, 13] = PATINA_LIGHT
    # the little cog on the lid
    for x, y in ((7, 3), (8, 3), (6, 2), (9, 2), (7, 1), (8, 1), (7, 2), (8, 2)):
        px[x, y] = BRASS if (x, y) not in ((7, 2), (8, 2)) else SHADOW
    px[6, 1] = px[9, 1] = px[6, 3] = px[9, 3] = (0, 0, 0, 0)
    if mood == "overdrive":
        px[7, 1] = px[8, 1] = GLASS["overdrive"]
    lens(px, 8.0, 7.8, 3.0, mood)
    for y in (8, 9, 10):
        px[2, y] = SHADOW
        px[13, y] = SHADOW
    px[2, 10] = px[13, 10] = OUTLINE
    for lx in (5, 10):
        px[lx, 14] = SHADOW
        px[lx, 15] = OUTLINE
    return img


def variant_c(mood):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 8.0, 8.5
    mask = set()
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / 4.6, (y + 0.5 - cy) / 6.0
            if dx * dx + dy * dy <= 1 and y <= 14:
                mask.add((x, y))
    for x, y in ((4, 2), (4, 3), (5, 3), (11, 2), (11, 3), (10, 3)):
        mask.add((x, y))
    shade_body(px, mask, cx, cy)
    # folded plate wings: darker bands on both sides
    for y in range(8, 13):
        px[4, y] = SHADOW
        px[11, y] = SHADOW
    px[5, 12] = PATINA
    px[10, 12] = PATINA
    px[5, 11] = PATINA_LIGHT
    for x, y in ((7, 10), (8, 10), (7, 11)):
        px[x, y] = BRASS
    lens(px, 5.6, 7.0, 2.1, mood, small=True)
    lens(px, 10.4, 7.0, 2.1, mood, small=True)
    for lx in (6, 9):
        px[lx, 15] = BRASS
        px[lx + 1, 15] = BRASS
    return img


def eyes_pair(px, mood, left=5, right=9, y=6):
    """Two 2x2 lens eyes for the boxy bodies; the mood lives in which pixels stay lit."""
    glass = GLASS["overdrive"] if mood == "overdrive" else GLASS["sleep"] if mood == "sleep" else GLASS["calm"]
    for ex in (left, right):
        cells = [(ex, y), (ex + 1, y), (ex, y + 1), (ex + 1, y + 1)]
        if mood == "blink" or mood == "sleep":
            px[ex, y + 1] = px[ex + 1, y + 1] = PUPIL if mood == "blink" else glass
        elif mood == "happy":
            px[ex, y + 1] = glass
            px[ex + 1, y] = glass
            px[ex, y] = glass
            px[ex + 1, y + 1] = glass
            px[ex, y + 1] = PUPIL
            px[ex + 1, y + 1] = PUPIL
            px[ex, y] = px[ex + 1, y] = glass
        elif mood == "scared":
            for c in cells:
                px[c] = GLASS_HI["calm"]
            px[ex, y + 1] = PUPIL
        elif mood == "think":
            for c in cells:
                px[c] = glass
            px[ex, y] = PUPIL
        else:
            for c in cells:
                px[c] = glass
            px[ex, y] = GLASS_HI["overdrive" if mood == "overdrive" else "calm"]
            if mood == "calm":
                px[ex + 1, y + 1] = PUPIL


def box_body(px):
    """The block body the old mascot had: 10 by 7, standing on its legs, now in riveted copper."""
    mask = {(x, y) for x in range(3, 13) for y in range(4, 11)}
    shade_body(px, mask, 7.5, 7.0)
    for x, y in ((4, 5), (11, 5), (4, 9), (11, 9)):
        px[x, y] = SHINE
    return mask


def variant_f(mood):
    """Closest: the old silhouette (box, side nubs, four legs) recoloured copper with lens eyes."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    box_body(px)
    for x, y in ((1, 7), (2, 7), (1, 8), (2, 8), (13, 7), (14, 7), (13, 8), (14, 8)):
        px[x, y] = SHADOW if y == 8 else BASE
    for x in (4, 6, 9, 11):
        px[x, 11] = px[x, 12] = SHADOW
        px[x, 13] = OUTLINE
    eyes_pair(px, mood)
    px[10, 10] = PATINA
    if mood == "sleep":
        for x, y in ((12, 0), (13, 0), (14, 0), (13, 1), (12, 2), (13, 2), (14, 2)):
            px[x, y] = (200, 225, 255, 255)
    elif mood == "think":
        for x, y in ((8, 0), (8, 2), (7, 1), (9, 1)):
            px[x, y] = BRASS
        px[8, 1] = SHINE
    return img


def variant_d(mood):
    """The box with a cog on its head, bolt hands instead of nubs and two sturdy legs."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    box_body(px)
    for x, y in ((7, 3), (8, 3), (6, 2), (7, 2), (8, 2), (9, 2), (7, 1), (8, 1)):
        px[x, y] = BRASS
    px[7, 2] = px[8, 2] = SHADOW
    if mood == "overdrive":
        px[7, 1] = px[8, 1] = GLASS["overdrive"]
    for x in (2, 13):
        px[x, 7] = SHADOW
    px[1, 7] = px[14, 7] = BRASS
    for x in (4, 5, 10, 11):
        px[x, 11] = px[x, 12] = SHADOW
        px[x, 13] = OUTLINE
    eyes_pair(px, mood)
    px[10, 10] = PATINA
    px[11, 10] = PATINA_LIGHT
    return img


def variant_e(mood):
    """The box wound by a clockwork key in its side, eyes glowing behind a visor slit, four short legs."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    box_body(px)
    for x in range(4, 12):
        px[x, 6] = px[x, 7] = RING
    eyes_pair(px, mood)
    for x in (4, 7, 8, 11):
        if px[x, 6] == RING:
            px[x, 6] = px[x, 7] = RING
    px[13, 7] = SHADOW
    for x, y in ((14, 6), (14, 7), (14, 8), (15, 6), (15, 8)):
        px[x, y] = BRASS
    px[15, 7] = (0, 0, 0, 0)
    if mood == "overdrive":
        px[14, 7] = GLASS["overdrive"]
    for x in (4, 6, 9, 11):
        px[x, 11] = px[x, 12] = SHADOW
        px[x, 13] = OUTLINE
    px[5, 10] = PATINA
    px[6, 10] = PATINA_LIGHT
    return img


CLOSE = {"F": ("Почти как был", variant_f), "D": ("Шестерня на голове", variant_d), "E": ("Заводной ключ", variant_e)}


def close_sheet(path, original):
    """The old sprite next to the close variants, so the difference can be judged by eye."""
    global VARIANTS
    old = Image.open(original).convert("RGBA")
    keep = VARIANTS
    VARIANTS = {"0": ("Было (Оригинал)", lambda mood: old)}
    VARIANTS.update(CLOSE)
    sheet(path)
    VARIANTS = keep


VARIANTS = {"A": ("Шестерёнка", variant_a), "B": ("Котелок", variant_b), "C": ("Сова", variant_c)}


def sheet(path):
    scale, pad = 12, 18
    shown = ["calm", "happy", "scared", "sleep", "overdrive"]
    font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 22)
    small = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 15)
    cell = 16 * scale
    w = pad + (cell + pad) * len(shown) + 140
    h = pad + (cell + pad + 26) * len(VARIANTS) + 30
    out = Image.new("RGB", (w, h), (34, 34, 40))
    d = ImageDraw.Draw(out)
    labels = {"calm": "спокоен", "happy": "рад", "scared": "страх", "sleep": "спит", "overdrive": "форсаж"}
    y = pad + 30
    for key, (name, fn) in VARIANTS.items():
        d.text((pad, y - 28), f"{key} · {name}", fill=(235, 230, 225), font=font)
        x = pad
        for mood in shown:
            sprite = fn(mood).resize((cell, cell), Image.NEAREST)
            d.rectangle([x, y, x + cell, y + cell], fill=(52, 52, 60))
            out.paste(sprite, (x, y), sprite)
            d.text((x + 4, y + cell + 2), labels[mood], fill=(160, 160, 170), font=small)
            x += cell + pad
        # the real size, as in a hotbar slot, at 2x and 1x GUI scale
        slot = fn("calm")
        for i, s in enumerate((2, 3)):
            big = slot.resize((16 * s, 16 * s), Image.NEAREST)
            sx, sy = x + 10, y + 20 + i * 70
            d.rectangle([sx - 3, sy - 3, sx + 16 * s + 3, sy + 16 * s + 3], fill=(139, 139, 139))
            out.paste(big, (sx, sy), big)
        y += cell + pad + 26
    out.save(path)


def write_textures(directory, variant):
    fn = {**VARIANTS, **CLOSE}[variant][1]
    names = {"calm": "", "blink": "_blink", "happy": "_happy", "scared": "_scared", "think": "_think",
             "sleep": "_sleep", "overdrive": "_ultra"}
    for mood, suffix in names.items():
        fn(mood).save(Path(directory) / f"cogwit_talisman{suffix}.png")
    flicker = fn("overdrive")
    fpx = flicker.load()
    for yy in range(16):
        for xx in range(16):
            if fpx[xx, yy] == GLASS["overdrive"]:
                fpx[xx, yy] = GLASS_HI["overdrive"]
    flicker.save(Path(directory) / "cogwit_talisman_ultra_flicker.png")


if __name__ == "__main__":
    if sys.argv[1] == "--close":
        close_sheet(sys.argv[2], sys.argv[3])
        print("saved", sys.argv[2])
        sys.exit()
    sheet(sys.argv[1])
    if len(sys.argv) > 3:
        write_textures(sys.argv[2], sys.argv[3])
    print("saved", sys.argv[1])
