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
    print('ok')


if __name__ == '__main__':
    main()
