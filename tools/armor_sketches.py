"""Voxel concept sketches of alternative Horizon armor styles, rendered like a Minecraft player model.

Run: uv run --no-project --with pillow python -I tools/armor_sketches.py <out_dir>
One voxel is half a Minecraft pixel. Front of the player faces -z, the player's right side is -x.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
FONT = "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"


def box(x0, y0, z0, x1, y1, z1):
    """Box in Minecraft pixels → half-pixel voxel bounds [x0, x1)."""
    return tuple(int(round(v * 2)) for v in (x0, y0, z0, x1, y1, z1))


PARTS = [
    ("boot_r", box(-4.5, -0.25, -2.75, 0.25, 4.5, 2.75)),
    ("boot_l", box(-0.25, -0.25, -2.75, 4.5, 4.5, 2.75)),
    ("leg_r", box(-4.25, 4.5, -2.5, 0, 12, 2.5)),
    ("leg_l", box(0, 4.5, -2.5, 4.25, 12, 2.5)),
    ("body", box(-4.5, 11.5, -2.75, 4.5, 24, 2.75)),
    ("arm_r", box(-8.75, 11.5, -2.75, -4.5, 24.25, 2.75)),
    ("arm_l", box(4.5, 11.5, -2.75, 8.75, 24.25, 2.75)),
    ("head", box(-4.5, 23.5, -4.5, 4.5, 32.5, 4.5)),
]


class Surf:
    """Where a voxel sits on its part: which faces it touches and its position along them."""

    def __init__(self, part, b, x, y, z):
        x0, y0, z0, x1, y1, z1 = b
        self.part, self.x, self.y, self.z = part, x, y, z
        self.front, self.back = z == z0, z == z1 - 1
        self.left, self.right = x == x1 - 1, x == x0
        self.top, self.bottom = y == y1 - 1, y == y0
        self.u = x - x0  # across the part, from its -x side
        self.v = y - y0  # up the part
        self.w = z - z0  # front to back
        self.W, self.H, self.D = x1 - x0, y1 - y0, z1 - z0
        self.cx = x - (x0 + x1 - 1) / 2  # centred
        self.cy = y - (y0 + y1 - 1) / 2
        self.cz = z - (z0 + z1 - 1) / 2

    @property
    def edge(self):
        return sum((self.front or self.back, self.left or self.right, self.top or self.bottom)) >= 2


def build(paint, extras=(), parts=()):
    vox = {}
    for part, b in PARTS + list(parts):
        x0, y0, z0, x1, y1, z1 = b
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    if (x, y, z) in vox:
                        continue
                    s = Surf(part, b, x, y, z)
                    if not (s.front or s.back or s.left or s.right or s.top or s.bottom):
                        vox[(x, y, z)] = ((10, 10, 10), False)
                        continue
                    c = paint(s)
                    if c is not None:
                        vox[(x, y, z)] = c
    for (x, y, z), c in extras:
        vox[(x, y, z)] = c
    add_cogwit(vox)
    return vox


def add_cogwit(vox):
    """Cogwit sitting on the left shoulder, for scale and identity."""
    orange, dark = (217, 119, 87), (24, 14, 10)
    for x in range(10, 17):
        for y in range(49, 54):
            for z in range(-3, 3):
                vox[(x, y, z)] = (orange, False)
    for x in (9, 17):
        for z in (-2, -1, 0, 1):
            vox[(x, 51, z)] = (orange, False)
    for x in (11, 15):
        for z in (-3, 1):
            vox[(x, 48, z)] = (orange, False)
    vox[(12, 52, -4)] = (dark, False)
    vox[(15, 52, -4)] = (dark, False)
    vox[(12, 51, -4)] = (dark, False)
    vox[(15, 51, -4)] = (dark, False)


FACES = {
    (1, 0, 0): [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)],
    (-1, 0, 0): [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)],
    (0, 1, 0): [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    (0, -1, 0): [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
    (0, 0, 1): [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
    (0, 0, -1): [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)],
}


def render(vox, yaw, pitch=-14, scale=6.6, size=(380, 600)):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def rot(p):
        x, y, z = p
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    light = (-0.45, 0.8, -0.4)
    ln = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ln for c in light)
    polys = []
    for (x, y, z), (color, glow) in vox.items():
        for n, corners in FACES.items():
            if (x + n[0], y + n[1], z + n[2]) in vox:
                continue
            rn = rot(n)
            if rn[2] >= 0:
                continue
            pts = [rot((x + a, y - 33 + b, z + c)) for a, b, c in corners]
            depth = sum(p[2] for p in pts) / 4
            if glow:
                shade = 1.0
            else:
                lam = max(0.0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
                shade = 0.42 + 0.58 * lam
            col = tuple(min(255, int(ch * shade)) for ch in color)
            polys.append((depth, [(size[0] / 2 + p[0] * scale, size[1] * 0.56 - p[1] * scale) for p in pts], col, glow))
    polys.sort(key=lambda t: -t[0])
    img = Image.new("RGB", size, (0, 0, 0))
    glow_img = Image.new("RGB", size, (0, 0, 0))
    d, gd = ImageDraw.Draw(img), ImageDraw.Draw(glow_img)
    for depth, pts, col, glow in polys:
        d.polygon(pts, fill=col)
        gd.polygon(pts, fill=col if glow else (0, 0, 0))
    mask = Image.new("L", size, 0)
    md = ImageDraw.Draw(mask)
    for depth, pts, col, glow in polys:
        md.polygon(pts, fill=255)
    bloom = glow_img.filter(ImageFilter.GaussianBlur(9))
    return img, mask, bloom


def backdrop(size):
    w, h = size
    bg = Image.new("RGB", size)
    px = bg.load()
    for j in range(h):
        for i in range(w):
            t = j / h
            r = math.hypot(i - w / 2, j - h * 0.55) / (w * 0.9)
            v = 34 - 16 * t - 10 * r
            px[i, j] = (int(v), int(v), int(v + 4))
    d = ImageDraw.Draw(bg)
    d.ellipse((w / 2 - 95, h * 0.935 - 12, w / 2 + 95, h * 0.935 + 12), fill=(12, 12, 14))
    return bg


def figure(vox, yaw):
    size = (380, 600)
    img, mask, bloom = render(vox, yaw, size=size)
    out = backdrop(size)
    out.paste(img, (0, 0), mask)
    return Image.blend(out, Image.composite(out, out, mask), 0) if False else add(out, bloom)


def add(a, b):
    from PIL import ImageChops
    return ImageChops.add(a, b)


rng = random.Random(7)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(c, amount=6):
    n = rng.randint(-amount, amount)
    return tuple(max(0, min(255, ch + n)) for ch in c)


# ---------------------------------------------------------------- styles
def astronaut(s):
    """1. Скафандр: белая керамика, золотой визор, оранжевые метки Когвита, рюкзак жизнеобеспечения."""
    white, seam, joint = (226, 224, 217), (150, 150, 150), (40, 40, 46)
    gold, orange = (226, 160, 60), (217, 119, 87)
    if s.part == "head":
        if s.front and abs(s.cx) <= 6 and -6 <= s.cy <= 4:
            t = (s.cy + 6) / 10
            return (mix((120, 70, 20), gold, t), t > 0.75)
        if s.front and abs(s.cx) <= 7 and -7 <= s.cy <= 5:
            return ((175, 172, 165), False)
        return (noise(white, 3), False)
    if s.part == "body":
        if s.v in (0, 1):
            return (joint, False)
        if s.front and 10 <= s.v <= 14 and -5 <= s.cx <= 5:
            if s.v == 12 and s.cx in (-3, 0, 3):
                return ((90, 220, 140) if s.cx else orange, True)
            return ((70, 72, 80), False)
        if s.back and 4 <= s.v <= 21 and abs(s.cx) <= 7:
            return ((200, 198, 190), False)
        if s.front and s.cx == 0 and s.v < 10:
            return (seam, False)
        return (noise(white, 3), False)
    if s.part.startswith("arm"):
        if 9 <= s.v <= 11:
            return (joint, False)
        if s.part == "arm_l" and 16 <= s.v <= 20 and (s.front or s.left):
            return (orange, False)
        if s.part == "arm_r" and s.v >= 22 and s.top:
            return (orange, False)
        if s.v <= 2:
            return ((60, 60, 66), False)
        return (noise(white, 3), False)
    if s.part.startswith("leg"):
        if 6 <= s.v <= 7:
            return (joint, False)
        if s.front and 9 <= s.v <= 12 and abs(s.cx) <= 2:
            return ((185, 183, 176), False)
        return (noise(white, 3), False)
    if s.part.startswith("boot"):
        if s.v <= 2:
            return ((50, 50, 56), False)
        return (noise((205, 203, 196), 3), False)


def astronaut_extras():
    ex = []
    for side in (-1, 1):
        for y in range(52, 59):
            for z in range(-3, 3):
                for x in (10, 11):
                    ex.append(((side * x - (1 if side < 0 else 0), y, z), ((185, 183, 176) if x == 10 else (217, 119, 87), False)))
    for y in range(66, 74):
        ex.append(((-7, y, 3), ((120, 120, 126) if y < 73 else (255, 120, 80), y == 73)))
    for x in range(-7, 7):
        for y in range(28, 46):
            for z in range(6, 10):
                c = (210, 208, 200) if z < 9 else (190, 188, 180)
                glow = False
                if z == 9 and y in (32, 36, 40) and -4 <= x <= 3:
                    c, glow = (217, 119, 87), True
                ex.append(((x, y, z), (c, glow)))
    return ex


def crystal(s):
    """2. Аметистовая пустота: тёмный базальт, светящиеся фиолетовые трещины."""
    base = (34, 26, 44)
    crack = (190, 120, 255)
    h = (s.x * 7 + s.y * 13 + s.z * 5) % 23
    if s.part == "head" and s.front and -2 <= s.cy <= -1 and abs(s.cx) <= 6:
        return ((235, 200, 255), True)
    a = s.x + s.y + s.z
    vein = (a % 13 == 0 and (s.x - s.y) % 7 < 4) or ((s.x - s.y + 2 * s.z) % 17 == 0 and (s.y // 3) % 3 != 1)
    if vein:
        return (crack, True)
    if s.edge:
        return ((58, 44, 74), False)
    return (noise(base, 5), False)


def crystal_extras():
    ex = []

    def shard(x, y, z, dx, dy, dz, length, thick=2):
        for k in range(length):
            t = k / max(1, length - 1)
            c = mix((120, 70, 200), (240, 210, 255), t)
            r = max(0, thick - k // 3)
            for a in range(-r, r + 1):
                for b in range(-r, r + 1):
                    ex.append(((round(x + dx * k + a), round(y + dy * k), round(z + dz * k + b)), (c, k > length * 0.5)))

    shard(-14, 48, 0, -0.6, 1, 0, 12, 3)
    shard(-16, 46, -3, -1, 0.7, -0.3, 8, 2)
    shard(14, 47, 3, 0.6, 1, 0.4, 7, 1)
    shard(0, 46, 6, 0, 0.7, 1, 10, 2)
    shard(0, 34, 6, 0, 0.3, 1, 7, 2)
    shard(0, 24, 6, 0, 0.1, 1, 5, 1)
    shard(-12, 49, 3, -0.3, 1, 0.5, 6, 1)
    shard(-3, 65, 2, -0.3, 1, 0.4, 7, 1)
    shard(2, 65, 0, 0.2, 1, 0.3, 9, 1)
    shard(-4, 40, 6, -0.4, 0.6, 1, 8)
    shard(3, 34, 6, 0.4, 0.5, 1, 6)
    return ex


def knight(s):
    """3. Латы сингулярности: воронёная сталь, золотая гравировка орбит, щель визора."""
    steel, steel_hi, gold = (62, 66, 78), (110, 116, 132), (212, 160, 70)
    if s.part == "head":
        if s.front and s.cy in (0, 1) and abs(s.cx) <= 6:
            return ((210, 190, 255), True)
        if s.front and s.cx in (0, -1) and -7 <= s.cy <= 0:
            return ((210, 190, 255), True)
        if s.top and abs(s.cx) <= 1:
            return (gold, False)
    if s.part == "body" and s.front:
        d = math.hypot(s.cx, s.cy - 2)
        if d < 2.2:
            return ((10, 6, 14), False)
        if 2.6 < d < 3.6 or abs(math.hypot(s.cx * 0.7, (s.cy - 2) * 1.3) - 5.6) < 0.5:
            return (gold, d < 4)
    if s.part.startswith("leg") and s.front and s.v in (5, 6):
        return (gold, False)
    if s.part.startswith("boot") and s.v == 8:
        return (gold, False)
    if s.edge:
        return (steel_hi, False)
    return (noise(steel, 3), False)


def knight_extras():
    ex = []
    for side in (-1, 1):
        for x in range(10, 21):
            for y in range(42, 52):
                for z in range(-7, 7):
                    xx = side * x - (1 if side < 0 else 0)
                    dx, dy = (x - 10) / 11, (y - 42) / 10
                    if dx + (1 - dy) * 0.6 > 1.15:
                        continue
                    c = (212, 160, 70) if y == 42 or abs(z) == 6 and y % 3 == 0 else (72, 76, 90)
                    ex.append(((xx, y, z), (c, False)))
    for y in range(4, 48):
        spread = 8 + (47 - y) // 8
        for x in range(-spread, spread):
            z = 6 + (1 if (x + 40) % 6 < 3 else 0)
            c = (212, 160, 70) if y in (46, 47) or abs(x) == spread - 1 and y < 44 else (58, 22, 80)
            ex.append(((x, y, z), (c, False)))
    return ex


def copper(s):
    """4. Медный гироскоп: полированная и окисленная медь, заклёпки, иллюминатор, в духе Create."""
    shiny, oxid, rivet = (200, 110, 70), (80, 160, 130), (240, 190, 140)
    patina = (s.v <= 1 + (s.x * 5 + s.z * 3) % 3) or (s.edge and (s.x * 7 + s.y) % 5 == 0)
    if s.part == "head" and s.front:
        d = math.hypot(s.cx, s.cy + 1)
        if d < 4.2:
            return (mix((255, 200, 120), (217, 119, 87), d / 4.2), True)
        if d < 5.4:
            return ((60, 40, 30), False)
    if s.part == "body" and s.front and s.v in (12, 13) :
        return ((60, 40, 30), False)
    if s.part == "body" and s.front and 15 <= s.v <= 19 and 2 <= s.cx <= 6:
        return ((240, 230, 200) if (s.cx, s.v) != (4, 17) else (200, 40, 30), (s.cx, s.v) != (4, 17))
    if s.edge and (s.u + s.v + s.w) % 4 == 0 and not (s.top or s.bottom):
        return (rivet, False)
    if patina:
        return (noise(oxid, 6), False)
    if s.edge:
        return ((150, 80, 50), False)
    return (noise(shiny, 5), False)


def copper_extras():
    ex = []
    for side in (-1, 1):
        cy = 44
        for k in range(160):
            a = k / 160 * 2 * math.pi
            for r in (8, 9):
                for t in (0, 1):
                    x = side * (18 + t) - (1 if side < 0 else 0)
                    ex.append(((x, round(cy + math.sin(a) * r), round(math.cos(a) * r)), ((236, 160, 96), False)))
            x = side * 18 - (1 if side < 0 else 0)
            ex.append(((x - side, round(cy + math.sin(a) * 4), round(math.cos(a) * 4)), ((217, 119, 87), True)))
    for side in (-4, 2):
        for y in range(26, 52):
            ex.append(((side, y, 6), ((170, 95, 60), False)))
            ex.append(((side + 1, y, 6), ((150, 80, 50), False)))
            ex.append(((side, y, 7), ((200, 120, 80), False)))
            ex.append(((side + 1, y, 7), ((180, 100, 66), False)))
    for x in range(-6, 6):
        for y in range(30, 42):
            for z in range(6, 9):
                glow = z == 8 and (x * x + (y - 36) ** 2) < 10
                ex.append(((x, y, z), ((255, 170, 90) if glow else (120, 70, 45), glow)))
    return ex


def void(s):
    """5. Пустота: абсолютно чёрный силуэт, светится только кромка, как аккреционный ободок."""
    black = (8, 8, 10)
    rim = (255, 214, 160)
    if s.part == "head" and s.front and s.cy == 0 and abs(s.cx) <= 6:
        return ((255, 240, 220), True)
    if s.edge:
        return (rim, True)
    return (black, False)


STYLES = [
    ("Скафандр", "белая керамика, золотой визор, рюкзак", astronaut, astronaut_extras),
    ("Аметистовая пустота", "тёмный камень, кристаллы, трещины", crystal, crystal_extras),
    ("Латы сингулярности", "воронёная сталь, золото, плащ", knight, knight_extras),
    ("Медный гироскоп", "медь и патина, кольца, в духе Create", copper, copper_extras),
    ("Пустота", "чёрный силуэт, светится только кромка", void, lambda: []),
]


def label(img, title, sub, num):
    d = ImageDraw.Draw(img)
    big = ImageFont.truetype(FONT, 30)
    small = ImageFont.truetype(FONT, 19)
    d.text((24, 18), f"{num}. {title}", font=big, fill=(240, 236, 228))
    d.text((24, 58), sub, font=small, fill=(160, 158, 168))


# ---------------------------------------------------------------- mixes of 2 + 3 + 5
VOID_BLACK = (8, 8, 11)
GOLD = (222, 168, 72)


def mix_style(rim, crack, visor, emblem_gold, crack_spread):
    """Black silhouette with a glowing rim (5), knight shapes and the black-hole emblem (3), violet tears (2)."""

    def paint(s):
        if s.part == "cape":
            if s.edge or s.left or s.right or s.bottom:
                return (rim, True) if not s.top else ((40, 16, 58), False)
            return (noise((38, 14, 54), 3), False)
        if s.part.startswith("pauldron"):
            if s.edge:
                return (rim, True)
            if crystal_vein(s, 9):
                return (crack, True)
            return (VOID_BLACK, False)
        if s.part == "head" and s.front:
            hx, hy = math.floor(s.cx), math.floor(s.cy)
            if (hy in (0, 1) and -6 <= hx <= 5) or (hx in (-1, 0) and -6 <= hy <= 0):
                return (visor, True)
        if s.part == "body" and s.front:
            d = math.hypot(s.cx, s.cy - 2)
            if d < 2.2:
                return ((0, 0, 0), False)
            if 2.6 < d < 3.7:
                return ((GOLD if emblem_gold else rim), True)
            ang = math.atan2(s.cy - 2, s.cx)
            ray = (ang / (2 * math.pi / 7)) % 1
            if d > 4 and abs(ray - 0.5) < 0.06 + 0.4 / d and d < crack_spread:
                return (crack, True)
        if s.edge:
            return (rim, True)
        if crack_spread > 10 and crystal_vein(s, 13):
            return (crack, True)
        return (VOID_BLACK, False)

    return paint


def crystal_vein(s, period):
    return (s.x + s.y + s.z) % period == 0 and (s.x - s.y) % 7 < 3


KNIGHT_PARTS = [
    ("pauldron_r", box(-10.25, 21, -3.5, -3.75, 24, 3.5)),
    ("pauldron_l", box(3.75, 21, -3.5, 10.25, 24, 3.5)),
    ("pauldron_lip_r", box(-9.75, 19, -3.25, -4.25, 21, 3.25)),
    ("pauldron_lip_l", box(4.25, 19, -3.25, 9.75, 21, 3.25)),
    ("cape", box(-4.75, 1.5, 2.75, 4.75, 23.5, 3.5)),
]


def shards(color_lo, color_hi, sets):
    ex = []
    for x, y, z, dx, dy, dz, length, thick in sets:
        for k in range(length):
            t = k / max(1, length - 1)
            c = mix(color_lo, color_hi, t)
            r = max(0, thick - k // 3)
            for a in range(-r, r + 1):
                for b in range(-r, r + 1):
                    ex.append(((round(x + dx * k + a), round(y + dy * k), round(z + dz * k + b)), (c, k > length * 0.4)))
    return ex


SHOULDER_SHARDS = [(-17, 48, 0, -0.5, 1, 0, 10, 2), (-15, 48, -4, -0.6, 0.8, -0.4, 7, 1),
                   (-14, 48, 4, -0.3, 1, 0.5, 6, 1)]
BACK_SHARDS = [(-4, 44, 7, -0.6, 0.8, 0.7, 9, 1), (4, 44, 7, 0.6, 0.8, 0.7, 9, 1), (0, 46, 7, 0, 1, 0.6, 11, 1)]

MIXES = [
    ("Рыцарь Пустоты", "чёрные латы, золотая кромка, кристаллы на правом плече",
     mix_style((255, 214, 160), (190, 120, 255), (255, 236, 210), True, 7),
     lambda: shards((110, 60, 190), (240, 210, 255), SHOULDER_SHARDS), KNIGHT_PARTS),
    ("Разлом", "фиолетовая кромка, трещины от ядра, кристаллы на спине",
     mix_style((185, 125, 255), (215, 160, 255), (235, 215, 255), True, 40),
     lambda: shards((110, 60, 190), (240, 210, 255), BACK_SHARDS + SHOULDER_SHARDS[:1]), KNIGHT_PARTS),
    ("Тихий горизонт", "белая кромка, мало света, без плаща",
     mix_style((235, 235, 255), (180, 120, 255), (200, 170, 255), False, 9),
     lambda: shards((90, 50, 160), (220, 190, 255), SHOULDER_SHARDS[:1]), KNIGHT_PARTS[:2]),
]

STEEL, STEEL_HI, STEEL_LO = (44, 46, 58), (92, 96, 116), (26, 27, 34)


def plate_style(rim_mode, rim, crack, visor):
    """Knight plate (3) with amethyst tears (2); the glowing rim of 5 only as much as {@code rim_mode} allows."""

    def rimmed(s, glow_here):
        if rim_mode == "dim":
            return (mix(STEEL_HI, rim, 0.35), False)
        if rim_mode == "accents" and glow_here:
            return (rim, True)
        return (STEEL_HI, False)

    def paint(s):
        if s.part == "cape":
            if s.left or s.right or s.bottom:
                return (GOLD, False)
            return (noise((40, 16, 58), 3), False)
        if s.part.startswith("pauldron"):
            if s.edge:
                return rimmed(s, s.top or s.bottom)
            if crystal_vein(s, 9):
                return (crack, True)
            if s.part.startswith("pauldron_lip") and s.v == 1:
                return (GOLD, False)
            return (noise(STEEL, 3), False)
        if s.part == "head":
            if s.front:
                hx, hy = math.floor(s.cx), math.floor(s.cy)
                if (hy in (0, 1) and -6 <= hx <= 5) or (hx in (-1, 0) and -6 <= hy <= 0):
                    return (visor, True)
            if s.top and abs(math.floor(s.cx) + 0.5) <= 1:
                return (GOLD, False)
            if s.edge:
                return rimmed(s, s.top)
        if s.part == "body" and s.front:
            d = math.hypot(s.cx, s.cy - 2)
            if d < 2.2:
                return ((0, 0, 0), False)
            if 2.6 < d < 3.7:
                return (GOLD, True)
            ang = math.atan2(s.cy - 2, s.cx)
            ray = (ang / (2 * math.pi / 7)) % 1
            if 4 < d < 9 and abs(ray - 0.5) < 0.06 + 0.4 / d:
                return (crack, True)
        if s.part.startswith("leg") and s.front and s.v in (5, 6):
            return (GOLD, False)
        if s.part.startswith("boot") and s.v == 8:
            return (GOLD, False)
        if s.edge:
            return rimmed(s, False)
        if crystal_vein(s, 15):
            return (crack, True)
        if s.bottom or (s.v == 1 and not s.part.startswith("boot")):
            return (STEEL_LO, False)
        return (noise(STEEL, 3), False)

    return paint


AMETHYST = (190, 120, 255)
PLATES = [
    ("Без кромки", "латы и аметист, свет только в визоре, ядре и трещинах",
     plate_style("none", GOLD, AMETHYST, (230, 210, 255)),
     lambda: shards((110, 60, 190), (240, 210, 255), SHOULDER_SHARDS), KNIGHT_PARTS),
    ("Кромка-акцент", "светятся только края наплечников и шлема",
     plate_style("accents", (255, 214, 160), AMETHYST, (255, 236, 210)),
     lambda: shards((110, 60, 190), (240, 210, 255), SHOULDER_SHARDS), KNIGHT_PARTS),
    ("Тусклая кромка", "края чуть золотятся, но не светятся; кристаллы и на спине",
     plate_style("dim", GOLD, AMETHYST, (230, 210, 255)),
     lambda: shards((110, 60, 190), (240, 210, 255), SHOULDER_SHARDS + BACK_SHARDS), KNIGHT_PARTS),
]

if len(sys.argv) > 2 and sys.argv[2] == "plates":
    STYLES = [(t, sub, paint, ex) for t, sub, paint, ex, parts in PLATES]
    PARTS_FOR = {t: parts for t, sub, paint, ex, parts in PLATES}
    PREFIX = "plates"
elif len(sys.argv) > 2 and sys.argv[2] == "mix":
    STYLES = [(t, sub, paint, ex) for t, sub, paint, ex, parts in MIXES]
    PARTS_FOR = {t: parts for t, sub, paint, ex, parts in MIXES}
    PREFIX = "mix"
else:
    PARTS_FOR = {}
    PREFIX = "style"

cards = []
for i, (title, sub, paint, extras) in enumerate(STYLES, 1):
    vox = build(paint, extras(), PARTS_FOR.get(title, ()))
    front = figure(vox, 28)
    back = figure(vox, 180 + 28)
    card = Image.new("RGB", (front.width * 2, front.height), (0, 0, 0))
    card.paste(front, (0, 0))
    card.paste(back, (front.width, 0))
    label(card, title, sub, i)
    card.save(OUT / f"{PREFIX}_{i}.png")
    cards.append(card)
    print("rendered", title, len(vox))

cw, ch = cards[0].size
rows = (len(cards) + 1) // 2
sheet = Image.new("RGB", (cw * 2 + 12, (ch + 12) * rows), (18, 18, 20))
for i, c in enumerate(cards):
    sheet.paste(c, ((i % 2) * (cw + 12), (i // 2) * (ch + 12)))
sheet.save(OUT / {"style": "all_styles.png", "mix": "mixes.png", "plates": "plates.png"}[PREFIX])
