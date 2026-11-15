# VESTIGE: Game Design Document

**Platform:** Minecraft Java Edition 1.21.1 · NeoForge · Java 21 · Gradle
**Document status:** v1.2: design baseline. Milestones 0–2 are complete; gameplay systems start in Milestone 3. Implementation details are in [ARCHITECTURE.md](ARCHITECTURE.md).

**Revision history**
| Version | Milestone | Changes |
|---|---|---|
| 1.0 | M0 | Initial design |
| 1.1 | M1 | Development priorities (§0), equipment UI from the reference image (§9.5), Nether border divisor 4 (§6.1), Hush generation via biome source (§14.2), stronghold distance (§6.3), stance item swap (§7.3), combat key context (§7.2) |
| 1.2 | M2 | §27: config options now ship with their systems (current keys listed); §30: milestone status. No gameplay design changes |
**Scope note:** This is the design target. Every milestone will cut it into shippable, tested pieces. A feature counts as done only after it has been built and tested in-game.

---

## Table of Contents

0. [Development Priorities](#0-development-priorities)
1. [Vision](#1-vision)
2. [Design Pillars](#2-design-pillars)
3. [World Premise & Narrative](#3-world-premise--narrative)
4. [Gameplay Loop](#4-gameplay-loop)
5. [Progression Structure](#5-progression-structure)
6. [The World: Rings, the Pale, and Biomes](#6-the-world-rings-the-pale-and-biomes)
7. [Combat System](#7-combat-system)
8. [Player Resources & Survival Rules](#8-player-resources--survival-rules)
9. [Equipment System](#9-equipment-system)
10. [Weapons](#10-weapons)
11. [Armor Sets](#11-armor-sets)
12. [Accessories: Rings, Talismans, Relics](#12-accessories-rings-talismans-relics)
13. [Materials & Crafting](#13-materials--crafting)
14. [The Hush (Sculk Evil Biome)](#14-the-hush-sculk-evil-biome)
15. [The Nether: The Tithelands](#15-the-nether-the-tithelands)
16. [The End: The Unhatched Reach](#16-the-end-the-unhatched-reach)
17. [Boss Roster](#17-boss-roster)
18. [Enemy Bestiary](#18-enemy-bestiary)
19. [Structures](#19-structures)
20. [NPCs, Housing & Settlements](#20-npcs-housing--settlements)
21. [Quests & the Lamp Ledger](#21-quests--the-lamp-ledger)
22. [World Events](#22-world-events)
23. [Economy & Loot](#23-economy--loot)
24. [User Interface](#24-user-interface)
25. [Art & Audio Direction](#25-art--audio-direction)
26. [Multiplayer Rules](#26-multiplayer-rules)
27. [Configuration](#27-configuration)
28. [Content Budget](#28-content-budget)
29. [Technical Risks: Resolved in Milestone 1](#29-technical-risks-resolved-in-milestone-1)
30. [Milestone Roadmap](#30-milestone-roadmap)
31. [Glossary](#31-glossary)

---

## 0. Development Priorities

These are binding project priorities set by the project owner. When a trade-off appears, they win.

1. **Full scope is the target.** 7 bosses (+1 optional), ~35 enemy types, and ~45 weapons stay as long-term targets. Content is implemented gradually, never cut to reduce effort.
2. **Combat comes first.** The Elden Ring-inspired combat (smooth animations, dodge rolls, stamina, weapon-specific movesets, boss attack patterns) is the most important system. Other systems may wait for it; it may not be compromised for them.
3. **NPC housing is essential.** NPCs must move into suitable player-built houses and have individual shops, dialogue and unlock conditions.
4. **Every dimension is a full region.** The Overworld, Nether and End each get meaningful exploration, unique structures, caves, enemies, equipment and bosses.
5. **The Hush is a true evil biome**, not a recolored Deep Dark: its own surface, caves, spread, enemies, structures, materials and boss.
6. **The Dragon Egg boss (Vael) is a centerpiece** encounter with original mechanics and a unique design.
7. **Minecraft visual identity + dark fantasy.** Blocky, pixel-art, readable, and moody.

Core gameplay systems are never sacrificed to reduce scope. If a milestone runs long, it is split, not hollowed out.

---

## 1. Vision

VESTIGE turns a Minecraft world into a **finite, hand-crafted-feeling RPG**. The playable world starts small, enclosed by a wall of unmaking called **the Pale**. Each major boss you defeat pushes the Pale outward and returns stolen land: harder regions, new biomes, deeper dungeons. Terraria's progression is the backbone: bosses, events, and new materials gate each other in a web. Elden Ring's combat is the moment-to-moment feel: stamina, rolls, telegraphs, poise, and weapon arts. Minecraft's sandbox holds it together. You still mine, build, and shape the world, and the houses you build bring the settlement to life.

**One-sentence pitch:** *Reclaim a dying world one boss at a time, fighting with deliberate Souls-like combat, building a town of refugees, and choosing at the end whether the world should stay small and safe or be opened again.*

**What makes it not "a pile of mobs and items":**
- Every boss drops a **Wick**, and lighting it at the Heartlamp physically expands the world border. Progression is visible on the map.
- Every structure exists to teach, reward, gate, or explain something.
- Every new material comes from a specific place and enables a specific next step.
- Earlier bosses' relics are needed again at the climax, so nothing becomes obsolete trivia.

---

## 2. Design Pillars

| Pillar | Meaning | Test question for any feature |
|---|---|---|
| **Reclamation** | The world grows as you progress. Space is a reward. | Does this give the player a reason to push outward? |
| **Deliberate combat** | Every attack, roll, and heal is a commitment. | Can the player read it, react to it, and learn it? |
| **Sound & silence** | Sound is a resource and a threat, central to the Hush and the Warden. | Does this make the player listen? |
| **A home worth defending** | The settlement is the emotional anchor and the hub. | Does this make the town more alive or more useful? |
| **Still Minecraft** | Blocky, readable, buildable. No realism creep. | Would this look right next to a vanilla oak tree? |

**Anti-goals:** XP-level grinding as the main progression; random loot spam; bosses that are just big HP bars; structures with no purpose; unreadable "gotcha" attacks.

---

## 3. World Premise & Narrative

### 3.1 The Vestige

The world was once boundless. An order of keepers, **the Lamplighters**, saw something stirring at the edges of the world and lit the **Heartlamp**, a great flame that raised the Pale, a fog-white wall of unmaking, and folded the world down to a small, defensible remnant: *the Vestige*.

To keep the Pale stable, they bound its edge to **Wicks**: living anchors entrusted to great stewards. Centuries later the stewards have rotted into monsters, but the Wicks still hold. Kill a steward, carry its Wick back to the Heartlamp, and the Pale recedes. The land beyond comes back, older and wilder.

### 3.2 What the player slowly learns

1. **The Hush** (sculk) is not the enemy the Lamplighters feared. It is a scar: a silence left behind when something *ate sound*. The Warden did not create the Hush. It is the Hush's prisoner, swollen with every sound the silence devoured.
2. **The Ender Dragon was a jailer**, not a tyrant. She was the mother of an heir she refused to let hatch, and her egg is the thing the Lamplighters fled from.
3. **The Pale was not built to keep danger out.** It was built to keep the world *too small to be worth devouring*.

### 3.3 The ending choice

After the Heir dies, the Heartlamp gutters and the last Lamplighter wakes. Beat them (the optional superboss) or simply talk to them, and you get the final choice:

- **Rekindle:** The Pale holds at its current radius forever. Hush spread stops permanently. The world becomes a calm, finished place: a building and living epilogue.
- **Unlight:** The Pale falls. The border expands to the configured maximum (or is removed). Hush spread speeds up and "Pale-touched" elites roam the far lands: an endless post-game.

This uses the world border, a core Minecraft system, as the vehicle for the narrative's ending.

### 3.4 Tone

Melancholic, quiet, strange, and occasionally warm. The NPCs are refugees with dry humor. The world is not grimdark gore. It is **faded**: candles, bells, wax, rust, ash, and silence.

---

## 4. Gameplay Loop

### 4.1 Moment-to-moment (seconds)
Read a telegraph → roll or block → punish in the recovery window → manage stamina → spend Resonance on a weapon art when it counts.

### 4.2 Expedition (10–40 minutes)
Leave the settlement → travel to a Waylight → explore a structure or cave → fight elites and a miniboss → find gear, materials, or lore → rest at a Waylight (refill flasks) → return or push deeper.

### 4.3 Chapter (2–4 hours)
Gather materials → upgrade at the blacksmith → find the boss's summon or key → defeat the boss → carry its Wick to the Heartlamp → **the Pale recedes** → new ring, new NPCs, new recipes.

### 4.4 Campaign (~25–40 hours)
Embers (Overworld) → Ashes (Nether) → the Hush (Warden) → **the Resounding** (the world changes) → the End → the Heir → the Choice.

### 4.5 First hour (target experience)

1. Spawn in the **Hearthstead**, a small ruined courtyard around the dim Heartlamp. **Wren**, the Lamplighter's apprentice, is here.
2. Wren offers a **Keepsake**: choose one starting weapon (Blade, Pike, Hand Maul, or Hunting Bow) and receive a Wick Flask (3 charges) and the **Lamp Ledger** journal.
3. A short tutorial path teaches light attack, heavy attack, roll, and lock-on against Hollowed.
4. Wren points you to the nearest **Waylight**. Lighting it teaches respawn, flask refills, and fast travel.
5. You see the Pale on the horizon. The Ledger lists the first goal: "The bell still rings in the paddock to the east."
6. You build a shelter. Wren notes that refugees will come "if there's a proper roof, a light, a chair and a table." Housing is introduced.

---

## 5. Progression Structure

### 5.1 Acts

| Act | Name | Region | Bosses | Gate out |
|---|---|---|---|---|
| I | **Embers** | Overworld: Hearthland and Marches | The Bellwether, Mother Tallow | Hearthkey opens the Counting House in the Nether |
| II | **Ashes** | Nether (the Tithelands) | The Tithe-Engine | The Mute Bell can be forged |
| III | **The Hush** | Hush scars and the deep dark | The Warden of the Unsung | Defeat triggers **the Resounding** |
| IV | **Echoes** | Post-Resounding Overworld: Wilds and Reaches | (Elites, events, Resonite tier) | Eyes of Ender awaken |
| V | **The Unhatched Reach** | The End | Ender Dragon, the Severed Archivist, Vael the Unhatched Heir | The Heartlamp gutters |
| Epilogue | **The Last Light** | The Heartlamp Sanctum | The Last Lamplighter (optional) | The Choice |

### 5.2 Progression web (hard and soft gates)

```
                 Start (Hearthland, Pale radius 750)
                               │
          ┌────────────────────┴───────────────────┐
   [B1] The Bellwether                      Hush scar (dangerous early,
   → Bellwether Wick, Resonant Clapper       Sculkbone side-path)
   → Bellbronze tier, Old Rivet arrives
          │
   [B2] Mother Tallow
   → Tallow Wick, Tallow Ember
   → Wickiron tier, 3rd relic slot
   → Hearthkey (Tallow Ember + gold)
          │
   [B3] The Tithe-Engine (Nether, Counting House)
   → Tithe Wick, Gilded Heart, Assay Gold
   → Cindersteel tier, Oru arrives, 2nd talisman slot
          │
   Mute Bell = Resonant Clapper + Hushsilk ×8 + Assay Gold ×4
          │
   [B4] The Warden of the Unsung (Unsung Nave)
   → Unsung Wick, Unsung Echo
   ═══ THE RESOUNDING ═══
   → Resonite ore wakes, Hush spread accelerates, Eyes of Ender work,
     Echo Duelists appear, 4th relic slot
          │
   [B5] Ender Dragon (vanilla fight, rebalanced HP)
   → Dragon Egg
          │
   [B6] The Severed Archivist (Null Archive, outer End)
   → Archivist's Index reveals the Hatchery
          │
   Brooding Altar: Dragon Egg + Tallow Ember + Gilded Heart + Unsung Echo
          │
   [B7] Vael, the Unhatched Heir
   → Heir's Wick (final Pale expansion), Heirshell tier
          │
   [Epilogue] The Last Lamplighter → The Choice
```

**Hard gates** (cannot be bypassed): the Wick → border expansion; the Hearthkey → Counting House; the Mute Bell → Warden; the Resounding → Eyes of Ender function and Resonite becomes mineable; the Archivist's Index → the Hatchery is revealed; the Brooding Altar → Vael.

**Soft gates** (allowed but brutal): the Hush before the Warden; the Nether before the Tithe-Engine; outer rings near the Pale. Skilled players can sequence-break *within* a gate. That is part of the fun.

### 5.3 Why not levels?

The player's power comes from:
1. **Gear tier** (materials unlocked by bosses).
2. **Reinforcement** (+0 to +10 at the blacksmith).
3. **Reforging** (Terraria-style prefixes).
4. **Permanent pickups:** Ember Hearts (max HP), Breath Stones (max stamina), Flask Wicks (flask charges).
5. **Slot unlocks** (accessory slots tied to boss milestones).
6. **Build choices** (weapon class, burden, accessory synergies).
7. **Player skill.**

Vanilla XP stays for enchanting and repair only. It never increases combat stats.

---

## 6. The World: Rings, the Pale, and Biomes

### 6.1 The Pale (configurable world border)

The Overworld world border *is* the Pale. It expands as Wicks are lit at the Heartlamp.

| Event | Default radius (blocks) | Diameter | Ring unlocked |
|---|---|---|---|
| World start | 750 | 1,500 | Hearthland |
| Bellwether Wick lit | 1,000 | 2,000 | Marches (part) |
| Tallow Wick lit | 1,250 | 2,500 | Marches |
| Tithe Wick lit | 1,750 | 3,500 | Wilds |
| Unsung Wick lit (Resounding) | 2,500 | 5,000 | Reaches |
| Heir's Wick lit | 3,000 | 6,000 | Pale Verge |
| Ending: Unlight | config max (default 6,000) | 12,000 | Everything |

- Expansion is **animated**: the border lerps outward over 60 seconds with a world-wide sound cue and a Ledger entry.
- All values, the center, and the expansion speed are configurable. A "static border" option disables progression expansion.
- **The Pale's visuals:** dense white fog near the border, ash-like particles, and muted sounds. **Pale-touched** elite variants spawn within 64 blocks of the edge (more dangerous, better loot).
- **Other dimensions (verified in Milestone 1):** vanilla copies the Overworld border's size *unscaled* to the Nether and End; only the center is divided by the coordinate scale. VESTIGE therefore manages them explicitly. A listener on the Overworld border runs after vanilla's delegate and rescales:
  - **Nether radius = Overworld radius ÷ `border.netherDivisor`** (default **4**): 188 → 250 → 312 → 438 → 625 → 750. A divisor of 4 (not 8) keeps all five Nether biomes and the Counting House reachable at each stage.
  - **End radius = Overworld radius** (divisor 1). End outer islands (1,024+ blocks from the center) become reachable only after the Resounding (radius 2,500), a natural gate that matches the Eye of Ender gate.

### 6.2 Rings (distance bands)

Distance from the world center sets enemy tier, structure density, and loot tier. Newly reclaimed land is more dangerous than land near home.

| Ring | Radius | Enemy tier | Notable guaranteed content |
|---|---|---|---|
| **Hearthland** | 0–750 | 1 | Hearthstead, 3+ Waylights, Belfry Paddock, 1 Ruined Village, 1 small Catacomb |
| **Marches** | 750–1,250 | 2 | The Chandlery, 1 Hush scar (guaranteed), Pale Keep, Rust Mine |
| **Wilds** | 1,250–1,750 | 3 | Surveyor's Vault, Sunken Orrery, Frozen Reliquary, 2nd Hush scar |
| **Reaches** | 1,750–2,500 | 4 | Stronghold (at least 1 guaranteed), large Catacombs, Pale Keeps |
| **Pale Verge** | 2,500–3,000 | 5 | Pale-touched zones, endgame structures, Lamplighter ruins |

**Tier scaling for vanilla mobs:** HP and damage multipliers per ring (configurable, default ×1.0 / ×1.4 / ×1.9 / ×2.6 / ×3.4) plus a chance for elite affixes. Custom mobs have hand-authored stats per tier variant.

### 6.3 Guaranteed placement

A finite world must *guarantee* key content. Boss arenas and key structures use custom structure placement constrained to their ring, with a fallback search at world creation. If no valid biome exists in the ring, the structure brings its own terrain. Strongholds are reconfigured (datapack override of `strongholds.json`, ring `distance` 32 → 20) so the first ring lands about 880–1,680 blocks from the center: inside the Pale once the Resounding expands it.

### 6.4 Biomes: every biome has a reason to visit

| Biome | Signature discovery | Purpose |
|---|---|---|
| Plains / Meadow | **Belfry Paddock** (Bellwether arena), Tolling Rams | Act I boss |
| Forest / Birch | **Waylights**, Hollowed camps, Ember Heart shrines | Early resources |
| Dark Forest | **Hollowed Grove**: a ritual clearing with a Talisman of the Grove | Talisman, lore |
| Swamp / Mangrove | **The Chandlery** (Mother Tallow), Waxen, Will-o'-Wicks | Act I boss |
| Taiga | **Hunter's Lodge**, nighttime Hart Hunt, Hart King miniboss | Bow progression, NPC (Huntress) |
| Snowy / Ice | **Frozen Reliquary** dungeon, Frostbound Pilgrims | Breath Stones, frost relic |
| Desert | **Glass Tombs**: sand-buried halls, Sandwrights | Fang (dagger) uniques, Flask Wick |
| Badlands | **Rust Mines**: minecart dungeon, Rustbacks, Rust Duke | Reinforcement materials |
| Jungle | **Sunken Orrery**: a puzzle temple that marks boss locations on maps | Navigation, rings |
| Savanna | **Shepherd's Cairns**: lore stones linked to the Bellwether | Bellwether lore, Bell catalyst |
| Mountains / Peaks | **Surveyor's Vault**: hidden Lamplighter laboratory | Pale lore, Surveyor's Lens relic |
| Cherry Grove | **Petal Shrine**: a peaceful shrine (no hostile spawns) | Talisman, rest point |
| Mushroom Fields | **Spore Hermit**: a biome NPC home | Biome NPC, potions |
| Ocean / Beach | **Drowned Belfry**: submerged bell tower, Drowned Bellringers | Water-breathing relic, Bellbronze extras |
| Rivers | **Ferryman's Posts**: fast-travel boats between distant Waylights | Travel |
| Lush Caves | **Ember Heart grottos** | Max HP |
| Dripstone Caves | **Rust Veins** cave variant | Ore |
| Deep Dark | Integrated into the Hush (see §14) | Act III |
| **Hush** (new) | See §14 | Evil biome |
| Pale Verge | **Lamplighter Ruins** | Endgame lore, Lamplight materials |

### 6.5 Custom cave types

- **Wick Caverns** (Overworld, Y 0–40): amber-lit caverns with glowing *Wickstone* veins and Ember Heart grottos. Safe-ish, rewarding.
- **Rust Veins** (Badlands/Dripstone): tunnels lined with oxidized copper and Rustbacks.
- **Echo Hollows** (Hush): see §14.4.
- **Lava Tubes** (Nether): see §15.4.
- **Shell Hollows** (End): see §16.4.

### 6.6 Waylights (checkpoints)

Waylights are lanterns on stone plinths, generated at fixed points in every ring and inside major dungeons.
- **Lighting:** costs nothing; it is a discovery.
- **Resting:** sets your respawn, refills Wick Flask charges, and clears minor statuses. Optionally (configurable, default ON) it respawns non-boss enemies in nearby *dungeon* structures, never in the open world.
- **Fast travel** between lit Waylights (disabled during boss fights and within 24 blocks of hostile mobs).
- Players **cannot craft** Waylights. Placement is part of the level design.

---

## 7. Combat System

### 7.1 Philosophy
Combat is **commitment-based**: every action has windup, active, and recovery frames, and costs stamina. Damage is high on both sides. Learning patterns matters more than gear checks, but gear matters.

### 7.2 Control scheme (rebindable)

| Action | Default | Notes |
|---|---|---|
| Light attack | LMB | Chains into a combo (3–5 hits by class) |
| Heavy attack | **R** (tap) / hold to charge | Charged heavies have hyperarmor |
| Guard / Off-hand | RMB | Block with a shield, parry with a buckler, off-hand attack, or use a catalyst |
| Dodge roll / backstep | **Left Alt** (configurable to double-tap direction) | No direction = backstep |
| Lock-on | **Middle mouse** | Scroll switches targets |
| Weapon art | **F** | Costs Resonance |
| Wick Flask | **H** | Animation-locked heal |
| Swap stance | **V** | Combat stance ↔ Exploration stance |
| Swap secondary | **X** | Cycles the secondary slot |
| Equipment screen | **K** | RPG equipment UI |
| Lamp Ledger | **J** | Journal |

Combat binds use a dedicated **combat key-conflict context**: they are active only in combat stance, while vanilla binds on the same keys (pick block on middle mouse, offhand swap on F) are suppressed there. Outside combat stance, the vanilla keys behave normally.

### 7.3 Stances: integrating with Minecraft's hotbar

- **Exploration stance:** vanilla hotbar behavior (mine, place, eat, use tools). Attacks use vanilla rules (weak). This is for building.
- **Combat stance:** the weapon in the **Main Weapon slot** is *physically moved* into the main hand, and the item that was there is stashed in a hidden holding slot. The same happens for the **Secondary slot** and the off-hand. Leaving the stance swaps everything back. The swap is server-authoritative and moves the real item stacks, so nothing can be duplicated, vanilla rendering and attributes keep working, and other mods see a normal held item. LMB and RMB become combat actions; mining and placing are disabled; the hotbar is dimmed. Opening an inventory, dying, logging out, or changing dimension restores the stashed items first.
- Taking damage or locking on automatically enters combat stance (configurable).

### 7.4 Attack anatomy

All timings are in ticks (20 ticks = 1 second). Targets for the baseline classes:

| Class | Light (windup / active / recovery) | Light stamina | Heavy (windup / active / recovery) | Heavy stamina | Combo |
|---|---|---|---|---|---|
| Fang (dagger) | 3 / 2 / 5 | 8 | 7 / 3 / 9 | 16 | 5 |
| Blade (sword) | 5 / 3 / 7 | 12 | 10 / 3 / 12 | 22 | 4 |
| Pike (spear) | 6 / 2 / 9 | 13 | 11 / 3 / 13 | 24 | 3 |
| Censer (flail) | 8 / 5 / 10 | 15 | 14 / 6 / 14 | 28 | 3 |
| Greatblade | 12 / 4 / 14 | 22 | 18 / 5 / 18 | 36 | 3 |
| Maul (hammer) | 14 / 4 / 16 | 24 | 20 / 5 / 20 | 40 | 2 |

- **Combo window:** the last 60% of recovery accepts the next input. Inputs buffer for 6 ticks.
- **Cancels:** recovery can be roll-cancelled after 40% (not during active frames).
- **Running attack, rolling attack, jump attack, and plunge attack** exist for every class, each with its own motion value.
- **Hitboxes:** server-side swept volumes (oriented boxes sampled along the weapon arc each active tick). Each target is hit at most once per swing. Reach and arc width are per class.
- **Feedback:** hit-stop (2–4 ticks, scaled by weight), a camera micro-shake, directional hit particles, damage-type colored sparks, and a distinct sound per material hit (flesh, metal, sculk, bone, wax).

### 7.5 Motion values (damage multipliers of weapon attack rating)

| Attack | Fang | Blade | Pike | Censer | Greatblade | Maul |
|---|---|---|---|---|---|---|
| Light (1st) | 0.80 | 1.00 | 1.00 | 1.05 | 1.20 | 1.30 |
| Heavy (uncharged) | 1.30 | 1.45 | 1.45 | 1.50 | 1.60 | 1.70 |
| Heavy (charged) | 1.70 | 1.90 | 1.90 | 2.00 | 2.20 | 2.40 |
| Plunge | 2.00 | 2.20 | 2.30 | 2.20 | 2.60 | 2.80 |
| Riposte / backstab | 3.50 | 3.00 | 3.00 | 2.80 | 2.80 | 2.60 |

### 7.6 Dodge

- **Roll** (direction) or **backstep** (no direction). The roll type depends on **Burden** (equip load ratio):

| Burden | Load ratio | Roll distance | I-frames (ticks) | Stamina |
|---|---|---|---|---|
| Light | ≤ 30% | 4.5 blocks | 2–10 | 14 |
| Medium | ≤ 70% | 3.5 blocks | 3–9 | 16 |
| Heavy | ≤ 100% | 2.5 blocks | 4–8 | 20 |
| Overburdened | > 100% | stumble | none | 25 |

- **Perfect dodge:** if an enemy's active hitbox overlaps you during your first 3 i-frame ticks, you get +15 Resonance, a brief time-slow flash (cosmetic only, client side), and a chime.
- Accessories can alter rolls (e.g., Hollow Ring → a short **blink-step**; the Nullsteel set → phasing through enemies). Roll modifiers do **not stack**. The highest-priority one wins, and the UI shows which.

### 7.7 Guarding and parrying

- **Shields** have **Guard Absorption** (per damage type) and **Stability** (stamina damage reduction when blocking).
- **Guard break:** if stamina hits 0 while blocking, you are staggered for 20 ticks.
- **Parry** (bucklers, Fangs, some arts): a 4-tick window starting on tick 2. A successful parry staggers the enemy and opens a **riposte** for 30 ticks.
- **Parriable attacks** are marked with a **white glint** before active frames. Unparriable attacks get a **red glint**. Grabs get a **red glint + rising chime**.

### 7.8 Poise and stagger

- Every attack deals **poise damage**. Enemies have a **Poise** pool that regenerates 2 seconds after the last hit.
- **Poise break → stagger:** the enemy kneels for 40–60 ticks and can be **riposted** (front, critical motion value).
- **Player poise** comes from armor. Heavy and charged attacks grant **hyperarmor** during windup (you can't be interrupted by low-poise hits).
- **Backstabs** work against non-boss humanoids that are unaware or attacking another target.

### 7.9 Lock-on

- Targets the nearest valid enemy within **24 blocks** in the view cone. Line of sight must hold. The lock drops after 2 seconds without LOS or beyond 28 blocks.
- **Camera:** smooth yaw and pitch tracking (critically damped, so no snapping). Works in first and third person. Third person gets a shoulder offset.
- **Movement while locked:** strafing; rolls are relative to the target.
- **Boss multi-part targets** (e.g., the Archivist's head vs. body, or the Warden's soul-orbs) are separate lock points. Scroll cycles them.
- The lock marker is a small brass reticle. It turns **red** when the target is about to perform a red-glint attack.

### 7.10 Damage model

**Damage types:**

| Type | Theme | Typical source |
|---|---|---|
| **Physical**: Slash / Strike / Pierce | Steel | Most weapons |
| **Cinder** | Fire, slag | Nether gear, Kiln-Born |
| **Hush** | Silence, sculk | Hush enemies, Warden |
| **Resonance** | Sound, bells | Bell catalysts, Bellbronze, Resonite |
| **Void** | Unmaking | End enemies, Vael |
| **Radiance** | Lamplight | Lamplighter gear, Waylight-blessed items |

**Formula** (per damage type, then summed):

```
raw        = AttackRating_type × MotionValue × (1 + bonus%)
afterDef   = raw² / (raw + 2 × Defense_type)          // smooth falloff; large hits pierce defense
afterAbs   = afterDef × Π(1 − Absorption_i)           // armor pieces, accessories, buffs (multiplicative)
final      = max(afterAbs, raw × 0.10) × situational  // floor of 10% of raw
situational: counter-hit ×1.15, stagger critical uses riposte MV, guard → shield absorption
```

**Weaknesses (examples):** Hush creatures take ×1.5 from Resonance and ×1.25 from Radiance. Nether creatures take ×0.5 from Cinder and ×1.25 from Hush. Void creatures take ×1.3 from Radiance. Wax enemies take ×1.4 from Cinder, but their wick explodes faster.

**Status buildups** (fill a meter; trigger when it reaches 100%):

| Status | Effect |
|---|---|
| **Rend** | Burst of 12% max HP + 5% of current |
| **Scorch** | DoT for 6s; reduces flask healing by 30% |
| **Gilded** | Movement −8% per stack; 5 stacks encase you for 1.5s (mash to break) |
| **Hushed** | Resonance gain disabled, arts locked, footsteps louder to Hush enemies, 10s |
| **Voidbite** | Max HP −20% until you rest or use a Pale Salt |
| **Frostbind** | Stamina regen −50% and roll distance −30% for 8s |

### 7.11 Weapon arts

Arts are powerful special moves fueled by **Resonance** (see §8.3), not mana. Each weapon class has a default art. Unique weapons have signature arts. **Etchings** (rare loot) replace the art on non-unique weapons at Old Rivet's anvil.

Default class arts:

| Class | Art | Cost | Effect |
|---|---|---|---|
| Fang | **Quickstep** | 15 | Short blink with i-frames; the next hit within 1s is a guaranteed critical on staggered foes |
| Blade | **Riposte Stance** | 20 | 12-tick parry stance; on success, an automatic counter-slash ×2.2 |
| Pike | **Impaling Charge** | 30 | Forward dash thrust; on hit, pins small enemies |
| Censer | **Smolder Wake** | 25 | Spin that leaves an incense cloud (status buildup over time) |
| Greatblade | **Cleaving Wake** | 35 | Overhead slam with a forward shockwave line |
| Maul | **Tremor** | 35 | Ground pound; ring AoE; huge poise damage |
| Longbow | **Pinning Shot** | 25 | Charged arrow that roots the target for 2s |
| Bell (catalyst) | **Toll** | 20 | Expanding sound ring; interrupts casters; strong vs. Hush |

### 7.12 Enemy telegraph language (consistent everywhere)

| Signal | Meaning |
|---|---|
| Weapon/limb **white glint** | Parriable attack incoming |
| **Red glint** | Unparriable; roll it |
| **Red glint + rising chime** | Grab; must roll, block fails |
| **Ground decal** (glowing sculk / ash / wax / void rune) | AoE landing zone; fills up as timing approaches |
| **Line of particles** | Beam or charge path |
| Boss **roar + screen ring** | Phase transition; brief invulnerability, then a new moveset |

Every boss attack has **at least 8 ticks** of readable windup. Delayed attacks ("Souls-like delays") are allowed but always paired with a distinctive sound.

### 7.13 Enemy AI framework

All custom enemies share one state machine:
`Idle → Patrol → Alert (search) → Engage (spacing, strafing) → Windup → Active → Recovery → Engage …`
- **Attack selection** is weighted by distance, angle, cooldowns, and "recent repeat" penalties (to avoid spamming the same move).
- **Engage spacing:** enemies circle and do not just walk straight into you.
- **Group etiquette:** at most 2 enemies attack the same player at once ("attack tokens"). The rest circle, flank, or use ranged moves. This makes multi-enemy fights fair without being trivial.
- **Hearing** (Hush enemies): see §14.5.

---

## 8. Player Resources & Survival Rules

### 8.1 Health
- **HP scale ×5:** base max HP is 100, shown as a numeric bar. The vanilla heart HUD is hidden. Vanilla damage sources (fall, lava, vanilla mobs) are scaled to match.
- **Ember Hearts:** +10 max HP each, found in Wick Caverns and Hearthland shrines. Max 200 HP from 10 hearts.
- **Resonant Hearts** (post-Resounding, deep caves): +5 max HP each, up to 300 total.
- **Natural regeneration:** only out of combat (no damage dealt or taken for 10s), at 1 HP/s, and only if food ≥ 6.

### 8.2 Stamina
- Base 100. **Breath Stones** grant +5 each (max 150) and are found in temples, the Frozen Reliquary, and the Orrery.
- Regen 32/s after 12 ticks of delay; −50% while guarding; −50% if food < 6. **Hunger matters for combat.**
- Sprinting costs 6/s in combat stance and nothing in exploration stance outside combat.

### 8.3 Resonance (weapon art resource)
- 0–100. Built by: landing hits (+3 to +8 by weapon weight), perfect dodges (+15), parries (+20), and ripostes (+15).
- Decays by 4/s after 6s out of combat.
- **Hushed** status blocks gain.
- *Why:* it rewards aggressive, skilled play and avoids a mana-potion economy.

### 8.4 The Wick Flask
- Heals 40% of max HP over 20 ticks. The animation locks movement to a walk.
- Starts with 3 charges. **Flask Wicks** add +1 each (max 10); they come from dungeons, boss drops, and quests.
- Refills only at Waylights or at the Heartlamp.
- **Kindling** upgrades (+1 to +5, at the Heartlamp with Tallow wax) raise the heal to 60%.

### 8.5 Death and Glim
- **Glim** is the currency (see §23). On death, your carried Glim is left as an **Ember-Ghost** where you died. Touch it to recover. Die again first and it's gone.
- **Equipped RPG slots are always kept.** The rest of the inventory follows the chosen **Run Mode**:
  - **Wayfarer** (default): keep inventory; only Glim drops.
  - **Pilgrim:** non-equipped inventory drops (vanilla-like).
  - **Ashen:** hardcore; one life.
- Bosses fully reset if all players in the arena die.

---

## 9. Equipment System

### 9.1 Slots

| Slot | Count | Unlock |
|---|---|---|
| Main Weapon | 1 | Start |
| Secondary (shield, weapon, catalyst, or lantern) | 1 (+1 swap set) | Start; a 2nd swap set after the Bellwether |
| Head | 1 | Start |
| Chest | 1 | Start |
| Legs | 1 | Start |
| Boots | 1 | Start |
| Rings | 2 | Start |
| Talismans | 1 → 2 | 2nd after the Tithe-Engine |
| Relics (accessories) | 2 → 4 | 3rd after Mother Tallow, 4th after the Resounding |
| Wick Flask | 1 | Start (fixed) |

- **Armor slots mirror vanilla armor slots.** Equipping head, chest, legs, or boots in the RPG UI uses the vanilla armor slots, so armor renders natively and stays compatible with other mods. Vanilla armor items are valid but translated into VESTIGE stats (a "Common" baseline).
- **Weapons, rings, talismans, and relics** live in a mod-owned per-player inventory (persisted player data, synced to client).
- **No duplicate equips:** an item can't occupy two slots. Some accessories are mutually exclusive by **family** (e.g., two roll-modifiers); the UI shows the conflict.

### 9.2 Stats every item can carry

`Attack Rating (per type)`, `Poise Damage`, `Status Buildup`, `Defense (per type)`, `Absorption (per type)`, `Poise`, `Weight`, `Stamina modifiers`, `Roll modifiers`, `Move speed`, `Resonance gain`, `Noise` (for the Hush), `Special effect` (a unique scripted behavior).

### 9.3 Burden
`Burden = Σ equipped weight / Equip Capacity` (base capacity 60, raised by certain talismans and the Cindersteel set). See the roll table in §7.6.

### 9.4 Reinforcement & reforging (at Old Rivet)
- **Reinforcement:** +0 to +10. Each level adds ~7% attack rating (weapons) or ~5% defense (armor). Costs **Temper Shards** (+1 to +5), **Bright Temper** (+6 to +9), or a **Wick Ember** (+10) plus Glim.
- **Reforging** rolls one prefix from the item category's pool. It costs Glim (scaled by item tier) and can be repeated.

| Prefix (weapons) | Effect | Prefix (armor and accessories) | Effect |
|---|---|---|---|
| Keen | +10% critical damage | Steadfast | +6 poise |
| Heavy | +15% poise damage, +10% weight | Nimble | −10% weight |
| Tempered | +8% AR | Quiet | −15% noise |
| Swift | −10% windup ticks | Warded | +5% all absorption |
| Hollow | −20% weight, −5% AR | Lamplit | +3% Radiance absorption, light radius +2 |
| Ringing | +5 Resonance on hit | Wicked | Wick Flask heals +5% more |
| Hushed | +8 Hush buildup | Hale | +10 max HP |
| Gilded | +10% Glim from kills | Breathing | +8% stamina regen |

### 9.5 The equipment screen (layout)

**Visual reference:** the Elden Ring quick-item layout supplied by the project owner. Elements adopted from it:

| Reference element | VESTIGE translation (pixel art, GUI-scale aware) |
|---|---|
| Square slot tiles in a **cross arrangement** (left, top, right, bottom) | The **Readiness Cross** in the center of the screen and on the HUD: right = Main Weapon, left = Secondary, top = Active Relic, bottom = Wick Flask |
| Dark, slightly translucent tile with a soft inner vignette and a thin 1-px lighter frame | Tile texture: 24×24 px, near-black slate (#121116 → #1c1a21 vignette), 1-px frame in worn pale stone (#5d5a55) with a darker outer shadow line |
| Faint **ghost silhouette at the top center** of each tile showing the slot type | Each slot draws a 9-px, ~25%-opacity silhouette of its category (sword, shield, helm, ring, diamond-talisman, relic-box, flask). It disappears behind the item once the slot is filled |
| A thin **ornamental rule near the bottom edge** of each tile | A 1-px, 14-px-wide hairline (#7a6f5c), centered 3 px above the bottom edge |
| Item fills most of the tile, warm-lit against a desaturated UI | Items render at 16×16 centered (scaled ×1.25 in the large central tiles), so warm item colors pop against the grey UI |
| **Serif quantity numerals** at the bottom-right with a dark shadow | Counts and flask charges in the bottom-right corner, using a small custom pixel serif font texture with a 1-px shadow |
| Gritty dark background | The screen background is a blurred world with a dark grain overlay (#000 at 55% + noise texture) instead of the vanilla dirt or gradient |
| Slots separated by small even gaps, grouped in clusters | Clusters with 2-px gaps between tiles and 10-px gaps between clusters; no heavy boxes or panels around groups |

**Layout:**

```
┌──────────────────────────────────────────────────────────────────────┐
│ ◈ EQUIPMENT                                     Burden ▓▓▓▓▓░░ Medium │
│                                                                      │
│   ARMOR                       READINESS                 ACCESSORIES  │
│  ┌──┐                           ┌──┐                    RINGS        │
│  │⛑ │ Head                      │▣ │ Relic (active)     ┌──┐┌──┐      │
│  └──┘                           └──┘                    │◯ ││◯ │      │
│  ┌──┐                     ┌──┐ ┌────┐ ┌──┐              └──┘└──┘      │
│  │▤ │ Chest   (Secondary) │⛨ │ │DOLL│ │⚔ │ (Main)       TALISMANS    │
│  └──┘                     └──┘ └────┘ └──┘              ┌──┐┌──┐      │
│  ┌──┐                           ┌──┐                    │◇ ││◇ │      │
│  │▥ │ Legs                      │⚱5│ Wick Flask         └──┘└──┘      │
│  └──┘                           └──┘                    RELICS       │
│  ┌──┐                    swap set ⇄ [⚔][⛨] (small)      ┌──┐┌──┐┌──┐┌──┐│
│  │▦ │ Boots                                             │▣ ││▣ ││▣ ││⛓ ││
│  └──┘                                                   └──┘└──┘└──┘└──┘│
│ ──────────────────────────────────────────────────────────────────── │
│  HP 140   STA 115   POISE 34   │ ABSORPTION  Phy 18%  Cin 9%  Hush 12% │
│  AR Slash 182 (+Res 40)        │             Res 6%   Void 4% Rad 3%  │
│  Roll: Medium (i-frames 3–9)   │ SET: Bellbronze (3/4): Toll inactive │
│ ──────────────────────────────────────────────────────────────────── │
│                  [ player inventory grid, same tile style ]          │
└──────────────────────────────────────────────────────────────────────┘
```

- The **center cross** reproduces the reference's arrangement: four large tiles (28×28) around a live, mouse-rotatable **paper-doll** of the player. It is the "what I fight with" cluster.
- **Armor** is a vertical column of four tiles (left). **Accessories** are grouped tiles (right). Locked slots (`⛓`) show a chained tile, and their tooltip names the unlock condition.
- **Text style:** section labels in small caps, parchment color (#d9cfb8) at 70% opacity; values at full opacity; ember (#e08a3c) only for highlights (set bonuses active, upgrades).
- **Tooltips:** dark tile-style frame. Comparison mode shows green/red deltas against the equipped item.
- **The HUD uses the same cross:** a 4-tile Readiness Cross at the bottom-left (Main / Secondary / Relic / Flask with charges), like the reference.

---

## 10. Weapons

### 10.1 Classes
**Fang** (dagger) · **Blade** (straight sword) · **Pike** (spear) · **Censer** (incense flail: original; leaves status clouds) · **Greatblade** · **Maul** (hammer) · **Longbow** · **Bell** (sound catalyst: original; "Tolls" are spells) · Shields: **Buckler** (parry), **Kite** (block), **Bulwark** (tower) · **Hand Lantern** (off-hand: light radius, reduces Hushed buildup, weak strike).

**Dual-wield:** two Fangs or two Blades in Main + Secondary enable **Paired** combos (both hands strike on heavy).

### 10.2 Weapon list by tier (baseline target; final numbers set in the balancing milestone)

| Tier | Gate | Weapons (class) | Signature / unique |
|---|---|---|---|
| 0 Wanderer | Start | Keepsake Blade, Ash Pike, Hand Maul, Hunting Bow, Plank Buckler | n/a |
| 1 Bellbronze | Bellwether | Bellbronze Blade, Bellbronze Pike, Tolling Maul, Handbell (Bell) | **Clapper of the Bellwether** (Maul): heavy attacks ring a Resonance shockwave. Art *Herd Call*: pulls small enemies inward |
| 2 Wickiron | Mother Tallow | Wickiron Blade, Wickiron Greatblade, Taper Fang, Chandler's Censer | **Mother's Taper** (Censer): clouds harden into wax that slows enemies. Art *Last Light*: ignites all wax for burst Cinder |
| 2.5 Sculkbone (Hush side-path) | Hush surface | Sculkbone Fang, Murmur Pike | High Hush buildup, low weight, quiet |
| 3 Cindersteel / Assay | Tithe-Engine | Cindersteel Greatblade, Kiln Maul, Assay Blade, Slag Censer, Ashwood Longbow | **Abbot's Scale** (Censer, a balance-scale flail): the art *Weigh the Debt* deals damage equal to the damage you took in the last 5s ×1.5 |
| 4 Hush (post-Warden) | Warden | Hushsilver Blade, Echo Pike | **Quietus** (Greatblade): attacks replay as a delayed echo 1s later at 40% damage. Art *Last Silence*: a 4s zone where enemies cannot act |
| 5 Resonite | Resounding | Resonite Blade, Resonite Maul, Chorus Bell, Resonite Longbow | **Grand Carillon** (Bell): Tolls chain between enemies |
| 6 Nullsteel | Ender Dragon / End | Nullsteel Fang, Nullsteel Pike, Null Greatblade | **Archivist's Quill** (Fang): marks enemies with runes that detonate after 3 hits |
| 7 Heirshell | Vael | Heirshell Blade, Heirshell Bulwark | **Starless Fang** (Fang): Void damage; perfect dodges spawn a void clone that strikes once. **Umbilical Lash** (whip-Pike): art *Tether*: links to an enemy and pulls you to it |
| 8 Lamplight | Epilogue | n/a | **Last Wick** (Blade): Radiance; converts Resonance to healing |

Vanilla swords and axes remain usable as "Common" Blade and Maul analogs with low ratings and no arts.

---

## 11. Armor Sets

Each set has 4 pieces, a weight class, absorption values, and a **set bonus** (2-piece minor, 4-piece major).

| Set | Tier | Weight | 2-piece | 4-piece |
|---|---|---|---|---|
| Wanderer's Garb | 0 | Light | +5% stamina regen | Fall damage −30% |
| Bellbronze Mail | 1 | Medium | +8 poise | **Toll:** every 4th hit emits a small Resonance shockwave |
| Wickiron Plate | 2 | Heavy | +10% Physical absorption | **Slow Burn:** below 30% HP, wax hardens: +20% absorption and +15 poise |
| Hushweave (Sculkbone) | 2.5 | Light | Noise −25% | **Unseen:** backstab damage +30%; Hush enemies' detection −50% |
| Assay Gilt | 3 | Medium | Glim gain +15% | Immune to Gilded; gold pickup grants Resonance |
| Cindersteel Harness | 3 | Heavy | Equip capacity +10 | **Kiln-Heart:** stand still 2s → the next attack gains +30% Cinder |
| Unsung Shroud | 4 | Light | Hush absorption +15% | **Mute Step:** rolls make no noise and leave an echo decoy |
| Resonite Raiment | 5 | Medium | Resonance gain +20% | **Chorus:** perfect dodges release a Resonance pulse |
| Nullsteel Plate | 6 | Medium | Void absorption +15% | **Phase:** rolls pass through enemies (roll modifier) |
| Heirshell Carapace | 7 | Medium | +20 poise | **Unborn:** once every 180s, lethal damage instead leaves you in a shell at 1 HP, invulnerable for 3s |
| Lamplight Regalia | 8 | Medium | Radiance AR +10% | **Beacon:** Waylight-like aura; allies regenerate 1 HP/s |

Smaller themed pieces (e.g., Hunter's Hood, Pilgrim's Wraps, Drowned Bellringer's Helm) fill out builds and mixing options.

---

## 12. Accessories: Rings, Talismans, Relics

Accessory families avoid both "+5% of everything" filler and overlap:
- **Rings:** stat-shaping (numbers).
- **Talismans:** rule-changing (mechanics).
- **Relics:** abilities and movement (verbs).

### 12.1 Rings (examples, ~16 at release)
| Ring | Source | Effect |
|---|---|---|
| Ring of Quick Breath | Frozen Reliquary | Stamina regen +15% |
| Bellbronze Band | Craft (T1) | +10 poise |
| Waxen Ring | Chandlery | Successful parry heals 5% HP |
| Hollow Ring | Pale Keep | Roll becomes a short **blink-step** (roll modifier) |
| Tithe Ring | Tithe-Engine | Picking up gold or Glim grants Resonance |
| Ring of the Unsung | Warden | Resonance never decays |
| Hart's Ring | Hart King | +12% damage while above 80% HP |
| Ember Signet | Rust Duke | Cinder AR +10%, Scorch buildup on hit |

### 12.2 Talismans (examples, ~14 at release)
| Talisman | Source | Effect |
|---|---|---|
| Lightfoot Feather | Hunter's Lodge quest | Burden thresholds +15% (lighter rolls) |
| Muffled Heart | Hush Containment Lab | Noise −50% (Hush) |
| Talisman of the Grove | Hollowed Grove | Out-of-combat regen ×3 |
| Ledger of Debts | Oru (shop) | +25% damage, but Glim is lost on hit (1% per hit) |
| Gilded Heart *(key item)* | Tithe-Engine | Equippable: immune to Gilded; also needed for the Brooding Altar |
| Surveyor's Mark | Surveyor's Vault | Reveals structure icons on held maps within 256 blocks |
| Bell of Warding | Muted Monk | Hushed buildup −60% |
| Talisman of Second Breath | Sunken Orrery | A one-time revive every 10 min at 20% HP |

### 12.3 Relics (examples, ~14 at release)
| Relic | Source | Effect |
|---|---|---|
| Climber's Hooks | Rust Mine | Wall-cling and climb up 3 blocks |
| Windsail Cape | Drowned Belfry | Glide by holding jump |
| Heir's Wing | Vael | Double jump (combines with the Cape) |
| Lantern of Warding | Wren quest | Light radius 10 that repels Murmurs |
| Pale Hook | Pale Keep | Grappling hook (24 blocks) |
| Bellbronze Spurs | Craft | Sprint +15%, sprint stamina −50% |
| Diver's Bell | Drowned Belfry | Water breathing + underwater speed |
| Surveyor's Lens | Surveyor's Vault | Reveals hidden doors and dungeon secrets |

---

## 13. Materials & Crafting

### 13.1 Material tiers

| Material | Where | Requires |
|---|---|---|
| **Wickstone** (glowing ore) | Wick Caverns | Iron tools |
| **Resonant Clapper** | Bellwether drop | n/a |
| **Bellbronze Ingot** | Copper + Resonant Clapper fragment at the **Founder's Anvil** | Bellwether |
| **Tallow Ember / Wax** | Mother Tallow, Waxen | n/a |
| **Wickiron Ingot** | Iron quenched in Tallow wax | Mother Tallow |
| **Sculkbone, Hushsilk** | Hush mobs, Hush surface | Hush exploration |
| **Assay Gold** | Gilded blackstone in Counting House veins (refined) | Tithe-Engine (the refinery unlocks) |
| **Cinderglass** | Lava Tubes | Nether |
| **Cindersteel Ingot** | Netherite scrap + Assay Gold | Tithe-Engine |
| **Hushsilver** | Echo Hollows deep veins | Pre-Warden: low yield; post-Warden: full yield |
| **Echo Marrow** | Warden, Unsung Knights | Warden |
| **Resonite** | Deepslate (generated as *Dormant Resonite*, unbreakable until the Resounding) | Resounding |
| **Nullsteel, Voidpearl** | End, Null Archive | Dragon / Archivist |
| **Heirshell Plate** | Vael | Vael |
| **Lamplight Ember** | Last Lamplighter, Pale Verge ruins | Epilogue |

**Dormant ore design:** Resonite generates at world creation but stays "dormant" (a blockstate) and unbreakable until the Resounding flag is set. This avoids world regeneration while keeping Terraria's "new ores appear" excitement: at the Resounding, all dormant ore starts to glow (client-side blockstate rendering keyed to a synced global flag, with no world rewriting).

### 13.2 Stations
- **Founder's Anvil:** RPG weapon and armor crafting (tiered recipes).
- **Old Rivet's Forge** (NPC): reinforcement and reforging.
- **Heartlamp** (spawn): lights Wicks, upgrades Flask Kindling.
- **Refinery** (Nether, unlocked by the Tithe-Engine): Assay Gold.
- **Resonance Loom** (post-Resounding): bells, Resonite accessories.

Recipes are data-driven (JSON) and viewable in the Lamp Ledger once discovered.

---

## 14. The Hush (Sculk Evil Biome)

### 14.1 Concept
The Hush is **silence that hungers**. It doesn't burn or rot; it *mutes*. Inside it, music fades, ambient sound drops away, and the player's own footsteps become the loudest thing in the world. That is exactly the danger, because Hush creatures are **blind and hunt by sound**.

### 14.2 Distribution
- 2 large **Hush Scars** per world by default (configurable 1–4), 250–400 blocks across. One is guaranteed in the Marches; one is in the Wilds.
- **Generation (verified in Milestone 1):** a custom biome source wraps the vanilla Overworld biome source and returns the Hush biome inside seed-derived scar shapes, with noisy edges, at guaranteed ring positions. Vanilla surface rules, features, spawns, fog, and music then follow the Hush biome everywhere it appears. No third-party biome library is needed.
- Each scar has a surface layer (corrupted biome), Echo Hollows below, and a Choir Pit leading to the deep dark. The Unsung Nave sits beneath the largest scar.

### 14.3 Surface look
- **Ground:** Hushgrass (grey-teal, sculk-veined), Muted Soil, sculk patches.
- **Flora:** Hollowwood trees (pale bark, sculk-vein leaves, no leaf rustle sound), Murmur Stalks (tall plants that "inhale" with a particle pulse), Quietcaps (fungi that absorb sounds nearby).
- **Sky and fog:** desaturated teal fog, slightly darker sky, and drifting "sound motes" (soul-like particles flowing *toward* the ground).
- **Audio:** music fades out, ambient volume −70%, and a low heartbeat-like drone.

### 14.4 Underground: Echo Hollows & Choir Pits
- **Echo Hollows:** vast cavities with sculk catalyst columns, ribcage-like arches of Sculkbone, and pools of "Still Water" (no splash sounds; noise −100% while wading, but it slows you). Hushsilver veins line the walls.
- **Choir Pits:** vertical shafts ringed with sculk shriekers and **Hollow Choristers**. Descending is a vertical stealth puzzle.
- **The Deep Dark** (vanilla) is integrated as the bottom layer. **Swallowed Villages** (enhanced ancient cities) are Hush dungeons containing the Hush Containment Lab's lost notes.

### 14.5 Noise mechanic (core Hush gameplay)
- Every player action has a **noise value**: walking 2, sprinting 6, rolling 8, attacking 6–12, block break/place 10, eating 4, sneaking 0.5. Armor weight adds a multiplier. Prefixes and talismans reduce it.
- Hush enemies have a **hearing radius**. Noise above their threshold within that radius sets them to *alert* toward the source.
- **HUD:** a "Noise" ring around the crosshair appears only in the Hush.
- **Tools:** the throwable **Chime Stone** creates noise elsewhere (distraction). Still Water and Quietcaps dampen noise. The Lantern reduces Hushed buildup but *Murmurs are attracted to its warmth* (a trade-off).
- Vanilla Warden behavior (sniffing, vibrations) inspired this, but it is a custom system that also applies to all Hush mobs.

### 14.6 Spread
- **Pre-Resounding:** very slow spread (a random tick converts adjacent grass, stone, or sand within 4 blocks of existing Hush blocks; default ~1 conversion per chunk per in-game day near scars).
- **Post-Resounding:** ×6 speed. **Muted** variants of villagers and animals appear when they stand on Hush ground.
- **Barriers:** Chime Lanterns (ward radius 12), Radiance-infused blocks, and the Settlement's Heartlamp radius (64) prevent spread.
- **Purification:** **Chime Powder** (Muted Monk) and the **Tuning Staff** (post-Resounding; a sprayer that converts a cone of Hush back).
- Spread is chunk-budgeted (max N conversions per tick, configurable) and can be disabled entirely.

### 14.7 Hush enemies
See §18.3: Murmurs, Shriekhusks, Hollow Choristers, Lurkjaws, Sculk Catalyst Hearts, Unsung Knights (elite), and the Muted.

### 14.8 Hush structures
- **Listening Posts:** abandoned Lamplighter towers at scar edges. They contain the Muted Monk (NPC rescue), a Waylight, and a Chime Lantern recipe.
- **Hush Containment Lab** (hidden laboratory): a buried Lamplighter research site, breached. Its puzzle doors are opened by placing noise in the right rooms. It yields the Muffled Heart, lore, and the **Mute Bell blueprint**.
- **Choir Pits** (above).
- **Swallowed Villages** (dungeons).
- **The Unsung Nave** (Warden arena).

### 14.9 Hush rewards
Sculkbone/Hushweave set (the stealth side-path), the Muffled Heart, the Bell of Warding, Hushsilk, Sculkbone weapons, Echo Marrow, the Quietus, and the Unsung Shroud.

---

## 15. The Nether: The Tithelands

### 15.1 Concept
The Nether is a **collapsed tithe economy**. Piglins once paid gold tribute to a cathedral-bank, the Counting House, whose engine refined gold into "worth." The engine is still collecting. Gold is a weapon, a lure, and a currency here.

### 15.2 Region roles

| Biome | Role | Content |
|---|---|---|
| Nether Wastes | Hub; Counting House location | Tithe-Collectors, Pilgrim Waystations |
| Crimson Forest | The faithful | Gilded Faithful (piglin cultists), Shrines of Tribute |
| Warped Forest | The heretics | Warped Pilgrims (neutral), Oru's hideout |
| Soul Sand Valley | The dead debtors | Soul Reliquary dungeon, Soulbound Ossifers, Ash Wraiths |
| Basalt Deltas | Industry | Cinder Kilns dungeon, Kiln-Born, Clinker Crabs |

### 15.3 Nether structures
- **The Counting House:** a fortified vault-cathedral sealed by a Hearthkey door. It contains a 3-floor dungeon (ledger halls, coin-chute traps, piglin vault guards), the caged Oru (NPC rescue), and the Tithe-Engine arena at its core.
- **Cinder Kilns:** basalt furnace-dungeons with heat zones (standing in them builds Scorch). The Kiln Matriarch miniboss guards Cindersteel recipes.
- **Soul Reliquary:** an ossuary with a soul-lantern puzzle. Freed souls disable Ossifer reassembly. Rewards include a Flask Wick and the Ring of Debts Paid.
- **Pilgrim Waystations:** small warped-forest camps with Waylights. They are the only safe rest points in the Nether.
- **Shrines of Tribute:** small crimson shrines. Offering gold yields random buffs or curses (risk/reward).
- **Vanilla bastions and fortresses** are kept but restocked with VESTIGE loot tables and elite guards.

### 15.4 Lava Tubes (Nether caves)
Hollow obsidian-glass and basalt tubes beneath the Nether Wastes floor (custom carver). They contain Cinderglass, Clinker Crab nests, and a shortcut network between regions. Lava rises and falls on a slow cycle, so routes open and close.

### 15.5 Gold mechanics
- **Gilded status** (from Tithe enemies and the boss).
- **Tithe-Collectors** steal 5% of your carried gold items or Glim on a grab. Killing one returns it with interest.
- **Throwing gold** at certain enemies or the boss distracts them (they "assay" it).

### 15.6 Nether materials and gear
Assay Gold, Cinderglass, Cindersteel, Netherite (vanilla, now a component), Soul Ash. Gear: the Cindersteel and Assay Gilt sets, Kiln Maul, Slag Censer, Abbot's Scale, Tithe Ring, Ledger of Debts.

---

## 16. The End: The Unhatched Reach

### 16.1 Concept
The End is a **nursery that became a prison**. The outer islands are the ruins of an archive-cult that studied the egg and a hatchery built for an heir that was never allowed to be born.

### 16.2 Access
- Eyes of Ender are **"deaf"** before the Resounding. They fall like inert pearls and won't fill portal frames, with a message in the Ledger: "The eye listens for a heartbeat it cannot hear."
- After the Resounding, they work, and at least one stronghold is guaranteed inside the Reaches.

### 16.3 The Ender Dragon
The vanilla fight stays intact (it's iconic) but is rebalanced to the HP ×5 scale and gets custom loot. The **Dragon Egg** becomes a key item with a unique tooltip, and it can't be lost: if the egg is destroyed, it respawns on the exit portal.

### 16.4 Outer End content
- **Shellfall Wastes:** islands littered with giant black eggshell fragments (a new block set: Shellstone, Shellglass). Shellmites nest here.
- **Shell Hollows** (caves): hollowed-out islands with Voidpearl clusters inside giant shell fragments.
- **End Temples (Cenotaphs):** 3 per world. Each holds an **Index Page** (needed to summon the Archivist), Null Scribes, and a lore mural telling the egg's story.
- **The Null Archive:** a large dungeon-library with gravity-shifting rooms, where page-puzzles open sealed stacks. The Severed Archivist's arena lies at its top.
- **The Hatchery:** hidden until the Archivist's Index reveals it (it becomes visible as a light beam). It's an island-cradle with the Brooding Altar.
- **End Cities:** kept, restocked with Nullsteel loot and Tetherling guards.

### 16.5 End enemies
Hollow Envoys, Shellmites, Voidmoths, Tetherlings, and Null Scribes. Post-Vael: Starless Wyrmlings (see §18.5).

### 16.6 The Egg event: the Hatching Rite
1. Defeat the Ender Dragon → obtain the Dragon Egg.
2. Defeat the Severed Archivist → the Archivist's Index → the Hatchery is revealed.
3. Place the Dragon Egg on the **Brooding Altar**, then offer the **three Warmths** from earlier bosses: the **Tallow Ember** (Mother Tallow), the **Gilded Heart** (Tithe-Engine), and the **Unsung Echo** (Warden). Each Warmth lights a brazier.
4. **The egg grows** over a 30-second cutscene-like sequence: the camera is free, the arena seals, the egg swells from 1 block to a 6-block-tall shell, and the music builds.
5. The encounter with **Vael, the Unhatched Heir** begins (see §17.7).
6. All three Warmths are re-obtainable by re-summoning their bosses, so the rite can't be soft-locked.

---

## 17. Boss Roster

**Shared boss rules:**
- **Arena seal:** a curtain of pale light (a "Wick-Seal") closes the arena entrance during the fight.
- **Custom boss bar:** name, phase pips, and a poise bar under the HP bar.
- **Multiplayer scaling:** HP × (1 + 0.6 × (players − 1)). Loot rolls per player.
- **Re-summonable** after the first kill (summon items are craftable).
- **Phase transitions:** a short invulnerable roar (≤ 2s) with a screen-ring effect, then new moves are added (not only "faster").
- **Each boss teaches** one skill the next region tests.

---

### 17.1 The Bellwether *(Act I: Plains/Meadow, Belfry Paddock)*

**Appearance:** A colossal ram-shaped construct, 4 blocks tall, made of tarnished copper plates over grey wool, with a cracked bronze bell for a head and a hanging clapper for a tongue. Its horns are coiled bell-ropes.

**Lore:** The steward of the first Wick. It gathered "the lost" (wandering villagers) into its paddock to keep them safe from the Pale. It still does, though they're Hollowed now.

**Summon:** Ring the Great Bell in the paddock (always available).
**Teaches:** rolling through charges, reading ground telegraphs.

| Phase | HP | Moves |
|---|---|---|
| 1, "The Shepherd" | 100–60% | **Butt Charge** (3-tick red glint, long line telegraph; misses crash into fences), **Toll Ring** (head-bell rings, an expanding ground ring you must roll through or jump), **Hoof Stamp** (front cone), **Herd** (summons 2–3 Tolling Rams) |
| 2, "The Cracked Bell" | 60–0% | The bell cracks and Toll Rings come in **pairs with different speeds**; **Chain Charge** (3 consecutive charges, each re-aimed); **Clapper Swing** (180° sweep, parriable white glint) |

**Drops:** Bellwether Wick, Resonant Clapper (guaranteed), Clapper of the Bellwether (25%), Bellbronze Band (30%), Ember Heart ×1 (first kill).
**Unlocks:** Pale → 1,000; Bellbronze recipes; **Old Rivet** can move in; 2nd weapon swap set.

---

### 17.2 Mother Tallow *(Act I: Swamp, the Chandlery)*

**Appearance:** A hunched candle-hag, 5 blocks tall, made of layered drippy wax with a dozen half-melted candles embedded in her back. Her face is a smooth wax mask with a single burning wick where a mouth should be. She drags a rusted candelabra like a staff.

**Lore:** Steward of the second Wick. She made candles for the Lamplighters, kept them burning, and slowly became one.

**Summon:** Light the central wick of the Chandlery with a **Lit Taper** (crafted from a Waxen drop and a torch).
**Teaches:** terrain control, managing debuffs, aggression windows.

| Phase | HP | Moves |
|---|---|---|
| 1, "Dripping" | 100–55% | **Wax Rain** (marked puddles that harden into slowing wax tiles), **Candelabra Sweep** (parriable), **Taper Lob** (arcing fire projectile), **Call the Waxen** |
| 2, "Burning Down" | 55–20% | Her wick lights: she is faster and loses 1 HP/s (a soft timer that rewards defensive play). **Melt Lunge** (a 3-hit combo); **Wick Whip** (a long-range lash, red glint); hardened wax tiles **ignite** when she passes near them |
| 3, "Last Light" | 20–0% | She collapses into a crawling wax pool. Her real body is the wick-flame core, which must be struck while it emerges. **Flare Burst** (AoE, telegraphed by brightening) |

**Drops:** Tallow Wick, **Tallow Ember** (Warmth #1, guaranteed), Mother's Taper (25%), Waxen Ring (30%), Flask Wick ×1 (first kill).
**Unlocks:** Pale → 1,250; Wickiron recipes; 3rd relic slot; Hearthkey recipe; **Sable** (Outfitter) can move in.

---

### 17.3 The Tithe-Engine *(Act II: Nether, the Counting House)*

**Appearance:** A walking reliquary-furnace about 7 blocks tall: a blackstone-and-gold cathedral organ on four piston legs, coin chutes on its back, and a furnace-mouth chest glowing with molten gold. Inside sits **Abbot Grusk**, an ancient piglin fused to the controls by gold chains.

**Lore:** Steward of the Nether's Wick. It was built to tithe the Nether's gold so piglins would never fight over it. It forgot why, but never stopped collecting.

**Summon:** Open the Counting House vault with a **Hearthkey**.
**Teaches:** multi-part targets, exploiting a lure mechanic, arena awareness.

| Phase | HP | Moves |
|---|---|---|
| 1, "The Collection" | 100–60% | **Coin Mortar** (arcing gold barrages with glowing landing decals), **Smelting Spray** (cone of molten gold, Gilded buildup), **Ledger Stomp** (leg slam, ring), **Conveyor Rush** (charges along arena rails; the rails are visible beforehand) |
| 2, "Open Furnace" | 60–25% | The chest opens to expose its **heart** (crit point). Lava channels in the floor rise and fall on a pattern. **Assay Beam** (sweeping beam, line telegraph); **Grusk's Hand-Cannon** (the Abbot fires from inside, parriable bullets via buckler reflect) |
| 3, "The Abbot" | 25–0% | The Engine collapses; **Abbot Grusk** ejects and fights on foot with the Abbot's Scale: a fast duelist with flail combos, a **Weigh the Debt** retaliation (punishes button-mashing), and gold-coin caltrops |

**Unique mechanic, "Tribute":** Throwing gold items (nuggets, ingots, blocks) into the furnace mouth makes the Engine stop and "assay" them: 1/2/4 seconds of vulnerability depending on value. Each tribute type works only once per phase. Players who hoarded gold get a tactical tool.

**Drops:** Tithe Wick, **Gilded Heart** (Warmth #2), Assay Gold ×12, Abbot's Scale (25%), Tithe Ring (30%), Breath Stone ×1.
**Unlocks:** Pale → 1,750; Refinery and Cindersteel recipes; 2nd talisman slot; **Oru** can move in; the Mute Bell can be forged.

---

### 17.4 The Warden of the Unsung *(Act III: the Hush, the Unsung Nave)*

**Appearance:** Based on the vanilla Warden but grown to 1.6× scale, with a hollow ribcage chest holding **five trapped soul-lights** that pulse with the sounds it has stolen. Sculk tendrils stretch from its back into the arena walls. Its ear-horns are elongated, and new ones sprout every phase.

**Lore:** Not the Hush's master but its *vessel*. Every sound the Hush devoured went into it. It's a prison that learned to walk.

**Arena, the Unsung Nave:** A drowned cathedral of Sculkbone arches in the deep dark: a circular nave with pews (cover that breaks), sculk shriekers in alcoves, and a dome. The floor has **Still Water** pools.

**Summon:** Ring the **Mute Bell** at the altar. It makes no sound, and the Nave answers.
**Teaches:** everything: sound management, phase reading, positioning.

| Phase | HP | Moves |
|---|---|---|
| 1, "The Deaf Hunter" | 100–60% | **Blind:** targets the *loudest* player (the noise mechanic is active; sneaking gives brief windows for charged heavies, but standing still too long triggers **Sniff Sweep**). **Tremor Slam** (sculk veins on the floor light up in a ring), **Lunging Grab** (red glint + chime; a caught player takes heavy damage), **Sonic Lance** (the chest opens; a 12-tick line telegraph; pierces blocks), **Tendril Lash** (arena walls whip a lane) |
| 2, "Unsealing" | 60–25% | The chest stays open and the **5 soul-lights orbit** it. Each is a separate lock-on target; breaking one staggers the Warden and releases a *recovered sound* (a brief echo of a vanilla sound) that heals players 5%. Shriekers erupt, summoning Murmurs. **Sonic Ring** (360° boom with a gap you must roll into), **Pew Crush** (grabs cover and throws it) |
| 3, "The Last Silence" | 25–0% | **All sound is muted for players** (client-side audio duck except for the heartbeat and the boss's telegraph cues). The Warden now hears *everything*. Arena-wide **Hushed** buildup. **Echo Recall:** every attack replays as a translucent echo 1.5s later at the same location, so players must dodge both the attack and its echo. **Final Scream** at 5%: a 3-second charge-up, survived by hiding in a Still Water pool or by breaking it with a Resonance art |

**Drops:** Unsung Wick, **Unsung Echo** (Warmth #3), Echo Marrow ×6, Quietus (20%), Ring of the Unsung (25%), Unsung Shroud set pieces (vendor-unlock at the Muted Monk).

**On death, THE RESOUNDING** (see §22.1): Pale → 2,500 (after the Wick is lit); 4th relic slot; Resonite wakes; Eyes of Ender work; the Hush accelerates; Echo Duelists appear; new NPCs.

---

### 17.5 The Ender Dragon *(Act V: End, vanilla arena)*
Vanilla behavior is kept. HP is scaled to the ×5 system (configurable). On kill, it drops Nullsteel fragments, and the Dragon Egg gets a key-item tooltip. If a VESTIGE-wide setting is ON (default), end crystals heal less, so the fight isn't a damage-sponge chore.

---

### 17.6 The Severed Archivist *(Act V: outer End, the Null Archive)*

**Appearance:** A gaunt enderman scholar, 6 blocks tall, whose **head floats separately** from its body, tethered by a strand of purple light. The body wears tattered archive robes and wields a page-blade. The head is a cracked mask of shellstone with one huge eye, orbited by flying pages.

**Lore:** It studied the egg for millennia and cut its own head off so it could keep reading while its body kept guard.

**Arena:** The Archive's apex: a library floor with floating bookshelf platforms and gravity runes.

**Summon:** Place all three **Index Pages** on the Reading Lectern.
**Teaches:** splitting attention, gravity mechanics (preparing for Vael).

| Phase | HP | Moves |
|---|---|---|
| 1, "Divided Study" | 100–50% | Two targets that share an HP pool. **Body:** page-blade combos (parriable), **Swap** (teleports to switch places with the player). **Head:** **Rune Script** (writes glowing runes on the floor that detonate after 2s), **Page Storm** (homing pages) |
| 2, "Rewritten" | 50–0% | The tether shortens and pulls the head back toward the body. **Gravity Pages** (zones flip gravity for 3s; ceilings become floors), **Erasure** (a beam deletes floating platforms, which reform 8s later), **Reunion** (head and body merge for a 10s burst combo, then split, with a big crit window) |

**Drops:** **Archivist's Index** (reveals the Hatchery), Archivist's Quill (25%), Voidpearl ×8, Nullsteel ingots.
**Unlocks:** **Envoy Nihl** (End NPC); Nullsteel upgrades.

---

### 17.7 Vael, the Unhatched Heir *(Act V: the Hatchery)*

**Appearance (evolves across phases):**
- **In Ovo:** a 6-block obsidian-black egg veined with violet light. Inside, a coiled embryo's silhouette moves. Shell fragments orbit it.
- **Hatchling:** a long-necked, eyeless creature with a pale-violet translucent body. Constellations drift *inside* it. Its wings are membranes of thin light. Its limbs are too long. It moves in stuttering, unfinished motions.
- **Ascendant:** the shell fragments fuse into armor plates along its spine; it grows a halo of broken eggshell; its wings solidify into void-black geometry.

**Lore:** The Ender Dragon's heir, a thing that would grow by devouring *space itself*. The dragon kept it from hatching. The Lamplighters shrank the world so there'd be less to devour. You have just fed it three warmths.

**Arena:** The Hatchery: a large cradle-shaped island ring around the Brooding Altar, with floating shell islands. The arena **shrinks** in Phase 3.

| Phase | HP | Moves |
|---|---|---|
| 1, "In Ovo" | 100–70% | The egg is invulnerable except at **glowing fissures** (rotating weak points). **Shell Volley** (orbiting fragments launch in sequence), **Lullaby Pull** (pulls players toward the egg; resist by sprinting outward), **Levitation Nodes** (zones lift you; fall damage risk) |
| 2, "Hatchling" | 70–35% | Vael bursts out. **Umbilical Tether** (attaches to a player; going more than 12 blocks away snaps you back, so dodge-heavy play must stay close), **Starless Breath** (a sweeping beam with a long telegraph), **Stutter Step** (a teleport-strike combo leaving afterimages that attack 0.5s later), **Inversion** (brief arena-wide gravity flip) |
| 3, "Heir Ascendant" | 35–0% | **Devour:** Vael eats outer shell islands one by one, so the arena shrinks on a visible timer. **Echo of the Mother** (ghostly dragon-breath sweeps, a callback to the Ender Dragon), **Unmaking** (Pale-white tendrils emerge from the void edge, blocking ground lanes), **Final Hatching** at 10%: Vael curls into a new egg for 6 seconds; break it before it completes or Vael heals 25% |

**Drops:** **Heir's Wick** (final Pale expansion), Heirshell Plates ×10, Starless Fang (25%), Umbilical Lash (25%), Heir's Wing (guaranteed first kill), Ember Heart ×1, Flask Wick ×1.
**After:** the Heartlamp gutters and the Epilogue begins.

---

### 17.8 The Last Lamplighter *(Epilogue: optional superboss, the Heartlamp Sanctum beneath the Hearthstead)*

**Appearance:** A tall figure in a lamplighter's long coat, with a lantern for a head, carrying a lamp-pole topped with a hooked brass snuffer.

**Fight:** A Radiance-themed duel in a dim sanctum lit only by the player's light sources. They extinguish lights (snuffer hook), relight them as traps, and in Phase 2 they **use the player's own discovered Waylights** as teleport anchors, with the arena showing illusions of each.

**Outcome:** Win or talk them down (a dialogue option unlocked by collecting all lore Ledger entries); either way leads to the **Choice** (§3.3). The Lamplight Regalia and the Last Wick drop on a victory.

---

### 17.9 Minibosses

| Miniboss | Location | Signature mechanic | Drop |
|---|---|---|---|
| **Pale Knight** | Pale Keeps | Fights in fog; vanishes and reappears with a delayed thrust | Hollow Ring, Pale Hook |
| **Rust Duke** | Rust Mines | Rides a minecart along rails; heavy armor that must be dented with Strike | Ember Signet, Climber's Hooks |
| **Hart King** | Taiga, at night (Hart Hunt) | Pack leader; calls Hunt-Spirits; antler charges | Hart's Ring, Longbow recipe |
| **Unsung Knight Captain** | Swallowed Village | Blind duelist; parry-heavy; tracks noise | Echo Pike, Hushsilk |
| **Kiln Matriarch** | Cinder Kilns | Heats up over time: kill fast or face a superheated phase | Cindersteel recipe, Kiln Maul |
| **Null Scribe Primarch** | Null Archive | Writes Rune Script and summons Scribes | Index Page, Voidpearl |
| **Drowned Bellringer** | Drowned Belfry | Underwater bell tolls pull you down | Diver's Bell, Windsail Cape |

---

## 18. Enemy Bestiary

Every enemy has a **lesson**: one thing it teaches or tests.

### 18.1 Overworld (pre-Resounding)
| Enemy | Region | Lesson / behavior |
|---|---|---|
| **Hollowed** | Plains, forests | Basic fodder: slow, grabby, comes in groups; teaches spacing |
| **Tolling Rams** | Plains | Telegraphed charge; teaches rolling sideways |
| **Waxen** | Swamp | Lit wick = explodes in 5s unless killed with a heavy or doused with a water bottle |
| **Will-o'-Wicks** | Swamp | Harmless lure lights; following one leads to Waxen ambushes or hidden loot (50/50) |
| **Sandwrights** | Desert | Reform from sand unless killed with Strike damage |
| **Frostbound Pilgrims** | Snowy | Frozen until heated: light sources thaw them, so sneak in darkness |
| **Thornback Stalkers** | Jungle | Leaf camouflage; reveal themselves with a hiss (audio cue) |
| **Hunt-Spirits** | Taiga, nights | Pack flanking; attack-token AI showcase |
| **Rustbacks** | Badlands, Rust Veins | High-poise beetles; weak underside (lock-on point exposed after a stagger) |
| **Gravelings** | Caves | Gravel mimics that drop from ceilings |
| **Drowned Bellringers** | Ocean | Underwater bell pulls |
| **Sporecallers** | Mushroom | Spore clouds (status); friendly if you have the Spore Hermit's token |

### 18.2 Overworld (post-Resounding)
| Enemy | Lesson |
|---|---|
| **Echo Duelists** | Phantoms that spawn rarely at locations where players died, mimicking the dead player's equipment and moveset at that time. Defeat them for Glim and a chance at an Etching |
| **Pale-touched elites** | Any mob near the Pale gets affixes (Blinking, Shielded, Vampiric, Splitting) |
| **Resonant Golems** | Resonite-ore guardians in deep caves; vulnerable only after being hit by a Resonance attack |

### 18.3 The Hush
| Enemy | Lesson / behavior |
|---|---|
| **Murmurs** | Small swarming sculk-mites, blind; hunt noise; attracted to lantern warmth |
| **Shriekhusks** | Converted zombies; scream when hit (alerting others) unless killed in one blow |
| **Hollow Choristers** | Tall singers that buff nearby Hush mobs while singing; must be interrupted (Bell Toll, ranged hit, or backstab) |
| **Lurkjaws** | Ambush from the sculk floor; reveal themselves with a floor ripple decal |
| **Catalyst Hearts** | Stationary spawner nodes; destroying them stops local spawns and drops Hushsilk |
| **Unsung Knights** | Elite blind swordsmen; parry-centric; strongly punish noisy, roll-spamming players |
| **The Muted** | Converted villagers and animals; harmless at first, but they hum to alert others |

### 18.4 The Nether
| Enemy | Lesson / behavior |
|---|---|
| **Tithe-Collectors** | Armored piglin brutes; grab-steal gold/Glim; kill them to get it back with interest |
| **Gilded Faithful** | Piglin cultists; buff each other via shrines |
| **Kiln-Born** | Basalt golems that heat over time; kill early |
| **Clinker Crabs** | Magma-block mimics |
| **Ash Wraiths** | Drain Resonance; flee from light |
| **Soulbound Ossifers** | Skeleton knights that reassemble unless their soul is freed (Radiance damage, or the reliquary puzzle) |
| **Penitents** | Chained piglins; their chains drag you toward them |
| **Warped Pilgrims** | Neutral; trade and give directions; hostile if you wear Assay Gilt (the Tithe's colors) |

### 18.5 The End
| Enemy | Lesson / behavior |
|---|---|
| **Hollow Envoys** | Enderman variants that swap positions with you |
| **Shellmites** | Shulker cousins firing slow, homing shell shards that pierce |
| **Voidmoths** | Swarm the brightest light source; your lantern becomes a liability |
| **Tetherlings** | Tether to you and periodically yank you back to their position |
| **Null Scribes** | Write delayed-detonation runes on the floor |
| **Starless Wyrmlings** (post-Vael) | Vael's brood; fast fliers; late-game farming target for Heirshell |

---

## 19. Structures

Every structure has a **purpose** column. No purpose → no structure.

| Structure | Dimension / location | Purpose | Contents |
|---|---|---|---|
| **Hearthstead** | Spawn | Hub | Heartlamp, Wren, starting houses (ruined, repairable), tutorial path |
| **Waylights** | Everywhere (fixed spacing ~300 blocks plus dungeons) | Checkpoints and fast travel | Lantern plinth |
| **Belfry Paddock** | Plains / Meadow, Hearthland | Boss 1 | Great Bell, fences, Hollowed shepherds |
| **The Chandlery** | Swamp, Marches | Boss 2 | Wax-dripping mill, candle-making floors, Waxen |
| **Ruined Villages** | Plains / Savanna / Taiga | Rescue, early loot | Old Rivet (caged by Hollowed), Ember Heart, supplies |
| **Pale Keeps** (abandoned castles) | Ring edges | Lore, accessories, miniboss | Pale Knight, Hollow Ring, Lamplighter journals |
| **Catacombs of the Unlit** | Underground, all rings (jigsaw, scaling size) | Main dungeon crawl | Puzzles, traps, elites, Flask Wicks, Etchings |
| **Sunken Orrery** (ancient temple) | Jungle | Navigation | Rotating-ring puzzle; solving it marks boss and structure locations on a map |
| **Surveyor's Vault** (hidden laboratory) | Mountain interiors | Pale lore, utility | Surveyor's Lens, Surveyor's Mark, redstone-door puzzles |
| **Hush Containment Lab** (hidden laboratory) | Under the Hush | Warden gate | Mute Bell blueprint, Muffled Heart |
| **Glass Tombs** | Desert | Fang weapons | Sand-flood traps, Sandwrights |
| **Rust Mines** | Badlands | Upgrade materials, miniboss | Rail network, Rust Duke |
| **Frozen Reliquary** | Snowy | Stamina | Breath Stones, darkness-stealth sections |
| **Hunter's Lodge** | Taiga | NPC, bow line | Huntress NPC, Hart Hunt quest |
| **Hollowed Grove** | Dark Forest | Talisman | Ritual circle event |
| **Petal Shrine** | Cherry | Safe zone | Talisman, sitting bench (mob-free) |
| **Spore Hermit's Hut** | Mushroom | Biome NPC | Spore Hermit |
| **Drowned Belfry** | Ocean | Mobility relics | Underwater bell tower, Drowned Bellringer |
| **Shepherd's Cairns** | Savanna | Lore | Bellwether lore stones, Handbell |
| **Ferryman's Posts** | Rivers | Travel | Boats between distant Waylights |
| **Listening Posts** | Hush edges | Safe point, NPC rescue | Muted Monk, Waylight |
| **Choir Pits / Swallowed Villages** | Hush | Dungeons | Hushsilver, Unsung Knight Captain |
| **The Unsung Nave** | Under the largest Hush scar | Boss 4 | Warden arena |
| **The Counting House** (Nether fortress) | Nether Wastes | Boss 3 | Vault dungeon, Oru, Tithe-Engine |
| **Cinder Kilns** | Basalt Deltas | Dungeon | Kiln Matriarch |
| **Soul Reliquary** | Soul Sand Valley | Dungeon | Puzzle, Flask Wick |
| **Pilgrim Waystations** | Warped Forest | Safe rest | Waylight, Warped Pilgrims |
| **Shrines of Tribute** | Crimson | Risk/reward | Gold offering altar |
| **End Temples (Cenotaphs)** | Outer End | Archivist summon | Index Pages, murals |
| **The Null Archive** | Outer End | Boss 6 | Gravity puzzles |
| **The Hatchery** | Outer End (revealed) | Boss 7 | Brooding Altar |
| **Lamplighter Ruins** | Pale Verge | Endgame lore | Lamplight Embers |
| **Heartlamp Sanctum** | Beneath the Hearthstead | Epilogue | Last Lamplighter |

**Secrets standard:** every dungeon has ≥ 1 secret (a hidden wall revealed by a Surveyor's Lens or a rolling-attack break, a lever puzzle, or a noise puzzle). The Ledger tracks "secrets found / total" per structure.

---

## 20. NPCs, Housing & Settlements

### 20.1 Valid housing (checked by flood-fill, inspired by Terraria)
A room is valid if:
- It is **enclosed**: a flood-fill from the interior stays bounded within limits.
- It has an interior air volume of **48–700 blocks**, with ceilings at least 3 blocks high.
- It has an **entrance**: a door, fence gate, or trapdoor in its boundary.
- It has a **light source**.
- It has a **seat**: stairs, a slab-chair, or a VESTIGE chair block.
- It has a **surface**: a crafting table, table block, lectern, or anvil.
- It contains **< 10% Hush blocks** and **no Hush blocks within 8 blocks**.
- It is **not claimed** by another NPC.

Players check rooms with the **Housing Banner** (right-click: shows a pass/fail checklist; can assign a specific NPC). Checks are budgeted: at most one flood-fill per tick, capped at 2,000 blocks.

### 20.2 Arrival
- At each dawn, the game checks: free valid houses × NPCs whose **arrival conditions** are met. At most one NPC arrives per day. They walk in from the nearest Waylight to their house.
- Dead NPCs return after one full day.
- **Happiness** (biome preference, neighbor likes/dislikes, crowding) adjusts shop prices from ×0.8 to ×1.3 and unlocks bonus stock at high happiness.

### 20.3 Roster

| NPC | Role | Arrival condition | Shop / interaction | Biome pref. |
|---|---|---|---|---|
| **Wren**, Lamplighter's Apprentice | Guide, main quest | Start (lives at the Hearthstead) | Hints, Ledger updates, Flask Kindling | Hearthland |
| **Pell**, Tinker-Merchant | Basic goods | Valid house + player has carried 200 Glim | Torches, rope, Chime Stones, food, arrows, basic potions | Plains |
| **Old Rivet**, the Golem Smith | Blacksmith | Rescued from a Ruined Village, *or* the Bellwether is defeated | Reinforcement, reforge, repairs, Etching application | Mountains |
| **Sable**, the Outfitter | Equipment merchant | Mother Tallow defeated + player owns 3 accessories | Armor pieces, rings, accessory dyes | Forest |
| **The Peddler of Lost Things** | Traveling merchant | Random (≈1 in 4 days), stays one day | Rare rotating stock: Etchings, rings, a guaranteed "lost" item | n/a |
| **Nettle**, Bounty Warden | Quest NPC | Bellwether defeated | Bounty board: elite hunts, rewards | Taiga |
| **The Huntress (Aske)** | Biome NPC | Hart Hunt quest completed | Bows, arrows, Lightfoot Feather | Taiga |
| **Spore Hermit** | Biome NPC | Hut found + quest | Potions, status cures, spore token | Mushroom |
| **The Muted Monk** | Hush NPC | Rescued at a Listening Post | Chime Powder, Bell of Warding, Unsung Shroud (post-Warden). *Speaks only through written notes in the UI* | Prefers silence (no noisy neighbors) |
| **Oru, Ghast-Tongue** | Nether NPC, piglin heretic | Rescued in the Counting House + Tithe-Engine defeated | Nether gear, Ledger of Debts, gold-to-Glim exchange | Warped |
| **Envoy Nihl** | End NPC, an enderman who refuses to teleport | Archivist defeated | Nullsteel gear, Voidpearl, Pale Salts | The End (can live in an End-built house) |
| **The Cartwright** | Utility | Resounding | Sells maps of reclaimed regions and minecart travel | Plains |

**Dialogue:** every NPC has a pool of greetings that react to progression flags (e.g., after the Resounding everyone comments on the noise), comments on neighbors, lore lines, and quest dialogue. Dialogue is data-driven (JSON) with conditions.

**Appearance:** custom models (not villager reskins) with distinct silhouettes: Rivet is an iron golem frame with a leather apron and a forge-glow chest; the Muted Monk has a stitched-mouth hood; Oru is a scarred piglin wearing warped fungus instead of gold; Nihl is an enderman in a heavy travel cloak, chained to an anchor.

### 20.4 Settlement growth
The Hearthstead starts as ruins. Completing settlement quests unlocks **restorable structures** (a well, a forge yard, a lamp-post road). Restoring them extends the Heartlamp's ward radius and adds town ambience (NPC routines, lit streets).

---

## 21. Quests & the Lamp Ledger

### 21.1 The Lamp Ledger (journal UI)
Tabs: **Path** (main quests) · **Errands** (side quests) · **Bounties** · **Bestiary** (kill counts unlock weaknesses, then drops, then lore) · **Places** (discovered structures, secrets found / total) · **Lore** (collected pages) · **Recipes**.

### 21.2 Quest types
- **Path** (main): guided progression ("The bell still rings in the paddock to the east"). Gives hints, never waypoint-hand-holding by default (configurable compass marker).
- **Errands** (NPC side quests, 3–5 per NPC): fetch, rescue, explore, defeat. Each ends with a meaningful reward (accessory, recipe, slot, or NPC stock).
- **Bounties** (Nettle, repeatable): named elites with affixes spawn in a specified region. Rewards are Glim, Temper Shards, and Etchings.
- **Cartography:** discovering every structure type in a ring rewards an Ember Heart.

### 21.3 Quest data
Quests are JSON-defined: objectives (kill / collect / discover / interact / flag), prerequisites, rewards, and dialogue keys. State is stored per player (with shared world flags for boss progression).

---

## 22. World Events

### 22.1 The Resounding *(permanent: after the Warden)*
A world-wide event: every player hears a cascade of stolen sounds. The Ledger opens the Act IV chapter. Effects:
- Dormant Resonite activates; Resonant Hearts begin spawning in deep caves.
- Hush spread is multiplied ×6; the Muted appear.
- Eyes of Ender work.
- Echo Duelists and Resonant Golems join spawn pools.
- New NPC: the Cartwright. Shops gain new stock.
- (Once lit) the Unsung Wick → Pale 2,500.

### 22.2 Quiet Night *(random night, ~1 in 8; more frequent post-Resounding)*
The Hush "breathes": the moon dims, music cuts out, and Hush mobs spawn anywhere outside warded radii. Survivors earn Hushsilk and a chance at a Quietcap Talisman.

### 22.3 The Collection *(after the Tithe-Engine, if a player carries > 64 gold)*
Piglin Tithe-Collectors come through overworld portals for a 5-minute raid on the settlement, demanding tithe. Pay (lose gold) or fight (loot, Assay Gold).

### 22.4 The Hart Hunt *(taiga, full moon)*
Hunt-Spirits roam the taiga. The Hart King may appear.

### 22.5 Starfall *(post-Vael)*
Void shards fall in the outer rings. They contain Heirshell fragments but spawn Starless Wyrmlings.

---

## 23. Economy & Loot

### 23.1 Glim
- **Glim** is a numeric purse, not an inventory item. It is shown on the HUD.
- Dropped by enemies (scaled by tier and elite status), found in chests, and earned from quests and selling.
- Spent at NPC shops, on reinforcement, reforging, and Flask Kindling.
- Lost on death unless recovered (§8.5).
- **Not** used for leveling.

### 23.2 Loot philosophy
- **Hand-placed key loot:** every Ember Heart, Breath Stone, Flask Wick, and unique accessory is placed in a specific structure or boss table. No RNG for progression-critical items.
- **Tiered random loot:** chests roll from ring-tier tables (materials, consumables, Glim, Temper Shards, a small chance of an Etching or ring).
- **Rare drops:** each named enemy has a ~1–3% unique drop (e.g., the Hollow Chorister's **Choir Mask**).
- **Rarity colors:** Common (grey), Uncommon (green), Rare (blue), Relic (violet), Wick (ember orange: boss-unique).
- **Loot tables** are data-driven. Vanilla loot tables in vanilla structures are *augmented* (via loot modifiers), not replaced, so vanilla progression still functions.

---

## 24. User Interface

| Element | Description |
|---|---|
| **HUD** | Top-left: HP (red), Stamina (green), Resonance (gold) bars with segment ticks. Bottom-right: Glim counter. Bottom-left: the **Readiness Cross** (4 tiles in the reference style: Main / Secondary / Relic / Flask with charge count). The vanilla heart row is hidden (replaced by the HP bar); the armor and hunger bars stay visible because hunger matters |
| **Boss bar** | Bottom-center, wide: name in serif-ish pixel caps, HP bar with phase pips, poise sub-bar |
| **Lock-on reticle** | Brass diamond on the target. Red when a red-glint attack is incoming |
| **Noise ring** | Hush only: a crosshair ring filling with noise |
| **Damage numbers** | Optional (default off); colored by damage type |
| **Equipment screen** | §9.5 |
| **Lamp Ledger** | Book-like, two-page spread; parchment on a dark leather frame |
| **Shop UI** | Dark panel, NPC portrait, buy/sell tabs, Glim prices, happiness indicator |
| **Dialogue** | Bottom-of-screen box with the NPC's name and portrait; options as buttons |
| **Waylight menu** | Rest / Travel / Kindle Flask (at the Heartlamp) |
| **Housing Banner check** | Checklist overlay (✓/✗ per requirement) |

All screens share one style kit: slate panels, brass trims, ember highlights, and parchment text.

---

## 25. Art & Audio Direction

### 25.1 Visual rules
- **Minecraft first:** 16×16 block textures, low-poly box-based entity models (Blockbench), no smooth gradients or photoreal shading.
- **Regional palettes:**
  - Hearthland: warm greys, amber lights, faded wool colors.
  - Swamp/Chandlery: off-white wax, sooty brown, candle yellow.
  - Hush: grey-teal, bone white, cyan soul glow, deep blue-black.
  - Nether: blackstone, tarnished gold, slag orange, soul cyan.
  - End: shell black, violet, starlight white.
  - Pale: desaturated white fog, ash.
- **Bosses** read clearly in silhouette from 30 blocks away. Weak points glow.
- **Animations:** keyframed in Blockbench. Attacks have exaggerated windups (readability), snappy active frames, and weighty recoveries. Bosses get idle "breathing" loops so they never look frozen.
- **Player animations:** custom attack, roll, flask, and riposte animations in first and third person.

### 25.2 Audio
- Bells (Bellwether), crackling wax (Tallow), clanking coins and furnaces (Tithe-Engine), and silence (Warden) are each boss's audio identity.
- The Hush is defined by the *absence* of sound: ducking other channels is the main tool.
- Telegraph sounds are distinct per attack type (grab chime, red-glint hiss, white-glint ring).
- Vanilla sounds are reused and pitched where appropriate. New sound assets are added gradually.

---

## 26. Multiplayer Rules
- **Progression flags are world-wide** (like Terraria): one Wick lit expands the Pale for everyone.
- **Per-player:** equipment, Glim, quest progress, Ledger, Ember Hearts and Breath Stones, Waylights lit.
- Boss loot rolls per participating player (anyone who dealt damage or was in the arena when the seal closed).
- **Lock-on and combat are server-authoritative** with client-side animation prediction. I-frames are evaluated on the server, with a small latency tolerance (configurable, default 2 ticks).
- PvP follows the vanilla gamerule; the combat system applies.

---

## 27. Configuration

**Implemented so far** (`serverconfig/vestige-server.toml`, editable in-game through the mod list's config screen):

| Key | Default | Range | Since |
|---|---|---|---|
| `border.scaleDimensions` | `true` | n/a | M2 |
| `border.netherDivisor` | `4.0` | 1–16 | M2 |
| `border.endDivisor` | `1.0` | 1–16 | M2 |
| `border.minimumScaledSize` | `32.0` | 1–1024 | M2 |

Options are added in the same milestone as the system that reads them, so the file never contains switches that do nothing. **Planned** server config (TOML), including at least:
- `border.initialRadius` (750), `border.expansions` (list per Wick), `border.maxRadius` (6000), `border.center`, `border.expansionSeconds` (60), `border.staticMode` (false).
- `rings.mobScaling` (list), `rings.eliteChance`.
- `hush.scarCount` (2), `hush.spreadEnabled`, `hush.spreadRate`, `hush.postResoundingMultiplier`.
- `combat.hpScale` (5), `combat.iframeLatencyTolerance` (2), `combat.autoCombatStance` (true), `combat.damageNumbers` (false).
- `death.runMode` (WAYFARER / PILGRIM / ASHEN).
- `waylight.respawnDungeonMobs` (true), `waylight.fastTravel` (true).
- `npc.arrivalPerDay` (1), `npc.travelingMerchantChance`.
- `boss.multiplayerScaling` (0.6), `boss.dragonHpMultiplier`.
- `quests.showCompassMarkers` (false).
Client config: HUD layout, damage numbers, lock-on camera smoothing, keybinds.

---

## 28. Content Budget (target for 1.0)

| Category | Count |
|---|---|
| Major bosses | 7 (+1 optional superboss) |
| Minibosses | 7 |
| Custom enemy types | ~35 |
| NPCs | 12 |
| Weapons | ~45 (8 classes + shields) |
| Armor sets | 11 (+ loose pieces) |
| Rings / Talismans / Relics | ~16 / ~14 / ~14 |
| Custom structures | ~30 types |
| Custom biomes | 1 major (Hush) + visual sub-variants |
| Custom cave types | 5 |
| Events | 5 |
| Quests | ~60 (main + errands) |

This budget is large. Each milestone must deliver a **vertical slice**: fewer items, finished properly, rather than everything at half quality.

---

## 29. Technical Risks: Resolved in Milestone 1

All 14 assumptions were checked against the real NeoForge 21.1.266 / Minecraft 1.21.1 toolchain: decompiled sources, compiled prototypes, GameTests and in-game runs. Full details and evidence are in [ARCHITECTURE.md §2](ARCHITECTURE.md#2-verification-results).

Changes to the design caused by verification:
- **Nether border:** vanilla does *not* scale it. VESTIGE scales it explicitly, with a default divisor of 4 (§6.1).
- **Hush generation:** a custom wrapping biome source instead of TerraBlender, because it guarantees scar placement per ring (§14.2).
- **Strongholds:** ring distance override 32 → 20 (§6.3).
- **Combat stance:** physical item swap between RPG slots and the hands (§7.3).
- **Controls:** combat binds use their own key-conflict context (§7.2).
- **Audio:** the container has no audio device, so Hush and Warden audio ducking can only be verified by logic and logs, not by listening. It is flagged that way when implemented.

Visual verification is possible. A full client runs headless (software OpenGL under Xvfb) and loads into a world, so HUD, UI, models and animations are checked from real in-game screenshots.

---

## 30. Milestone Roadmap

Each milestone ends with: code written → imports and APIs verified → `./gradlew build` passes → automated GameTests run → manual or headless test notes → docs updated → commit.

| # | Milestone | Deliverable (vertical slice) |
|---|---|---|
| 0 | **Game Design Document** ✅ | This document |
| 1 | **Technical architecture** ✅ | Verified dependency list, package layout, data model (attachments, components, registries), networking map, ADRs for risks 1–14 |
| 2 | **Project setup** ✅ | Compiling NeoForge 1.21.1 mod, single-JAR release with bundled libraries, run configs, module system with enforced dependency rules, GameTest and unit-test harnesses, verification scripts (dev and production server), config with in-game screen, datagen, cross-dimension border scaling |
| 3 | **Core RPG systems** | HP ×5 scaling, stamina, Resonance, Glim purse and Ember-Ghost, Wick Flask, Waylights (rest, respawn, travel), HUD |
| 4 | **Combat system** | Stances, light/heavy combos, rolls with i-frames, guard/parry, poise/stagger/riposte, lock-on, damage formula, 3 weapon classes with arts, enemy AI framework + 2 test enemies (Hollowed, Tolling Ram) |
| 5 | **Equipment system** | Equipment UI, slots and unlocks, item stat components, burden, reinforcement/reforge (temporary station until Old Rivet exists), first accessories |
| 6 | **World generation** | The Pale (border control and expansion), rings and mob scaling, Waylight placement, Hearthstead, Belfry Paddock + **Bellwether boss** (first full boss as the template), Chandlery + **Mother Tallow** |
| 7 | **Hush biome & Warden** | Hush biome, noise system, Hush mobs, Echo Hollows, spread, Listening Post, Containment Lab, Unsung Nave, **Warden of the Unsung**, the Resounding |
| 8 | **Nether content** | Tithelands enemies, Counting House, Cinder Kilns, Lava Tubes, **Tithe-Engine** |
| 9 | **End content & egg boss** | Eye-of-Ender gate, End temples, Null Archive, **Severed Archivist**, Hatchery, Hatching Rite, **Vael** |
| 10 | **NPC & housing** | Housing checks, arrival logic, NPC models, dialogue, shops, quests, Lamp Ledger |
| 11 | **Structures & loot** | Remaining structures, secrets, loot tables and modifiers, minibosses, events |
| 12 | **Balancing & optimization** | Numeric pass across all tiers, profiling (spread, flood-fill, AI), config polish, final JAR, player guide |

*Note on ordering:* the user-specified order is kept. The Bellwether and Mother Tallow land in Milestone 6 because they are the overworld progression's first gates and the boss template every later boss builds on. NPC housing comes in Milestone 10, so early milestones provide temporary access to Old Rivet's services (e.g., a Founder's Anvil fallback) that are moved to the NPC later.

---

## 31. Glossary

| Term | Meaning |
|---|---|
| **The Pale** | The world border, a fog wall of unmaking |
| **Wick** | Boss-dropped anchor; lighting it at the Heartlamp expands the Pale |
| **Heartlamp** | Spawn hub flame; Wick lighting, flask upgrades |
| **Waylight** | Checkpoint lantern (rest, respawn, fast travel) |
| **Glim** | Currency, dropped on death |
| **Ember-Ghost** | Your dropped Glim at the death location |
| **Resonance** | Combat resource for weapon arts |
| **Burden** | Equip load ratio, which sets the roll type |
| **The Hush** | Sculk evil biome |
| **Noise** | Sound value of player actions, used by Hush AI |
| **The Resounding** | Post-Warden world state (hardmode equivalent) |
| **Warmths** | Tallow Ember, Gilded Heart, and Unsung Echo, required to hatch Vael |
| **Etching** | Item that changes the weapon art on non-unique weapons |
| **Rings (world)** | Distance bands: Hearthland, Marches, Wilds, Reaches, Pale Verge |
