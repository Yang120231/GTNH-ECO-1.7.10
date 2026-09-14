# GTNH Environment

This project targets Minecraft 1.7.10 on GT New Horizons.

Use JDK 25 for local development and CI, matching the current GTNH development environment.
The Gradle daemon criteria in `gradle/gradle-daemon-jvm.properties` select JDK 25
and allow Gradle to download it automatically. The wrapper can be launched with
JDK 17 or newer; on Windows it uses `JAVA_HOME` before the `java` on `PATH`.

## Build System

- Gradle 9.3.1 wrapper is committed in the repository; a global Gradle installation is not required.
- GTNH settings convention plugin: 2.0.29.
- GTNHGradle provides the Forge 1.7.10 workspace, deobfuscation, run tasks, and reobfuscation.
- The project uses the GTNH settings convention plugin and explicit mod dependency versions in `gradle.properties`.
- Java syntax is compiled through Jabel for Java 8 compatibility (`enableModernJavaSyntax = jabel`).

Common commands:

```powershell
.\gradlew.bat build
.\gradlew.bat clean build
.\gradlew.bat runClient
.\gradlew.bat setupDecompWorkspace
.\gradlew.bat spotlessApply
```

Build artifacts are written to `build/libs/`. Use the main jar (without the
`-dev` or `-sources` suffix) when installing the mod. `build` also runs tests,
Spotless, and Checkstyle; do not skip these checks to obtain a successful build.

In IntelliJ IDEA, import `settings.gradle.kts`, use the Gradle wrapper, and select
JDK 25 for Gradle. Reload the Gradle project after changing the build configuration.
The first build needs network access to download the toolchains, Minecraft assets,
and Maven dependencies.

On Windows, a previous Gradle daemon can retain a handle to
`build/rfg/recompiled_minecraft-1.7.10.jar`, causing `clean` to fail with
"Unable to delete directory". After any active builds finish, stop the daemon
and rebuild with a single-use daemon:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean build --no-daemon
```

## Dependency Rule

Use GTNH forked artifacts. Do not replace them with upstream/original mod jars.

Important examples:

```text
com.github.GTNewHorizons:Applied-Energistics-2-Unofficial
com.github.GTNewHorizons:NotEnoughItems
com.github.GTNewHorizons:CodeChickenCore
```

Dependencies are pinned to the stable local ECO-1.7.10 development baseline,
not the moving `daily` manifest. Update the version properties deliberately and
verify compatibility with Minecraft 1.7.10 / Forge 10.13.4.1614.

## Current Runtime Helpers

The explicitly declared development runtime dependencies are AE2, StructureLib,
GTNHLib, ModularUI2, GregTech, and Waila, in addition to dependencies supplied by
the GTNH build convention and transitive dependencies.

These are development dependencies for testing behavior close to a GTNH client, not bundled libraries.
