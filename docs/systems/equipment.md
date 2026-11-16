# Equipment, Attributes and Damage Types (Milestone 5)

Implements GDD §7.3 (stance swap), §7.6 (burden rolls), §7.10 (damage types and statuses), §8.6 (attributes and Attunement), §9 (equipment), §11 (armor sets) and §12 (accessories). The code lives in two modules:

- **`stats`**: pure, unit-tested rules with no dependencies: damage types, attributes, grades, burden, rarity, statuses and the stat sheet.
- **`equipment`**: slots, data tables, the stat calculator, stance, Attunement, Kindling, the menu and networking.

The client code (screen, HUD, tooltips, armor rendering) lives in `client/equipment` and `client/rpg`.

## The stat sheet (`stats`)

- **What it is:** a `StatSheet` collects everything that shapes a player's numbers:
  - attributes and level;
  - health and stamina bonuses;
  - equip load and capacity, burden and poise;
  - per-type absorption and defence;
  - damage, art, Resonance-gain, flask, status-resistance and Glim bonuses;
  - sprint modifiers, perks and set counts.
- **How it is computed:** `EquipmentStats.of(player)` builds it from attributes, armor, set bonuses, accessories, and the weight of the main and secondary weapons. It is cached by a fingerprint of everything that feeds it.
- **When it is computed:** the moment equipment, attributes or stance change, on both the server and the owning client. That is why the screen, the HUD and combat all agree at once.
- **How other modules read it:** through `Stats.of(player)`, a hook that the `equipment` module installs. The `combat`, `vitals` and `player` modules never import `equipment`.

| Feeds into | Effect |
|---|---|
| Max health | `vestige:vitality` modifier (Vitality + equipment), applied before the ×5 health scale |
| Max stamina and regeneration | `StaminaService` |
| Poise | Player flinch resistance: poise damage taken within 3 s accumulates; you flinch only once it exceeds your poise |
| Absorption and defence | `CombatDamage`, per type (absorption caps at 90%) |
| Burden | The roll you get (see below) |
| Perks | Named rules implemented where they act (ring, talisman, relic and set effects) |

## Attributes and Attunement (GDD §8.6)

Six attributes, base 10 and maximum 60. Level = 1 + the sum of points above base.

| Attribute | Raises | Scales |
|---|---|---|
| **Vitality** (VIT) | Max health: +4 per point to 20, +3 to 40, +1.5 to 60 | — |
| **Endurance** (END) | Stamina +2 per point to 30, then +1; equip capacity +1.2 per point; poise +0.4 per point | — |
| **Strength** (STR) | — | Physical damage of heavy weapons (grade per weapon) |
| **Dexterity** (DEX) | — | Physical damage of quick weapons and bows |
| **Cadence** (CAD) | Art damage up to +40%; Resonance gain up to +50% | Resonance, Hush and Void damage |
| **Lamplight** (LMP) | Wick Flask heal up to +15% of max HP; status resistance up to 40% | Cinder and Radiance damage |

- **Scaling curve:** soft-capped. It reaches 0.35 at 20, 0.80 at 40 and 1.0 at 60.
- **Attack rating:**
  - AR per type = base × (1 + Σ grade × curve) over the attributes that scale that type;
  - grades: S 1.4, A 1.1, B 0.8, C 0.55, D 0.35, E 0.15;
  - an unmet requirement multiplies every type by 0.6, and the tooltip says so in red.
- **Level defence:** +0.5 flat defence per level after the first. A fresh character keeps the Milestone 3–4 balance.
- **Attunement** (the Waylight menu's **Attune**):
  - plan points with +/−; the screen projects health, stamina, load, poise, attack, defence, flask, status resistance and art damage;
  - Confirm pays the whole plan at once.
  - Each point costs the next level's price, `round(120 + 18l + 0.9l² + 0.004l³)` Glim: 139 at level 1, 1,578 at level 30, 9,368 at level 80.
  - The server re-checks the price, the Waylight (lit and within 6 blocks) and the caps. A forged packet is refused; the multiplayer test covers this.

## Damage types and their identities (GDD §7.10)

Every type is more than a multiplier. Each one changes how a fight plays:

| Type | Identity |
|---|---|
| **Slash** | Builds **Rend** (at 0.2× damage): sustained cutting ends in a burst of 12% max HP + 5% of current health |
| **Strike** | +50% poise damage; a blocked Strike costs 1.4× guard stamina. It breaks guards and stances |
| **Pierce** | Counter-hits: ×1.25 against a target in its attack windup or active frames |
| **Cinder** | Builds **Scorch** (at 0.6×): 6 s of burning at 1.5% max HP per second, with all healing ×0.7 while it lasts |
| **Resonance** | Adds 0.8× its damage as poise damage; shields absorb only half of it. Sound passes through guards |
| **Hush** | Ignores half of the target's defence; builds **Hushed** (at 0.6×): players gain no Resonance and cannot use arts, enemies stop recovering poise and take +10% damage, for 10 s |
| **Void** | Ignores absorption entirely; builds **Voidbite** (at 0.5×): max HP −20% until you rest at a Waylight |
| **Radiance** | ×1.3 against pale, hush and void creatures, and the attacker heals 4% of the Radiance dealt |

- **Status meters:**
  - they fill to 100 and then trigger;
  - they decay after 3 s without buildup;
  - Lamplight, Bell of Warding and enemy profiles resist them;
  - they show under the vitals on the HUD (filling while building, the status name in capitals while active);
  - they are synced to the owning client.
- **Status damage** uses the `vestige:status` damage type, which ignores armor and i-frames, and has its own death message.
- **Enemies have families and weaknesses:** Hollowed and the Brute are pale, weak to Radiance and Resonance; the Tolling Ram is a beast, weak to Pierce, and resists Resonance.
- **Enemy attacks carry types too:** the Ram's toll ring is 70% Resonance and 30% Strike.

## Equipment (GDD §9)

| Slot | Count | Notes |
|---|---|---|
| Main Weapon, Secondary | 1 each | The weapon's or shield's weight counts toward burden |
| Head, Chest, Legs, Boots | 1 each | The vanilla armor slots: armor renders natively, vanilla armor uses the Common baseline |
| Rings | 2 | Stat-shaping |
| Talismans | 1 (2nd chained) | The 2nd unlocks after the Tithe-Engine (M8) |
| Relics | 2 (3rd and 4th chained) | 3rd after Mother Tallow (M6), 4th after the Resounding (M7) |
| Wick Flask | Fixed | Shown in the cross: charges and Kindling |

- **Rules:** `EquipmentRules.check` refuses a wrong kind, a locked slot, a duplicate, or a second accessory of the same family. The slot never accepts it, and the screen shows a chained tile with its unlock condition.
- **Data-driven:** armor (`vestige/armor`), accessories (`vestige/accessories`) and sets (`vestige/armor_sets`) are synced data tables generated by `tools/gen_equipment_data.py`. Weapons gained `damage`, `scaling`, `requirements`, `status` and `rarity` in `vestige/weapons`.
- **Rarity:** Common, Uncommon, Rare, Relic and Wick. It colours the tile's rule, the item name and the tooltip.

### Armor sets (3D, GeckoLib)

| Set | Weight (4 pieces) | 2 pieces | 4 pieces |
|---|---|---|---|
| Wanderer's Garb (hood with scarf, long coat, wraps) | 8.3 (Light) | +5% stamina regeneration | Sure Foot: fall damage −30% |
| Bellbronze Mail (bell helm with clapper, scale mail) | 20.6 (Medium) | +8 poise | Toll: every 4th weapon hit rings a shockwave (12 Resonance status damage, 15 poise, 2.5 block radius) |
| Wickiron Plate (visored helm with candle crown, plate with wax seams) | 32.7 (Heavy) | +10% physical absorption | Slow Burn: below 30% HP, +20% absorption and +15 poise |

- **Models:** each set is a GeckoLib armor model with an idle animation (cloth sway, the clapper, the candle flames), built by `tools/gen_models.py`.
- **First person:** VESTIGE chest armor is also drawn on the first-person arm (`RenderArmEvent`), so sleeves and gauntlets match the third-person model.
- **Recipes:** the Wanderer's Garb is craftable (leather, wool, string).

### Accessories (18)

- **Rings:**
  - Quick Breath: stamina regen +15%;
  - Bellbronze Band: +10 poise;
  - Waxen: a parry heals 5%;
  - Hollow: the roll becomes a blink-step;
  - Tithe: +8 Resonance per kill;
  - the Unsung: Resonance never decays;
  - Hart's: +12% damage above 80% HP;
  - Ember Signet: Cinder +10% and Scorch on hit.
- **Talismans:**
  - Lightfoot Feather: burden thresholds +15%;
  - the Grove: out-of-combat regen;
  - Ledger of Debts: +25% damage, but each hit taken costs 1% of your Glim;
  - Second Breath: a revive at 20% HP every 10 minutes;
  - Bell of Warding: Hushed buildup −60%.
- **Relics:**
  - Windsail Cape: glide by holding jump;
  - Bellbronze Spurs: sprint +15% at half the stamina;
  - Diver's Bell: water breathing and swim speed;
  - Climber's Hooks: cling to and climb walls;
  - Pale Hook: **G** grapples up to 24 blocks.
- **Families:** movement relics share families, so they can't be stacked against the design.
- **Cycling:** **Y** cycles the active relic, which is shown at the top of the cross.

### Burden and rolls (GDD §7.6)

| Burden | Load ratio (× 1 + threshold bonus) | Roll |
|---|---|---|
| Light | ≤ 30% | 4.5 blocks, i-frames 2–10, 14 stamina |
| Medium | ≤ 70% | 3.5 blocks, i-frames 3–10, 16 stamina |
| Heavy | ≤ 100% | 2.5 blocks, i-frames 4–8, 20 stamina |
| Overburdened | > 100% | A stumble (`roll_stumble` pose), 1.2 blocks, no i-frames, 25 stamina |

The Hollow Ring replaces any roll with a blink-step: 2.8 blocks over 3 ticks, i-frames 0–7. Roll modifiers do not stack.

### Combat stance (GDD §7.3)

- **V** moves the Main Weapon's real stack into the selected hotbar slot and the Secondary into the off-hand, stashing what was there. V again swaps back.
- **Forced exit:** before death drops (highest priority), logout, dimension change, opening any container, and server stop.
- **Dropping the stance weapon** is undone.
- Nothing is copied, so nothing can be duplicated; GameTests cover this.

### Wick Flask upgrades (GDD §8.4)

- **Kindle Flask** at a lit Waylight costs one **Kindling Ember** and 400 × (level + 1) Glim.
- It raises Kindling from +0 to +5, and the heal from 40% to 60%.
- Lamplight adds up to 15% of max HP on top. Scorch reduces all healing.

## The equipment screen (GDD §9.5)

**I** opens it (the server opens the menu; stance is exited first). The layout fits a 240-unit-tall GUI (1280×720 at GUI scale 3):

- **Header:** the title and a burden bar with Light and Medium tick marks and the burden class.
- **Left:** a column of four armor tiles.
- **Centre:** the Readiness Cross around a live paper-doll of the player (drag to turn it):
  - right: Main Weapon (large tile);
  - left: Secondary;
  - top: the active Relic (click to cycle);
  - bottom: the Wick Flask (charges and Kindling, with a tooltip).
- **Right:** rings, talismans and relics, with chained tiles for locked slots.
- **Stats column:**
  - level and the six attributes (raised ones in ember);
  - health, stamina, poise and flask heal;
  - attack per damage type of the main weapon.
- **Bottom:**
  - absorption per type, status resistance and defence (left);
  - the inventory grid (centre);
  - set bonuses with their text, wrapped (right).
- **Tile style:**
  - slate tiles (#121116 → #1c1a21) with a pale stone frame and a shadow line;
  - a 14-px rule tinted by rarity;
  - ghost silhouettes in empty slots;
  - a dark grain overlay that fades in over the world;
  - no HUD drawn underneath.
- **Tooltips:**
  - weapons: AR per type with green and red deltas against the equipped Main, scaling grades, requirements (red if unmet), status buildup and weight;
  - armor: absorption per type with deltas against the worn piece, poise, weight and the set with the active bonuses;
  - accessories: kind, effect and weight;
  - locked slots name their unlock.
- **Shift-click** routes items: weapons go to Main (then Secondary), shields to Secondary, armor to its slot, accessories to the first free slot of their kind.

## The HUD

- **Readiness Cross:** bottom left. Main, Secondary, active Relic and Flask with charges. Key hints: V, G, H. The trim turns ember in stance.
- **Status meters:** under the health, stamina and Resonance bars.

## Where items come from until their gates exist (interim)

Bosses, dungeons and NPCs arrive in Milestones 6–11. Until then:

| Source | Contents |
|---|---|
| Dungeons, mineshafts and ruined portals | Rings and talismans (`lamplighter_trinkets`), plus Kindling Embers and Flask Wicks |
| Desert and jungle temples, stronghold corridors | Relics, the Hollow Ring, the Ledger, Second Breath and the Unsung ring (`lamplighter_relics`) |
| Village weaponsmiths and armorers, pillager outposts | Bellbronze and Wickiron pieces (`lamplighter_armor`) |
| Hollowed Brute 15%, Tolling Ram 8% | Kindling Ember |

These use NeoForge `add_table` loot modifiers, so other mods' loot is untouched. Each item moves to its GDD source when that content exists.

## Keys

| Key | Action |
|---|---|
| I | Equipment screen |
| V | Combat stance |
| G | Use relic (Pale Hook) |
| Y | Next relic |

All keys are rebindable under the VESTIGE category.

## Testing

| What | Where |
|---|---|
| Attribute curves, level cost, attack rating, burden classes, damage-spread algebra | `StatsRulesTest` (unit, 6) |
| Attributes and health, attunement cost and refusal, strength scaling, slot rules, the Wickiron set and burden, burden picks the roll, every damage identity, Rend, Scorch, Voidbite (cured by rest), Hushed, player poise, Second Breath, stance without duplication, forced stance exit on a container, menu shift-click routing | `gametest/combat/EquipmentTests` (GameTest, 18) |
| Real client: screen empty and full, real shift-click equipping, tooltips (weapon, comparison, armor set, ring, locked slot), the doll turned, immediate stat changes, three armor sets front, back, side and first person, stance and the HUD cross, Pale Hook, Windsail glide, status HUD, Attunement (plan, pay, health up), Kindling | `scripts/client-driver.sh equipment` (24 checks) |
| Multiplayer: dedicated server and two clients. A equips through the menu, stats are synced and recomputed, a forged Attune packet is refused, relics cycle, stance and exit. B sees A's armor and stance weapons, and does not receive A's private slots | `scripts/multiplayer-test.sh` (11 checks) |

Screenshots: [`docs/verification/m5/`](../verification/m5/).
