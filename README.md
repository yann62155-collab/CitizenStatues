# Citizen Statues

Mod Minecraft Java **1.21.1**, **NeoForge 21.1.215**, **Java 21**.

Citizen Statues adds silent, interactive guide statues for the CITIZEN server.
Players will be able to open a destination guide, navigate destinations through
a graphical interface, and optionally use client-side French speech recognition.
The statue never speaks and never plays voice audio.

## Development status

This repository is the implementation workspace. The feature checklist is being
delivered incrementally; a feature is only marked complete after implementation
and verification.

### Planned modules
- Custom statue entity, model, texture, placement and persistence
- Per-statue destination registry and graphical administration
- Player destination browser with search, pagination and confirmation
- Client-only microphone permission and offline speech recognition
- Intent matching, configurable retry flow and GUI fallback
- Server-side permission checks, teleport validation and anti-spam
- Automated build and compatibility tests

## Build

Requirements: JDK 21 and Gradle 8.10.2 or compatible.

```bash
gradle build
```

The distributable JAR is generated in `build/libs/`.

## Compatibility

Target: Minecraft 1.21.1 / NeoForge 21.1.215.
