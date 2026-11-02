"""Renders a GeckoLib armor geo.json with its texture and glow mask from a few angles, for quick look-dev.

Run: uv run --no-project --with pillow python -I tools/render_geo.py <geo.json> <texture.png> <glowmask.png> <out.png>
     [--hide bone,bone] [--rot bone:x:y:z ...] [--title text]

Orthographic, painter's algorithm per texel, simple sun shading; glowing texels stay bright and bloom. A plain
player body is drawn underneath so gaps in the armor read as gaps. Front of the player faces -z, the player's
right side is -x, so the front view shows the right arm on the left of the picture.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

args = sys.argv[1:]
geo_path, tex_path, glow_path, out_path = (Path(a) for a in args[:4])
opts = args[4:]
hidden, extra_rot, title = set(), {}, ""
i = 0
while i < len(opts):
    if opts[i] == "--hide":
        hidden |= set(opts[i + 1].split(","))
        i += 2
    elif opts[i] == "--rot":
        name, x, y, z = opts[i + 1].split(":")
        extra_rot[name] = (float(x), float(y), float(z))
        i += 2
    elif opts[i] == "--title":
        title = opts[i + 1]
        i += 2
    else:
        raise SystemExit(f"unknown option {opts[i]}")

geo = json.loads(geo_path.read_text())["minecraft:geometry"][0]
tex = Image.open(tex_path).convert("RGBA")
glow = Image.open(glow_path).convert("RGBA")
tp, gp = tex.load(), glow.load()
bones = {b["name"]: b for b in geo["bones"]}


def rot_matrix(rx, ry, rz):
    """Bedrock order: z, then y, then x, applied to the point (so x is outermost)."""
    ax, ay, az = (math.radians(v) for v in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    mx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    my = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    mz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))

    def mul(a, b):
        return tuple(tuple(sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)) for r in range(3))

    return mul(mx, mul(my, mz))


def apply(m, p):
    return tuple(m[r][0] * p[0] + m[r][1] * p[1] + m[r][2] * p[2] for r in range(3))


def chain(name):
    out = []
    while name:
        out.append(bones[name])
        name = bones[name].get("parent")
    return out


def is_hidden(name):
    return any(b["name"] in hidden for b in chain(name))


def to_model(name, p):
    """A point given in model space on a cube of bone `name`, moved by that bone's and its parents' rotations."""
    for b in chain(name):
        r = b.get("rotation", [0, 0, 0])
        e = extra_rot.get(b["name"], (0, 0, 0))
        rx, ry, rz = r[0] + e[0], r[1] + e[1], r[2] + e[2]
        if rx or ry or rz:
            pv = b["pivot"]
            # bedrock x is mirrored relative to the math frame: flip x for the rotation
            q = (-(p[0] - pv[0]), p[1] - pv[1], p[2] - pv[2])
            q = apply(rot_matrix(rx, ry, rz), q)
            p = (-q[0] + pv[0], q[1] + pv[1], q[2] + pv[2])
    return p


# faces: corners in (u, v) order -> 3D, outward normal; u runs left to right as seen from outside, v top to bottom
def face_corners(o, s, face):
    x0, y0, z0 = o
    x1, y1, z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
    # each entry: top-left, top-right, bottom-left (seen from outside), normal
    return {
        "north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0), (0, 0, -1)),
        "south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1), (0, 0, 1)),
        "east": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0), (-1, 0, 0)),
        "west": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), (1, 0, 0)),
        "up": ((x1, y1, z1), (x0, y1, z1), (x1, y1, z0), (0, 1, 0)),
        "down": ((x1, y0, z0), (x0, y0, z0), (x1, y0, z1), (0, -1, 0)),
    }[face]


PLAYER = [  # name, origin, size, colour: a plain mannequin under the armor
    ("armorHead", (-4, 24, -4), (8, 8, 8), (196, 160, 132)),
    ("armorBody", (-4, 12, -2), (8, 12, 4), (70, 92, 120)),
    ("armorRightArm", (-8, 12, -2), (4, 12, 4), (70, 92, 120)),
    ("armorLeftArm", (4, 12, -2), (4, 12, 4), (70, 92, 120)),
    ("armorRightLeg", (-4, 0, -2), (4, 12, 4), (52, 58, 96)),
    ("armorLeftLeg", (0, 0, -2), (4, 12, 4), (52, 58, 96)),
]


def collect():
    """Every visible texel of the model as (corners in model space, colour, glow, normal)."""
    quads = []
    for name, o, s, colour in PLAYER:
        for face in ("north", "south", "east", "west", "up", "down"):
            tl, tr, bl, n = face_corners(o, s, face)
            steps = 8
            for j in range(steps):
                for k in range(steps):
                    def at(a, bb):
                        return tuple(tl[d] + (tr[d] - tl[d]) * a + (bl[d] - tl[d]) * bb for d in range(3))
                    quads.append(((at(k / steps, j / steps), at((k + 1) / steps, j / steps),
                                   at((k + 1) / steps, (j + 1) / steps), at(k / steps, (j + 1) / steps)),
                                  colour + (255,), False, n))
    for name, b in bones.items():
        if is_hidden(name):
            continue
        for c in b.get("cubes", []):
            for face, uv in c["uv"].items():
                tl, tr, bl, n = face_corners(c["origin"], c["size"], face)
                (u0, v0), (uw, vh) = uv["uv"], uv["uv_size"]
                for j in range(vh):
                    for k in range(uw):
                        col = tp[u0 + k, v0 + j]
                        if col[3] < 8:
                            continue
                        g = gp[u0 + k, v0 + j][3] > 8

                        def at(a, bb):
                            return tuple(tl[d] + (tr[d] - tl[d]) * a + (bl[d] - tl[d]) * bb for d in range(3))

                        corners = (at(k / uw, j / vh), at((k + 1) / uw, j / vh), at((k + 1) / uw, (j + 1) / vh),
                                   at(k / uw, (j + 1) / vh))
                        corners = tuple(to_model(name, p) for p in corners)
                        quads.append((corners, col, g, None))
    return quads


QUADS = collect()
SUN = (0.45, 0.75, -0.5)
SL = math.sqrt(sum(v * v for v in SUN))
SUN = tuple(v / SL for v in SUN)


def normal_of(c):
    a = [c[1][d] - c[0][d] for d in range(3)]
    b = [c[3][d] - c[0][d] for d in range(3)]
    n = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    ln = math.sqrt(sum(v * v for v in n)) or 1
    return tuple(-v / ln for v in n)


def render(yaw, pitch, scale, size):
    ss = 3
    w, h = size[0] * ss, size[1] * ss
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bloom = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    db, dg = ImageDraw.Draw(base), ImageDraw.Draw(bloom)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        # bedrock x already runs to the player's left, which is the picture's right from the front
        x, y, z = p[0], p[1] - 16, p[2]
        x, z = x * cy_ - z * sy_, x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    items = []
    for corners, col, g, n in QUADS:
        vc = [view(p) for p in corners]
        nn = normal_of(corners)
        vn = view((nn[0], nn[1] + 16, nn[2]))
        if vn[2] > 0.02:  # faces away from the camera (which looks along +z)
            continue
        depth = sum(p[2] for p in vc) / 4
        light = max(0.0, -(nn[0] * -SUN[0] + nn[1] * SUN[1] + nn[2] * SUN[2]) * -1)
        items.append((depth, vc, col, g, 0.55 + 0.45 * light))
    items.sort(key=lambda t: -t[0])
    for depth, vc, col, g, shade in items:
        pts = [(w / 2 + p[0] * scale * ss, h * 0.56 - p[1] * scale * ss) for p in vc]
        if g:
            c = col[:3]
            db.polygon(pts, fill=c + (255,))
            dg.polygon(pts, fill=c + (255,))
        else:
            c = tuple(int(v * shade) for v in col[:3])
            db.polygon(pts, fill=c + (255,))
            dg.polygon(pts, fill=(0, 0, 0, 255))
    base = base.resize(size, Image.LANCZOS)
    bloom = bloom.resize(size, Image.LANCZOS).filter(ImageFilter.GaussianBlur(5))
    return base, bloom


def backdrop(size):
    img = Image.new("RGB", size)
    d = ImageDraw.Draw(img)
    for y in range(size[1]):
        t = y / size[1]
        d.line([(0, y), (size[0], y)], fill=(int(34 + 30 * t), int(36 + 34 * t), int(44 + 30 * t)))
    return img


views = [("спереди", 0, -8), ("3/4", 35, -10), ("сбоку", 90, -6), ("сзади", 180, -10)]
cell = (300, 440)
sheet = Image.new("RGB", (cell[0] * len(views), cell[1] + (34 if title else 0)), (20, 20, 24))
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 18)
small = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 13)
top = 34 if title else 0
for k, (name, yaw, pitch) in enumerate(views):
    bg = backdrop(cell)
    base, bloom = render(yaw, pitch, 11.0, cell)
    bg.paste(base, (0, 0), base)
    glow_layer = Image.new("RGB", cell, (0, 0, 0))
    glow_layer.paste(bloom, (0, 0), bloom)
    from PIL import ImageChops
    bg = ImageChops.add(bg, glow_layer.point(lambda v: int(v * 0.8)))
    ImageDraw.Draw(bg).text((10, cell[1] - 22), name, fill=(150, 150, 160), font=small)
    sheet.paste(bg, (k * cell[0], top))
if title:
    ImageDraw.Draw(sheet).text((12, 7), title, fill=(230, 230, 236), font=font)
sheet.save(out_path)
print("saved", out_path, len(QUADS), "texels")
