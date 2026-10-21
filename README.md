<!-- SPDX-FileCopyrightText: 2026 boredhero
SPDX-License-Identifier: MPL-2.0 -->

# CC: Real Computing Edition

A fork of [CC: Tweaked](https://github.com/cc-tweaked/CC-Tweaked) for ambitious Minecraft automation: independent background computation, configurable compute resources, and much broader turtle interaction with the world.

The purpose is to make programmable computers and turtles substantially more capable, so complex automation is limited less by the mod's abstractions.

## Status

**Experimental compute, turtle and identity release.** Independent background Lua jobs are implemented through `compute` (also `require("cc.compute")`). Jobs use separate Lua states on a dedicated thread pool, support cancellation, and return copied results without blocking the owner. Turtles support block/entity vision, UUID-targeted entity interaction, diagonal block actions with face and sneak options, menu-based container transfers, and tick-driven held-item use. Computer blocks, turtles and pockets have persistent UUIDs, UUID-addressed rednet and independent UUID storage. Computer and turtle terminal UIs are denser. Native acceleration and custom mod GUI adapters remain planned. Numeric rednet remains compatible; internal computer files migrate to UUID directories.

Read [the compute API and examples](doc/compute.md), or run `compute_demo` in CraftOS. See [turtle vision and targeted interaction examples](doc/turtle-vision.md) for `observe`, `digAt`, `placeAt` and `useAt`.

## Target

- FTB Evolution 1.45.1 deployment
- Minecraft **1.21.1**
- NeoForge **21.1.250**
- CC: Tweaked **1.120.2**, upstream tag `v1.21.1-1.120.2`
- Upstream commit `c7ccc20c756bdd437b09908840485a0e15dc7d7b`
- Java **21** runtime; Java **25** Gradle daemon (as required by this upstream build)

These Minecraft, loader, and CC versions were verified against the target server on 2026-10-20. The development branch is `main`.

## Build

```sh
./gradlew :forge:assemble :core:test :forge:test
```

The `forge` project targets **NeoForge** on this Minecraft version. The distributable is `projects/forge/build/libs/cc-real-computing-edition-1.21.1-1.120.2-rce.0.6.0-SNAPSHOT.jar`; do not install the sources or API jars.

This is a replacement for CC: Tweaked, retaining the `computercraft` mod ID, existing block IDs and public API packages for compatibility. Install only one of the two mods. Test with a copy of your world and matching client/server builds before deployment; compatibility with the full pack is not yet certified.

## Validation

On 2026-10-21, the entity/interaction milestone passed 1,632 core unit tests (13 skipped), 18 NeoForge unit tests, and 124 of 125 required NeoForge game tests in the final run. The remaining failure was the existing intermittent double-chest test; all 125 passed in the preceding run. One optional treasure-loot test also failed. Eight new game tests cover visible players/mobs/items, entity occlusion and change events, feeding/shearing/pickup, full inventories, attack/use protection, explicit faces/sneaking, locked/protected chest transfers, real-tick bow release, cancellation and consumable completion. Java style, Lua lint and changelog checks passed. Builds use Temurin JDK 25 and target Minecraft 1.21.1 / NeoForge 21.1.250. Visual UI inspection, full-pack gameplay and deployment to the Evolution server remain untested.

## Direction

1. **Implemented:** independent compute jobs with isolated Lua state, copied inputs/results, cancellation, completion events and polling. CPU work runs independently of game ticks.
2. Server-configurable CPU, memory and queue budgets with useful profiling. Expensive computation should remain responsive without manual yield calls throughout algorithms.
3. **Implemented:** block/entity vision, diagonal block actions, explicit faces, sneaking, entity interactions, container menu transfers and held-item use with protection hooks.
4. **Implemented:** persistent UUIDs for computers, turtles and pockets, UUID rednet addressing/discovery, and independent clone storage. Existing numeric rednet remains compatible.
5. Benchmark Java/Cobalt against native options such as LuaJIT or targeted C kernels before selecting a native backend. Native code must justify its compatibility, packaging and maintenance costs with measured improvements.

See [UUIDs and terminal UI settings](doc/identity.md), and [the architecture and milestone plan](doc/real-computing.md). The first representative workload is a farm controller performing 3D route planning while handling fleet messages and rendering dashboards.

## Upstream and licensing

This is an independent fork, not an official CC: Tweaked release. Original authorship and licenses are preserved; see [LICENSES](LICENSES), file-level SPDX notices, and [the upstream README](README.upstream.md). Existing API documentation is at [tweaked.cc](https://tweaked.cc/).
