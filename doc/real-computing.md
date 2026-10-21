<!-- SPDX-FileCopyrightText: 2026 boredhero
SPDX-License-Identifier: MPL-2.0 -->

# Real Computing Edition: architecture and milestones

## Intent and current status

Make ComputerCraft suitable for substantial autonomous systems: concurrent planning, persistent services, fleet coordination, simulation, and rich interaction with modded Minecraft.

The background-compute milestone is implemented as an experimental Cobalt worker pool; see [the actual API](compute.md). Block vision with invalidation events and targeted diagonal block interactions are also implemented; see [the turtle API contract](turtle-vision.md). Native acceleration and further turtle capabilities below remain planned. Target Minecraft 1.21.1 and NeoForge 21.1.250 first, preserving compatibility with CC: Tweaked 1.120.2 programs and peripherals.

## What upstream already does

Lua execution already runs outside the Minecraft server tick thread. `ComputerThread` owns Java workers and a fair scheduler; `ConfigSpec.computerThreads` configures the shared pool (default 1). `CobaltLuaMachine` executes Lua, and the scheduler can pause it. `TimeoutState` separately enforces the seven-second execution timeout. The Lua `parallel` library provides cooperative coroutines inside one machine, not independent simultaneous execution of its tasks.

World-facing operations use separate server-thread machinery. `MainThreadScheduler` and the main-thread execution budgets are distinct from the Lua worker scheduler. Raising `max_main_global_time` does not give pure Lua arithmetic a larger CPU budget.

The change we need is independent computation within a computer, with explicit ownership and lifecycle, rather than simply moving existing Lua execution to another thread.

## Compute worker design

- Each worker owns an isolated Lua state and executes on a bounded pool of platform threads, independently of tick frequency. Runnable jobs use round-robin time slices with per-computer admission limits; do not dedicate an unbounded OS thread to every submitted job.
- Initial workers run pure computation on copied inputs. They cannot directly access Minecraft levels, entities, inventories, peripherals, or the owning computer's mutable Lua globals.
- Inputs and outputs have a defined data-only format. Bound message size, nesting and queues; reject cycles and unsupported values with useful errors. Account for serialization overhead and memory as well as worker runtime.
- Results enter a thread-safe completion queue and wake the owner's event processing. World changes are submitted through the server-thread bridge. Simulation against a snapshot must revalidate relevant state before committing actions.
- Define cancellation, errors, timeouts and cleanup on computer shutdown, reboot, block removal, chunk unload and server stop. A generation identifier must prevent a previous boot's result from reaching a new one.
- Long pure-compute jobs should be preempted and resumed without requiring manual application yields. Give operators configurable budgets and diagnostics rather than removing scheduling and cancellation controls.
- Specify event delivery and backpressure so a slow consumer cannot accumulate unlimited results. Never block the server tick waiting for a worker.

Implemented API: `compute.submit(source, input)` returns a job ID; `compute_done` carries the ID and success flag; `compute.take(id)` retrieves the retained result or error; `compute.status(id)` polls; `compute.cancel(id)` requests cancellation; `compute.forget(id)` cancels/discards unwanted work. Source receives its input through `...` and returns one data value. Results survive dropped events until taken or forgotten. Jobs last only for the current computer boot and do not persist across shutdown. Module loading and captured closures are not supported.

The pool has configurable job, source, payload and execution-time bounds. It does **not** enforce a hard per-job Lua heap limit: Cobalt runs in the shared JVM. Native library calls and compilation are not guaranteed to yield at the target slice boundary. Hard memory isolation remains future work.

## Native execution decision

Start by measuring Cobalt on the real workload. Compare an isolated Cobalt worker implementation against native candidates using the same inputs and result contracts. LuaJIT is a candidate, not a selected backend: language semantics, supported Lua features, cancellation, native crashes, cross-platform binaries and library loading need evaluation. JNI calls are not automatically faster than JIT-compiled Java, particularly when transferring large world snapshots.

Targeted native kernels (for example path search on compact occupancy data) may provide a smaller first step than replacing all Lua execution. A native backend should remain optional until it demonstrates a material end-to-end improvement and has a defined failure model. Do not expose arbitrary native library loading to in-game programs.

## Turtle capability expansion

Implemented: `observe(radius)`, `unobserve()`, `turtle_vision` invalidation events,
`digAt(x,y,z)`, `placeAt(x,y,z)` and `useAt(x,y,z)`. Cached block observations are
invalidated by block mutations, pose changes and chunk availability. Interactions
use real-position ray checks and existing owner/protection hooks. Entity vision,
explicit faces, incremental snapshot diffs and the additional interactions below
remain future work.


Build on `TurtlePlayer` and existing `TurtlePlaceCommand` interaction paths. Existing place operations already invoke some item/block/entity interactions; audit their limits before duplicating them.

Prioritize explicit item use against a selected face and hit position, sneak interactions, empty-hand interactions, entity targeting, and container operations. Add diagonal reach: turtles should be able to explicitly target diagonal blocks for inspection and interaction, with documented reach and line-of-sight rules. This concerns reach; diagonal movement is a separate capability. Diagonal harvesting must not require breaking and restoring a neighboring crop as a stepping stone.

Add built-in player-like vision: query the blocks and entities a turtle sees within a requested radius, with positions, block states, range, occlusion and line-of-sight rules. Prefer cached observations with change events or incremental updates so programs do not need periodic full environmental scans. Snapshot collection remains on the server thread; visibility processing and planning can use compute workers. Define observation age, unloaded chunks and update costs explicitly. This should not require a separate scanner peripheral. Later investigate sustained use (charging items, eating, bows), equipment, recipes and mod-specific machine workflows.

Keep server event hooks, ownership/claim checks, reach and item consumption consistent with normal gameplay. Return structured outcomes distinguishing unsupported actions, denied interactions and ordinary failure. Serialize conflicting actions by a single turtle; a compute worker must not concurrently mutate its inventory or pose.

Universal player parity is the ambition, not a compatibility claim. Some mods assume a connected real player or client UI. Record and test those cases rather than treating fake-player right-click as a universal solution.

## Stable device identity

Implemented for computer blocks, turtles and pockets: `os.getComputerUUID()`,
block peripheral `getUUID()`, persistent components and duplicate detection.
`rednet.send` accepts UUIDs; `lookupUUID` and `broadcastUUID` support UUID fleets.
Internal storage migrates to UUID directories and recognised clones copy files
independently. Numeric rednet remains compatible. See [migration, pocket-copy
limitations and examples](identity.md).

## Milestones and acceptance evidence

1. **Reproducible baseline:** build the server's exact upstream release against NeoForge 21.1.250, preserve licenses and IDs, run core and loader unit tests, and record artifact provenance. A build is not a full modpack compatibility test.
2. **Independent workers:** implement worker isolation and completion delivery. Tests must prove two CPU jobs overlap when two workers are configured, owner events stay responsive during work, results cannot cross computer lifecycles, cancellation works, and job queues and retained result buffers remain bounded. Hard per-job heap isolation is not part of this milestone.
3. **Farm benchmark:** use representative 3D occupancy snapshots, path requests and fleet message traffic. Measure route throughput, result correctness, message/dashboard latency, server tick p95/p99, CPU use and memory. Compare upstream, Java workers and any native prototype with equal resources, both warm and cold.
4. **Interaction expansion:** validate explicit interactions in a copied Evolution world with representative vanilla and modded crops, tools, inventories and entities. Verify successful actions, failures, item counts, permissions and save/reload behavior.
5. **Pack qualification:** test Advanced Peripherals, existing farm scripts, client/server handshakes, world save/load and upgrades before deploying a release to the live server.

Keep upstream behavior available for existing programs. Build artifacts retain the `computercraft` ID and are replacements, not an additional installed mod. Do not silently change all computational or world-action budgets in the baseline.
