-- SPDX-FileCopyrightText: 2026 boredhero
-- SPDX-License-Identifier: MPL-2.0

--[[- Run isolated Lua computations on a dedicated background thread pool.

Jobs receive copied data and cannot access peripherals or Minecraft directly.
They are automatically time-sliced, without requiring manual yields.
Results remain available until taken or forgotten, including when completion
events are dropped. Jobs are cancelled on computer shutdown or unload.

@module compute
]]

--- Submit Lua source with one optional input value, received through `...`.
-- @tparam string source The Lua chunk to execute. It must return at most one data value.
-- @param[opt] input The data to copy into the worker.
-- @treturn number The job ID, owned by this computer boot.
-- @throws If admission limits are full or source/input exceeds transfer limits.
function submit(source, input) end

--- Query a job without consuming its result.
-- @tparam number id The job ID.
-- @treturn string|nil queued, running, done, error, cancelled, or nil for an unknown ID.
function status(id) end

--- Retrieve and remove a finished result, releasing its quota.
-- @tparam number id The job ID.
-- @treturn boolean|nil True on success, false on failure, or nil if pending.
-- @return The result, error message, or the string "pending".
-- @throws If the job ID is unknown or already consumed.
function take(id) end

--- Request cancellation. Take or forget the eventual cancelled result.
-- @tparam number id The job ID.
-- @treturn boolean Whether a new cancellation request was accepted.
function cancel(id) end

--- Cancel and discard a job, suppressing future completion notifications.
-- @tparam number id The job ID.
-- @treturn boolean Whether an outstanding job was found.
function forget(id) end

--- Get the server's effective compute resource limits.
-- @treturn table Thread, job, source, payload and execution time limits.
function limits() end
