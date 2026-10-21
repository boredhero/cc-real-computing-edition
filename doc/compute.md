<!-- SPDX-FileCopyrightText: 2026 boredhero
SPDX-License-Identifier: MPL-2.0 -->

# Background compute jobs

Available in Real Computing Edition 0.2.0 for Minecraft 1.21.1 / NeoForge. The global `compute` API is also available as `require("cc.compute")`.

A job executes Lua source in its own Cobalt state, on a dedicated background thread pool. Two jobs can execute simultaneously when the pool has at least two threads. CPU-heavy loops need no `sleep()` or manual yields. Jobs are automatically time-sliced and resumed, independently of Minecraft ticks and the normal computer worker pool.

## Quick start

```lua
local id = compute.submit([[
    local input = ...
    local sum = 0
    for i = 1, input.count do sum = sum + i end
    return { sum = sum }
]], { count = 1000000 })

-- This computer can process network traffic, draw or submit other jobs meanwhile.
while true do
    local ok, result = compute.take(id)
    if ok ~= nil then
        assert(ok, result)
        print(result.sum)
        break
    end
    sleep(0.05)
end
```

Run `compute_demo` for two concurrent jobs and automatic cleanup. For a real route planner, submit a compact occupancy snapshot and goals; return a route, then revalidate the next movement against the current world before acting on it.

## API

| Function | Behavior |
| --- | --- |
| `compute.submit(source, input)` | Copies one optional input value and returns a numeric job ID. Throws if the source/input is invalid or admission limits are full. Syntax errors become job errors asynchronously. |
| `compute.status(id)` | Returns `queued`, `running`, `done`, `error`, or `cancelled`; returns `nil` for an unknown, consumed or forgotten ID. Time-sliced jobs alternate between queued and running. |
| `compute.take(id)` | Pending: `nil, "pending"`. Finished: `true, value` or `false, errorMessage`. A finished result is removed and its quota released. Taking an unknown or already-consumed ID throws. A successful `nil` result is `true, nil`, distinct from pending. |
| `compute.cancel(id)` | Requests cancellation; returns whether a new cancellation request was accepted. Completion is asynchronous for a running job. The cancelled result must still be taken or forgotten. Lua `pcall` cannot swallow cancellation. |
| `compute.forget(id)` | Cancels/discards unwanted work and suppresses future completion notifications. Returns false for an unknown/already-forgotten ID. Running work retains its quota until it actually stops. Use this when abandoning a job or cleaning up after a program error. |
| `compute.limits()` | Returns the effective `threads`, `globalJobs`, `computerJobs`, `payloadBytes`, `sourceBytes`, and `runtimeMillis`. |

`compute_done, id, success` is emitted once when a job finishes, fails or is cancelled, unless it was forgotten or its computer shut down. The event contains no result payload. The computer's normal bounded event queue may drop notifications: the retained result remains accessible through `take`. Event-driven programs should also periodically check outstanding job IDs. After taking/forgetting a result, ignore any stale notification for that ID.

IDs belong to the current computer boot. Another computer cannot access them. Reboot, shutdown and unload cancel all jobs and discard results. Closing the server context cancels its entire compute pool. Jobs are not persistent and do not keep chunks loaded. Terminal results count against admission limits until taken or forgotten, preventing a slow consumer from accumulating unlimited result buffers.

## Worker environment and data

The source is a Lua chunk, not a function closure. Its input is the first `...` argument. Return one value (use a table for multiple fields); returning multiple values or yielding out of the top-level chunk is an error. Internal coroutines are supported.

Workers have the Cobalt base, math, string, table, coroutine, UTF-8 and bit32 libraries. They have no `fs`, `os`, `io`, `package`, `require`, `debug`, `peripheral`, `turtle`, `http` or nested `compute` API. Printing, explicit garbage collection and dynamic source loading are unavailable. Define helper functions inside the submitted source, or read a source file on the owning computer before submitting it. Bytecode input is rejected.

Input and output may contain nil, booleans, finite numbers, binary strings, and plain tables whose keys are strings, finite numbers or booleans. Functions, threads, userdata, metatables and cycles are rejected, including inside nested tables. Shared subtables are copied independently rather than preserving identity. The format preserves binary strings, including NUL and non-UTF-8 bytes. Limits: 32 nesting levels and 65,536 serialized values, plus the configurable encoded byte limit.

The owner and worker never share mutable Lua objects. Mutating the input after submission cannot change a running job. Workers cannot directly touch the Minecraft world: do world/peripheral/turtle operations on the owning computer after receiving results.

## Server configuration

In `computercraft-server.toml`, all `[compute]` settings require a world/server restart:

```toml
[compute]
threads = 2
global_jobs = 256
computer_jobs = 8
payload_bytes = 1048576
source_bytes = 262144
runtime_millis = 300000
```

`runtime_millis` is accumulated elapsed time while a worker is executing a job, including compilation and serialization, but excluding time waiting in the run queue. It is not the normal seven-second computer timeout and is not a per-tick allowance. It is not a hardware CPU-time counter: OS descheduling and garbage collection while running count toward it. The target scheduling slice is 10 ms, checked by a separate monitor.

Payload and job limits bound admission and retained input/output buffers, **not the worker's Lua heap**. Workers share the JVM heap. Compilation and library operations may run longer than the target slice or temporarily allocate more memory before interruption is observed. This first implementation is not process isolation or a hard memory sandbox. Native acceleration and stronger memory accounting are future work.

## Validation

Tests cover simultaneous worker occupancy and third-job progress, no manual yielding, execution beyond seven seconds, cancellation inside `pcall`, execution budgets, nested coroutine preemption, binary data and table isolation, invalid payloads, retained results with dropped notifications, quota recovery, boot isolation, and owner event responsiveness. Unit and Lua integration tests do not replace testing with the full Evolution modpack in a copied world.
