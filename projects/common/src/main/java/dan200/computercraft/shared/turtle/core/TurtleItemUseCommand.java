// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;

/**
 * Start or cancel a bounded, asynchronous held item action.
 * @param cancel Whether to cancel instead of starting.
 * @param ticks Maximum hold duration.
 * @param yaw Relative horizontal aim.
 * @param pitch Vertical aim.
 * @param sneak Whether to sneak.
 */
public record TurtleItemUseCommand(boolean cancel, int ticks, float yaw, float pitch, boolean sneak) implements TurtleCommand {
    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        var use = ((TurtleBrain) turtle).itemUse;
        if (!cancel) return use.start(ticks, yaw, pitch, sneak);
        use.cancel();
        return TurtleCommandResult.success();
    }
}
