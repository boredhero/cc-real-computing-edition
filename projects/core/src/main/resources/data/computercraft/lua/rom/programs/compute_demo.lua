-- SPDX-FileCopyrightText: 2026 boredhero
-- SPDX-License-Identifier: MPL-2.0

local count = tonumber((...)) or 5000000
if count < 1 or count > 100000000 or count ~= math.floor(count) then
    error("Usage: compute_demo [integer from 1 to 100000000]", 0)
end

local jobs = {}
local started = os.epoch("utc")
local ok, err = pcall(function()
    for i = 1, 2 do
        jobs[i] = compute.submit([[
            local input = ...
            local sum = 0
            for n = 1, input.count do sum = sum + n end
            return { label = input.label, sum = sum }
        ]], { count = count, label = i })
    end
    print("Two background jobs submitted.")
    print("The owner is free to process events while they run.")

    local remaining = 2
    local pending = { jobs[1], jobs[2] }
    while remaining > 0 do
        for i, id in pairs(pending) do
            local success, result = compute.take(id)
            if success ~= nil then
                assert(success, result)
                assert(result.sum == count * (count + 1) / 2, "Unexpected result")
                print(("Job %d: %.0f"):format(result.label, result.sum))
                pending[i] = nil
                remaining = remaining - 1
            end
        end
        if remaining > 0 then sleep(0.05) end
    end
    print(("Finished in %.3f seconds."):format((os.epoch("utc") - started) / 1000))
end)

-- Also clean up on Ctrl+T or a job failure. Forget is safe for already-taken results.
for _, id in ipairs(jobs) do compute.forget(id) end
if not ok then error(err, 0) end
