// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.mixin.LivingEntityUseAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Tick-driven held item use. Inventory contents are synchronised back after every tick. */
public final class TurtleItemUse {
    private final TurtleBrain turtle;
    private @Nullable TurtlePlayer player;
    private volatile boolean cancelRequested;
    private int remaining;
    private int elapsed;
    private net.minecraft.core.BlockPos origin = net.minecraft.core.BlockPos.ZERO;
    private net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
    private int selectedSlot;

    public TurtleItemUse(TurtleBrain turtle) {
        this.turtle = turtle;
    }

    public boolean active() {
        return player != null;
    }

    public void requestCancel() {
        cancelRequested = true;
    }

    public TurtleCommandResult start(int ticks, float yaw, float pitch, boolean sneak) {
        if (active()) return TurtleCommandResult.failure("Already using an item");
        var actor = TurtlePlayer.get(turtle);
        actor.player().setShiftKeyDown(sneak);
        var direction = Vec3.directionFromRotation(pitch, turtle.getDirection().toYRot() + yaw);
        actor.aimAt(turtle, Vec3.atCenterOf(turtle.getPosition()).add(direction));
        // Put the muzzle just outside the turtle, otherwise arrows and air-use rays hit its own block.
        var muzzle = direction.scale(0.501 / Math.max(Math.abs(direction.x), Math.max(Math.abs(direction.y), Math.abs(direction.z))));
        actor.player().setPos(actor.player().position().add(muzzle));
        if (actor.isBlockProtected(actor.player().serverLevel(), turtle.getPosition())) return TurtleCommandResult.failure("Cannot use items in protected area");
        actor.loadInventory(turtle);
        try {
            var result = actor.player().gameMode.useItem(actor.player(), turtle.getLevel(), actor.player().getMainHandItem(), InteractionHand.MAIN_HAND);
            if (!result.consumesAction()) return TurtleCommandResult.failure("Item use denied or had no effect");
            elapsed = 0;
            remaining = ticks;
            cancelRequested = false;
            origin = turtle.getPosition().immutable();
            facing = turtle.getDirection();
            selectedSlot = turtle.getSelectedSlot();
            if (actor.player().isUsingItem()) player = actor;
            else notifyFinished("completed");
            return TurtleCommandResult.success();
        } finally {
            actor.unloadInventory(turtle, !active());
            if (!active()) actor.player().setShiftKeyDown(false);
        }
    }

    public void tick() {
        var actor = player;
        if (actor == null) return;
        if (cancelRequested || turtle.isRemoved() || turtle.getLevel() != actor.player().level()
            || !turtle.getPosition().equals(origin) || turtle.getDirection() != facing || turtle.getSelectedSlot() != selectedSlot) {
            cancel();
            return;
        }
        actor.loadInventory(turtle);
        try {
            ((LivingEntityUseAccess) actor.player()).computercraft$tickItemUse();
            elapsed++;
            if (!actor.player().isUsingItem()) finish("completed");
            else if (--remaining <= 0) {
                actor.player().releaseUsingItem();
                finish("released");
            }
        } catch (RuntimeException e) {
            actor.player().stopUsingItem();
            finish("failed");
            throw e;
        } finally {
            actor.unloadInventory(turtle, !active());
        }
    }

    public void cancel() {
        var actor = player;
        if (actor == null) return;
        actor.loadInventory(turtle);
        try {
            actor.player().stopUsingItem();
            finish("cancelled");
        } finally {
            actor.unloadInventory(turtle);
        }
    }

    private void finish(String reason) {
        if (player != null) player.player().setShiftKeyDown(false);
        player = null;
        notifyFinished(reason);
    }

    private void notifyFinished(String reason) {
        var computer = turtle.getOwner().getServerComputer();
        if (computer != null) computer.queueEvent("turtle_item_use", new Object[]{ reason, elapsed });
    }
}
