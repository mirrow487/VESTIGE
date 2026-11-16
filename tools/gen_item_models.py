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


def item_model(name, gui_texture):
    """Top-level item model: the 3D model everywhere except the inventory and item frames."""
    flat = {'parent': 'minecraft:item/handheld', 'textures': {'layer0': gui_texture}}
    dump(f'models/item/{name}.json', {
        'loader': 'neoforge:separate_transforms',
        'gui_light': 'front',
        'base': {'parent': f'vestige:item/{name}_3d'},
        'perspectives': {'gui': flat, 'fixed': flat},
    })


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


if __name__ == '__main__':
    w = weapon_atlas()
    keepsake_blade(w)
    ash_pike(w)
    hand_maul(w)
    plank_buckler(buckler_atlas())
    for name in ('keepsake_blade', 'ash_pike', 'hand_maul', 'plank_buckler'):
        item_model(name, f'vestige:item/{name}')
    print('item models written')
