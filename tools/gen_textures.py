"""Generates VESTIGE pixel-art textures with fixed palettes (GDD §25). Original art, 16x16 items,
64x32 humanoid mob skins. Run: python3 -I tools/gen_textures.py
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'vestige', 'textures')

OUT = (32, 30, 36, 255)        # outline
STEEL_HI = (230, 230, 236, 255)
STEEL = (168, 170, 182, 255)
STEEL_LO = (110, 112, 126, 255)
BRASS = (176, 138, 60, 255)
BRASS_LO = (107, 82, 34, 255)
LEATHER = (90, 59, 38, 255)
WOOD = (122, 90, 58, 255)
WOOD_LO = (86, 62, 40, 255)
EMBER = (224, 138, 60, 255)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def outline(img):
    """Adds a 1px dark outline around opaque pixels (vanilla item style)."""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and src.getpixel((nx, ny))[3] > 0 and src.getpixel((nx, ny)) != OUT:
                        img.putpixel((x, y), OUT)
                        break


def keepsake_blade():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    # Blade along the diagonal x + y = 15 from the guard to the tip, two pixels wide.
    for x in range(5, 14):
        y = 15 - x
        px(img, x, y, STEEL_HI)
        px(img, x + 1, y, STEEL)
        px(img, x, y + 1, STEEL_LO if x > 5 else STEEL)
    px(img, 14, 1, STEEL_HI)
    # Fuller (darker line) down the middle of the blade.
    for x in range(7, 12):
        px(img, x + 1, 15 - x, STEEL_LO)
    # Crossguard perpendicular to the blade, brass with an ember stone.
    for i in range(-2, 3):
        px(img, 4 + i, 11 + i, BRASS if abs(i) < 2 else BRASS_LO)
    px(img, 4, 11, EMBER)
    # Grip and pommel.
    px(img, 3, 12, LEATHER)
    px(img, 2, 13, LEATHER)
    px(img, 1, 14, BRASS)
    outline(img)
    save(img, 'item/keepsake_blade.png')


def ash_pike():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(1, 11):
        y = 15 - x
        px(img, x, y, WOOD if x % 3 else WOOD_LO)
    # Brass collar.
    px(img, 10, 5, BRASS)
    px(img, 11, 4, BRASS_LO)
    # Leaf-shaped steel head.
    for x, y, c in [(12, 3, STEEL), (13, 2, STEEL_HI), (14, 1, STEEL_HI), (12, 2, STEEL_HI), (13, 3, STEEL_LO),
                    (11, 3, STEEL_LO), (12, 4, STEEL_LO), (13, 1, STEEL), (14, 2, STEEL_LO)]:
        px(img, x, y, c)
    outline(img)
    save(img, 'item/ash_pike.png')


def hand_maul():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(2, 9):
        px(img, x, 15 - x, WOOD if x % 2 else WOOD_LO)
    px(img, 1, 14, BRASS)
    # Iron head: a diamond around (11, 4) with a brass band.
    for y in range(0, 9):
        for x in range(7, 16):
            d = abs(x - 11) + abs(y - 4)
            if d <= 3:
                c = STEEL_HI if (x - 11) - (y - 4) < -1 else (STEEL if d < 3 else STEEL_LO)
                if x - 11 == y - 4:
                    c = BRASS
                px(img, x, y, c)
    outline(img)
    save(img, 'item/hand_maul.png')


def plank_buckler():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    plank_hi = (150, 112, 70, 255)
    iron = (78, 80, 92, 255)
    for y in range(1, 15):
        for x in range(1, 15):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.6:
                c = plank_hi if (x // 3) % 2 else WOOD
                if x % 3 == 0:
                    c = WOOD_LO
                if d > 5.6:
                    c = iron
                px(img, x, y, c)
    for x in range(2, 14):
        px(img, x, 7, iron)
        px(img, x, 8, iron)
    for y in range(6, 10):
        for x in range(6, 10):
            px(img, x, y, STEEL_LO if (x in (6, 9) or y in (6, 9)) else STEEL)
    px(img, 7, 7, EMBER)
    px(img, 8, 8, EMBER)
    outline(img)
    save(img, 'item/plank_buckler.png')


def humanoid_skin(path, skin, skin_dark, cloth, cloth_dark, accent, eye_glow, seed):
    """64x32 humanoid layout (HumanoidModel.createMesh, mirrored left limbs)."""
    rnd = random.Random(seed)
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))

    def noisy(c, amt=10):
        d = rnd.randint(-amt, amt)
        return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)

    def fill(x0, y0, w, h, c, amt=10):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), noisy(c, amt))

    # Head (8x8 faces at y 0..16), skin with sunken features.
    fill(0, 0, 32, 16, skin)
    fill(8, 0, 8, 8, skin_dark)          # top: thin hair/scalp
    fill(24, 8, 8, 8, skin_dark)         # back of head
    fx, fy = 8, 8                        # face
    for x in (fx + 1, fx + 2, fx + 5, fx + 6):     # hollow eye sockets
        for y in (fy + 3, fy + 4):
            img.putpixel((x, y), (18, 16, 20, 255))
    img.putpixel((fx + 2, fy + 4), eye_glow)
    img.putpixel((fx + 5, fy + 4), eye_glow)
    for x in range(fx + 2, fx + 6):                # gaunt mouth line
        img.putpixel((x, fy + 6), (40, 34, 36, 255))
    img.putpixel((fx + 3, fy + 2), skin_dark)
    img.putpixel((fx + 4, fy + 2), skin_dark)
    # Hood overlay (hat layer at 32,0): tattered cloth around the head, open face.
    for y in range(0, 16):
        for x in range(32, 64):
            if y < 8 or not (40 <= x < 48 and y >= 9) and rnd.random() < 0.92:
                img.putpixel((x, y), noisy(cloth_dark, 8))
    for x in range(40, 48):
        img.putpixel((x, 15), (0, 0, 0, 0)) if rnd.random() < 0.5 else None
    # Body: robe with rope belt and a faded accent stripe.
    fill(16, 16, 24, 16, cloth)
    fill(20, 16, 8, 4, cloth_dark)
    for x in range(16, 40):
        img.putpixel((x, 27), noisy(accent, 6))
    for y in range(20, 32):
        img.putpixel((23, y), noisy(cloth_dark, 6))
    # Torn hem.
    for x in range(16, 40):
        if rnd.random() < 0.35:
            img.putpixel((x, 31), (0, 0, 0, 0))
    # Arms: ragged sleeves over bare, pale forearms.
    fill(40, 16, 16, 16, skin)
    for y in range(20, 26):
        for x in range(40, 56):
            img.putpixel((x, y), noisy(cloth, 8))
    fill(44, 16, 4, 4, cloth_dark)
    # Legs: dark wrapped trousers.
    fill(0, 16, 16, 16, cloth_dark)
    for y in range(20, 32, 3):
        for x in range(0, 16):
            img.putpixel((x, y), noisy((cloth_dark[0] - 12, cloth_dark[1] - 12, cloth_dark[2] - 10, 255), 4))
    save(img, path)


def star(path, color):
    """Four-point telegraph glint (bright core, soft rays)."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx), abs(y - cy)
            ray = max(0.0, 1.0 - min(dx, dy) / 1.2) * max(0.0, 1.0 - max(dx, dy) / 8.0)
            core = max(0.0, 1.0 - (dx * dx + dy * dy) / 6.0)
            a = min(1.0, ray + core)
            if a > 0.02:
                mix = min(1.0, core * 1.5)
                c = tuple(int(color[i] * (1 - mix) + 255 * mix) for i in range(3))
                img.putpixel((x, y), c + (int(a * 255),))
    save(img, path)


def ring(path):
    """Ground decal: crisp outer ring plus a soft inner fill (tinted at render time)."""
    n = 64
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            r = ((x - c) ** 2 + (y - c) ** 2) ** 0.5 / c
            if r <= 1.0:
                edge = max(0.0, 1.0 - abs(r - 0.94) / 0.06)
                fill = 0.35 * r
                # Rune-like ticks around the ring for a Minecraft-y pixel look.
                tick = 0.0
                if 0.80 < r < 0.86 and (x // 4 + y // 4) % 3 == 0:
                    tick = 0.6
                a = min(1.0, edge + fill + tick)
                img.putpixel((x, y), (255, 255, 255, int(a * 255)))
    save(img, path)


def reticle(path):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if 6.0 <= d <= 7.5:
                img.putpixel((x, y), BRASS if (x + y) % 2 else (226, 190, 110, 255))
            elif 2.0 <= d < 3.0:
                img.putpixel((x, y), (240, 220, 170, 255))
    save(img, path)


keepsake_blade()
ash_pike()
hand_maul()
plank_buckler()
# Hollowed and Brute skins are painted by tools/gen_models.py (GeckoLib models).
star('particle/glint_white.png', (235, 240, 255))
star('particle/glint_red.png', (230, 40, 30))
ring('misc/telegraph_ring.png')
reticle('misc/lock_reticle.png')
print('ok')
