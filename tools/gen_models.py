"""Generates VESTIGE GeckoLib entity models (Blockbench-compatible .geo.json), their textures
(painted per face on a box-UV atlas) and their keyframe animations (.animation.json).

    python3 -I tools/gen_models.py

Models are authored here as code so they stay reproducible; the output opens in Blockbench
(Bedrock / GeckoLib format) for hand refinement. Units are pixels (16 = 1 block), y up,
the face looks north (-Z).
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'vestige')


# ----------------------------------------------------------------------------- model building
class Model:
    def __init__(self, name, tex_w, tex_h):
        self.name, self.tw, self.th = name, tex_w, tex_h
        self.bones = []
        self.cubes = []  # (bone, cube dict, paint fn)
        self.cursor = [0, 0]
        self.row_h = 0

    def bone(self, name, parent, pivot):
        b = {'name': name, 'pivot': list(pivot)}
        if parent:
            b['parent'] = parent
        b['cubes'] = []
        self.bones.append(b)
        return name

    def _alloc(self, w, h, d):
        bw, bh = 2 * (int(round(d)) + int(round(w))), int(round(d)) + int(round(h))
        bw, bh = max(bw, 2), max(bh, 2)
        if self.cursor[0] + bw > self.tw:
            self.cursor = [0, self.cursor[1] + self.row_h]
            self.row_h = 0
        u, v = self.cursor
        self.cursor[0] += bw
        self.row_h = max(self.row_h, bh)
        if v + bh > self.th:
            raise SystemExit('texture atlas too small for ' + self.name)
        return u, v

    def cube(self, bone, origin, size, paint, inflate=0.0, mirror=False):
        w, h, d = size
        u, v = self._alloc(w, h, d)
        c = {'origin': list(origin), 'size': list(size), 'uv': [u, v]}
        if inflate:
            c['inflate'] = inflate
        if mirror:
            c['mirror'] = True
        next(b for b in self.bones if b['name'] == bone)['cubes'].append(c)
        self.cubes.append((c, paint))

    def geo(self):
        bones = []
        for b in self.bones:
            out = {k: v for k, v in b.items() if k != 'cubes'}
            if b['cubes']:
                out['cubes'] = b['cubes']
            bones.append(out)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + self.name, 'texture_width': self.tw, 'texture_height': self.th,
                            'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
            'bones': bones}]}

    def texture(self, seed):
        img = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        rnd = random.Random(seed)
        for c, paint in self.cubes:
            w, h, d = [int(round(x)) for x in c['size']]
            w, h, d = max(w, 1), max(h, 1), max(d, 1)
            u, v = c['uv']
            faces = {
                'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
                'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
                'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
            }
            for face, (fx, fy, fw, fh) in faces.items():
                for y in range(fh):
                    for x in range(fw):
                        col = paint(face, x, y, fw, fh, rnd)
                        if col is not None and 0 <= fx + x < self.tw and 0 <= fy + y < self.th:
                            img.putpixel((fx + x, fy + y), col)
        return img


def noisy(rnd, base, amt=9):
    d = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, c + d)) for c in base[:3]) + (255,)


def solid(base, amt=9, shade_down=True):
    """Plain material with noise; darker underside and lower edge for volume."""
    def paint(face, x, y, w, h, rnd):
        c = list(base)
        if face == 'down':
            c = [int(v * 0.72) for v in c]
        elif face == 'up':
            c = [min(255, int(v * 1.08)) for v in c]
        elif shade_down and h > 2:
            f = 1.0 - 0.18 * (y / (h - 1))
            c = [int(v * f) for v in c]
        return noisy(rnd, c, amt)
    return paint


def tattered(base, holes=0.3, amt=10):
    """Cloth whose lower edge is torn (transparent pixels near the bottom)."""
    inner = solid(base, amt)

    def paint(face, x, y, w, h, rnd):
        if face not in ('up',) and h > 3 and y >= h - 3 and rnd.random() < holes * (y - (h - 4)) / 3.0:
            return None
        if rnd.random() < 0.06:
            return noisy(rnd, [int(v * 0.8) for v in base], amt)
        return inner(face, x, y, w, h, rnd)
    return paint


# ----------------------------------------------------------------------------- palettes
ASH = (150, 146, 138)
ASH_DARK = (112, 108, 101)
ROBE = (98, 88, 76)
ROBE_DARK = (64, 58, 52)
ROPE = (140, 116, 74)
BONE = (205, 198, 180)
EYE = (196, 214, 222, 255)
IRON = (96, 98, 104)
RUST = (130, 72, 44)
RED = (124, 40, 32)


def hollow_face(skin, glow):
    base = solid(skin, 8)

    def paint(face, x, y, w, h, rnd):
        if face != 'north':
            return base(face, x, y, w, h, rnd)
        # Sunken features: two hollow sockets with a pale glint, a gaunt mouth line.
        ex = (1, 2) if w >= 7 else (1,)
        if y in (2, 3) and (x in ex or x in tuple(w - 1 - e for e in ex)):
            if y == 3 and (x == ex[-1] or x == w - 1 - ex[-1]):
                return glow
            return (16, 14, 18, 255)
        if y == 5 and 2 <= x <= w - 3:
            return (38, 32, 34, 255)
        if y == 1 and x in (2, w - 3):
            return noisy(rnd, [int(c * 0.75) for c in skin], 4)
        return base(face, x, y, w, h, rnd)
    return paint


def hood(cloth):
    base = tattered(cloth, holes=0.15)

    def paint(face, x, y, w, h, rnd):
        if face == 'north' and 1 <= x <= w - 2 and y >= 2:
            return None  # open face of the hood
        if face == 'down':
            return None
        return base(face, x, y, w, h, rnd)
    return paint


def rope_belt(face, x, y, w, h, rnd):
    if face in ('up', 'down'):
        return noisy(rnd, ROBE_DARK)
    return noisy(rnd, ROPE if (x + y) % 3 else [int(c * 0.8) for c in ROPE], 6)


def claw(face, x, y, w, h, rnd):
    return noisy(rnd, BONE if y < h - 1 else (230, 226, 210), 6)


# ----------------------------------------------------------------------------- the Hollowed
def hollowed():
    m = Model('hollowed', 128, 128)
    m.bone('root', None, (0, 0, 0))
    for side, sx in (('right', -1), ('left', 1)):
        x0 = -3.5 if side == 'right' else 0.5
        m.bone(side + '_leg', 'root', (2 * sx, 12, 0))
        m.cube(side + '_leg', (x0, 6, -1.5), (3, 6, 3), solid(ROBE_DARK), mirror=side == 'left')
        m.bone(side + '_shin', side + '_leg', (2 * sx, 6, 0))
        m.cube(side + '_shin', (x0, 0, -1.5), (3, 6, 3), solid(ASH_DARK), mirror=side == 'left')
        m.cube(side + '_shin', (x0, 0, -2.5), (3, 1, 1), solid(ASH_DARK))
    m.bone('hips', 'root', (0, 12, 0))
    m.cube('hips', (-4, 11, -2), (8, 2, 4), rope_belt)
    m.bone('robe_front', 'hips', (0, 12, -2))
    m.cube('robe_front', (-3.5, 3, -2.5), (7, 9, 1), tattered(ROBE, 0.45))
    m.bone('robe_back', 'hips', (0, 12, 2))
    m.cube('robe_back', (-4, 2, 1.5), (8, 10, 1), tattered(ROBE, 0.5))
    m.bone('torso', 'hips', (0, 12, 0))
    m.cube('torso', (-4, 12, -2), (8, 11, 4), tattered(ROBE, 0.0))
    m.cube('torso', (-3, 19, 1.5), (6, 4, 2), solid(ROBE_DARK))          # hunchback ridge
    m.bone('head', 'torso', (0, 23, -1))
    m.cube('head', (-3.5, 23, -4.5), (7, 7, 7), hollow_face(ASH, EYE))
    m.cube('head', (-4, 22.5, -5), (8, 8.5, 8), hood(ROBE_DARK), inflate=0.2)
    m.cube('head', (-2, 23.5, 3), (4, 4, 3), tattered(ROBE_DARK, 0.3))   # hood tail
    for side, sx in (('right', -1), ('left', 1)):
        x0 = -7 if side == 'right' else 4
        m.bone(side + '_arm', 'torso', (5.5 * sx, 22, 0))
        m.cube(side + '_arm', (x0, 15, -1.5), (3, 8, 3), tattered(ROBE, 0.4), mirror=side == 'left')
        m.bone(side + '_forearm', side + '_arm', (5.5 * sx, 15, 0))
        m.cube(side + '_forearm', (x0 + 0.25, 7, -1.25), (2.5, 8, 2.5), solid(ASH), mirror=side == 'left')
        for i, fx in enumerate((x0, x0 + 1, x0 + 2)):
            m.cube(side + '_forearm', (fx, 4.5 - (i % 2), -1.5), (1, 2.5 + (i % 2), 1), claw)
    return m


# ----------------------------------------------------------------------------- the Hollowed Brute
def brute():
    m = Model('hollowed_brute', 128, 128)
    m.bone('root', None, (0, 0, 0))
    for side, sx in (('right', -1), ('left', 1)):
        x0 = -5 if side == 'right' else 1
        m.bone(side + '_leg', 'root', (3 * sx, 15, 0))
        m.cube(side + '_leg', (x0, 8, -2), (4, 7, 4), solid(ROBE_DARK), mirror=side == 'left')
        m.bone(side + '_shin', side + '_leg', (3 * sx, 8, 0))
        m.cube(side + '_shin', (x0, 0, -2), (4, 8, 4), solid(ASH_DARK), mirror=side == 'left')
        m.cube(side + '_shin', (x0 - 0.5, 0, -3), (5, 2, 5), solid(IRON))  # iron-wrapped feet
    m.bone('hips', 'root', (0, 15, 0))
    m.cube('hips', (-6, 13, -3), (12, 3, 6), solid(IRON))
    m.bone('robe_front', 'hips', (0, 15, -3))
    m.cube('robe_front', (-4, 5, -3.5), (8, 10, 1), tattered(RED, 0.5))
    m.bone('robe_back', 'hips', (0, 15, 3))
    m.cube('robe_back', (-5, 6, 2.5), (10, 9, 1), tattered(RED, 0.5))
    m.bone('torso', 'hips', (0, 15, 0))
    m.cube('torso', (-6, 15, -3), (12, 13, 6), solid(ASH_DARK))
    m.cube('torso', (-5, 24, 2.5), (10, 5, 3), solid(ASH_DARK))         # hulking back
    m.cube('torso', (-4.5, 27, -4), (9, 2, 8), solid(RUST, 12))          # iron collar
    m.bone('head', 'torso', (0, 28, -1))
    m.cube('head', (-3, 28, -4), (6, 6, 6), hollow_face(ASH_DARK, (224, 120, 90, 255)))
    m.cube('head', (-3.5, 27.5, -4.5), (7, 7.5, 7), bucket_helm, inflate=0.15)
    for side, sx in (('right', -1), ('left', 1)):
        x0 = -10 if side == 'right' else 6
        m.bone(side + '_arm', 'torso', (8 * sx, 27, 0))
        m.cube(side + '_arm', (x0, 18, -2), (4, 9, 4), solid(ASH), mirror=side == 'left')
        m.cube(side + '_arm', (x0 - 0.5, 24, -2.5), (5, 4, 5), solid(RUST, 12))  # shoulder plate
        m.bone(side + '_forearm', side + '_arm', (8 * sx, 18, 0))
        m.cube(side + '_forearm', (x0, 9, -2), (4, 9, 4), solid(ASH), mirror=side == 'left')
        m.cube(side + '_forearm', (x0 - 0.5, 12, -2.5), (5, 2, 5), solid(IRON))      # manacle
        m.cube(side + '_forearm', (x0 - 0.5, 4, -2.5), (5, 5, 5), solid(ASH_DARK))   # huge fist
        m.cube(side + '_forearm', (x0 + 1.5, 6, 2.5), (1, 6, 1), solid(IRON, 14))   # dangling chain
    return m


def bucket_helm(face, x, y, w, h, rnd):
    if face == 'north' and y in (2, 3) and 1 <= x <= w - 2:
        return None if y == 2 else (20, 14, 12, 255)   # eye slit
    if face == 'down':
        return None
    base = RUST if (x * 3 + y * 5) % 7 else IRON
    if y == 0 or y == h - 1:
        base = IRON
    return noisy(rnd, base, 12)


# ----------------------------------------------------------------------------- animations
def kf(t, rot, easing='easeInOutSine'):
    return t, {'vector': rot, 'easing': easing}


def channel(*frames):
    return {('%.2f' % t): v for t, v in frames}


def hollowed_animations(heavy):
    """Idle breathing, shambling walk and a death collapse. Rotations in degrees (Bedrock).
    `heavy` slows and deepens everything for the Brute."""
    s = 1.35 if heavy else 1.0
    idle_len = 3.2 * s
    walk_len = 1.1 * s
    anims = {
        'animation.hollowed.idle': {'loop': True, 'animation_length': idle_len, 'bones': {
            'torso': {'rotation': channel(kf(0, [10, 0, 0]), kf(idle_len / 2, [14, 0, 1.5]), kf(idle_len, [10, 0, 0]))},
            'head': {'rotation': channel(kf(0, [14, 0, 0]), kf(idle_len * 0.35, [18, -6, 2]), kf(idle_len * 0.75, [12, 5, -2]),
                                         kf(idle_len, [14, 0, 0]))},
            'right_arm': {'rotation': channel(kf(0, [-8, 0, 4]), kf(idle_len / 2, [-12, 0, 6]), kf(idle_len, [-8, 0, 4]))},
            'left_arm': {'rotation': channel(kf(0, [-6, 0, -4]), kf(idle_len / 2, [-10, 0, -6]), kf(idle_len, [-6, 0, -4]))},
            'right_forearm': {'rotation': channel(kf(0, [-10, 0, 0]), kf(idle_len / 2, [-16, 0, 0]), kf(idle_len, [-10, 0, 0]))},
            'left_forearm': {'rotation': channel(kf(0, [-12, 0, 0]), kf(idle_len / 2, [-18, 0, 0]), kf(idle_len, [-12, 0, 0]))},
            'robe_front': {'rotation': channel(kf(0, [-3, 0, 0]), kf(idle_len / 2, [-6, 0, 0]), kf(idle_len, [-3, 0, 0]))},
            'robe_back': {'rotation': channel(kf(0, [4, 0, 0]), kf(idle_len / 2, [7, 0, 0]), kf(idle_len, [4, 0, 0]))},
            'right_shin': {'rotation': channel(kf(0, [8, 0, 0]), kf(idle_len, [8, 0, 0]))},
            'left_shin': {'rotation': channel(kf(0, [8, 0, 0]), kf(idle_len, [8, 0, 0]))},
            'right_leg': {'rotation': channel(kf(0, [-6, 0, 0]), kf(idle_len, [-6, 0, 0]))},
            'left_leg': {'rotation': channel(kf(0, [-6, 0, 0]), kf(idle_len, [-6, 0, 0]))},
        }},
        'animation.hollowed.walk': {'loop': True, 'animation_length': walk_len, 'bones': {
            'right_leg': {'rotation': channel(kf(0, [28, 0, 0]), kf(walk_len / 2, [-28, 0, 0]), kf(walk_len, [28, 0, 0]))},
            'left_leg': {'rotation': channel(kf(0, [-28, 0, 0]), kf(walk_len / 2, [28, 0, 0]), kf(walk_len, [-28, 0, 0]))},
            'right_shin': {'rotation': channel(kf(0, [5, 0, 0]), kf(walk_len * 0.25, [35, 0, 0]), kf(walk_len / 2, [10, 0, 0]),
                                               kf(walk_len, [5, 0, 0]))},
            'left_shin': {'rotation': channel(kf(0, [10, 0, 0]), kf(walk_len * 0.75, [35, 0, 0]), kf(walk_len, [10, 0, 0]))},
            'hips': {'position': channel(kf(0, [0, 0, 0]), kf(walk_len * 0.25, [0, -0.8, 0]), kf(walk_len / 2, [0, 0, 0]),
                                         kf(walk_len * 0.75, [0, -0.8, 0]), kf(walk_len, [0, 0, 0])),
                     'rotation': channel(kf(0, [0, 6, 0]), kf(walk_len / 2, [0, -6, 0]), kf(walk_len, [0, 6, 0]))},
            'torso': {'rotation': channel(kf(0, [16, -4, 2]), kf(walk_len / 2, [16, 4, -2]), kf(walk_len, [16, -4, 2]))},
            'head': {'rotation': channel(kf(0, [10, 4, 0]), kf(walk_len / 2, [10, -4, 0]), kf(walk_len, [10, 4, 0]))},
            # Arms hang and swing late, like dead weight.
            'right_arm': {'rotation': channel(kf(0, [-22, 0, 6]), kf(walk_len / 2, [8, 0, 6]), kf(walk_len, [-22, 0, 6]))},
            'left_arm': {'rotation': channel(kf(0, [8, 0, -6]), kf(walk_len / 2, [-22, 0, -6]), kf(walk_len, [8, 0, -6]))},
            'right_forearm': {'rotation': channel(kf(0, [-20, 0, 0]), kf(walk_len / 2, [-8, 0, 0]), kf(walk_len, [-20, 0, 0]))},
            'left_forearm': {'rotation': channel(kf(0, [-8, 0, 0]), kf(walk_len / 2, [-20, 0, 0]), kf(walk_len, [-8, 0, 0]))},
            'robe_front': {'rotation': channel(kf(0, [-10, 0, 0]), kf(walk_len / 2, [6, 0, 0]), kf(walk_len, [-10, 0, 0]))},
            'robe_back': {'rotation': channel(kf(0, [12, 0, 0]), kf(walk_len / 2, [4, 0, 0]), kf(walk_len, [12, 0, 0]))},
        }},
        'animation.hollowed.death': {'loop': 'hold_on_last_frame', 'animation_length': 1.4, 'bones': {
            'root': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.25, [-8, 0, 0], 'easeOutQuad'), kf(0.55, [10, 0, 4], 'easeInQuad'),
                                         kf(1.05, [84, 0, 6], 'easeInCubic'), kf(1.25, [78, 0, 6], 'easeOutQuad'),
                                         kf(1.4, [82, 0, 6], 'easeInOutSine')),
                     'position': channel(kf(0, [0, 0, 0]), kf(0.55, [0, -2, 0]), kf(1.05, [0, -3, -2], 'easeInCubic'),
                                         kf(1.4, [0, -3, -2]))},
            'right_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.55, [55, 0, 0], 'easeOutQuad'), kf(1.4, [20, 0, 0]))},
            'left_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.55, [60, 0, 0], 'easeOutQuad'), kf(1.4, [25, 0, 0]))},
            'right_leg': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.55, [-40, 0, 0], 'easeOutQuad'), kf(1.4, [-10, 0, 0]))},
            'left_leg': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.55, [-35, 0, 0], 'easeOutQuad'), kf(1.4, [-12, 0, 0]))},
            'torso': {'rotation': channel(kf(0, [10, 0, 0]), kf(0.25, [-15, 0, 0], 'easeOutQuad'), kf(0.55, [30, 0, 0]),
                                          kf(1.4, [20, 0, 0]))},
            'head': {'rotation': channel(kf(0, [14, 0, 0]), kf(0.25, [-25, 0, 0], 'easeOutQuad'), kf(1.05, [20, 10, 0]),
                                         kf(1.4, [15, 25, 0]))},
            'right_arm': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.3, [-60, 0, 30], 'easeOutQuad'), kf(1.05, [-150, 0, 20], 'easeInQuad'),
                                              kf(1.4, [-160, 0, 25]))},
            'left_arm': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.3, [-50, 0, -30], 'easeOutQuad'), kf(1.05, [-140, 0, -25], 'easeInQuad'),
                                             kf(1.4, [-155, 0, -30]))},
        }},
    }
    if heavy:
        anims = {k.replace('hollowed', 'hollowed_brute'): v for k, v in anims.items()}
    return {'format_version': '1.8.0', 'animations': anims}


# ----------------------------------------------------------------------------- the Tolling Ram
WOOL = (214, 204, 182)
WOOL_DARK = (168, 156, 134)
FACE = (58, 52, 48)
HORN = (122, 104, 80)
HORN_DARK = (84, 70, 54)
HORN_TIP = (214, 200, 168)
HOOF = (40, 36, 34)
BRONZE = (196, 138, 62)
LEATHER_COL = (92, 60, 38)


def wool(base=WOOL):
    """Matted fleece: clumps of lighter and darker locks, ragged underside."""
    def paint(face, x, y, w, h, rnd):
        if face == 'down':
            return noisy(rnd, [int(c * 0.7) for c in base], 6)
        clump = ((x // 2) + (y // 2) * 3) % 4
        f = (1.06, 0.94, 1.0, 0.88)[clump]
        if face == 'up':
            f += 0.06
        elif h > 3 and y >= h - 2:
            f -= 0.1
        return noisy(rnd, [min(255, int(c * f)) for c in base], 7)
    return paint


def horn(face, x, y, w, h, rnd):
    ridge = (x + y) % 3 == 0
    return noisy(rnd, HORN_DARK if ridge else HORN, 6)


def horn_tip(face, x, y, w, h, rnd):
    return noisy(rnd, HORN_TIP if (x + y) % 3 else HORN, 6)


def ram_face(face, x, y, w, h, rnd):
    if face == 'north':
        if y == 1 and x in (0, w - 1):
            return (232, 176, 60, 255)      # amber eyes set wide
        if y == h - 1 and 1 <= x <= w - 2:
            return (30, 26, 24, 255)
    return noisy(rnd, FACE, 6)


def bell(face, x, y, w, h, rnd):
    if face == 'down':
        return (34, 26, 18, 255)            # the mouth of the bell
    if face == 'up':
        return noisy(rnd, [int(c * 0.85) for c in BRONZE], 6)
    if y in (1, h - 2):
        return noisy(rnd, [int(c * 0.72) for c in BRONZE], 6)   # engraved bands
    if x == 1 and 1 < y < h - 2:
        return (246, 206, 132, 255)                              # polished highlight
    return noisy(rnd, BRONZE, 8)


def tolling_ram():
    m = Model('tolling_ram', 128, 128)
    m.bone('root', None, (0, 0, 0))
    m.bone('body', 'root', (0, 13, 0))
    m.cube('body', (-5.5, 9, -8.5), (11, 10, 17), wool())
    m.cube('body', (-6, 16, -7.5), (12, 3.5, 14), wool((226, 218, 198)), inflate=0.25)   # fleece ridge
    m.cube('body', (-4.5, 8, -6.5), (9, 1.5, 12), wool(WOOL_DARK))                       # shaggy belly
    m.bone('tail', 'body', (0, 17, 8.5))
    m.cube('tail', (-1.5, 13.5, 8), (3, 4, 2), wool())
    for side, sx in (('right', -1), ('left', 1)):
        for end, z in (('front', -5.5), ('hind', 5.5)):
            leg = f'{end}_{side}_leg'
            x0 = 1.5 if side == 'left' else -4.5
            m.bone(leg, 'body', (3 * sx, 10, z))
            m.cube(leg, (x0, 4.5, z - 1.5), (3, 6, 3), wool(WOOL_DARK), mirror=side == 'left')
            m.bone(f'{end}_{side}_shin', leg, (3 * sx, 4.5, z))
            m.cube(f'{end}_{side}_shin', (x0 + 0.25, 1, z - 1.25), (2.5, 3.5, 2.5), solid(FACE), mirror=side == 'left')
            m.cube(f'{end}_{side}_shin', (x0, 0, z - 1.5), (3, 1, 3), solid(HOOF))
    m.bone('neck', 'body', (0, 15.5, -7.5))
    m.cube('neck', (-3, 13, -12), (6, 7, 6), wool())
    m.bone('bell', 'neck', (0, 13.5, -10.5))
    m.cube('bell', (-3.2, 12.6, -12.4), (6.4, 1.4, 6.6), solid(LEATHER_COL))             # collar
    m.cube('bell', (-0.6, 11.4, -13.6), (1.2, 1.4, 1.2), solid(IRON))                     # hanger
    m.cube('bell', (-2.2, 7.4, -14.4), (4.4, 4.2, 4.2), bell)                              # the bell
    m.cube('bell', (-2.7, 6.8, -14.9), (5.4, 0.8, 5.2), bell)                              # flared lip
    m.cube('bell', (-0.6, 6.0, -12.9), (1.2, 1.0, 1.2), solid(IRON))                       # clapper
    m.bone('head', 'neck', (0, 19, -11))
    m.cube('head', (-3, 15.5, -17.5), (6, 6, 7), ram_face)
    m.cube('head', (-2, 15.5, -19.5), (4, 3.5, 2), solid(FACE))                           # muzzle
    m.cube('head', (-3.3, 20.5, -16.5), (6.6, 2, 5.5), wool((226, 218, 198)), inflate=0.1)  # forelock
    for side, sx in (('right', -1), ('left', 1)):
        # Ears and heavy ridged horns curling back, out and down past the jaw, pale at the tips.
        m.cube('head', (3 if sx > 0 else -4.5, 18.5, -14.5), (1.5, 1, 2.5), solid(FACE))
        hx = 2.4 if sx > 0 else -5.4
        spiral = [((hx, 20.0, -15.5), (3, 3, 3), horn), ((hx + 1.8 * sx, 19.6, -13.0), (3, 3.4, 3), horn),
                  ((hx + 2.8 * sx, 16.4, -11.8), (3, 3.6, 3), horn), ((hx + 2.4 * sx, 13.6, -13.6), (3, 3, 3), horn),
                  ((hx + 1.4 * sx, 13.4, -16.2), (2.4, 2.4, 2.6), horn), ((hx + 0.8 * sx, 14.2, -18.0), (1.8, 1.8, 1.8), horn_tip)]
        for origin, size, paint in spiral:
            m.cube('head', origin, size, paint, mirror=side == 'left')
    return m


def ram_animations():
    idle_len, walk_len = 3.6, 0.9
    a = 'animation.tolling_ram.'
    return {'format_version': '1.8.0', 'animations': {
        a + 'idle': {'loop': True, 'animation_length': idle_len, 'bones': {
            'body': {'position': channel(kf(0, [0, 0, 0]), kf(idle_len / 2, [0, 0.3, 0]), kf(idle_len, [0, 0, 0]))},
            'neck': {'rotation': channel(kf(0, [0, 0, 0]), kf(idle_len * 0.3, [6, 4, 0]), kf(idle_len * 0.55, [14, -2, 0]),
                                         kf(idle_len * 0.8, [4, -5, 0]), kf(idle_len, [0, 0, 0]))},
            'head': {'rotation': channel(kf(0, [0, 0, 0]), kf(idle_len * 0.55, [10, 0, 3]), kf(idle_len, [0, 0, 0]))},
            'bell': {'rotation': channel(kf(0, [0, 0, 4]), kf(idle_len * 0.3, [0, 0, -5]), kf(idle_len * 0.6, [0, 0, 4]),
                                         kf(idle_len, [0, 0, 4]))},
            'tail': {'rotation': channel(kf(0, [10, 0, 0]), kf(idle_len * 0.1, [10, 0, 18]), kf(idle_len * 0.2, [10, 0, -14]),
                                         kf(idle_len * 0.3, [10, 0, 0]), kf(idle_len, [10, 0, 0]))},
        }},
        # Diagonal pairs (front-right with hind-left), a bobbing head and a swinging bell.
        a + 'walk': {'loop': True, 'animation_length': walk_len, 'bones': {
            'front_right_leg': {'rotation': channel(kf(0, [-26, 0, 0]), kf(walk_len / 2, [24, 0, 0]), kf(walk_len, [-26, 0, 0]))},
            'hind_left_leg': {'rotation': channel(kf(0, [-24, 0, 0]), kf(walk_len / 2, [22, 0, 0]), kf(walk_len, [-24, 0, 0]))},
            'front_left_leg': {'rotation': channel(kf(0, [24, 0, 0]), kf(walk_len / 2, [-26, 0, 0]), kf(walk_len, [24, 0, 0]))},
            'hind_right_leg': {'rotation': channel(kf(0, [22, 0, 0]), kf(walk_len / 2, [-24, 0, 0]), kf(walk_len, [22, 0, 0]))},
            'front_right_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(walk_len * 0.75, [-30, 0, 0]), kf(walk_len, [0, 0, 0]))},
            'front_left_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(walk_len * 0.25, [-30, 0, 0]), kf(walk_len / 2, [0, 0, 0]),
                                                    kf(walk_len, [0, 0, 0]))},
            'body': {'position': channel(kf(0, [0, 0, 0]), kf(walk_len / 4, [0, 0.5, 0]), kf(walk_len / 2, [0, 0, 0]),
                                         kf(walk_len * 0.75, [0, 0.5, 0]), kf(walk_len, [0, 0, 0])),
                     'rotation': channel(kf(0, [0, 0, 2]), kf(walk_len / 2, [0, 0, -2]), kf(walk_len, [0, 0, 2]))},
            'neck': {'rotation': channel(kf(0, [4, 0, 0]), kf(walk_len / 4, [10, 0, 0]), kf(walk_len / 2, [4, 0, 0]),
                                         kf(walk_len * 0.75, [10, 0, 0]), kf(walk_len, [4, 0, 0]))},
            'bell': {'rotation': channel(kf(0, [12, 0, 6]), kf(walk_len / 2, [-10, 0, -6]), kf(walk_len, [12, 0, 6]))},
            'tail': {'rotation': channel(kf(0, [14, 0, 6]), kf(walk_len / 2, [14, 0, -6]), kf(walk_len, [14, 0, 6]))},
        }},
        # Knees buckle, the ram drops and rolls onto its side; the bell gives a last swing.
        a + 'death': {'loop': 'hold_on_last_frame', 'animation_length': 1.5, 'bones': {
            'root': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.35, [6, 0, 0], 'easeOutQuad'), kf(0.9, [4, 0, 84], 'easeInCubic'),
                                         kf(1.1, [4, 0, 78], 'easeOutQuad'), kf(1.5, [4, 0, 82])),
                     'position': channel(kf(0, [0, 0, 0]), kf(0.35, [0, -3, 0], 'easeOutQuad'), kf(0.9, [3, -4, 0], 'easeInCubic'),
                                         kf(1.5, [3, -4, 0]))},
            'front_right_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.35, [-70, 0, 0], 'easeOutQuad'), kf(1.5, [-20, 0, 0]))},
            'front_left_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.35, [-65, 0, 0], 'easeOutQuad'), kf(1.5, [-15, 0, 0]))},
            'hind_right_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.4, [60, 0, 0], 'easeOutQuad'), kf(1.5, [20, 0, 0]))},
            'hind_left_shin': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.4, [55, 0, 0], 'easeOutQuad'), kf(1.5, [15, 0, 0]))},
            'front_right_leg': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.9, [-20, 0, 0]), kf(1.5, [-30, 0, 0]))},
            'hind_right_leg': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.9, [20, 0, 0]), kf(1.5, [30, 0, 0]))},
            'neck': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.35, [-20, 0, 0], 'easeOutQuad'), kf(0.9, [20, 0, 0]),
                                         kf(1.5, [30, 10, 0]))},
            'bell': {'rotation': channel(kf(0, [0, 0, 0]), kf(0.9, [0, 0, 30]), kf(1.2, [0, 0, -20]), kf(1.5, [0, 0, 5]))},
        }},
    }}


# ----------------------------------------------------------------------------- the Ember-Ghost
def ember(alpha=200):
    """Charred cloth glowing ember-orange at the seams; partly see-through (translucent render)."""
    def paint(face, x, y, w, h, rnd):
        seam = (x + 2 * y) % 5 == 0 or (y == h - 1)
        if seam:
            c = (255, 160 + rnd.randint(0, 50), 60, alpha)
        elif rnd.random() < 0.1:
            c = (255, 210, 120, alpha)
        else:
            v = rnd.randint(-8, 8)
            c = (92 + v, 40 + v, 24 + v, alpha)
        return c
    return paint


def flame_tail(face, x, y, w, h, rnd):
    t = y / max(1, h - 1)
    if rnd.random() < t * 0.55:
        return None                                 # frays into nothing
    return (255, int(200 - 90 * t), int(90 - 60 * t), int(220 - 120 * t))


def ghost_hood(face, x, y, w, h, rnd):
    if face == 'north' and 1 <= x <= w - 2 and 1 <= y <= h - 2:
        if y == 3 and x in (2, w - 3):
            return (255, 236, 170, 255)              # two bright eyes in the dark
        return (18, 10, 8, 235)
    return ember(215)(face, x, y, w, h, rnd)


def ember_ghost():
    m = Model('ember_ghost', 64, 64)
    m.bone('root', None, (0, 0, 0))
    m.bone('body', 'root', (0, 14, 0))
    m.cube('body', (-3.5, 12, -2.5), (7, 9, 5), ember(205))
    m.cube('body', (-3, 9, -2), (6, 3, 4), ember(185))
    m.bone('tail', 'body', (0, 9, 0))
    m.cube('tail', (-2.5, 4, -1.5), (5, 5, 3), flame_tail)
    m.cube('tail', (-1.5, 0, -1), (3, 4, 2), flame_tail)
    m.bone('head', 'body', (0, 21, 0))
    m.cube('head', (-3.5, 21, -3.5), (7, 7, 7), ghost_hood)
    m.cube('head', (-1.5, 22, 3.5), (3, 4, 2), ember(190))   # hood tail
    for side, sx in (('right', -1), ('left', 1)):
        x0 = -6 if sx < 0 else 3.5
        m.bone(side + '_arm', 'body', (4.5 * sx, 20, 0))
        m.cube(side + '_arm', (x0, 13, -1), (2.5, 7, 2), ember(170), mirror=sx > 0)
        m.cube(side + '_arm', (x0 + 0.25, 10, -0.75), (2, 3, 1.5), flame_tail, mirror=sx > 0)
    return m


def ember_ghost_animations():
    n = 2.4
    return {'format_version': '1.8.0', 'animations': {
        'animation.ember_ghost.idle': {'loop': True, 'animation_length': n, 'bones': {
            'root': {'position': channel(kf(0, [0, 2, 0]), kf(n / 2, [0, 4, 0]), kf(n, [0, 2, 0]))},
            'body': {'rotation': channel(kf(0, [4, 0, 2]), kf(n / 2, [8, 0, -2]), kf(n, [4, 0, 2]))},
            'head': {'rotation': channel(kf(0, [12, -6, 0]), kf(n / 2, [16, 6, 0]), kf(n, [12, -6, 0]))},
            'tail': {'rotation': channel(kf(0, [8, 0, 6]), kf(n / 4, [14, 0, -6]), kf(n / 2, [8, 0, 6]),
                                         kf(n * 0.75, [14, 0, -6]), kf(n, [8, 0, 6]))},
            'right_arm': {'rotation': channel(kf(0, [-20, 0, 10]), kf(n / 2, [-28, 0, 16]), kf(n, [-20, 0, 10]))},
            'left_arm': {'rotation': channel(kf(0, [-24, 0, -12]), kf(n / 2, [-16, 0, -18]), kf(n, [-24, 0, -12]))},
        }},
    }}


# ----------------------------------------------------------------------------- armor sets (GeckoLib armor bones)
# Bones follow GeckoLib's armor convention; GeckoLib aligns each to the wearer's biped part.
ARMOR_BONES = {'armorHead': (0, 24, 0), 'armorBody': (0, 24, 0), 'armorRightArm': (-5, 22, 0), 'armorLeftArm': (5, 22, 0),
               'armorRightLeg': (-1.9, 12, 0), 'armorLeftLeg': (1.9, 12, 0), 'armorRightBoot': (-1.9, 12, 0),
               'armorLeftBoot': (1.9, 12, 0)}


def armor_model(name):
    m = Model('armor_' + name, 128, 128)
    for bone, pivot in ARMOR_BONES.items():
        m.bone(bone, None, pivot)
    return m


def both(m, bone_r, bone_l, origin_r, size, paint, mirror_x=0.0, inflate=0.0):
    """A cube on the right limb and its mirror on the left (x mirrored around the body centre)."""
    m.cube(bone_r, origin_r, size, paint, inflate=inflate)
    x, y, z = origin_r
    m.cube(bone_l, (-(x + size[0]), y, z), size, paint, inflate=inflate, mirror=True)


CLOTH_BROWN = (112, 86, 60)
CLOTH_DARK = (78, 60, 44)
WOOL_RED = (140, 48, 40)
LEATHER = (104, 70, 44)
BRASS_C = (186, 146, 66)
BRONZE_C = (186, 128, 62)
BRONZE_DARK = (122, 82, 40)
IRON_DARK = (62, 62, 70)
IRON_EDGE = (98, 98, 108)
WAX_C = (226, 214, 186)


def bronze_scales(face, x, y, w, h, rnd):
    scale = (x + (y // 2) % 2) % 2 == 0 and y % 2 == 0
    base = BRONZE_C if not scale else (212, 158, 84)
    if y == h - 1 or face == 'down':
        base = BRONZE_DARK
    return noisy(rnd, base, 8)


def bronze_plain(face, x, y, w, h, rnd):
    base = BRONZE_C
    if face == 'up':
        base = (212, 158, 84)
    elif y == h - 1 or x == 0:
        base = BRONZE_DARK
    return noisy(rnd, base, 7)


def bell_helm(face, x, y, w, h, rnd):
    if face == 'north' and y in (h // 2, h // 2 + 1) and 1 < x < w - 2:
        return (20, 14, 10, 255)                       # visor slit
    if face == 'down':
        return None
    return bronze_plain(face, x, y, w, h, rnd)


def iron_plate(face, x, y, w, h, rnd):
    base = IRON_DARK
    if y == 0 or x == 0 or x == w - 1:
        base = IRON_EDGE
    if rnd.random() < 0.05:
        base = (82, 82, 92)
    # Wax runs down from the top edge here and there.
    if face in ('north', 'south') and (x * 7) % 5 == 0 and y < 2 + (x % 3):
        base = WAX_C
    return noisy(rnd, base, 6)


def great_helm(face, x, y, w, h, rnd):
    if face == 'north' and y == h // 2 - 1 and 1 < x < w - 2:
        return (232, 140, 60, 255)                     # ember glow through the eye slit
    if face == 'north' and y == h // 2 and 1 < x < w - 2:
        return (16, 12, 10, 255)
    if face == 'down':
        return None
    return iron_plate(face, x, y, w, h, rnd)


def candle(face, x, y, w, h, rnd):
    return noisy(rnd, WAX_C if y > 0 or face != 'up' else (60, 44, 30), 5)


def flame(face, x, y, w, h, rnd):
    return (255, 214 if y == 0 else 150, 90, 255)


def wick_cord(face, x, y, w, h, rnd):
    return noisy(rnd, (52, 40, 30) if (x + y) % 2 else (236, 140, 54), 4)


def hood_open(face, x, y, w, h, rnd):
    if face == 'north' and 1 <= x <= w - 2 and 2 <= y:
        return None                                    # the face shows through
    if face == 'down':
        return None
    return tattered(CLOTH_BROWN, 0.1)(face, x, y, w, h, rnd)


def wanderer_armor():
    m = armor_model('wanderer')
    m.cube('armorHead', (-4.5, 23.5, -4.5), (9, 9, 9), hood_open, inflate=0.1)
    m.cube('armorHead', (-2, 28.5, 4), (4, 4, 3), tattered(CLOTH_BROWN, 0.25))           # hood peak drooping back
    m.cube('armorBody', (-4.5, 11, -2.5), (9, 13, 5), tattered(CLOTH_DARK, 0.15))          # coat
    m.cube('armorBody', (-4.9, 21.6, -2.9), (9.8, 2.6, 5.8), solid(WOOL_RED, 10))           # scarf
    m.cube('armorBody', (-2.5, 18.5, -3.2), (2.4, 4, 1), tattered(WOOL_RED, 0.3))          # scarf tail
    m.cube('armorBody', (-4.8, 12.4, -2.8), (9.6, 1.3, 5.6), solid(LEATHER, 8))           # belt
    m.cube('armorBody', (-0.8, 12.2, -3.1), (1.6, 1.7, 0.6), solid(BRASS_C, 6))           # buckle
    m.cube('armorBody', (1.6, 11.4, 2.4), (3.2, 3.4, 1.6), solid(LEATHER, 10))            # satchel
    m.bone('cape', 'armorBody', (0, 23.5, 2.6))
    m.cube('cape', (-4, 9, 2.7), (8, 14.5, 0.6), tattered(CLOTH_BROWN, 0.5))
    both(m, 'armorRightArm', 'armorLeftArm', (-8.5, 15, -2.5), (5, 9, 5), tattered(CLOTH_DARK, 0.1))
    both(m, 'armorRightArm', 'armorLeftArm', (-8.4, 11.6, -2.4), (4.8, 2.4, 4.8), solid(LEATHER, 8))   # wrapped forearms
    both(m, 'armorRightLeg', 'armorLeftLeg', (-4.2, 3, -2.3), (4.6, 9.5, 4.6), solid(CLOTH_BROWN, 9))
    both(m, 'armorRightLeg', 'armorLeftLeg', (-4.3, 5.5, -2.45), (4.8, 1.5, 4.9), solid(LEATHER, 8))   # knee wraps
    both(m, 'armorRightBoot', 'armorLeftBoot', (-4.3, -0.2, -2.6), (4.8, 4.2, 5.2), solid(LEATHER, 10))
    both(m, 'armorRightBoot', 'armorLeftBoot', (-4.5, 3.6, -2.8), (5.2, 1.2, 5.6), solid((84, 56, 36), 8))  # cuffs
    return m


def bellbronze_armor():
    m = armor_model('bellbronze')
    m.cube('armorHead', (-4.6, 24, -4.6), (9.2, 7.4, 9.2), bell_helm)
    m.cube('armorHead', (-5.3, 23, -5.3), (10.6, 1.6, 10.6), bronze_plain)               # flared bell lip
    m.cube('armorHead', (-0.6, 31, -4.4), (1.2, 2.2, 8.8), bronze_plain)                 # crest
    m.bone('clapper', 'armorHead', (0, 33, 0))
    m.cube('clapper', (-1, 33, -1), (2, 1.6, 2), solid((90, 92, 100), 6))                  # finial
    m.cube('armorBody', (-4.6, 11.6, -2.6), (9.2, 12.6, 5.2), bronze_scales)
    m.cube('armorBody', (-4.3, 22.4, -3.0), (8.6, 2, 6), bronze_plain)                    # gorget
    m.cube('armorBody', (-4.8, 12, -2.9), (9.6, 1.2, 5.8), solid((90, 92, 100), 6))        # chain belt
    both(m, 'armorRightArm', 'armorLeftArm', (-9.4, 20, -3.2), (5.8, 3.4, 6.4), bronze_plain)   # bell pauldron
    both(m, 'armorRightArm', 'armorLeftArm', (-9.8, 19, -3.6), (6.6, 1.2, 7.2), bronze_plain)   # pauldron lip
    both(m, 'armorRightArm', 'armorLeftArm', (-8.5, 11.5, -2.5), (5, 7.5, 5), bronze_scales)
    both(m, 'armorRightLeg', 'armorLeftLeg', (-4.4, 6.5, -2.8), (4.8, 5.6, 0.9), bronze_plain)   # tasset plate
    both(m, 'armorRightLeg', 'armorLeftLeg', (-4.2, 2.5, -2.4), (4.6, 9.6, 4.8), bronze_scales)
    both(m, 'armorRightBoot', 'armorLeftBoot', (-4.4, -0.2, -2.8), (5, 4.2, 5.4), bronze_plain)
    both(m, 'armorRightBoot', 'armorLeftBoot', (-4.2, -0.2, -3.6), (4.6, 2, 1), bronze_plain)    # toe cap
    return m


def wickiron_armor():
    m = armor_model('wickiron')
    m.cube('armorHead', (-4.8, 23.4, -4.8), (9.6, 9, 9.6), great_helm)
    m.bone('candles', 'armorHead', (0, 32.4, 0))
    for i, (cx, ch) in enumerate(((-3, 2.6), (-0.6, 3.6), (1.8, 2.2))):
        m.cube('candles', (cx, 32.4, -1), (1.2, ch, 1.2), candle)
        m.cube('candles', (cx + 0.35, 32.4 + ch, -0.65), (0.5, 0.9, 0.5), flame)
    m.cube('armorBody', (-4.8, 11, -2.9), (9.6, 13.4, 5.8), iron_plate)
    m.cube('armorBody', (-4.9, 15, -3.0), (9.8, 0.8, 6.0), wick_cord)                    # wick-cord bands
    m.cube('armorBody', (-4.9, 19, -3.0), (9.8, 0.8, 6.0), wick_cord)
    both(m, 'armorRightArm', 'armorLeftArm', (-10, 18.2, -3.6), (6.6, 5.2, 7.2), iron_plate)   # great pauldron
    both(m, 'armorRightArm', 'armorLeftArm', (-9.6, 16.4, -3.2), (5.8, 2, 6.4), iron_plate)    # second lame
    both(m, 'armorRightArm', 'armorLeftArm', (-8.6, 11.4, -2.6), (5.2, 5, 5.2), iron_plate)    # vambrace
    both(m, 'armorRightLeg', 'armorLeftLeg', (-4.4, 2.5, -2.6), (5, 10, 5.2), iron_plate)
    both(m, 'armorRightLeg', 'armorLeftLeg', (-4.2, 5.4, -3.2), (4.6, 2.2, 1), iron_plate)      # knee cop
    both(m, 'armorRightBoot', 'armorLeftBoot', (-4.5, -0.3, -2.9), (5.2, 5, 5.8), iron_plate)
    return m


def armor_animations(name):
    n = 2.8
    bones = {}
    if name == 'wanderer':
        bones['cape'] = {'rotation': channel(kf(0, [6, 0, 0]), kf(n / 2, [11, 0, 1.5]), kf(n, [6, 0, 0]))}
    elif name == 'bellbronze':
        bones['clapper'] = {'rotation': channel(kf(0, [0, 0, 6]), kf(n / 2, [0, 0, -6]), kf(n, [0, 0, 6]))}
    else:
        bones['candles'] = {'scale': channel(kf(0, [1, 1, 1]), kf(n * 0.3, [1, 1.04, 1]), kf(n * 0.6, [1, 0.98, 1]), kf(n, [1, 1, 1]))}
    return {'format_version': '1.8.0', 'animations': {'animation.armor.idle': {'loop': True, 'animation_length': n, 'bones': bones}}}


def write_json(rel, obj):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1)


def main():
    for model, seed, heavy in ((hollowed(), 7, False), (brute(), 11, True)):
        write_json('geo/entity/%s.geo.json' % model.name, model.geo())
        tex = model.texture(seed)
        path = os.path.join(ROOT, 'textures', 'entity', model.name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        tex.save(path)
        write_json('animations/entity/%s.animation.json' % model.name, hollowed_animations(heavy))
    ram = tolling_ram()
    write_json('geo/entity/tolling_ram.geo.json', ram.geo())
    ram.texture(17).save(os.path.join(ROOT, 'textures', 'entity', 'tolling_ram.png'))
    write_json('animations/entity/tolling_ram.animation.json', ram_animations())
    g = ember_ghost()
    write_json('geo/entity/ember_ghost.geo.json', g.geo())
    g.texture(23).save(os.path.join(ROOT, 'textures', 'entity', 'ember_ghost.png'))
    write_json('animations/entity/ember_ghost.animation.json', ember_ghost_animations())
    for name, builder, seed in (('wanderer', wanderer_armor, 31), ('bellbronze', bellbronze_armor, 32), ('wickiron', wickiron_armor, 33)):
        am = builder()
        write_json('geo/item/armor/%s.geo.json' % name, am.geo())
        path = os.path.join(ROOT, 'textures', 'item', 'armor', name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        am.texture(seed).save(path)
        write_json('animations/item/armor/%s.animation.json' % name, armor_animations(name))
    print('ok')


if __name__ == '__main__':
    main()
