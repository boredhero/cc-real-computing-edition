// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.lua.LuaException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detached, bounded Lua data for queued adapter arguments and results. */
public final class TurtleInteractionData {
    private int remaining = 4096;
    private int characters = 65536;

    private TurtleInteractionData() {
    }

    public static Map<?, ?> copy(Map<?, ?> value) throws LuaException {
        return (Map<?, ?>) new TurtleInteractionData().value(value, 0);
    }

    private Object value(Object value, int depth) throws LuaException {
        if (--remaining < 0 || depth > 16) throw new LuaException("Interaction data is too large or deeply nested");
        if (value instanceof String string) {
            characters -= string.length();
            if (characters < 0) throw new LuaException("Interaction strings are too large");
            return string;
        }
        if (value instanceof Boolean) return value;
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) return number.doubleValue();
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<Object, Object>();
            for (var entry : map.entrySet()) {
                var key = entry.getKey();
                if (!(key instanceof String || key instanceof Number || key instanceof Boolean)) throw new LuaException("Invalid interaction table key");
                result.put(value(key, depth + 1), value(entry.getValue(), depth + 1));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<Object>();
            for (var entry : list) result.add(value(entry, depth + 1));
            return Collections.unmodifiableList(result);
        }
        throw new LuaException("Interaction values must be strings, finite numbers, booleans, tables or lists");
    }
}
