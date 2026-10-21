// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.compute;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LibFunction;

import static org.junit.jupiter.api.Assertions.*;

class ComputeCodecTest {
    private final LuaState state = new LuaState();

    @Test
    void rejectsUnsupportedValuesIncludingNestedFunctions() {
        var table = new LuaTable();
        table.rawset("function", LibFunction.create(s -> Constants.NIL));
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, table, 1024));
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, new LuaThread(state), 1024));
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, ValueFactory.valueOf(Double.NaN), 1024));
    }

    @Test
    void rejectsCyclesMetatablesAndTableKeys() throws LuaError {
        var cyclic = new LuaTable();
        cyclic.rawset("self", cyclic);
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, cyclic, 1024));
        var meta = new LuaTable();
        meta.setMetatable(state, new LuaTable());
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, meta, 1024));
        var key = new LuaTable();
        key.rawset(new LuaTable(), Constants.TRUE);
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, key, 1024));
    }

    @Test
    void boundsDepthAndBytesBeforeCrossingThreads() {
        var root = new LuaTable();
        var table = root;
        for (var i = 0; i < 40; i++) {
            var next = new LuaTable();
            table.rawset("child", next);
            table = next;
        }
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, root, 1024));
        assertThrows(LuaError.class, () -> ComputeCodec.encode(state, ValueFactory.valueOf("a".repeat(1024)), 1024));
    }

    @Test
    void copiesSharedSubtablesAndPreservesScalarKeys() throws LuaError {
        var root = new LuaTable();
        var shared = new LuaTable();
        shared.rawset(Constants.TRUE, ValueFactory.valueOf(42));
        root.rawset("a", shared);
        root.rawset("b", shared);
        var copy = ComputeCodec.decode(ComputeCodec.encode(state, root, 1024)).checkTable();
        assertNotSame(copy.rawget("a"), copy.rawget("b"));
        assertEquals(42, copy.rawget("a").checkTable().rawget(Constants.TRUE).checkInteger());
    }
}
