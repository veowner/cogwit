"""Builds the two Event Horizon effect models for GeckoLib: the gravity well and the collapse.

  gravity_well  a black hole hanging in the air: pitch-black core, hugging photon rings, a tilted accretion disk with an
                inner hot disk, and faint lensing halo rings. Pivot of root = the centre of the effect.
  collapse      a dark violet gravitational shell that implodes onto a white-hot core, and a ground shockwave ring.
                Pivot of root = the player's feet (the shell and the core sit one block above).

Geometry notes (Minecraft model pixels, 16 = one block, Y up, front faces -Z):
  * Rings are built from segment bones (rotated about the ring axis) so they animate as one bone. The broad faces of a
    segment are clipped to the true annular sector (transparent texels outside), so the rim is round although the cubes
    are boxes. Those faces need an alpha-discarding render type (cutout / translucent, no cull).
  * Painting is a function of the 3D point of each texel, so overlapping geometry and neighbouring segments agree.
  * Spiral arm counts are chosen so a segment's pattern stays continuous with its neighbours.

Usage: build_effects_geo.py <resources_root> <preview_png>
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

RES = Path(sys.argv[1])
PREVIEW = sys.argv[2]
ATLAS_W = 256
FACES = ("north", "south", "east", "west", "up", "down")

# ---------------------------------------------------------------- palette
BLACK = (0, 0, 0, 255)
WHITE_HOT = (255, 243, 214)
AMBER = (255, 179, 71)
EMBER = (200, 85, 30)
VIOLET_HI = (199, 125, 255)
VIOLET = (155, 77, 219)
VIOLET_LO = (94, 42, 140)
GOLD = (222, 168, 72)
GOLD_HI = (246, 206, 120)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def lerp(a, b, t):
    t = clamp(t)
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(stops, t):
    t = clamp(t)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            return lerp(c0, c1, (t - p0) / max(1e-9, p1 - p0))
    return tuple(stops[-1][1])


def rgba(c, a=255):
    return tuple(int(round(max(0, min(255, v)))) for v in c[:3]) + (a,)


def scaled(c, k):
    return tuple(v * k for v in c[:3])


def hash2(ix, iy, seed=0):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (seed * 83492791)
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def hash3(ix, iy, iz, seed=0):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, z, seed=0):
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out += w * hash3(ix + dx, iy + dy, iz + dz, seed)
    return out


BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


# ---------------------------------------------------------------- texture specs
class Solid:
    """One flat colour; identical solids share a tiny 2x2 tile of the atlas."""

    def __init__(self, color, glow=False):
        self.color, self.glow = tuple(color), glow


class Paint:
    """A per-texel painter: fn(point_in_bone_space, face, cube) -> (rgba, glow) or None for transparent."""

    def __init__(self, fn, density):
        self.fn, self.density = fn, density


TRANSPARENT = Solid((0, 0, 0, 0))


def face_dims(size, face):
    x, y, z = size
    return {"north": (x, y), "south": (x, y), "east": (z, y), "west": (z, y), "up": (x, z), "down": (x, z)}[face]


def face_point(cube, face, u, v):
    """Bone-space point of a face at texture coordinate (u, v) in 0..1, matching GeckoLib's box UV layout."""
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    hx, hy, hz = ox + sx, oy + sy, oz + sz
    if face == "north":
        return (hx - u * sx, hy - v * sy, oz)
    if face == "south":
        return (ox + u * sx, hy - v * sy, hz)
    if face == "east":
        return (hx, hy - v * sy, hz - u * sz)
    if face == "west":
        return (ox, hy - v * sy, oz + u * sz)
    if face == "up":
        return (ox + u * sx, hy, oz + v * sz)
    return (ox + u * sx, oy, hz - v * sz)


FACE_NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
               "up": (0, 1, 0), "down": (0, -1, 0)}


class Model:
    def __init__(self, identifier, bounds):
        self.identifier, self.bounds = identifier, bounds
        self.bones, self.by_name = [], {}
        self.img = self.glow = None
        self.emissive = set()
        self.atlas_h = 0

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        assert name not in self.by_name, f"duplicate bone {name}"
        b = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            assert parent in self.by_name, f"{name}: unknown parent {parent}"
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        self.by_name[name] = b
        return b

    def cube(self, b, origin, size, faces):
        if not isinstance(faces, dict):
            faces = {f: faces for f in FACES}
        assert set(faces) == set(FACES), "cube needs all six faces"
        c = {"origin": [round(v, 4) for v in origin], "size": [round(v, 4) for v in size], "faces": faces, "uv": {}}
        b["cubes"].append(c)
        return c

    # -------- atlas packing and painting
    def build(self):
        solids, requests = {}, []
        for b in self.bones:
            for c in b["cubes"]:
                for f, spec in c["faces"].items():
                    if isinstance(spec, Solid):
                        solids.setdefault((spec.color, spec.glow), None)
                    else:
                        w, h = face_dims(c["size"], f)
                        tw = max(1, math.ceil(w * spec.density - 1e-6))
                        th = max(1, math.ceil(h * spec.density - 1e-6))
                        requests.append((th, tw, c, f, spec))
        reqs = [(2, 2, key) for key in solids] + requests
        reqs.sort(key=lambda r: (-r[0], -r[1]))
        x = y = shelf_h = 0
        placed = []
        for r in reqs:
            th, tw = r[0], r[1]
            if x + tw > ATLAS_W:
                x, y, shelf_h = 0, y + shelf_h, 0
            placed.append((x, y, tw, th, r))
            x += tw
            shelf_h = max(shelf_h, th)
        h = 16
        while h < y + shelf_h:
            h *= 2
        self.atlas_h = h
        self.img = Image.new("RGBA", (ATLAS_W, h), (0, 0, 0, 0))
        px = self.img.load()
        for x0, y0, tw, th, r in placed:
            if len(r) == 3:  # a solid tile
                color, glow = r[2]
                solids[r[2]] = (x0, y0)
                for j in range(th):
                    for i in range(tw):
                        px[x0 + i, y0 + j] = color
                        if glow and color[3]:
                            self.emissive.add((x0 + i, y0 + j))
                continue
            _, _, c, f, spec = r
            c["uv"][f] = {"uv": [x0, y0], "uv_size": [tw, th]}
            for j in range(th):
                for i in range(tw):
                    p = face_point(c, f, (i + 0.5) / tw, (j + 0.5) / th)
                    res = spec.fn(p, f, c)
                    if res is None:
                        continue
                    color, glow = res
                    px[x0 + i, y0 + j] = color
                    if glow:
                        self.emissive.add((x0 + i, y0 + j))
        for b in self.bones:
            for c in b["cubes"]:
                for f, spec in c["faces"].items():
                    if isinstance(spec, Solid):
                        sx, sy = solids[(spec.color, spec.glow)]
                        c["uv"][f] = {"uv": [sx, sy], "uv_size": [2, 2]}
        self.glow = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        gp = self.glow.load()
        for (gx, gy) in self.emissive:
            gp[gx, gy] = px[gx, gy]

    def geo(self):
        out = []
        for b in self.bones:
            o = {"name": b["name"], "pivot": b["pivot"]}
            if "parent" in b:
                o["parent"] = b["parent"]
            if "rotation" in b:
                o["rotation"] = b["rotation"]
            if b["cubes"]:
                o["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
            out.append(o)
        bw, bh, bo = self.bounds
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": self.identifier, "texture_width": ATLAS_W, "texture_height": self.atlas_h,
                            "visible_bounds_width": bw, "visible_bounds_height": bh, "visible_bounds_offset": bo},
            "bones": out}]}


# ---------------------------------------------------------------- shared geometry builders
def ring_segments(m, parent, prefix, plane, n, r0, r1, t0, t1, faces_fn, fill=1.0, overlap_deg=0.0, pivot=(0, 0, 0)):
    """n segment bones around the Y axis ('xz', a flat ring) or the Z axis ('xy', a ring facing the viewer).

    Each segment is a box whose radial axis is local +X; (t0, t1) is its thickness range along Y (xz) or Z (xy).
    faces_fn(k, angle_degrees) -> faces dict for the cube."""
    half = math.pi / n
    for k in range(n):
        ang = 360.0 * k / n
        rot = (0, ang, 0) if plane == "xz" else (0, 0, ang)
        b = m.bone(f"{prefix}_{k:02d}", parent, pivot, rot)
        length = 2 * r1 * math.tan(half * fill + math.radians(overlap_deg)) + (0.08 if overlap_deg else 0.0)
        if plane == "xz":
            origin, size = (r0, t0, -length / 2), (r1 - r0, t1 - t0, length)
        else:
            origin, size = (r0, -length / 2, t0), (r1 - r0, length, t1 - t0)
        m.cube(b, origin, size, faces_fn(k, ang))


def sphere_slabs(m, bone, centre, radius, slabs, faces_fn, eps=0.0):
    """A rounded blob of stacked slabs; each slab is four axis-aligned boxes whose corners lie on the circle of that
    height, giving a 16-sided cross-section with no rotated bones. faces_fn(slab_index, box_index, y0, y1) -> faces."""
    cx, cy, cz = centre
    h = 2 * radius / slabs
    a1, a2 = math.radians(11.25), math.radians(33.75)
    for s in range(slabs):
        y0, y1 = -radius + s * h, -radius + (s + 1) * h
        ymin = 0.0 if y0 < 0 < y1 or y0 * y1 <= 0 else min(abs(y0), abs(y1))
        rho = math.sqrt(max(radius * radius - ymin * ymin, 0.0))
        boxes = ((2 * rho * math.cos(a1), 2 * rho * math.sin(a1)), (2 * rho * math.sin(a1), 2 * rho * math.cos(a1)),
                 (2 * rho * math.cos(a2), 2 * rho * math.sin(a2)), (2 * rho * math.sin(a2), 2 * rho * math.cos(a2)))
        for k, (bx, bz) in enumerate(boxes):
            lo, hi = cy + y0 - eps * k, cy + y1 + eps * k
            m.cube(bone, (cx - bx / 2, lo, cz - bz / 2), (bx, hi - lo, bz), faces_fn(s, k, y0, y1))


# ================================================================ 1. GRAVITY WELL
W = Model("geometry.gravity_well", (8, 8, [0, 0, 0]))
W.bone("root", None, (0, 0, 0))
W.bone("body", "root", (0, 0, 0))

# ---- core: pitch black, not emissive
CORE_R = 7.8
core = W.bone("core", "body", (0, 0, 0))
sphere_slabs(W, core, (0, 0, 0), CORE_R, 10, lambda s, k, y0, y1: Solid(BLACK, False))

# ---- photon rings hugging the core: one facing the viewer (xy) and one flat (xz), white-hot to orange
PH_R0, PH_R1 = 8.1, 9.5
PH_STOPS = [(0.0, (255, 255, 250)), (0.30, WHITE_HOT), (0.62, AMBER), (1.0, (232, 112, 42))]
W.bone("photon_ring", "body", (0, 0, 0))


def photon_faces(plane, beam_deg, n):
    broad = ("north", "south") if plane == "xy" else ("up", "down")

    def faces(k, ang):
        d = 0.5 + 0.5 * math.cos(math.radians(ang - beam_deg))  # doppler: one side of the ring burns hotter

        def paint(p, face, cube):
            rho = math.hypot(p[0], p[1] if plane == "xy" else p[2])
            t = (rho - PH_R0) / (PH_R1 - PH_R0)
            return rgba(ramp(PH_STOPS, t * 0.75 + (1 - d) * 0.5)), True

        out = {f: Paint(paint, 5) for f in broad}
        for f in FACES:
            if f in broad:
                continue
            hot = 0.0 if f == "west" else 0.55 if f == "east" else 0.3
            out[f] = Solid(rgba(ramp(PH_STOPS, hot + (1 - d) * 0.4)), True)
        return out
    return faces


ring_segments(W, "photon_ring", "photon_xy", "xy", 28, PH_R0, PH_R1, -0.5, 0.5, photon_faces("xy", 35, 28), overlap_deg=0.9)
ring_segments(W, "photon_ring", "photon_xz", "xz", 28, PH_R0, PH_R1, -0.5, 0.5, photon_faces("xz", 120, 28), overlap_deg=0.9)

# ---- accretion disk
DISK_R0, DISK_R1 = 12.0, 38.0
DISK_STOPS = [(0.0, (255, 252, 240)), (0.08, WHITE_HOT), (0.24, (255, 214, 120)), (0.38, AMBER), (0.58, EMBER),
              (0.74, (178, 62, 88)), (0.88, VIOLET), (1.0, VIOLET_LO)]
INNER_STOPS = [(0.0, (255, 255, 255)), (0.35, (255, 247, 224)), (0.7, (255, 216, 134)), (1.0, AMBER)]
W.bone("disk_tilt", "body", (0, 0, 0), rotation=(20, 0, 10))
W.bone("disk", "disk_tilt", (0, 0, 0))
W.bone("disk_inner", "disk", (0, 0, 0))


def disk_faces(n, r0, r1, arms, stops, fade_from, grooves, wind, dither_cell, seed):
    half = math.pi / n
    tol = 0.7  # px of tangential overlap so no hairline cracks open between segments

    def faces(k, ang):
        def make(shade):
            def fn(p, face, cube):
                x, z = p[0], p[2]
                rho = math.hypot(x, z)
                a = math.atan2(z, x)
                if rho < r0 or rho > r1 or abs(a) > half + tol / max(rho, 1.0):
                    return None
                t = (rho - r0) / (r1 - r0)
                wa = a - math.radians(ang)  # angle in the disk plane, shared by all segments
                if t > fade_from:
                    keep = 1.0 - (t - fade_from) / (1.0 - fade_from)
                    bx = int(math.floor(rho * math.cos(wa) / dither_cell)) % 4
                    bz = int(math.floor(rho * math.sin(wa) / dither_cell)) % 4
                    if (BAYER[bz][bx] + 0.5) / 16.0 >= keep:
                        return None
                spiral = math.sin(arms * wa - wind * math.log(rho))
                mod = 0.88 + 0.16 * spiral
                for gr, gw in grooves:
                    if abs(rho - gr) < gw:
                        mod *= 0.5
                cell = (int(math.floor(rho * math.cos(wa) * 2.5)), int(math.floor(rho * math.sin(wa) * 2.5)))
                mod *= 0.95 + 0.1 * hash2(cell[0], cell[1], seed)
                col = list(ramp(stops, t))
                if hash2(cell[0], cell[1], seed + 5) < 0.006:
                    col = [255, 250, 235]
                    mod = 1.0
                return rgba(scaled(col, mod * shade)), True
            return fn
        out = {f: TRANSPARENT for f in FACES}
        out["up"] = Paint(make(1.0), 3)
        out["down"] = Paint(make(0.8), 1.5)
        return out
    return faces


ring_segments(W, "disk", "disk_seg", "xz", 24, DISK_R0, DISK_R1, -0.3, 0.3,
              disk_faces(24, DISK_R0, DISK_R1, 12, DISK_STOPS, 0.9, ((17.5, 0.28), (22.6, 0.3), (28.4, 0.3), (33.2, 0.28)),
                         5.5, 0.45, 11), overlap_deg=0.8)
ring_segments(W, "disk_inner", "inner_seg", "xz", 16, 9.9, 14.2, 0.1, 0.7,
              disk_faces(16, 9.9, 14.2, 8, INNER_STOPS, 0.45, (), 3.0, 0.4, 23), overlap_deg=1.2)

# ---- halo: faint lensing rings, dashed, with four gold studs
HALO_R0, HALO_R1 = 43.7, 44.3
W.bone("halo", "body", (0, 0, 0))
HALO_DIM = rgba((58, 26, 92), 150)
HALO_MID = rgba((84, 38, 130), 185)
HALO_HI = rgba((124, 62, 186), 215)


def halo_faces(k, ang):
    c = HALO_HI if k % 6 == 0 else HALO_MID if k % 2 == 0 else HALO_DIM
    return Solid(c, True)


ring_segments(W, "halo", "halo_xy", "xy", 40, HALO_R0, HALO_R1, -0.3, 0.3, halo_faces, fill=0.68)
ring_segments(W, "halo", "halo_xz", "xz", 40, HALO_R0, HALO_R1, -0.3, 0.3, halo_faces, fill=0.68)
studs = W.by_name["halo"]
for sx, sy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    cx, cy = sx * 44.0, sy * 44.0
    W.cube(studs, (cx - 0.9, cy - 0.9, -0.9), (1.8, 1.8, 1.8), Solid(rgba(GOLD), True))
    W.cube(studs, (cx - 0.45, cy - 0.45, -1.15), (0.9, 0.9, 2.3), Solid(rgba(GOLD_HI), True))

W.build()

# ================================================================ 2. COLLAPSE
C = Model("geometry.collapse", (28, 4, [0, 1.5, 0]))
C.bone("root", None, (0, 0, 0))
C.bone("body", "root", (0, 0, 0))
SPH_C, SPH_R, SLABS = 16.0, 24.0, 16
sphere = C.bone("sphere", "body", (0, SPH_C, 0))
SEAM_COLORS = (rgba(VIOLET_LO), rgba(VIOLET), rgba(VIOLET_HI))
DEAD = Solid((15, 6, 28, 255), False)


def sphere_paint(face, c):
    def fn(p, f, cube):
        qx, qy, qz = p[0], p[1] - SPH_C, p[2]
        rho = math.hypot(qx, qz)
        lon = math.degrees(math.atan2(qz, qx))
        # dark violet body: low-frequency cloud plus fine speckle, lighter towards the top
        n = vnoise(qx * 0.17, qy * 0.17, qz * 0.17, 3) * 0.65 + vnoise(qx * 0.5, qy * 0.5, qz * 0.5, 9) * 0.35
        body = lerp((16, 7, 34), (62, 26, 108), 0.12 + 0.88 * n)
        body = scaled(body, (0.78 + 0.22 * (qy / SPH_R * 0.5 + 0.5)) * (0.94 + 0.12 * hash3(int(qx * 2), int(qy * 2), int(qz * 2), 4)))
        # glowing seams: latitude lines every 6 px, meridians every 30 degrees; flat tops only get their outline
        lat_d = abs(qy / 6.0 - round(qy / 6.0)) * 6.0
        lat = lat_d <= 0.3
        if lat and f in ("up", "down"):
            ox, oy, oz = cube["origin"]
            sx, sy, sz = cube["size"]
            edge = min(p[0] - ox, ox + sx - p[0], p[2] - oz, oz + sz - p[2])
            lat = edge <= 0.62
        lon_d = rho * math.radians(abs((lon + 15.0) % 30.0 - 15.0))
        mer = rho > 6.0 and lon_d <= 0.4
        if lat or mer:
            r = hash3(int(math.floor(qx / 1.6)), int(math.floor(qy / 1.6)), int(math.floor(qz / 1.6)), 8)
            if r < 0.2 and not (lat and mer):
                return rgba(body), False  # a dropped dash: the seam flickers like a crack
            if lat and mer:
                return SEAM_COLORS[2], True
            tier = 0 if r < 0.45 else 1 if r < 0.8 else 2
            return SEAM_COLORS[tier], True
        return rgba(body), False
    return fn


def sphere_faces(s, k, y0, y1):
    upper = (y0 + y1) / 2 > 0
    out = {}
    for f in FACES:
        if f in ("up", "down"):
            buried = (f == "down") if upper else (f == "up")
            out[f] = DEAD if buried else Paint(sphere_paint(f, None), 1)
        else:
            out[f] = Paint(sphere_paint(f, None), 2)
    return out


sphere_slabs(C, sphere, (0, SPH_C, 0), SPH_R, SLABS, sphere_faces, eps=0.02)

core_c = C.bone("core", "body", (0, SPH_C, 0))
CORE_STOPS = [(0.0, (255, 255, 252)), (0.55, (255, 246, 222)), (1.0, (255, 214, 140))]


def core_paint(p, face, cube):
    # white-hot in the middle of each face, amber at the edges
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    d = max(abs(p[0] - (ox + sx / 2)) / (sx / 2), abs(p[1] - (oy + sy / 2)) / (sy / 2), abs(p[2] - (oz + sz / 2)) / (sz / 2))
    return rgba(ramp(CORE_STOPS, (d - 0.55) / 0.45)), True


for size in ((6, 4, 4), (4, 6, 4), (4, 4, 6)):
    C.cube(core_c, (-size[0] / 2, SPH_C - size[1] / 2, -size[2] / 2), size, {f: Paint(core_paint, 3) for f in FACES})

C.bone("shockwave", "body", (0, 0.5, 0))
SW_R0, SW_R1, SW_N = 14.5, 17.5, 32
SW_STOPS = [(0.0, VIOLET_LO), (0.3, VIOLET), (0.62, VIOLET_HI), (0.86, (236, 214, 255)), (1.0, (255, 252, 244))]


def shock_faces(k, ang):
    half = math.pi / SW_N

    def make(shade):
        def fn(p, face, cube):
            x, z = p[0], p[2]
            rho = math.hypot(x, z)
            a = math.atan2(z, x)
            if rho < SW_R0 or rho > SW_R1 or abs(a) > half + 0.5 / rho:
                return None
            t = (rho - SW_R0) / (SW_R1 - SW_R0)
            wa = a - math.radians(ang)
            streak = 0.86 + 0.14 * math.sin(16 * wa - 4.0 * math.log(rho))
            cell = (int(math.floor(rho * math.cos(wa) * 3)), int(math.floor(rho * math.sin(wa) * 3)))
            streak *= 0.94 + 0.12 * hash2(cell[0], cell[1], 41)
            return rgba(scaled(ramp(SW_STOPS, t), streak * shade)), True
        return fn
    out = {f: TRANSPARENT for f in FACES}
    out["up"] = Paint(make(1.0), 4)
    out["down"] = Paint(make(0.85), 2)
    out["east"] = Solid(rgba((255, 252, 244)), True)
    out["west"] = Solid(rgba(VIOLET_LO), True)
    return out


ring_segments(C, "shockwave", "shock_seg", "xz", SW_N, SW_R0, SW_R1, 0.25, 0.75, shock_faces, overlap_deg=0.6,
              pivot=(0, 0.5, 0))
C.build()


# ================================================================ animations
def fmt(t):
    return f"{t:.3f}"


def sampled(length, step, fn):
    n = int(round(length / step))
    return {fmt(k * step): fn(k * step) for k in range(n + 1)}


def vec(v):
    return [round(float(x), 4) for x in v]


def ease_out(x):
    x = clamp(x)
    return 1 - (1 - x) ** 3


def ease_in(x):
    return clamp(x) ** 3


def smooth(x):
    x = clamp(x)
    return x * x * (3 - 2 * x)


def pulse(t, period, amp, phase=0.0, sign=1):
    """1 -> 1 + sign*amp -> 1 once per period (a raised cosine), starting at `phase` of the cycle."""
    return 1 + sign * amp * (0.5 - 0.5 * math.cos(2 * math.pi * (t / period + phase)))


SPAWN, WELL_COLLAPSE, WELL_IDLE = 0.5, 0.4, 2.0
CHARGE, BLAST, C_IDLE = 1.5, 0.8, 1.0
HOLD = "hold_on_last_frame"  # keep the final pose instead of snapping back when a one-shot ends


def spawn_scale(t):
    if t <= 0.3:
        s = 1.15 * ease_out(t / 0.3)
    else:
        s = 1.15 - 0.15 * smooth((t - 0.3) / 0.2)
    return vec([s] * 3)


# spin ramps that end at the idle angular speed (disk 360 deg/s, inner disk -540 deg/s) and a whole number of turns
def spin_ramp(total, end_speed, length):
    # angle(t) = a t + b t^2 with angle(L) = total and angle'(L) = end_speed:  aL + bL^2 = total, a + 2bL = end_speed
    b = (end_speed * length - total) / (length * length)
    a = end_speed - 2 * b * length
    return lambda t: a * t + b * t * t


disk_spawn = spin_ramp(720, 360, SPAWN)
inner_spawn = spin_ramp(-1080, -540, SPAWN)
well_spawn = {"body": {"scale": sampled(SPAWN, 0.025, spawn_scale)},
              "disk": {"rotation": sampled(SPAWN, 0.025, lambda t: vec([0, disk_spawn(t), 0]))},
              "disk_inner": {"rotation": sampled(SPAWN, 0.025, lambda t: vec([0, inner_spawn(t), 0]))},
              "photon_ring": {"scale": sampled(SPAWN, 0.025, lambda t: vec([1 + 0.25 * math.sin(math.pi * smooth(t / SPAWN))] * 3))}}

well_idle = {
    "disk": {"rotation": sampled(WELL_IDLE, 0.25, lambda t: vec([0, 360 * t, 0]))},
    "disk_inner": {"rotation": sampled(WELL_IDLE, 0.25, lambda t: vec([0, -540 * t, 0]))},
    "disk_tilt": {"rotation": sampled(WELL_IDLE, 0.1, lambda t: vec([1.5 * math.sin(math.pi * t), 0,
                                                                     1.5 * (math.cos(math.pi * t) - 1)]))},
    "photon_ring": {"scale": sampled(WELL_IDLE, 0.05, lambda t: vec([pulse(t, 2.0, 0.08)] * 3))},
    "halo": {"scale": sampled(WELL_IDLE, 0.05, lambda t: vec([pulse(t, 2.0, 0.08, 0.25)] * 3))},
    "core": {"scale": sampled(WELL_IDLE, 0.05, lambda t: vec([pulse(t, 2.0, 0.05, 0.0, -1)] * 3))},
}


def well_collapse_scale(t):
    if t <= 0.1:
        s = 1 + 0.3 * ease_out(t / 0.1)
    else:
        s = 1.3 * (1 - ease_in((t - 0.1) / 0.3))
    return vec([s] * 3)


well_collapse = {
    "body": {"scale": sampled(WELL_COLLAPSE, 0.0125, well_collapse_scale)},
    "disk": {"rotation": sampled(WELL_COLLAPSE, 0.0125, lambda t: vec([0, 360 * t + 7200 * t * t, 0]))},
    "disk_inner": {"rotation": sampled(WELL_COLLAPSE, 0.0125, lambda t: vec([0, -(540 * t + 10800 * t * t), 0]))},
}


def charge_s(t):
    return clamp(t / CHARGE)


def wobble(t):
    s = charge_s(t)
    amp = 1.5 + 5.5 * s * s
    phase = 2 * math.pi * (1.2 * t + 1.93 * t * t)
    return vec([amp * math.sin(phase), 120 * s * s, 0.8 * amp * math.cos(phase)])


collapse_charge = {
    "sphere": {"scale": sampled(CHARGE, 0.025, lambda t: vec([1 - 0.85 * charge_s(t) ** 3] * 3)),
               "rotation": sampled(CHARGE, 0.025, wobble)},
    "core": {"scale": sampled(CHARGE, 0.025, lambda t: vec([0.3 + 1.3 * charge_s(t) ** 2] * 3))},
    "shockwave": {"scale": {"0.000": [0, 0, 0], fmt(CHARGE): [0, 0, 0]}},
}


def blast_core(t):
    if t <= 0.1:
        s = 1.6 + 1.4 * ease_out(t / 0.1)
    elif t <= 0.3:
        s = 3.0 * (1 - smooth((t - 0.1) / 0.2))
    else:
        s = 0.0
    return vec([s] * 3)


def blast_ring(t):
    e = ease_out(t / BLAST)
    return vec([0.2 + 11.8 * e, 1, 0.2 + 11.8 * e])


collapse_blast = {
    "sphere": {"scale": {"0.000": [0, 0, 0], fmt(BLAST): [0, 0, 0]}},
    "core": {"scale": sampled(BLAST, 0.025, blast_core)},
    "shockwave": {"scale": sampled(BLAST, 0.025, blast_ring),
                  "rotation": sampled(BLAST, 0.025, lambda t: vec([0, 90 * ease_out(t / BLAST), 0]))},
}

collapse_idle = {
    "core": {"scale": sampled(C_IDLE, 0.05, lambda t: vec([0.3 * pulse(t, C_IDLE, 0.12)] * 3))},
    "shockwave": {"scale": {"0.000": [0, 0, 0], fmt(C_IDLE): [0, 0, 0]}},
}

WELL_ANIMS = {
    "animation.well.spawn": (HOLD, SPAWN, well_spawn),
    "animation.well.idle": (True, WELL_IDLE, well_idle),
    "animation.well.collapse": (HOLD, WELL_COLLAPSE, well_collapse),
}
COLLAPSE_ANIMS = {
    "animation.collapse.charge": (HOLD, CHARGE, collapse_charge),
    "animation.collapse.blast": (HOLD, BLAST, collapse_blast),
    "animation.collapse.idle": (True, C_IDLE, collapse_idle),
}


def anim_json(anims):
    out = {}
    for name, (loop, length, bones) in anims.items():
        out[name] = {"loop": loop, "animation_length": length, "bones": bones}
    return {"format_version": "1.8.0", "animations": out}


# ================================================================ write + validate
def write_model(model, name, anims, anim_name):
    geo_path = RES / f"assets/cogwit/geo/entity/{name}.geo.json"
    tex_path = RES / f"assets/cogwit/textures/entity/{name}.png"
    glow_path = RES / f"assets/cogwit/textures/entity/{name}_glowmask.png"
    anim_path = RES / f"assets/cogwit/animations/entity/{anim_name}.animation.json"
    for p in (geo_path, tex_path, glow_path, anim_path):
        p.parent.mkdir(parents=True, exist_ok=True)
    geo_path.write_text(json.dumps(model.geo(), indent=1))
    model.img.save(tex_path)
    model.glow.save(glow_path)
    anim_path.write_text(json.dumps(anim_json(anims), indent=1))
    return geo_path, tex_path, glow_path, anim_path


def validate(model, geo_path, tex_path, glow_path, anim_path, required, anims, lengths):
    geo = json.loads(geo_path.read_text())
    desc = geo["minecraft:geometry"][0]["description"]
    bones = geo["minecraft:geometry"][0]["bones"]
    names = {b["name"] for b in bones}
    assert len(names) == len(bones), "duplicate bone names"
    missing = [n for n in required if n not in names]
    assert not missing, f"missing bones {missing}"
    tw, th = desc["texture_width"], desc["texture_height"]
    assert tw in (128, 256) and th & (th - 1) == 0, f"bad atlas size {tw}x{th}"
    for b in bones:
        if "parent" in b:
            assert b["parent"] in names, f"{b['name']}: unknown parent"
        for c in b.get("cubes", []):
            assert set(c["uv"]) == set(FACES), f"{b['name']}: cube lacks faces"
            for f, u in c["uv"].items():
                ux, uy = u["uv"]
                uw, uh = u["uv_size"]
                assert uw > 0 and uh > 0 and 0 <= ux and ux + uw <= tw and 0 <= uy and uy + uh <= th, \
                    f"{b['name']} {f}: uv outside the atlas"
    for p in (tex_path, glow_path):
        im = Image.open(p)
        assert im.mode == "RGBA" and im.size == (tw, th), f"{p.name} is not RGBA {tw}x{th}"
    glow = Image.open(glow_path).load()
    tex = Image.open(tex_path).load()
    for (x, y) in model.emissive:
        assert glow[x, y] == tex[x, y]
    anim = json.loads(anim_path.read_text())["animations"]
    assert set(anim) == set(anims), "animation names differ"
    for name, a in anim.items():
        assert abs(a["animation_length"] - lengths[name]) < 1e-9, f"{name}: wrong length"
        for bname, chans in a["bones"].items():
            assert bname in names, f"{name} animates unknown bone {bname}"
            for ch, frames in chans.items():
                assert ch in ("position", "rotation", "scale")
                times = sorted(float(k) for k in frames)
                assert times[0] >= 0 and times[-1] <= a["animation_length"] + 1e-6, f"{name}/{bname} outside the length"
                assert all(len(v) == 3 for v in frames.values())
                if a["loop"] is True:
                    first, last = frames[fmt(times[0])], frames[fmt(times[-1])]
                    if ch == "rotation" and bname in ("disk", "disk_inner"):
                        assert all(abs((l - f_) % 360) < 1e-3 or abs((l - f_) % 360 - 360) < 1e-3 for f_, l in zip(first, last))
                    else:
                        assert first == last or all(abs(f_ - l) < 1e-3 for f_, l in zip(first, last)) or \
                            fmt(times[-1]) == fmt(a["animation_length"]), f"{name}/{bname}/{ch} does not close"
    return geo, anim


wp = write_model(W, "gravity_well", WELL_ANIMS, "gravity_well")
cp = write_model(C, "collapse", COLLAPSE_ANIMS, "collapse")
validate(W, *wp, ["root", "body", "core", "photon_ring", "disk_tilt", "disk", "disk_inner", "halo"], WELL_ANIMS,
         {"animation.well.spawn": SPAWN, "animation.well.idle": WELL_IDLE, "animation.well.collapse": WELL_COLLAPSE})
validate(C, *cp, ["root", "body", "sphere", "core", "shockwave"], COLLAPSE_ANIMS,
         {"animation.collapse.charge": CHARGE, "animation.collapse.blast": BLAST, "animation.collapse.idle": C_IDLE})
print("validation ok")


# ================================================================ preview: textured box rasteriser with bone poses
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_vec(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def rot_matrix(rx, ry, rz):
    rx, ry, rz = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(mz, mat_mul(my, mx))


def track_value(frames, t):
    ks = sorted((float(k), v) for k, v in frames.items())
    if t <= ks[0][0]:
        return ks[0][1]
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t <= t1:
            f = (t - t0) / max(1e-9, t1 - t0)
            return [v0[i] + (v1[i] - v0[i]) * f for i in range(3)]
    return ks[-1][1]


def pose_at(anims, name, t):
    pose = {}
    for bname, chans in anims[name][2].items():
        pose[bname] = {ch: track_value(fr, t) for ch, fr in chans.items()}
    return pose


def world_transforms(model, pose):
    xf = {}
    for b in model.bones:
        pv = b["pivot"]
        ar = pose.get(b["name"], {})
        rot = [b.get("rotation", [0, 0, 0])[i] + ar.get("rotation", [0, 0, 0])[i] for i in range(3)]
        sc = ar.get("scale", [1, 1, 1])
        pos = ar.get("position", [0, 0, 0])
        r = rot_matrix(*rot)
        ms = [[r[i][j] * sc[j] for j in range(3)] for i in range(3)]
        rc = mat_vec(ms, pv)
        t_local = [pos[i] + pv[i] - rc[i] for i in range(3)]
        if "parent" in b:
            mp, tp = xf[b["parent"]]
            m = mat_mul(mp, ms)
            t = [a + c for a, c in zip(mat_vec(mp, t_local), tp)]
        else:
            m, t = ms, t_local
        xf[b["name"]] = (m, t)
    return xf


LIGHT = (-0.35, 0.8, -0.5)
LN = math.sqrt(sum(c * c for c in LIGHT))
LIGHT = tuple(c / LN for c in LIGHT)


def render_model(model, pose, az, el, size, scale, centre, ss=2, bg=None):
    W_, H_ = size[0] * ss, size[1] * ss
    sc = scale * ss
    az_r, el_r = math.radians(az), math.radians(el)
    cam = (math.sin(az_r) * math.cos(el_r), math.sin(el_r), -math.cos(az_r) * math.cos(el_r))
    f = tuple(-c for c in cam)
    r = (f[1] * 0 - f[2] * 1, f[2] * 0 - f[0] * 0, f[0] * 1 - f[1] * 0)  # cross(f, up)
    rn = math.sqrt(sum(c * c for c in r))
    r = tuple(c / rn for c in r)
    u = (r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    xf = world_transforms(model, pose)
    tex = model.img.load()
    glow = model.glow.load()
    cxs, cys = size[0] * ss / 2, size[1] * ss / 2
    cr = sum(centre[i] * r[i] for i in range(3))
    cu = sum(centre[i] * u[i] for i in range(3))

    def project(p):
        return ((sum(p[i] * r[i] for i in range(3)) - cr) * sc + cxs,
                -(sum(p[i] * u[i] for i in range(3)) - cu) * sc + cys,
                sum(p[i] * f[i] for i in range(3)))

    zbuf = [1e18] * (W_ * H_)
    base = bg.resize((W_, H_), Image.BILINEAR) if bg else Image.new("RGB", (W_, H_), (8, 8, 14))
    bpx = base.load()
    col = [bpx[x, y] for y in range(H_) for x in range(W_)]
    glowbuf = [(0, 0, 0)] * (W_ * H_)
    translucent = []

    def raster(tri, uvs, rect, shade_n, translucent_pass):
        (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            return
        inv = 1.0 / area
        minx, maxx = max(0, int(math.floor(min(x0, x1, x2)))), min(W_ - 1, int(math.ceil(max(x0, x1, x2))))
        miny, maxy = max(0, int(math.floor(min(y0, y1, y2)))), min(H_ - 1, int(math.ceil(max(y0, y1, y2))))
        rx0, ry0, rw, rh = rect
        (ua, va), (ub, vb), (uc, vc) = uvs
        for py in range(miny, maxy + 1):
            yy = py + 0.5
            for pxx in range(minx, maxx + 1):
                xx = pxx + 0.5
                w0 = ((x1 - xx) * (y2 - yy) - (x2 - xx) * (y1 - yy)) * inv
                w1 = ((x2 - xx) * (y0 - yy) - (x0 - xx) * (y2 - yy)) * inv
                w2 = 1.0 - w0 - w1
                if w0 < -1e-7 or w1 < -1e-7 or w2 < -1e-7:
                    continue
                z = w0 * z0 + w1 * z1 + w2 * z2
                idx = py * W_ + pxx
                if z >= zbuf[idx] + (1e-4 if translucent_pass else 0.0) and not translucent_pass:
                    continue
                if translucent_pass and z >= zbuf[idx]:
                    continue
                tu = w0 * ua + w1 * ub + w2 * uc
                tv = w0 * va + w1 * vb + w2 * vc
                tx = rx0 + min(rw - 1, max(0, int(tu * rw)))
                ty = ry0 + min(rh - 1, max(0, int(tv * rh)))
                c = tex[tx, ty]
                a = c[3]
                if a < 12:
                    continue
                if translucent_pass != (a < 250):
                    continue
                gl = glow[tx, ty][3] > 0
                if gl:
                    lit = (c[0], c[1], c[2])
                else:
                    k = shade_n
                    lit = (c[0] * k, c[1] * k, c[2] * k)
                if translucent_pass:
                    al = a / 255.0
                    old = col[idx]
                    col[idx] = (int(old[0] * (1 - al) + lit[0] * al), int(old[1] * (1 - al) + lit[1] * al),
                                int(old[2] * (1 - al) + lit[2] * al))
                else:
                    zbuf[idx] = z
                    col[idx] = (int(lit[0]), int(lit[1]), int(lit[2]))
                if gl:
                    glowbuf[idx] = (c[0], c[1], c[2])
                elif not translucent_pass:
                    glowbuf[idx] = (0, 0, 0)

    faces = []
    for b in model.bones:
        m, t = xf[b["name"]]
        for c in b["cubes"]:
            for fname in FACES:
                uvd = c["uv"][fname]
                rect = (uvd["uv"][0], uvd["uv"][1], uvd["uv_size"][0], uvd["uv_size"][1])
                corners = []
                for (uu, vv) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    lp = face_point(c, fname, uu, vv)
                    wp_ = [a + bb for a, bb in zip(mat_vec(m, lp), t)]
                    corners.append(project(wp_))
                n = mat_vec(m, FACE_NORMAL[fname])
                nn = math.sqrt(sum(v * v for v in n)) or 1.0
                n = [v / nn for v in n]
                facing = sum(n[i] * f[i] for i in range(3))
                if facing > 0:  # seen from behind (the render type does not cull): light from the other side
                    n = [-v for v in n]
                shade = 0.42 + 0.58 * max(0.0, sum(n[i] * LIGHT[i] for i in range(3)))
                depth = sum(cc[2] for cc in corners) / 4
                faces.append((depth, corners, rect, shade, uvd))
    quad_uv = [(0, 0), (1, 0), (1, 1), (0, 1)]
    for depth, corners, rect, shade, _ in faces:
        raster((corners[0], corners[1], corners[2]), (quad_uv[0], quad_uv[1], quad_uv[2]), rect, shade, False)
        raster((corners[0], corners[2], corners[3]), (quad_uv[0], quad_uv[2], quad_uv[3]), rect, shade, False)
    for depth, corners, rect, shade, _ in sorted(faces, key=lambda t: -t[0]):
        rx0, ry0, rw, rh = rect
        if not any(tex[rx0 + i, ry0 + j][3] and tex[rx0 + i, ry0 + j][3] < 250 for i in range(rw) for j in range(rh)):
            continue
        raster((corners[0], corners[1], corners[2]), (quad_uv[0], quad_uv[1], quad_uv[2]), rect, shade, True)
        raster((corners[0], corners[2], corners[3]), (quad_uv[0], quad_uv[2], quad_uv[3]), rect, shade, True)
    img = Image.new("RGB", (W_, H_))
    img.putdata(col)
    gimg = Image.new("RGB", (W_, H_))
    gimg.putdata(glowbuf)
    bloom = gimg.filter(ImageFilter.GaussianBlur(4.0 * ss)).point(lambda v: int(v * 0.35))
    wide = gimg.filter(ImageFilter.GaussianBlur(12.0 * ss)).point(lambda v: int(v * 0.22))
    img = ImageChops.add(ImageChops.add(img, bloom), wide)
    return img.resize(size, Image.LANCZOS)


def backdrop(size, seed):
    w, h = size
    bg = Image.new("RGB", size)
    px = bg.load()
    for j in range(h):
        for i in range(w):
            r = math.hypot(i - w / 2, j - h / 2) / (w * 0.75)
            v = 22 - 14 * min(1.0, r)
            px[i, j] = (int(v * 0.85), int(v * 0.85), int(v + 6))
    rr = random.Random(seed)
    for _ in range(int(w * h / 1600)):
        x, y = rr.randrange(w), rr.randrange(h)
        b = rr.randrange(70, 190)
        px[x, y] = (b, b, min(255, b + 30))
    return bg


def ground(img, centre_y, rx, ry):
    d = ImageDraw.Draw(img)
    cx = img.size[0] / 2
    d.ellipse((cx - rx, centre_y - ry, cx + rx, centre_y + ry), fill=(26, 24, 36))


try:
    FONT = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 16)
except OSError:
    FONT = ImageFont.load_default()

TILE = (520, 520)
tiles = []


def tile(model, pose, az, el, scale, centre, label, seed, ground_spec=None):
    bg = backdrop(TILE, seed)
    if ground_spec:
        ground(bg, *ground_spec)
    im = render_model(model, pose, az, el, TILE, scale, centre, bg=bg)
    ImageDraw.Draw(im).text((12, 10), label, fill=(240, 232, 255), font=FONT)
    return im


well_pose = pose_at(WELL_ANIMS, "animation.well.idle", 0.5)
row1 = [
    tile(W, well_pose, 38, 30, 5.2, (0, 0, 0), "gravity_well  3/4 view (idle t=0.5s)", 1),
    tile(W, well_pose, 200, 26, 5.2, (0, 0, 0), "gravity_well  behind, from above", 2),
    tile(W, well_pose, -20, -14, 5.2, (0, 0, 0), "gravity_well  front, from below", 3),
]
cg = (0, 16, 0)
row2 = [
    tile(C, pose_at(COLLAPSE_ANIMS, "animation.collapse.idle", 0.0), 32, 16, 5.4, (0, 14, 0),
         "collapse  idle (sphere 48px, ring hidden)", 4),
    tile(C, pose_at(COLLAPSE_ANIMS, "animation.collapse.charge", 1.5), 32, 16, 5.4, (0, 14, 0),
         "collapse  charge end (sphere 0.15, core 1.6)", 5),
    tile(C, pose_at(COLLAPSE_ANIMS, "animation.collapse.blast", 0.1), 32, 16, 3.4, (0, 14, 0),
         "collapse  blast t=0.1s (core 3, ring 2.4x)", 6),
]
row3 = [
    tile(C, pose_at(COLLAPSE_ANIMS, "animation.collapse.blast", 0.45), 32, 20, 1.5, (0, 6, 0),
         "collapse  blast t=0.45s (ring ~8x)", 7),
    tile(C, {"sphere": {"scale": [0, 0, 0]}, "core": {"scale": [0, 0, 0]},
             "shockwave": {"scale": [1, 1, 1]}}, 32, 55, 9.0, (0, 0, 0), "shockwave ring at scale 1, from above", 8),
    tile(W, pose_at(WELL_ANIMS, "animation.well.spawn", 0.3), 32, 20, 5.2, (0, 0, 0), "gravity_well  spawn t=0.3s (1.15x)", 9),
]


def atlas_view(model, title, width):
    k = max(1, width // ATLAS_W)
    big = model.img.resize((ATLAS_W * k, model.atlas_h * k), Image.NEAREST)
    gl = model.glow.resize((ATLAS_W * k, model.atlas_h * k), Image.NEAREST)
    check = Image.new("RGB", big.size, (110, 112, 124))
    cd = ImageDraw.Draw(check)
    for yy in range(0, big.size[1], 16):
        for xx in range(0, big.size[0], 16):
            if (xx // 16 + yy // 16) % 2:
                cd.rectangle((xx, yy, xx + 15, yy + 15), fill=(96, 98, 110))
    a = check.copy()
    a.paste(big, (0, 0), big)
    g = Image.new("RGB", big.size, (0, 0, 0))
    g.paste(gl, (0, 0), gl)
    out = Image.new("RGB", (big.size[0] * 2 + 20, big.size[1] + 30), (24, 25, 31))
    out.paste(a, (0, 30))
    out.paste(g, (big.size[0] + 20, 30))
    ImageDraw.Draw(out).text((8, 6), f"{title}  {ATLAS_W}x{model.atlas_h}   texture | glowmask", fill=(240, 232, 255), font=FONT)
    return out


atlas_w_img = atlas_view(W, "gravity_well.png", 512)
atlas_c_img = atlas_view(C, "collapse.png", 256)
sheet_w = max(TILE[0] * 3, atlas_w_img.size[0] + atlas_c_img.size[0] + 10)
sheet = Image.new("RGB", (sheet_w, TILE[1] * 3 + max(atlas_w_img.size[1], atlas_c_img.size[1])), (24, 25, 31))
for r_i, row in enumerate((row1, row2, row3)):
    for c_i, im in enumerate(row):
        sheet.paste(im, (c_i * TILE[0], r_i * TILE[1]))
sheet.paste(atlas_w_img, (0, TILE[1] * 3))
sheet.paste(atlas_c_img, (atlas_w_img.size[0] + 10, TILE[1] * 3))
sheet.save(PREVIEW)

print(f"gravity_well: atlas {ATLAS_W}x{W.atlas_h}, bones {len(W.bones)}, cubes {sum(len(b['cubes']) for b in W.bones)}, "
      f"emissive texels {len(W.emissive)}")
print(f"collapse:     atlas {ATLAS_W}x{C.atlas_h}, bones {len(C.bones)}, cubes {sum(len(b['cubes']) for b in C.bones)}, "
      f"emissive texels {len(C.emissive)}")
for name, (loop, length, tracks) in {**WELL_ANIMS, **COLLAPSE_ANIMS}.items():
    print(f"{name}: loop={loop} length={length}s bones={sorted(tracks)}")
