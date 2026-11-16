"""Generates VESTIGE combat datapack JSON (movesets, weapons, enemies, damage types, tags,
spawns, loot, recipes). Numbers follow GDD §7.4–§7.6 and §18. Edit here, then run:

    python3 -I tools/gen_combat_data.py

Output goes to src/main/resources/data (committed).
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data')


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def action(windup, active, recovery, pose, *, stamina=0, mv=1.0, poise=0, damage=0, hit=None, motion=None,
           telegraph=None, hyperarmor=False, charge=None, dodge=None, tracking=30, stagger=0,
           combo=0.4, cancel=0.4, hitstop=3):
    a = {
        'timing': {'windup': windup, 'active': active, 'recovery': recovery,
                   'combo_window': combo, 'cancel_window': cancel, 'hitstop': hitstop},
        'power': {'stamina': stamina, 'motion_value': mv, 'poise_damage': poise, 'damage': damage},
        'pose': pose,
    }
    if hyperarmor:
        a['hyperarmor'] = True
    if hit:
        a['hit'] = hit
    if motion:
        a['motion'] = motion
    if telegraph:
        a['telegraph'] = telegraph
    if charge:
        a['charge'] = charge
    if dodge:
        a['dodge'] = dodge
    if tracking != 30:
        a['tracking'] = tracking
    if stagger:
        a['inflict_stagger'] = stagger
    return a


def arc(reach, frm, to, y_max=2.2):
    return {'type': 'arc', 'reach': reach, 'from': frm, 'to': to, 'y_min': 0.0, 'y_max': y_max}


def thrust(reach, radius=0.45, y_min=0.2, y_max=2.0):
    return {'type': 'thrust', 'reach': reach, 'radius': radius, 'y_min': y_min, 'y_max': y_max}


def circle(radius, offset, y_max=1.6):
    return {'type': 'circle', 'radius': radius, 'offset': offset, 'y_min': -0.5, 'y_max': y_max}


def moveset(actions, light=None, heavy=None, running=None, rolling=None):
    m = {'actions': actions}
    if light:
        m['light'] = light
    if heavy:
        m['heavy'] = heavy
    if running:
        m['running'] = running
    if rolling:
        m['rolling'] = rolling
    return m


# ---------------------------------------------------------------- player
write('vestige/vestige/movesets/player.json', moveset({
    # GDD §7.6 medium burden: 3.5 blocks, i-frames ticks 3-9, 16 stamina.
    'roll': action(0, 10, 6, 'roll', stamina=16, dodge={'iframe_start': 3, 'iframe_end': 10, 'distance': 3.5, 'move_ticks': 10},
                   combo=0.4, cancel=0.4, hitstop=0),
    'backstep': action(0, 7, 5, 'backstep', stamina=10, dodge={'iframe_start': 2, 'iframe_end': 7, 'distance': 1.6, 'move_ticks': 7},
                       hitstop=0),
    'flinch': action(0, 0, 8, 'flinch', hitstop=0),
    'stagger': action(0, 0, 24, 'stagger', hitstop=0),
}))

# ---------------------------------------------------------------- blade (GDD §7.4: 5/3/7, 12 stamina; heavy 10/3/12, 22)
write('vestige/vestige/movesets/blade.json', moveset({
    'light_1': action(5, 3, 7, 'blade_slash_rl', stamina=12, mv=1.0, poise=8, hit=arc(2.6, 70, -70), motion={'active': 0.10}),
    'light_2': action(5, 3, 7, 'blade_slash_lr', stamina=12, mv=1.0, poise=8, hit=arc(2.6, -70, 70), motion={'active': 0.10}),
    'light_3': action(6, 3, 8, 'blade_overhead', stamina=12, mv=1.05, poise=9, hit=arc(2.7, 30, -20), motion={'active': 0.12}),
    'light_4': action(6, 3, 10, 'blade_thrust', stamina=13, mv=1.15, poise=10, hit=thrust(3.0, 0.5), motion={'active': 0.18}),
    'heavy': action(10, 3, 12, 'blade_heavy', stamina=22, mv=1.45, poise=18, hit=arc(2.9, 80, -60), motion={'active': 0.15},
                    hyperarmor=True, charge={'max_ticks': 14, 'motion_bonus': 0.45, 'poise_bonus': 10}, hitstop=5),
    'running': action(6, 3, 9, 'blade_running', stamina=14, mv=1.1, poise=10, hit=arc(2.8, 50, -50),
                      motion={'windup': 0.25, 'active': 0.30}),
    'rolling': action(4, 3, 8, 'blade_rolling', stamina=10, mv=0.9, poise=7, hit=arc(2.6, -40, 60), motion={'active': 0.12}),
}, light=['light_1', 'light_2', 'light_3', 'light_4'], heavy='heavy', running='running', rolling='rolling'))

# ---------------------------------------------------------------- pike (6/2/9, 13; heavy 11/3/13, 24)
write('vestige/vestige/movesets/pike.json', moveset({
    'light_1': action(6, 2, 9, 'pike_thrust', stamina=13, mv=1.0, poise=7, hit=thrust(3.6), motion={'active': 0.12}),
    'light_2': action(6, 2, 9, 'pike_thrust_low', stamina=13, mv=1.0, poise=7, hit=thrust(3.6, y_min=0.0, y_max=1.6), motion={'active': 0.12}),
    'light_3': action(7, 2, 10, 'pike_thrust', stamina=14, mv=1.1, poise=9, hit=thrust(3.8), motion={'active': 0.16}),
    'heavy': action(11, 3, 13, 'pike_heavy', stamina=24, mv=1.45, poise=16, hit=thrust(4.0, 0.55), motion={'active': 0.30},
                    hyperarmor=True, charge={'max_ticks': 14, 'motion_bonus': 0.45, 'poise_bonus': 8}, hitstop=5),
    'running': action(6, 3, 10, 'pike_running', stamina=15, mv=1.15, poise=10, hit=thrust(3.8, 0.5),
                      motion={'windup': 0.30, 'active': 0.35}),
    'rolling': action(4, 2, 8, 'pike_thrust', stamina=10, mv=0.9, poise=6, hit=thrust(3.4)),
}, light=['light_1', 'light_2', 'light_3'], heavy='heavy', running='running', rolling='rolling'))

# ---------------------------------------------------------------- maul (14/4/16, 24; heavy 20/5/20, 40)
write('vestige/vestige/movesets/maul.json', moveset({
    'light_1': action(14, 4, 16, 'maul_overhead', stamina=24, mv=1.3, poise=25, hit=arc(2.5, 18, -18), motion={'active': 0.08}, hitstop=4),
    'light_2': action(14, 4, 16, 'maul_swing', stamina=24, mv=1.3, poise=25, hit=arc(2.6, 90, -60), motion={'active': 0.08}, hitstop=4),
    'heavy': action(20, 5, 20, 'maul_heavy', stamina=40, mv=1.7, poise=40, hit=circle(2.0, 1.4),
                    hyperarmor=True, charge={'max_ticks': 16, 'motion_bonus': 0.7, 'poise_bonus': 20}, hitstop=6),
    'running': action(10, 4, 14, 'maul_overhead', stamina=26, mv=1.35, poise=26, hit=arc(2.6, 40, -40),
                      motion={'windup': 0.30, 'active': 0.10}, hitstop=4),
    'rolling': action(8, 4, 14, 'maul_swing', stamina=20, mv=1.1, poise=20, hit=arc(2.6, 60, -60), hitstop=4),
}, light=['light_1', 'light_2'], heavy='heavy', running='running', rolling='rolling'))

# ---------------------------------------------------------------- enemies (GDD §18.1). Every attack has >= 9 windup ticks.
write('vestige/vestige/movesets/hollowed.json', moveset({
    'swipe': action(14, 3, 14, 'hollowed_swipe', damage=22, hit=arc(2.4, 60, -60), motion={'active': 0.15},
                    telegraph={'glint': 'white', 'lead': 6}, tracking=25),
    'backhand': action(9, 3, 16, 'hollowed_backhand', damage=18, hit=arc(2.3, -60, 60), motion={'active': 0.12},
                       telegraph={'glint': 'white', 'lead': 5}, tracking=20),
    'lunge_grab': action(18, 4, 22, 'hollowed_lunge', damage=32, hit=thrust(2.0, 0.6), motion={'active': 0.45},
                         telegraph={'glint': 'grab', 'lead': 8}, tracking=20, stagger=20),
    'flinch': action(0, 0, 10, 'flinch', hitstop=0),
    'stagger': action(0, 0, 40, 'stagger', hitstop=0),
}))
write('vestige/vestige/movesets/hollowed_brute.json', moveset({
    'overhead_slam': action(22, 3, 26, 'brute_slam', damage=40, hit=circle(1.8, 1.6), hyperarmor=True, tracking=15, hitstop=5,
                            telegraph={'glint': 'red', 'lead': 8, 'decal': {'type': 'circle', 'radius': 1.8, 'offset': 1.6}}),
    'sweep': action(16, 4, 18, 'brute_sweep', damage=30, hit=arc(3.0, 80, -80), hyperarmor=True, tracking=20,
                    telegraph={'glint': 'white', 'lead': 6}),
    'flinch': action(0, 0, 12, 'flinch', hitstop=0),
    'stagger': action(0, 0, 50, 'stagger', hitstop=0),
}))

write('vestige/vestige/enemies/hollowed.json', {
    'moveset': 'vestige:hollowed', 'health': 90, 'defense': 10, 'poise': 20, 'poise_regen_delay': 60,
    'engage': {'min': 1.3, 'max': 2.4, 'strafe_speed': 0.45, 'pause_min': 12, 'pause_max': 32},
    'attacks': [
        {'action': 'swipe', 'weight': 4, 'max_range': 2.6, 'cooldown': 30, 'follow_up': 'backhand', 'follow_chance': 0.45},
        {'action': 'lunge_grab', 'weight': 2, 'min_range': 1.8, 'max_range': 4.2, 'cooldown': 80},
    ],
})
write('vestige/vestige/enemies/hollowed_brute.json', {
    'moveset': 'vestige:hollowed_brute', 'health': 220, 'defense': 25, 'poise': 60, 'poise_regen_delay': 80,
    'engage': {'min': 1.8, 'max': 2.8, 'strafe_speed': 0.35, 'pause_min': 20, 'pause_max': 40},
    'attacks': [
        {'action': 'overhead_slam', 'weight': 3, 'max_range': 3.2, 'cooldown': 60},
        {'action': 'sweep', 'weight': 3, 'max_range': 3.2, 'cooldown': 40},
    ],
})

# ---------------------------------------------------------------- weapons (GDD §10.2 tier 0 + vanilla swords as Common blades)
def weapon(name, items, cls, moveset_id, ar, weight, poise=1.0):
    write('vestige/vestige/weapons/%s.json' % name, {'items': items, 'class': cls, 'moveset': moveset_id,
                                                     'attack_rating': ar, 'poise_damage': poise, 'weight': weight})


weapon('keepsake_blade', ['vestige:keepsake_blade'], 'blade', 'vestige:blade', 60, 3.0)
weapon('ash_pike', ['vestige:ash_pike'], 'pike', 'vestige:pike', 55, 4.5)
weapon('hand_maul', ['vestige:hand_maul'], 'maul', 'vestige:maul', 72, 7.0)
for mat, ar, w in [('wooden', 30, 2.0), ('stone', 40, 3.0), ('golden', 38, 2.5), ('iron', 52, 3.0), ('diamond', 66, 3.5), ('netherite', 80, 4.0)]:
    weapon('vanilla_%s_sword' % mat, ['minecraft:%s_sword' % mat], 'blade', 'vestige:blade', ar, w, 0.9)

# ---------------------------------------------------------------- damage types and tags
for name, msg in [('weapon', 'vestige.weapon'), ('enemy_attack', 'vestige.enemy')]:
    write('vestige/damage_type/%s.json' % name, {'exhaustion': 0.1, 'message_id': msg, 'scaling': 'never'})
types = ['vestige:weapon', 'vestige:enemy_attack']
for tag in ['bypasses_armor', 'bypasses_cooldown', 'no_knockback']:
    write('minecraft/tags/damage_type/%s.json' % tag, {'replace': False, 'values': types})
write('vestige/tags/damage_type/native_scale.json', {'values': types})

# ---------------------------------------------------------------- spawning
write('vestige/tags/worldgen/biome/spawns_hollowed.json', {'values': [
    '#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_savanna',
    'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow', 'minecraft:swamp']})
write('vestige/neoforge/biome_modifier/hollowed_spawns.json', {
    'type': 'neoforge:add_spawns', 'biomes': '#vestige:spawns_hollowed',
    'spawners': [
        {'type': 'vestige:hollowed', 'weight': 40, 'minCount': 1, 'maxCount': 3},
        {'type': 'vestige:hollowed_brute', 'weight': 6, 'minCount': 1, 'maxCount': 1},
    ]})

# ---------------------------------------------------------------- loot
def entity_loot(name, pools):
    write('vestige/loot_table/entities/%s.json' % name, {'type': 'minecraft:entity', 'pools': pools,
                                                        'random_sequence': 'vestige:entities/%s' % name})


def drop(item, lo, hi):
    return {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': item, 'functions': [
        {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}},
        {'function': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting',
         'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}]}]}


entity_loot('hollowed', [drop('minecraft:bone', 0, 1), drop('minecraft:string', 0, 2)])
entity_loot('hollowed_brute', [drop('minecraft:bone', 1, 2), drop('minecraft:iron_nugget', 0, 3)])

# ---------------------------------------------------------------- recipes (tier 0 is craftable; Wren also offers one as a keepsake, GDD §4.5)
def shaped(name, pattern, key):
    write('vestige/recipe/%s.json' % name, {
        'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
        'key': {k: {'item': v} for k, v in key.items()}, 'result': {'count': 1, 'id': 'vestige:%s' % name}})


shaped('keepsake_blade', ['  I', ' I ', 'S  '], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})
shaped('ash_pike', ['  I', ' S ', 'S  '], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})
shaped('hand_maul', ['CIC', ' S ', ' S '], {'C': 'minecraft:cobblestone', 'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})

print('ok', file=sys.stderr)
