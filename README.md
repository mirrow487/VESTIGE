# VESTIGE

A Minecraft RPG overhaul mod for **Minecraft Java 1.21.1 · NeoForge 21.1.266 · Java 21**.

Reclaim a dying world one boss at a time: Terraria-style progression, Elden Ring-style combat, inside Minecraft's sandbox.

## Status
- ✅ Milestone 0: Game Design Document
- ✅ Milestone 1: Technical architecture (toolchain and API assumptions verified)
- ✅ Milestone 2: Project setup (module system, build, tests, single-JAR release)
- ✅ Milestone 3: Combat foundation (attacks, combos, charge, dodge, stamina, lock-on, telegraphed enemies)
- ⏭ Milestone 4: Combat depth + core RPG systems

## Controls (while holding a weapon)
| Action | Default key |
|---|---|
| Light attack (combo) | Left mouse |
| Heavy attack (hold to charge) | R |
| Dodge roll / backstep (no direction) | Left Alt |
| Lock on / switch target | Middle mouse / scroll |

## Build
```bash
./gradlew build          # → build/libs/vestige-<version>.jar
scripts/verify.sh        # build + unit tests + GameTests + dev and production server smoke runs
```

## Documents
- [Game Design Document](docs/GDD.md)
- [Technical Architecture](docs/ARCHITECTURE.md)
- [Development Guide](docs/DEVELOPMENT.md)
- [Combat System](docs/systems/combat.md)
