// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.api.turtle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A validated interaction, valid only for the duration of an adapter callback. The player's main hand
 * contains the selected turtle item; inventory changes are copied back after the callback.
 * Use the player inventory for item changes, not the turtle inventory (which is synchronised afterwards).
 *
 * @param turtle The calling turtle.
 * @param level The server level.
 * @param hit The visible target and clicked face.
 * @param player The turtle's owner-aware fake player, including sneak state.
 */
public record TurtleInteractionContext(ITurtleAccess turtle, ServerLevel level, BlockHitResult hit, ServerPlayer player) {
    /**
     * Get the target block position.
     * @return The target position.
     */
    public BlockPos pos() {
        return hit.getBlockPos();
    }
}
