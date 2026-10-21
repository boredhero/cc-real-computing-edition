// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.core.compute.ComputeCodec;
import dan200.computercraft.core.compute.ComputeScheduler;
import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LibFunction;

import java.util.Objects;

/** Native bindings preserve binary strings and reject unsupported values before crossing threads. */
public final class ComputeAPI implements ILuaAPI {
    private final IAPIEnvironment environment;
    private final ComputeScheduler scheduler;
    private ComputeScheduler.@Nullable Session session;

    public ComputeAPI(IAPIEnvironment environment, ComputeScheduler scheduler) {
        this.environment = environment;
        this.scheduler = scheduler;
    }

    @Override
    public String[] getNames() {
        return new String[]{ "compute" };
    }

    @Override
    public String getModuleName() {
        return "cc.compute";
    }

    @Override
    public void startup() {
        shutdown();
        session = scheduler.newSession((id, success) -> environment.queueEvent("compute_done", id, success));
    }

    @Override
    public void shutdown() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    public void add(LuaTable table) {
        var session = Objects.requireNonNull(this.session, "Compute API has not started");
        var limits = scheduler.limits();
        table.rawset("submit", LibFunction.createV((state, args) -> {
            var value = args.first();
            if (!(value instanceof LuaString source)) throw new LuaError("Expected Lua source string");
            if (source.length() > limits.sourceBytes()) throw new LuaError("Compute source exceeds byte limit");
            var bytes = new byte[source.length()];
            source.copyTo(bytes, 0);
            var input = ComputeCodec.encode(state, args.arg(2), limits.payloadBytes());
            return ValueFactory.valueOf((double) session.submit(bytes, input));
        }));
        table.rawset("status", LibFunction.create((state, value) -> {
            var status = session.status(jobId(value));
            return status == null ? Constants.NIL : ValueFactory.valueOf(status);
        }));
        table.rawset("take", LibFunction.createV((state, args) -> {
            var result = session.take(jobId(args.first()));
            if (result == null) return ValueFactory.varargsOf(Constants.NIL, ValueFactory.valueOf("pending"));
            return result.success()
                ? ValueFactory.varargsOf(Constants.TRUE, ComputeCodec.decode(Objects.requireNonNull(result.value())))
                : ValueFactory.varargsOf(Constants.FALSE, ValueFactory.valueOf(Objects.requireNonNull(result.error())));
        }));
        table.rawset("cancel", LibFunction.create((state, value) -> ValueFactory.valueOf(session.cancel(jobId(value)))));
        table.rawset("forget", LibFunction.create((state, value) -> ValueFactory.valueOf(session.forget(jobId(value)))));
        table.rawset("limits", LibFunction.create(state -> {
            var result = new LuaTable();
            result.rawset("threads", ValueFactory.valueOf(limits.threads()));
            result.rawset("globalJobs", ValueFactory.valueOf(limits.globalJobs()));
            result.rawset("computerJobs", ValueFactory.valueOf(limits.computerJobs()));
            result.rawset("payloadBytes", ValueFactory.valueOf(limits.payloadBytes()));
            result.rawset("sourceBytes", ValueFactory.valueOf(limits.sourceBytes()));
            result.rawset("runtimeMillis", ValueFactory.valueOf(limits.runtimeMillis()));
            return result;
        }));
    }

    private static long jobId(LuaValue value) throws LuaError {
        if (!(value instanceof LuaNumber)) throw new LuaError("Expected compute job ID");
        var number = value.toDouble();
        if (!Double.isFinite(number) || number < 1 || number >= (1L << 53) || number != Math.floor(number)) {
            throw new LuaError("Invalid compute job ID");
        }
        return (long) number;
    }
}
