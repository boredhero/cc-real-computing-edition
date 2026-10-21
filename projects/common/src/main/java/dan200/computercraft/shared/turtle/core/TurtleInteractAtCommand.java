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
 * @param options Face and sneaking options.
 * @param count Maximum transfer count.
 * @param slot One-based container slot, or zero for any slot.
 */
public record TurtleInteractAtCommand(int x, int y, int z, Action action, TurtleInteractionOptions options, int count, int slot) implements TurtleCommand {
    public enum Action { DIG, PLACE, USE, LIST, PUSH, PULL }

    public TurtleInteractAtCommand(int x, int y, int z, Action action) {
        this(x, y, z, action, TurtleInteractionOptions.DEFAULT, 64, 0);
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        var pos = TurtleTarget.position(turtle, x, y, z);
        if (!TurtleTarget.inReach(turtle, pos)) return TurtleCommandResult.failure("Target is out of reach");
        var level = (ServerLevel) turtle.getLevel();
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return TurtleCommandResult.failure("Target is not loaded");
        var view = TurtleTarget.view(turtle);
        var face = options.direction(turtle);
        var hit = action == Action.PLACE ? TurtleTarget.visible(turtle, view, pos) : TurtleTarget.visibleFace(turtle, view, pos, face);
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
        player.player().setShiftKeyDown(options.sneak());
        player.aimAt(turtle, hit.getLocation());
        if (player.isBlockProtected(level, pos)) return TurtleCommandResult.failure("Cannot interact in protected area");
        player.loadInventory(turtle);
        try {
            var stack = player.player().getMainHandItem();
            if (action == Action.PLACE) {
                if (!(stack.getItem() instanceof BlockItem)) return TurtleCommandResult.failure("Selected item is not a block; use useAt for items");
                // Select a real, visible support face which places into precisely the requested cell.
                BlockHitResult placement = null;
                for (var supportFace : Direction.values()) {
                    if (face != null && supportFace != face) continue;
                    var support = pos.relative(supportFace.getOpposite());
                    if (!level.isInWorldBounds(support) || !level.hasChunkAt(support)) continue;
                    var shape = level.getBlockState(support).getShape(view, support);
                    if (shape.isEmpty()) continue;
                    var end = shape.bounds().getCenter().add(Vec3.atLowerCornerOf(support));
                    var supportHit = TurtleTarget.ray(turtle, view, end);
                    if (supportHit.getLocation().distanceToSqr(Vec3.atCenterOf(turtle.getPosition())) > TurtleTarget.REACH * TurtleTarget.REACH) continue;
                    if (supportHit.getType() == HitResult.Type.MISS || !supportHit.getBlockPos().equals(support) || supportHit.getDirection() != supportFace) continue;
                    var context = new BlockPlaceContext(new UseOnContext(player.player(), InteractionHand.MAIN_HAND, supportHit));
                    if (context.getClickedPos().equals(pos) && context.canPlace() && !player.isBlockProtected(level, support)) {
                        placement = supportHit;
                        break;
                    }
                }
                // Replace a visible replaceable block, such as grass, directly.
                if (placement == null && !level.isEmptyBlock(pos) && (face == null || hit.getDirection() == face)) {
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
            var containerAction = action == Action.LIST || action == Action.PUSH || action == Action.PULL;
            if (containerAction) {
                if (player.player() instanceof TurtleMenuAccess menus) menus.setTurtleMenusAllowed(true);
                var permission = PlatformHelper.get().useOn(player.player(), stack, hit);
                if (permission instanceof PlatformHelper.UseOnResult.Continue allowed && allowed.block() && (!options.sneak() || stack.isEmpty())) {
                    var state = level.getBlockState(pos);
                    var result = state.useItemOn(stack, level, player.player(), InteractionHand.MAIN_HAND, hit);
                    if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) state.useWithoutItem(level, player.player(), hit);
                }
                return transferContainer(player.player());
            }
            var permission = PlatformHelper.get().useOn(player.player(), stack, hit);
            boolean success;
            if (permission instanceof PlatformHelper.UseOnResult.Handled handled) {
                success = handled.result().consumesAction();
            } else {
                var allowed = (PlatformHelper.UseOnResult.Continue) permission;
                success = false;
                if (action == Action.USE && allowed.block() && (!options.sneak() || stack.isEmpty())) {
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
            if (player.player() instanceof TurtleMenuAccess menus) menus.setTurtleMenusAllowed(false);
            if (player.player().containerMenu != player.player().inventoryMenu) player.player().doCloseContainer();
            player.unloadInventory(turtle);
            player.player().setShiftKeyDown(false);
        }
    }

    private TurtleCommandResult transferContainer(net.minecraft.server.level.ServerPlayer player) {
        var menu = player.containerMenu;
        if (menu == player.inventoryMenu || !menu.stillValid(player)) return TurtleCommandResult.failure("Block did not open an accessible container");
        var slots = menu.slots.stream().filter(entry -> entry.container != player.getInventory()).toList();
        if (slot > slots.size()) return TurtleCommandResult.failure("Container slot is out of range");
        if (action == Action.LIST) {
            var contents = new java.util.HashMap<Integer, Object>();
            for (var i = 0; i < slots.size(); i++) {
                var item = slots.get(i).getItem();
                if (!item.isEmpty()) contents.put(i + 1, dan200.computercraft.api.detail.VanillaDetailRegistries.ITEM_STACK.getDetails(item));
            }
            return TurtleCommandResult.success(new Object[]{ java.util.Map.of("size", slots.size(), "items", contents) });
        }
        var moved = 0;
        for (var i = 0; i < slots.size() && moved < count; i++) {
            if (slot != 0 && slot != i + 1) continue;
            var entry = slots.get(i);
            if (action == Action.PUSH) {
                var held = player.getMainHandItem();
                var before = held.getCount();
                entry.safeInsert(held, count - moved);
                moved += before - held.getCount();
            } else {
                var item = entry.getItem();
                if (item.isEmpty()) continue;
                var capacity = 0;
                for (var j = 0; j < 16; j++) {
                    var stored = player.getInventory().getItem(j);
                    if (stored.isEmpty()) {
                        capacity += item.getMaxStackSize();
                    } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(stored, item)) {
                        capacity += Math.max(0, stored.getMaxStackSize() - stored.getCount());
                    }
                }
                if (capacity == 0 || (!entry.allowModification(player) && count - moved < item.getCount())) continue;
                var taken = entry.safeTake(count - moved, capacity, player);
                moved += taken.getCount();
                for (var j = 0; j < 16 && !taken.isEmpty(); j++) {
                    var stored = player.getInventory().getItem(j);
                    if (stored.isEmpty()) player.getInventory().setItem(j, taken.split(Math.min(taken.getCount(), taken.getMaxStackSize())));
                    else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(stored, taken)) {
                        var transfer = Math.min(taken.getCount(), Math.max(0, stored.getMaxStackSize() - stored.getCount()));
                        stored.grow(transfer);
                        taken.shrink(transfer);
                    }
                }
                // Modded onTake callbacks may change the inventory after the capacity check.
                if (!taken.isEmpty()) player.drop(taken, false);
            }
        }
        menu.broadcastChanges();
        return moved > 0 || count == 0 ? TurtleCommandResult.success(new Object[]{ moved }) : TurtleCommandResult.failure("No items could be transferred");
    }

    private static boolean placementAllowed(ITurtleAccess turtle, TurtlePlayer player, BlockHitResult hit) {
        var level = (ServerLevel) turtle.getLevel();
        var destination = new BlockPlaceContext(new UseOnContext(player.player(), InteractionHand.MAIN_HAND, hit)).getClickedPos();
        return level.isInWorldBounds(destination) && level.hasChunkAt(destination)
            && TurtleTarget.inReach(turtle, destination) && !player.isBlockProtected(level, destination);
    }

}
