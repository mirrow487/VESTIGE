"""Generates VESTIGE equipment data (GDD §8.6, §11, §12): armor pieces and sets, rings, talismans and
relics, their interim loot sources (vanilla chests, GDD §23.2: augmented, never replaced) and recipes.

    python3 -I tools/gen_equipment_data.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data')


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def phys(v, **elements):
    d = {'slash': v, 'strike': v, 'pierce': v}
    d.update(elements)
    return d


# ---------------------------------------------------------------- armor (GDD §11)
def armor(name, slot, set_id, weight, poise, absorption, rarity='common', **extra):
    stats = {'poise': poise, 'absorption': absorption}
    stats.update(extra)
    write('vestige/vestige/armor/%s.json' % name, {'items': ['vestige:' + name], 'slot': slot, 'set': 'vestige:' + set_id,
                                                  'weight': weight, 'stats': stats, 'rarity': rarity})


# Wanderer's Garb: light travelling clothes. Little protection, keeps the roll light.
armor('wanderer_hood', 'head', 'wanderer', 1.2, 1, phys(0.03, cinder=0.02, hush=0.04, void=0.02, radiance=0.02, resonance=0.02))
armor('wanderer_coat', 'chest', 'wanderer', 3.5, 3, phys(0.07, cinder=0.04, hush=0.06, void=0.03, radiance=0.03, resonance=0.03))
armor('wanderer_trousers', 'legs', 'wanderer', 2.4, 2, phys(0.05, cinder=0.03, hush=0.05, void=0.02, radiance=0.02, resonance=0.02))
armor('wanderer_boots', 'feet', 'wanderer', 1.2, 1, phys(0.03, cinder=0.02, hush=0.03, void=0.01, radiance=0.01, resonance=0.01))
# Bellbronze Mail: bronze scale cast from the Bellwether's bell metal. Rings true against sound.
armor('bellbronze_helm', 'head', 'bellbronze', 3.6, 4, phys(0.06, resonance=0.09, cinder=0.04, hush=0.03, void=0.03, radiance=0.04), 'uncommon')
armor('bellbronze_mail', 'chest', 'bellbronze', 7.8, 8, phys(0.12, resonance=0.13, cinder=0.07, hush=0.05, void=0.05, radiance=0.06), 'uncommon')
armor('bellbronze_tassets', 'legs', 'bellbronze', 5.6, 6, phys(0.09, resonance=0.10, cinder=0.05, hush=0.04, void=0.04, radiance=0.05), 'uncommon')
armor('bellbronze_sabatons', 'feet', 'bellbronze', 3.6, 3, phys(0.05, resonance=0.06, cinder=0.03, hush=0.02, void=0.02, radiance=0.03), 'uncommon')
# Wickiron Plate: iron quenched in tallow; heavy, warm, hard to burn.
armor('wickiron_helm', 'head', 'wickiron', 5.6, 7, phys(0.09, cinder=0.09, resonance=0.04, hush=0.03, void=0.04, radiance=0.03), 'rare')
armor('wickiron_plate', 'chest', 'wickiron', 12.5, 14, phys(0.17, cinder=0.14, resonance=0.07, hush=0.05, void=0.06, radiance=0.05), 'rare')
armor('wickiron_cuisses', 'legs', 'wickiron', 9.0, 10, phys(0.13, cinder=0.10, resonance=0.05, hush=0.04, void=0.05, radiance=0.04), 'rare')
armor('wickiron_greaves', 'feet', 'wickiron', 5.6, 6, phys(0.08, cinder=0.06, resonance=0.03, hush=0.02, void=0.03, radiance=0.03), 'rare')

write('vestige/vestige/armor_sets/wanderer.json', {
    'name': "Wanderer's Garb", 'two_piece': {'stamina_regen': 0.05}, 'two_text': '+5% stamina regeneration',
    'four_piece': {'perk': 'wanderer_sure_foot'}, 'four_text': 'Sure Foot: fall damage -30%'})
write('vestige/vestige/armor_sets/bellbronze.json', {
    'name': 'Bellbronze Mail', 'two_piece': {'poise': 8}, 'two_text': '+8 poise',
    'four_piece': {'perk': 'bellbronze_toll'}, 'four_text': 'Toll: every 4th hit rings a Resonance shockwave'})
write('vestige/vestige/armor_sets/wickiron.json', {
    'name': 'Wickiron Plate', 'two_piece': {'absorption': phys(0.10)}, 'two_text': '+10% physical absorption',
    'four_piece': {'perk': 'wickiron_slow_burn'}, 'four_text': 'Slow Burn: below 30% HP, +20% absorption and +15 poise'})


# ---------------------------------------------------------------- accessories (GDD §12)
def acc(name, kind, description, stats=None, family=None, weight=0.5, rarity='uncommon'):
    a = {'items': ['vestige:' + name], 'kind': kind, 'weight': weight, 'rarity': rarity, 'description': description}
    if stats:
        a['stats'] = stats
    if family:
        a['family'] = family
    write('vestige/vestige/accessories/%s.json' % name, a)


# Rings: they shape numbers.
acc('ring_of_quick_breath', 'ring', 'Breathe easier between blows.', {'stamina_regen': 0.15})
acc('bellbronze_band', 'ring', 'A band of bell metal: you stand firmer.', {'poise': 10}, weight=1.0)
acc('waxen_ring', 'ring', 'A successful parry heals 5% of your health.', {'perk': 'waxen_ring'}, rarity='rare')
acc('hollow_ring', 'ring', 'Your roll becomes a short blink-step with a long invulnerable window.', {'perk': 'hollow_ring'},
    family='roll_modifier', rarity='rare')
acc('tithe_ring', 'ring', 'Every kill rings 8 Resonance into you.', {'perk': 'tithe_ring'}, rarity='rare')
acc('ring_of_the_unsung', 'ring', 'Resonance never fades.', {'perk': 'unsung_ring'}, rarity='relic')
acc('harts_ring', 'ring', '+12% damage while above 80% health.', {'perk': 'harts_ring'}, rarity='rare')
acc('ember_signet', 'ring', 'Cinder damage +10%, and every hit builds Scorch.', {'type_bonus': {'cinder': 0.10}, 'perk': 'ember_signet'},
    rarity='rare')
# Talismans: they change rules.
acc('lightfoot_feather', 'talisman', 'Burden thresholds +15%: lighter rolls in heavier gear.', {'burden_threshold': 0.15}, weight=0.2)
acc('talisman_of_the_grove', 'talisman', 'Out of combat, you mend three times as fast.', {'perk': 'grove'})
acc('ledger_of_debts', 'talisman', '+25% damage, but each hit you take costs 1% of your Glim.', {'perk': 'ledger_of_debts'}, rarity='rare')
acc('talisman_of_second_breath', 'talisman', 'Once every 10 minutes, a killing blow leaves you at 20% health instead.',
    {'perk': 'second_breath'}, rarity='relic')
acc('bell_of_warding', 'talisman', 'Hushed buildup -60%.', {'perk': 'bell_of_warding'})
# Relics: they add verbs.
acc('windsail_cape', 'relic', 'Hold jump while falling to glide.', {'perk': 'windsail_cape'}, family='air', weight=1.0, rarity='rare')
acc('bellbronze_spurs', 'relic', 'Sprint 15% faster for half the stamina.', {'sprint_speed': 0.15, 'sprint_stamina_factor': 0.5,
                                                                          'perk': 'bellbronze_spurs'}, weight=1.0)
acc('divers_bell', 'relic', 'Breathe underwater and swim faster.', {'perk': 'divers_bell'}, weight=1.5)
acc('climbers_hooks', 'relic', 'Cling to a wall by jumping into it, and climb.', {'perk': 'climbers_hooks'}, weight=1.0, rarity='rare')
acc('pale_hook', 'relic', 'Active (G): grapple to a surface up to 24 blocks away.', {'perk': 'pale_hook'}, weight=1.0, rarity='relic')


# ---------------------------------------------------------------- interim sources (vanilla loot, augmented)
def loot_table(name, rolls, entries):
    write('vestige/loot_table/chests/%s.json' % name, {'type': 'minecraft:chest', 'random_sequence': 'vestige:chests/' + name,
                                                      'pools': [{'rolls': rolls, 'entries': entries}]})


def item(name, weight, lo=1, hi=1):
    e = {'type': 'minecraft:item', 'name': 'vestige:' + name, 'weight': weight}
    if hi > 1:
        e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    return e


EMPTY = {'type': 'minecraft:empty', 'weight': 1}
loot_table('lamplighter_trinkets', 1, [dict(EMPTY, weight=40), item('ring_of_quick_breath', 6), item('bellbronze_band', 6),
                                         item('lightfoot_feather', 6), item('talisman_of_the_grove', 5), item('bell_of_warding', 5),
                                         item('harts_ring', 3), item('waxen_ring', 3), item('ember_signet', 3), item('tithe_ring', 3),
                                         item('kindling_ember', 8), item('flask_wick', 4)])
loot_table('lamplighter_relics', 1, [dict(EMPTY, weight=30), item('windsail_cape', 4), item('bellbronze_spurs', 5), item('divers_bell', 5),
                                       item('climbers_hooks', 4), item('pale_hook', 2), item('hollow_ring', 3), item('ledger_of_debts', 3),
                                       item('talisman_of_second_breath', 1), item('ring_of_the_unsung', 1)])
loot_table('lamplighter_armor', 1, [dict(EMPTY, weight=30)] + [item(n, 3) for n in (
    'bellbronze_helm', 'bellbronze_mail', 'bellbronze_tassets', 'bellbronze_sabatons')] + [item(n, 2) for n in (
    'wickiron_helm', 'wickiron_plate', 'wickiron_cuisses', 'wickiron_greaves')])

MODIFIERS = {
    'trinkets_dungeon': ('minecraft:chests/simple_dungeon', 'lamplighter_trinkets'),
    'trinkets_mineshaft': ('minecraft:chests/abandoned_mineshaft', 'lamplighter_trinkets'),
    'trinkets_ruined_portal': ('minecraft:chests/ruined_portal', 'lamplighter_trinkets'),
    'relics_desert': ('minecraft:chests/desert_pyramid', 'lamplighter_relics'),
    'relics_jungle': ('minecraft:chests/jungle_temple', 'lamplighter_relics'),
    'relics_stronghold': ('minecraft:chests/stronghold_corridor', 'lamplighter_relics'),
    'armor_weaponsmith': ('minecraft:chests/village/village_weaponsmith', 'lamplighter_armor'),
    'armor_armorer': ('minecraft:chests/village/village_armorer', 'lamplighter_armor'),
    'armor_outpost': ('minecraft:chests/pillager_outpost', 'lamplighter_armor'),
}
for name, (table, ours) in MODIFIERS.items():
    write('vestige/loot_modifiers/%s.json' % name, {
        'type': 'neoforge:add_table',
        'conditions': [{'condition': 'neoforge:loot_table_id', 'loot_table_id': table}],
        'table': 'vestige:chests/' + ours})
write('neoforge/loot_modifiers/global_loot_modifiers.json', {'replace': False, 'entries': ['vestige:' + n for n in MODIFIERS]})


# ---------------------------------------------------------------- recipes: the Wanderer's Garb is tier 0 (craftable)
def shaped(name, pattern, key):
    write('vestige/recipe/%s.json' % name, {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
                                            'key': {k: {'item': v} for k, v in key.items()}, 'result': {'count': 1, 'id': 'vestige:' + name}})


L, W, S = 'minecraft:leather', 'minecraft:brown_wool', 'minecraft:string'
shaped('wanderer_hood', ['WLW', 'W W'], {'W': W, 'L': L})
shaped('wanderer_coat', ['L L', 'WSW', 'LWL'], {'L': L, 'W': W, 'S': S})
shaped('wanderer_trousers', ['WLW', 'W W', 'L L'], {'W': W, 'L': L})
shaped('wanderer_boots', ['W W', 'L L'], {'W': W, 'L': L})
print('ok')
