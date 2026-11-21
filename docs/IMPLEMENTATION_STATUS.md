# Implementation status

Target platform: Minecraft 1.21.1 / NeoForge 21.1.215 / Java 21.

## Implemented in repository
- Gradle/ModDev project structure, mod metadata and GitHub Actions build workflow.
- Main mod entry point and entity registration.
- Silent, immobile guide statue entity with profile ID persisted in entity NBT.
- Temporary visible armor-stand renderer (not the final custom CITIZEN model).
- World-scoped registry service loaded at server start and saved at shutdown.
- Permission-level-2 server commands: `/citizenstatues summon <name>`, `/citizenstatues list`, `/citizenstatues move <statueId>` (same dimension only), `/citizenstatues rename <statueId> <newName>`, `/citizenstatues enable <statueId>`, `/citizenstatues disable <statueId>`, `/citizenstatues remove <statueId>`, `/citizenstatues destination add <statueId> <destinationId> <name>` and `/citizenstatues destination remove <statueId> <destinationId>`, `/citizenstatues specialization <statueId> <type>`, `/citizenstatues synonym add <statueId> <destinationId> <phrase>` and `/citizenstatues synonym remove <statueId> <phrase>`.
- Destination model with dimension-key and coordinate validation.
- Independent statue profiles, destinations, specializations and synonyms.
- In-memory registry with UUID identifiers.
- Versioned JSON persistence with atomic replacement and corrupt-file preservation.
- French text normalization, conservative destination/synonym matching, per-player intent orchestration, and text-only retry prompts.
- Per-player retry state with configurable attempt count and GUI-fallback outcome.
- Searchable and paginated destination catalog.
- Teleport policy validation, per-player cooldown primitive, and server-side teleport service with dimension/world-border/safe-landing checks (not yet wired to player GUI requests).
- Privacy-first voice settings defaults (disabled, offline-only, French, bounded capture duration), plus an ephemeral bounded capture-session policy (policy only; no microphone backend yet).
- Administration permission adapter contract, centralized operator permission check, and server-side admin service for rename/enable/remove and destination CRUD.
- Voice detection: adaptive noise-floor voice activity detection (listening stops ~0.8 s after the player finishes, 6 s timeout with no speech, 10 s hard cap), automatic gain control for quiet microphones, live partial transcript on the action bar, and two parallel Vosk recognisers (free dictation with 3 alternatives + a grammar restricted to the statue's destinations, synonyms, ordinals and commands).
- Voice understanding: fuzzy (edit distance), simplified French phonetic and re-joined-word matching over every hypothesis, with ambiguity detection, ordinals ("le deuxième", "numéro 3", "le dernier"), "annuler" / "menu" commands, and retry messages quoting what was heard with the closest destinations. Network protocol version 2.
- JUnit tests covering destination validation, matching, retries, pagination, persistence, teleport policy and rate limiting.

## Still outstanding
- Final custom 3D CITIZEN character model and texture (brown hair, red scarf, brown outfit, gold chest cross, sword, plinth and target height).
- Statue placement item and full in-game placement/management workflow.
- Clickable in-chat destination list on statue interaction, with server-validated `/citizenstatues travel <statueId> <destinationId>` execution; a dedicated graphical destination screen and interaction packets are still outstanding.
- Full graphical administration screens and per-statue configuration editor; current admin operations are command-based.
- Server-side teleport execution is wired to clickable destination requests, with dimension, world-border and safe-landing checks; automated integration verification remains outstanding.
- Microphone permission UX, bounded capture implementation and offline French speech-to-text engine.
- Speech-to-intent integration, per-player retry messages and automatic GUI fallback wiring.
- Server-side permission wiring for all admin actions and complete packet validation.
- Automated client/dedicated-server integration tests.
- Successful GitHub Actions run and verified distributable JAR.

A planned item is not complete until implementation exists and relevant tests/builds pass.
