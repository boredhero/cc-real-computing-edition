// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.ITurtleAccess;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Validated, immutable interaction options copied from Lua before queueing a command.
 * @param face The requested relative or world face, or null for automatic selection.
 * @param sneak Whether to sneak.
 */
public record TurtleInteractionOptions(@Nullable String face, boolean sneak) {
    public static final TurtleInteractionOptions DEFAULT = new TurtleInteractionOptions(null, false);

    public static TurtleInteractionOptions parse(Optional<Map<?, ?>> options) throws LuaException {
        if (options.isEmpty()) return DEFAULT;
        var values = options.get();
        var face = values.get("face");
        if (face != null && (!(face instanceof String s) || !java.util.Set.of("front", "back", "left", "right", "up", "down", "north", "south", "east", "west").contains(s))) {
            throw new LuaException("Invalid face: expected front, back, left, right, up, down or a compass direction");
        }
        var sneak = values.get("sneak");
        if (sneak != null && !(sneak instanceof Boolean)) throw new LuaException("sneak must be a boolean");
        return new TurtleInteractionOptions((String) face, Boolean.TRUE.equals(sneak));
    }

    public @Nullable Direction direction(ITurtleAccess turtle) {
        if (face == null) return null;
        return switch (face) {
            case "front" -> turtle.getDirection();
            case "back" -> turtle.getDirection().getOpposite();
            case "left" -> turtle.getDirection().getCounterClockWise();
            case "right" -> turtle.getDirection().getClockWise();
            default -> Direction.byName(face);
        };
    }
}
