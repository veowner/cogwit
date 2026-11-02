"""Builds the 3D Event Horizon armor for GeckoLib: geometry, packed texture, glow mask and idle animation.

Style: dark knight plate with amethyst tears, glowing accent rims on the pauldrons and helmet only, a cape,
and a living black-hole core on the chest (rotating rings, orbiting motes, a heartbeat that the cracks follow).

Usage: build_horizon_geo.py <resources_root> <preview_png> [A|B|C helmet and pauldron variant, B by default]

GeckoLib negates the x and y rotations of geo bones but not of animation keyframes: in the geometry a positive x
tilts the top of a bone backwards, in an animation a negative x swings the bottom of the faceplate up and out.
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

RES = Path(sys.argv[1])
PREVIEW = sys.argv[2]
S = 2  # texels per model pixel
rng = random.Random(1307)

# ---------------- palette ----------------
BLACK = (0, 0, 0, 255)
STEEL = (44, 46, 58, 255)
STEEL_HI = (92, 96, 116, 255)
STEEL_LO = (24, 25, 32, 255)
STEEL_DARK = (30, 31, 40, 255)
GOLD = (222, 168, 72, 255)
GOLD_HI = (246, 206, 120, 255)
GOLD_LO = (150, 104, 38, 255)
RIM = (255, 214, 160, 255)
VIOLET = (190, 120, 255, 255)
VIOLET_HI = (240, 214, 255, 255)
VIOLET_LO = (84, 40, 140, 255)
CAPE = (40, 16, 58, 255)
CAPE_LO = (26, 10, 38, 255)
VISOR = (236, 222, 255, 255)
COGWIT_ORANGE = (217, 119, 87, 255)

EMISSIVE = set()  # (x, y) texels that glow


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def ramp(stops, t):
    """stops: list of (pos, color)"""
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            return lerp(c0, c1, (t - p0) / max(1e-9, p1 - p0))
    return stops[-1][1]


# ---------------- geometry model ----------------
bones = []  # dicts: name, parent, pivot, rotation, cubes[(origin, size, material)]


def bone(name, parent=None, pivot=(0, 0, 0), rotation=None):
    b = {"name": name, "pivot": list(pivot), "cubes": []}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = list(rotation)
    bones.append(b)
    return b


def cube(b, origin, size, material):
    b["cubes"].append({"origin": list(origin), "size": list(size), "mat": material})


# ---------------- helmet variants ----------------
# Every helmet is a shell ("helm") and a faceplate that swings up from a hinge over the brow ("faceplate");
# Cogwit's eye rides on the faceplate so it lifts with it.
FACE_HINGE = (0, 31.6, -4.6)


def cogwit_eye_at(origin, size):
    eye = bone("cogwit_eye", "faceplate", FACE_HINGE)
    cube(eye, origin, size, "cogwit_eye")
    shut = bone("cogwit_eye_closed", "faceplate", FACE_HINGE)
    cube(shut, (origin[0], origin[1] + size[1] * 0.4, origin[2]), (size[0], size[1] * 0.2, size[2]), "cogwit_eye_closed")


def ear_discs():
    """Small accretion discs over the ears, each a ring of glowing segments that spins."""
    for side, sx in (("r", -1), ("l", 1)):
        x_out = 4.55 * sx
        ear = bone(f"ear_{side}", "helm", (x_out, 27.5, 0))
        cube(ear, (x_out - 0.3 if sx > 0 else x_out - 0.3, 26.0, -1.5), (0.6, 3.0, 3.0), "ear")
        ring = bone(f"ear_ring_{side}", f"ear_{side}", (x_out + 0.15 * sx, 27.5, 0))
        for k in range(8):
            seg = bone(f"ear_ring_{side}_{k}", f"ear_ring_{side}", (x_out + 0.15 * sx, 27.5, 0), rotation=(45 * k, 0, 0))
            cube(seg, (x_out + 0.15 * sx - 0.15, 27.5 + 1.9, -0.35), (0.3, 0.45, 0.7), "ring_violet")


def helmet_horizon():
    """A: a smooth dome with one wide visor band that wraps round to the ears, like an event horizon seen edge on."""
    helm = bone("helm", "armorHead", (0, 24, 0))
    cube(helm, (-4.5, 23.5, -4.5), (9, 8.6, 9), "helm")
    cube(helm, (-4.0, 32.1, -4.0), (8, 0.7, 8), "helm_cap")
    cube(helm, (-4.0, 24.5, 4.5), (8, 6.0, 0.5), "helm")
    cube(helm, (-0.6, 32.5, -3.5), (1.2, 0.6, 7.5), "gold")
    for x in (-4.8, 4.5):
        cube(helm, (x, 27.6, -4.3), (0.3, 1.4, 4.4), "visor_band")
    ear_discs()
    face = bone("faceplate", "armorHead", FACE_HINGE)
    cube(face, (-4.3, 23.7, -5.15), (8.6, 7.8, 0.65), "face_horizon")
    cube(face, (-3.2, 23.35, -5.35), (6.4, 1.4, 0.8), "chin_vents")
    cogwit_eye_at((-3.3, 29.65, -5.45), (1.6, 1.5, 0.3))


def helmet_knight():
    """B: an angular void knight: heavy brow, cheek blades, an angry V visor and a tall fin. The shell is open at the
    front, so the wearer's face shows when the faceplate is up."""
    helm = bone("helm", "armorHead", (0, 24, 0))
    cube(helm, (-4.5, 32.0, -4.5), (9, 0.5, 9), "helm_shell")
    cube(helm, (-4.5, 23.5, 4.0), (9, 8.5, 0.5), "helm_shell")
    for x in (-4.5, 4.0):
        cube(helm, (x, 23.5, -4.5), (0.5, 8.5, 8.5), "helm_shell")
    brow = bone("brow", "helm", (0, 31, -4.8), rotation=(-12, 0, 0))
    cube(brow, (-4.8, 30.3, -5.7), (9.6, 1.3, 1.7), "steel_dark_gold")
    for side, sx in (("r", -1), ("l", 1)):
        blade = bone(f"cheek_{side}", "helm", (4.7 * sx, 28, -3), rotation=(0, 14 * sx, 0))
        cube(blade, (4.7 * sx - 0.35, 23.0, -5.3), (0.7, 5.4, 4.6), "blade")
    fin = bone("fin", "helm", (0, 32.5, 0), rotation=(12, 0, 0))
    cube(fin, (-0.45, 32.3, -2.2), (0.9, 2.8, 6.8), "fin")
    face = bone("faceplate", "armorHead", FACE_HINGE)
    cube(face, (-4.3, 23.7, -5.15), (8.6, 6.7, 0.65), "face_knight")
    cube(face, (-0.45, 24.0, -5.55), (0.9, 5.6, 0.5), "gold")
    cogwit_eye_at((-3.4, 28.95, -5.45), (1.4, 1.2, 0.3))


def helmet_mask():
    """C: a sculpted mask in three plates with two eye slits, a jaw guard and ear panels."""
    helm = bone("helm", "armorHead", (0, 24, 0))
    cube(helm, (-4.5, 23.5, -4.5), (9, 9, 9), "helm")
    for x in (-4.85, 4.45):
        cube(helm, (x, 25.0, -2.5), (0.4, 4.5, 5.0), "steel_dark_gold")
    for x in (-1.8, 1.3):
        cube(helm, (x, 32.45, -4.0), (0.5, 0.4, 8.0), "gold")
    face = bone("faceplate", "armorHead", FACE_HINGE)
    cube(face, (-3.8, 29.5, -5.1), (7.6, 2.1, 0.6), "mask_top")
    cube(face, (-3.8, 26.6, -5.25), (7.6, 2.9, 0.75), "mask_eyes")
    cube(face, (-3.3, 24.3, -5.15), (6.6, 2.3, 0.65), "mask_low")
    cube(face, (-2.6, 23.4, -5.3), (5.2, 1.0, 0.8), "chin_vents")
    cogwit_eye_at((-3.3, 30.0, -5.4), (1.3, 1.2, 0.3))


# ---------------- pauldron variants ----------------
def pauldrons_layered(side, sx, arm):
    """A: three overlapping lames that fan out from the shoulder."""
    p = bone(f"pauldron_{side}", arm, (5 * sx, 22, 0))
    top = bone(f"pauldron_{side}_top", f"pauldron_{side}", (6 * sx, 23, 0), rotation=(0, 0, 12 * sx))
    cube(top, (-10.0 if sx < 0 else 4.0, 21.6, -3.6), (6.0, 2.4, 7.2), "pauldron")
    cube(p, (-10.2 if sx < 0 else 5.0, 19.9, -3.3), (5.2, 1.6, 6.6), "pauldron_lip")
    cube(p, (-10.0 if sx < 0 else 5.6, 18.5, -3.0), (4.4, 1.3, 6.0), "pauldron_lip")
    return 24.6


def pauldrons_angular(side, sx, arm):
    """B: a tall plate with a raised blade along its top and a spike at the outer edge."""
    p = bone(f"pauldron_{side}", arm, (5 * sx, 22, 0))
    cube(p, (-10.4 if sx < 0 else 3.8, 21.0, -3.7), (6.6, 3.2, 7.4), "pauldron")
    cube(p, (-9.9 if sx < 0 else 4.3, 19.2, -3.4), (5.6, 1.8, 6.8), "pauldron_lip")
    cube(p, (-9.0 if sx < 0 else 8.2, 24.2, -3.5), (0.8, 1.2, 7.0), "gold")
    spike = bone(f"pauldron_{side}_spike", f"pauldron_{side}", (9.6 * sx, 24, 0), rotation=(0, 0, -28 * sx))
    cube(spike, (9.6 * sx - 0.5, 23.6, -0.6), (1.0, 3.2, 1.2), "blade")
    return 24.8


def pauldrons_compact(side, sx, arm):
    """C: a compact rounded shell with a gold stripe, close to the arm."""
    p = bone(f"pauldron_{side}", arm, (5 * sx, 22, 0))
    cube(p, (-9.8 if sx < 0 else 4.2, 21.2, -3.3), (5.6, 2.8, 6.6), "pauldron_c")
    cube(p, (-9.4 if sx < 0 else 4.6, 24.0, -2.9), (4.8, 0.5, 5.8), "pauldron_c")
    cube(p, (-9.6 if sx < 0 else 4.4, 19.6, -3.1), (5.2, 1.6, 6.2), "pauldron_lip")
    return 24.5


VARIANT = sys.argv[3] if len(sys.argv) > 3 else "B"  # the user picked the void knight on 2026-11-01
HELMETS = {"A": helmet_horizon, "B": helmet_knight, "C": helmet_mask}
PAULDRONS = {"A": pauldrons_layered, "B": pauldrons_angular, "C": pauldrons_compact}

bone("armorHead", pivot=(0, 24, 0))
HELMETS[VARIANT]()

# body: breastplate, collar, side ribs, the core in a gold bezel between two pectoral plates, belt, cape
bone("armorBody", pivot=(0, 24, 0))
chest = bone("chest", "armorBody", (0, 24, 0))
cube(chest, (-4.5, 11.5, -2.75), (9, 13, 5.5), "chest")
cube(chest, (-4.75, 11.25, -3.0), (9.5, 1.5, 6), "trim")
cube(chest, (-3.2, 23.4, -3.35), (6.4, 1.2, 0.6), "steel_dark_gold")
cube(chest, (-3.6, 23.5, 2.4), (7.2, 2.2, 0.8), "steel_dark_gold")
for x in (-4.65, 3.55):
    cube(chest, (x, 23.5, -2.4), (1.1, 1.4, 4.8), "steel_dark_gold")
for x in (-4.85, 4.45):
    cube(chest, (x, 13.0, -2.0), (0.4, 8.0, 4.0), "steel_dark")
for side, sx in (("r", -1), ("l", 1)):
    pec = bone(f"pec_{side}", "chest", (3.7 * sx, 21, -3.0), rotation=(0, -10 * sx, 0))
    cube(pec, (3.7 * sx - 0.9, 17.6, -3.4), (1.8, 5.8, 0.65), "plate")
frame = bone("core_frame", "chest", (0, 19.5, -3.1))
cube(frame, (-1.9, 22.15, -3.3), (3.8, 0.55, 0.5), "bezel")
cube(frame, (-1.9, 16.3, -3.3), (3.8, 0.55, 0.5), "bezel")
for x in (-3.05, 2.5):
    cube(frame, (x, 17.6, -3.3), (0.55, 3.8, 0.5), "bezel")
for k in range(4):
    corner = bone(f"core_frame_{k}", "core_frame", (0, 19.5, -3.1), rotation=(0, 0, 45 + 90 * k))
    cube(corner, (-0.9, 19.5 + 2.55, -3.3), (1.8, 0.55, 0.5), "bezel")
# the back, Stark style: two thrusters on the shoulder blades, a glowing spine, and a cape split in two flaps
for side, sx in (("r", -1), ("l", 1)):
    pod = bone(f"thruster_{side}", "armorBody", (2.6 * sx, 21, 3.5))
    cube(pod, (2.6 * sx - 1.0, 19.0, 2.75), (2.0, 3.5, 1.5), "thruster")
    nozzle = bone(f"thruster_{side}_flame", f"thruster_{side}", (2.6 * sx, 19.0, 3.5))
    cube(nozzle, (2.6 * sx - 0.6, 18.3, 3.1), (1.2, 0.7, 0.8), "nozzle")
    flap = bone(f"cape_{side}", "armorBody", (2.6 * sx, 18.5, 3.0))
    cube(flap, (0.9 if side == "l" else -4.5, 3.5, 2.85), (3.6, 15.0, 0.5), "cape")
bone("spine", "armorBody", (0, 18, 3.0))
for k in range(6):
    yk = 12.4 + k * 1.9
    vert = bone(f"vertebra_{k}", "spine", (0, yk, 3.0))
    cube(vert, (-0.8, yk, 2.75), (1.6, 1.4, 0.55), "vertebra")
    light = bone(f"spine_light_{k}", f"vertebra_{k}", (0, yk + 0.7, 3.35))
    cube(light, (-0.35, yk + 0.35, 3.25), (0.7, 0.7, 0.15), "spine_light")

# the living core: halo that beats, a black hole that clenches, two counter-rotating rings, orbiting motes
CORE = (0, 19.5, -3.0)
bone("core", "armorBody", CORE)
halo = bone("core_glow", "core", CORE)
cube(halo, (-2.75, 16.75, -2.95), (5.5, 5.5, 0.2), "core_halo")
hole = bone("core_hole", "core", CORE)
cube(hole, (-1.25, 18.25, -3.35), (2.5, 2.5, 0.4), "core_hole")
bone("core_ring", "core", CORE)
for k in range(10):
    seg = bone(f"core_ring_{k}", "core_ring", CORE, rotation=(0, 0, 36 * k))
    cube(seg, (-0.55, 19.5 + 2.0, -3.3), (1.1, 0.45, 0.3), "ring_gold")
bone("core_ring_inner", "core", CORE)
for k in range(6):
    seg = bone(f"core_ring_inner_{k}", "core_ring_inner", CORE, rotation=(0, 0, 60 * k))
    cube(seg, (-0.4, 19.5 + 1.3, -3.45), (0.8, 0.35, 0.3), "ring_violet")
bone("core_motes", "core", CORE)
for k in range(3):
    m = bone(f"core_mote_{k}", "core_motes", CORE, rotation=(0, 0, 120 * k))
    cube(m, (-0.2, 19.5 + 3.3, -3.7), (0.4, 0.4, 0.4), "mote")
# cracks radiate from the core over the breastplate and breathe with the heartbeat
bone("cracks", "core", CORE)
for k, angle in enumerate((10, 50, 90, 130, 170, 210, 330)):
    a = math.radians(angle)
    limit = 5.6
    if abs(math.cos(a)) > 1e-6:
        limit = min(limit, 4.3 / abs(math.cos(a)))
    if math.sin(a) > 1e-6:
        limit = min(limit, (24.3 - 19.5) / math.sin(a))
    length = limit - 3.0
    ray = bone(f"crack_{k}", "cracks", CORE, rotation=(0, 0, angle - 90))
    cube(ray, (-0.15, 19.5 + 3.0, -2.9), (0.3, length, 0.12), "crack")

# arms: sleeves, gauntlets with a glowing emitter on the outer forearm, elbow guards, pauldrons
crystal_base = 24
for side, sx in (("r", -1), ("l", 1)):
    arm = "armorRightArm" if side == "r" else "armorLeftArm"
    bone(arm, pivot=(5 * sx, 22, 0))
    sleeve = bone(f"sleeve_{side}", arm, (5 * sx, 22, 0))
    x_sleeve = -8.5 if side == "r" else 3.5
    cube(sleeve, (x_sleeve, 11.5, -2.5), (5, 13, 5), "sleeve")
    cube(sleeve, (x_sleeve + 0.8, 16.8, 2.35), (3.4, 2.2, 0.6), "steel_dark_gold")
    gauntlet = bone(f"gauntlet_{side}", arm, (5 * sx, 14, 0))
    cube(gauntlet, (x_sleeve - 0.3, 11.2, -2.8), (5.6, 4.6, 5.6), "gauntlet")
    cube(gauntlet, (x_sleeve + 0.3, 10.95, -3.05), (4.4, 1.6, 0.6), "steel_dark")
    emitter = bone(f"emitter_{side}", f"gauntlet_{side}", (-8.95 if sx < 0 else 8.95, 13.7, 0))
    cube(emitter, (-9.15 if sx < 0 else 8.8, 12.4, -1.4), (0.35, 2.6, 2.8), "emitter")
    top = PAULDRONS[VARIANT](side, sx, arm)
    crystal_base = top

bone("crystals", "armorRightArm", (-7, crystal_base, 0))
for k, (pivot, rot, origin, size) in enumerate((
        ((-7.6, 0, 0.5), (0, 0, 22), (-8.1, 0, 0.0), (1.0, 3.6, 1.0)),
        ((-6.4, 0, -1.6), (-22, 0, -12), (-6.8, 0, -2.0), (0.8, 2.6, 0.8)),
        ((-8.8, 0, 2.0), (16, 0, 38), (-9.15, 0, 1.65), (0.7, 2.2, 0.7)),
        ((-9.2, 0, -1.8), (-12, 0, 30), (-9.5, 0, -2.1), (0.6, 1.6, 0.6)))):
    shard = bone(f"crystal_{k}", "crystals", (pivot[0], crystal_base, pivot[2]), rotation=rot)
    cube(shard, (origin[0], crystal_base - 0.2, origin[2]), size, "crystal")

# legs: plate thighs with a hip plate and an angled knee cop
for side, x0, px in (("r", -4.2, -1.9), ("l", -0.4, 1.9)):
    leg = "armorRightLeg" if side == "r" else "armorLeftLeg"
    bone(leg, pivot=(px, 12, 0))
    thigh = bone(f"thigh_{side}", leg, (px, 12, 0))
    cube(thigh, (x0, 3.5, -2.3), (4.6, 8.75, 4.6), "thigh")
    cube(thigh, (x0 + 0.4, 9.4, -2.65), (3.8, 2.4, 0.4), "steel_dark")
    knee = bone(f"knee_{side}", f"thigh_{side}", (px, 6, -2.6), rotation=(-8, 0, 0))
    cube(knee, (x0 + 0.5, 4.4, -3.2), (3.6, 3.0, 0.9), "knee")

# boots: toe caps, an ankle fin on the outside and a small heel thruster that burns in flight
for side, x0, px in (("r", -4.3, -1.9), ("l", -0.5, 1.9)):
    name = "armorRightBoot" if side == "r" else "armorLeftBoot"
    sx = -1 if side == "r" else 1
    bone(name, pivot=(px, 12, 0))
    boot = bone(f"boot_{side}", name, (px, 12, 0))
    cube(boot, (x0, -0.3, -2.6), (4.8, 4.0, 5.0), "boot")
    cube(boot, (x0 + 0.3, -0.35, -3.35), (4.2, 1.9, 0.8), "steel_dark_gold")
    x_out = x0 - 0.3 if sx < 0 else x0 + 4.8
    fin = bone(f"ankle_{side}", f"boot_{side}", (x_out, 2.5, 0), rotation=(0, 0, 10 * sx))
    cube(fin, (x_out - 0.2, 1.6, -1.4), (0.5, 2.8, 3.4), "blade")
    heel = bone(f"heel_{side}", f"boot_{side}", (px, 0.5, 2.6))
    cube(heel, (px - 1.0, -0.2, 2.3), (2.0, 1.6, 0.8), "thruster")
    flame = bone(f"heel_{side}_flame", f"heel_{side}", (px, -0.2, 2.7))
    cube(flame, (px - 0.5, -0.9, 2.35), (1.0, 0.7, 0.7), "nozzle")


# token cell sockets: a frame on the armor, a cell body bone and a glow bar bone scaled by charge
def socket(index, parent, frame_origin, frame_size, body_origin, body_size, glow_origin, glow_size):
    frame = bone(f"socket_{index}", parent, (0, 0, 0))
    cube(frame, frame_origin, frame_size, "socket_frame")
    cx = body_origin[0] + body_size[0] / 2
    cz = body_origin[2] + body_size[2] / 2
    body = bone(f"cell_{index}", parent, (cx, body_origin[1], cz))
    cube(body, body_origin, body_size, "cell_body")
    gx = glow_origin[0] + glow_size[0] / 2
    gz = glow_origin[2] + glow_size[2] / 2
    glow = bone(f"cell_{index}_glow", f"cell_{index}", (gx, glow_origin[1], gz))
    cube(glow, glow_origin, glow_size, "cell_glow")


socket(0, "armorHead", (-1.25, 26.0, 5.0), (2.5, 3.5, 0.5), (-0.75, 26.5, 5.5), (1.5, 2.5, 0.3),
       (-0.5, 26.75, 5.8), (1.0, 2.0, 0.15))
# the four chest cells sit in a row under the core like a battery belt feeding it
socket(9, "armorBody", (-4.0, 12.75, -3.05), (8, 3.6, 0.3), (0, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 0))
for k, xc in zip(range(1, 5), (-2.85, -0.95, 0.95, 2.85)):
    socket(k, "armorBody", (xc - 0.05, 13.0, -3.1), (0.1, 0.1, 0.1), (xc - 0.7, 13.0, -3.35), (1.4, 3.1, 0.3),
           (xc - 0.45, 13.25, -3.5), (0.9, 2.6, 0.15))
socket(5, "armorRightLeg", (-4.7, 5.0, -1.25), (0.5, 5, 2.5), (-5.0, 5.5, -0.75), (0.3, 4, 1.5),
       (-5.15, 5.75, -0.5), (0.15, 3.5, 1.0))
socket(6, "armorLeftLeg", (4.2, 5.0, -1.25), (0.5, 5, 2.5), (4.7, 5.5, -0.75), (0.3, 4, 1.5),
       (5.0, 5.75, -0.5), (0.15, 3.5, 1.0))
socket(7, "armorRightBoot", (-4.8, 0.5, -1.25), (0.5, 2.8, 2.5), (-5.1, 0.8, -0.75), (0.3, 2.2, 1.5),
       (-5.25, 0.95, -0.5), (0.15, 1.9, 1.0))
# socket 9 is only the shared frame of the chest belt; drop its empty cell bones and the per-cell frames
bones[:] = [b for b in bones if b["name"] not in ("cell_9", "cell_9_glow")]
for b in bones:
    if b["name"] in ("socket_1", "socket_2", "socket_3", "socket_4"):
        b["cubes"] = []

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
            tw, th = max(1, math.ceil(w * S)), max(1, math.ceil(h * S))
            requests.append((th, tw, b, c, f))

requests.sort(key=lambda r: (-r[0], -r[1]))
atlas_w = 256
x = y = shelf_h = 0
placed = []
for th, tw, b, c, f in requests:
    if x + tw > atlas_w:
        x = 0
        y += shelf_h
        shelf_h = 0
    placed.append((x, y, tw, th, b, c, f))
    x += tw
    shelf_h = max(shelf_h, th)
atlas_h = 1
while atlas_h < y + shelf_h:
    atlas_h *= 2
atlas_h = max(atlas_h, 64)

img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
px = img.load()


def put(x, y, color, glow=False):
    px[x, y] = color
    if glow:
        EMISSIVE.add((x, y))


# ---------------- painters ----------------
def paint_plate(x0, y0, w, h, base=STEEL, hi=STEEL_HI, lo=STEEL_LO, veins=0.0):
    """Brushed dark steel with a bevel; veins sprinkles short glowing amethyst tears."""
    for j in range(h):
        for i in range(w):
            n = rng.randint(-2, 2) + (1 if (i + j * 3) % 7 == 0 else 0)
            c = tuple(max(0, min(255, base[k] + n)) for k in range(3)) + (255,)
            if j == 0 or i == 0:
                c = hi
            elif j == h - 1 or i == w - 1:
                c = lo
            put(x0 + i, y0 + j, c)
    for _ in range(int(w * h * veins)):
        i, j = rng.randrange(1, max(2, w - 1)), rng.randrange(1, max(2, h - 1))
        dx, dy = rng.choice([(1, 1), (1, -1), (1, 0), (0, 1)])
        for t in range(rng.randint(2, 4)):
            ii, jj = i + dx * t, j + dy * t
            if 0 < ii < w - 1 and 0 < jj < h - 1:
                put(x0 + ii, y0 + jj, VIOLET if t else VIOLET_HI, glow=True)


def paint_gold(x0, y0, w, h, glow=False):
    for j in range(h):
        for i in range(w):
            c = GOLD_HI if j == 0 else GOLD_LO if j == h - 1 else GOLD
            put(x0 + i, y0 + j, c, glow=glow and j < h - 1)


def paint_rim(x0, y0, w, h, rows=(), border=False):
    """Glowing accent rim on chosen rows, or around the whole face."""
    for j in range(h):
        for i in range(w):
            if j in rows or (border and (i in (0, w - 1) or j in (0, h - 1))):
                put(x0 + i, y0 + j, RIM, glow=True)


def paint_visor(x0, y0, w, h):
    paint_plate(x0, y0, w, h)
    bar_top, bar_bot = int(h * 0.38), int(h * 0.5)
    for j in range(h):
        for i in range(w):
            in_bar = bar_top <= j <= bar_bot and int(w * 0.14) <= i < w - int(w * 0.14)
            in_stem = abs(i - (w - 1) / 2) <= w * 0.07 and bar_bot <= j <= int(h * 0.82)
            if in_bar or in_stem:
                edge = j == bar_top or (in_stem and j == int(h * 0.82))
                put(x0 + i, y0 + j, VISOR if edge else VIOLET, glow=True)


def paint_halo(x0, y0, w, h):
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = min(w, h) / 2
    for j in range(h):
        for i in range(w):
            d = math.hypot(i - cx, j - cy) / r
            if d > 1.0:
                px[x0 + i, y0 + j] = (0, 0, 0, 0)
                continue
            put(x0 + i, y0 + j, ramp([(0, VIOLET_HI), (0.45, VIOLET), (1.0, VIOLET_LO)], d), glow=True)


def paint_hole(x0, y0, w, h):
    for j in range(h):
        for i in range(w):
            edge = i in (0, w - 1) or j in (0, h - 1)
            put(x0 + i, y0 + j, VIOLET_LO if edge else BLACK, glow=edge)


def paint_crystal(x0, y0, w, h, face):
    for j in range(h):
        for i in range(w):
            t = 1 - j / max(1, h - 1)  # texture v=0 is the top of side faces
            c = ramp([(0, VIOLET_LO), (0.55, VIOLET), (1.0, VIOLET_HI)], t)
            if face in ("up", "down"):
                c = VIOLET_HI if face == "up" else VIOLET_LO
            put(x0 + i, y0 + j, c, glow=t > 0.35 or face == "up")


def paint_thruster(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h, STEEL_DARK, STEEL_HI, STEEL_LO)
    if face == "south":
        for j in range(1, h - 1, 2):
            for i in range(1, w - 1):
                put(x0 + i, y0 + j, GOLD_LO)
    if face in ("east", "west", "north", "south"):
        for i in range(w):
            put(x0 + i, y0 + h - 1, GOLD)


def paint_cape(x0, y0, w, h, face):
    for j in range(h):
        for i in range(w):
            c = CAPE if (i // 3) % 2 == 0 else CAPE_LO
            if face in ("north", "south") and (i in (0, w - 1) or j == h - 1):
                c = GOLD_LO if j < h - 1 else GOLD
            put(x0 + i, y0 + j, c)


def paint_band(x0, y0, w, h, rows):
    """A glowing violet slit over the given rows with a white-hot middle row and ends that fade to steel."""
    mid = rows[len(rows) // 2]
    for j in rows:
        for i in range(w):
            edge = min(i, w - 1 - i)
            if edge == 0:
                continue
            c = VISOR if j == mid and edge > 1 else VIOLET if edge > 1 else VIOLET_LO
            put(x0 + i, y0 + j, c, glow=True)


def paint_face_horizon(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h)
    if face != "north":
        return
    # one wide visor at eye level, faint panel lines above and gold vents below
    band = [r for r in range(h) if 5 <= r <= 7]
    paint_band(x0, y0, w, h, band)
    for i in range(2, w - 2):
        put(x0 + i, y0 + 3, STEEL_LO)
    for i in range(3, w - 3, 2):
        for j in range(11, 14):
            put(x0 + i, y0 + j, GOLD_LO)
    for j in range(h):
        put(x0 + w // 2, y0 + j, STEEL_LO if j < 4 or j > 8 else px[x0 + w // 2, y0 + j])


def paint_face_knight(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h, STEEL_DARK, STEEL_HI, STEEL_LO)
    if face != "north":
        return
    c = (w - 1) / 2
    for i in range(1, w - 1):
        d = abs(i - c)
        if d < 1.2:
            continue
        r = int(round(3 + (c - d) * 0.32))
        for j in (r, r + 1):
            if 0 < j < h - 1:
                put(x0 + i, y0 + j, VISOR if j == r and d > 2 else VIOLET, glow=True)
    for j in range(9, h - 1):
        for i in range(2, w - 2, 3):
            put(x0 + i, y0 + j, STEEL_LO)


def paint_mask_eyes(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h)
    if face != "north":
        return
    c = w // 2
    for side in (-1, 1):
        for k in range(1, 6):
            i = c + side * (1 + k) - (1 if side < 0 else 0)
            rows = (2, 3) if k < 5 else (2,)
            for j in rows:
                put(x0 + i, y0 + j, VISOR if j == 2 and 1 < k < 5 else VIOLET, glow=True)


def paint_mask_plate(x0, y0, w, h, face, top):
    paint_plate(x0, y0, w, h)
    if face != "north":
        return
    for i in range(w):
        put(x0 + i, y0 + (h - 1 if top else 0), GOLD)
    if not top:
        for j in range(1, h - 1):
            put(x0 + w // 2, y0 + j, STEEL_LO)


def paint_vents(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h, STEEL_DARK, STEEL_HI, STEEL_LO)
    if face == "north":
        for i in range(2, w - 2, 2):
            for j in range(1, h - 1):
                put(x0 + i, y0 + j, (14, 14, 18, 255))


def paint_edged(x0, y0, w, h, face, edge=GOLD, base=STEEL_DARK):
    """Dark steel with a gold edge along the bottom of every side face (and round the top face)."""
    paint_plate(x0, y0, w, h, base, STEEL_HI, STEEL_LO)
    if face in ("north", "south", "east", "west"):
        for i in range(w):
            put(x0 + i, y0 + h - 1, edge)
    elif face == "up":
        paint_rim(x0, y0, w, h, border=True)


def paint_blade(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h, STEEL_DARK, STEEL_HI, STEEL_LO)
    for j in range(h):
        put(x0, y0 + j, GOLD)
    if face in ("east", "west") and w > 3:
        for j in range(1, h - 1):
            put(x0 + w // 2, y0 + j, VIOLET_LO if j % 3 else VIOLET, glow=j % 3 == 0)


def paint_fin(x0, y0, w, h, face):
    paint_plate(x0, y0, w, h)
    for i in range(w):
        put(x0 + i, y0, GOLD_HI)
    if face in ("east", "west"):
        for i in range(2, w - 2):
            put(x0 + i, y0 + h // 2, VIOLET, glow=True)


def paint_emitter(x0, y0, w, h, face):
    if face in ("east", "west"):
        paint_halo(x0, y0, w, h)
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for j in range(h):
            for i in range(w):
                if px[x0 + i, y0 + j][3] == 0:
                    put(x0 + i, y0 + j, STEEL_DARK)
                elif abs(i - cx) < 0.8 and abs(j - cy) < 0.8:
                    put(x0 + i, y0 + j, VISOR, glow=True)
    else:
        paint_plate(x0, y0, w, h, STEEL_DARK, GOLD_LO, STEEL_LO)


def paint_bezel(x0, y0, w, h, face):
    for j in range(h):
        for i in range(w):
            c = GOLD_HI if j == 0 else GOLD_LO if j == h - 1 else GOLD
            put(x0 + i, y0 + j, c, glow=face == "north" and j == 0)


for x0, y0, tw, th, b, c, f in placed:
    mat = c["mat"]
    if mat == "helm":
        if f == "north":
            paint_visor(x0, y0, tw, th)
        else:
            paint_plate(x0, y0, tw, th, veins=0.01)
            if f == "up":
                paint_rim(x0, y0, tw, th, border=True)
            elif f in ("east", "west", "south"):
                paint_rim(x0, y0, tw, th, rows=(0,))
    elif mat == "chest":
        paint_plate(x0, y0, tw, th, veins=0.008)
        if f == "north":
            # a faint violet bloom on the steel around the core, so the core sits in light rather than on a sticker
            cx, cy = (0 - c["origin"][0]) * S, (c["origin"][1] + c["size"][1] - 19.5) * S
            for j in range(th):
                for i in range(tw):
                    d = math.hypot(i - cx, j - cy)
                    if 5 < d < 11:
                        base = px[x0 + i, y0 + j]
                        px[x0 + i, y0 + j] = lerp(base, VIOLET_LO, 0.45 * (1 - (d - 5) / 6))
    elif mat in ("sleeve", "thigh"):
        paint_plate(x0, y0, tw, th, veins=0.012)
        if mat == "thigh" and f == "north":
            for i in range(tw):
                put(x0 + i, y0 + th // 2, GOLD_LO)
    elif mat == "boot":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO)
        if f in ("north", "south", "east", "west"):
            for i in range(tw):
                put(x0 + i, y0 + 1, GOLD)
    elif mat == "cuff":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO)
        if f in ("north", "south", "east", "west"):
            for i in range(tw):
                put(x0 + i, y0, GOLD)
    elif mat == "pauldron":
        paint_plate(x0, y0, tw, th, veins=0.02)
        if f == "up":
            paint_rim(x0, y0, tw, th, border=True)
        elif f in ("north", "south", "east", "west"):
            paint_rim(x0, y0, tw, th, rows=(0, th - 1))
    elif mat == "pauldron_lip":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO)
        if f in ("north", "south", "east", "west"):
            for i in range(tw):
                put(x0 + i, y0 + th - 1, GOLD)
    elif mat == "steel_dark":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO)
    elif mat == "trim":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO)
        for i in range(tw):
            put(x0 + i, y0 + th // 2, GOLD)
    elif mat == "gold":
        paint_gold(x0, y0, tw, th)
    elif mat == "cape":
        paint_cape(x0, y0, tw, th, f)
    elif mat == "thruster":
        paint_thruster(x0, y0, tw, th, f)
    elif mat == "nozzle":
        for j in range(th):
            for i in range(tw):
                hot = f == "down" or j == th - 1
                put(x0 + i, y0 + j, VIOLET_HI if hot else VIOLET, glow=True)
    elif mat == "vertebra":
        paint_plate(x0, y0, tw, th, STEEL, GOLD_LO, STEEL_LO)
    elif mat == "spine_light":
        for j in range(th):
            for i in range(tw):
                put(x0 + i, y0 + j, VIOLET_HI if (i + j) % 3 == 0 else VIOLET, glow=True)
    elif mat == "core_halo":
        if f in ("north", "south"):
            paint_halo(x0, y0, tw, th)
        else:
            for j in range(th):
                for i in range(tw):
                    put(x0 + i, y0 + j, VIOLET_LO, glow=True)
    elif mat == "core_hole":
        paint_hole(x0, y0, tw, th)
    elif mat == "ring_gold":
        paint_gold(x0, y0, tw, th, glow=True)
    elif mat == "ring_violet":
        for j in range(th):
            for i in range(tw):
                put(x0 + i, y0 + j, VIOLET_HI if j == 0 else VIOLET, glow=True)
    elif mat == "mote":
        for j in range(th):
            for i in range(tw):
                put(x0 + i, y0 + j, VIOLET_HI, glow=True)
    elif mat == "crack":
        for j in range(th):
            for i in range(tw):
                t = j / max(1, th - 1)  # v=0 is the outer end of the ray
                put(x0 + i, y0 + j, ramp([(0, VIOLET_LO), (0.5, VIOLET), (1, VIOLET_HI)], t), glow=True)
    elif mat == "crystal":
        paint_crystal(x0, y0, tw, th, f)
    elif mat == "cogwit_eye":
        for j in range(th):
            for i in range(tw):
                pupil = f == "north" and 1 <= i <= tw - 2 and 1 <= j <= th - 2
                put(x0 + i, y0 + j, (20, 12, 10, 255) if pupil else COGWIT_ORANGE, glow=not pupil)
    elif mat == "cogwit_eye_closed":
        for j in range(th):
            for i in range(tw):
                put(x0 + i, y0 + j, COGWIT_ORANGE, glow=True)
    elif mat == "socket_frame":
        paint_plate(x0, y0, tw, th, (10, 10, 14, 255), GOLD_LO, STEEL_LO)
    elif mat == "cell_body":
        paint_plate(x0, y0, tw, th, (60, 34, 20, 255), (110, 64, 36, 255), (30, 16, 8, 255))
    elif mat == "cell_glow":
        for j in range(th):
            for i in range(tw):
                put(x0 + i, y0 + j, ramp([(0, (255, 243, 214)), (0.5, (255, 179, 71)), (1, (200, 85, 30))],
                                         j / max(1, th - 1)), glow=True)
    elif mat == "face_horizon":
        paint_face_horizon(x0, y0, tw, th, f)
    elif mat == "face_knight":
        paint_face_knight(x0, y0, tw, th, f)
    elif mat == "mask_eyes":
        paint_mask_eyes(x0, y0, tw, th, f)
    elif mat in ("mask_top", "mask_low"):
        paint_mask_plate(x0, y0, tw, th, f, mat == "mask_top")
    elif mat == "chin_vents":
        paint_vents(x0, y0, tw, th, f)
    elif mat == "helm_shell":
        paint_plate(x0, y0, tw, th, veins=0.01)
        if f == "up":
            paint_rim(x0, y0, tw, th, border=True)
    elif mat == "helm_cap":
        paint_plate(x0, y0, tw, th, veins=0.01)
        if f == "up":
            paint_rim(x0, y0, tw, th, border=True)
    elif mat == "visor_band":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO)
        if f in ("east", "west"):
            paint_band(x0, y0, tw, th, list(range(th)))
    elif mat == "ear":
        paint_edged(x0, y0, tw, th, f)
    elif mat == "steel_dark_gold":
        paint_edged(x0, y0, tw, th, f)
    elif mat == "blade":
        paint_blade(x0, y0, tw, th, f)
    elif mat == "fin":
        paint_fin(x0, y0, tw, th, f)
    elif mat == "plate":
        paint_plate(x0, y0, tw, th, veins=0.015)
        if f == "north":
            for j in range(th):
                put(x0 + tw - 1, y0 + j, GOLD_LO)
    elif mat == "bezel":
        paint_bezel(x0, y0, tw, th, f)
    elif mat == "gauntlet":
        paint_plate(x0, y0, tw, th, STEEL_DARK, STEEL_HI, STEEL_LO, veins=0.01)
        if f in ("north", "south", "east", "west"):
            for i in range(tw):
                put(x0 + i, y0, GOLD)
                put(x0 + i, y0 + 1, VIOLET, glow=True)
    elif mat == "emitter":
        paint_emitter(x0, y0, tw, th, f)
    elif mat == "knee":
        paint_plate(x0, y0, tw, th)
        if f == "north":
            paint_rim(x0, y0, tw, th, rows=(0,))
            for i in range(tw):
                put(x0 + i, y0 + th - 1, GOLD)
    elif mat == "pauldron_c":
        paint_plate(x0, y0, tw, th, veins=0.01)
        if f in ("north", "south", "east", "west", "up"):
            for i in range(tw):
                put(x0 + i, y0 + th // 2, GOLD)
            if f == "up":
                paint_rim(x0, y0, tw, th, border=True)
    else:
        raise SystemExit(f"no painter for material {mat}")
    c.setdefault("uv", {})[f] = {"uv": [x0, y0], "uv_size": [tw, th]}

# ---------------- write geometry ----------------
geo_bones = []
for b in bones:
    out = {"name": b["name"], "pivot": b["pivot"]}
    if "parent" in b:
        out["parent"] = b["parent"]
    if "rotation" in b:
        out["rotation"] = b["rotation"]
    if b["cubes"]:
        out["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
    geo_bones.append(out)

geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.horizon_armor", "texture_width": atlas_w, "texture_height": atlas_h,
                    "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
    "bones": geo_bones}]}

geo_path = RES / "assets/cogwit/geo/item/armor/horizon_armor.geo.json"
tex_path = RES / "assets/cogwit/textures/item/armor/horizon_armor.png"
glow_path = RES / "assets/cogwit/textures/item/armor/horizon_armor_glowmask.png"
anim_path = RES / "assets/cogwit/animations/item/armor/horizon_armor.animation.json"
for p in (geo_path, tex_path, anim_path):
    p.parent.mkdir(parents=True, exist_ok=True)
geo_path.write_text(json.dumps(geo, indent=1))
img.save(tex_path)

glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
gp = glow.load()
for (gx, gy) in EMISSIVE:
    gp[gx, gy] = px[gx, gy]
glow.save(glow_path)

# ---------------- animation ----------------
LENGTH = 8.0
STEP = 0.1


def sampled(fn):
    """Keyframes every STEP seconds over the whole loop, so curves stay smooth with linear interpolation."""
    n = int(round(LENGTH / STEP))
    return {f"{k * STEP:.2f}": fn(k * STEP) for k in range(n + 1)}


def heartbeat(t):
    """0..1 pulse every 2 seconds: a strong beat and a softer echo, like a heart."""
    p = t % 2.0
    return math.exp(-((p - 0.15) / 0.09) ** 2) + 0.55 * math.exp(-((p - 0.48) / 0.1) ** 2)


def spin(turns):
    return {"0.00": [0, 0, 0], f"{LENGTH:.2f}": [0, 0, 360 * turns]}


def beat_scale(amount):
    return sampled(lambda t: [round(1.0 + amount * heartbeat(t), 4)] * 3)


def wave(amplitude, cycles, offset=0.0):
    return lambda t: round(offset + amplitude * math.sin(t / LENGTH * cycles * 2 * math.pi), 4)


crystal_y, crystal_turn = wave(0.3, 2), wave(6, 1)
cape_r = wave(3, 2, offset=6)
cape_l = lambda t: round(6 + 3 * math.sin(t / LENGTH * 4 * math.pi + 1.3), 4)


def spine_wave(k):
    """Light runs up the spine from the waist to the neck right after each heartbeat."""
    return sampled(lambda t: [round(1.0 + 0.5 * math.exp(-(((t % 2.0) - 0.25 - k * 0.07) / 0.06) ** 2), 4)] * 3)


def flame():
    return sampled(lambda t: [round(1.0 + 0.12 * math.sin(t * 37) + 0.08 * math.sin(t * 23), 4),
                              round(1.0 + 0.35 * abs(math.sin(t * 11)), 4),
                              round(1.0 + 0.12 * math.sin(t * 37) + 0.08 * math.sin(t * 23), 4)])
idle_bones = {
        "core_ring": {"rotation": spin(-1)},
        "core_ring_inner": {"rotation": spin(2)},
        "core_motes": {"rotation": spin(-3)},
        "core_glow": {"scale": beat_scale(0.28)},
        "core_hole": {"scale": beat_scale(-0.12)},
        "cracks": {"scale": beat_scale(0.1)},
        "crystals": {"position": sampled(lambda t: [0, crystal_y(t), 0]),
                     "rotation": sampled(lambda t: [0, crystal_turn(t), 0])},
        "cape_r": {"rotation": sampled(lambda t: [cape_r(t), 0, 0])},
        "cape_l": {"rotation": sampled(lambda t: [cape_l(t), 0, 0])},
        "thruster_r_flame": {"scale": flame()},
        "thruster_l_flame": {"scale": flame()},
        **{f"spine_light_{k}": {"scale": spine_wave(k)} for k in range(6)},
}


def flutter(base, amplitude, cycles, phase=0.0):
    return sampled(lambda t: [round(base + amplitude * math.sin(t / LENGTH * cycles * 2 * math.pi + phase), 3), 0, 0])


def jets(length, flicker):
    return sampled(lambda t: [round(1.3 + 0.1 * math.sin(t * 41), 4),
                              round(length + flicker * math.sin(t * 29) + 0.5 * flicker * math.sin(t * 53), 4),
                              round(1.3 + 0.1 * math.sin(t * 41), 4)])


# flying: capes stream back and flutter, thrusters burn long
fly_bones = {**idle_bones,
             "cape_r": {"rotation": flutter(40, 6, 24)},
             "cape_l": {"rotation": flutter(40, 6, 24, 1.7)},
             "thruster_r_flame": {"scale": jets(3.4, 0.35)},
             "thruster_l_flame": {"scale": jets(3.4, 0.35)}}
# sprinting: capes lift a little, thrusters warm up
run_bones = {**idle_bones,
             "cape_r": {"rotation": flutter(20, 5, 16)},
             "cape_l": {"rotation": flutter(20, 5, 16, 1.3)},
             "thruster_r_flame": {"scale": jets(1.8, 0.2)},
             "thruster_l_flame": {"scale": jets(1.8, 0.2)}}


def keys(*frames):
    """frames: (time, value) pairs; values are [x, y, z] lists or a single number for uniform scale."""
    return {f"{t:.2f}": (v if isinstance(v, list) else [v, v, v]) for t, v in frames}


def pop(start, peak=1.3, length=0.35):
    """Scale from nothing to a little overshoot and back to normal, starting at {@code start} seconds."""
    return keys((0, 0), (start, 0), (start + length * 0.6, peak), (start + length, 1))


# suiting up: the helmet spins down, plates slam in, the core ignites, the cape unfurls, crystals grow,
# the thrusters fire once and the light runs up the spine
EQUIP_LENGTH = 1.8
equip_bones = {
    "helm": {"position": keys((0, [0, 12, 0]), (0.3, [0, -0.6, 0]), (0.42, [0, 0, 0])),
             "rotation": keys((0, [0, 200, 0]), (0.36, [0, -8, 0]), (0.46, [0, 0, 0]))},
    "pauldron_r": {"position": keys((0, [-5, 9, 0]), (0.15, [-5, 9, 0]), (0.45, [0, -0.6, 0]), (0.55, [0, 0, 0]))},
    "pauldron_l": {"position": keys((0, [5, 9, 0]), (0.25, [5, 9, 0]), (0.55, [0, -0.6, 0]), (0.65, [0, 0, 0]))},
    "sleeve_r": {"scale": keys((0, [1, 0.05, 1]), (0.1, [1, 0.05, 1]), (0.4, [1, 1.05, 1]), (0.5, [1, 1, 1]))},
    "sleeve_l": {"scale": keys((0, [1, 0.05, 1]), (0.2, [1, 0.05, 1]), (0.5, [1, 1.05, 1]), (0.6, [1, 1, 1]))},
    "chest": {"position": keys((0, [0, 0, 7]), (0.3, [0, 0, -0.4]), (0.4, [0, 0, 0])),
              "scale": keys((0, 0.7), (0.3, 1.04), (0.4, 1))},
    "thigh_r": {"position": keys((0, [-3, 0, 0]), (0.25, [0.3, 0, 0]), (0.35, [0, 0, 0]))},
    "thigh_l": {"position": keys((0, [3, 0, 0]), (0.25, [-0.3, 0, 0]), (0.35, [0, 0, 0]))},
    "boot_r": {"position": keys((0, [0, -4, 0]), (0.2, [0, 0.4, 0]), (0.3, [0, 0, 0]))},
    "boot_l": {"position": keys((0, [0, -4, 0]), (0.25, [0, 0.4, 0]), (0.35, [0, 0, 0]))},
    "core": {"scale": pop(0.55, 1.6, 0.45)},
    "core_glow": {"scale": pop(0.7, 2.4, 0.5)},
    "cracks": {"scale": pop(0.85, 1.3, 0.4)},
    "core_ring": {"rotation": keys((0, [0, 0, 0]), (EQUIP_LENGTH, [0, 0, -900]))},
    "core_ring_inner": {"rotation": keys((0, [0, 0, 0]), (EQUIP_LENGTH, [0, 0, 1260]))},
    "cape_r": {"scale": keys((0, [1, 0, 1]), (0.85, [1, 0, 1]), (1.2, [1, 1.06, 1]), (1.3, [1, 1, 1])),
               "rotation": keys((0, [40, 0, 0]), (0.85, [40, 0, 0]), (1.3, [4, 0, 0]), (1.5, [6, 0, 0]))},
    "cape_l": {"scale": keys((0, [1, 0, 1]), (0.9, [1, 0, 1]), (1.25, [1, 1.06, 1]), (1.35, [1, 1, 1])),
               "rotation": keys((0, [40, 0, 0]), (0.9, [40, 0, 0]), (1.35, [4, 0, 0]), (1.55, [6, 0, 0]))},
    "crystals": {"scale": pop(1.05, 1.35, 0.4)},
    # the faceplate stays up while the suit assembles, then slams shut and the visor lights
    "faceplate": {"rotation": keys((0, [-100, 0, 0]), (1.3, [-100, 0, 0]), (1.44, [4, 0, 0]), (1.5, [-3, 0, 0]),
                                   (1.58, [0, 0, 0]))},
    "thruster_r_flame": {"scale": keys((0, 0), (1.15, 0), (1.3, [1.8, 4.0, 1.8]), (1.6, [1, 1, 1]))},
    "thruster_l_flame": {"scale": keys((0, 0), (1.15, 0), (1.3, [1.8, 4.0, 1.8]), (1.6, [1, 1, 1]))},
    **{f"spine_light_{k}": {"scale": keys((0, 1), (0.95 + k * 0.06, 1), (1.02 + k * 0.06, 2.2), (1.12 + k * 0.06, 1))}
       for k in range(6)},
}

anim = {"format_version": "1.8.0", "animations": {
    "animation.horizon.idle": {"loop": True, "animation_length": LENGTH, "bones": idle_bones},
    "animation.horizon.fly": {"loop": True, "animation_length": LENGTH, "bones": fly_bones},
    "animation.horizon.run": {"loop": True, "animation_length": LENGTH, "bones": run_bones},
    "animation.horizon.equip": {"animation_length": EQUIP_LENGTH, "bones": equip_bones},
}}
anim_path.write_text(json.dumps(anim, indent=1))

scale_px = 3
prev = Image.new("RGBA", (atlas_w * scale_px * 2 + 20, atlas_h * scale_px), (90, 90, 96, 255))
big = img.resize((atlas_w * scale_px, atlas_h * scale_px), Image.NEAREST)
prev.paste(big, (0, 0), big)
g2 = glow.resize((atlas_w * scale_px, atlas_h * scale_px), Image.NEAREST)
prev.paste(g2, (atlas_w * scale_px + 20, 0), g2)
prev.save(PREVIEW)
print("atlas", atlas_w, atlas_h, "faces", len(placed), "emissive", len(EMISSIVE), "bones", len(bones))
