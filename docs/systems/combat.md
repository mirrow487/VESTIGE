# Combat System (Milestones 3–4)

Implements GDD §7 (combat), §8.1–8.2 (health scale, stamina) and §8.3 (Resonance). This document covers how the system works and how to extend it.

## Overview

```
 keyboard/mouse ──► ClientCombat ──► PlayerCombatMachine (prediction) ──► PlayerAnimations (playerAnimator)
                         │ CombatPayloads.Input
                         ▼
 server: PlayerCombat ──► PlayerCombatMachine (authoritative) ──► HitResolver ──► CombatDamage ──► target
             │ ActionStart / ActionEnd / ActionHold / HitFeedback / Event
             ▼
 all clients: ClientActions (mirror) ──► TimelineGeoModel / PlayerAnimations / TelegraphFx / HitFx / LockOn
 enemies:  VestigeMob + ActionCombatGoal ──► same RunningAction + HitResolver + CombatDamage
```

**One action runtime for everything (ADR-006).** An *action* (`combat.action.ActionDef`) is a timed move with three phases: windup, active and recovery. Players, Hollowed and future bosses all run actions through `RunningAction`, so hit timing, hitstop, charge holds, telegraphs and animations behave the same everywhere.

**One timeline for animation and hits (ADR-007).** An action's `pose` names a sequence in `combat.anim.PoseLibrary`. Its keys are anchored to phases (e.g. "windup peak at the first active tick", "strike complete at the last active tick"), so `PoseTimeline` lays them onto the action's exact tick timing. The same timeline:
- becomes a playerAnimator keyframe animation for players (`client.anim.PlayerAnimations`), and
- is sampled every frame and blended over the GeckoLib bones of enemy models (`client.entity.TimelineGeoModel`, ADR-011).

Changing a number in a moveset JSON changes the hit window *and* the animation together.

## Modules

| Module | Responsibility |
|---|---|
| `vitals` | Stamina attachment and rules, ×5 health scale (`HealthScaling`), scaling of vanilla damage and healing |
| `weapon` | Weapon and shield items; `WeaponProfile` and `ShieldProfile` data, synced to clients |
| `combat` | Actions, movesets, `PlayerCombatMachine`, server `PlayerCombat`, guard/parry (`PlayerDefense`), riposte targeting, Resonance, hit geometry, damage formula, payloads, telegraph particle types |
| `entity` | `VestigeMob` and `AnimatedMob` (GeckoLib) base classes, `EnemyProfile` data, `ActionCombatGoal` AI, attack tokens, Hollowed, Hollowed Brute, Tolling Ram |
| `client` | Input and prediction, animations, lock-on, HUD, telegraph and hit effects, enemy renderer, dev test driver |

## Player combat

- **Armed** = holding an item that has a weapon profile (VESTIGE weapons and vanilla swords). While armed, LMB is the light attack, **R** is the heavy attack (hold to charge), **Left Alt** dodges (direction from movement keys, none = backstep), and **Middle mouse** toggles lock-on. Vanilla attack, mining and pick-block are suppressed while armed. *(The GDD §7.3 stance toggle (V) arrives with the RPG equipment slots in Milestone 5; until then, holding a weapon is the stance.)*
- **`PlayerCombatMachine`** runs identically on the client (prediction, instant animation) and the server (authority):
  - light combo chains inside the combo window (last 60% of recovery);
  - a 10-tick input buffer;
  - the combo resets after 12 idle ticks;
  - a light attack while sprinting is the **running attack**, and one within 6 ticks of a dodge is the **rolling attack**;
  - heavies pause at their windup peak while held (charge, up to `max_ticks`), and the motion value grows with the charge ratio;
  - a dodge can cancel only from 40% into an attack's recovery (commitment), and chained dodges need the previous dodge's combo window.
- **Stamina** must be above 0 to start an action, and the action may drain it to 0. Regeneration is 32/s after a 12-tick delay (half speed below 6 food) and pauses during actions. Creative players spend none.
- **Dodges:**
  - the client applies the movement (an ease-out curve that sums to the dodge distance);
  - the server applies i-frames from the action start, backdated by `combat.iframeLatencyTolerance` ticks;
  - attacks with an attacker, projectiles and explosions are cancelled during i-frames; lava, drowning and similar damage are not;
  - the first 3 i-frame ticks are a **perfect dodge** (screen flash, sound, +15 Resonance).
- **Being hit** by an enemy action interrupts the current action (8-tick flinch; grabs inflict a longer stagger), unless the action has hyperarmor and is in its windup.
- **Swapping items** mid-attack cancels the attack.
- **Server reconciliation:**
  - the client tags each input with a sequence number;
  - a server `ActionStart` with a matching sequence is ignored (already predicted);
  - `ActionEnd(REJECTED, seq)` cancels the prediction;
  - server-originated actions (flinch, stagger) are adopted.

## Hits and damage

- **`HitResolver`** sweeps the action's `HitShape` on every active tick. For arcs, only the slice swept during that tick is tested, so fast swings can't tunnel. Each target is hit at most once per action, and line of sight is required.
- **Shapes:** `arc` (reach, from, to in degrees; +right), `thrust` (capsule length and radius), `circle` (radius, offset ahead). All have a height band.
- **`DamageCalculator`** (GDD §7.10): `raw = AR × MV`, `after = raw² / (raw + 2·defence)`, floored at 10% of raw. Reeling targets (staggered or parried) take ×1.5 from ordinary hits; ripostes use their own motion value instead.
- **Defence:**
  - VESTIGE enemies use their profile `defense`;
  - other entities use the Common baseline from vanilla armor: `4·armor + 2·toughness`;
  - players currently get vanilla-derived defence (RPG armor stats come in M5).
- **Damage types** `vestige:weapon` and `vestige:enemy_attack` are tagged `bypasses_armor`, `bypasses_cooldown` and `no_knockback` (VESTIGE handles all three) plus `vestige:native_scale` (already in ×5 units).
- **Poise:**
  - enemies flinch when hit outside hyperarmor;
  - when poise breaks they **stagger** (40–50 ticks; hits deal critical damage);
  - poise refills after `poise_regen_delay` ticks without being hit;
  - vanilla mobs get a small knockback instead.
- **Hitstop:** on a hit, the attacker's and target's timelines pause for `hitstop` ticks on the server, and every client pauses the same actions (animation speed 0). Camera shake, sparks and weight-scaled sounds play on top.

## Enemies and telegraphs

- **`EnemyProfile`** (`data/<ns>/vestige/enemies/<entity>.json`): moveset, health, defence, poise, engagement distances, the pause between attacks, and an attack table (weight, range, cooldown, follow-up and its chance).
- **`ActionCombatGoal`:**
  - approach, hold spacing, strafe (switching side every 2–4 s);
  - pick a usable attack by weight;
  - at most **2 enemies attack the same target at once** (`AttackTokens`);
  - a random pause between attacks keeps fights readable.
- **During an action:** the enemy turns toward its target only in the windup (`tracking` degrees per tick) and is locked during the active and recovery frames, so a well-timed sidestep or roll works. Root motion moves it.
- **Telegraphs** (`telegraph` in the action):
  - a **glint** flashes `lead` ticks before the active frames: white = parriable, red = roll, grab = red plus a rising three-note chime;
  - a **ground decal** fills up over the windup where the attack will land;
  - the lock-on reticle turns red while the target winds up a red or grab attack;
  - `CombatDataConsistencyTest` enforces that every enemy attack has a telegraph, a hitbox and a windup of at least 8 ticks.

## Guard, parry and riposte (M4 stage 1, GDD §7.7–7.8)

**Inputs:** RMB guards (unless you are aiming at a block without a lock-on, so doors and chests still work), **F** uses the weapon art. The vanilla offhand-swap key is swallowed while armed.

**Guard** is a held action: the guard action pauses at its windup peak while RMB is held (the same mechanism as a charged heavy). Which guard you get depends on the offhand (`PlayerCombat.guardActionFor`):

| Offhand | Action | Behaviour |
|---|---|---|
| Nothing / non-shield | `guard_weapon` | Weapon guard: absorption 0.55, stability 30 |
| Kite or bulwark shield (incl. vanilla shield) | `guard_shield` | `ShieldProfile` absorption and stability (vanilla shield: 0.9 / 55) |
| Buckler | `parry_buckler` | Not a guard: a 2/4/12 parry with its window on ticks 2–5 |

- While guarding: stamina regenerates at half speed and you walk at 35% speed. A block costs `damage × (1 − stability/100) × 0.9 + 3` stamina and the guard cone is 120°.
- **Guard break:** a block that empties stamina interrupts with `guard_break` (24 ticks).
- **Grabs** (red glint plus chime) cannot be blocked or parried.

**Parry** (`PlayerDefense.resolve`):
- An attack lands inside the action's `defense.parry` window (backdated by the latency tolerance), the attacker is in a 100° front cone, and the attack is parriable (white glint or none).
- **Result:**
  - the attacker is interrupted into `parried` (a 45-tick reel);
  - the player gets hitstop 7, +20 Resonance, a gold flash and a bell sound;
  - the parry becomes cancellable as soon as its hitstop ends, so the riposte follows immediately.
- An early press fails: the window is the only thing that counts.

**Riposte** (`RiposteTargeting`): a light attack becomes the weapon's `riposte` when a target is either
- **reeling** (parried or poise-broken), within 2.8 blocks and 50° of your aim, or
- facing away by at least 110° within 2.2 blocks (a **backstab**).

The target is locked into `riposted` (it turns to face you and kneels) for the riposte's duration. The riposte is invulnerable, uses its own motion value (blade/pike 3.0, maul 2.6) and gives +15 Resonance. The lock-on reticle shows a gold prompt while a riposte is available.

## Resonance (GDD §8.3)

Resonance is the combat-flow meter, not mana: you fill it by fighting well and lose it by getting hit.

| Event | Change |
|---|---|
| Landing a hit | +3 to +8 by weapon weight |
| Block | +2 |
| Perfect dodge | +15 |
| Parry | +20 |
| Riposte | +15 |
| Taking an unblocked hit | −10 |
| 120 ticks out of combat | decays 4/s |

**Thresholds:**
- **Resonant (≥50):** your hits deal +20% poise damage, which shortens the path to a stagger and then a riposte.
- **Crescendo (100):** the next art deals +30% damage.

The HUD bar shows the threshold tick, the current art's cost tick and a pulse at Crescendo. Note particles orbit the player while Resonant.

**Weapon arts** (`art {cost, on_parry}` on an action, started with F, refused without enough Resonance):

| Class | Art | Cost | What it does |
|---|---|---|---|
| Blade | Riposte Stance | 20 | A 13-tick parry stance; a parry triggers the automatic `riposte_counter` (MV 2.2) |
| Pike | Impaling Charge | 30 | A long-reach running thrust that inflicts a 30-tick stagger |
| Maul | Tremor | 35 | An overhead slam with hyperarmor; 3.5-block ring, 70 poise damage |

## Weapon classes (M4 stage 2, GDD §7.4, §7.11, §10)

Every class has light combos, a chargeable heavy, running and rolling attacks, an art and a riposte, with timings from GDD §7.4 and motion values from §7.5. Each moves differently: the poses are authored per class in `PoseLibrary` and the 3D item models in `tools/gen_item_models.py`.

| Class (item) | Feel | Art (cost) | Notes |
|---|---|---|---|
| Fang (Taper Fang) | Low crouch, 3-tick cuts, 5-hit combo ending in a 360° spin | **Quickstep** (15): a 5-tick i-frame blink in the input direction; the next hit within 1 s is a critical (on a reeling target it uses the riposte motion value) | Riposte MV 3.5: two quick stabs |
| Greatblade (Wickiron Greatblade) | Two-handed 12-tick sweeps with follow-through; heavy is a whirling 360° cleave | **Cleaving Wake** (35): rise and slam, then a shockwave runs 7 blocks ahead (a `thrust` hitbox drawn as ground debris, `fx: "wake"`) | Light reach 3.3 |
| Censer (Chandler's Censer) | Wide flail arcs with long active frames; the pot is flung by item rotation; heavy is a spin | **Smolder Wake** (25): spin, then an incense cloud (radius 4, wider than the spin) burns enemies inside every 10 ticks for 5 s (`IncenseCloud`, no poise damage) | Cloud is transient, never saved |
| Longbow (Hunting Bow) | Quick shot (light) or hold-to-draw aimed shot (heavy, +0.9 MV and +45% speed at full draw) | **Pinning Shot** (25): braced full draw; the arrow roots the target (40-tick stagger) | Uses arrows from the inventory (creative: none needed); no arrows = no shot. Arrows (`CombatArrow`) deal damage through `CombatDamage` with ranged feedback |
| Bell (Handbell) | Tolls are sound rings: Peal (a small ring ahead), Toll (heavy, a 4.5-block ring around you) | **Toll** (20): a 7-block ring that interrupts everything it passes (forced stagger) | Damage type is physical until the Resonance damage type arrives (M5) |
| Shields | Kite (Iron Kite Shield, vanilla shield): block. Bulwark (Timber Bulwark): absorbs everything physical, stability 78, 22% walk speed | n/a | `guard_bulwark` action; braced pose |

**Action effects** (`ActionDef.Effects`, fired on an action's first active tick):
- `projectile {speed, charge_speed, ammo}`: looses a `CombatArrow` along the player's aim (lock-on already aims).
- `cloud {radius, duration, interval, motion_value, offset}`: spawns an `IncenseCloud`.
- `fx: "wake"`: ground shockwave visual along a thrust hitbox.
- `keen` ticks: the next hit within them is a critical.

**RING hitbox:** a band that expands from 0 to `reach` over the active ticks, centred `offset` ahead; a low `y_max` makes it jumpable. `ActionFx` draws every ring exactly where its hitbox is (gold for players, red for enemies), with a bell sound.

**`CombatDamage.Options`:** melee (attacker hitstop), ranged (no attacker hitstop; the archer hears a hit confirmation), cloud (damage only), keen (forced critical).

**Sprinting** in combat stance (armed) costs 6 stamina per second (GDD §8.2); at zero the sprint stops on both the server and the client.

## Tolling Ram (GDD §18.1)

A GeckoLib quadruped (`TollingRam`, `tools/gen_models.py`): matted fleece, dark ridged spiral horns, a bronze bell on a leather collar. Its lesson is **rolling sideways**.

| Attack | Telegraph | What to do |
|---|---|---|
| **Butt Charge** | Paw-scraping windup (18 ticks), red glint, a 9-block line on the ground. It aims only during the scrape | Roll or step sideways once it starts running; it gallops 8 blocks straight, then skids and shakes its head (punish window). A hit staggers you |
| **Toll Ring** | Rears up, red glint, a 5-block circle | It slams down and rings its bell: a low expanding ring. Jump it or roll through it |
| **Headbutt** | Head rears back, white glint | Parriable |

It spawns in plains, sunflower plains and meadows in daylight. Its reactions (`ram_flinch`, `ram_stagger`, `ram_parried`, `ram_riposted`: the forelegs fold and it kneels) are its own poses.

**Quadruped animation:** `TimelineGeoModel` maps the shared timeline channels onto bones through a binding table:
- arm channels → forelegs, leg channels → hind legs;
- torso channels → neck;
- body channels → the whole animal.

So the same pose language and frame-locked hitboxes work for any creature.

## Models and animation quality

- **Enemies** (`TimelineGeoModel`, ADR-011 rev. 2):
  - Hollowed and Hollowed Brute are GeckoLib models built by `tools/gen_models.py` (`.geo.json`, `.animation.json` and a 128×128 painted texture, all Blockbench-compatible);
  - keyframed idle, walk (speed follows limb swing) and death (collapse and hold, then a poof after 32 ticks);
  - combat poses are sampled from the shared timeline every frame and blended over the keyframed bones (smoothstep, 2.5 ticks in, 4 out), with elbows/knees on separate forearm/shin bones and a root height channel for kneels.
- **Weapons** (`tools/gen_item_models.py`): all eleven weapons and shields are 3D element models with bevelled parts and painted part atlases (2 texels per unit). The Hunting Bow switches to a drawn model (string at the cheek, arrow nocked) while a shot is drawn (`vestige:draw` item property).
  - Each weapon is built along its own axis, then rotated −45° about the grip, so it sits exactly where the flat sprite sat; every calibrated pose stays valid.
  - The inventory and item frames keep the crisp 16×16 sprite (`neoforge:separate_transforms`).
- **Player poses** follow anticipation → snap → overshoot → settle, for example:
  - the buckler parry tucks, punches across the chest (face forward, visible in first person), then swats outward;
  - Tremor leans back overhead, then drives the maul head into the ground.
  - playerAnimator renders the offhand item in first person during actions, so the buckler is visible while parrying.

## Lock-on

The middle mouse button locks the nearest mob in a 60° cone within 24 blocks that is in line of sight. Scroll switches to the nearest target on that side. The reticle (a brass diamond, red while the target winds up an attack you must dodge) is drawn just in front of the target's chest, toward the camera, so the target's own model never hides it. The lock drops beyond 28 blocks, after 2 s without sight, or when you stop holding a weapon. The player's real yaw and pitch follow the target with a critically damped follow (rate 14/s, pitch clamped to ±50°), so walking becomes strafing and the server sees the same facing. Mouse look is disabled while locked.

## Animation conventions (measured in-game)

`combat.anim.Channel` defines one convention for all back ends. Positive yaw and roll mean toward the entity's right, negative pitch raises a limb forward, positive torso pitch leans forward, positive body pitch tips the model forward, and positive item pitch tips a held item's point forward.

playerAnimator mirrors **torso yaw, torso roll, body yaw and body roll**. This was measured with the `calib` driver scenario: single-axis poses held on a player and screenshotted from the front. `PlayerAnimations` negates those four channels when converting, so pose data never has to care. Arms, head, item rotations and torso pitch match vanilla model parts.

## Data formats

### Moveset (`data/<ns>/vestige/movesets/<id>.json`)
```json
{
  "actions": {
    "light_1": {
      "timing": {"windup": 5, "active": 3, "recovery": 7, "combo_window": 0.4, "cancel_window": 0.4, "hitstop": 3},
      "power": {"stamina": 12, "motion_value": 1.0, "poise_damage": 8},
      "hit": {"type": "arc", "reach": 2.6, "from": 70, "to": -70, "y_min": 0, "y_max": 2.2},
      "motion": {"active": 0.10},
      "pose": "blade_slash_rl"
    }
  },
  "light": ["light_1", "light_2"], "heavy": "heavy", "running": "running", "rolling": "rolling"
}
```
Optional per action: `hyperarmor`, `charge {max_ticks, motion_bonus, poise_bonus}`, `dodge {iframe_start, iframe_end, distance, move_ticks}`, `telegraph {glint, lead, decal {type, radius, offset, length, width}}`, `tracking`, `inflict_stagger`, `defense {guard, parry {start, end}, invulnerable}`, `art {cost, on_parry}` and `power.damage` (enemy attack rating). A moveset may also name its `art` and `riposte` actions; enemy movesets must contain `parried` and `riposted`.

### Shield (`data/<ns>/vestige/shields/<id>.json`)
```json
{"items": ["vestige:plank_buckler"], "kind": "buckler", "absorption": 0.7, "stability": 35, "weight": 1.5}
```

### Weapon (`data/<ns>/vestige/weapons/<id>.json`)
```json
{"items": ["vestige:keepsake_blade"], "class": "blade", "moveset": "vestige:blade", "attack_rating": 60, "poise_damage": 1.0, "weight": 3.0}
```

All combat data is generated by `tools/gen_combat_data.py` and checked by `CombatDataConsistencyTest`.

## Adding content

- **A weapon of an existing class:** add the item (`weapon.WeaponModule`) and a weapon profile JSON. No other code is needed.
- **A new weapon class:**
  1. write the moveset JSON;
  2. add its pose sequences to `PoseLibrary`;
  3. run `scripts/client-driver.sh anim` to check the poses.
- **A new enemy:**
  1. create a `VestigeMob` subclass (or reuse `Hollowed` for humanoids);
  2. register the entity type;
  3. add a profile JSON, a moveset JSON (it must include `flinch` and `stagger`) and the poses;
  4. add a renderer.

## Testing

| What | Where |
|---|---|
| Machine rules: combos, buffer, charge, dodge windows, i-frames, hitstop, latency tolerance | `PlayerCombatMachineTest` (unit) |
| Hit shapes, coordinate frames | `HitGeometryTest` (unit) |
| Damage formula | `DamageCalculatorTest` (unit) |
| Pose anchoring and easing | `PoseTimelineTest` (unit) |
| Data references and readability rules | `CombatDataConsistencyTest` (unit) |
| Guard/parry/art/riposte machine rules, riposte geometry, Resonance rules | `PlayerCombatMachineTest`, `CombatDepthRulesTest` (unit) |
| Bow hits at range and needs arrows, Pinning Shot roots, Toll hits all around, incense burns over time, Cleaving Wake reach (and control), Quickstep critical, bulwark absorbs, armed sprint costs stamina | `gametest/combat/CombatArsenalTests` (GameTest) |
| Ram charge hits in line and staggers, sidestep beats it, Toll Ring hits grounded players but is jumped | `gametest/combat/TollingRamTests` (GameTest) |
| Ring hitbox expansion, coverage and jumpability | `HitGeometryTest` (unit) |
| Weapon guard, guard break, buckler parry, early parry fails, grabs unparriable, riposte after parry, backstab, art spends Resonance, Riposte Stance counter, Crescendo bonus, Resonance gain/decay, shield beats weapon guard | `gametest/combat/CombatDepthTests` (GameTest) |
| Hits land only in active frames, misses behind, stamina, rejection, i-frames vs. a real swipe (plus control), interrupt, poise break, charged > tapped, swap cancel, health scale, profile health, AI attacks | `gametest/combat/CombatTests` (GameTest) |
| The real client: prediction, HUD, animations playing, roll movement, lock-on, glint, dodge through a live attack, hits, decal, `/kill`, config screen drill-down | `scripts/client-driver.sh combat` (screenshots plus `DRIVER CHECK` lines) |
| Real client: guards, buckler parry against a live Hollowed (reel, Resonance), riposte kill, backstab, all three arts, guard released server-side | `scripts/client-driver.sh depth` |
| Every stage 2 class from the side (windup peak, strike, art) and first person; drawn bow, aimed shot hitting a dummy, ring/wake/cloud visuals; kite and tower guards | `scripts/client-driver.sh arsenal` |
| Tolling Ram poses (scrape, gallop, skid, toll, headbutt, reactions, death) and a live charge rolled out of | `scripts/client-driver.sh ram` |
| 3D weapon models present, in hand (front/side/first person), mid-swing, arts from the side, buckler parry frames, on the ground, flat inventory icons | `scripts/client-driver.sh items` |
| GeckoLib enemy models: poses front/side, death | `scripts/client-driver.sh models` |
| Lock-on reticle against a stationary target | `scripts/client-driver.sh lock` |
| Rotation conventions (single-axis test poses) | `scripts/client-driver.sh calib` |

Selected screenshots from the real client are in [`docs/verification/m3/`](../verification/m3/) and [`docs/verification/m4/`](../verification/m4/).

**Driver rules learned the hard way:**
- Scenario steps are built when the mod loads, before any world or registry exists. Anything that reads game state (registries, the player, values for failure messages) must be a lambda or supplier.
- Wait for preconditions instead of fixed delays (e.g. `lockOnto` waits until the target is visible client-side and the player is armed).
- `client-driver.sh` deletes the previous log and stops on startup crashes, so a failed build is never mistaken for a test result.

**Not verifiable here:** sound (no audio device), and real-time "feel" at full frame rate (software rendering is slow; the driver slows the game with `/tick rate` to capture exact frames).
