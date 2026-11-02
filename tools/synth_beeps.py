"""Synthesizes Cogwit's voice: short robot beeps and trills, three variants per meaning, as mono OGG Vorbis.

Usage: uv run --no-project --with numpy --with soundfile python -I tools/synth_beeps.py <sounds_dir>
Writes <sounds_dir>/beep/<meaning>_<n>.ogg. Everything is generated here, so there is nothing to credit.

Each blip is a gliding tone: mostly sine with a soft-clipped edge and a touch of FM, so it sounds like a small
copper speaker rather than a pure test tone. Meanings:
  hello  a rising chirp and a warble        yes    two quick rising blips      no     two falling buzzy blips
  alert  three fast high beeps, trembling   happy  a running arpeggio upwards  think  soft random blips (computing)
  sad    a slow sliding fall                chirp  one short blip, for ordinary lines
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 22050
OUT = Path(sys.argv[1]) / "beep"


def blip(f0, f1, dur, vib=0.0, buzz=0.25, fm=0.15, gap=0.0):
    """One tone gliding from f0 to f1 Hz, with an attack/release envelope and a little silence after."""
    n = int(RATE * dur)
    t = np.arange(n) / RATE
    glide = f0 * (f1 / f0) ** (t / dur)
    if vib:
        glide = glide * (1 + vib * np.sin(2 * np.pi * 28 * t))
    phase = 2 * np.pi * np.cumsum(glide) / RATE
    tone = np.sin(phase + fm * np.sin(2.01 * phase))
    tone = (1 - buzz) * tone + buzz * np.tanh(4 * tone)
    env = np.minimum(1, t / 0.006) * np.minimum(1, (dur - t) / 0.025)
    return np.concatenate([tone * np.clip(env, 0, 1), np.zeros(int(RATE * gap))])


def build(parts):
    sound = np.concatenate(parts)
    tail = np.zeros(int(RATE * 0.03))
    sound = np.concatenate([sound, tail])
    return 0.5 * sound / max(1e-6, np.max(np.abs(sound)))


def hello(rng):
    base = rng.uniform(600, 760)
    return build([blip(base, base * 1.9, 0.12, gap=0.03), blip(base * 1.6, base * 1.4, 0.16, vib=0.06)])


def yes(rng):
    base = rng.uniform(700, 900)
    return build([blip(base, base * 1.15, 0.07, gap=0.035), blip(base * 1.3, base * 1.55, 0.09)])


def no(rng):
    base = rng.uniform(520, 640)
    return build([blip(base, base * 0.85, 0.09, buzz=0.6, gap=0.04), blip(base * 0.8, base * 0.55, 0.14, buzz=0.6)])


def alert(rng):
    base = rng.uniform(1300, 1500)
    return build([blip(base, base * 1.05, 0.06, vib=0.08, gap=0.04) for _ in range(3)])


def happy(rng):
    base = rng.uniform(560, 680)
    steps = [1, 1.26, 1.5, 2.0, 2.52][: rng.integers(4, 6)]
    parts = [blip(base * s, base * s * 1.04, 0.055, gap=0.012) for s in steps]
    parts.append(blip(base * steps[-1], base * steps[-1] * 1.3, 0.1, vib=0.05))
    return build(parts)


def think(rng):
    parts = []
    for _ in range(rng.integers(3, 6)):
        f = rng.uniform(450, 1100)
        parts.append(blip(f, f * rng.uniform(0.9, 1.1), rng.uniform(0.035, 0.06), buzz=0.1, gap=rng.uniform(0.01, 0.04)))
    return build(parts)


def sad(rng):
    base = rng.uniform(700, 820)
    return build([blip(base, base * 0.45, 0.42, vib=0.03, buzz=0.15)])


def chirp(rng):
    f = rng.uniform(800, 1250)
    return build([blip(f, f * rng.choice([0.8, 1.25, 1.4]), rng.uniform(0.05, 0.08), buzz=0.15)])


MEANINGS = {"hello": hello, "yes": yes, "no": no, "alert": alert, "happy": happy, "think": think, "sad": sad,
            "chirp": chirp}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for i, (name, fn) in enumerate(MEANINGS.items()):
        for n in range(3):
            rng = np.random.default_rng(1000 * i + n)
            sf.write(OUT / f"{name}_{n}.ogg", fn(rng).astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print("wrote", len(MEANINGS) * 3, "sounds to", OUT)
