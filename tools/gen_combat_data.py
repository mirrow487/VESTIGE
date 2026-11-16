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
           combo=0.4, cancel=0.4, hitstop=3, guard=False, parry=None, invulnerable=False, art=None, effects=None):
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
    defense = {}
    if guard:
        defense['guard'] = True
    if parry:
        defense['parry'] = {'start': parry[0], 'end': parry[1]}
    if invulnerable:
        defense['invulnerable'] = True
    if defense:
        a['defense'] = defense
    if art:
        a['art'] = art
    if effects:
        a['effects'] = effects
    return a


# A held guard: raised over the windup, then paused at its peak for as long as the button is held
# (reuses the charge hold), lowered over the recovery.
HOLD = {'max_ticks': 6000, 'motion_bonus': 0}


def arc(reach, frm, to, y_max=2.2):
    return {'type': 'arc', 'reach': reach, 'from': frm, 'to': to, 'y_min': 0.0, 'y_max': y_max}


def thrust(reach, radius=0.45, y_min=0.2, y_max=2.0):
    return {'type': 'thrust', 'reach': reach, 'radius': radius, 'y_min': y_min, 'y_max': y_max}


def circle(radius, offset, y_max=1.6):
    return {'type': 'circle', 'radius': radius, 'offset': offset, 'y_min': -0.5, 'y_max': y_max}


def ring(reach, thickness, offset=0.0, y_max=0.6):
    # Expanding band; a low y_max means it can be jumped (GDD §17 Toll Ring).
    return {'type': 'ring', 'reach': reach, 'radius': thickness, 'offset': offset, 'y_min': -0.5, 'y_max': y_max}


def moveset(actions, light=None, heavy=None, running=None, rolling=None, art=None, riposte=None):
    m = {'actions': actions}
    if art:
        m['art'] = art
    if riposte:
        m['riposte'] = riposte
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
    # GDD §7.7. Guards hold at their peak while the button is held; a buckler parries instead.
    'guard_weapon': action(4, 1, 6, 'guard_weapon', guard=True, charge=HOLD, combo=0.0, cancel=0.0, hitstop=0),
    'guard_shield': action(3, 1, 5, 'guard_shield', guard=True, charge=HOLD, combo=0.0, cancel=0.0, hitstop=0),
    'guard_bulwark': action(5, 1, 7, 'guard_bulwark', guard=True, charge=HOLD, combo=0.0, cancel=0.0, hitstop=0),
    # 4-tick parry window starting on tick 2 (GDD §7.7); a committed recovery if it whiffs.
    'parry_buckler': action(2, 4, 12, 'parry_buckler', stamina=8, parry=(2, 6), combo=0.5, cancel=0.5, hitstop=0),
    'guard_break': action(0, 0, 24, 'guard_break', hitstop=0),
    # Wick Flask (GDD §8.4): raise, then heal over the 20 active ticks; a walk at most. Empty: a futile shake.
    'flask_drink': action(8, 20, 8, 'flask_drink', combo=0.0, cancel=0.5, hitstop=0),
    'flask_empty': action(0, 0, 18, 'flask_shake', combo=0.0, cancel=0.6, hitstop=0),
    # Resting at a Waylight (GDD §6.6): kneel before the lantern.
    'rest': action(6, 1, 28, 'rest_kneel', hitstop=0),
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
    # Art: Riposte Stance (GDD §7.11). A parry stance; a successful parry triggers the counter-slash.
    'riposte_stance': action(2, 12, 10, 'blade_riposte_stance', stamina=10, parry=(1, 14), hitstop=0,
                             art={'cost': 20, 'on_parry': 'riposte_counter'}),
    'riposte_counter': action(2, 3, 12, 'blade_counter', mv=2.2, poise=30, hit=arc(2.8, 60, -60), motion={'active': 0.2},
                              hitstop=6, art={'cost': 0}),
    # Critical (riposte / backstab), GDD §7.5 motion value 3.0.
    'riposte': action(7, 3, 16, 'blade_riposte', mv=3.0, hit=thrust(2.8, 0.6), invulnerable=True, hitstop=9),
}, light=['light_1', 'light_2', 'light_3', 'light_4'], heavy='heavy', running='running', rolling='rolling',
   art='riposte_stance', riposte='riposte'))

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
    # Art: Impaling Charge. Dash thrust that pins small enemies (forced stagger).
    'impaling_charge': action(7, 5, 14, 'pike_charge', stamina=20, mv=1.6, poise=30, hit=thrust(3.0, 0.6),
                              motion={'windup': 0.05, 'active': 0.55}, hyperarmor=True, stagger=30, hitstop=6, art={'cost': 30}),
    'riposte': action(7, 3, 16, 'pike_riposte', mv=3.0, hit=thrust(3.2, 0.6), invulnerable=True, hitstop=9),
}, light=['light_1', 'light_2', 'light_3'], heavy='heavy', running='running', rolling='rolling',
   art='impaling_charge', riposte='riposte'))

# ---------------------------------------------------------------- maul (14/4/16, 24; heavy 20/5/20, 40)
write('vestige/vestige/movesets/maul.json', moveset({
    'light_1': action(14, 4, 16, 'maul_overhead', stamina=24, mv=1.3, poise=25, hit=arc(2.5, 18, -18), motion={'active': 0.08}, hitstop=4),
    'light_2': action(14, 4, 16, 'maul_swing', stamina=24, mv=1.3, poise=25, hit=arc(2.6, 90, -60), motion={'active': 0.08}, hitstop=4),
    'heavy': action(20, 5, 20, 'maul_heavy', stamina=40, mv=1.7, poise=40, hit=circle(2.0, 1.4),
                    hyperarmor=True, charge={'max_ticks': 16, 'motion_bonus': 0.7, 'poise_bonus': 20}, hitstop=6),
    'running': action(10, 4, 14, 'maul_overhead', stamina=26, mv=1.35, poise=26, hit=arc(2.6, 40, -40),
                      motion={'windup': 0.30, 'active': 0.10}, hitstop=4),
    'rolling': action(8, 4, 14, 'maul_swing', stamina=20, mv=1.1, poise=20, hit=arc(2.6, 60, -60), hitstop=4),
    # Art: Tremor. Ground pound: ring shockwave around the player, huge poise damage.
    'tremor': action(16, 3, 20, 'maul_tremor', stamina=30, mv=1.5, poise=70, hit=circle(3.5, 0.0, 1.4), hyperarmor=True,
                     hitstop=7, art={'cost': 35}),
    'riposte': action(9, 4, 16, 'maul_riposte', mv=2.6, hit=arc(2.6, 20, -20), invulnerable=True, hitstop=10),
}, light=['light_1', 'light_2'], heavy='heavy', running='running', rolling='rolling',
   art='tremor', riposte='riposte'))


# ---------------------------------------------------------------- fang (3/2/5, 8; heavy 7/3/9, 16; combo 5; MV 0.8 / 1.3 / 1.7; crit 3.5)
write('vestige/vestige/movesets/fang.json', moveset({
    'light_1': action(3, 2, 5, 'fang_slash_r', stamina=8, mv=0.8, poise=4, hit=arc(2.1, 55, -55), motion={'active': 0.12}, hitstop=2),
    'light_2': action(3, 2, 5, 'fang_slash_l', stamina=8, mv=0.8, poise=4, hit=arc(2.1, -55, 55), motion={'active': 0.12}, hitstop=2),
    'light_3': action(3, 2, 6, 'fang_stab', stamina=8, mv=0.85, poise=5, hit=thrust(2.4, 0.4), motion={'active': 0.2}, hitstop=2),
    'light_4': action(3, 2, 5, 'fang_slash_r', stamina=8, mv=0.85, poise=5, hit=arc(2.1, 55, -55), motion={'active': 0.12}, hitstop=2),
    'light_5': action(4, 4, 9, 'fang_spin', stamina=10, mv=1.0, poise=8, hit=arc(2.3, -170, 170), motion={'active': 0.08}, hitstop=3),
    'heavy': action(7, 3, 9, 'fang_lunge', stamina=16, mv=1.3, poise=10, hit=thrust(2.9, 0.45), motion={'active': 0.4},
                    hyperarmor=True, charge={'max_ticks': 10, 'motion_bonus': 0.4, 'poise_bonus': 5}, hitstop=4),
    'running': action(4, 2, 8, 'fang_slide', stamina=10, mv=0.95, poise=6, hit=thrust(2.6, 0.45),
                      motion={'windup': 0.3, 'active': 0.4}, hitstop=2),
    'rolling': action(2, 2, 6, 'fang_slash_r', stamina=7, mv=0.75, poise=4, hit=arc(2.1, 55, -55), hitstop=2),
    # Art: Quickstep (GDD §7.11). A short i-frame blink; the next hit within 1 s is a critical.
    'quickstep': action(0, 6, 5, 'fang_quickstep', stamina=8, dodge={'iframe_start': 0, 'iframe_end': 6, 'distance': 3.2, 'move_ticks': 5},
                        hitstop=0, art={'cost': 15}, effects={'keen': 20}),
    'riposte': action(5, 3, 14, 'fang_riposte', mv=3.5, hit=thrust(2.4, 0.6), invulnerable=True, hitstop=8),
}, light=['light_1', 'light_2', 'light_3', 'light_4', 'light_5'], heavy='heavy', running='running', rolling='rolling',
   art='quickstep', riposte='riposte'))

# ---------------------------------------------------------------- greatblade (12/4/14, 22; heavy 18/5/18, 36; MV 1.2 / 1.6 / 2.2; crit 2.8)
write('vestige/vestige/movesets/greatblade.json', moveset({
    'light_1': action(12, 4, 14, 'gb_sweep_r', stamina=22, mv=1.2, poise=22, hit=arc(3.3, 100, -80), motion={'active': 0.1}, hitstop=5),
    'light_2': action(12, 4, 14, 'gb_sweep_l', stamina=22, mv=1.25, poise=22, hit=arc(3.3, -100, 80), motion={'active': 0.1}, hitstop=5),
    'light_3': action(13, 4, 16, 'gb_overhead', stamina=24, mv=1.35, poise=30, hit=arc(3.4, 20, -15), motion={'active': 0.12}, hitstop=6),
    'heavy': action(18, 5, 18, 'gb_heavy', stamina=36, mv=1.6, poise=40, hit=arc(3.5, 170, -170), hyperarmor=True,
                    charge={'max_ticks': 16, 'motion_bonus': 0.6, 'poise_bonus': 20}, hitstop=7),
    'running': action(12, 4, 15, 'gb_running', stamina=24, mv=1.3, poise=28, hit=arc(3.3, 25, -25),
                      motion={'windup': 0.3, 'active': 0.15}, hitstop=6),
    'rolling': action(10, 4, 14, 'gb_rolling', stamina=20, mv=1.1, poise=22, hit=arc(3.1, -30, 40), hitstop=5),
    # Art: Cleaving Wake. An overhead slam; a shockwave runs 7 blocks along the ground ahead.
    'cleaving_wake': action(18, 6, 18, 'gb_cleaving_wake', stamina=26, mv=1.5, poise=45, hit=thrust(7.0, 0.8, y_min=-0.5, y_max=1.4),
                            hyperarmor=True, hitstop=6, art={'cost': 35}, effects={'fx': 'wake'}),
    'riposte': action(10, 4, 18, 'gb_riposte', mv=2.8, hit=thrust(2.9, 0.6), invulnerable=True, hitstop=10),
}, light=['light_1', 'light_2', 'light_3'], heavy='heavy', running='running', rolling='rolling',
   art='cleaving_wake', riposte='riposte'))

# ---------------------------------------------------------------- censer (8/5/10, 15; heavy 14/6/14, 28; MV 1.05 / 1.5 / 2.0; crit 2.8)
write('vestige/vestige/movesets/censer.json', moveset({
    'light_1': action(8, 5, 10, 'censer_swing_r', stamina=15, mv=1.05, poise=12, hit=arc(3.1, 110, -70), motion={'active': 0.06}),
    'light_2': action(8, 5, 10, 'censer_swing_l', stamina=15, mv=1.05, poise=12, hit=arc(3.1, -110, 70), motion={'active': 0.06}),
    'light_3': action(9, 4, 12, 'censer_overhead', stamina=16, mv=1.15, poise=16, hit=arc(3.2, 25, -25), motion={'active': 0.1}, hitstop=4),
    'heavy': action(14, 6, 14, 'censer_spin', stamina=28, mv=1.5, poise=25, hit=circle(3.0, 0.0), hyperarmor=True,
                    charge={'max_ticks': 12, 'motion_bonus': 0.5, 'poise_bonus': 10}, hitstop=4),
    'running': action(8, 5, 11, 'censer_swing_r', stamina=16, mv=1.1, poise=13, hit=arc(3.1, 110, -70),
                      motion={'windup': 0.25, 'active': 0.12}),
    'rolling': action(6, 5, 10, 'censer_swing_l', stamina=13, mv=0.95, poise=11, hit=arc(3.0, -110, 70)),
    # Art: Smolder Wake. A spin, then an incense cloud wider than the spin burns everything inside for 5 s.
    'smolder_wake': action(10, 8, 14, 'censer_smolder', stamina=20, mv=1.2, poise=20, hit=circle(3.2, 0.0), hyperarmor=True, hitstop=3,
                           art={'cost': 25}, effects={'cloud': {'radius': 4.0, 'duration': 100, 'interval': 10, 'motion_value': 0.18}}),
    'riposte': action(9, 4, 16, 'censer_riposte', mv=2.8, hit=arc(2.7, 25, -25), invulnerable=True, hitstop=9),
}, light=['light_1', 'light_2', 'light_3'], heavy='heavy', running='running', rolling='rolling',
   art='smolder_wake', riposte='riposte'))

# ---------------------------------------------------------------- longbow: arrows from the inventory; hold the heavy to draw
write('vestige/vestige/movesets/longbow.json', moveset({
    'quick_shot': action(8, 1, 7, 'bow_quick', stamina=10, mv=0.9, poise=6, hitstop=3,
                         effects={'projectile': {'speed': 2.4}}),
    'aimed_shot': action(14, 1, 9, 'bow_aimed', stamina=16, mv=1.0, poise=10, hitstop=4,
                         charge={'max_ticks': 20, 'motion_bonus': 0.9, 'poise_bonus': 10},
                         effects={'projectile': {'speed': 2.6, 'charge_speed': 0.45}}),
    # Art: Pinning Shot. A braced full draw; the arrow roots the target for 2 s.
    'pinning_shot': action(16, 1, 10, 'bow_pinning', stamina=14, mv=1.6, poise=20, stagger=40, hitstop=5,
                           art={'cost': 25}, effects={'projectile': {'speed': 3.2}}),
    'riposte': action(6, 3, 14, 'bow_riposte', mv=2.4, hit=arc(2.4, 40, -40), invulnerable=True, hitstop=8),
}, light=['quick_shot'], heavy='aimed_shot', running='quick_shot', rolling='quick_shot',
   art='pinning_shot', riposte='riposte'))

# ---------------------------------------------------------------- bell (catalyst): Tolls are sound rings
write('vestige/vestige/movesets/bell.json', moveset({
    'peal': action(7, 3, 8, 'bell_peal', stamina=12, mv=0.9, poise=10, hit=ring(2.2, 1.0, offset=1.6, y_max=2.0)),
    'toll': action(14, 6, 12, 'bell_toll', stamina=22, mv=1.3, poise=30, hit=ring(4.5, 1.2, y_max=1.4), hyperarmor=True,
                   charge={'max_ticks': 12, 'motion_bonus': 0.4, 'poise_bonus': 15}, hitstop=4),
    # Art: Toll. A great expanding ring that interrupts everything it passes (forced stagger).
    'great_toll': action(12, 8, 14, 'bell_great_toll', stamina=16, mv=1.2, poise=60, stagger=25, hit=ring(7.0, 1.5, y_max=2.4),
                         hyperarmor=True, hitstop=4, art={'cost': 20}),
    'riposte': action(6, 3, 14, 'bell_riposte', mv=2.6, hit=circle(1.8, 1.0, 2.2), invulnerable=True, hitstop=9),
}, light=['peal'], heavy='toll', running='peal', rolling='peal', art='great_toll', riposte='riposte'))

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
    'parried': action(0, 0, 45, 'parried', hitstop=0),
    'riposted': action(0, 0, 50, 'riposted', hitstop=0),
}))
write('vestige/vestige/movesets/hollowed_brute.json', moveset({
    'overhead_slam': action(22, 3, 26, 'brute_slam', damage=40, hit=circle(1.8, 1.6), hyperarmor=True, tracking=15, hitstop=5,
                            telegraph={'glint': 'red', 'lead': 8, 'decal': {'type': 'circle', 'radius': 1.8, 'offset': 1.6}}),
    'sweep': action(16, 4, 18, 'brute_sweep', damage=30, hit=arc(3.0, 80, -80), hyperarmor=True, tracking=20,
                    telegraph={'glint': 'white', 'lead': 6}),
    'flinch': action(0, 0, 12, 'flinch', hitstop=0),
    'stagger': action(0, 0, 50, 'stagger', hitstop=0),
    'parried': action(0, 0, 40, 'parried', hitstop=0),
    'riposted': action(0, 0, 50, 'riposted', hitstop=0),
}))


# Tolling Ram (GDD §18.1): teaches rolling sideways. The charge only aims during its pawing windup.
write('vestige/vestige/movesets/tolling_ram.json', moveset({
    'butt_charge': action(18, 14, 22, 'ram_charge', damage=34, hit=thrust(1.6, 0.8, y_max=1.6), motion={'active': 0.6},
                          telegraph={'glint': 'red', 'lead': 6, 'decal': {'type': 'thrust', 'length': 9.0, 'width': 1.6}},
                          tracking=12, stagger=20, hyperarmor=True, hitstop=5),
    'toll_ring': action(16, 10, 16, 'ram_toll', damage=22, hit=ring(5.0, 1.0), hyperarmor=True, tracking=0, hitstop=2,
                        telegraph={'glint': 'red', 'lead': 6, 'decal': {'type': 'circle', 'radius': 5.0, 'offset': 0.0}}),
    'headbutt': action(11, 3, 14, 'ram_headbutt', damage=24, hit=thrust(2.0, 0.7), motion={'active': 0.25},
                       telegraph={'glint': 'white', 'lead': 5}, tracking=25),
    'flinch': action(0, 0, 10, 'ram_flinch', hitstop=0),
    'stagger': action(0, 0, 45, 'ram_stagger', hitstop=0),
    'parried': action(0, 0, 40, 'ram_parried', hitstop=0),
    'riposted': action(0, 0, 50, 'ram_riposted', hitstop=0),
}))

write('vestige/vestige/enemies/hollowed.json', {
    'moveset': 'vestige:hollowed', 'health': 90, 'defense': 10, 'poise': 20, 'poise_regen_delay': 60, 'glim': 32,
    'engage': {'min': 1.3, 'max': 2.4, 'strafe_speed': 0.45, 'pause_min': 12, 'pause_max': 32},
    'attacks': [
        {'action': 'swipe', 'weight': 4, 'max_range': 2.6, 'cooldown': 30, 'follow_up': 'backhand', 'follow_chance': 0.45},
        {'action': 'lunge_grab', 'weight': 2, 'min_range': 1.8, 'max_range': 4.2, 'cooldown': 80},
    ],
})
write('vestige/vestige/enemies/hollowed_brute.json', {
    'moveset': 'vestige:hollowed_brute', 'health': 220, 'defense': 25, 'poise': 60, 'poise_regen_delay': 80, 'glim': 110,
    'engage': {'min': 1.8, 'max': 2.8, 'strafe_speed': 0.35, 'pause_min': 20, 'pause_max': 40},
    'attacks': [
        {'action': 'overhead_slam', 'weight': 3, 'max_range': 3.2, 'cooldown': 60},
        {'action': 'sweep', 'weight': 3, 'max_range': 3.2, 'cooldown': 40},
    ],
})

write('vestige/vestige/enemies/tolling_ram.json', {
    'moveset': 'vestige:tolling_ram', 'health': 140, 'defense': 15, 'poise': 35, 'poise_regen_delay': 70, 'glim': 64,
    'engage': {'min': 2.0, 'max': 7.0, 'strafe_speed': 0.4, 'pause_min': 18, 'pause_max': 40},
    'attacks': [
        {'action': 'butt_charge', 'weight': 4, 'min_range': 3.5, 'max_range': 9.0, 'cooldown': 90},
        {'action': 'toll_ring', 'weight': 2, 'max_range': 4.0, 'cooldown': 100},
        {'action': 'headbutt', 'weight': 4, 'max_range': 2.4, 'cooldown': 30},
    ],
})

# ---------------------------------------------------------------- weapons (GDD §10.2 tier 0 + vanilla swords as Common blades)
def weapon(name, items, cls, moveset_id, ar, weight, poise=1.0):
    write('vestige/vestige/weapons/%s.json' % name, {'items': items, 'class': cls, 'moveset': moveset_id,
                                                     'attack_rating': ar, 'poise_damage': poise, 'weight': weight})


weapon('keepsake_blade', ['vestige:keepsake_blade'], 'blade', 'vestige:blade', 60, 3.0)
weapon('ash_pike', ['vestige:ash_pike'], 'pike', 'vestige:pike', 55, 4.5)
weapon('hand_maul', ['vestige:hand_maul'], 'maul', 'vestige:maul', 72, 7.0)
weapon('taper_fang', ['vestige:taper_fang'], 'fang', 'vestige:fang', 62, 1.5)
weapon('wickiron_greatblade', ['vestige:wickiron_greatblade'], 'greatblade', 'vestige:greatblade', 96, 11.0)
weapon('chandlers_censer', ['vestige:chandlers_censer'], 'censer', 'vestige:censer', 74, 5.0)
weapon('hunting_bow', ['vestige:hunting_bow'], 'longbow', 'vestige:longbow', 58, 2.5)
weapon('handbell', ['vestige:handbell'], 'bell', 'vestige:bell', 60, 2.0)
for mat, ar, w in [('wooden', 30, 2.0), ('stone', 40, 3.0), ('golden', 38, 2.5), ('iron', 52, 3.0), ('diamond', 66, 3.5), ('netherite', 80, 4.0)]:
    weapon('vanilla_%s_sword' % mat, ['minecraft:%s_sword' % mat], 'blade', 'vestige:blade', ar, w, 0.9)

# ---------------------------------------------------------------- shields (GDD §7.7)
def shield(name, items, kind, absorption, stability, weight):
    write('vestige/vestige/shields/%s.json' % name, {'items': items, 'kind': kind, 'absorption': absorption,
                                                     'stability': stability, 'weight': weight})


shield('plank_buckler', ['vestige:plank_buckler'], 'buckler', 0.7, 35, 1.5)
shield('vanilla_shield', ['minecraft:shield'], 'kite', 0.9, 55, 4.0)
shield('iron_kite_shield', ['vestige:iron_kite_shield'], 'kite', 0.9, 60, 4.5)
shield('timber_bulwark', ['vestige:timber_bulwark'], 'bulwark', 1.0, 78, 9.0)

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

write('vestige/tags/worldgen/biome/spawns_tolling_ram.json', {'values': [
    'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow']})
write('vestige/neoforge/biome_modifier/tolling_ram_spawns.json', {
    'type': 'neoforge:add_spawns', 'biomes': '#vestige:spawns_tolling_ram',
    'spawners': [{'type': 'vestige:tolling_ram', 'weight': 12, 'minCount': 1, 'maxCount': 2}]})

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
entity_loot('tolling_ram', [drop('minecraft:mutton', 1, 3), drop('minecraft:white_wool', 1, 2), drop('minecraft:leather', 0, 1)])
entity_loot('hollowed_brute', [drop('minecraft:bone', 1, 2), drop('minecraft:iron_nugget', 0, 3)])

# ---------------------------------------------------------------- recipes (tier 0 is craftable; Wren also offers one as a keepsake, GDD §4.5)
def shaped(name, pattern, key):
    write('vestige/recipe/%s.json' % name, {
        'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
        'key': {k: {'item': v} for k, v in key.items()}, 'result': {'count': 1, 'id': 'vestige:%s' % name}})


shaped('keepsake_blade', ['  I', ' I ', 'S  '], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})
shaped('ash_pike', ['  I', ' S ', 'S  '], {'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})
shaped('plank_buckler', [' P ', 'PIP', ' P '], {'P': 'minecraft:oak_planks', 'I': 'minecraft:iron_ingot'})
shaped('hunting_bow', [' SX', 'S X', ' SX'], {'S': 'minecraft:stick', 'X': 'minecraft:string'})
shaped('iron_kite_shield', ['IPI', 'IPI', ' I '], {'I': 'minecraft:iron_ingot', 'P': 'minecraft:oak_planks'})
shaped('timber_bulwark', ['PIP', 'PPP', 'PIP'], {'P': 'minecraft:oak_log', 'I': 'minecraft:iron_ingot'})
shaped('hand_maul', ['CIC', ' S ', ' S '], {'C': 'minecraft:cobblestone', 'I': 'minecraft:iron_ingot', 'S': 'minecraft:stick'})

print('ok', file=sys.stderr)
