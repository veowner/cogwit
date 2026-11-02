"""Draws the 16x16 inventory icons of the Horizon armor in the same style as the 3D model.

Usage: build_horizon_icons.py <resources_root> <preview_png>
"""
import sys
from pathlib import Path

from PIL import Image

RES = Path(sys.argv[1])
PREVIEW = sys.argv[2]

PALETTE = {
    ".": (0, 0, 0, 0),
    "K": (14, 14, 20, 255),     # outline
    "S": (48, 50, 63, 255),     # steel
    "H": (96, 100, 122, 255),   # steel highlight
    "D": (28, 29, 37, 255),     # steel shadow
    "G": (222, 168, 72, 255),   # gold
    "g": (150, 104, 38, 255),   # dark gold
    "Y": (250, 214, 132, 255),  # bright gold
    "R": (255, 222, 172, 255),  # glowing rim
    "V": (190, 120, 255, 255),  # violet
    "v": (96, 48, 156, 255),    # dark violet
    "W": (242, 220, 255, 255),  # violet glow
    "B": (0, 0, 0, 255),        # the black hole
    "O": (238, 150, 70, 255),   # charged token cell
    "P": (52, 20, 74, 255),     # cape
}

ICONS = {
    "horizon_helmet": [
        "......KGK.......",
        "......KGgK......",
        "..KKKKKgSKKKKK..",
        ".KDDDDDDDDDDDDK.",
        "..KRRRRRRRRRRK..",
        "..KHWSSSGSSWDK..",
        "..KHVWSSGSWVDK..",
        "..KHSVWSGWVSDK..",
        "..KHSSVWGWVSDK..",
        ".KKHSSSSGSSSDKK.",
        "KDKHSSSSGSSSDKDK",
        "KDKHSSSSGSSSDKDK",
        ".KDKHSSSSSSDKDK.",
        "..KKDDDDDDDDKK..",
        "...KKKKKKKKKK...",
        "................",
    ],
    "horizon_chestplate": [
        "................",
        ".KKKKKKKKKKKKKK.",
        "KRRRRKGGGGKRRRRK",
        "KHSSSKSSSSKSVSDK",
        "KGGGKSGvvGSKGGGK",
        ".KSDKGVBBVGKSDK.",
        ".KSDKvBBBBvKSDK.",
        ".KSVKGVBBVGKSDK.",
        ".KSDKSGvvGSKSDK.",
        ".KGDKSSWSSSKDGK.",
        ".KKKKGGGGGGKKKK.",
        "....KOKOKgOK....",
        "....KOKOKgOK....",
        "....KKKKKKKK....",
        "................",
        "................",
    ],
    "horizon_leggings": [
        "................",
        "...KKKKKKKKKK...",
        "...KGGGGGGGGK...",
        "...KHSSKKHSDK...",
        "...KHSSKKHSDK...",
        "...KHVSKKHSDK...",
        "...KHDDKKHDDK...",
        "...KGGGKKGGGK...",
        "...KHSSKKHSDK...",
        "...KHSSKKHVDK...",
        "...KHSSKKHSDK...",
        "...KHSSKKHSDK...",
        "...KDDDKKDDDK...",
        "...KKKKKKKKKK...",
        "................",
        "................",
    ],
    "horizon_boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..KKKK....KKKK..",
        "..KHSK....KHSK..",
        "..KHVK....KHSK..",
        "..KGGK....KGGK..",
        "..KHSDK...KHSDK.",
        "..KHSSSK..KHSSSK",
        "..KHSSSK..KHSSSK",
        "..KDDDDK..KDDDDK",
        "..KKKKKK..KKKKKK",
        "................",
        "................",
    ],
}

previews = []
for name, rows in ICONS.items():
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    img.save(RES / f"assets/cogwit/textures/item/{name}.png")
    previews.append(img.resize((128, 128), Image.NEAREST))

sheet = Image.new("RGBA", (128 * 4 + 30, 128), (139, 139, 139, 255))
for i, p in enumerate(previews):
    sheet.paste(p, (i * 138, 0), p)
sheet.save(PREVIEW)
