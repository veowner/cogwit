from pathlib import Path
from PIL import Image

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ROOT = str(PROJECT_ROOT / "src/main/resources/assets/cogwit/textures")
PREVIEW = str(PROJECT_ROOT / "horizon-preview.png")

def H(s):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)

BASE = H('17121D'); DEEP = H('0D0A11'); EDGE = H('2E2340'); MID = H('221A2C')
VIO = H('A970FF'); CORE = H('F3E8FF'); GOLD = H('E9A23B')
BASE2 = H('1B1522')            # speckle variant of base
VIO_DIM = H('6B43AE'); VIO_DK = H('3B2766')
GOLD_HI = H('F6C46B'); GOLD_DK = H('9A6420')
OUT = H('050307')

class Face:
    def __init__(self, img, x0, y0, x1, y1):
        self.img, self.x0, self.y0, self.w, self.h = img, x0, y0, x1 - x0, y1 - y0
    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((self.x0 + x, self.y0 + y), c)
    def get(self, x, y):
        return self.img.getpixel((self.x0 + x, self.y0 + y))
    def rect(self, x0, y0, x1, y1, c):          # inclusive
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)
    def row(self, y, c, x0=0, x1=None):
        self.rect(x0, y, self.w - 1 if x1 is None else x1, y, c)
    def col(self, x, c, y0=0, y1=None):
        self.rect(x, y0, x, self.h - 1 if y1 is None else y1, c)
    def plate(self, c=BASE, speck=True):
        for y in range(self.h):
            for x in range(self.w):
                v = (self.x0 + x) * 7 + (self.y0 + y) * 13 + (self.x0 + x) * (self.y0 + y)
                self.put(x, y, BASE2 if (speck and c == BASE and v % 9 == 0) else c)

def new(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))

def corners(f, c=MID, y0=0, y1=None):
    f.col(0, c, y0, y1); f.col(f.w - 1, c, y0, y1)

# ------------------------------------------------------------------ layer 1
def layer1():
    im = new(64, 32)
    # ---- helmet (head box)
    top = Face(im, 8, 0, 16, 8); bot = Face(im, 16, 0, 24, 8)
    R = Face(im, 0, 8, 8, 16); F = Face(im, 8, 8, 16, 16)
    L = Face(im, 16, 8, 24, 16); B = Face(im, 24, 8, 32, 16)
    for f in (R, F, L, B):
        f.plate(); corners(f); f.row(0, EDGE); f.row(7, DEEP); f.row(6, MID)
        f.row(2, DEEP)                       # brow seam above visor
        f.row(5, DEEP)                       # seam under visor
    top.plate(); top.row(0, EDGE); top.col(0, EDGE); top.row(7, DEEP); top.col(7, DEEP)
    # crest stripe front->back
    top.col(3, DEEP, 0, 7); top.col(4, DEEP, 0, 7)
    top.col(3, VIO_DIM, 1, 6); top.col(4, VIO_DIM, 1, 6)
    top.col(3, VIO, 3, 5); top.col(4, VIO, 3, 5)
    bot.plate(DEEP); bot.rect(0, 0, 7, 0, EDGE)
    # visor slit (rows 3,4 of head = y 11,12) wraps around
    F.row(3, VIO); F.row(4, VIO_DIM)
    F.rect(3, 3, 4, 3, CORE); F.rect(3, 4, 4, 4, VIO)
    F.row(2, DEEP, 0, 7)
    F.put(0, 2, MID); F.put(7, 2, MID)
    # jaw vents
    F.rect(2, 6, 2, 6, DEEP); F.rect(5, 6, 5, 6, DEEP); F.col(3, DEEP, 5, 7); F.col(4, DEEP, 5, 7)
    F.put(1, 6, VIO_DK); F.put(6, 6, VIO_DK)
    # sides: slit wraps from front edge backwards (R front edge at x=7, L front edge at x=0)
    for k, (c3, c4) in enumerate([(VIO, VIO_DIM), (VIO, VIO_DIM), (VIO, VIO_DK), (VIO_DIM, VIO_DK), (VIO_DIM, None), (VIO_DK, None)]):
        R.put(7 - k, 3, c3); L.put(k, 3, c3)
        if c4: R.put(7 - k, 4, c4); L.put(k, 4, c4)
    for s in (R, L):
        s.col(3 if s is R else 4, DEEP, 5, 6)
        s.row(1, EDGE, 0, 7) if False else None
    # ear module
    R.rect(2, 5, 3, 6, DEEP); L.rect(4, 5, 5, 6, DEEP)
    R.put(2, 6, VIO_DK); L.put(5, 6, VIO_DK)
    # back
    B.col(3, DEEP, 1, 6); B.col(4, DEEP, 1, 6)
    B.put(3, 3, VIO_DIM); B.put(4, 3, VIO_DIM); B.put(3, 4, VIO); B.put(4, 4, VIO)
    B.row(2, DEEP); B.row(5, DEEP)

    # ---- chestplate body box
    bt = Face(im, 20, 16, 28, 20); bb = Face(im, 28, 16, 36, 20)
    bR = Face(im, 16, 20, 20, 32); bF = Face(im, 20, 20, 28, 32)
    bL = Face(im, 28, 20, 32, 32); bB = Face(im, 32, 20, 40, 32)
    bt.plate(); bt.rect(0, 0, 7, 0, EDGE); bt.rect(0, 3, 7, 3, DEEP); bt.rect(2, 1, 5, 2, MID)
    bb.plate(DEEP); bb.row(0, EDGE)
    for f in (bR, bF, bL, bB):
        f.plate(); corners(f, MID); f.row(0, EDGE); f.row(11, DEEP); f.row(10, MID)
        f.row(7, DEEP); f.row(8, EDGE)  # waist segment seam
        f.row(5, DEEP) if False else None
    # front core
    bF.rect(2, 2, 5, 5, VIO)
    for (x, y) in [(2, 2), (5, 2), (2, 5), (5, 5)]:
        bF.put(x, y, VIO_DK)
    bF.rect(3, 3, 4, 4, CORE)
    bF.rect(1, 1, 6, 1, DEEP)               # collar seam
    bF.put(1, 2, DEEP); bF.put(6, 2, DEEP); bF.put(1, 5, DEEP); bF.put(6, 5, DEEP)
    bF.rect(1, 6, 6, 6, DEEP)
    # lines running from core outward (wrap to sides)
    bF.put(0, 3, VIO_DIM); bF.put(1, 3, VIO_DIM); bF.put(6, 3, VIO_DIM); bF.put(7, 3, VIO_DIM)
    bF.put(0, 4, VIO_DK); bF.put(7, 4, VIO_DK)
    bR.put(3, 3, VIO_DIM); bR.put(2, 3, VIO_DK); bL.put(0, 3, VIO_DIM); bL.put(1, 3, VIO_DK)
    bR.put(3, 4, VIO_DK); bL.put(0, 4, VIO_DK)
    # abs
    bF.col(3, DEEP, 7, 10); bF.col(4, DEEP, 7, 10)
    bF.put(3, 9, VIO_DK); bF.put(4, 9, VIO_DK)
    bF.put(1, 9, DEEP); bF.put(6, 9, DEEP)
    for s in (bR, bL):
        s.row(6, DEEP)
    # back plates + spine
    bB.rect(1, 1, 6, 1, DEEP); bB.rect(1, 6, 6, 6, DEEP)
    bB.col(3, DEEP, 2, 10); bB.col(4, DEEP, 2, 10)
    for y in (2, 3, 4, 5):
        bB.put(3, y, VIO_DIM); bB.put(4, y, VIO_DIM)
    bB.put(3, 3, VIO); bB.put(4, 3, VIO)
    for y in (7, 8, 9):
        bB.put(3, y, VIO_DK) if y == 9 else None

    # ---- arms (chestplate sleeves)
    at = Face(im, 44, 16, 48, 20); ab = Face(im, 48, 16, 52, 20)
    aR = Face(im, 40, 20, 44, 32); aF = Face(im, 44, 20, 48, 32)
    aL = Face(im, 48, 20, 52, 32); aB = Face(im, 52, 20, 56, 32)
    at.plate(MID, speck=False); at.rect(0, 0, 3, 0, EDGE); at.col(0, EDGE); at.col(3, DEEP); at.row(3, DEEP)
    at.rect(1, 1, 2, 2, VIO_DIM); at.put(1, 1, VIO)
    ab.plate(DEEP)
    for f in (aR, aF, aL, aB):
        f.plate(); f.col(0, MID); f.col(3, MID)
        # shoulder plate rows 0-3
        f.rect(0, 0, 3, 3, MID); f.row(0, EDGE)
        f.row(3, VIO); f.row(4, DEEP)
        f.put(0, 3, VIO_DIM); f.put(3, 3, VIO_DIM)
        # bicep seam / elbow
        f.row(7, DEEP); f.row(8, EDGE)
        # gauntlet
        f.row(9, DEEP); f.rect(0, 10, 3, 10, MID); f.row(11, DEEP)
    aF.put(1, 10, VIO_DIM); aF.put(2, 10, VIO_DIM)
    aB.put(1, 10, VIO_DIM); aB.put(2, 10, VIO_DIM)
    aR.put(1, 10, VIO_DK); aR.put(2, 10, VIO_DK); aL.put(1, 10, VIO_DK); aL.put(2, 10, VIO_DK)
    # shoulder plate bulge detail: front dot
    aF.put(1, 1, DEEP); aF.put(2, 1, DEEP)
    aB.put(1, 1, DEEP); aB.put(2, 1, DEEP)

    # ---- boots (leg box lower part + bottom face)
    lb = Face(im, 8, 16, 12, 20)
    lb.plate(DEEP, speck=False); lb.rect(0, 0, 3, 0, MID); lb.col(0, MID); lb.col(3, OUT); lb.row(3, OUT)
    lb.put(1, 1, VIO_DK); lb.put(2, 1, VIO_DK)
    for x0, x1 in [(0, 4), (4, 8), (8, 12), (12, 16)]:
        f = Face(im, x0, 20, x1, 32)
        f.rect(0, 6, 3, 11, DEEP)
        f.row(6, EDGE)
        f.row(7, VIO)
        f.row(8, VIO_DK)
        f.row(9, MID) if False else None
        f.row(10, DEEP)
        f.row(11, BASE)
    # toe cap on front face
    fF = Face(im, 4, 20, 8, 32)
    fF.rect(0, 10, 3, 10, MID)
    fF.rect(1, 9, 2, 9, MID)
    fF.put(1, 7, CORE); fF.put(2, 7, CORE) if False else None
    # outer side highlight
    Face(im, 0, 20, 4, 32).col(0, EDGE, 6, 10); Face(im, 8, 20, 12, 32).col(3, EDGE, 6, 10)
    return im

# ------------------------------------------------------------------ layer 2
def layer2():
    im = new(64, 32)
    # ---- hips / belt: body rows 27..31 (local 7..11) of side faces
    for x0, x1 in [(16, 20), (20, 28), (28, 32), (32, 40)]:
        f = Face(im, x0, 20, x1, 32)
        w = f.w
        for y in range(7, 12):
            f.row(y, BASE)
        # speckle
        for y in range(7, 12):
            for x in range(w):
                if ((x0 + x) * 5 + y * 11) % 8 == 0:
                    f.put(x, y, BASE2)
        f.row(7, EDGE)          # y=27 upper belt edge highlight
        f.row(8, GOLD)          # y=28 gold belt line
        f.row(9, DEEP)          # belt shadow
        f.row(11, DEEP)
        f.row(10, MID)
        f.put(0, 7, MID) if False else None
    fF = Face(im, 20, 20, 28, 32)
    # buckle
    fF.rect(3, 7, 4, 9, GOLD); fF.put(3, 7, GOLD_HI); fF.put(4, 7, GOLD_HI)
    fF.put(3, 8, VIO); fF.put(4, 8, CORE)
    fF.put(3, 9, GOLD_DK); fF.put(4, 9, GOLD_DK)
    # hip plate seams + violet nubs
    fF.col(1, DEEP, 10, 11); fF.col(6, DEEP, 10, 11)
    fF.put(1, 10, VIO_DK); fF.put(6, 10, VIO_DK)
    fF.col(3, DEEP, 10, 11); fF.col(4, DEEP, 10, 11)
    bB = Face(im, 32, 20, 40, 32)
    bB.col(3, DEEP, 10, 11); bB.col(4, DEEP, 10, 11)
    bB.put(3, 10, VIO_DK); bB.put(4, 10, VIO_DK)
    # gold darker accents for ring feel
    for x0, x1 in [(16, 20), (28, 32)]:
        f = Face(im, x0, 20, x1, 32)
        f.put(1, 8, GOLD_DK) if False else None
    Face(im, 32, 20, 40, 32).put(1, 8, GOLD_DK); Face(im, 32, 20, 40, 32).put(6, 8, GOLD_DK)

    # ---- leggings: leg box top + side rows 20..27 (local 0..7)
    lt = Face(im, 4, 16, 8, 20)
    lt.plate(); lt.row(0, EDGE); lt.col(0, EDGE); lt.col(3, DEEP); lt.row(3, DEEP)
    lt.rect(1, 1, 2, 2, MID)
    faces = [Face(im, 0, 20, 4, 32), Face(im, 4, 20, 8, 32), Face(im, 8, 20, 12, 32), Face(im, 12, 20, 16, 32)]
    for f in faces:
        for y in range(0, 8):
            for x in range(4):
                v = (f.x0 + x) * 3 + y * 7
                f.put(x, y, BASE2 if v % 8 == 0 else BASE)
        f.row(0, DEEP)           # under-belt shadow
        f.row(1, EDGE)           # thigh-top highlight
        f.col(0, MID, 1, 7) if False else None
        # knee plate
        f.row(4, EDGE); f.rect(0, 5, 3, 5, MID); f.row(6, DEEP)
        f.row(7, BASE)
    # front: knee glow + thigh vent lines
    fF = faces[1]
    fF.put(1, 5, VIO); fF.put(2, 5, VIO)
    fF.put(1, 6, VIO_DK); fF.put(2, 6, VIO_DK)
    fF.col(1, DEEP, 2, 3); fF.col(2, DEEP, 2, 3)
    fF.put(1, 3, VIO_DK); fF.put(2, 3, VIO_DK)
    fB = faces[3]
    fB.put(1, 5, VIO_DIM); fB.put(2, 5, VIO_DIM)
    # sides: vertical violet seam
    faces[0].col(2, VIO_DIM, 2, 3); faces[0].put(2, 5, VIO_DIM)
    faces[2].col(1, VIO_DIM, 2, 3); faces[2].put(1, 5, VIO_DIM)
    faces[0].col(2, DEEP, 4, 4) if False else None
    return im

# ------------------------------------------------------------------ icons
def icon_from_mask(rows, fill=BASE, hi=EDGE, lo=DEEP):
    im = new(16, 16)
    g = [list(r.ljust(16, '.')) for r in (list(rows) + [''] * 16)[:16]]
    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and g[y][x] in 'Xo'
    for y in range(16):
        for x in range(16):
            ch = g[y][x]
            if ch == '.':
                continue
            if ch == 'o':
                im.putpixel((x, y), OUT); continue
            nb = [solid(x + dx, y + dy) and g[y + dy][x + dx] != 'o' for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0))]
            if not all(nb):
                im.putpixel((x, y), OUT); continue
            c = fill
            im.putpixel((x, y), c)
    # shading: highlights top/left of interior, shadow bottom/right
    px = im.load()
    def is_outline_or_empty(x, y):
        return not (0 <= x < 16 and 0 <= y < 16) or px[x, y][3] == 0 or px[x, y] == OUT
    base = [[px[x, y] for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if base[y][x] != fill:
                continue
            up = is_outline_or_empty(x, y - 1); left = is_outline_or_empty(x - 1, y)
            dn = is_outline_or_empty(x, y + 1); rt = is_outline_or_empty(x + 1, y)
            if up or left:
                px[x, y] = hi
            elif dn or rt:
                px[x, y] = lo
            elif (x * 5 + y * 3) % 7 == 0:
                px[x, y] = BASE2
    return im

def helmet_icon():
    m = ["",
         "",
         "",
         "....XXXXXXXX....",
         "...XXXXXXXXXX...",
         "..XXXXXXXXXXXX..",
         "..XXXXXXXXXXXX..",
         "..XXXXXXXXXXXX..",
         "..XXXXXXXXXXXX..",
         "..XXXXXXXXXXXX..",
         "..XXXXXXXXXXXX..",
         "..XXXX....XXXX..",
         "..XXXX....XXXX..",
         "..XXXX....XXXX..",
         ]
    im = icon_from_mask(m)
    p = im.load()
    for x in range(3, 13):
        if p[x, 8][3] and p[x, 8] != OUT: p[x, 8] = VIO
        if p[x, 9][3] and p[x, 9] != OUT: p[x, 9] = VIO_DIM
    p[7, 8] = CORE; p[8, 8] = CORE
    # dome crest
    p[7, 4] = VIO_DIM; p[8, 4] = VIO_DIM; p[7, 5] = VIO_DK; p[8, 5] = VIO_DK
    # brow seam
    for x in range(3, 13):
        if p[x, 7][3] and p[x, 7] != OUT: p[x, 7] = DEEP
    for x in range(3, 13):
        if p[x, 10][3] and p[x, 10] != OUT: p[x, 10] = DEEP
    return im

def chest_icon():
    m = ["",
         "",
         "..XXXX....XXXX..",
         ".XXXXXX..XXXXXX.",
         ".XXXXXXXXXXXXXX.",
         ".XXXXXXXXXXXXXX.",
         ".XXXXoXXXXXXoXX.".replace('o', 'X'),
         ".XXXXXXXXXXXXXX.",
         ".XXXXXXXXXXXXXX.",
         ".XXXXXXXXXXXXXX.",
         "....XXXXXXXX....",
         "....XXXXXXXX....",
         "....XXXXXXXX....",
         "....XXXXXXXX....",
         ]
    # build with dividers: rows 6..9 x4 and x11 are outline
    rows = [list(r.ljust(16, '.')) for r in m]
    for y in range(6, 10):
        rows[y][4] = 'o'; rows[y][11] = 'o'
    im = icon_from_mask([''.join(r) for r in rows])
    p = im.load()
    # shoulder plates lighter with violet line
    for (xs, xe) in [(1, 3), (12, 14)]:
        for y in range(3, 6):
            for x in range(xs, xe + 1):
                if p[x, y][3] and p[x, y] not in (OUT, EDGE): p[x, y] = MID
    for xs, xe in [(2, 3), (12, 13)]:
        for x in range(xs, xe + 1):
            p[x, 6] = VIO_DIM
    # collar
    for x in (5, 6, 9, 10):
        p[x, 4] = DEEP
    # core
    for y in (6, 9):
        for x in (6, 7, 8, 9): p[x, y] = VIO
    for y in (7, 8):
        p[6, y] = VIO; p[9, y] = VIO
    for x, y in [(6, 6), (9, 6), (6, 9), (9, 9)]: p[x, y] = VIO_DK
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]: p[x, y] = CORE
    # abdomen seams
    for x in range(5, 11):
        p[x, 11] = DEEP
    p[7, 12] = DEEP; p[8, 12] = DEEP
    p[7, 11] = VIO_DK; p[8, 11] = VIO_DK
    return im

def legs_icon():
    rows = ["",
            "",
            "...XXXXXXXXXX...",
            "...XXXXXXXXXX...",
            "...XXXXXXXXXX...",
            "...XXXXXXXXXX...",
            "...XXXXXXXXXX...",
            "...XXXXXXXXXX...",
            "...XXXXXXXXXX...",
            "...XXXX..XXXX...",
            "...XXXX..XXXX...",
            "...XXXX..XXXX...",
            "...XXXX..XXXX...",
            "...XXXX..XXXX...",
            ]
    im = icon_from_mask(rows)
    p = im.load()
    for x in range(4, 12):
        p[x, 4] = GOLD
        p[x, 5] = DEEP
    for x in (4, 11): p[x, 4] = GOLD_DK
    p[7, 4] = GOLD_HI; p[8, 4] = GOLD_HI
    p[7, 3] = VIO; p[8, 3] = VIO
    p[7, 5] = VIO_DK; p[8, 5] = VIO_DK
    # knees
    for x in (4, 5): p[x, 10] = VIO_DIM
    for x in (10, 11): p[x, 10] = VIO_DIM
    for x in (4, 5, 10, 11): p[x, 11] = MID
    return im

def boots_icon():
    rows = ["",
            "",
            "",
            "..XXXXX..XXXXX..",
            "..XXXXX..XXXXX..",
            "..XXXXX..XXXXX..",
            "..XXXXX..XXXXX..",
            "..XXXXX..XXXXX..",
            "..XXXXX..XXXXX..",
            "..XXXXX..XXXXX..",
            ".XXXXXX..XXXXXX.",
            ".XXXXXX..XXXXXX.",
            ".XXXXXX..XXXXXX.",
            ]
    im = icon_from_mask(rows, fill=DEEP, hi=EDGE, lo=H('0A070D'))
    p = im.load()
    for x in range(3, 6): p[x, 5] = VIO
    for x in range(10, 13): p[x, 5] = VIO
    for x in range(3, 6): p[x, 6] = VIO_DK
    for x in range(10, 13): p[x, 6] = VIO_DK
    for x in range(3, 6): p[x, 4] = MID
    for x in range(10, 13): p[x, 4] = MID
    # sole
    for x in list(range(2, 6)) + list(range(10, 14)):
        p[x, 11] = BASE
    return im

def template_icon():
    im = new(16, 16)
    p = im.load()
    for y in range(1, 15):
        for x in range(1, 15):
            if (x in (1, 14)) and (y in (1, 14)):
                continue
            edge = x in (1, 14) or y in (1, 14)
            p[x, y] = OUT if edge else (EDGE if (x in (2, 13) or y in (2, 13)) else BASE)
    # inner dark panel
    for y in range(3, 13):
        for x in range(3, 13):
            p[x, y] = DEEP
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        p[x, y] = MID
    # slot ticks
    for x in (4, 5, 10, 11):
        p[x, 2] = DEEP; p[x, 13] = DEEP
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d < 1.8:
                p[x, y] = OUT
            elif 1.8 <= d <= 4.3:
                a = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
                t = (a + (d - 1.8) * 0.28) % 1.0
                if t < 0.12: c = CORE
                elif t < 0.40: c = VIO
                elif t < 0.60: c = VIO_DIM
                elif t < 0.78: c = VIO_DK
                else: c = None
                if c: p[x, y] = c
    return im

def core_icon():
    im = new(16, 16)
    p = im.load()
    cx = cy = 7.5
    R = 5.4
    # ring pixels (parametric ellipse, tilted)
    ring = {}
    steps = 720
    ang = math.radians(-24)
    for i in range(steps):
        t = 2 * math.pi * i / steps
        ex, ey = 7.4 * math.cos(t), 2.2 * math.sin(t)
        rx = ex * math.cos(ang) - ey * math.sin(ang)
        ry = ex * math.sin(ang) + ey * math.cos(ang)
        xi, yi = int(round(cx + rx)), int(round(cy + ry))
        front = math.sin(t) > 0
        if 0 <= xi < 16 and 0 <= yi < 16:
            ring[(xi, yi)] = ring.get((xi, yi), False) or front
    # halo
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if R < d <= R + 1.0:
                p[x, y] = VIO_DK
    # back part of ring (hidden behind sphere)
    for (x, y), front in ring.items():
        if not front and math.hypot(x - cx, y - cy) > R:
            p[x, y] = GOLD_DK
    # sphere
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= R:
                if d > R - 1.0:
                    a = math.atan2(y - cy, x - cx)
                    diff = abs(((a + 3 * math.pi / 4 + math.pi) % (2 * math.pi)) - math.pi)
                    c = CORE if diff < 0.45 else VIO if diff < 1.7 else VIO_DIM if diff < 2.5 else VIO_DK
                    p[x, y] = c
                elif d > R - 2.0:
                    p[x, y] = H('0C0910')
                else:
                    p[x, y] = H('050307') if d < 3.0 else H('08060B')
    # front part of ring
    for (x, y), front in ring.items():
        if front:
            p[x, y] = GOLD_HI if (x - y) % 4 == 0 else GOLD
    return im

# ------------------------------------------------------------------ output
def save(im, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)

def main():
    l1, l2 = layer1(), layer2()
    icons = [("item/horizon_helmet.png", helmet_icon()), ("item/horizon_chestplate.png", chest_icon()),
             ("item/horizon_leggings.png", legs_icon()), ("item/horizon_boots.png", boots_icon()),
             ("item/horizon_template.png", template_icon()), ("item/singularity_core.png", core_icon())]
    save(l1, "models/armor/horizon_layer_1.png"); save(l2, "models/armor/horizon_layer_2.png")
    for rel, im in icons:
        save(im, rel)
    S, gap = 8, 16
    W = 2 * 64 * S + 3 * gap
    Hh = 32 * S + 16 * S + 3 * gap
    pv = Image.new('RGBA', (W, Hh), (110, 110, 118, 255))
    def paste(im, x, y):
        big = im.resize((im.width * S, im.height * S), Image.NEAREST)
        pv.alpha_composite(big, (x, y))
    paste(l1, gap, gap); paste(l2, 2 * gap + 64 * S, gap)
    x = gap
    for _, im in icons:
        paste(im, x, 2 * gap + 32 * S); x += 16 * S + gap
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    pv.save(PREVIEW)
    print("ok")

main()
