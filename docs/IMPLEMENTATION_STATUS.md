# Implementation status

Target platform: Minecraft 1.21.1 / NeoForge 21.1.215 / Java 21.

## Implemented in the current foundation
- Gradle/ModDev project structure and mod metadata.
- Main mod entry point.
- Destination value object with dimension-key and coordinate validation.
- Independent statue profiles with per-statue destinations and synonyms.
- In-memory registry with UUID identifiers.
- Versioned JSON persistence with atomic replacement and corrupt-file preservation.
- French text normalization and conservative destination/synonym matching.
- Per-player retry state with a three-attempt GUI-fallback outcome.
- Searchable and paginated destination catalog.
- Server teleport policy validation contract.
- Per-player cooldown primitive.
- Privacy-first voice settings defaults (disabled, offline-only, French, bounded capture duration).
- Administration permission adapter contract.
- GitHub Actions build workflow.

## Not yet implemented / not verified
- Actual NeoForge entity registration, statue placement item, custom 3D model and texture.
- Entity interaction and server/client packet registration.
- Actual server lifecycle integration for loading/saving the registry.
- Player and admin screens.
- Microphone capture and offline speech-to-text engine integration.
- End-to-end server teleport execution and safe landing search.
- Permissions wired to Minecraft permission levels.
- Automated unit/integration tests, dedicated-server test, and client test.
- Successful GitHub Actions build and distributable JAR verification.

A planned item is not considered complete until its implementation exists and the relevant test passes.
