"""Draws the Token Cell Charger block textures and writes its models/blockstate (Create integration).

Run:  uv run --no-project --with pillow python -I tools/draw_cell_charger.py
All textures are drawn from scratch in the mod's copper and black palette.
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/cogwit"
TEX = ROOT / "textures/block"
MODELS = ROOT / "models/block"
ITEM_MODELS = ROOT / "models/item"
BLOCKSTATES = ROOT / "blockstates"


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


COPPER_DK = hexc("6B3A21")
COPPER_MID = hexc("B4693C")
COPPER_LT = hexc("D98A55")
COPPER_HI = hexc("F0AA74")
BLACK = hexc("14100E")
BLACK_LT = hexc("231B17")
PIT = hexc("070505")
GLOW = hexc("FF9A3C")
GLOW_DK = hexc("C2561E")
STEEL_DK = hexc("3A3836")
STEEL_MID = hexc("5A5754")
STEEL_LT = hexc("7A7773")


def new(fill):
    return Image.new("RGBA", (16, 16), fill)


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), c)


def frame(im, c_top_left, c_bottom_right):
    for i in range(16):
        im.putpixel((i, 0), c_top_left)
        im.putpixel((0, i), c_top_left)
        im.putpixel((i, 15), c_bottom_right)
        im.putpixel((15, i), c_bottom_right)


def speckle(im, seed, tones, chance, box=(0, 0, 15, 15)):
    rng = random.Random(seed)
    for y in range(box[1], box[3] + 1):
        for x in range(box[0], box[2] + 1):
            if rng.random() < chance:
                im.putpixel((x, y), rng.choice(tones))


def rivets(im, pts):
    for x, y in pts:
        im.putpixel((x, y), COPPER_HI)
        im.putpixel((x + 1, y + 1), COPPER_DK)


def side():
    im = new(COPPER_MID)
    speckle(im, 11, [COPPER_LT, COPPER_DK], 0.10)
    frame(im, COPPER_LT, COPPER_DK)
    # inner panel line
    rect(im, 2, 2, 13, 2, COPPER_DK)
    rect(im, 2, 13, 13, 13, COPPER_DK)
    rect(im, 2, 3, 2, 12, COPPER_DK)
    rect(im, 13, 3, 13, 12, COPPER_DK)
    rect(im, 3, 3, 12, 3, COPPER_LT)
    rivets(im, [(3, 4), (11, 4), (3, 10), (11, 10)])
    # black accent band
    rect(im, 4, 7, 11, 8, BLACK)
    rect(im, 4, 7, 11, 7, BLACK_LT)
    return im


def top():
    im = new(BLACK)
    speckle(im, 5, [BLACK_LT], 0.18)
    frame(im, COPPER_MID, COPPER_DK)
    rect(im, 1, 1, 14, 1, COPPER_DK)
    rect(im, 1, 14, 14, 14, COPPER_DK)
    rect(im, 1, 2, 1, 13, COPPER_DK)
    rect(im, 14, 2, 14, 13, COPPER_DK)
    # cell-shaped slot: x 5..10, z 3..12 with a terminal nub at the far end
    rect(im, 4, 2, 11, 13, COPPER_DK)
    rect(im, 5, 3, 10, 12, PIT)
    rect(im, 7, 2, 8, 2, COPPER_LT)          # terminal nub
    rect(im, 5, 3, 10, 3, BLACK_LT)          # inner shadow top
    rect(im, 5, 3, 5, 12, BLACK_LT)          # inner shadow left
    rect(im, 11, 3, 11, 12, COPPER_LT)       # rim highlight right
    rect(im, 4, 13, 11, 13, COPPER_LT)       # rim highlight bottom
    # charge pips along the slot
    for y in (5, 7, 9):
        rect(im, 7, y, 8, y, GLOW_DK)
    rivets(im, [(2, 2), (12, 2), (2, 12), (12, 12)])
    return im


def end():
    im = new(COPPER_MID)
    speckle(im, 23, [COPPER_LT, COPPER_DK], 0.10)
    frame(im, COPPER_LT, COPPER_DK)
    rect(im, 2, 2, 13, 2, COPPER_DK)
    rect(im, 2, 13, 13, 13, COPPER_DK)
    rect(im, 2, 3, 2, 12, COPPER_DK)
    rect(im, 13, 3, 13, 12, COPPER_DK)
    # dark collar around the 4x4 shaft hole (pixels 6..9)
    rect(im, 4, 4, 11, 11, BLACK)
    rect(im, 5, 5, 10, 10, STEEL_DK)
    rect(im, 4, 4, 11, 4, BLACK_LT)
    rivets(im, [(3, 3), (11, 3), (3, 11), (11, 11)])
    return im


def cell():
    im = new(GLOW_DK)
    for y in range(16):
        c = GLOW if y < 6 else GLOW_DK
        rect(im, 0, y, 15, y, c)
    rect(im, 0, 0, 15, 0, COPPER_HI)
    rect(im, 0, 15, 15, 15, COPPER_DK)
    rect(im, 0, 0, 0, 15, COPPER_LT)
    rect(im, 15, 0, 15, 15, COPPER_DK)
    return im


def shaft():
    im = new(STEEL_MID)
    speckle(im, 3, [STEEL_LT, STEEL_DK], 0.2)
    rect(im, 0, 0, 15, 0, STEEL_LT)
    rect(im, 0, 15, 15, 15, STEEL_DK)
    return im


def face_n(x0, y0, x1, y1):
    return {"uv": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "texture": "#end"}


def face_s(x0, y0, x1, y1):
    return {"uv": [x0, 16 - y1, x1, 16 - y0], "texture": "#end"}


def plates():
    """Four pieces per end around the shaft hole (6..10), so the hole stays open for the shaft."""
    boxes = [(0, 0, 6, 16), (10, 0, 16, 16), (6, 0, 10, 6), (6, 10, 10, 16)]
    out = []
    for x0, y0, x1, y1 in boxes:
        out.append({"from": [x0, y0, 0], "to": [x1, y1, 1], "faces": {"north": face_n(x0, y0, x1, y1)}})
        out.append({"from": [x0, y0, 15], "to": [x1, y1, 16], "faces": {"south": face_s(x0, y0, x1, y1)}})
    return out


def body():
    return {
        "from": [0, 0, 0], "to": [16, 16, 16],
        "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
        },
    }


def cell_box():
    f = {"uv": [0, 0, 16, 16], "texture": "#cell"}
    return {
        "from": [5, 12, 3], "to": [11, 16.5, 13],
        "faces": {k: dict(f) for k in ("up", "north", "south", "east", "west")},
    }


def shaft_box():
    f = {"uv": [0, 0, 16, 16], "texture": "#shaft"}
    return {"from": [6, 6, 0], "to": [10, 10, 16],
            "faces": {k: dict(f) for k in ("up", "down", "north", "south", "east", "west")}}


def model(elements, extra_textures=None):
    textures = {"particle": "#side", "side": "cogwit:block/cell_charger_side",
                "top": "cogwit:block/cell_charger_top", "end": "cogwit:block/cell_charger_end"}
    textures.update(extra_textures or {})
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    for name, img in (("side", side()), ("top", top()), ("end", end()), ("cell", cell()), ("shaft", shaft())):
        img.save(TEX / f"cell_charger_{name}.png")

    cell_tex = {"cell": "cogwit:block/cell_charger_cell"}
    dump(MODELS / "cell_charger.json", model([body()] + plates()))
    dump(MODELS / "cell_charger_filled.json", model([body()] + plates() + [cell_box()], cell_tex))
    # the item has no spinning shaft from Create's renderer, so it carries a static one
    dump(ITEM_MODELS / "cell_charger.json",
         model([body()] + plates() + [shaft_box()], {"shaft": "cogwit:block/cell_charger_shaft"}))

    variants = {}
    for axis, rot in (("z", None), ("x", 90)):
        for has in ("false", "true"):
            v = {"model": "cogwit:block/cell_charger" + ("_filled" if has == "true" else "")}
            if rot:
                v["y"] = rot
            variants[f"axis={axis},has_cell={has}"] = v
    dump(BLOCKSTATES / "cell_charger.json", {"variants": variants})


if __name__ == "__main__":
    main()
