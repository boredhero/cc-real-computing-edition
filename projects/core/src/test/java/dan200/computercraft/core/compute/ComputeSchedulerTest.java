// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.compute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.squiddev.cobalt.*;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
@Execution(ExecutionMode.SAME_THREAD)
class ComputeSchedulerTest {
    private ComputeScheduler pool = new ComputeScheduler(ComputeLimits.DEFAULT);
    private final LinkedBlockingQueue<Long> events = new LinkedBlockingQueue<>();
    private final LuaState state = new LuaState();

    @AfterEach
    void close() throws InterruptedException {
        pool.requestStop();
        assertTrue(pool.awaitStopped(3, TimeUnit.SECONDS), "Compute threads must terminate");
        assertEquals(0, pool.outstandingJobs());
    }

    private ComputeScheduler.Session session() {
        return pool.newSession((id, ok) -> events.add(id));
    }

    private long submit(ComputeScheduler.Session session, String code) throws LuaError {
        return session.submit(code.getBytes(StandardCharsets.UTF_8), ComputeCodec.encode(state, Constants.NIL, 1024));
    }

    private ComputeScheduler.Completion await(ComputeScheduler.Session session, long id) throws Exception {
        awaitCondition(() -> {
            var status = session.status(id);
            return "done".equals(status) || "error".equals(status) || "cancelled".equals(status);
        });
        return Objects.requireNonNull(session.take(id));
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Condition did not become true");
            Thread.sleep(1);
        }
    }

    @Test
    void transfersBinaryDataAndIsolatesMutableTables() throws Exception {
        var input = new LuaTable();
        input.rawset("count", ValueFactory.valueOf(41));
        var binary = new byte[]{ 0, (byte) 255, (byte) 128, 65 };
        input.rawset("binary", ValueFactory.valueOf(binary));
        var session = session();
        var id = session.submit("local x = ...; x.count = x.count + 1; return x".getBytes(StandardCharsets.UTF_8), ComputeCodec.encode(state, input, 1024));
        input.rawset("count", ValueFactory.valueOf(100));
        var result = await(session, id);
        assertTrue(result.success());
        var output = ComputeCodec.decode(Objects.requireNonNull(result.value())).checkTable();
        assertEquals(42, output.rawget("count").checkInteger());
        assertEquals(100, input.rawget("count").checkInteger());
        var actual = new byte[4];
        output.rawget("binary").checkLuaString().copyTo(actual, 0);
        assertArrayEquals(binary, actual);
        assertEquals(id, events.poll(1, TimeUnit.SECONDS));
        assertNull(session.status(id));
    }

    @Test
    void runsTwoJobsAtOnceAndPreemptsThemForOtherWork() throws Exception {
        var session = session();
        var a = submit(session, "while true do end");
        var b = submit(session, "while true do end");
        awaitCondition(() -> pool.runningJobs() == 2);
        var quick = await(session, submit(session, "return 6 * 7"));
        assertTrue(quick.success(), "A third job must progress while both long jobs remain alive");
        assertEquals(42, ComputeCodec.decode(Objects.requireNonNull(quick.value())).checkInteger());
        assertNotNull(session.status(a));
        assertNotNull(session.status(b));
        session.cancel(a);
        session.cancel(b);
        assertEquals("Cancelled", await(session, a).error());
        assertEquals("Cancelled", await(session, b).error());
    }

    @Test
    void cancellationCannotBeCaughtByLuaAndPoolStaysUsable() throws Exception {
        pool = new ComputeScheduler(new ComputeLimits(1, 8, 8, 1024, 1024, 10_000));
        var session = session();
        var id = submit(session, "while true do pcall(function() while true do end end) end");
        awaitCondition(() -> pool.runningJobs() == 1);
        assertTrue(session.cancel(id));
        assertEquals("Cancelled", await(session, id).error());
        assertTrue(await(session, submit(session, "return true")).success());
    }

    @Test
    void longComputationsSurviveTheNormalSevenSecondTimeout() throws Exception {
        var session = session();
        var id = submit(session, "while true do end");
        awaitCondition(() -> pool.runningJobs() == 1);
        Thread.sleep(7200);
        assertNull(session.take(id), "Compute jobs should not inherit the computer's seven-second timeout");
        assertTrue(session.cancel(id));
        assertEquals("Cancelled", await(session, id).error());
    }

    @Test
    void appliesExecutionBudgetWithoutPoisoningOtherJobs() throws Exception {
        pool = new ComputeScheduler(new ComputeLimits(1, 8, 8, 1024, 1024, 100));
        var session = session();
        var result = await(session, submit(session, "while true do pcall(function() while true do end end) end"));
        assertFalse(result.success());
        assertEquals("Compute execution budget exceeded", result.error());
        assertTrue(await(session, submit(session, "return 'alive'")).success());
    }

    @Test
    void retainsResultsWhenNotificationsAreIgnoredAndEnforcesQuotas() throws Exception {
        pool = new ComputeScheduler(new ComputeLimits(1, 2, 1, 1024, 1024, 10_000));
        var first = session();
        var second = session();
        var third = session();
        var a = submit(first, "return 1");
        assertEquals(a, events.poll(5, TimeUnit.SECONDS));
        assertThrows(LuaError.class, () -> submit(first, "return 2"));
        var b = submit(second, "return 2");
        assertEquals(b, events.poll(5, TimeUnit.SECONDS));
        assertThrows(LuaError.class, () -> submit(third, "return 3"));
        assertTrue(Objects.requireNonNull(first.take(a)).success());
        assertTrue(await(third, submit(third, "return 3")).success());
        assertTrue(Objects.requireNonNull(second.take(b)).success());
        assertEquals(0, pool.outstandingJobs());
    }

    @Test
    void closedBootCannotNotifyOrLeakIntoNewBoot() throws Exception {
        var old = session();
        var id = submit(old, "while true do end");
        awaitCondition(() -> pool.runningJobs() == 1);
        old.close();
        var fresh = session();
        var newId = submit(fresh, "return 42");
        assertNotEquals(id, newId);
        assertTrue(await(fresh, newId).success());
        awaitCondition(() -> pool.outstandingJobs() == 0);
        assertNull(fresh.status(id));
        assertNull(old.status(id));
        assertEquals(newId, events.poll(1, TimeUnit.SECONDS));
        assertTrue(events.isEmpty());
        assertThrows(LuaError.class, () -> submit(old, "return 1"));
    }

    @Test
    void forgettingRunningAndCompletedJobsReleasesTheirResources() throws Exception {
        var session = session();
        var running = submit(session, "while true do end");
        awaitCondition(() -> pool.runningJobs() == 1);
        assertTrue(session.forget(running));
        assertNull(session.status(running));
        assertThrows(LuaError.class, () -> session.take(running));
        awaitCondition(() -> pool.outstandingJobs() == 0);
        assertTrue(events.isEmpty());
        var done = submit(session, "return 42");
        assertEquals(done, events.poll(5, TimeUnit.SECONDS));
        assertTrue(session.forget(done));
        assertFalse(session.forget(done));
        assertEquals(0, pool.outstandingJobs());
    }

    @Test
    void pollingWorksEvenWhenEveryNotificationIsDropped() throws Exception {
        var session = pool.newSession((id, ok) -> { });
        var result = await(session, submit(session, "return 42"));
        assertTrue(result.success());
        assertEquals(42, ComputeCodec.decode(Objects.requireNonNull(result.value())).checkInteger());
    }

    @Test
    void sourceAndResultSizeLimitsAreEnforced() throws Exception {
        pool = new ComputeScheduler(new ComputeLimits(1, 8, 8, 128, 128, 10_000));
        var session = session();
        assertThrows(LuaError.class, () -> submit(session, " ".repeat(129)));
        var result = await(session, submit(session, "return string.rep('a', 256)"));
        assertFalse(result.success());
        assertEquals("Compute payload exceeds byte limit", result.error());
        assertTrue(await(session, submit(session, "return 42")).success());
    }

    @Test
    void reportsSourceRuntimeAndResultErrors() throws Exception {
        var session = session();
        for (var source : new String[]{ "this isn't lua", "error('example')", "return function() end", "coroutine.yield()", "return 1, 2" }) {
            var result = await(session, submit(session, source));
            assertFalse(result.success(), source);
            assertNotNull(result.error());
        }
        assertTrue(await(session, submit(session, "return nil")).success());
    }

    @Test
    void resumesPreemptedNestedCoroutinesCorrectly() throws Exception {
        var session = session();
        var result = await(session, submit(session, """
            local co = coroutine.create(function()
                local sum = 0
                for i = 1, 10000000 do sum = sum + i end
                return sum
            end)
            local ok, sum = coroutine.resume(co)
            assert(ok, sum)
            return sum
            """));
        assertTrue(result.success(), result.error());
        assertEquals(50000005000000.0, ComputeCodec.decode(Objects.requireNonNull(result.value())).toDouble());
    }

    @Test
    void workersHaveNoHostCapabilitiesAndNoSharedGlobals() throws Exception {
        var session = session();
        var result = await(session, submit(session, """
            assert(fs == nil and peripheral == nil and turtle == nil and http == nil)
            assert(os == nil and io == nil and package == nil and debug == nil)
            assert(compute == nil and load == nil and collectgarbage == nil)
            secret = 42
            return math.sqrt(81)
            """));
        assertTrue(result.success(), result.error());
        assertEquals(9, ComputeCodec.decode(Objects.requireNonNull(result.value())).checkInteger());
        assertTrue(await(session, submit(session, "assert(secret == nil); return true")).success());
    }
}
