# Project: MineKings
NeoForge 1.21.1 mod on Java 21, Parchment 2024.11.17, GPL v3. Overhauls Minecraft villages into political nations with biome-driven cultures, titled leaders, and vassal hierarchies.

## Commands
- `./gradlew build` — compile + jar to `build/libs/`
- `./gradlew deploy` — build then copy jar into the Prism 1.21.1 mods folder (custom task)
- `./gradlew runClient` — launch a dev client
- `./gradlew runServer` — launch a dev dedicated server
- `./gradlew compileJava` — compile only (fastest feedback loop)

## Architecture
Three layers; each new version builds on the last without modifying shipped layers.

- `src/main/java/.../village/` — `VillageManager` (SavedData `minekings_villages`), `Village` (id/name/box/attributes), `VillageAttributes` (workstation-scan-driven). Villages are apolitical spatial/economic units.
- `src/main/java/.../politics/` — `PoliticsManager` (SavedData `minekings_politics`), `Polity`, `Character` (shadows `java.lang.Character` — fully qualify outside this package), `Allegiance` (record), `Culture` + `CultureManager` (datapack-loaded). Day-tick reconciliation auto-founds polities, runs succession, accrues treasury, reconciles villager embodiment.
- `src/main/java/.../client/` + `.../politics/*Payload.java` / `*Handler.java` — `HubScreen` (M key), Bannerlord-style tabs (Character/Clan/Kingdom/Encyclopedia), request/response packet pairs.
- `src/main/java/.../economy/` — `ConversionTable` (datapack), `StorehouseDrain`, resource bookkeeping inside `Village`.
- `src/main/java/.../registry/` — `DeferredRegister`s for blocks/items/creative tabs. Wire new registries in `MineKings.java` constructor.
- `src/main/resources/data/minekings/`
  - `cultures/*.json` — tier labels, leader titles, name pools, biome affinity
  - `building_types/*.json` — workstation block → attribute mapping
  - `economy/conversions/*.json` — resource conversions
  - `worldgen/structure/village_*.json` + `structure_set/villages.json` — 5 biome variants using `"type": "minecraft:jigsaw"` with vanilla's template pools as `start_pool`
  - `tags/worldgen/biome/has_structure/village_*.json` — per-biome MineKings village placement
  - `tags/worldgen/structure/village.json` — tag grouping all 5 variants (used by `/minekings locate`)
- `src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_*.json` — overrides with `replace: true, values: []` that disable vanilla villages globally.