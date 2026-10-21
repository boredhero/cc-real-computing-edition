// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.compute;

import org.squiddev.cobalt.*;

import java.io.*;
import java.util.IdentityHashMap;

/** A bounded data-only wire format. No Lua objects cross between machines. */
public final class ComputeCodec {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_VALUES = 65_536;

    private ComputeCodec() {
    }

    public static byte[] encode(LuaState state, LuaValue value, int limit) throws LuaError {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                check(1);
                bytes.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                check(len);
                bytes.write(b, off, len);
            }

            private void check(int count) throws IOException {
                if (count > limit - bytes.size()) throw new IOException("Compute payload exceeds byte limit");
            }
        })) {
            new Encoder(state, output).write(value, 0);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new LuaError(e.getMessage());
        }
    }

    public static LuaValue decode(byte[] bytes) throws LuaError {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return read(input);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid internal compute payload", e);
        }
    }

    private static LuaValue read(DataInputStream input) throws IOException, LuaError {
        return switch (input.readUnsignedByte()) {
            case 0 -> Constants.NIL;
            case 1 -> ValueFactory.valueOf(input.readBoolean());
            case 2 -> ValueFactory.valueOf(input.readDouble());
            case 3 -> ValueFactory.valueOf(input.readNBytes(input.readInt()));
            case 4 -> {
                var count = input.readInt();
                var table = new LuaTable();
                for (var i = 0; i < count; i++) table.rawset(read(input), read(input));
                yield table;
            }
            default -> throw new IOException("Unknown value tag");
        };
    }

    private static final class Encoder {
        private final LuaState state;
        private final DataOutputStream output;
        private final IdentityHashMap<LuaTable, Boolean> parents = new IdentityHashMap<>();
        private int values;

        private Encoder(LuaState state, DataOutputStream output) {
            this.state = state;
            this.output = output;
        }

        private void write(LuaValue value, int depth) throws IOException, LuaError {
            if (++values > MAX_VALUES) throw new LuaError("Compute payload has too many values");
            if (depth > MAX_DEPTH) throw new LuaError("Compute payload is too deeply nested");
            switch (value.type()) {
                case Constants.TNIL -> output.writeByte(0);
                case Constants.TBOOLEAN -> {
                    output.writeByte(1);
                    output.writeBoolean(value.toBoolean());
                }
                case Constants.TNUMBER -> {
                    var number = value.toDouble();
                    if (!Double.isFinite(number)) throw new LuaError("Compute payload numbers must be finite");
                    output.writeByte(2);
                    output.writeDouble(number);
                }
                case Constants.TSTRING -> {
                    var string = (LuaString) value;
                    output.writeByte(3);
                    output.writeInt(string.length());
                    string.write((OutputStream) output);
                }
                case Constants.TTABLE -> {
                    var table = (LuaTable) value;
                    if (table.getMetatable(state) != null) throw new LuaError("Compute payload tables cannot have metatables");
                    if (parents.put(table, true) != null) throw new LuaError("Compute payload cannot contain cycles");
                    if (table.size() > MAX_VALUES / 2) throw new LuaError("Compute payload has too many values");
                    output.writeByte(4);
                    output.writeInt(table.size());
                    LuaValue key = Constants.NIL;
                    while (true) {
                        var pair = table.next(key);
                        key = pair.first();
                        if (key.isNil()) break;
                        if (!(key instanceof LuaString || key instanceof LuaNumber || key instanceof LuaBoolean)) {
                            throw new LuaError("Compute table keys must be strings, numbers or booleans");
                        }
                        write(key, depth + 1);
                        write(pair.arg(2), depth + 1);
                    }
                    parents.remove(table);
                }
                default -> throw new LuaError("Cannot transfer " + value.typeName() + " to a compute worker");
            }
        }
    }
}
