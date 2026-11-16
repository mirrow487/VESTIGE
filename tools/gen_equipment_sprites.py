"""16x16 inventory icons for VESTIGE armor, rings, talismans and relics (GDD §25: pixel art, fixed palettes).

    python3 -I tools/gen_equipment_sprites.py
"""
import json
import math
import os
from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'vestige')
OUT = (32, 30, 36, 255)


def img():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def px(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c if len(c) == 4 else (*c, 255))


def rect(im, x0, y0, x1, y1, c, shade=True):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            k = 1.0
            if shade:
                k = 1.12 if y == y0 else (0.8 if y == y1 else 1.0)
            px(im, x, y, tuple(min(255, int(v * k)) for v in c[:3]))


def outline(im):
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        im.putpixel((x, y), OUT)
                        break


def save(im, name):
    outline(im)
    path = os.path.join(ASSETS, 'textures', 'item', name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    with open(os.path.join(ASSETS, 'models', 'item', name + '.json'), 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'vestige:item/' + name}}, f, indent=2)
        f.write('\n')


SETS = {
    'wanderer': {'main': (112, 86, 60), 'trim': (140, 48, 40), 'dark': (78, 60, 44)},
    'bellbronze': {'main': (196, 138, 62), 'trim': (232, 186, 104), 'dark': (122, 82, 40)},
    'wickiron': {'main': (66, 66, 76), 'trim': (226, 214, 186), 'dark': (40, 40, 48)},
}


def helm(p, name):
    im = img()
    rect(im, 4, 3, 11, 11, p['main'])
    rect(im, 3, 10, 12, 12, p['dark'])
    if name.startswith('wanderer'):
        rect(im, 5, 6, 10, 11, (40, 30, 26), shade=False)       # open hood
        rect(im, 6, 1, 9, 3, p['main'])
    elif name.startswith('bellbronze'):
        rect(im, 6, 1, 9, 2, p['trim'])
        rect(im, 5, 7, 10, 7, (24, 18, 12), shade=False)        # visor
        rect(im, 2, 12, 13, 13, p['dark'])                       # bell lip
    else:
        rect(im, 5, 7, 10, 7, (232, 140, 60), shade=False)       # ember eye slit
        for cx in (5, 8, 10):
            rect(im, cx, 0, cx, 2, p['trim'], shade=False)
            px(im, cx, 0, (255, 200, 90))
    return im


def chest(p, name):
    im = img()
    rect(im, 3, 2, 12, 13, p['main'])
    rect(im, 1, 2, 3, 7, p['dark'])
    rect(im, 12, 2, 14, 7, p['dark'])
    rect(im, 6, 2, 9, 3, p['trim'] if not name.startswith('wick') else p['dark'])
    rect(im, 3, 10, 12, 10, p['trim'] if name.startswith('wanderer') else (90, 92, 100), shade=False)
    if name.startswith('bellbronze'):
        for y in range(4, 10, 2):
            for x in range(4 + (y // 2) % 2, 12, 2):
                px(im, x, y, p['trim'])
    if name.startswith('wick'):
        for x in (4, 7, 11):
            rect(im, x, 4, x, 6, p['trim'], shade=False)
    return im


def legs(p, name):
    im = img()
    rect(im, 3, 2, 12, 4, p['dark'])
    rect(im, 3, 5, 7, 14, p['main'])
    rect(im, 8, 5, 12, 14, p['main'])
    rect(im, 3, 8, 7, 9, p['trim'] if not name.startswith('wick') else (98, 98, 108), shade=False)
    rect(im, 8, 8, 12, 9, p['trim'] if not name.startswith('wick') else (98, 98, 108), shade=False)
    return im


def boots(p, name):
    im = img()
    rect(im, 2, 5, 6, 12, p['main'])
    rect(im, 9, 5, 13, 12, p['main'])
    rect(im, 1, 11, 6, 13, p['dark'])
    rect(im, 9, 11, 14, 13, p['dark'])
    rect(im, 2, 5, 6, 6, p['trim'], shade=False)
    rect(im, 9, 5, 13, 6, p['trim'], shade=False)
    return im


PIECES = {'wanderer': ('wanderer_hood', 'wanderer_coat', 'wanderer_trousers', 'wanderer_boots'),
          'bellbronze': ('bellbronze_helm', 'bellbronze_mail', 'bellbronze_tassets', 'bellbronze_sabatons'),
          'wickiron': ('wickiron_helm', 'wickiron_plate', 'wickiron_cuisses', 'wickiron_greaves')}
for set_id, names in PIECES.items():
    for fn, name in zip((helm, chest, legs, boots), names):
        save(fn(SETS[set_id], name), name)


def ring(band, gem, glow=None):
    im = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 9)
            if 3.2 <= d <= 5.2:
                k = 1.15 if y < 8 else 0.85
                px(im, x, y, tuple(min(255, int(v * k)) for v in band))
    rect(im, 6, 2, 9, 5, gem)
    px(im, 6, 2, (255, 255, 255))
    if glow:
        px(im, 8, 3, glow)
    return im


RINGS = {
    'ring_of_quick_breath': ((170, 172, 184), (120, 210, 200)),
    'bellbronze_band': ((196, 138, 62), (232, 186, 104)),
    'waxen_ring': ((226, 214, 186), (236, 140, 54)),
    'hollow_ring': ((120, 120, 130), (30, 30, 36)),
    'tithe_ring': ((214, 176, 70), (250, 220, 120)),
    'ring_of_the_unsung': ((60, 70, 76), (95, 168, 168)),
    'harts_ring': ((140, 100, 60), (190, 50, 50)),
    'ember_signet': ((80, 80, 90), (240, 110, 40)),
}
for name, (band, gem) in RINGS.items():
    save(ring(band, gem), name)


def talisman(shape, main, accent):
    im = img()
    if shape == 'feather':
        for i in range(12):
            px(im, 3 + i, 13 - i, accent)
            for w in range(1, 3 + (i < 8)):
                px(im, 3 + i - w, 13 - i - w + 1, main)
                px(im, 3 + i + w - 1, 13 - i + w, main)
    elif shape == 'leaf':
        for y in range(2, 14):
            half = int(4.5 * math.sin(math.pi * (y - 2) / 12))
            for x in range(7 - half, 9 + half):
                px(im, x, y, main)
        for y in range(3, 14):
            px(im, 8, y, accent)
    elif shape == 'ledger':
        rect(im, 3, 2, 12, 13, main)
        rect(im, 4, 3, 11, 12, (226, 214, 186))
        for y in (5, 7, 9, 11):
            rect(im, 5, y, 10, y, (110, 90, 70), shade=False)
        rect(im, 3, 2, 4, 13, accent)
    elif shape == 'lungs':
        for cx in (5, 10):
            for y in range(4, 13):
                for x in range(cx - 2, cx + 3):
                    if math.hypot(x - cx, (y - 8) * 0.6) < 3:
                        px(im, x, y, main)
        rect(im, 7, 2, 8, 6, accent)
    else:  # bell
        for y in range(3, 13):
            half = 2 + (y - 3) // 3
            for x in range(8 - half, 8 + half):
                px(im, x, y, main)
        rect(im, 4, 12, 11, 13, accent)
        rect(im, 7, 1, 8, 2, accent)
    return im


TALISMANS = {
    'lightfoot_feather': ('feather', (230, 226, 214), (170, 160, 140)),
    'talisman_of_the_grove': ('leaf', (98, 150, 70), (60, 100, 44)),
    'ledger_of_debts': ('ledger', (90, 50, 40), (214, 176, 70)),
    'talisman_of_second_breath': ('lungs', (210, 120, 120), (240, 220, 200)),
    'bell_of_warding': ('bell', (95, 168, 168), (60, 110, 110)),
}
for name, args in TALISMANS.items():
    save(talisman(*args), name)


def relic(kind):
    im = img()
    if kind == 'windsail_cape':
        for y in range(2, 15):
            spread = 2 + (y - 2) // 2
            for x in range(8 - spread, 8 + spread):
                px(im, x, y, (70, 110, 150) if (x + y) % 5 else (230, 230, 240))
        rect(im, 5, 1, 10, 2, (214, 176, 70))
    elif kind == 'bellbronze_spurs':
        rect(im, 2, 8, 10, 10, (196, 138, 62))
        rect(im, 2, 5, 3, 10, (196, 138, 62))
        for a in range(0, 360, 45):
            px(im, 12 + round(2.2 * math.cos(math.radians(a))), 9 + round(2.2 * math.sin(math.radians(a))), (232, 186, 104))
        px(im, 12, 9, (122, 82, 40))
    elif kind == 'divers_bell':
        for y in range(2, 14):
            half = 3 + (y - 2) // 3
            for x in range(8 - half, 8 + half):
                px(im, x, y, (150, 150, 160))
        rect(im, 5, 6, 10, 9, (120, 200, 230), shade=False)
        rect(im, 6, 7, 7, 8, (230, 250, 255), shade=False)
    elif kind == 'climbers_hooks':
        for cx in (4, 11):
            rect(im, cx - 1, 6, cx + 1, 13, (104, 70, 44))
            for i in range(4):
                px(im, cx - 2 + i, 5 - i // 2, (170, 172, 184))
            px(im, cx + 2, 4, (220, 222, 230))
    else:  # pale hook
        for i in range(10):
            px(im, 2 + i, 13 - i, (200, 200, 210) if i % 2 else (140, 140, 150))
        for x, y in ((11, 2), (12, 2), (13, 3), (13, 4), (12, 5), (10, 3)):
            px(im, x, y, (236, 236, 244))
    return im


for name in ('windsail_cape', 'bellbronze_spurs', 'divers_bell', 'climbers_hooks', 'pale_hook'):
    save(relic(name), name)

# Kindling Ember: a coal glowing from within.
im = img()
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 8.5)
        if d < 5.5:
            c = (255, 214, 140) if d < 1.8 else (236, 120, 40) if d < 3.6 else (90, 40, 28)
            px(im, x, y, c)
save(im, 'kindling_ember')
print('ok')
