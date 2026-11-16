# Combat System (Milestone 3)

Implements GDD §7 (combat) and §8.1–8.2 (health scale, stamina). This document covers how the system works and how to extend it.

## Overview

```
 keyboard/mouse ──► ClientCombat ──► PlayerCombatMachine (prediction) ──► PlayerAnimations (playerAnimator)
                         │ CombatPayloads.Input
                         ▼
 server: PlayerCombat ──► PlayerCombatMachine (authoritative) ──► HitResolver ──► CombatDamage ──► target
             │ ActionStart / ActionEnd / ActionHold / HitFeedback / Event
             ▼
 all clients: ClientActions (mirror) ──► HollowedModel / PlayerAnimations / TelegraphFx / HitFx / LockOn
 enemies:  VestigeMob + ActionCombatGoal ──► same RunningAction + HitResolver + CombatDamage
```

**One action runtime for everything (ADR-006).** An *action* (`combat.action.ActionDef`) is a timed move with three phases: windup, active and recovery. Players, Hollowed and future bosses all run actions through `RunningAction`, so hit timing, hitstop, charge holds, telegraphs and animations behave the same everywhere.

**One timeline for animation and hits (ADR-007).** An action's `pose` names a sequence in `combat.anim.PoseLibrary`. Its keys are anchored to phases (e.g. "windup peak at the first active tick", "strike complete at the last active tick"), so `PoseTimeline` lays them onto the action's exact tick timing. The same timeline:
- becomes a playerAnimator keyframe animation for players (`client.anim.PlayerAnimations`), and
- is sampled every frame for enemy models (`client.entity.HollowedModel`).

Changing a number in a moveset JSON changes the hit window *and* the animation together.

## Modules

| Module | Responsibility |
|---|---|
| `vitals` | Stamina attachment and rules, ×5 health scale (`HealthScaling`), scaling of vanilla damage and healing |
| `weapon` | Weapon items and `WeaponProfile` data (class, moveset, attack rating, weight), synced to clients |
| `combat` | Actions, movesets, `PlayerCombatMachine`, server `PlayerCombat`, hit geometry, damage formula, payloads, telegraph particle types |
| `entity` | `VestigeMob` base class, `EnemyProfile` data, `ActionCombatGoal` AI, attack tokens, Hollowed and Hollowed Brute |
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
  - the first 3 i-frame ticks are a **perfect dodge** (screen flash and sound now; Resonance later).
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
- **`DamageCalculator`** (GDD §7.10): `raw = AR × MV`, `after = raw² / (raw + 2·defence)`, floored at 10% of raw. Staggered targets take ×1.5 (until ripostes arrive in M4).
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
Optional per action: `hyperarmor`, `charge {max_ticks, motion_bonus, poise_bonus}`, `dodge {iframe_start, iframe_end, distance, move_ticks}`, `telegraph {glint, lead, decal {type, radius, offset, length, width}}`, `tracking`, `inflict_stagger`, and `power.damage` (enemy attack rating).

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
| Hits land only in active frames, misses behind, stamina, rejection, i-frames vs. a real swipe (plus control), interrupt, poise break, charged > tapped, swap cancel, health scale, profile health, AI attacks | `gametest/combat/CombatTests` (GameTest) |
| The real client: prediction, HUD, animations playing, roll movement, lock-on, glint, dodge through a live attack, hits, decal, `/kill`, config screen drill-down | `scripts/client-driver.sh combat` (screenshots plus `DRIVER CHECK` lines) |
| Lock-on reticle against a stationary target | `scripts/client-driver.sh lock` |
| Rotation conventions (single-axis test poses) | `scripts/client-driver.sh calib` |

Selected screenshots from the real client are in [`docs/verification/m3/`](../verification/m3/).

**Driver rules learned the hard way:**
- Scenario steps are built when the mod loads, before any world or registry exists. Anything that reads game state (registries, the player, values for failure messages) must be a lambda or supplier.
- Wait for preconditions instead of fixed delays (e.g. `lockOnto` waits until the target is visible client-side and the player is armed).
- `client-driver.sh` deletes the previous log and stops on startup crashes, so a failed build is never mistaken for a test result.

**Not verifiable here:** sound (no audio device), and real-time "feel" at full frame rate (software rendering is slow; the driver slows the game with `/tick rate` to capture exact frames).
