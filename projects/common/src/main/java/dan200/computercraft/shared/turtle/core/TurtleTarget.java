// SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** Geometry shared by vision and targeted interactions. Never loads chunks. */
public final class TurtleTarget {
    public static final double REACH = 4.5;

    private TurtleTarget() {
    }

    public static BlockPos position(ITurtleAccess turtle, int x, int y, int z) {
        return turtle.getPosition().relative(turtle.getDirection().getClockWise(), x).above(y).relative(turtle.getDirection(), z);
    }

    public static boolean inReach(ITurtleAccess turtle, BlockPos pos) {
        return !pos.equals(turtle.getPosition()) && pos.distSqr(turtle.getPosition()) <= REACH * REACH;
    }

    public static BlockGetter view(ITurtleAccess turtle) {
        var level = turtle.getLevel();
        var origin = turtle.getPosition();
        return new BlockGetter() {
            @Override
            public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }
            @Override
            public BlockState getBlockState(BlockPos pos) {
                if (pos.equals(origin)) return Blocks.AIR.defaultBlockState();
                return level.isInWorldBounds(pos) && level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.BARRIER.defaultBlockState();
            }
            @Override
            public FluidState getFluidState(BlockPos pos) {
                return Fluids.EMPTY.defaultFluidState();
            }
            @Override
            public int getHeight() {
                return level.getHeight();
            }
            @Override
            public int getMinBuildHeight() {
                return level.getMinBuildHeight();
            }
        };
    }

    public static BlockHitResult ray(ITurtleAccess turtle, BlockGetter view, Vec3 end) {
        return view.clip(new ClipContext(Vec3.atCenterOf(turtle.getPosition()), end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
    }

    public static @Nullable BlockHitResult visible(ITurtleAccess turtle, BlockGetter view, BlockPos pos) {
        if (!turtle.getLevel().isInWorldBounds(pos) || !turtle.getLevel().hasChunkAt(pos)) return null;
        // Sample the actual outline bounds, so short crops and slabs must really be visible.
        var shape = view.getBlockState(pos).getShape(view, pos);
        var bounds = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        for (int i = -1; i < Direction.values().length; i++) {
            var end = bounds.getCenter();
            if (i >= 0) {
                var direction = Direction.values()[i];
                end = end.add(direction.getStepX() * bounds.getXsize() * 0.499,
                    direction.getStepY() * bounds.getYsize() * 0.499,
                    direction.getStepZ() * bounds.getZsize() * 0.499);
            }
            var hit = ray(turtle, view, end.add(Vec3.atLowerCornerOf(pos)));
            if ((shape.isEmpty() && hit.getType() == HitResult.Type.MISS) || (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos))) {
                return new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false);
            }
        }
        return null;
    }
}
