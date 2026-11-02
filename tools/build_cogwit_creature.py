"""Builds the Cogwit creature family for GeckoLib: one shared geometry, four palettes and the animation set.

Cogwit is a blocky copper critter: a wide riveted body, two glowing lens eyes, stubby arms with little pincer
claws, four short legs. His three brothers share the exact same geometry and UV layout, only the
paint differs (sizes come from the renderer scale):
  cogwit  copper with corner rivets      spark  bright brass-copper with a light stripe (fast, small)
  rivet   copper with a patina band      anvil  dark bronze with riveted plates (the tank)

Geometry notes (Minecraft model pixels, Y up, the front faces -Z, the creature's right side is -X):
  * root (ground pivot) -> body, legs. body -> eyes, arms (+ claw prongs), shell, helmet. arm_r -> item_r locator.
  * shell and helmet are painted near-white so the renderer can tint them with an armour colour.
  * claw_*_top / claw_*_bot are extra prong bones so a claw can really snap shut.

Usage: build_cogwit_creature.py <resources_root> <preview_png>
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RES = Path(sys.argv[1])
PREVIEW = sys.argv[2]
S = 2  # texels per model pixel
ATLAS_W = 128

# ---------------------------------------------------------------- geometry model
bones = []


def bone(name, parent=None, pivot=(0, 0, 0)):
    b = {"name": name, "pivot": list(pivot), "cubes": []}
    if parent:
        b["parent"] = parent
    bones.append(b)
    return b


def cube(b, origin, size, material):
    b["cubes"].append({"origin": list(origin), "size": list(size), "mat": material})


bone("root", None, (0, 0, 0))

# body: 10 wide, 6 tall, 6 deep, standing on 2px legs. Pivot at the middle so it can tumble and tilt.
body = bone("body", "root", (0, 5, 0))
cube(body, (-5, 2, -3), (10, 6, 6), "body")

# eyes: tall black squares that stick out of the front by 0.4px; pivot at their centre so a blink squashes them in place
for side, sx in (("l", 1), ("r", -1)):
    eye = bone(f"eye_{side}", "body", (2.5 * sx, 5.75, -3.2))
    cube(eye, (1.75 if sx > 0 else -3.25, 4.75, -3.4), (1.5, 2, 0.5), "eye")

# arms: 2x2x2 stubs at mid height, each ending in a two-pronged claw pointing forward
for side, sx in (("l", 1), ("r", -1)):
    arm = bone(f"arm_{side}", "body", (5 * sx, 5, 0))
    cube(arm, (5 if sx > 0 else -7, 4, -1), (2, 2, 2), "arm")
    px0 = 6.0 if sx > 0 else -7.0
    top = bone(f"claw_{side}_top", f"arm_{side}", (6.5 * sx, 5.075, -1))
    cube(top, (px0, 5.15, -2.6), (1, 0.8, 1.8), "claw_top")
    bot = bone(f"claw_{side}_bot", f"arm_{side}", (6.5 * sx, 4.925, -1))
    cube(bot, (px0, 4.05, -2.6), (1, 0.8, 1.8), "claw_bot")
    if side == "r":
        # held-item locator between the right claw prongs; follows the arm and the claw swing, has no cubes
        bone("item_r", "arm_r", (-6.5, 5, -2.5))

# shell: 0.25px bigger than the body on top/back/sides (its front sits inside the body), tintable
shell = bone("shell", "body", (0, 5, 0))
cube(shell, (-5.25, 2.5, -2.75), (10.5, 5.75, 6), "shell")

# helmet: a small cap with a brim on top of the body, tintable
helmet = bone("helmet", "body", (0, 8, 0))
cube(helmet, (-3, 8.15, -2.25), (6, 1.35, 4.5), "helmet")
cube(helmet, (-3, 8.15, -2.75), (6, 0.7, 0.6), "helmet_brim")

# legs: 1.5 x 2 x 1.5, pivot at the top so they swing; they reach 0.25px into the body to avoid a seam
for name, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
    cx, cz = 3.5 * sx, 1.75 * sz
    leg = bone(name, "root", (cx, 2, cz))
    cube(leg, (cx - 0.75, 0, cz - 0.75), (1.5, 2.25, 1.5), "leg")

REQUIRED_BONES = ["root", "body", "eye_l", "eye_r", "arm_l", "arm_r", "shell", "helmet", "item_r",
                  "leg_fl", "leg_fr", "leg_bl", "leg_br"]

# ---------------------------------------------------------------- UV packing (shared by all variants)
FACES = ("north", "south", "east", "west", "up", "down")


def face_dims(size, face):
    x, y, z = size
    return {"north": (x, y), "south": (x, y), "east": (z, y), "west": (z, y), "up": (x, z), "down": (x, z)}[face]


requests = []
for b in bones:
    for c in b["cubes"]:
        for f in FACES:
            w, h = face_dims(c["size"], f)
            requests.append((max(1, math.ceil(h * S - 1e-6)), max(1, math.ceil(w * S - 1e-6)), c, f))
requests.sort(key=lambda r: (-r[0], -r[1]))
x = y = shelf_h = 0
placed = []
for th, tw, c, f in requests:
    if x + tw > ATLAS_W:
        x, y, shelf_h = 0, y + shelf_h, 0
    placed.append((x, y, tw, th, c, f))
    c.setdefault("uv", {})[f] = {"uv": [x, y], "uv_size": [tw, th]}
    x += tw
    shelf_h = max(shelf_h, th)
ATLAS_H = 16
while ATLAS_H < y + shelf_h:
    ATLAS_H *= 2


# ---------------------------------------------------------------- palettes
class Pal:
    def __init__(self, base, hi, hi2, lo, deep, **extra):
        self.base, self.hi, self.hi2, self.lo, self.deep = base, hi, hi2, lo, deep
        self.__dict__.update(extra)


def rgba(c):
    c = tuple(c)
    return c if len(c) == 4 else c + (255,)


def dim(c, k):
    return rgba(tuple(int(v * k) for v in c))


PALETTES = {
    "cogwit": Pal((186, 112, 62), (210, 138, 84), (238, 180, 124), (150, 86, 46), (110, 60, 32),
                  rivet=(250, 212, 162), rivet_sh=(96, 52, 28), patina=(64, 148, 132), patina_hi=(118, 196, 176)),
    "spark": Pal((214, 150, 74), (230, 172, 96), (250, 210, 140), (182, 122, 58), (138, 90, 40),
                 stripe=(255, 222, 140), stripe_lo=(200, 150, 70)),
    "rivet": Pal((176, 100, 58), (198, 124, 76), (226, 160, 110), (142, 78, 44), (104, 56, 30),
                  band=(64, 148, 132), band_hi=(118, 196, 176)),
    "anvil": Pal((128, 82, 52), (150, 100, 66), (186, 136, 94), (100, 62, 38), (70, 42, 26),
                groove=(60, 36, 22), groove_hi=(170, 120, 80), rivet=(226, 186, 110), rivet_sh=(50, 30, 18)),
}
SHELL = Pal((226, 228, 232), (240, 242, 246), (255, 255, 255), (204, 207, 214), (180, 184, 194),
            groove=(194, 198, 207))
# the eyes are lenses: glowing cyan glass, a dark pupil, a dark rim on the sides
EYE = (96, 222, 226, 255)
EYE_HI = (214, 255, 250, 255)
EYE_PUPIL = (18, 34, 44, 255)
EYE_SIDE = (40, 30, 30, 255)


def leg_pal(p):
    return Pal(p.lo, p.base, p.hi, p.deep, tuple(int(v * 0.72) for v in p.deep))


# ---------------------------------------------------------------- painters
class Canvas:
    def __init__(self, img, x0, y0, w, h):
        self.px, self.x0, self.y0, self.w, self.h = img.load(), x0, y0, w, h

    def put(self, i, j, c):
        if 0 <= i < self.w and 0 <= j < self.h:
            self.px[self.x0 + i, self.y0 + j] = rgba(c)

    def get(self, i, j):
        return self.px[self.x0 + i, self.y0 + j]

    def fill(self, fn):
        for j in range(self.h):
            for i in range(self.w):
                self.put(i, j, fn(i, j))


def tone(cv, face, p, lower=0.58):
    """Crisp pixel-art shading: bright 1-texel top rim, flat upper body, darker lower band, darkest bottom row."""
    w, h = cv.w, cv.h
    if face == "up":
        cv.fill(lambda i, j: p.hi2 if i in (0, w - 1) or j in (0, h - 1) else p.hi)
    elif face == "down":
        cv.fill(lambda i, j: p.deep if i in (0, w - 1) or j in (0, h - 1) else p.lo)
    else:
        low = max(1, int(round(h * lower)))

        def fn(i, j):
            if j == 0:
                return p.hi2
            if j == h - 1 and h > 2:
                return p.deep
            if i in (0, w - 1):
                return p.hi if j < low else p.base
            return p.base if j < low else p.lo
        cv.fill(fn)


def hline(cv, j, i0, i1, c):
    for i in range(i0, i1 + 1):
        cv.put(i, j, c)


def vline(cv, i, j0, j1, c):
    for j in range(j0, j1 + 1):
        cv.put(i, j, c)


def rivet(cv, i, j, p):
    cv.put(i, j, p.rivet)
    cv.put(i + 1, j + 1, p.rivet_sh)


def paint_body(cv, face, variant):
    p = PALETTES[variant]
    tone(cv, face, p)
    w, h = cv.w, cv.h
    sides = face in ("north", "south", "east", "west")
    if variant == "spark" and sides:
        hline(cv, 8, 0, w - 1, p.stripe)
        hline(cv, 9, 1, w - 2, p.stripe_lo)
    elif variant == "rivet" and sides:
        hline(cv, 7, 0, w - 1, p.band_hi)
        hline(cv, 8, 0, w - 1, p.band)
        hline(cv, 9, 0, w - 1, p.band)
    elif variant == "anvil":
        paint_anvil_plates(cv, face, p)
    elif variant == "cogwit" and face in ("north", "south"):
        # corner rivets and a spot of patina, as on the item sprite
        for (i, j) in ((1, 1), (w - 3, 1), (1, h - 4), (w - 3, h - 4)):
            rivet(cv, i, j, p)
        if face == "north":
            for (i, j) in ((w - 6, h - 3), (w - 5, h - 3), (w - 5, h - 4)):
                cv.put(i, j, p.patina)
            cv.put(w - 6, h - 4, p.patina_hi)


def paint_anvil_plates(cv, face, p):
    """Heavy riveted plating: a lit-lip seam above a lower skirt plate, rivets only on plate corners."""
    w, h = cv.w, cv.h
    if face in ("north", "south"):
        hline(cv, 7, 1, w - 2, p.groove)
        hline(cv, 8, 1, w - 2, p.groove_hi)
        for i in (2, w - 4):
            rivet(cv, i, 2, p)
            rivet(cv, i, 9, p)
        rivet(cv, w // 2 - 1, 9, p)
    elif face in ("east", "west"):
        hline(cv, 7, 1, w - 2, p.groove)
        hline(cv, 8, 1, w - 2, p.groove_hi)
        for i in (3, w - 5):
            rivet(cv, i, 9, p)
    elif face == "up":
        for i in (6, w - 7):
            vline(cv, i, 1, h - 2, p.groove)
            vline(cv, i + 1, 1, h - 2, p.groove_hi)
        for (i, j) in ((2, 2), (2, h - 4), (w - 4, 2), (w - 4, h - 4)):
            rivet(cv, i, j, p)


def paint_arm(cv, face, variant):
    p = PALETTES[variant]
    tone(cv, face, leg_pal(p) if face == "north" else p)
    if variant == "anvil" and face in ("east", "west", "up"):
        rivet(cv, 1, 1, p)


def paint_claw(cv, face, variant, top):
    p = PALETTES[variant]
    tone(cv, face, p, lower=0.5)
    inner = (face == "down") if top else (face == "up")
    if inner:
        cv.fill(lambda i, j: p.deep)
    if face == "north":
        # a light pincer tip
        hline(cv, 0 if top else cv.h - 1, 0, cv.w - 1, p.hi2)


def paint_leg(cv, face, variant):
    tone(cv, face, leg_pal(PALETTES[variant]), lower=0.5)


def paint_eye(cv, face):
    cv.fill(lambda i, j: EYE if face == "north" else EYE_SIDE)
    if face == "north":
        cv.put(0, 0, EYE_HI)
        cv.put(cv.w // 2, cv.h // 2, EYE_PUPIL)
        cv.put(cv.w // 2, cv.h // 2 - 1, EYE_PUPIL)


def paint_shell(cv, face):
    p = SHELL
    tone(cv, face, p, lower=0.66)
    w, h = cv.w, cv.h
    if face in ("north", "south"):
        for i in (w // 3, w - w // 3 - 1):
            vline(cv, i, 1, h - 2, p.groove)
        hline(cv, h // 2, 1, w - 2, p.groove)
    elif face in ("east", "west"):
        vline(cv, w // 2, 1, h - 2, p.groove)
        hline(cv, h // 2, 1, w - 2, p.groove)
    elif face == "up":
        for i in (w // 3, w - w // 3 - 1):
            vline(cv, i, 1, h - 2, p.groove)
        hline(cv, h // 2, 1, w - 2, p.groove)


def paint_helmet(cv, face, brim=False):
    p = SHELL
    tone(cv, face, p, lower=0.55)
    w, h = cv.w, cv.h
    if brim:
        if face in ("north", "south"):
            hline(cv, h - 1, 0, w - 1, p.deep)
        return
    if face in ("north", "south", "east", "west"):
        vline(cv, w // 2, 1, h - 2, p.groove)
    elif face == "up":
        hline(cv, h // 2, 1, w - 2, p.groove)
        vline(cv, w // 2, 1, h - 2, p.groove)


def paint_face(img, variant, mat, face, x0, y0, tw, th):
    cv = Canvas(img, x0, y0, tw, th)
    if mat == "body":
        paint_body(cv, face, variant)
    elif mat == "eye":
        paint_eye(cv, face)
    elif mat == "arm":
        paint_arm(cv, face, variant)
    elif mat == "claw_top":
        paint_claw(cv, face, variant, True)
    elif mat == "claw_bot":
        paint_claw(cv, face, variant, False)
    elif mat == "leg":
        paint_leg(cv, face, variant)
    elif mat == "shell":
        paint_shell(cv, face)
    elif mat == "helmet":
        paint_helmet(cv, face)
    elif mat == "helmet_brim":
        paint_helmet(cv, face, brim=True)
    else:
        raise SystemExit(f"no painter for material {mat}")


textures = {}
for variant in PALETTES:
    img = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    for x0, y0, tw, th, c, f in placed:
        paint_face(img, variant, c["mat"], f, x0, y0, tw, th)
    textures[variant] = img

# ---------------------------------------------------------------- write geometry + textures
geo_bones = []
for b in bones:
    out = {"name": b["name"], "pivot": b["pivot"]}
    if "parent" in b:
        out["parent"] = b["parent"]
    if b["cubes"]:
        out["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
    geo_bones.append(out)

geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.cogwit_creature", "texture_width": ATLAS_W, "texture_height": ATLAS_H,
                    "visible_bounds_width": 4, "visible_bounds_height": 3, "visible_bounds_offset": [0, 0.5, 0]},
    "bones": geo_bones}]}

geo_path = RES / "assets/cogwit/geo/entity/cogwit_creature.geo.json"
anim_path = RES / "assets/cogwit/animations/entity/cogwit_creature.animation.json"
tex_dir = RES / "assets/cogwit/textures/entity"
for p in (geo_path, anim_path):
    p.parent.mkdir(parents=True, exist_ok=True)
tex_dir.mkdir(parents=True, exist_ok=True)
geo_path.write_text(json.dumps(geo, indent=1))
for variant, img in textures.items():
    img.save(tex_dir / f"{variant}.png")


# ---------------------------------------------------------------- animations
STEP = 0.05


def smooth(points):
    """Cosine-eased curve through (time, value) waypoints; values may be numbers."""
    pts = sorted(points)

    def fn(t):
        if t <= pts[0][0]:
            return pts[0][1]
        for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
            if t <= t1:
                k = (t - t0) / (t1 - t0)
                k = 0.5 - 0.5 * math.cos(math.pi * k)
                return v0 + (v1 - v0) * k
        return pts[-1][1]
    return fn


def keys(length, fn, extra=()):
    """Keyframes on a dense grid (plus extra times), so linear interpolation still reads as a smooth curve."""
    times = {round(k * STEP, 4) for k in range(int(round(length / STEP)) + 1)}
    times |= {round(t, 4) for t in extra if 0 <= t <= length}
    out = {}
    for t in sorted(times):
        v = fn(t)
        out[f"{t:.2f}"] = [round(float(a), 4) + 0.0 for a in v]
    return out


def v3(x=0.0, y=0.0, z=0.0):
    return [x, y, z]


def phase_fn(length):
    return lambda t: 2 * math.pi * t / length


# idle: breathing bob, lazy arm sway, one blink
IDLE_L = 2.0


def blink(t):
    return smooth([(0, 1), (1.30, 1), (1.38, 0.1), (1.46, 0.1), (1.56, 1), (2.0, 1)])(t)


def idle():
    ph = phase_fn(IDLE_L)
    eye = lambda t: v3(1, blink(t), 1)
    return {
        "body": {"position": keys(IDLE_L, lambda t: v3(0, 0.3 * (0.5 - 0.5 * math.cos(ph(t))), 0)),
                 "rotation": keys(IDLE_L, lambda t: v3(0.8 * math.sin(ph(t) + 0.5), 0, 1.2 * math.sin(ph(t))))},
        "arm_l": {"rotation": keys(IDLE_L, lambda t: v3(3 * math.sin(ph(t) + 0.6), 0, 5 * math.sin(ph(t) + 0.3)))},
        "arm_r": {"rotation": keys(IDLE_L, lambda t: v3(-3 * math.sin(ph(t) + 0.2), 0, -5 * math.sin(ph(t) + 0.9)))},
        "eye_l": {"scale": keys(IDLE_L, eye, (1.30, 1.38, 1.46, 1.56))},
        "eye_r": {"scale": keys(IDLE_L, eye, (1.30, 1.38, 1.46, 1.56))},
    }


# walk: diagonal gait, bobbing, waddle
WALK_L = 0.6


def walk():
    ph = phase_fn(WALK_L)
    swing = 32.0

    def leg(sign):
        rot = keys(WALK_L, lambda t: v3(sign * swing * math.sin(ph(t)), 0, 0))
        lift = keys(WALK_L, lambda t: v3(0, 0.4 * max(0.0, sign * math.cos(ph(t))), 0))
        return {"rotation": rot, "position": lift}

    return {
        "leg_fl": leg(1), "leg_br": leg(1), "leg_fr": leg(-1), "leg_bl": leg(-1),
        "body": {"position": keys(WALK_L, lambda t: v3(0.25 * math.sin(ph(t)), 0.3 * (0.5 + 0.5 * math.cos(2 * ph(t))), 0)),
                 "rotation": keys(WALK_L, lambda t: v3(0, 3 * math.sin(ph(t) + math.pi / 2), 4 * math.sin(ph(t))))},
        "arm_l": {"rotation": keys(WALK_L, lambda t: v3(-26 * math.sin(ph(t)), 0, 4 * math.sin(2 * ph(t))))},
        "arm_r": {"rotation": keys(WALK_L, lambda t: v3(26 * math.sin(ph(t)), 0, -4 * math.sin(2 * ph(t))))},
    }


# attack: wind up, lunge forward, right claw snaps open and shut, recover
ATTACK_L = 0.4


def attack():
    L = ATTACK_L
    lunge = smooth([(0, 0), (0.1, 0.4), (0.18, -1.6), (0.27, -1.2), (0.4, 0)])
    crouch = smooth([(0, 0), (0.1, -0.35), (0.18, 0.15), (0.4, 0)])
    lean = smooth([(0, 0), (0.1, -6), (0.18, 14), (0.4, 0)])
    arm_r = smooth([(0, 0), (0.1, -70), (0.18, 45), (0.28, 25), (0.4, 0)])
    arm_r_z = smooth([(0, 0), (0.1, -14), (0.18, 8), (0.4, 0)])
    arm_l = smooth([(0, 0), (0.1, 14), (0.2, -22), (0.4, 0)])
    snap = smooth([(0, 0), (0.1, 0.5), (0.17, 0.5), (0.21, 0), (0.27, 0.4), (0.33, 0.4), (0.37, 0), (0.4, 0)])
    squint = smooth([(0, 1), (0.18, 0.55), (0.32, 0.55), (0.4, 1)])
    ex = (0.1, 0.18, 0.21, 0.27, 0.33, 0.37)
    return {
        "root": {"position": keys(L, lambda t: v3(0, 0, lunge(t)), ex)},
        "body": {"position": keys(L, lambda t: v3(0, crouch(t), 0), ex),
                 "rotation": keys(L, lambda t: v3(lean(t), 0, 0), ex)},
        "arm_r": {"rotation": keys(L, lambda t: v3(arm_r(t), 0, arm_r_z(t)), ex)},
        "arm_l": {"rotation": keys(L, lambda t: v3(arm_l(t), 0, 0), ex)},
        "claw_r_top": {"position": keys(L, lambda t: v3(0, snap(t), 0), ex)},
        "claw_r_bot": {"position": keys(L, lambda t: v3(0, -snap(t), 0), ex)},
        "eye_l": {"scale": keys(L, lambda t: v3(1, squint(t), 1), ex)},
        "eye_r": {"scale": keys(L, lambda t: v3(1, squint(t), 1), ex)},
    }


# jump_off: crouch, leap with a spin and a tumble, land squashed, settle
JUMP_L = 0.7


def jump_off():
    L = JUMP_L
    T0, T1, PEAK = 0.12, 0.56, 7.0
    mid = (T0 + T1) / 2

    def height(t):
        if t <= T0 or t >= T1:
            return 0.0
        return PEAK * (1 - ((t - mid) / (mid - T0)) ** 2)

    sy = smooth([(0, 1), (0.12, 0.78), (0.2, 1.22), (0.34, 1.04), (0.5, 1.12), (0.56, 0.72), (0.63, 1.08), (0.7, 1)])
    sx = smooth([(0, 1), (0.12, 1.15), (0.2, 0.88), (0.34, 0.98), (0.5, 0.93), (0.56, 1.2), (0.63, 0.96), (0.7, 1)])
    spin = smooth([(0, 0), (0.14, 0), (0.54, 360), (0.7, 360)])
    flip = smooth([(0, 0), (0.18, 0), (0.5, 360), (0.7, 360)])
    tuck = smooth([(0, 0), (0.12, 10), (0.22, 55), (0.44, 55), (0.56, -10), (0.7, 0)])
    flail = smooth([(0, 0), (0.14, 0), (0.22, 1), (0.5, 1), (0.56, 0), (0.7, 0)])
    squint = smooth([(0, 1), (0.12, 0.5), (0.2, 1.25), (0.5, 1.25), (0.56, 0.3), (0.66, 1), (0.7, 1)])
    ex = (T0, T1, mid, 0.2, 0.5, 0.56)
    grid_ph = lambda t: 2 * math.pi * 3.0 * t
    return {
        "root": {"position": keys(L, lambda t: v3(0, height(t), 0), ex),
                 "rotation": keys(L, lambda t: v3(0, spin(t), 0), ex),
                 "scale": keys(L, lambda t: v3(sx(t), sy(t), sx(t)), ex)},
        "body": {"rotation": keys(L, lambda t: v3(flip(t), 0, 0), ex)},
        "leg_fl": {"rotation": keys(L, lambda t: v3(tuck(t), 0, 0), ex)},
        "leg_fr": {"rotation": keys(L, lambda t: v3(tuck(t), 0, 0), ex)},
        "leg_bl": {"rotation": keys(L, lambda t: v3(-tuck(t), 0, 0), ex)},
        "leg_br": {"rotation": keys(L, lambda t: v3(-tuck(t), 0, 0), ex)},
        "arm_l": {"rotation": keys(L, lambda t: v3(0, 0, 40 * flail(t) * math.sin(grid_ph(t))), ex),
                  "position": keys(L, lambda t: v3(0, 0.8 * flail(t), 0), ex)},
        "arm_r": {"rotation": keys(L, lambda t: v3(0, 0, -40 * flail(t) * math.sin(grid_ph(t) + 0.8)), ex),
                  "position": keys(L, lambda t: v3(0, 0.8 * flail(t), 0), ex)},
        "eye_l": {"scale": keys(L, lambda t: v3(1, squint(t), 1), ex)},
        "eye_r": {"scale": keys(L, lambda t: v3(1, squint(t), 1), ex)},
    }


# happy: two little hops, both arms up and waving, eyes squeezed into happy slits
HAPPY_L = 0.8


def happy():
    L = HAPPY_L

    def hop(t):
        return 1.6 * math.sin(math.pi * (t % 0.4) / 0.4) ** 1.2 if t < L - 1e-9 else 0.0

    def sy(t):
        return 0.88 + 0.22 * min(1.0, hop(t) / 1.0)

    lift = smooth([(0, 0), (0.12, 1), (0.68, 1), (0.8, 0)])
    squint = smooth([(0, 1), (0.1, 0.4), (0.7, 0.4), (0.8, 1)])
    wave = lambda t: 8 * math.sin(2 * math.pi * 5 * t)
    snap = lambda t: 0.35 * max(0.0, math.sin(2 * math.pi * 5 * t)) * lift(t)
    ex = (0.4,)
    return {
        "root": {"position": keys(L, lambda t: v3(0, hop(t), 0), ex),
                 "scale": keys(L, lambda t: v3(1 / math.sqrt(sy(t)), sy(t), 1 / math.sqrt(sy(t))), ex)},
        "body": {"rotation": keys(L, lambda t: v3(0, 0, 3 * math.sin(2 * math.pi * 2.5 * t)), ex)},
        "arm_l": {"position": keys(L, lambda t: v3(0, 1.4 * lift(t), 0), ex),
                  "rotation": keys(L, lambda t: v3(0, 0, lift(t) * (30 + wave(t))), ex)},
        "arm_r": {"position": keys(L, lambda t: v3(0, 1.4 * lift(t), 0), ex),
                  "rotation": keys(L, lambda t: v3(0, 0, -lift(t) * (30 + wave(t + 0.05))), ex)},
        "claw_l_top": {"position": keys(L, lambda t: v3(0, snap(t), 0), ex)},
        "claw_l_bot": {"position": keys(L, lambda t: v3(0, -snap(t), 0), ex)},
        "claw_r_top": {"position": keys(L, lambda t: v3(0, snap(t + 0.05), 0), ex)},
        "claw_r_bot": {"position": keys(L, lambda t: v3(0, -snap(t + 0.05), 0), ex)},
        "eye_l": {"scale": keys(L, lambda t: v3(1, squint(t), 1), ex)},
        "eye_r": {"scale": keys(L, lambda t: v3(1, squint(t), 1), ex)},
        "leg_fl": {"rotation": keys(L, lambda t: v3(-12 * hop(t) / 1.6, 0, 0), ex)},
        "leg_fr": {"rotation": keys(L, lambda t: v3(-12 * hop(t) / 1.6, 0, 0), ex)},
        "leg_bl": {"rotation": keys(L, lambda t: v3(12 * hop(t) / 1.6, 0, 0), ex)},
        "leg_br": {"rotation": keys(L, lambda t: v3(12 * hop(t) / 1.6, 0, 0), ex)},
    }


ANIMS = {
    "animation.cogwit.idle": (True, IDLE_L, idle()),
    "animation.cogwit.walk": (True, WALK_L, walk()),
    "animation.cogwit.attack": (False, ATTACK_L, attack()),
    "animation.cogwit.jump_off": (False, JUMP_L, jump_off()),
    "animation.cogwit.happy": (False, HAPPY_L, happy()),
}
anim = {"format_version": "1.8.0", "animations": {
    name: {"loop": loop, "animation_length": length, "bones": bone_tracks}
    for name, (loop, length, bone_tracks) in ANIMS.items()}}
anim_path.write_text(json.dumps(anim, indent=1))

# ---------------------------------------------------------------- validation
geo_back = json.loads(geo_path.read_text())
names = {b["name"] for b in geo_back["minecraft:geometry"][0]["bones"]}
missing = [n for n in REQUIRED_BONES if n not in names]
assert not missing, f"missing bones {missing}"
for b in geo_back["minecraft:geometry"][0]["bones"]:
    if "parent" in b:
        assert b["parent"] in names, f"{b['name']} has unknown parent {b['parent']}"
    for c in b.get("cubes", []):
        assert set(c["uv"]) == set(FACES), f"{b['name']} cube lacks faces"
        for f, u in c["uv"].items():
            ux, uy = u["uv"]
            uw, uh = u["uv_size"]
            assert 0 <= ux and ux + uw <= ATLAS_W and 0 <= uy and uy + uh <= ATLAS_H, f"{b['name']} {f} outside atlas"
assert not [b for b in geo_back["minecraft:geometry"][0]["bones"] if b["name"] == "item_r"][0].get("cubes")
for variant in PALETTES:
    im = Image.open(tex_dir / f"{variant}.png")
    assert im.mode == "RGBA" and im.size == (ATLAS_W, ATLAS_H), f"{variant} texture mismatch"
for name, (loop, length, tracks) in ANIMS.items():
    for bname, chans in tracks.items():
        assert bname in names, f"{name} animates unknown bone {bname}"
        for ch, frames in chans.items():
            assert ch in ("position", "rotation", "scale")
            assert max(float(k) for k in frames) <= length + 1e-6, f"{name}/{bname} runs past the animation end"
            if loop:
                assert frames["0.00"] == frames[f"{length:.2f}"], f"{name}/{bname}/{ch} does not close its loop"

# ---------------------------------------------------------------- preview: a tiny box ray caster
ABS = []  # (bone, min, max, uv, mat)
for b in bones:
    for c in b["cubes"]:
        o, s = c["origin"], c["size"]
        ABS.append((b["name"], o, [o[k] + s[k] for k in range(3)], c["uv"], c["mat"]))


def norm(v):
    n = math.sqrt(sum(a * a for a in v))
    return tuple(a / n for a in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


FACE_SHADE = {"up": 1.0, "north": 0.9, "south": 0.74, "east": 0.8, "west": 0.84, "down": 0.55}


def render_view(tex, az, el, hidden=(), tint=None, size=(400, 360), scale=22.0, label=""):
    az_r, el_r = math.radians(az), math.radians(el)
    cam = (math.sin(az_r) * math.cos(el_r), math.sin(el_r), -math.cos(az_r) * math.cos(el_r))
    f = norm(tuple(-a for a in cam))
    r = norm(cross(f, (0, 1, 0)))
    u = cross(r, f)
    cx, cy = size[0] / 2, size[1] * 0.6
    centre = (0, 4.5, 0)
    ac, bc = dot(centre, r), dot(centre, u)
    out = Image.new("RGB", size, (0, 0, 0))
    d = ImageDraw.Draw(out)
    for j in range(size[1]):
        t = j / size[1]
        d.line([(0, j), (size[0], j)], fill=(int(58 - 20 * t), int(60 - 20 * t), int(72 - 22 * t)))
    # soft ground disc
    gx, gy = cx, cy + bc * scale
    ry = 6.0 * scale * abs(math.sin(el_r))
    d.ellipse((gx - 7.0 * scale, gy - ry, gx + 7.0 * scale, gy + ry), fill=(30, 31, 40))
    px = out.load()
    zbuf = {}
    tpx = tex.load()
    for name, lo, hi, uv, mat in ABS:
        if name in hidden:
            continue
        xs, ys = [], []
        for ix in (lo[0], hi[0]):
            for iy in (lo[1], hi[1]):
                for iz in (lo[2], hi[2]):
                    p = (ix, iy, iz)
                    xs.append(cx + (dot(p, r) - ac) * scale)
                    ys.append(cy - (dot(p, u) - bc) * scale)
        for sy in range(max(0, int(min(ys))), min(size[1], int(max(ys)) + 1)):
            for sx in range(max(0, int(min(xs))), min(size[0], int(max(xs)) + 1)):
                a = (sx + 0.5 - cx) / scale + ac
                b = -(sy + 0.5 - cy) / scale + bc
                o = tuple(a * r[k] + b * u[k] for k in range(3))
                t0, t1, axis_in, sign_in = -1e9, 1e9, None, 0
                ok = True
                for k in range(3):
                    if abs(f[k]) < 1e-9:
                        if not lo[k] <= o[k] <= hi[k]:
                            ok = False
                            break
                        continue
                    ta, tb = (lo[k] - o[k]) / f[k], (hi[k] - o[k]) / f[k]
                    side = -1 if f[k] > 0 else 1  # entering through the min face if the ray moves +k
                    if ta > tb:
                        ta, tb = tb, ta
                    if ta > t0:
                        t0, axis_in, sign_in = ta, k, side
                    t1 = min(t1, tb)
                if not ok or t0 > t1 or axis_in is None:
                    continue
                if (sx, sy) in zbuf and zbuf[(sx, sy)] <= t0:
                    continue
                h = tuple(o[k] + f[k] * t0 for k in range(3))
                sz = [hi[k] - lo[k] for k in range(3)]
                k = axis_in
                face = {(0, 1): "east", (0, -1): "west", (1, 1): "up", (1, -1): "down",
                        (2, -1): "north", (2, 1): "south"}[(k, sign_in)]
                fx = [(h[i] - lo[i]) / sz[i] if sz[i] else 0 for i in range(3)]
                if face == "north":
                    uu, vv = 1 - fx[0], 1 - fx[1]
                elif face == "south":
                    uu, vv = fx[0], 1 - fx[1]
                elif face == "east":
                    uu, vv = 1 - fx[2], 1 - fx[1]
                elif face == "west":
                    uu, vv = fx[2], 1 - fx[1]
                elif face == "up":
                    uu, vv = fx[0], fx[2]
                else:
                    uu, vv = fx[0], 1 - fx[2]
                (ux, uy), (uw, uh) = uv[face]["uv"], uv[face]["uv_size"]
                c = tpx[ux + min(uw - 1, max(0, int(uu * uw))), uy + min(uh - 1, max(0, int(vv * uh)))]
                col = [c[i] for i in range(3)]
                if tint and mat in ("shell", "helmet", "helmet_brim"):
                    col = [col[i] * tint[i] / 255 for i in range(3)]
                shade = FACE_SHADE[face]
                zbuf[(sx, sy)] = t0
                px[sx, sy] = tuple(min(255, int(col[i] * shade)) for i in range(3))
    return out


try:
    FONT = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 18)
except OSError:
    FONT = ImageFont.load_default()

TINTS = {"cogwit": (186, 192, 204), "spark": (255, 205, 95), "rivet": (96, 226, 220), "anvil": (84, 72, 96)}
tile_w, tile_h = 400, 360
atlas_scale = 3
sheet_h = tile_h * 2 + ATLAS_H * atlas_scale + 70
sheet = Image.new("RGB", (tile_w * 4, sheet_h), (24, 25, 31))
sd = ImageDraw.Draw(sheet)
for n, variant in enumerate(PALETTES):
    front = render_view(textures[variant], 32, 22, hidden=("shell", "helmet"))
    back = render_view(textures[variant], 212, 22, tint=TINTS[variant])
    sheet.paste(front, (n * tile_w, 0))
    sheet.paste(back, (n * tile_w, tile_h))
    sd.text((n * tile_w + 14, 10), variant, fill=(245, 240, 232), font=FONT)
    sd.text((n * tile_w + 14, tile_h + 10), f"{variant}  back, shell + helmet tinted", fill=(245, 240, 232), font=FONT)
    big = textures[variant].resize((ATLAS_W * atlas_scale, ATLAS_H * atlas_scale), Image.NEAREST)
    bg = Image.new("RGB", big.size, (96, 98, 108))
    bg.paste(big, (0, 0), big)
    sheet.paste(bg, (n * tile_w + 14, tile_h * 2 + 40))
    sd.text((n * tile_w + 14, tile_h * 2 + 10), f"{variant}.png {ATLAS_W}x{ATLAS_H}", fill=(245, 240, 232), font=FONT)
sheet.save(PREVIEW)

print(f"atlas {ATLAS_W}x{ATLAS_H}  faces {len(placed)}  bones {len(bones)}  textures {list(PALETTES)}")
for name, (loop, length, tracks) in ANIMS.items():
    print(f"{name}: loop={loop} length={length}s bones={len(tracks)}")
