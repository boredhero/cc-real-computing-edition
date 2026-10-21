// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.compute;

import dan200.computercraft.core.util.ThreadUtils;
import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** A context-owned pool for isolated, time-sliced Lua computations. No Minecraft ticks are involved. */
public final class ComputeScheduler {
    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
    private final ComputeLimits limits;
    private final Object lock = new Object();
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Set<Job> jobs = new HashSet<>();
    private final Set<Job> running = new HashSet<>();
    private final List<Thread> threads = new ArrayList<>();
    private @Nullable ScheduledExecutorService monitor;
    private boolean closed;
    private long nextId;

    public ComputeScheduler(ComputeLimits limits) {
        this.limits = limits;
    }

    public ComputeLimits limits() {
        return limits;
    }

    public Session newSession(BiConsumer<Long, Boolean> completion) {
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Compute pool is closed");
            return new Session(completion);
        }
    }

    private void start() {
        if (!threads.isEmpty()) return;
        monitor = Executors.newSingleThreadScheduledExecutor(ThreadUtils.factory("Compute-Monitor"));
        monitor.scheduleAtFixedRate(() -> {
            synchronized (lock) {
                var now = System.nanoTime();
                for (var job : running) {
                    var state = job.state;
                    if (state != null && now - job.started >= SLICE_NANOS) state.interrupt();
                }
            }
        }, 5, 5, TimeUnit.MILLISECONDS);
        var factory = ThreadUtils.lowPriorityFactory("Compute-Worker");
        for (var i = 0; i < limits.threads(); i++) {
            var thread = factory.newThread(this::work);
            threads.add(thread);
            thread.start();
        }
    }

    private void work() {
        while (true) {
            Job job;
            synchronized (lock) {
                while (queue.isEmpty() && !closed) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (closed) return;
                job = queue.removeFirst();
                running.add(job);
                job.started = System.nanoTime();
                job.status = "running";
            }

            Completion result = null;
            try {
                result = job.execute();
            } catch (Stopped e) {
                result = new Completion(false, null, job.cancelled ? "Cancelled" : "Compute execution budget exceeded");
            } catch (LuaError | CompileException | RuntimeException | StackOverflowError e) {
                var message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
                result = new Completion(false, null, message.substring(0, Math.min(message.length(), 2048)));
            }

            synchronized (lock) {
                job.spent += System.nanoTime() - job.started;
                running.remove(job);
                if (job.cancelled) result = new Completion(false, null, "Cancelled");
                if (result == null) {
                    job.status = "queued";
                    queue.addLast(job);
                    lock.notifyAll();
                } else {
                    finish(job, result);
                }
            }
        }
    }

    private void finish(Job job, Completion result) {
        job.state = null;
        job.thread = null;
        job.source = job.input = null;
        job.result = result;
        job.status = job.cancelled ? "cancelled" : result.success() ? "done" : "error";
        if (job.owner.closed || job.discarded) {
            jobs.remove(job);
            job.owner.owned.remove(job.id);
        } else {
            // Only an ID and status enter the bounded computer event queue. The result stays here until taken.
            // Holding the lock prevents shutdown/reboot from racing this notification into a new session.
            job.owner.completion.accept(job.id, result.success());
        }
    }

    public int runningJobs() {
        synchronized (lock) {
            return running.size();
        }
    }

    public int outstandingJobs() {
        synchronized (lock) {
            return jobs.size();
        }
    }

    public void requestStop() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            for (var job : List.copyOf(jobs)) job.owner.close();
            if (monitor != null) monitor.shutdownNow();
            lock.notifyAll();
        }
    }

    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        var deadline = System.nanoTime() + unit.toNanos(timeout);
        List<Thread> workers;
        synchronized (lock) {
            workers = List.copyOf(threads);
        }
        for (var thread : workers) {
            var remaining = deadline - System.nanoTime();
            if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
            if (thread.isAlive()) return false;
        }
        return true;
    }

    /** A computer boot's job namespace. Closing it cancels work and discards all retained results. */
    public final class Session implements AutoCloseable {
        private final BiConsumer<Long, Boolean> completion;
        private final Map<Long, Job> owned = new HashMap<>();
        private boolean closed;

        private Session(BiConsumer<Long, Boolean> completion) {
            this.completion = completion;
        }

        public long submit(byte[] source, byte[] input) throws LuaError {
            synchronized (lock) {
                if (closed || ComputeScheduler.this.closed) throw new LuaError("Compute session is closed");
                if (owned.size() >= limits.computerJobs()) throw new LuaError("Too many outstanding compute jobs; take completed results first");
                if (jobs.size() >= limits.globalJobs()) throw new LuaError("Server compute job limit reached");
                if (source.length > limits.sourceBytes() || input.length > limits.payloadBytes()) throw new LuaError("Compute input exceeds byte limit");
                if (nextId >= (1L << 53) - 1) throw new LuaError("Compute job IDs exhausted");
                var job = new Job(++nextId, this, source.clone(), input.clone());
                owned.put(job.id, job);
                jobs.add(job);
                queue.addLast(job);
                start();
                lock.notifyAll();
                return job.id;
            }
        }

        public @Nullable String status(long id) {
            synchronized (lock) {
                var job = owned.get(id);
                return job == null || job.discarded ? null : job.status;
            }
        }

        public @Nullable Completion take(long id) throws LuaError {
            synchronized (lock) {
                var job = owned.get(id);
                if (job == null || job.discarded) throw new LuaError("Unknown compute job");
                if (job.result == null) return null;
                owned.remove(id);
                jobs.remove(job);
                return job.result;
            }
        }

        public boolean cancel(long id) {
            synchronized (lock) {
                var job = owned.get(id);
                if (job == null || job.result != null || job.cancelled) return false;
                job.cancelled = true;
                var state = job.state;
                if (state != null) state.interrupt();
                if (queue.remove(job)) finish(job, new Completion(false, null, "Cancelled"));
                return true;
            }
        }

        public boolean forget(long id) {
            synchronized (lock) {
                var job = owned.get(id);
                if (job == null || job.discarded) return false;
                job.discarded = true;
                cancel(id);
                if (!running.contains(job)) {
                    owned.remove(id);
                    jobs.remove(job);
                }
                // Running jobs retain their quota until cancellation has actually stopped them.
                return true;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                for (var job : List.copyOf(owned.values())) {
                    cancel(job.id);
                    if (!running.contains(job)) jobs.remove(job);
                }
                owned.clear();
            }
        }
    }

    @SuppressWarnings("ArrayRecordComponent") // Internal byte payload, consumed once; value equality is not used.
    public record Completion(boolean success, byte @Nullable [] value, @Nullable String error) {
    }

    private final class Job {
        private final long id;
        private final Session owner;
        private byte @Nullable [] source;
        private byte @Nullable [] input;
        private volatile @Nullable LuaState state;
        private @Nullable LuaThread thread;
        private volatile boolean cancelled;
        private boolean discarded;
        private long started;
        private long spent;
        private String status = "queued";
        private @Nullable Completion result;

        private Job(long id, Session owner, byte[] source, byte[] input) {
            this.id = id;
            this.owner = owner;
            this.source = source;
            this.input = input;
        }

        private void checkStop() {
            if (cancelled || spent + System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(limits.runtimeMillis())) {
                throw new Stopped();
            }
        }

        private @Nullable Completion execute() throws LuaError, CompileException {
            checkStop();
            Varargs arguments = Constants.NONE;
            var thread = this.thread;
            if (thread == null) {
                var state = this.state = LuaState.builder().interruptHandler(() -> {
                    checkStop();
                    return InterruptAction.SUSPEND;
                }).build();
                CoreLibraries.standardGlobals(state);
                Bit32Lib.add(state);
                // No host facilities or dynamic loading. Pure Lua functions and standard data libraries only.
                for (var name : List.of("print", "collectgarbage", "load", "loadstring", "loadfile", "dofile")) {
                    state.globals().rawset(name, Constants.NIL);
                }
                var code = Objects.requireNonNull(source);
                if (code.length > 0 && code[0] == 27) throw new LuaError("Compute workers accept source text, not bytecode");
                var function = LoadState.load(state, new ByteArrayInputStream(code), "=compute", state.globals());
                arguments = ComputeCodec.decode(Objects.requireNonNull(input));
                thread = this.thread = new LuaThread(state, function);
                source = input = null;
            }
            checkStop();
            var current = Objects.requireNonNull(state).getCurrentThread();
            var target = current == null || current == state.getMainThread() ? thread : current;
            var values = LuaThread.run(target, arguments);
            checkStop();
            if (values == null) return null; // VM preemption, not a user-visible yield.
            if (thread.isAlive()) throw new LuaError("A compute job cannot yield to its caller");
            if (values.count() > 1) throw new LuaError("Return one value from a compute job (use a table for multiple results)");
            var output = ComputeCodec.encode(Objects.requireNonNull(state), values.first(), limits.payloadBytes());
            checkStop();
            return new Completion(true, output, null);
        }
    }

    /** Bypasses Lua pcall, so a job cannot defeat cancellation by catching errors. */
    private static final class Stopped extends Error {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
