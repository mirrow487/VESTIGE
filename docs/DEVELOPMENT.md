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
| Unit tests | `./gradlew test` (part of `build`) | Pure logic (no game classes), plus `ArchitectureRulesTest` |
| GameTests | `./gradlew runGameTestServer` | Game logic on a real server. Tests live in `dev.vestige.gametest.<module>` |
| Dev server smoke | `./gradlew runServer -Psmoke` | Mod loads on a dedicated server (catches client-class leaks); logs `VESTIGE SMOKE …` and stops |
| Production server smoke | `scripts/prod-server-smoke.sh` | Installs the real NeoForge server into `prod-server/`, loads the **built JAR**, and checks startup |
| Headless client | see below | HUD, UI, models, animations by screenshot |

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
- Time is measured in **ticks** (20 per second).
- Game-class-free math goes in its own class so it can be unit-tested (example: `pale.BorderScaling`).
- Never reference `net.minecraft.client` or `dev.vestige.client` from common code. The build fails if you do.
- Dev-only behavior must be behind a `vestige.dev.*` system property (see `dev.DevModule`).
- GameTest structure templates: `tools/gen_empty_structure.py` generates `data/vestige/structure/empty.nbt`.

## License
The project license is currently **All Rights Reserved** (set in `gradle.properties` → `mod_license`). The project owner should choose a final license before public release.
