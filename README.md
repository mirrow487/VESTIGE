# VESTIGE

A Minecraft RPG overhaul mod for **Minecraft Java 1.21.1 · NeoForge 21.1.266 · Java 21**.

Reclaim a dying world one boss at a time: Terraria-style progression, Elden Ring-style combat, inside Minecraft's sandbox.

## Status
- ✅ Milestone 0: Game Design Document
- ✅ Milestone 1: Technical architecture (toolchain and API assumptions verified)
- ✅ Milestone 2: Project setup (module system, build, tests, single-JAR release)
- ⏭ Milestone 3: Core RPG systems

## Build
```bash
./gradlew build          # → build/libs/vestige-<version>.jar
scripts/verify.sh        # build + unit tests + GameTests + dev and production server smoke runs
```

## Documents
- [Game Design Document](docs/GDD.md)
- [Technical Architecture](docs/ARCHITECTURE.md)
- [Development Guide](docs/DEVELOPMENT.md)
