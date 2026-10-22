// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.lua.LuaException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TurtleInteractionDataTest {
    @Test
    void detachesNestedInputsAndResults() throws LuaException {
        var nested = new HashMap<String, Object>();
        nested.put("value", 1);
        var list = new ArrayList<>(List.of("first"));
        var copy = TurtleInteractionData.copy(Map.of("nested", nested, "list", list));
        nested.put("value", 2);
        list.clear();
        assertEquals(Map.of("value", 1.0), copy.get("nested"));
        assertEquals(List.of("first"), copy.get("list"));
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) copy.get("nested")).clear());
    }

    @Test
    void rejectsCyclesAndOversizedOrUnsupportedValues() {
        var cycle = new HashMap<String, Object>();
        cycle.put("self", cycle);
        assertThrows(LuaException.class, () -> TurtleInteractionData.copy(cycle));
        assertThrows(LuaException.class, () -> TurtleInteractionData.copy(Map.of("text", "x".repeat(65537))));
        assertThrows(LuaException.class, () -> TurtleInteractionData.copy(Map.of("number", Double.NaN)));
        assertThrows(LuaException.class, () -> TurtleInteractionData.copy(Map.of("object", new Object())));
        assertThrows(LuaException.class, () -> TurtleInteractionData.copy(Map.of("list", java.util.Collections.nCopies(4097, true))));
    }
}
