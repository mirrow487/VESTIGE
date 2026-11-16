# VESTIGE: Technical Architecture

**Milestone 1 deliverable.** Verified toolchain, dependency decisions, system architecture, data model, networking, asset pipeline, and test strategy.
Companion to the [Game Design Document](GDD.md). Section references like "GDD §7" point there.

Every API named in this document was checked against the decompiled NeoForge 21.1.266 / Minecraft 1.21.1 sources or exercised in a running prototype (see §2). Anything not yet verified is marked **[verify in M*n*]**.

---

## Contents
1. [Toolchain & Dependencies](#1-toolchain--dependencies)
2. [Verification Results](#2-verification-results)
3. [Architectural Principles](#3-architectural-principles)
4. [Module & Package Layout](#4-module--package-layout)
5. [Data Model](#5-data-model)
6. [Networking](#6-networking)
7. [Subsystem Designs](#7-subsystem-designs)
8. [Asset Pipeline](#8-asset-pipeline)
9. [Testing Strategy & Definition of Done](#9-testing-strategy--definition-of-done)
10. [Performance Budgets](#10-performance-budgets)
11. [Architecture Decision Records](#11-architecture-decision-records)
12. [Build Environment](#12-build-environment)

---

## 1. Toolchain & Dependencies

All versions are pinned. They were resolved from the live Maven repositories on 2026-11-15 and compiled together successfully.

| Component | Version | Coordinates / source | License | Role |
|---|---|---|---|---|
| Minecraft | 1.21.1 | n/a | n/a | Target |
| Java | 21 (OpenJDK 21.0.12) | n/a | n/a | Toolchain |
| Gradle | 8.14.3 | Wrapper (`gradlew`, committed) | n/a | Build |
| NeoForge | **21.1.266** | `net.neoforged:neoforge` @ maven.neoforged.net | LGPL-2.1 | Mod loader |
| ModDevGradle | **2.0.148** | `net.neoforged.moddev` plugin | LGPL-2.1 | Gradle plugin (decompile, runs, jarJar) |
| GeckoLib | **4.9.3** | `software.bernie.geckolib:geckolib-neoforge-1.21.1` @ dl.cloudsmith.io | MIT | Entity and boss model animation |
| playerAnimator | **2.0.4+1.21.1** | `dev.kosmx.player-anim:player-animation-lib-forge` @ maven.kosmx.dev | MIT | Player attack, roll, and flask animations |
| ~~TerraBlender~~ | *(not used)* | (1.21.1-4.1.0.8 verified available) | LGPLv3 | Replaced by a custom biome source (ADR-004) |
| Parchment mappings | *(not used)* | Redirects to a host blocked in the build environment | n/a | Official Mojang names are used; parameter names stay `p_xxxxx_` |
| JUnit 5 | **5.14.4** (BOM) | Maven Central | EPL | Pure-logic unit tests and architecture rules |

**Distribution:** GeckoLib and playerAnimator are bundled with **Jar-in-Jar** (`jarJar` configuration of ModDevGradle, version ranges `[4.9.3,4.10)` and `[2.0.4+1.21.1,2.1)`). The release is a single `vestige-<version>.jar`. Verified: the built jar contains `META-INF/jarjar/geckolib-neoforge-1.21.1-4.9.3.jar`, `player-animation-lib-forge-2.0.4+1.21.1.jar`, and `metadata.json`.

---

## 2. Verification Results

Method: a throwaway NeoForge project (all candidate dependencies) was compiled. The decompiled game sources were inspected directly. Prototypes were run as a **GameTest server**, a **dedicated server**, and a **full client under Xvfb**.

| # | Assumption | Result | Evidence |
|---|---|---|---|
| 1 | NeoForge 1.21.1 + ModDevGradle is the current setup | ✅ Confirmed | NeoForge 21.1.266 + MDG 2.0.148 resolve, decompile (5,364 sources), and compile |
| 2 | Per-player persistent data via data attachments | ✅ Confirmed, **with built-in sync** | `AttachmentType.Builder` has `serialize(Codec)`, `copyOnDeath()`, `copyHandler(...)` and **`sync(StreamCodec)` / `sync(BiPredicate, StreamCodec)`**. `IAttachmentHolder.syncData(type)` re-syncs after in-place mutation |
| 3 | Networking via `CustomPacketPayload` + payload registrar | ✅ Confirmed | `RegisterPayloadHandlersEvent` (mod bus); `PayloadRegistrar.playToServer/playToClient/playBidirectional`, `.versioned()`, `.executesOn(HandlerThread)`; `PacketDistributor.sendToServer/sendToPlayer/sendToPlayersTrackingEntityAndSelf` |
| 4 | Item stats via data components | ✅ Confirmed | `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, modid)` |
| 5 | Hiding vanilla HUD layers | ✅ Confirmed | `RenderGuiLayerEvent.Pre` (cancellable) + `VanillaGuiLayers.PLAYER_HEALTH`, `ARMOR_LEVEL`, `FOOD_LEVEL`, `BOSS_OVERLAY`, `CROSSHAIR`. Own layers via `RegisterGuiLayersEvent` |
| 6 | Entity animation library | ✅ GeckoLib 4.9.3 | `GeoEntity`, `GeoEntityRenderer`, `GeoModel`, `AnimationController`, `RawAnimation` present. Loaded as a mod in the dev client |
| 7 | Player animation library | ✅ playerAnimator 2.0.4 | `PlayerAnimationAccess`, `PlayerAnimationRegistry` (loads `assets/<ns>/player_animations/*.json`), `ModifierLayer`, `KeyframeAnimationPlayer`. Loaded in the dev client |
| 8 | Adding the Hush biome | ✅ **Custom biome source chosen** | Prototype `OverlayBiomeSource` registered in `Registries.BIOME_SOURCE`, injected into an override of `data/minecraft/worldgen/world_preset/normal.json`. The fresh world reported `mushroom_fields` with **mycelium surface** inside the seed-derived disc at x=600, and vanilla biomes outside. The seed is injected at `ServerAboutToStartEvent`, which fires before `loadLevel()` on dedicated *and* integrated servers |
| 9 | Nether and End borders follow the Overworld (scaled) | ❌ **False, fixed** | `BorderChangeListener.DelegateBorderChangeListener` copies **size unscaled**; only the center is scaled (`Level.java:140`). Only the Overworld border is saved (`MinecraftServer.java:557`) and re-applied at load. GameTest `vanillaNetherBorderEqualsOverworld` ✅ confirmed this. GameTest `rescalingListenerOverridesDelegate` ✅ proved the fix: a listener added after the delegate rescales both `setSize` and `lerpSizeBetween` |
| 10 | Strongholds can be repositioned via datapack | ✅ Confirmed | `strongholds.json` uses `concentric_rings` (`distance` 32, `spread` 3, `count` 128). Ring radius in chunks = `4d + 6d·ring ± 1.25d`. `distance: 20` → first ring at 880–1,680 blocks |
| 11 | Structures via datapack JSON + `.nbt` templates | ✅ Registries confirmed | `STRUCTURE_TYPE`, `STRUCTURE_PIECE`, `STRUCTURE_PLACEMENT`, `CARVER`, `FEATURE`, `PLACED_FEATURE` registries exist. A generated `.nbt` template loaded in GameTests. Full jigsaw pipeline **[verify in M6]** |
| 12 | Client audio ducking | ⚠️ API confirmed, **not audible here** | `PlaySoundEvent`, `PlaySoundSourceEvent`, `PlayStreamingSourceEvent`, `SoundEngineLoadEvent` exist. The container has no audio device ("Error starting SoundSystem"), so sound features are verified by logic and logs only |
| 13 | Server performance of spread and flood-fill | ⏳ Budgeted by design (§10) | Profiled when implemented (M7, M10) |
| 14 | Headless automated testing | ✅ Confirmed | `runGameTestServer`: "All 2 required tests passed". Runs with no display |
| + | **Visual verification** | ✅ Possible | Full client under `xvfb-run` with Mesa llvmpipe renders the title screen and **loads into a world** via `--quickPlaySingleplayer`. Screenshots with ImageMagick `import`. No input automation tool is installed, so in-game actions are driven by a dev-only test driver (§9.3) |
| + | Multipart entities | ✅ | `net.neoforged.neoforge.entity.PartEntity`, `IEntityExtension.isMultipartEntity()/getParts()` |
| + | Damage pipeline hooks | ✅ | `LivingIncomingDamageEvent` (`getContainer()`, `setAmount`, `addReductionModifier(DamageContainer.Reduction, IReductionFunction)`, `setInvulnerabilityTicks`), `LivingDamageEvent`, `LivingDeathEvent`, `LivingDropsEvent`, `LivingHealEvent` |
| + | Input interception | ✅ | `InputEvent.InteractionKeyMappingTriggered` (cancellable; `isAttack/isUseItem/isPickBlock`, `setSwingHand`), `InputEvent.MouseScrollingEvent` (cancellable), `IKeyConflictContext` on `KeyMapping` |
| + | Camera control | ✅ | `ViewportEvent.ComputeCameraAngles.setYaw/setPitch/setRoll` |
| + | Menus | ✅ | `IMenuTypeExtension.create(IContainerFactory)`, `IPlayerExtension.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)` |
| + | Noise source events | ✅ | `VanillaGameEvent` (`getVanillaEvent`, `getEventPosition`, `getCause`) |
| + | Item toss interception | ✅ | `ItemTossEvent` (cancellable) |
| + | Global loot modifiers | ✅ | `NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS` |
| + | Key-conflict contexts | ✅ | `IKeyConflictContext.isActive()/conflicts()` |

**Findings that changed the design** were written back into GDD v1.1 (§0, §6.1, §6.3, §7.2, §7.3, §9.5, §14.2, §29).

### 2.1 Findings during Milestone 3

| Finding | Consequence |
|---|---|
| playerAnimator mirrors torso/body yaw and roll relative to vanilla model parts (measured with the `calib` driver scenario) | `PlayerAnimations` negates those channels; pose data uses one convention (`combat.anim.Channel`) |
| A `SpeedModifier` passed to a `ModifierLayer` constructor before its animation is set is never linked, so the layer reads inactive and nothing plays | One speed modifier per player layer, added with `addModifierLast` |
| Positive item pitch tips a held item's point forward | Thrust poses use positive item pitch |
| NeoForge refuses payloads on connections without negotiated channels | `core.Net` guards sends; GameTest players use `NetworkRegistry.configureMockConnection` |
| The GameTest world is creative, and new server players have 60 ticks of spawn protection | Test players are set to survival and run their light tick 61 times |
| `/kill` deals `Float.MAX_VALUE`; scaling it ×5 overflowed to Infinity, giving NaN health and an unkillable entity | `HealthScaling.scaleAmount` saturates; regression GameTest |
| Software rendering is slow, so screenshots landed several ticks late | The client driver slows the game with vanilla `/tick rate` while capturing |
| Vanilla clamps the `max_health` attribute at 1024 (found in M4 tests) | With the ×5 scale, bosses will exceed it: boss health must be handled outside the vanilla attribute (planned with the boss framework, M6+) |

---

## 3. Architectural Principles

1. **The server is authoritative for outcomes; the client is authoritative for feel.** The server decides hits, damage, stamina, i-frames, loot, and progression. The client predicts animations, camera, and movement so input feels instant.
2. **Ticks are the unit of time.** All combat timing is in server ticks (20/s), defined once in data and shared by hit detection *and* animation generation, so they cannot drift apart (§8.2).
3. **One combat runtime for everyone.** Players, enemies, and bosses execute the same `Action` model (windup → active → recovery, hitbox sweeps, poise, telegraphs). Bosses add phase logic on top, not a separate engine.
4. **Data over code for content; code for behavior.** Weapons, movesets, armor, accessories, loot, quests, dialogue, shops, and structures are data (JSON / components / datapack). Unique behaviors (boss AI, special accessory effects) are code, registered by ID.
5. **Strict client/server separation.** Client-only classes live under `client/` and are only reachable through `@EventBusSubscriber(value = Dist.CLIENT)` or `FMLEnvironment.dist` checks. Dedicated-server and GameTest runs are part of CI to catch leaks.
6. **Budgets everywhere.** Any world-scanning system (spread, housing, structure checks) runs under a per-tick budget with a work queue.
7. **Vanilla-compatible by default.** Augment vanilla systems (loot modifiers, real held items in combat stance, vanilla armor slots) instead of replacing them, to stay compatible with other mods.

---

## 4. Module & Package Layout

Single Gradle project, mod id **`vestige`**, base package **`dev.vestige`**.

### 4.1 Module system (implemented in Milestone 2)

Every gameplay system is a **module**: a top-level package under `dev.vestige` with one class implementing `core.VestigeModule` (`id()` equals the package name; `init(modBus, gameBus)`).

- **Modules own their registrations.** Each module creates its own `DeferredRegister`s and listeners. *Changed from the M1 plan*, which had a central `registry/` package. Per-module ownership keeps systems separable (requirement: combat, weapons, equipment, bosses, NPCs, world generation, and UI as separate systems).
- **`core.VestigeModules`** is the single ordered list of common modules. `core.ModuleLoader` initializes them and rejects duplicate ids.
- **Client code** lives in `dev.vestige.client` and starts from a separate `@Mod(value = "vestige", dist = Dist.CLIENT)` entry point (`VestigeClient`). Client-side parts of a system go in `client/<system>/`.
- **Dependency rules are enforced by a unit test** (`ArchitectureRulesTest`), which scans imports on every build:
  1. Every module package must be declared with the modules it may depend on (`core` and root classes like `VestigeIds` are always allowed).
  2. Common code must not import client classes (`dev.vestige.client`, `net.minecraft.client`, `com.mojang.blaze3d`, `net.neoforged.neoforge.client`).
  3. Module ids must equal package names.
  Adding a dependency between systems is therefore an explicit, reviewed change to that table.

**Implemented:**
- M2: `core`, `config`, `pale` (cross-dimension border scaling), `datagen`, `dev`, `gametest`, `client`.
- M4: `player` (Glim, Wick Flask, Ember-Ghost, Run Mode; depends on config, vitals, combat, entity) and `waylight` (block, registry, rest, travel; depends on config, vitals, combat, player). The combat module stays independent of both: the flask reaches the action machine through `PlayerCombat.setFlaskCheck`, the same hook pattern as `VitalsModule.setRegenFactor`.
- M3: `vitals` (health scale, stamina), `weapon` (items, weapon profiles), `combat` (actions, machine, hits, damage, payloads), `entity` (enemy base class, AI, Hollowed), `catalog` (creative tab, spawn eggs). Client: `client.combat`, `client.anim`, `client.entity`, `client.hud`, `client.fx`, `client.devdriver`. Shared infrastructure in `core`: `SyncedDataTable` (datapack JSON with codecs, reloadable, synced to clients) and `Net` (sends that skip connections lacking VESTIGE channels).
- Combat details: [systems/combat.md](systems/combat.md). The tree below is the full target layout; packages are created when their system is implemented, never as empty placeholders.

**Target layout:**

```
src/main/java/dev/vestige/
├── Vestige.java                  // @Mod entry: registers configs, initializes the module list
├── VestigeIds.java               // MOD_ID, logger, id() helper
├── core/                         // VestigeModule, ModuleLoader, VestigeModules (module list)
├── config/                       // ServerConfig, ClientConfig (ModConfigSpec)
├── network/                      // Payload records + registration (§6), one sub-package per feature
├── progression/                  // WorldProgress (SavedData): bosses, wicks, resounding, flags
├── pale/                         // PaleManager, DimensionBorderScaler, Rings, Pale-touched spawns
├── player/                       // Vitals (stamina, resonance, flask, glim), EmberGhost, RunMode
├── combat/
│   ├── action/                   // Action, ActionPhase, ActionRunner (shared by players and mobs)
│   ├── moveset/                  // MoveSet & AttackDef (data-driven), reload listener
│   ├── hit/                      // HitboxSweeper (OBB sweep), HitRegistry (once per swing)
│   ├── damage/                   // DamageProfile, DamageCalculator, Absorption, StatusBuildup
│   ├── defense/                  // Dodge (i-frames), Guard, Parry, Poise/Stagger, Riposte
│   └── art/                      // WeaponArt registry (code behaviors by id)
├── equipment/                    // RpgInventory attachment, SlotType, StanceSwap, StatSheet,
│                                 // Burden, AccessoryEffect registry, slot unlocks
├── item/                         // WeaponItem, RpgArmorItem, AccessoryItem, components
│                                 // (WeaponStats, ArmorStats, Reinforcement, Prefix, Etching)
├── entity/
│   ├── ai/                       // VestigeMob base, ActionBrain, AttackSelector, AttackTokens,
│   │                             // Telegraph, Hearing (Hush), PaleAffixes
│   ├── mob/{overworld,hush,nether,end}/
│   ├── boss/                     // BossEntity base, PhaseController, ArenaSeal, BossBar,
│   │                             // Summoning, MultiplayerScaling, one package per boss
│   └── npc/                      // NpcEntity base, routines
├── world/
│   ├── biome/                    // HushOverlayBiomeSource, ScarLayout (seeded, ring-aware)
│   ├── structure/                // Structure types, RingPlacement, pieces, processors
│   ├── carver/ feature/          // Wick Caverns, Echo Hollows, Lava Tubes, Shell Hollows
│   └── waylight/                 // Waylight block entity, registry of lit Waylights, travel
├── hush/                         // NoiseSystem, HushSpread (budgeted), purification
├── npc/                          // HousingValidator, HousingQueue, ArrivalScheduler,
│                                 // Happiness, Dialogue, Shop
├── quest/                        // QuestDef (JSON), QuestTracker, objectives, LampLedger
├── event/                        // world events: Resounding, QuietNight, Collection, HartHunt, Starfall
├── client/
│   ├── hud/                      // Vitals bars, Readiness Cross, boss bar, noise ring, lock reticle
│   ├── screen/                   // EquipmentScreen, LedgerScreen, ShopScreen, DialogueScreen,
│   │                             // WaylightScreen, HousingCheckOverlay; ui/ tile kit (§7.11)
│   ├── input/                    // Keybinds, CombatKeyContext, input interception
│   ├── camera/                   // LockOnController (critically damped), shoulder offset
│   ├── anim/                     // playerAnimator bridge, action → animation mapping
│   ├── render/                   // GeckoLib renderers, telegraph decals, hit FX
│   └── sound/                    // Hush and Warden ducking, telegraph cues
├── datagen/                      // Providers: models, blockstates, lang, loot, recipes, tags
├── dev/                          // Dev-only hooks behind system properties (smoke autostop, test driver)
└── gametest/                     // GameTests per subsystem (gametest/<module>/)
src/main/resources/
├── META-INF/neoforge.mods.toml
├── assets/vestige/{textures,models,geo,animations,player_animations,lang,sounds}
└── data/
    ├── vestige/{movesets,weapons,quests,dialogue,shops,loot_table,structure,worldgen,...}
    └── minecraft/worldgen/{world_preset/normal.json, ...large_biomes, amplified, structure_set/strongholds.json}
tools/                            // Python asset generators (§8), run with `python3 -I`
docs/                             // GDD, ARCHITECTURE, per-system docs
```

---

## 5. Data Model

### 5.1 Player attachments (`AttachmentType`, serialized with Codecs)

| Attachment | Contents | Persist | Copy on death | Sync to owner | Sync to trackers |
|---|---|---|---|---|---|
| `vitals` | stamina, maxStaminaBonus, resonance, flaskCharges, flaskMax, kindling, emberHearts, resonantHearts, breathStones | ✅ | ✅ | ✅ | ❌ |
| `glim` | carried Glim | ✅ | ✅ (0 after death, ghost holds it) | ✅ | ❌ |
| `rpg_inventory` | Main, Secondary, swap set, Rings[2], Talismans[2], Relics[4], stance stash (main and off-hand) | ✅ | ✅ (always kept) | ✅ | ✅ (visuals) |
| `combat_state` | current action id, phase, tick, stance, lock target id, poise | ❌ (transient) | ❌ | ✅ | ✅ (for animation) |
| `discovery` | lit Waylights, discovered structures, secrets, bestiary counts, lore | ✅ | ✅ | ✅ | ❌ |
| `quests` | active and completed quests, objective progress | ✅ | ✅ | ✅ | ❌ |
| `slot_unlocks` | unlocked accessory slots | ✅ | ✅ | ✅ | ❌ |

Rules: mutate through a service class only (`Vitals.spendStamina(player, n)`), and call `player.syncData(type)` after in-place mutation. Hot values (stamina every tick) are synced at most every 2 ticks, and only when changed.

### 5.2 World data (`SavedData` on the Overworld)

- `WorldProgress`: boss kills (first-kill flags), wicks lit, `resounding` flag, ending choice, event cooldowns.
- `WaylightRegistry`: positions and dimensions of all generated Waylights (for travel).
- `HousingRegistry`: valid houses, assigned NPC, last validation tick.
- `EmberGhosts`: per-player ghost position and amount.
- Hush spread queues are rebuilt from loaded chunks; they are not persisted.

### 5.3 Item data components

| Component | On | Fields |
|---|---|---|
| `vestige:weapon` | Weapons | class, moveset id, AR per damage type, poise damage, status buildups, weight, art id |
| `vestige:armor` | Armor | defense and absorption per type, poise, weight, set id |
| `vestige:accessory` | Rings, talismans, relics | family, effect ids + params, weight |
| `vestige:reinforcement` | Weapons, armor | level 0–10 |
| `vestige:prefix` | Any RPG item | prefix id |
| `vestige:etching` | Non-unique weapons | art override id |

Base stats live in datapack JSON (`data/vestige/weapons/*.json`) keyed by item id, so balancing needs no code change. Components hold only per-stack state (reinforcement, prefix, etching).

### 5.4 Data-driven content (reloadable)

`movesets/`, `weapons/`, `armor/`, `accessories/`, `quests/`, `dialogue/`, `shops/`, `npcs/` (arrival conditions, preferences), loot tables, and structures. Each type has a codec and a `SimpleJsonResourceReloadListener`, validated at load. Invalid entries fail loudly in dev and are skipped with an error log in production.

---

## 6. Networking

Registered once in `RegisterPayloadHandlersEvent` with `registrar.versioned("1")`. Records with `StreamCodec`s.

| Payload | Direction | Purpose |
|---|---|---|
| `CombatInput` | C→S | Light, heavy (press/release), roll (direction), guard (on/off), art, flask, stance toggle, swap secondary. Carries the client tick for latency tolerance |
| `LockOnRequest` | C→S | Target entity id or clear. The server validates range and LOS |
| `ActionStart` | S→C (tracking + self) | Entity id, action id, start tick. Drives animations for everyone |
| `ActionCancel` | S→C | Interrupt (stagger, parry) |
| `HitFeedback` | S→C | Hit-stop length, hit position, damage type, material (for particles and sound) |
| `TelegraphCue` | S→C | Glint type, decal shape and position, duration |
| `BossState` | S→C | Phase, poise, multi-part targets |
| `PaleUpdate` | S→C | Ring and Pale info for HUD and fog |
| `OpenEquipment` / menu sync | via `openMenu` | Equipment screen |
| `ShopAction`, `DialogueChoice`, `QuestTrack` | C→S | UI actions, validated on the server |
| `HousingReport` | S→C | Checklist results for the Housing Banner |
| `WaylightTravel` | C→S | Travel request, validated (combat, hostiles, lit) |

Attachments with `.sync(...)` cover vitals, glim, inventory, discovery, and quests. No custom packets are needed for those.

---

## 7. Subsystem Designs

### 7.1 Combat runtime (GDD §7). **Highest priority.**

**Action model** (shared by players, mobs, and bosses):

```
Action { id, windupTicks, activeTicks, recoveryTicks, comboWindow, cancelWindow,
         staminaCost, motionValue, poiseDamage, hyperarmor, hitbox: HitboxSpec,
         movement: RootMotion (per-tick forward/side displacement), telegraph: Glint,
         animation: animation id, onHit/onStart hooks (by id) }
```

- **ActionRunner** (server, ticked from `EntityTickEvent.Post` for players and from the AI for mobs): advances phases, applies root motion, runs hitbox sweeps during active ticks, opens combo and cancel windows, and fires `ActionStart` and `ActionCancel`.
- **Input buffering:** the server keeps a 6-tick buffer per player. The client sends inputs immediately and *predicts* the animation start. If the server rejects the input (no stamina, locked), it sends `ActionCancel` and the client blends out.
- **Hit detection (`HitboxSweeper`):** each active tick, sample the weapon arc as oriented boxes between the previous and current blade angle (sweeping, so fast swings don't tunnel). Query entities in the union AABB and test OBB intersection. `HitRegistry` ensures one hit per target per action. Hitbox shapes come from the moveset (arc radius, angle range, height band, thrust length).
- **Dodge:** the client applies roll motion (vanilla movement is client-authoritative, so it is smooth). The server starts the authoritative i-frame window at the `CombatInput` arrival, backdated by up to `iframeLatencyTolerance` ticks (default 2, clamped by measured ping). The roll is ≤ 9 blocks/s, below vanilla's "moved too quickly" threshold.
- **Damage interception:** `LivingIncomingDamageEvent` cancels the event while in i-frames, applies guard and parry, and replaces vanilla armor reduction with VESTIGE's formula (`addReductionModifier(Reduction.ARMOR, (c, r) -> 0)`, then set the amount from `DamageCalculator`).
- **Poise and stagger:** poise lives in `combat_state` for players and in fields for mobs. Breaking it triggers a `Stagger` action and flags the target for riposte.
- **Lock-on (client):** `LockOnController` selects targets (cone, range, LOS) and rotates the *player's actual* yaw and pitch each frame with a critically damped spring, so movement, attacks, and the server all agree. The camera offset is handled in `ComputeCameraAngles`. Multi-part targets come from `getParts()`.
- **Stance:** see §7.3. Combat input is consumed by `InteractionKeyMappingTriggered` cancellation while in combat stance.

### 7.2 Damage pipeline

```
attack lands (HitboxSweeper or projectile)
 → DamageProfile {per-type AR × MV, poise dmg, status buildup, flags(parriable, crit)}
 → custom DamageSource (vestige damage types) carrying the profile
 → LivingIncomingDamageEvent: i-frames? guard/parry? → DamageCalculator
      afterDef = raw² / (raw + 2·Def)  → × Π(1 − absorption) → floor 10% → situational
 → LivingDamageEvent.Post: poise, status buildup, Resonance gain, HitFeedback
 → LivingDeathEvent: Glim, Ember-Ghost, bestiary, boss handling
Vanilla sources (fall, lava, vanilla mobs) × hpScale (5) in LivingIncomingDamageEvent.
```

`DamageCalculator` is a pure function with JUnit tests.

### 7.3 Equipment & stance (GDD §9, ADR-005)

- **RpgInventory** is an attachment (an `ItemStackHandler`-like structure serialized by codec). Armor uses the *vanilla* armor slots, shown inside the RPG screen through `Slot` wrappers.
- **Stance swap** is server-side and atomic. On entering combat stance: `stash.main ← hotbar[selected]`, `hotbar[selected] ← rpg.main`, and the same for the off-hand. Leaving reverses it. While in stance, the selected hotbar index is locked, and `ItemTossEvent` is cancelled and restored for the swapped stacks. The swap is reversed on: opening any container, death (before drops), logout, dimension change, and server stop. A GameTest suite covers each path for no-duplication and no-loss.
- **StatSheet** is recomputed when equipment changes (not every tick). It is applied through attribute modifiers (`MAX_HEALTH`, `MOVEMENT_SPEED`) and cached values (absorptions, poise, burden, roll profile).
- **AccessoryEffect** registry: an id → behavior object with hooks (`onHit`, `onDodge`, `onTick`, `modifyStats`, `onDamaged`). Families are used for exclusivity.

### 7.4 The Pale (GDD §6.1, ADR-003)

- `PaleManager` owns the Overworld border. It computes the radius from `WorldProgress` and config and calls `lerpSizeBetween` for animated expansion.
- `DimensionBorderScaler` is a `BorderChangeListener` added to the Overworld border at `ServerStartedEvent`, after vanilla's delegate, so it runs after it. It rescales Nether (÷ `netherDivisor`) and End (÷1) for set, lerp, and center changes. It is also applied once at startup, because the vanilla load re-applies unscaled settings.
- The Pale is **disabled in GameTest servers** (tests run about 13 million blocks out) and when `border.staticMode` is set.

### 7.5 World generation (GDD §6, §14, ADR-004)

- **HushOverlayBiomeSource** wraps `{"type":"minecraft:multi_noise","preset":"minecraft:overworld"}` in overrides of the `normal`, `large_biomes`, and `amplified` world presets. **ScarLayout** derives scar centers from the seed under ring constraints (one in the Marches, one in the Wilds by default). Shapes are ellipses with noise-perturbed edges in quart coordinates. The seed is injected at `ServerAboutToStartEvent` (verified to precede `loadLevel()`).
- Hush surface uses vanilla surface rules keyed to the Hush biome. Echo Hollows are a carver plus features restricted to the Hush biome.
- **Structures:** jigsaw structures (JSON + `.nbt` templates) with a custom `vestige:ring` `StructurePlacementType`: ring-constrained, count-guaranteed, deterministic per seed. Boss arenas use fixed seeded positions computed like scars, so `WorldProgress` and the Ledger can point to them. **[verify jigsaw and placement in M6]**
- **Strongholds:** `structure_set/strongholds.json` override with `distance: 20`.
- **Existing worlds:** VESTIGE requires a new world (world presets apply at creation). This is documented, and a warning appears if a world lacks the overlay source.

### 7.6 Entities, AI & bosses

- Rendering: GeckoLib (`GeoEntity` + `GeoEntityRenderer`). Models and animations live in `assets/vestige/geo` and `animations`.
- AI: `VestigeMob` uses vanilla `Goal`s for idle, patrol, and target acquisition, and an **ActionBrain** for combat: spacing and strafing, `AttackSelector` (weighted by distance, angle, cooldown, and repeat penalty), and execution through the shared `ActionRunner`. **AttackTokens** (per target, max 2 attackers) are kept in a per-level manager.
- **Telegraphs:** an action's `telegraph` field emits a `TelegraphCue`. The client renders glints and ground decals (decals are flat quads with animated fill drawn in the level render stage **[verify render stage event in M4]**).
- **Bosses:** `BossEntity` adds a `PhaseController` (HP thresholds → transition action with invulnerability → new move pool), `ArenaSeal` (barrier blocks plus Wick-Seal visuals; the boss resets if every engaged player is dead or gone), `ServerBossEvent`-based bars replaced by a custom renderer, multiplayer HP scaling, and a summon item hook. Multi-part bosses (Archivist, Warden soul-lights, Tithe-Engine heart) use `PartEntity`.

### 7.7 Hush systems (GDD §14)

- **NoiseSystem:** player actions report noise directly (combat actions, movement sampled per tick, block events). `VanillaGameEvent` catches the rest. Noise events go into a per-level spatial bucket that Hush mobs' `Hearing` queries (radius and threshold). It is cleared every tick.
- **HushSpread:** a random-tick-style scheduler limited to loaded chunks near Hush blocks. Each tick it processes at most `hush.spreadBudget` conversions (default 16) from a queue. It is blocked by wards (Chime Lanterns, Heartlamp radius) through a spatial ward index.
- **Audio ducking (client):** `PlaySoundEvent` scales volumes in the Hush and in Warden phase 3. Verified by logic and logs only (no audio device here).

### 7.8 NPC housing (GDD §20, **essential**)

- **HousingValidator:** a flood fill from a start position over passable non-solid blocks, bounded (≤ 2,000 visited, early-out above 700 interior). It collects boundary blocks (doors, gates, trapdoors), light, seat, and surface tags (`#vestige:housing_seats`, `#vestige:housing_surfaces`, `#vestige:housing_entrances`), and Hush contamination. It is a pure algorithm over a `BlockGetter` abstraction, unit-tested with synthetic grids and GameTested with real builds.
- **HousingQueue:** at most one validation per tick. Houses are revalidated on nearby block changes (debounced) and at dawn.
- **ArrivalScheduler:** at dawn, picks one NPC whose `npcs/*.json` conditions (progression flags, discoveries, items owned, Glim carried) are met and assigns a free house. The NPC spawns at the nearest Waylight and pathfinds home.
- **Happiness:** biome preference, neighbor relations, and crowding → price multiplier (0.8–1.3) and stock tiers.
- **Dialogue and shops:** JSON with conditions on progression and quest state, rendered in custom screens.

### 7.9 Quests & Lamp Ledger
`QuestDef` JSON (objectives: kill / collect / discover / interact / flag; prerequisites; rewards). `QuestTracker` listens to game events and updates the `quests` attachment. The Ledger is a client screen reading synced attachments plus static data.

### 7.10 Progression events
`WorldProgress` changes emit internal events (`BossDefeated`, `WickLit`, `ResoundingTriggered`) consumed by the Pale, NPC arrival, spawn pools, Eye-of-Ender gating (cancel `EnderEyeItem` use pre-Resounding **[verify hook in M9]**), dormant-ore activation (block property toggled by a synced global flag, with no chunk rewriting), and the Ledger.

### 7.11 Client UI kit (GDD §9.5)
A reusable tile kit modeled on the owner's Elden Ring reference: `Tile` (24 and 28 px variants with vignette, frame, ghost silhouette, hairline rule, serif count), `Cluster` layout, a `Tooltip` frame, and the `ReadinessCross` (shared by the equipment screen and the HUD). It uses nine-slice textures generated by `tools/` so every screen is visually consistent.

### 7.12 Configuration
`ModConfigSpec` server config (border, rings, Hush, combat, death mode, NPC, bosses, quests) and client config (HUD, camera smoothing, damage numbers). Listed in GDD §27.

---

## 8. Asset Pipeline

### 8.1 Sources
- **Textures:** 16×16 (items and blocks) and entity sheets, generated or edited by `tools/*.py` (PIL) with fixed regional palettes (GDD §25). Original pixel art, no imported third-party assets.
- **Models:** block models via datagen. Weapons are 3D element item models (Blockbench Java-model format) from `tools/gen_item_models.py`, shown in hands, on the ground and on heads, with the flat sprite kept for the inventory via `neoforge:separate_transforms`. Entity models are GeckoLib `.geo.json` (Blockbench-compatible) from `tools/gen_models.py`, built from cuboid definitions so they stay editable in Blockbench later.
- **Animations:** GeckoLib `.animation.json` for entities and playerAnimator JSON for the player.

### 8.2 Timing sync: animations generated from movesets
Player attack animations are **generated from the same moveset JSON** the server uses for hit timing. Windup, active, and recovery tick counts become keyframe timestamps, and pose keyframes come from a per-class pose library. A changed number in a moveset therefore changes the hit window *and* the animation together. Boss animations follow the same rule: each boss action's tick timings are the source of truth, and the animation is built or validated against them. A GameTest asserts that each animation's length equals its action's total ticks.

### 8.3 Datagen
`runData` generates models, blockstates, lang (en_us), loot tables, recipes, and tags. Generated files are committed so builds don't depend on running datagen.

---

## 9. Testing Strategy & Definition of Done

### 9.1 Layers
| Layer | Tool | Covers |
|---|---|---|
| Unit | JUnit 5 (plain JVM) | Damage formula, burden, i-frame windows, housing flood fill on synthetic grids, scar layout determinism, ring math, quest condition evaluation |
| Game logic | NeoForge **GameTest** (`runGameTestServer`, headless) | Border scaling, stance swap (no dupes), equipment stats, combat actions hitting dummies, poise and stagger, housing on real builds, NPC arrival, boss phase transitions (scripted damage), loot tables, structure placement |
| Server smoke | `runServer` (dedicated) + auto-stop property | Mod loads on a dedicated server (catches client-class leaks), worldgen sanity logs |
| Visual | `runClient` under `xvfb-run` (Mesa llvmpipe) + `--quickPlaySingleplayer` + screenshots | HUD, screens, entity models, animations, telegraph visuals |
| Manual (owner) | Real client | Feel, audio, balance |

### 9.1a Commands (implemented in M2)
| Command | What it runs |
|---|---|
| `./gradlew build` | Compile, unit tests, architecture rules, JAR (`build/libs/vestige-<version>.jar`) |
| `./gradlew runGameTestServer` | All GameTests headless; the task fails if any required test fails |
| `./gradlew runServer -Psmoke` | Dev dedicated server; logs `VESTIGE SMOKE …` and stops itself |
| `scripts/prod-server-smoke.sh` | Installs the real NeoForge server and loads the **built JAR** (including Jar-in-Jar libraries) |
| `scripts/verify.sh` | All of the above in sequence |
| `scripts/client-driver.sh [combat\|anim\|calib]` | Real client, headless, in a fresh world: scripted combat scenario with `DRIVER CHECK` assertions and screenshots (exits non-zero on failures) |
| `xvfb-run ./gradlew runClient -PquickPlay=<world>` | Headless client straight into a world (screenshots via ImageMagick `import`) |

See [DEVELOPMENT.md](DEVELOPMENT.md) for details.

### 9.2 Definition of done (per feature)
1. Code compiles (`./gradlew build`) with no new warnings in VESTIGE code.
2. Unit tests and GameTests for the feature pass. Earlier tests still pass.
3. A dedicated-server smoke run passes (`scripts/verify.sh`, including the production-server run with the built JAR).
4. For visual features: a screenshot from the headless client is reviewed. Features that can't be verified here (audio, real-time feel) are listed as **not verified** in the milestone report.
5. Docs are updated (GDD if the design changed, `docs/systems/<system>.md` for the implementation).

### 9.3 Dev test driver
No input automation tool is installed, so a **dev-only** client driver (enabled by a system property in the `client` run config, compiled out of release behavior) runs scripted sequences: spawn dummy, enter stance, attack, roll, open screen, then capture screenshots through Minecraft's own `Screenshot.grab`. This makes visual checks deterministic and repeatable. **[verify Screenshot API in M3]**

---

## 10. Performance Budgets (server, per tick, per level)

| System | Budget |
|---|---|
| Combat sweeps | ≤ 0.3 ms with 20 combatants (OBB tests only against entities in a pre-filtered AABB) |
| Enemy AI | Action selection every 4 ticks unless in an action. Pathfinding via vanilla navigation |
| Hush spread | ≤ 16 conversions per tick (configurable). Queue capped |
| Housing validation | ≤ 1 flood fill per tick, ≤ 2,000 blocks |
| Noise system | Spatial buckets, cleared every tick. O(listeners × nearby events) |
| Attachment sync | Changed-only, ≤ every 2 ticks for vitals |

Spark-style profiling is done in M12. GameTests assert worst-case timings for the flood fill and spread batch.

---

## 11. Architecture Decision Records

| ADR | Decision | Why | Alternatives rejected |
|---|---|---|---|
| 001 | NeoForge 21.1.266 + MDG 2.0.148, Mojang mappings | Current, verified, compiles | Parchment (host blocked; cosmetic benefit only) |
| 002 | GeckoLib (entities) + playerAnimator (player), bundled via Jar-in-Jar | Mature, MIT-licensed, verified on 1.21.1; a single-JAR release | Hand-written keyframe system (large cost, worse tooling); requiring separate installs |
| 003 | Explicit Nether and End border scaling via a post-delegate listener | Vanilla does not scale size (verified); a listener keeps `/worldborder` and lerps consistent | Mixin into the delegate (more fragile); per-dimension timers (desync risk) |
| 004 | Hush via a wrapping biome source, not TerraBlender | Guaranteed ring placement and deterministic scar positions; no extra dependency; verified surface rules follow | TerraBlender regions (weighted random, so no guarantees); fake biome via block replacement only (no biome fog, music, or spawns) |
| 005 | Combat stance physically swaps item stacks | Real held items work with vanilla rendering, attributes, and other mods; no dupe risk if atomic | Render-only substitution (attributes and other mods break); mirrored copies (dupe risk) |
| 006 | One shared Action runtime for players, mobs, bosses | Consistent timing, telegraphs, poise; less code; easier balancing | Separate boss scripting engine |
| 007 | Ticks as the single time unit; animations generated from movesets | Hitbox and animation can't drift apart | Hand-timed animations |
| 008 | Content as datapack JSON, behaviors by registered id | Fast balancing, reloadable, mod-pack friendly | Hard-coded stats |
| 009 | Server-authoritative outcomes, client-predicted feel with bounded latency tolerance | Responsive and multiplayer-safe | Fully client-authoritative hits (cheatable) |
| 010 | New-world requirement | World presets apply at creation | Retro-generating existing worlds (unsafe) |
| 011 (rev. 2, M4) | Every enemy, humanoids included, is a GeckoLib model (`AnimatedMob`): keyframed idle/walk/death, with combat poses sampled from the shared `PoseTimeline` and blended over the bones each frame through a per-body binding table (`TimelineGeoModel`: humanoid, quadruped). *Rev. 1 (M3) posed a vanilla `HumanoidModel`; replaced at the owner's request for distinctive, high-quality models and animations* | One pose definition still drives players (playerAnimator) and enemies, so hitboxes and animation stay in sync; enemies get distinctive silhouettes (hoods, claws, chains), elbows/knees and root motion that the vanilla biped lacks | Fully hand-keyframed attack animations per enemy (would drift from hitbox timing); vanilla biped (too stiff and generic) |
| 012 | Datapack content goes through `core.SyncedDataTable`; server→client sends go through `core.Net` | One reload/sync path for movesets, weapons and enemies; a connection without VESTIGE channels (another mod's fake player) never crashes a send | Per-table hand-written packets; unguarded `PacketDistributor` calls |
| 013 | Player combat prediction runs the same `PlayerCombatMachine` as the server | Instant animation on press with near-zero divergence; inputs carry a sequence number for reconciliation | Server-only timing (laggy feel); separate client logic (drift) |

---

## 12. Build Environment

The cloud build environment **"VESTIGE"** (personal) allows these hosts in addition to package registries:
`maven.neoforged.net`, `dl.cloudsmith.io` (GeckoLib), `maven.kosmx.dev` (playerAnimator), `maven.minecraftforge.net`, `maven.modrinth.com`, `api.modrinth.com`, `maven.parchmentmc.org` (redirects to a blocked host; unused), the Mojang hosts (`piston-meta`, `piston-data`, `launchermeta`, `libraries.minecraft.net`, `resources.download.minecraft.net`), `api.foojay.io`, and `github.com` / `objects.githubusercontent.com`.

Machine: 4 CPUs, 15 GB RAM, Xvfb + Mesa 25.2 (llvmpipe), ImageMagick, Python 3 + Pillow. First-time decompile and recompile takes about 2–3 minutes. Incremental builds take seconds.
