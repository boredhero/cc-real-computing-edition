// SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.platform.PlatformHelper;
import dan200.computercraft.shared.turtle.upgrades.TurtleTool;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Explicit block targeting without moving the turtle or impersonating a closer player.
 *
 * @param x Right offset.
 * @param y Up offset.
 * @param z Forward offset.
 * @param action The interaction to perform.
 */
public record TurtleInteractAtCommand(int x, int y, int z, Action action) implements TurtleCommand {
    public enum Action { DIG, PLACE, USE }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        var pos = TurtleTarget.position(turtle, x, y, z);
        if (!TurtleTarget.inReach(turtle, pos)) return TurtleCommandResult.failure("Target is out of reach");
        var level = (ServerLevel) turtle.getLevel();
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return TurtleCommandResult.failure("Target is not loaded");
        var view = TurtleTarget.view(turtle);
        var hit = TurtleTarget.visible(turtle, view, pos);
        if (hit == null) return TurtleCommandResult.failure("Target is obstructed");
        if (hit.getLocation().distanceToSqr(Vec3.atCenterOf(turtle.getPosition())) > TurtleTarget.REACH * TurtleTarget.REACH) return TurtleCommandResult.failure("Target face is out of reach");
        if (action == Action.DIG) {
            TurtleCommandResult failure = TurtleCommandResult.failure("No supported digging tool equipped");
            for (var side : TurtleSide.values()) {
                if (!(turtle.getUpgrade(side) instanceof TurtleTool tool)) continue;
                var result = tool.digAt(turtle, side, hit);
                if (result.isSuccess()) {
                    turtle.playAnimation(side == TurtleSide.LEFT ? TurtleAnimation.SWING_LEFT_TOOL : TurtleAnimation.SWING_RIGHT_TOOL);
                    return result;
                }
                failure = result;
            }
            return failure;
        }

        var player = TurtlePlayer.get(turtle);
        player.aimAt(turtle, hit.getLocation());
        if (player.isBlockProtected(level, pos)) return TurtleCommandResult.failure("Cannot interact in protected area");
        player.loadInventory(turtle);
        try {
            var stack = player.player().getMainHandItem();
            if (action == Action.PLACE) {
                if (!(stack.getItem() instanceof BlockItem)) return TurtleCommandResult.failure("Selected item is not a block; use useAt for items");
                // Select a real, visible support face which places into precisely the requested cell.
                BlockHitResult placement = null;
                for (var face : Direction.values()) {
                    var support = pos.relative(face.getOpposite());
                    if (!level.isInWorldBounds(support) || !level.hasChunkAt(support)) continue;
                    var shape = level.getBlockState(support).getShape(view, support);
                    if (shape.isEmpty()) continue;
                    var end = shape.bounds().getCenter().add(Vec3.atLowerCornerOf(support));
                    var supportHit = TurtleTarget.ray(turtle, view, end);
                    if (supportHit.getLocation().distanceToSqr(Vec3.atCenterOf(turtle.getPosition())) > TurtleTarget.REACH * TurtleTarget.REACH) continue;
                    if (supportHit.getType() == HitResult.Type.MISS || !supportHit.getBlockPos().equals(support) || supportHit.getDirection() != face) continue;
                    var context = new BlockPlaceContext(new UseOnContext(player.player(), InteractionHand.MAIN_HAND, supportHit));
                    if (context.getClickedPos().equals(pos) && context.canPlace() && !player.isBlockProtected(level, support)) {
                        placement = supportHit;
                        break;
                    }
                }
                // Replace a visible replaceable block, such as grass, directly.
                if (placement == null && !level.isEmptyBlock(pos)) {
                    var context = new BlockPlaceContext(new UseOnContext(player.player(), InteractionHand.MAIN_HAND, hit));
                    if (context.getClickedPos().equals(pos) && context.canPlace()) placement = hit;
                }
                if (placement == null) return TurtleCommandResult.failure("No visible placement face for target");
                hit = placement;
                player.aimAt(turtle, hit.getLocation());
            } else if (level.isEmptyBlock(pos)) {
                return TurtleCommandResult.failure("No block to use");
            }

            if (action == Action.PLACE && !placementAllowed(turtle, player, hit)) {
                return TurtleCommandResult.failure("Placement destination is unavailable or protected");
            }
            var permission = PlatformHelper.get().useOn(player.player(), stack, hit);
            boolean success;
            if (permission instanceof PlatformHelper.UseOnResult.Handled handled) {
                success = handled.result().consumesAction();
            } else {
                var allowed = (PlatformHelper.UseOnResult.Continue) permission;
                success = false;
                if (action == Action.USE && allowed.block()) {
                    var state = level.getBlockState(pos);
                    var result = state.useItemOn(stack, level, player.player(), InteractionHand.MAIN_HAND, hit);
                    success = result.consumesAction();
                    if (!success && result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                        success = state.useWithoutItem(level, player.player(), hit).consumesAction();
                    }
                }
                if (!success && allowed.item() && !stack.isEmpty()) {
                    if (stack.getItem() instanceof BlockItem && !placementAllowed(turtle, player, hit)) return TurtleCommandResult.failure("Placement destination is unavailable or protected");
                    success = stack.useOn(new UseOnContext(player.player(), InteractionHand.MAIN_HAND, hit)).consumesAction();
                }
            }
            if (!success) return TurtleCommandResult.failure("Interaction denied or had no effect");
            turtle.playAnimation(TurtleAnimation.WAIT);
            return TurtleCommandResult.success();
        } finally {
            player.unloadInventory(turtle);
            if (player.player().containerMenu != player.player().inventoryMenu) player.player().doCloseContainer();
        }
    }

    private static boolean placementAllowed(ITurtleAccess turtle, TurtlePlayer player, BlockHitResult hit) {
        var level = (ServerLevel) turtle.getLevel();
        var destination = new BlockPlaceContext(new UseOnContext(player.player(), InteractionHand.MAIN_HAND, hit)).getClickedPos();
        return level.isInWorldBounds(destination) && level.hasChunkAt(destination)
            && TurtleTarget.inReach(turtle, destination) && !player.isBlockProtected(level, destination);
    }

}
