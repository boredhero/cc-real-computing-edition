-- SPDX-FileCopyrightText: 2019 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

local capture = require "test_helpers".capture_program

describe("The id program", function()
    it("displays a persistent UUID when provided by the host", function()
        local uuid = "779150ba-3a48-4a1b-ab82-269ce0c6d1e1"
        stub(os, "getComputerUUID", function() return uuid end)
        expect(capture("id")):matches {
            ok = true, output = "This is computer #" .. os.getComputerID() .. "\nUUID: " .. uuid .. "\n", error = "",
        }
    end)


    it("displays computer id", function()
        local id = os.getComputerID()

        expect(capture("id"))
            :matches { ok = true, output = "This is computer #" .. id .. "\n", error = "" }
    end)
end)
