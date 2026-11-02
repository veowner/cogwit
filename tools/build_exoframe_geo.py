"""Builds the copper exoframe armor for GeckoLib: geometry, packed texture and glow mask.

The exoframe is the mid-game armor between iron and the Event Horizon: a copper frame around the body with
Cogwit's orange light at its heart. Three looks to choose from:
  A  frame: thin copper rods, joints and straps, goggles, an open harness with a caged core lamp
  B  miner: riveted copper plates, a domed helmet with a headlamp and a grill, a boiler on the back
  C  prototype: copper plates in the Horizon's silhouette with patina accents, a ring core and a visor slit

Usage: build_exoframe_geo.py <resources_root> <preview_png> [A|B|C, A by default]

Also writes the idle animation and the four 16x16 inventory icons.
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

RES = Path(sys.argv[1])
PREVIEW = sys.argv[2]
VARIANT = sys.argv[3] if len(sys.argv) > 3 else "A"  # picked on 2026-11-01: the frame, the boldest step up to the Horizon
S = 2  # texels per model pixel
rng = random.Random(4219)

COPPER = (204, 112, 72, 255)
COPPER_HI = (240, 162, 112, 255)
COPPER_LO = (138, 70, 42, 255)
COPPER_DARK = (96, 50, 32, 255)
LEATHER = (58, 40, 32, 255)
LEATHER_HI = (86, 62, 48, 255)
IRON = (70, 72, 78, 255)
IRON_HI = (120, 124, 132, 255)
GLOW = (255, 160, 80, 255)
GLOW_HOT = (255, 236, 196, 255)
GLOW_LO = (196, 92, 40, 255)
PATINA = (86, 168, 138, 255)
PATINA_LO = (52, 112, 92, 255)
GLASS = (34, 22, 18, 255)

EMISSIVE = set()
bones = []


def bone(name, parent=None, pivot=(0, 0, 0)):
    b = {"name": name, "pivot": list(pivot), "cubes": []}
    if parent:
        b["parent"] = parent
    bones.append(b)
    return b


def cube(b, origin, size, mat):
    b["cubes"].append({"origin": list(origin), "size": list(size), "mat": mat})


def mirror_x(origin, size):
    """The same box on the other side of the body."""
    return (-(origin[0] + size[0]), origin[1], origin[2]), size


def both(b_r, b_l, origin, size, mat):
    cube(b_r, origin, size, mat)
    o, s = mirror_x(origin, size)
    cube(b_l, o, s, mat)


# the eight armor bones GeckoLib moves with the player's limbs
head = bone("armorHead", pivot=(0, 24, 0))
body = bone("armorBody", pivot=(0, 24, 0))
arm_r = bone("armorRightArm", pivot=(-5, 22, 0))
arm_l = bone("armorLeftArm", pivot=(5, 22, 0))
leg_r = bone("armorRightLeg", pivot=(-1.9, 12, 0))
leg_l = bone("armorLeftLeg", pivot=(1.9, 12, 0))
boot_r = bone("armorRightBoot", pivot=(-1.9, 12, 0))
boot_l = bone("armorLeftBoot", pivot=(1.9, 12, 0))
core = bone("core", "armorBody", (0, 19.5, -3))


def frame_variant():
    # head: a band round the brow, two crown ribs, goggles with orange lenses, ear joints
    for o, s in (((-4.5, 28.5, -4.6), (9, 1, 0.5)), ((-4.5, 28.5, 4.1), (9, 1, 0.5)),
                 ((-4.6, 28.5, -4.5), (0.5, 1, 9)), ((4.1, 28.5, -4.5), (0.5, 1, 9))):
        cube(head, o, s, "rod")
    cube(head, (-0.5, 32.0, -4.5), (1, 0.5, 9), "rod")
    cube(head, (-4.5, 32.0, -0.5), (9, 0.5, 1), "rod")
    for x in (-4.6, 4.1):
        cube(head, (x, 29.5, -0.5), (0.5, 2.5, 1), "rod")
        cube(head, (x - 0.1 if x < 0 else x, 26.3, -1.2), (0.6, 2.4, 2.4), "joint")
    for x in (-3.6, 0.8):
        cube(head, (x, 26.9, -4.95), (2.8, 2.0, 0.5), "lens")
    cube(head, (-0.8, 27.4, -4.85), (1.6, 0.6, 0.3), "rod")
    # a forehead plate and a nape plate, so the head reads as armored
    cube(head, (-3.0, 29.5, -4.75), (6.0, 1.8, 0.5), "plate")
    cube(head, (-3.5, 25.0, 4.1), (7.0, 3.5, 0.5), "plate")
    # body: harness straps, belt, the caged core lamp, a spine with two rails and a battery canister
    for x in (-3.2, 2.2):
        cube(body, (x, 13.5, -2.6), (1.0, 10.5, 0.4), "strap")
        cube(body, (x, 13.5, 2.2), (1.0, 10.5, 0.4), "strap")
    cube(body, (-4.5, 12.0, -2.75), (9, 1.5, 5.5), "belt")
    cube(body, (-3.5, 23.4, -2.7), (7, 0.8, 0.5), "rod")
    cube(core, (-1.4, 18.1, -3.25), (2.8, 2.8, 0.8), "lamp")
    for o, s in (((-1.9, 21.0, -3.4), (3.8, 0.5, 0.5)), ((-1.9, 17.6, -3.4), (3.8, 0.5, 0.5)),
                 ((-1.9, 17.6, -3.4), (0.5, 3.9, 0.5)), ((1.4, 17.6, -3.4), (0.5, 3.9, 0.5))):
        cube(core, o, s, "rod")
    cube(body, (-0.5, 12.5, 2.3), (1, 11, 0.8), "rod")
    for x in (-3.0, 2.2):
        cube(body, (x, 13.0, 2.3), (0.8, 10, 0.8), "rod")
    cube(body, (-1.5, 15.0, 2.9), (3, 6, 2), "canister")
    # arms: a shoulder joint, a rod down the outside, an elbow joint and a strapped forearm bracer
    both(arm_r, arm_l, (-8.7, 21.3, -1.5), (0.6, 3, 3), "joint")
    both(arm_r, arm_l, (-8.5, 16.0, -0.5), (0.5, 5.4, 1), "rod")
    both(arm_r, arm_l, (-8.7, 14.6, -1.2), (0.6, 2.4, 2.4), "joint")
    both(arm_r, arm_l, (-8.5, 11.5, -2.5), (5, 3, 5), "bracer")
    # legs: hip joint, thigh rod, knee disc, shin rod; boots with a dark sole
    both(leg_r, leg_l, (-4.6, 9.6, -1.2), (0.6, 2.4, 2.4), "joint")
    both(leg_r, leg_l, (-4.4, 5.0, -0.5), (0.5, 4.6, 1), "rod")
    both(leg_r, leg_l, (-3.1, 5.0, -2.75), (2.4, 2.4, 0.5), "joint")
    both(leg_r, leg_l, (-2.4, 3.6, -2.6), (1.0, 1.6, 0.4), "rod")
    both(boot_r, boot_l, (-4.3, -0.3, -2.6), (4.6, 3.6, 5.0), "boot")


def miner_variant():
    # head: a copper dome with a brim, a headlamp and a grill over the mouth
    cube(head, (-4.5, 27.0, -4.5), (9, 5.5, 9), "plate")
    cube(head, (-5.0, 27.0, -5.0), (10, 0.7, 10), "plate_dark")
    cube(head, (-1.0, 29.6, -5.3), (2, 2, 0.8), "lamp")
    cube(head, (-3.6, 24.0, -4.9), (7.2, 3.0, 0.5), "grill")
    for x in (-4.6, 4.1):
        cube(head, (x, 24.0, -4.5), (0.5, 3.0, 9), "plate_dark")
    for x in (-3.6, 0.8):
        cube(head, (x, 27.8, -4.95), (2.8, 1.4, 0.4), "lens")
    # body: a riveted breastplate with a pressure gauge, a belt, a boiler on the back with pipes to the shoulders
    cube(body, (-4.5, 14.0, -2.75), (9, 10, 5.5), "plate")
    cube(body, (-4.5, 12.0, -2.9), (9, 2, 5.8), "belt")
    cube(core, (-1.6, 18.0, -3.3), (3.2, 3.2, 0.6), "gauge")
    cube(body, (-3.0, 13.0, 2.8), (6, 9, 3), "canister")
    for x in (-3.4, 2.4):
        cube(body, (x, 21.5, 2.8), (1, 1, 3), "rod")
        cube(body, (x, 22.5, 0.5), (1, 1.5, 1), "rod")
    # arms: round shoulder pads and heavy bracers
    both(arm_r, arm_l, (-8.9, 21.0, -3.0), (5.4, 3.0, 6.0), "plate")
    both(arm_r, arm_l, (-8.6, 11.5, -2.6), (5.2, 4.5, 5.2), "bracer")
    both(arm_r, arm_l, (-8.6, 17.0, -2.6), (0.6, 4.0, 5.2), "strap")
    # legs: plates with knee guards; heavy boots
    both(leg_r, leg_l, (-4.2, 4.5, -2.4), (4.4, 7.5, 4.8), "plate_dark")
    both(leg_r, leg_l, (-3.6, 4.8, -2.9), (3.2, 2.6, 0.5), "plate")
    both(boot_r, boot_l, (-4.4, -0.3, -2.8), (4.8, 4.2, 5.4), "boot")


def prototype_variant():
    # head: a box helm like the Horizon's, a single orange slit, patina on the brow
    cube(head, (-4.5, 23.5, -4.5), (9, 9, 9), "plate")
    cube(head, (-4.2, 27.8, -4.75), (8.4, 1.2, 0.3), "slit")
    cube(head, (-4.7, 30.4, -4.9), (9.4, 1.0, 0.5), "patina")
    cube(head, (-0.5, 32.5, -3.5), (1, 0.8, 7), "patina")
    # body: a breastplate with a ring core, collar, belt and a small back unit
    cube(body, (-4.5, 11.5, -2.75), (9, 13, 5.5), "plate")
    cube(body, (-4.75, 11.25, -3.0), (9.5, 1.5, 6), "belt")
    cube(body, (-3.2, 23.4, -3.35), (6.4, 1.2, 0.6), "patina")
    cube(core, (-1.25, 18.25, -3.35), (2.5, 2.5, 0.4), "lamp")
    for o, s in (((-2.2, 21.0, -3.3), (4.4, 0.6, 0.5)), ((-2.2, 17.4, -3.3), (4.4, 0.6, 0.5)),
                 ((-2.2, 17.4, -3.3), (0.6, 4.2, 0.5)), ((1.6, 17.4, -3.3), (0.6, 4.2, 0.5))):
        cube(core, o, s, "rod")
    cube(body, (-2.5, 15.0, 2.75), (5, 6.5, 1.5), "canister")
    # arms: pauldrons with patina rims and bracers
    both(arm_r, arm_l, (-9.8, 21.0, -3.3), (5.6, 3.0, 6.6), "plate")
    both(arm_r, arm_l, (-9.6, 20.4, -3.1), (5.2, 0.6, 6.2), "patina")
    both(arm_r, arm_l, (-8.5, 11.5, -2.5), (5, 13, 5), "plate_dark")
    both(arm_r, arm_l, (-8.8, 11.2, -2.8), (5.6, 3.6, 5.6), "bracer")
    # legs and boots: plates with patina knees
    both(leg_r, leg_l, (-4.2, 3.5, -2.3), (4.6, 8.75, 4.6), "plate")
    both(leg_r, leg_l, (-3.7, 4.6, -2.8), (3.6, 2.6, 0.6), "patina")
    both(boot_r, boot_l, (-4.3, -0.3, -2.6), (4.8, 4.0, 5.0), "boot")


{"A": frame_variant, "B": miner_variant, "C": prototype_variant}[VARIANT]()

# ---------------- UV packing ----------------
FACES = ("north", "south", "east", "west", "up", "down")


def face_dims(size, face):
    x, y, z = size
    return {"north": (x, y), "south": (x, y), "east": (z, y), "west": (z, y), "up": (x, z), "down": (x, z)}[face]


requests = []
for b in bones:
    for c in b["cubes"]:
        for f in FACES:
            w, h = face_dims(c["size"], f)
            requests.append((max(1, math.ceil(h * S)), max(1, math.ceil(w * S)), b, c, f))
requests.sort(key=lambda r: (-r[0], -r[1]))
atlas_w = 256
x = y = shelf = 0
placed = []
for th, tw, b, c, f in requests:
    if x + tw > atlas_w:
        x, y, shelf = 0, y + shelf, 0
    placed.append((x, y, tw, th, c, f))
    x += tw
    shelf = max(shelf, th)
atlas_h = 64
while atlas_h < y + shelf:
    atlas_h *= 2
img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
px = img.load()


def put(i, j, colour, glow=False):
    px[i, j] = colour
    if glow:
        EMISSIVE.add((i, j))


def shade(c, n):
    return tuple(max(0, min(255, c[k] + n)) for k in range(3)) + (255,)


def metal(x0, y0, w, h, base, hi, lo, noise=3):
    """Beveled metal: light top and left edge, dark bottom and right edge, a little grain."""
    for j in range(h):
        for i in range(w):
            c = shade(base, rng.randint(-noise, noise))
            if j == 0 or i == 0:
                c = hi
            elif j == h - 1 or i == w - 1:
                c = lo
            put(x0 + i, y0 + j, c)


def rivets(x0, y0, w, h):
    if w < 6 or h < 6:
        return
    for i, j in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
        put(x0 + i, y0 + j, COPPER_HI)
        put(x0 + i + 1, y0 + j + 1, COPPER_DARK)


def glow_disc(x0, y0, w, h):
    cx, cy, r = (w - 1) / 2, (h - 1) / 2, min(w, h) / 2
    for j in range(h):
        for i in range(w):
            d = math.hypot(i - cx, j - cy) / max(r, 0.5)
            c = GLOW_HOT if d < 0.35 else GLOW if d < 0.8 else GLOW_LO
            put(x0 + i, y0 + j, c, glow=True)


for x0, y0, tw, th, c, f in placed:
    m = c["mat"]
    if m == "rod":
        metal(x0, y0, tw, th, COPPER, COPPER_HI, COPPER_LO, 2)
    elif m == "joint":
        metal(x0, y0, tw, th, COPPER_LO, COPPER_HI, COPPER_DARK)
        if f in ("east", "west", "north") and tw >= 3 and th >= 3:
            put(x0 + tw // 2, y0 + th // 2, GLOW, glow=True)
    elif m == "strap":
        metal(x0, y0, tw, th, LEATHER, LEATHER_HI, (36, 24, 18, 255), 2)
        for j in range(2, th - 1, 6):
            for i in range(tw):
                put(x0 + i, y0 + j, IRON_HI)
    elif m == "belt":
        metal(x0, y0, tw, th, LEATHER, LEATHER_HI, (36, 24, 18, 255), 2)
        if f == "north":
            for j in range(th):
                for i in range(tw // 2 - 1, tw // 2 + 2):
                    put(x0 + i, y0 + j, COPPER_HI if j in (0, th - 1) else COPPER)
    elif m in ("plate", "plate_dark", "bracer", "boot"):
        base = {"plate": COPPER, "plate_dark": COPPER_LO, "bracer": COPPER, "boot": COPPER_LO}[m]
        metal(x0, y0, tw, th, base, COPPER_HI, COPPER_DARK)
        rivets(x0, y0, tw, th)
        if m == "boot" and f in ("north", "south", "east", "west"):
            for i in range(tw):
                put(x0 + i, y0 + th - 1, LEATHER)
                put(x0 + i, y0 + th - 2, LEATHER)
        if m == "bracer" and f in ("north", "south", "east", "west"):
            for i in range(tw):
                put(x0 + i, y0 + th // 2, LEATHER)
    elif m == "patina":
        metal(x0, y0, tw, th, PATINA, (140, 210, 182, 255), PATINA_LO)
    elif m == "lamp":
        if f == "north":
            glow_disc(x0, y0, tw, th)
        else:
            metal(x0, y0, tw, th, COPPER_LO, COPPER_HI, COPPER_DARK)
    elif m == "lens":
        metal(x0, y0, tw, th, COPPER_LO, COPPER_HI, COPPER_DARK)
        if f == "north":
            for j in range(1, th - 1):
                for i in range(1, tw - 1):
                    put(x0 + i, y0 + j, GLOW_HOT if j == 1 and i < tw // 2 else GLOW, glow=True)
    elif m == "slit":
        for j in range(th):
            for i in range(tw):
                put(x0 + i, y0 + j, GLOW_HOT if j == th // 2 and 1 < i < tw - 2 else GLOW, glow=True)
    elif m == "grill":
        metal(x0, y0, tw, th, IRON, IRON_HI, (40, 40, 44, 255))
        if f == "north":
            for i in range(1, tw - 1, 2):
                for j in range(1, th - 1):
                    put(x0 + i, y0 + j, GLASS)
    elif m == "canister":
        metal(x0, y0, tw, th, COPPER, COPPER_HI, COPPER_DARK)
        rivets(x0, y0, tw, th)
        if f == "south":
            for j in range(th // 4, th - th // 4):
                for i in range(tw // 2 - 1, tw // 2 + 1):
                    put(x0 + i, y0 + j, GLOW, glow=True)
    elif m == "gauge":
        if f == "north":
            for j in range(th):
                for i in range(tw):
                    d = math.hypot(i - (tw - 1) / 2, j - (th - 1) / 2)
                    put(x0 + i, y0 + j, COPPER_HI if d > tw / 2 - 1 else (236, 226, 200, 255), glow=d <= tw / 2 - 1)
            for k in range(tw // 2):
                put(x0 + tw // 2 + k // 2, y0 + th // 2 - k // 2, GLOW_LO, glow=True)
        else:
            metal(x0, y0, tw, th, COPPER_LO, COPPER_HI, COPPER_DARK)
    else:
        raise SystemExit(f"no painter for {m}")
    c.setdefault("uv", {})[f] = {"uv": [x0, y0], "uv_size": [tw, th]}

geo_bones = []
for b in bones:
    out = {"name": b["name"], "pivot": b["pivot"]}
    if "parent" in b:
        out["parent"] = b["parent"]
    if b["cubes"]:
        out["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
    geo_bones.append(out)
geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.exoframe_armor", "texture_width": atlas_w, "texture_height": atlas_h,
                    "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
    "bones": geo_bones}]}
geo_path = RES / "assets/cogwit/geo/item/armor/exoframe_armor.geo.json"
tex_path = RES / "assets/cogwit/textures/item/armor/exoframe_armor.png"
glow_path = RES / "assets/cogwit/textures/item/armor/exoframe_armor_glowmask.png"
for p in (geo_path, tex_path):
    p.parent.mkdir(parents=True, exist_ok=True)
geo_path.write_text(json.dumps(geo, indent=1))
img.save(tex_path)
glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
gp = glow.load()
for (gx, gy) in EMISSIVE:
    gp[gx, gy] = px[gx, gy]
glow.save(glow_path)
img.resize((atlas_w * 3, atlas_h * 3), Image.NEAREST).save(PREVIEW)

# ---------------- animation: the core lamp beats like a slow heart ----------------
anim_path = RES / "assets/cogwit/animations/item/armor/exoframe_armor.animation.json"
anim_path.parent.mkdir(parents=True, exist_ok=True)
beat = {f"{t / 10:.1f}": [round(1 + 0.18 * math.exp(-((t / 10 % 2.0 - 0.2) / 0.12) ** 2), 4)] * 3 for t in range(0, 41)}
anim_path.write_text(json.dumps({"format_version": "1.8.0", "animations": {
    "animation.exoframe.idle": {"loop": True, "animation_length": 4.0, "bones": {"core": {"scale": beat}}}}}, indent=1))

# ---------------- inventory icons ----------------
ICON_PALETTE = {".": (0, 0, 0, 0), "K": (34, 20, 14, 255), "C": COPPER[:3] + (255,), "H": COPPER_HI, "L": COPPER_LO,
                "D": COPPER_DARK, "O": GLOW, "W": GLOW_HOT, "B": LEATHER, "I": IRON_HI}
ICONS = {
    "exoframe_upper": ["................", ".....KKKKKK.....", "....KWOKKOWK....", "....KKKKKKKK....",
                       "..KKKK....KKKK..", ".KHCCK....KCCLK.", ".KHCCKKKKKKCCLK.", ".KCLKBKKKKBKLCK.",
                       "..KK.BKWOKB.KK..", ".....BKOOKB.....", ".....BKKKKB.....", "......KBBK......",
                       ".....KKKKKK.....", ".....KHCCLK.....", ".....KKKKKK.....", "................"],
    "exoframe_lower": ["................", "....KKKKKKKK....", "....KBBBBBBK....", "....KHCCCCLK....",
                       "....KCK..KCK....", "....KCK..KCK....", "...KKOK..KOKK...", "....KCK..KCK....",
                       "....KCK..KCK....", "....KOK..KOK....", "...KHCK..KCLK...", "...KCCK..KCCK...",
                       "...KBBK..KBBK...", "...KKKK..KKKK...", "................", "................"],
}
# empty accessory slots show a faint outline of what goes in them, like the vanilla armor slots
SLOT_ICONS = {"exo_upper": "exoframe_upper", "exo_lower": "exoframe_lower"}
icon_dir = RES / "assets/cogwit/textures/item"
icon_dir.mkdir(parents=True, exist_ok=True)
for name, rows in ICONS.items():
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    icon = Image.new("RGBA", (16, 16))
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            icon.putpixel((i, j), ICON_PALETTE[ch])
    icon.save(icon_dir / f"{name}.png")
slot_dir = RES / "assets/cogwit/textures/slot"
slot_dir.mkdir(parents=True, exist_ok=True)
for slot, name in SLOT_ICONS.items():
    ghost = Image.new("RGBA", (16, 16))
    for j, row in enumerate(ICONS[name]):
        for i, ch in enumerate(row):
            if ch != ".":
                ghost.putpixel((i, j), (139, 139, 139, 255) if ch == "K" else (198, 198, 198, 120))
    ghost.save(slot_dir / f"{slot}.png")

print("atlas", atlas_w, atlas_h, "faces", len(placed), "bones", len(bones), "variant", VARIANT)
