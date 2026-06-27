# GTNH Environment

This project targets Minecraft 1.7.10 on GT New Horizons.

Use JDK 25 for local development and CI, matching the current GTNH development environment.

## Build System

- Gradle wrapper is committed in the repository.
- GTNHGradle provides the Forge 1.7.10 workspace, deobfuscation, run tasks, and reobfuscation.
- The project uses the GTNH catalog settings plugin to read GTNH manifest versions.

Common commands:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat setupDecompWorkspace
.\gradlew.bat spotlessApply
```

## Dependency Rule

Use GTNH forked artifacts. Do not replace them with upstream/original mod jars.

Important examples:

```text
com.github.GTNewHorizons:Applied-Energistics-2-Unofficial
com.github.GTNewHorizons:NotEnoughItems
com.github.GTNewHorizons:CodeChickenCore
```

GTNH 2.9 fixed release manifests may not exist yet. Until a fixed manifest is available, this workspace uses the `daily` manifest through the catalog plugin.

## Current Runtime Helpers

The development runtime includes AE2/NEI ecosystem dependencies and GTNH client helpers such as Angelica, Hodgepodge, CoreTweaks, GTNHLib, ModularUI, and BlockRenderer6343.

These are development dependencies for testing behavior close to a GTNH client, not bundled libraries.
