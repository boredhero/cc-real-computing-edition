<!-- SPDX-FileCopyrightText: 2026 boredhero
SPDX-License-Identifier: MPL-2.0 -->

# CC: Real Computing Edition

A fork of [CC: Tweaked](https://github.com/cc-tweaked/CC-Tweaked) for ambitious Minecraft automation: independent background computation, configurable compute resources, and much broader turtle interaction with the world.

The purpose is to make programmable computers and turtles substantially more capable, so complex automation is limited less by the mod's abstractions.

## Status

**Development baseline.** This initial fork builds the matching upstream release with fork branding. Independent Lua workers, native acceleration, and expanded interaction APIs are planned, not implemented yet. Existing CC programs retain upstream behavior.

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

The `forge` project targets **NeoForge** on this Minecraft version. The distributable is `projects/forge/build/libs/cc-real-computing-edition-1.21.1-1.120.2-rce.0.1.0-SNAPSHOT.jar`; do not install the sources or API jars.

This is a replacement for CC: Tweaked, retaining the `computercraft` mod ID, existing block IDs and public API packages for compatibility. Install only one of the two mods. Test with a copy of your world and matching client/server builds before deployment; compatibility with the full pack is not yet certified.

## Baseline validation

On 2026-10-21, `:forge:assemble :core:test :forge:test` completed successfully on Linux with Temurin JDK 25. Core: 1,606 passed and 13 skipped; NeoForge: 10 passed. The loader tests use ModDevGradle's FML-aware JUnit integration for NeoForge 21.1.250. Full-pack gameplay and dedicated-server deployment have not been tested.

## Direction

1. Independent compute workers with isolated Lua state, message passing, cancellation, and results delivered as events. CPU-heavy jobs should continue independently of game ticks.
2. Server-configurable CPU, memory and queue budgets with useful profiling. Expensive computation should remain responsive without manual yield calls throughout algorithms.
3. Rich player-style turtle interactions: explicit target faces, held-item use, sneaking, entity interactions, and container operations. Preserve normal server interaction hooks and item accounting.
4. Benchmark Java/Cobalt against native options such as LuaJIT or targeted C kernels before selecting a native backend. Native code must justify its compatibility, packaging and maintenance costs with measured improvements.

See [the architecture and milestone plan](doc/real-computing.md). The first representative workload is a farm controller performing 3D route planning while handling fleet messages and rendering dashboards.

## Upstream and licensing

This is an independent fork, not an official CC: Tweaked release. Original authorship and licenses are preserved; see [LICENSES](LICENSES), file-level SPDX notices, and [the upstream README](README.upstream.md). Existing API documentation is at [tweaked.cc](https://tweaked.cc/).
