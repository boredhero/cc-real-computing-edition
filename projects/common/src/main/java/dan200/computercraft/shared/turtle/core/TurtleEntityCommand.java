// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.platform.PlatformHelper;
import dan200.computercraft.shared.turtle.upgrades.TurtleTool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Re-resolve the UUID and visibility on execution, never trust an old observation.
 * @param target The target UUID.
 * @param action The interaction to perform.
 * @param sneak Whether to sneak.
 */
public record TurtleEntityCommand(UUID target, Action action, boolean sneak) implements TurtleCommand {
    public enum Action { ATTACK, USE, COLLECT }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        var level = (ServerLevel) turtle.getLevel();
        var entity = level.getEntity(target);
        if (entity == null) return TurtleCommandResult.failure("Entity is no longer loaded");
        var hit = TurtleTarget.visibleEntity(turtle, entity, TurtleTarget.REACH);
        if (hit == null) return TurtleCommandResult.failure("Entity is hidden or out of reach");
        var player = TurtlePlayer.get(turtle);
        player.player().setShiftKeyDown(sneak);
        player.aimAt(turtle, hit);
        if (player.isBlockProtected(level, entity.blockPosition())) return TurtleCommandResult.failure("Entity is in a protected area");
        if (action == Action.ATTACK) {
            if (entity instanceof Player other && (!level.getServer().isPvpAllowed() || !player.player().canHarmPlayer(other))) return TurtleCommandResult.failure("Player combat is disabled");
            // One attack attempt only: a denied hook must not be retried with a second tool.
            for (var side : TurtleSide.values()) {
                if (turtle.getUpgrade(side) instanceof TurtleTool tool) {
                    var result = tool.attackAt(turtle, side, entity, hit);
                    if (result.isSuccess()) turtle.playAnimation(side == TurtleSide.LEFT ? TurtleAnimation.SWING_LEFT_TOOL : TurtleAnimation.SWING_RIGHT_TOOL);
                    return result;
                }
            }
            return TurtleCommandResult.failure("No supported attack tool equipped");
        }
        player.loadInventory(turtle);
        try {
            boolean success;
            if (action == Action.COLLECT) {
                if (!(entity instanceof ItemEntity item)) return TurtleCommandResult.failure("Entity is not a dropped item");
                var count = item.getItem().getCount();
                var capacity = 0;
                for (var i = 0; i < 16; i++) {
                    var stack = player.player().getInventory().getItem(i);
                    if (stack.isEmpty()) {
                        capacity += item.getItem().getMaxStackSize();
                    } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, item.getItem())) {
                        capacity += Math.max(0, stack.getMaxStackSize() - stack.getCount());
                    }
                }
                if (capacity < count) return TurtleCommandResult.failure("Not enough space for the item stack");
                item.playerTouch(player.player());
                success = item.isRemoved() || item.getItem().getCount() < count;
            } else {
                success = PlatformHelper.get().interactWithEntity(player.player(), entity, hit);
            }
            if (success) turtle.playAnimation(TurtleAnimation.WAIT);
            return success ? TurtleCommandResult.success() : TurtleCommandResult.failure("Interaction denied or had no effect");
        } finally {
            if (player.player().containerMenu != player.player().inventoryMenu) player.player().doCloseContainer();
            player.unloadInventory(turtle);
            player.player().setShiftKeyDown(false);
        }
    }
}
