"""Turns the DevShot frames (gif_<scene>_<n>.png, one per game tick) into GIFs for the mod page.

Usage: uv run --no-project --with pillow python -I tools/make_gifs.py <screenshots_dir> <out_dir>

Each scene gets one palette built from a few of its frames, no dithering (the game is pixel art anyway), and frames
that came out identical to the one before are merged into a longer one. Pillow writes only the changed part of each
frame, so a still camera keeps the file small. Playback is 20 frames a second, the game's own tick rate.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

SRC = Path(sys.argv[1])
OUT = Path(sys.argv[2])
FRAME_MS = 50
# zoom: how much of the frame's middle to keep (2 = the middle half), shift: moves that window down by a share of
# the frame's height, width of the GIF, every how many frames, colours. The third person camera sits four blocks
# away, so the scenes about the player are cropped closer.
SCENES = {
    "slab": (1.7, 0.0, 640, 1, 255),
    "scan": (1.0, 0.0, 640, 1, 255),
    "landing": (1.4, 0.0, 640, 1, 255),
    "slam": (1.15, 0.0, 560, 2, 160),
    "beam": (1.45, -0.05, 600, 1, 200),
    "crew": (1.6, 0.12, 640, 1, 255),
}


def frames(scene):
    return sorted(SRC.glob(f"gif_{scene}_[0-9][0-9][0-9].png"))


def palette_from(images, colours):
    """One adaptive palette for the whole GIF, from up to six frames spread over it."""
    picks = images[:: max(1, len(images) // 6)][:6]
    strip = Image.new("RGB", (picks[0].width, picks[0].height * len(picks)))
    for i, im in enumerate(picks):
        strip.paste(im, (0, i * im.height))
    return strip.quantize(colors=colours, method=Image.Quantize.MEDIANCUT)


def crop(im, zoom, shift):
    w, h = round(im.width / zoom), round(im.height / zoom)
    left = (im.width - w) // 2
    top = min(im.height - h, max(0, (im.height - h) // 2 + round(shift * im.height)))
    return im.crop((left, top, left + w, top + h))


def build(scene):
    zoom, shift, width, step, colours = SCENES.get(scene, (1.0, 0.0, 640, 1, 255))
    paths = frames(scene)[::step]
    if not paths:
        return None
    images = []
    for path in paths:
        im = crop(Image.open(path).convert("RGB"), zoom, shift)
        height = round(im.height * width / im.width)
        images.append(im.resize((width, height), Image.Resampling.LANCZOS))
    palette = palette_from(images, colours)
    out, durations = [], []
    for im in images:
        if out and ImageChops.difference(im, out[-1][1]).getbbox() is None:
            durations[-1] += FRAME_MS * step
            continue
        out.append((im.quantize(palette=palette, dither=Image.Dither.NONE), im))
        durations.append(FRAME_MS * step)
    # a short pause on the last frame before it loops
    durations[-1] += 600
    target = OUT / f"{scene}.gif"
    first, rest = out[0][0], [q for q, _ in out[1:]]
    first.save(target, save_all=True, append_images=rest, duration=durations, loop=0, optimize=True, disposal=1)
    return target, len(paths), len(out)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    scenes = sorted({p.name.split("_")[1] for p in SRC.glob("gif_*_[0-9][0-9][0-9].png")})
    for scene in scenes:
        made = build(scene)
        if made:
            target, taken, kept = made
            print(f"{target.name}: {taken} frames, {kept} kept, {target.stat().st_size / 1e6:.2f} MB")
