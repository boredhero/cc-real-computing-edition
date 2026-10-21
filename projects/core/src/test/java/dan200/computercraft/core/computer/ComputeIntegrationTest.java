// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.computer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class ComputeIntegrationTest {
    @Test
    void ownerHandlesEventsWhileJobsRunAndConsumesResults() {
        ComputerBootstrap.run("""
            assert(require('cc.compute') == compute)
            local busy = compute.submit('while true do end')
            os.queueEvent('owner_ping', 42)
            local name, value = os.pullEvent('owner_ping')
            assert(value == 42 and compute.take(busy) == nil)
            local input = { n = 10000, binary = string.char(0, 255, 128) }
            local id = compute.submit([[
                local input = ...
                local sum = 0
                for i = 1, input.n do sum = sum + i end
                return { sum = sum, binary = input.binary }
            ]], input)
            input.n = 0
            while true do
                local event, done, ok = os.pullEvent('compute_done')
                if done == id then assert(ok); break end
            end
            local ok, result = compute.take(id)
            assert(ok and result.sum == 50005000)
            assert(result.binary == string.char(0, 255, 128))
            assert(compute.status(id) == nil)
            assert(not pcall(compute.take, id))
            assert(compute.cancel(busy))
            while true do
                local event, done, ok = os.pullEvent('compute_done')
                if done == busy then assert(not ok); break end
            end
            local ok, err = compute.take(busy)
            assert(ok == false and err == 'Cancelled')
            assert(compute.limits().threads >= 1)
            """, 10);
    }

    @Test
    void validatesInputsWithoutSilentlyDroppingFunctions() {
        ComputerBootstrap.run("""
            assert(not pcall(compute.submit, 'return ...', { bad = function() end }))
            local cyclic = {}; cyclic.self = cyclic
            assert(not pcall(compute.submit, 'return ...', cyclic))
            assert(not pcall(compute.submit, 123))
            assert(not pcall(compute.status, -1))
            assert(not pcall(compute.status, 1.5))
            assert(not pcall(compute.status, 0/0))
            assert(compute.status(999) == nil)
            assert(compute.cancel(999) == false)
            """, 5);
    }

    @Test
    void bundledDemoRunsOnARealCraftOSComputer() {
        ComputerBootstrap.run("assert(shell.run('compute_demo', '1000'))", 5);
    }
}
