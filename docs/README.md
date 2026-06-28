# Porting Notes

This directory contains working notes for the Minecraft 1.7.10 / GTNH port of Neo ECO AE Extension.

Use these files as the quick project map when migrating code from the 1.21.1 upstream project.

## Files

- [environment.md](environment.md): GTNH development environment, dependency policy, and useful Gradle commands.
- [migration-map.md](migration-map.md): High-level mapping from modern NeoForge concepts to Forge 1.7.10 / GTNH equivalents.
- [items.md](items.md): Item migration status and resource path notes.
- [rendering-model-pipeline.md](rendering-model-pipeline.md): Lightweight modern JSON model rendering pipeline for Forge 1.7.10 blocks and items.
- [tooltips.md](tooltips.md): Shared tooltip key convention, color rules, and block item registration notes.

## Upstream Reference

Upstream source project:

```text
E:\Minecraft Project\NeoECOAEExtension-1.21.1
```

This porting workspace:

```text
E:\Minecraft Project\NeoEcoExtension_1.7.10_Port
```

The upstream mod id is preserved:

```text
neoecoae
```

Maintainers:

```text
upstream: DancingSnow0517
GTNH port: Yang120231
```
