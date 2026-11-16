"""Generates VESTIGE's 3D weapon item models (Java block-model elements, Blockbench-compatible) and
their part textures. Run: python3 -I tools/gen_item_models.py

Each weapon is built along its own axis from bevelled boxes, then every element is rotated -45 deg on Z
around the grip, so the 3D weapon sits exactly where the flat sprite's diagonal sat in the hand. All
combat poses were calibrated against that placement. Inventory and item frames keep the crisp 16x16
sprite through NeoForge's `separate_transforms` loader; hands, the ground and heads show the 3D model.
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'vestige')
TEX_PX = 32           # texture size; UVs are always 0..16, so 1 model unit = 2 texels
U = TEX_PX // 16

# Vanilla item/handheld and item/generated display transforms (copied so the 3D model needs no parent).
HANDHELD = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
}

# ---------------------------------------------------------------------------------------------- textures
OUT = (32, 30, 36)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


class Texture:
    """A 32x32 part atlas: named material rectangles in UV units, each painted procedurally."""

    def __init__(self, seed):
        self.img = Image.new('RGBA', (TEX_PX, TEX_PX), (0, 0, 0, 0))
        self.mats = {}
        self.rng = random.Random(seed)

    def material(self, name, rect, painter):
        self.mats[name] = rect
        u0, v0, u1, v1 = rect
        for y in range(v0 * U, v1 * U):
            for x in range(u0 * U, u1 * U):
                c = painter(x - u0 * U, y - v0 * U, (u1 - u0) * U, (v1 - v0) * U, self.rng)
                self.img.putpixel((x, y), (*c, 255))

    def save(self, rel):
        path = os.path.join(ROOT, 'textures', rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


def noisy(base, amt):
    return lambda x, y, w, h, r: shade(base, 1 + r.uniform(-amt, amt))


def steel(base):
    # Brushed steel: faint lengthwise streaks, an occasional bright fleck.
    def p(x, y, w, h, r):
        f = 1.0 + (0.05 if (x * 7 + y // 6) % 5 == 0 else 0) + r.uniform(-0.03, 0.03)
        if r.random() < 0.02:
            f = 1.18
        return shade(base, f)
    return p


def leather(x, y, w, h, r):
    # Diagonal wrap: a dark seam every 3 texels along the grip.
    seam = (y + x) % 3 == 0
    return shade((92, 60, 38), (0.72 if seam else 1.0) + r.uniform(-0.04, 0.04))


def wood(base):
    def p(x, y, w, h, r):
        grain = (x * 5 + (y // 7)) % 4 == 0
        knot = r.random() < 0.01
        return shade(base, (0.8 if grain else 1.0) * (0.7 if knot else 1.0) + r.uniform(-0.04, 0.04))
    return p


def brass(x, y, w, h, r):
    f = 1.15 if y == 0 or x == 0 else (0.8 if y == h - 1 or x == w - 1 else 1.0)
    return shade((182, 142, 62), f + r.uniform(-0.04, 0.04))


def ember(x, y, w, h, r):
    d = abs(x - (w - 1) / 2) + abs(y - (h - 1) / 2)
    return (255, 214, 140) if d < 1 else (236, 140, 54)


def iron(x, y, w, h, r):
    # Dark forged iron with hammer marks.
    f = 1.0 + r.uniform(-0.08, 0.08)
    if r.random() < 0.06:
        f = 0.75
    return shade((78, 80, 92), f)


def planks(x, y, w, h, r):
    # Vertical boards 6 texels wide with dark joints and a few nail holes.
    board = x // 6
    if x % 6 == 0:
        return shade((70, 50, 32), 1.0)
    tone = [1.0, 0.9, 1.06, 0.95][board % 4]
    grain = (y * 3 + board * 5) % 9 == 0
    return shade((150, 112, 70), tone * (0.86 if grain else 1.0) + r.uniform(-0.03, 0.03))


def wax(x, y, w, h, r):
    drip = (x * 3 + y) % 7 == 0
    return shade((232, 222, 196), (0.9 if drip else 1.0) + r.uniform(-0.03, 0.03))


def wick(x, y, w, h, r):
    return shade((52, 40, 30), 1 + r.uniform(-0.1, 0.1))


def bronze(base):
    def p(x, y, w, h, r):
        band = y % 6 == 0
        return shade(base, (0.78 if band else 1.0) + r.uniform(-0.05, 0.05) + (0.12 if r.random() < 0.03 else 0))
    return p


def censer_pot(x, y, w, h, r):
    # Pierced brass: a grid of holes glowing with embers inside.
    if x % 4 == 2 and y % 4 == 2:
        return (255, 170, 70) if (x + y) % 8 else (236, 120, 40)
    return shade((170, 128, 56), (1.12 if y == 0 else 1.0) + r.uniform(-0.05, 0.05))


def string(x, y, w, h, r):
    return shade((222, 214, 190), 1 + r.uniform(-0.04, 0.04))


def bone(x, y, w, h, r):
    return shade((214, 204, 176), (0.85 if (x + y) % 5 == 0 else 1.0) + r.uniform(-0.03, 0.03))


def kite_face(x, y, w, h, r):
    # Hammered steel with a faint vertical sheen.
    f = 1.0 + 0.06 * (1 if x % 7 < 2 else 0) + r.uniform(-0.05, 0.05)
    if r.random() < 0.04:
        f -= 0.12
    return shade((150, 154, 168), f)


def atlas(seed, rel, mats):
    """Packs (name, w, h, painter) rectangles (UV units) into a 16x16-unit atlas, tallest first."""
    t = Texture(seed)
    x = y = 0
    shelf = 0
    for name, w, h, painter in sorted(mats, key=lambda m: -m[2]):
        if y + h > 16:
            x, y = x + shelf, 0
            shelf = 0
        assert x + w <= 16, ('atlas full', rel, name)
        t.material(name, (x, y, x + w, y + h), painter)
        y += h
        shelf = max(shelf, w)
    t.save(rel)
    return t


COMMON = lambda: [
    ('steel_hi', 1, 16, steel((228, 230, 238))), ('steel', 1, 16, steel((170, 172, 184))), ('steel_lo', 1, 16, steel((112, 114, 128))),
    ('brass', 2, 3, brass), ('brass_lo', 2, 3, lambda x, y, w, h, r: shade((120, 92, 40), 1 + r.uniform(-0.05, 0.05))),
    ('leather', 2, 8, leather), ('ember', 1, 1, ember), ('iron', 2, 5, iron),
    ('iron_lo', 2, 4, lambda x, y, w, h, r: shade(iron(x, y, w, h, r), 0.72)),
]


def weapon_atlas():
    t = Texture(7)
    t.material('steel_hi', (0, 0, 1, 16), steel((228, 230, 238)))
    t.material('steel', (1, 0, 3, 16), steel((170, 172, 184)))
    t.material('steel_lo', (3, 0, 4, 16), steel((112, 114, 128)))
    t.material('brass', (4, 0, 6, 4), brass)
    t.material('brass_lo', (4, 4, 6, 8), lambda x, y, w, h, r: shade((120, 92, 40), 1 + r.uniform(-0.05, 0.05)))
    t.material('leather', (6, 0, 8, 16), leather)
    t.material('wood', (8, 0, 10, 16), wood((128, 94, 60)))
    t.material('wood_lo', (10, 0, 11, 16), wood((92, 66, 42)))
    t.material('ember', (4, 8, 5, 9), ember)
    t.material('iron', (11, 0, 16, 6), iron)
    t.material('iron_lo', (11, 6, 16, 10), lambda x, y, w, h, r: shade(iron(x, y, w, h, r), 0.72))
    t.material('cloth', (11, 10, 13, 16), lambda x, y, w, h, r: shade((126, 36, 32), (0.8 if y % 4 == 0 else 1) + r.uniform(-0.05, 0.05)))
    t.save('item/weapon_parts.png')
    return t


def buckler_atlas():
    t = Texture(11)
    t.material('planks', (0, 0, 12, 12), planks)
    t.material('iron', (12, 0, 16, 6), iron)
    t.material('iron_lo', (12, 6, 16, 10), lambda x, y, w, h, r: shade(iron(x, y, w, h, r), 0.72))
    t.material('leather', (12, 10, 14, 16), leather)
    t.material('brass', (14, 10, 16, 14), brass)
    t.material('ember', (14, 14, 15, 15), ember)
    t.material('planks_back', (0, 12, 12, 16), lambda x, y, w, h, r: shade(planks(x, y, w, h, r), 0.78))
    t.save('item/plank_buckler_3d.png')
    return t


# ------------------------------------------------------------------------------------------------ models
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


class Model:
    def __init__(self, tex, texture_id, pivot=(8, 8, 8), angle=0):
        self.tex = tex
        self.texture_id = texture_id
        self.pivot = pivot
        self.angle = angle
        self.elements = []

    def _uv(self, mat, fu, fv):
        u0, v0, u1, v1 = self.tex.mats[mat]
        return [u0, v0, round(u0 + min(fu, u1 - u0), 3), round(v0 + min(fv, v1 - v0), 3)]

    def box(self, mat, frm, to, faces=None, skip=(), name=None):
        """An element from..to (model units). faces maps a face name to another material."""
        faces = faces or {}
        for v in (*frm, *to):
            assert -16 <= v <= 32, (name, frm, to)
        dx, dy, dz = (to[i] - frm[i] for i in range(3))
        size = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
        el = {'name': name or mat, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {}}
        if self.angle:
            el['rotation'] = {'angle': self.angle, 'axis': 'z', 'origin': list(self.pivot)}
        for f in FACES:
            if f in skip:
                continue
            m = faces.get(f, mat)
            el['faces'][f] = {'uv': self._uv(m, *size[f]), 'texture': '#parts'}
        self.elements.append(el)

    def axial(self, mat, a0, a1, w, d, x=0.0, z=0.0, **kw):
        """A box along the weapon axis: a0..a1 from the grip, w wide (x), d deep (z), offset x/z."""
        px, py, pz = self.pivot
        self.box(mat, (px + x - w / 2, py + a0, pz + z - d / 2), (px + x + w / 2, py + a1, pz + z + d / 2), **kw)

    def write(self, name, display):
        model = {
            'credit': 'VESTIGE - generated by tools/gen_item_models.py',
            'texture_size': [TEX_PX, TEX_PX],
            'textures': {'parts': self.texture_id, 'particle': self.texture_id},
            'elements': self.elements,
            'display': display,
        }
        dump(f'models/item/{name}_3d.json', model)


def dump(rel, obj):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def item_model(name, gui_texture, base=None, overrides=None, out=None):
    """Top-level item model: the 3D model everywhere except the inventory and item frames."""
    flat = {'parent': 'minecraft:item/handheld', 'textures': {'layer0': gui_texture}}
    model = {
        'loader': 'neoforge:separate_transforms',
        'gui_light': 'front',
        'base': {'parent': f'vestige:item/{base or name + "_3d"}'},
        'perspectives': {'gui': flat, 'fixed': flat},
    }
    if overrides:
        model['overrides'] = overrides
    dump(f'models/item/{out or name}.json', model)


# Vanilla bow display transforms (the 3D bow follows the bow sprite's layout).
BOW_DISPLAY = {
    'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'ground': HANDHELD['ground'], 'head': HANDHELD['head'],
}


def shield_display(scale, fp_scale, tp_translate=(0, 3, 3), fp_translate=(1, -1, -3)):
    tx, ty, tz = tp_translate
    fx, fy, fz = fp_translate
    return {
        'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [tx, ty, tz], 'scale': [scale] * 3},
        'thirdperson_lefthand': {'rotation': [0, -90, 0], 'translation': [tx, ty, tz], 'scale': [scale] * 3},
        'firstperson_righthand': {'rotation': [0, 170, 5], 'translation': [-fx, fy, fz], 'scale': [fp_scale] * 3},
        'firstperson_lefthand': {'rotation': [0, 190, -5], 'translation': [fx, fy, fz], 'scale': [fp_scale] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    }


# Grip point of the flat sprites (bottom-left of the diagonal), in model units.
GRIP = (3.5, 3.5, 8)


def keepsake_blade(t):
    m = Model(t, 'vestige:item/weapon_parts', GRIP, -45)
    m.axial('brass', -3.6, -2.4, 1.8, 1.8, name='pommel')
    m.axial('brass_lo', -2.4, -2.0, 1.2, 1.2, name='pommel_neck')
    m.axial('leather', -2.0, 1.6, 1.1, 1.1, name='grip')
    m.axial('brass', 1.6, 2.6, 6.0, 1.4, faces={'up': 'brass_lo', 'down': 'brass_lo'}, name='crossguard')
    m.axial('brass_lo', 1.8, 2.4, 0.8, 1.6, x=-3.2, name='quillon_l')
    m.axial('brass_lo', 1.8, 2.4, 0.8, 1.6, x=3.2, name='quillon_r')
    m.axial('ember', 1.75, 2.45, 0.9, 1.8, name='ember_stone')
    m.axial('brass', 2.6, 3.4, 2.2, 1.0, name='ricasso')
    # Blade: a raised dark fuller between thinner bevelled flanks; the edges catch the light.
    m.axial('steel_lo', 3.4, 12.5, 0.6, 0.62, faces={'up': 'steel'}, name='fuller')
    m.axial('steel', 3.4, 13.6, 0.55, 0.4, x=-0.575, faces={'west': 'steel_hi'}, skip=('east',), name='flank_l')
    m.axial('steel', 3.4, 13.6, 0.55, 0.4, x=0.575, faces={'east': 'steel_hi'}, skip=('west',), name='flank_r')
    m.axial('steel', 12.5, 13.6, 0.6, 0.5, faces={'up': 'steel_hi'}, name='fuller_end')
    m.axial('steel_hi', 13.6, 14.6, 1.1, 0.36, name='tip_1')
    m.axial('steel_hi', 14.6, 15.4, 0.5, 0.3, name='tip_2')
    m.write('keepsake_blade', HANDHELD)


def ash_pike(t):
    m = Model(t, 'vestige:item/weapon_parts', GRIP, -45)
    m.axial('brass', -5.0, -4.2, 1.3, 1.3, name='butt_cap')
    m.axial('wood', -4.2, 11.0, 0.9, 0.9, faces={'up': 'wood_lo', 'down': 'wood_lo'}, name='shaft')
    m.axial('leather', -1.2, 1.8, 1.1, 1.1, name='grip_wrap')
    m.axial('brass_lo', 4.8, 5.4, 1.1, 1.1, name='mid_band')
    m.axial('cloth', 9.2, 11.4, 0.4, 1.2, x=0.7, name='tassel')
    m.axial('brass', 11.0, 12.0, 1.4, 1.4, name='collar')
    m.axial('steel_lo', 12.0, 13.0, 0.9, 0.9, name='socket')
    # Leaf-shaped head: widest a third of the way up, a raised spine down the middle.
    m.axial('steel', 13.0, 14.6, 2.2, 0.4, faces={'east': 'steel_hi', 'west': 'steel_hi'}, name='leaf_1')
    m.axial('steel', 14.6, 16.0, 1.6, 0.4, faces={'east': 'steel_hi', 'west': 'steel_hi'}, name='leaf_2')
    m.axial('steel_hi', 16.0, 17.0, 0.9, 0.34, name='leaf_3')
    m.axial('steel_hi', 17.0, 17.6, 0.4, 0.3, name='point')
    m.axial('steel_lo', 13.0, 16.4, 0.4, 0.62, name='spine')
    m.write('ash_pike', HANDHELD)


def hand_maul(t):
    m = Model(t, 'vestige:item/weapon_parts', GRIP, -45)
    m.axial('brass', -3.4, -2.6, 1.4, 1.4, name='knob')
    m.axial('leather', -2.6, 1.8, 1.2, 1.2, name='grip')
    m.axial('wood', 1.8, 8.6, 1.0, 1.0, faces={'up': 'wood_lo'}, name='haft')
    m.axial('brass_lo', 5.4, 6.0, 1.2, 1.2, name='haft_band')
    # Iron head across the haft, brass bands near each striking face, a short top spike.
    m.axial('iron', 8.4, 11.6, 5.0, 3.0, faces={'up': 'iron_lo', 'down': 'iron_lo'}, skip=('east', 'west'), name='head')
    m.axial('brass', 8.2, 11.8, 0.6, 3.4, x=-1.6, skip=('east', 'west'), name='band_l')
    m.axial('brass', 8.2, 11.8, 0.6, 3.4, x=1.6, skip=('east', 'west'), name='band_r')
    m.axial('iron_lo', 8.7, 11.3, 0.5, 2.6, x=-2.75, name='face_l')
    m.axial('iron_lo', 8.7, 11.3, 0.5, 2.6, x=2.75, name='face_r')
    m.axial('iron', 11.6, 12.6, 1.2, 1.2, name='spike')
    m.axial('iron_lo', 12.6, 13.2, 0.6, 0.6, name='spike_tip')
    m.write('hand_maul', HANDHELD)


def plank_buckler(t):
    # A small round shield in the XY plane, face toward +Z (south), centred on the block.
    m = Model(t, 'vestige:item/plank_buckler_3d')
    z0, z1 = 7.4, 8.4
    back = {'north': 'planks_back'}
    m.box('planks', (5, 2.5, z0), (11, 13.5, z1), faces=back, name='board_mid')
    m.box('planks', (3, 3.5, z0), (5, 12.5, z1), faces=back, skip=('east',), name='board_l1')
    m.box('planks', (11, 3.5, z0), (13, 12.5, z1), faces=back, skip=('west',), name='board_r1')
    m.box('planks', (2, 5.5, z0), (3, 10.5, z1), faces=back, skip=('east',), name='board_l2')
    m.box('planks', (13, 5.5, z0), (14, 10.5, z1), faces=back, skip=('west',), name='board_r2')
    # Iron rim pieces on the front edge (slightly proud of the boards), a cross band and a domed boss.
    m.box('iron_lo', (5, 2.3, z1), (11, 3.1, z1 + 0.3), name='rim_bottom')
    m.box('iron_lo', (5, 12.9, z1), (11, 13.7, z1 + 0.3), name='rim_top')
    m.box('iron_lo', (1.8, 5.5, z1), (2.6, 10.5, z1 + 0.3), name='rim_left')
    m.box('iron_lo', (13.4, 5.5, z1), (14.2, 10.5, z1 + 0.3), name='rim_right')
    m.box('iron', (2.6, 7.4, z1), (13.4, 8.6, z1 + 0.2), name='band')
    m.box('iron', (6.0, 6.0, z1 + 0.2), (10.0, 10.0, z1 + 1.0), name='boss')
    m.box('iron_lo', (6.8, 6.8, z1 + 1.0), (9.2, 9.2, z1 + 1.6), name='boss_dome')
    m.box('ember', (7.6, 7.6, z1 + 1.6), (8.4, 8.4, z1 + 1.9), name='boss_stone')
    for x, y in ((4, 5), (12, 5), (4, 11), (12, 11)):
        m.box('brass', (x - 0.4, y - 0.4, z1), (x + 0.4, y + 0.4, z1 + 0.25), name='rivet')
    # Leather strap on the back.
    m.box('leather', (6.5, 4.5, z0 - 0.8), (9.5, 11.5, z0), skip=('south',), name='strap')
    # Held on the forearm: face outward from the arm, rim forward.
    display = {
        'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 3, 3], 'scale': [0.7, 0.7, 0.7]},
        'thirdperson_lefthand': {'rotation': [0, -90, 0], 'translation': [0, 3, 3], 'scale': [0.7, 0.7, 0.7]},
        'firstperson_righthand': {'rotation': [0, 170, 5], 'translation': [-1, -1, -3], 'scale': [0.62, 0.62, 0.62]},
        'firstperson_lefthand': {'rotation': [0, 190, -5], 'translation': [1, -1, -3], 'scale': [0.62, 0.62, 0.62]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    }
    m.write('plank_buckler', display)


def taper_fang():
    t = atlas(21, 'item/taper_fang_3d.png', COMMON() + [('wax', 2, 4, wax), ('wick', 1, 2, wick)])
    m = Model(t, 'vestige:item/taper_fang_3d', GRIP, -45)
    m.axial('ember', -3.7, -3.3, 0.4, 0.4, name='wick_ember')
    m.axial('wick', -3.3, -2.9, 0.3, 0.3, name='wick')
    m.axial('wax', -2.9, -1.6, 1.4, 1.4, name='candle_pommel')
    m.axial('leather', -1.6, 1.2, 1.0, 1.0, name='grip')
    m.axial('wax', 1.2, 2.0, 3.6, 1.2, name='wax_guard')
    m.axial('wax', 0.5, 1.2, 0.5, 0.6, x=-1.4, name='drip_l')
    m.axial('wax', 0.8, 1.2, 0.4, 0.5, x=1.25, name='drip_r')
    m.axial('steel_lo', 2.0, 6.6, 0.4, 0.44, name='spine')
    m.axial('steel', 2.0, 7.0, 0.32, 0.3, x=-0.36, faces={'west': 'steel_hi'}, skip=('east',), name='edge_l')
    m.axial('steel', 2.0, 7.0, 0.32, 0.3, x=0.36, faces={'east': 'steel_hi'}, skip=('west',), name='edge_r')
    m.axial('steel_hi', 6.6, 7.6, 0.6, 0.3, name='tip_1')
    m.axial('steel_hi', 7.6, 8.2, 0.24, 0.24, name='tip_2')
    m.write('taper_fang', HANDHELD)


def wickiron_greatblade():
    t = atlas(22, 'item/wickiron_greatblade_3d.png', COMMON() + [('wick', 1, 12, wick), ('wax', 2, 2, wax)])
    m = Model(t, 'vestige:item/wickiron_greatblade_3d', GRIP, -45)
    m.axial('brass', -6.2, -5.0, 2.0, 2.0, name='pommel')
    m.axial('leather', -5.0, 1.0, 1.2, 1.2, name='long_grip')
    m.axial('wick', -3.6, -3.1, 1.3, 1.3, name='cord_1')
    m.axial('wick', -1.6, -1.1, 1.3, 1.3, name='cord_2')
    m.axial('iron_lo', 1.0, 2.2, 7.0, 1.8, name='guard')
    m.axial('iron', 0.6, 2.6, 1.2, 2.0, x=-3.2, name='guard_cap_l')
    m.axial('iron', 0.6, 2.6, 1.2, 2.0, x=3.2, name='guard_cap_r')
    m.axial('wax', 1.1, 2.1, 1.2, 2.0, name='wax_seal')
    m.axial('ember', 1.3, 1.9, 0.6, 2.1, name='ember_core')
    m.axial('iron', 2.2, 17.5, 2.0, 0.7, faces={'up': 'iron_lo'}, name='blade_core')
    m.axial('wick', 3.0, 14.5, 0.5, 0.74, name='wick_fuller')
    m.axial('steel', 2.2, 17.8, 0.55, 0.46, x=-1.275, faces={'west': 'steel_hi'}, skip=('east',), name='edge_l')
    m.axial('steel', 2.2, 17.8, 0.55, 0.46, x=1.275, faces={'east': 'steel_hi'}, skip=('west',), name='edge_r')
    m.axial('iron', 17.5, 18.7, 1.8, 0.6, name='tip_1')
    m.axial('steel_hi', 18.7, 19.7, 0.9, 0.44, name='tip_2')
    m.write('wickiron_greatblade', HANDHELD)


def chandlers_censer():
    t = atlas(23, 'item/chandlers_censer_3d.png', COMMON() + [('pot', 6, 4, censer_pot), ('wood', 1, 8, wood((128, 94, 60)))])
    m = Model(t, 'vestige:item/chandlers_censer_3d', GRIP, -45)
    m.axial('brass', -3.4, -2.8, 1.3, 1.3, name='knob')
    m.axial('leather', -2.8, 1.4, 1.0, 1.0, name='grip')
    m.axial('wood', 1.4, 2.6, 0.9, 0.9, name='haft')
    m.axial('brass', 2.6, 3.2, 1.4, 1.4, name='ring')
    for i in range(5):
        a0 = 3.2 + i * 0.95
        w, d = (0.7, 0.25) if i % 2 == 0 else (0.25, 0.7)
        m.axial('iron_lo', a0, a0 + 1.0, w, d, name=f'link_{i}')
    m.axial('brass_lo', 7.9, 8.4, 2.4, 2.4, name='pot_foot')
    m.axial('pot', 8.4, 11.6, 3.2, 3.2, faces={'up': 'brass', 'down': 'brass'}, name='pot')
    m.axial('brass', 11.6, 12.3, 2.2, 2.2, name='lid')
    m.axial('brass_lo', 12.3, 13.1, 0.9, 0.9, name='finial')
    m.axial('ember', 13.1, 13.4, 0.5, 0.5, name='finial_ember')
    m.write('chandlers_censer', HANDHELD)


def _bow(m, drawn):
    """Limbs arc toward -x (forward, toward the target under vanilla's bow display transforms; checked
    in-game) as stepped segments; the string runs along the axis behind them. A drawn bow pulls the
    string back to the cheek with an arrow nocked across the grip, red-fletched at the string."""
    reach = 7.4
    segs = [(-7.4, -6.0), (-6.0, -4.6), (-4.6, -3.2), (-3.2, -1.6), (1.6, 3.2), (3.2, 4.6), (4.6, 6.0), (6.0, 7.4)]
    bend = 0.6 if drawn else 0.0
    for a0, a1 in segs:
        mid = (a0 + a1) / 2
        x = -3.0 + 2.5 * (mid / reach) ** 2 + bend * (abs(mid) / reach) ** 3
        m.axial('wood', a0, a1, 0.8, 0.8, x=x, faces={'east': 'wood_lo'}, name='limb')
    m.axial('leather', -1.6, 1.6, 1.2, 1.1, x=-3.1, name='grip')
    for a in (-7.4, 7.4):
        m.axial('bone', a - 0.3 if a < 0 else a, a if a < 0 else a + 0.3, 0.9, 0.9, x=-0.5 + bend, name='nock')
    if not drawn:
        m.axial('string', -7.2, 7.2, 0.2, 0.2, x=-0.3, name='string')
    else:
        pull = 1.6
        steps = 6
        for side in (-1, 1):
            for i in range(steps):
                a0 = side * 7.2 * (1 - i / steps)
                a1 = side * 7.2 * (1 - (i + 1) / steps)
                x = 0.0 + pull * (i + 0.5) / steps
                m.axial('string', min(a0, a1), max(a0, a1), 0.2, 0.2, x=x, name='string')
        gx, gy, gz = GRIP_BOW
        m.box('wood', (gx - 5.8, gy - 0.12, gz - 0.12), (gx + pull, gy + 0.12, gz + 0.12), name='arrow_shaft')
        m.box('steel_hi', (gx - 6.8, gy - 0.3, gz - 0.1), (gx - 5.8, gy + 0.3, gz + 0.1), name='arrowhead')
        m.box('feather', (gx + pull - 1.4, gy - 0.45, gz - 0.05), (gx + pull - 0.2, gy + 0.45, gz + 0.05), name='fletching')


GRIP_BOW = (8.0, 8.0, 8.0)


def hunting_bow():
    t = atlas(24, 'item/hunting_bow_3d.png', COMMON() + [('wood', 1, 8, wood((140, 100, 60))), ('wood_lo', 1, 8, wood((96, 68, 42))),
                                                         ('string', 1, 16, string), ('bone', 1, 1, bone),
                                                         ('feather', 1, 1, lambda x, y, w, h, r: (176, 48, 40))])
    for drawn, name in ((False, 'hunting_bow'), (True, 'hunting_bow_drawn')):
        m = Model(t, 'vestige:item/hunting_bow_3d', GRIP_BOW, -45)
        _bow(m, drawn)
        m.write(name, BOW_DISPLAY)


def handbell():
    t = atlas(25, 'item/handbell_3d.png', COMMON() + [('bronze', 4, 6, bronze((184, 128, 64))), ('bronze_lo', 3, 4, bronze((128, 84, 40))),
                                                      ('wood', 1, 8, wood((110, 76, 48)))])
    m = Model(t, 'vestige:item/handbell_3d', GRIP, -45)
    m.axial('brass', -3.2, -2.6, 1.3, 1.3, name='knob')
    m.axial('wood', -2.6, 2.4, 1.0, 1.0, name='handle')
    m.axial('leather', -1.4, 1.0, 1.15, 1.15, name='wrap')
    m.axial('brass', 2.4, 3.1, 1.5, 1.5, name='collar')
    m.axial('bronze', 3.1, 3.9, 2.0, 2.0, name='crown')
    m.axial('bronze', 3.9, 6.3, 2.8, 2.8, name='waist')
    m.axial('bronze_lo', 5.2, 5.6, 2.95, 2.95, name='band')
    m.axial('bronze', 6.3, 7.2, 3.6, 3.6, faces={'up': 'bronze_lo'}, name='lip')
    m.axial('iron_lo', 7.2, 7.8, 0.9, 0.9, name='clapper')
    m.write('handbell', HANDHELD)


def iron_kite_shield():
    t = atlas(26, 'item/iron_kite_shield_3d.png', COMMON() + [('face', 10, 6, kite_face)])
    m = Model(t, 'vestige:item/iron_kite_shield_3d')
    z0, z1 = 7.4, 8.4
    rows = [(3, 13, 10, 14), (3.5, 12.5, 7, 10), (4.5, 11.5, 4, 7), (5.5, 10.5, 1.5, 4), (6.5, 9.5, -0.5, 1.5), (7.4, 8.6, -1.6, -0.5)]
    for i, (x0, x1, y0, y1) in enumerate(rows):
        m.box('face', (x0, y0, z0), (x1, y1, z1), faces={'north': 'iron_lo'}, name=f'face_{i}')
    m.box('steel_hi', (2.8, 13.8, z1), (13.2, 14.4, z1 + 0.3), name='rim_top')
    m.box('steel', (2.8, 9.8, z1), (3.4, 13.8, z1 + 0.3), name='rim_l')
    m.box('steel', (12.6, 9.8, z1), (13.2, 13.8, z1 + 0.3), name='rim_r')
    # Lamplighter emblem: a brass lamp with an ember flame.
    m.box('brass', (6.8, 6.0, z1), (9.2, 9.0, z1 + 0.4), name='lamp')
    m.box('brass_lo', (7.2, 5.4, z1), (8.8, 6.0, z1 + 0.35), name='lamp_base')
    m.box('brass_lo', (7.4, 9.0, z1), (8.6, 9.6, z1 + 0.35), name='lamp_cap')
    m.box('ember', (7.6, 9.6, z1), (8.4, 11.0, z1 + 0.3), name='flame')
    m.box('leather', (6.5, 4.0, z0 - 0.8), (9.5, 11.5, z0), skip=('south',), name='strap')
    m.write('iron_kite_shield', shield_display(0.85, 0.7, tp_translate=(0, 1, 3)))


def timber_bulwark():
    t = atlas(27, 'item/timber_bulwark_3d.png', [m for m in COMMON() if m[0] in ('steel_hi', 'steel_lo', 'leather', 'iron', 'iron_lo', 'brass')]
              + [('planks', 10, 16, planks)])
    m = Model(t, 'vestige:item/timber_bulwark_3d')
    z0, z1 = 7.0, 9.0
    m.box('planks', (2, -4, z0), (14, 20, z1), faces={'north': 'iron_lo'}, name='wall')
    for y in (-2.5, 7.5, 17.5):
        m.box('iron', (1.8, y, z1), (14.2, y + 1.2, z1 + 0.3), name='band')
    m.box('iron_lo', (1.6, -4.2, z0 - 0.1), (2.2, 20.2, z1 + 0.2), name='edge_l')
    m.box('iron_lo', (13.8, -4.2, z0 - 0.1), (14.4, 20.2, z1 + 0.2), name='edge_r')
    m.box('steel_lo', (5.5, 13.2, z1), (10.5, 14.2, z1 + 0.15), name='slit')
    for x in (4, 12):
        for y in (2.5, 12.5):
            m.box('brass', (x - 0.4, y - 0.4, z1), (x + 0.4, y + 0.4, z1 + 0.3), name='stud')
    m.box('iron', (7, 4, z1), (9, 6, z1 + 1.2), name='spike_base')
    m.box('steel_hi', (7.5, 4.5, z1 + 1.2), (8.5, 5.5, z1 + 2.0), name='spike')
    m.box('leather', (6.5, 2.0, z0 - 0.8), (9.5, 12.0, z0), skip=('south',), name='strap')
    m.write('timber_bulwark', shield_display(0.62, 0.55, tp_translate=(0, -1, 2), fp_translate=(2, -3, -3)))


if __name__ == '__main__':
    w = weapon_atlas()
    keepsake_blade(w)
    ash_pike(w)
    hand_maul(w)
    plank_buckler(buckler_atlas())
    taper_fang()
    wickiron_greatblade()
    chandlers_censer()
    hunting_bow()
    handbell()
    iron_kite_shield()
    timber_bulwark()
    for name in ('keepsake_blade', 'ash_pike', 'hand_maul', 'plank_buckler', 'taper_fang', 'wickiron_greatblade',
                 'chandlers_censer', 'handbell', 'iron_kite_shield', 'timber_bulwark'):
        item_model(name, f'vestige:item/{name}')
    # The bow switches to its drawn model while a shot is being drawn (vestige:draw, set on the client).
    item_model('hunting_bow', 'vestige:item/hunting_bow', overrides=[
        {'predicate': {'vestige:draw': 0.5}, 'model': 'vestige:item/hunting_bow_drawn'}])
    item_model('hunting_bow', 'vestige:item/hunting_bow', base='hunting_bow_drawn_3d', out='hunting_bow_drawn')
    print('item models written')
