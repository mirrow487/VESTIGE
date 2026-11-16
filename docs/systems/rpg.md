# Core RPG Systems (Milestone 4, stage 3)

Implements GDD §6.6 (Waylights), §8.4 (Wick Flask), §8.5 (death, Glim and the Ember-Ghost) and §23.1 (Glim). The code lives in two modules: `player` (purse, flask, ghost, run mode) and `waylight` (block, registry, rest and travel).

## Glim (`player`)

- A number, not an item: the `glim` attachment is saved, kept across death (at 0, since the ghost holds it), and synced to its owner.
- All changes go through `GlimService`.
- **Earned on kills:**
  - VESTIGE enemies give their profile's `glim`: Hollowed 32, Tolling Ram 64, Hollowed Brute 110.
  - Vanilla hostile mobs give a twentieth of their scaled max health; a zombie gives 5.
  - The reward goes to the kill credit (`getKillCredit`).
- **HUD:** bottom right, a slate-and-brass counter with an ember glyph. A "+N" rises and fades on every gain, accumulating if gains come quickly.

## Ember-Ghost

- **On death,** carried Glim becomes an `EmberGhost` where the player fell. If they fell into the void or lava, it appears where they last stood safely.
- **The ghost** is a GeckoLib hooded shade of embers (`tools/gen_models.py`): translucent and self-lit, it bobs, its flame tail flickers, and its nameplate shows the owner and amount.
- **Only the owner reclaims it,** by touching it.
- **Dying again first replaces it:** `EmberGhosts` (world SavedData) records each player's current ghost. The newer death removes the older ghost if it is loaded; if it isn't, the older ghost fades the next time it loads.

## Run Mode (`config: death.runMode`)

- **WAYFARER** (default): the inventory is kept. It is stashed at death, the drops are cancelled, and it is restored on respawn. Only Glim is left behind.
- **PILGRIM:** vanilla-like drops.
- **Ashen** (one life) is a world-creation choice and arrives with world presets.

## Wick Flask (`player`)

- **The `flask` attachment:** charges, max (3 + Flask Wicks, up to 10) and Kindling (0–5). It is saved, kept across death, and synced to its owner.
- **H drinks**, armed or not, with no stamina needed. It runs the shared action machine's `flask_drink` action:
  - an 8-tick raise;
  - 20 healing ticks that together restore 40% of max health (60% at Kindling +5);
  - an 8-tick lowering.
- **While drinking** the player can only walk (40% speed).
- **Charge timing:** the charge is taken on the first healing tick. A hit during the raise costs nothing, while a hit mid-drink stops the healing.
- **With no charges,** H plays `flask_empty`: a futile shake by the ear.
- **Refills** happen on resting at a Waylight and on respawning.
- **Flask Wick** (item): use to add a charge, up to 10. Until dungeons and bosses exist (M6, M11) it comes from the creative tab.
- **Visuals and HUD:**
  - the 3D flask (ember liquid, wax-sealed neck, smouldering wick) is drawn in the left hand while drinking (`FlaskLayer`);
  - the HUD's flask tile (the Readiness Cross's bottom tile) shows the remaining charges, red at 0.

## Waylights (`waylight`)

- **The block:** a lantern on a stone plinth (`tools/gen_item_models.py`). It gives light 14 when lit and 3 when not, smokes when cold, and drifts embers when lit. It cannot be broken outside creative and is never crafted (GDD §6.6).
- **Lighting:** using a Waylight you haven't lit lights it and records the discovery for you (per player). The **"WAYLIGHT KINDLED"** title shows its name: the block entity's name, an anvil-named item, or "<Biome> Waylight".
- **The menu** (using a lit Waylight): a slate panel with Rest, Travel and Leave. A refusal is shown as a reason, not just a greyed button.
- **Rest:**
  - sets the respawn point beside the Waylight;
  - refills the flask and restores health and stamina;
  - clears effects;
  - the player kneels (`rest_kneel`).
  - Refused in combat or with an enemy within 12 blocks.
- **Travel:** goes to any other Waylight you have lit, across dimensions, ordered by distance. You arrive standing beside it. Refused in combat or within 24 blocks of an enemy.
- **Registry:** `WaylightRegistry` (Overworld SavedData) holds every Waylight's position and name, and the menu packet carries the destinations.
- **Placement:** worldgen places Waylights in Milestone 6; until then they come from the creative tab or commands.

## Testing

| What | Where |
|---|---|
| Flask heal amounts, wick cap, Glim values, rest and travel refusals | `RpgRulesTest` (unit) |
| Flask heals 40% and uses a charge; empty flask; hit cuts the heal; wick cap; Glim on kill; death leaves a ghost; owner reclaims; others cannot; a newer ghost replaces the older; lighting; rest; rest refused with an enemy near; travel; travel to an unlit Waylight refused | `gametest/combat/RpgSystemsTests` (GameTest) |
| The real client: light two Waylights with real clicks (title card), menu, travel list, travel, rest (kneel, refill), drink (front, side, HUD), Glim from a kill (popup), death, respawn at the rested Waylight, Ember-Ghost, reclaim | `scripts/client-driver.sh rpg` |

Screenshots: [`docs/verification/m4/`](../verification/m4/) (`s3-*`).
