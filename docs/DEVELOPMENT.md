# VESTIGE: Development Guide

How to build, run, test, and extend the mod. Architecture background is in [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements
- JDK 21
- Network access to `maven.neoforged.net`, `dl.cloudsmith.io` (GeckoLib), `maven.kosmx.dev` (playerAnimator), Maven Central, the Gradle plugin portal, and the Mojang download hosts.
- Optional, for headless client checks: `xvfb-run`, Mesa (software OpenGL), ImageMagick.

The first build decompiles and recompiles Minecraft (about 2–3 minutes). Later builds take seconds.

## Everyday commands

```bash
./gradlew build                     # compile + unit tests + architecture rules + JAR
./gradlew runClient                 # dev client (runs/client)
./gradlew runServer                 # dev dedicated server (runs/server; put eula=true in runs/server/eula.txt)
./gradlew runGameTestServer         # all GameTests, headless; fails the task on any failed test
./gradlew runData                   # regenerate src/generated/resources (commit the result)
scripts/verify.sh                   # everything below, in order
```

The release JAR is `build/libs/vestige-<version>.jar`. It bundles GeckoLib and playerAnimator (Jar-in-Jar), so players install only this file.

## Verification layers

| Layer | Command | Checks |
|---|---|---|
| Unit tests | `./gradlew test` (part of `build`) | Logic, data consistency and `ArchitectureRulesTest`; runs with Minecraft on the classpath (ModDevGradle unit-test mode), no world |
| GameTests | `./gradlew runGameTestServer` | Game logic on a real server. Tests live in `dev.vestige.gametest.<module>` |
| Dev server smoke | `./gradlew runServer -Psmoke` | Mod loads on a dedicated server (catches client-class leaks); logs `VESTIGE SMOKE …` and stops |
| Production server smoke | `scripts/prod-server-smoke.sh` | Installs the real NeoForge server into `prod-server/`, loads the **built JAR**, and checks startup |
| Headless client | see below | HUD, UI, models, animations by screenshot |

### Scripted client test (dev driver)
```bash
scripts/client-driver.sh combat   # full combat scenario: HUD, attacks, rolls, weapons, lock-on, enemies, config UI
scripts/client-driver.sh anim     # one attack, for quick animation iteration
scripts/client-driver.sh calib    # single-axis test poses, to check rotation conventions
scripts/client-driver.sh depth    # guards, live buckler parry, riposte, backstab, weapon arts, Resonance HUD
scripts/client-driver.sh items    # 3D weapon models: in hand (front/side/first person), arts, parry frames, ground, inventory
scripts/client-driver.sh arsenal  # stage 2 classes: side/first-person poses, arts, drawn bow + live shot, rings, wake, cloud, shields
scripts/client-driver.sh ram      # Tolling Ram poses and death, then a live charge the player rolls out of
scripts/client-driver.sh rpg      # Waylights (light, menu, travel, rest), Wick Flask, Glim, death -> Ember-Ghost -> reclaim
scripts/client-driver.sh models   # GeckoLib enemy models: poses front/side, death
scripts/client-driver.sh lock     # lock-on reticle against a stationary target
```
The driver (`client.devdriver.DevDriver`) only exists when `-Dvestige.dev.driver=<scenario>` is set. It presses real combat inputs, runs server commands, logs `DRIVER CHECK PASS/FAIL` lines and saves screenshots to `runs/client/screenshots/`. Each run uses a fresh copy of `runs/server/world`.

### Generated content
- `python3 -I tools/gen_combat_data.py`: movesets, weapons, enemies, damage types, tags, spawns, loot, recipes (edit the numbers here, not the JSON).
- `python3 -I tools/gen_textures.py`: item sprites, particle and HUD textures.
- `python3 -I tools/gen_models.py`: GeckoLib enemy geometry, animations and painted skins.
- `python3 -I tools/gen_item_models.py`: 3D weapon item models and their part textures.
- `python3 -I tools/gen_empty_structure.py <out.nbt> [size]`: GameTest templates.

### Headless client
```bash
# Create a world once (e.g. copy runs/server/world after a smoke run) into runs/client/saves/<name>
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -n 99 -s "-screen 0 1280x720x24" ./gradlew runClient -PquickPlay=<name>
# screenshot (xvfb-run keeps its auth file under /tmp/xvfb-run.*):
DISPLAY=:99 XAUTHORITY=$(ls -t /tmp/xvfb-run.*/Xauthority | head -1) import -window root shot.png
```
Put `onboardAccessibility:false` in `runs/client/options.txt` to skip the first-launch screen. Sound cannot be verified in headless environments (no audio device).

## Adding a gameplay system (module)

1. Create the package `dev.vestige.<module>` and a `<Name>Module implements VestigeModule` whose `id()` returns `"<module>"`.
2. Create the module's own `DeferredRegister`s inside it and register them in `init(modBus, gameBus)`.
3. Add the module to `core.VestigeModules.COMMON` (order matters only for registration side effects).
4. Declare it in `ArchitectureRulesTest.ALLOWED_DEPENDENCIES` with the modules it may use. Keep this list minimal.
5. Client-side parts go in `dev.vestige.client.<module>`, wired from `VestigeClient`.
6. Config options go in `config.ServerConfig` (or `ClientConfig`) with a `.translation(...)` key. Datagen picks up the English text.
7. Add unit tests for pure logic and GameTests in `dev.vestige.gametest.<module>`.
8. Update the GDD if the design changed, and ARCHITECTURE if the implementation approach changed.

## Conventions
- Combat timing, animation poses and their conventions: [systems/combat.md](systems/combat.md).
- Time is measured in **ticks** (20 per second).
- Game-class-free math goes in its own class so it can be unit-tested (example: `pale.BorderScaling`).
- Never reference `net.minecraft.client` or `dev.vestige.client` from common code. The build fails if you do.
- Dev-only behavior must be behind a `vestige.dev.*` system property (see `dev.DevModule`).
- GameTest structure templates: `tools/gen_empty_structure.py` generates `data/vestige/structure/empty.nbt`.

## License
The project license is currently **All Rights Reserved** (set in `gradle.properties` → `mod_license`). The project owner should choose a final license before public release.
