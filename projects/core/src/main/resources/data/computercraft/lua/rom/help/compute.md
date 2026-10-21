Background Lua compute workers

Run "compute_demo" to try two concurrent jobs.

compute.submit(source, input) returns a job ID.
The source receives input through ... and returns one data value.
Each job has its own Lua state on a separate compute thread pool.
Heavy loops need no manual yields. No turtle/peripheral/world access is available inside a worker.

compute.status(id) returns queued/running/done/error/cancelled, or nil for an unknown ID.
compute.take(id) returns nil, "pending" while running; otherwise true, result or false, error.
Taking a completed result removes it and releases its quota.
compute.cancel(id) requests cancellation. Take or forget the resulting cancelled job.
compute.forget(id) cancels/discards unwanted work, including cleanup after Ctrl+T.
compute.limits() returns the server's configured worker limits.

The compute_done event carries id, success. Results remain available through take even if an event is dropped.
Poll periodically when using events. Jobs are cancelled and results discarded on computer shutdown/unload.

Data supports nil, booleans, finite numbers, binary strings and plain tables with scalar keys.
Functions, metatables and cyclic tables cannot cross workers. Use a table to return multiple fields.
Limits bound job counts and input/output size, not the job's working heap; workers share JVM memory.

Full documentation: https://github.com/boredhero/cc-real-computing-edition/blob/main/doc/compute.md
