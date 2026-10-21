<!-- SPDX-FileCopyrightText: 2026 boredhero
SPDX-License-Identifier: MPL-2.0 -->

# CC: Real Computing Edition

A fork of [CC: Tweaked](https://github.com/cc-tweaked/CC-Tweaked) for ambitious Minecraft automation: independent background computation, configurable compute resources, and much broader turtle interaction with the world.

The purpose is to make programmable computers and turtles substantially more capable, so complex automation is limited less by the mod's abstractions.

## Status

**Experimental compute-worker release.** Independent background Lua jobs are implemented through `compute` (also `require("cc.compute")`). Jobs use separate Lua states on a dedicated thread pool, support cancellation, and return copied results without blocking the owner. Native acceleration and expanded turtle interaction/vision APIs remain planned. Existing programs keep upstream behavior unless they use the new API.

Read [the compute API and examples](doc/compute.md), or run `compute_demo` in CraftOS.

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

The `forge` project targets **NeoForge** on this Minecraft version. The distributable is `projects/forge/build/libs/cc-real-computing-edition-1.21.1-1.120.2-rce.0.2.0-SNAPSHOT.jar`; do not install the sources or API jars.

This is a replacement for CC: Tweaked, retaining the `computercraft` mod ID, existing block IDs and public API packages for compatibility. Install only one of the two mods. Test with a copy of your world and matching client/server builds before deployment; compatibility with the full pack is not yet certified.

## Validation

On 2026-10-21, the compute-worker build, Java style checks and changelog check completed successfully on Linux with Temurin JDK 25. Full unit suites: core 1,627 passed and 13 skipped; NeoForge 10 passed. Tests include concurrency, time-slicing beyond seven seconds, cancellation, boot isolation, bounded transfers, owner responsiveness and the bundled demo. The loader tests use ModDevGradle's FML-aware JUnit integration for NeoForge 21.1.250. Full-pack gameplay and dedicated-server deployment have not been tested.

## Direction

1. **Implemented:** independent compute jobs with isolated Lua state, copied inputs/results, cancellation, completion events and polling. CPU work runs independently of game ticks.
2. Server-configurable CPU, memory and queue budgets with useful profiling. Expensive computation should remain responsive without manual yield calls throughout algorithms.
3. Rich player-style turtle interactions: diagonal reach and harvesting, explicit target faces, held-item use, sneaking, entity interactions, container operations, and built-in vision of nearby visible blocks with incremental updates. Preserve normal server interaction hooks and item accounting.
4. Stable UUID-based device identity with readable names and numeric-ID compatibility.
5. Benchmark Java/Cobalt against native options such as LuaJIT or targeted C kernels before selecting a native backend. Native code must justify its compatibility, packaging and maintenance costs with measured improvements.

See [the architecture and milestone plan](doc/real-computing.md). The first representative workload is a farm controller performing 3D route planning while handling fleet messages and rendering dashboards.

## Upstream and licensing

This is an independent fork, not an official CC: Tweaked release. Original authorship and licenses are preserved; see [LICENSES](LICENSES), file-level SPDX notices, and [the upstream README](README.upstream.md). Existing API documentation is at [tweaked.cc](https://tweaked.cc/).
