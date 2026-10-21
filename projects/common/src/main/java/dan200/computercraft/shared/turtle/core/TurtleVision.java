// SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.detail.BlockReference;
import dan200.computercraft.api.detail.VanillaDetailRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.LongConsumer;

/** Cached block observations, invalidated by actual block mutations rather than periodic scans. */
public final class TurtleVision {
    public static final int MAX_RADIUS = 8;
    private static final Set<TurtleVision> WATCHERS = Collections.newSetFromMap(new WeakHashMap<>());
    private final TurtleAccessInternal turtle;
    private int radius;
    private long revision;
    private boolean dirty;
    private boolean notified;
    private @Nullable Level level;
    private BlockPos origin = BlockPos.ZERO;
    private Direction facing = Direction.NORTH;
    private long loadedChunks;
    private @Nullable Map<String, Object> snapshot;

    public TurtleVision(TurtleAccessInternal turtle) {
        this.turtle = turtle;
    }

    public static void blockChanged(Level level, BlockPos pos) {
        synchronized (WATCHERS) {
            for (var watcher : WATCHERS) {
                synchronized (watcher) {
                    if (watcher.level == level && pos.distSqr(watcher.origin) <= (watcher.radius + 1) * (watcher.radius + 1)) watcher.dirty = true;
                }
            }
        }
    }

    public void reset() {
        synchronized (WATCHERS) {
            WATCHERS.remove(this);
            synchronized (this) {
                radius = 0;
                level = null;
                snapshot = null;
                dirty = false;
                notified = false;
            }
        }
    }

    private long loadedChunks() {
        long bits = 0;
        int index = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (turtle.getLevel().hasChunkAt(turtle.getPosition().offset(x * 16, 0, z * 16))) bits |= 1L << index;
                index++;
            }
        }
        return bits;
    }

    private void checkPose() {
        var chunks = loadedChunks();
        if (level != turtle.getLevel() || !origin.equals(turtle.getPosition()) || facing != turtle.getDirection() || chunks != loadedChunks) {
            level = turtle.getLevel();
            origin = turtle.getPosition().immutable();
            facing = turtle.getDirection();
            loadedChunks = chunks;
            dirty = true;
        }
    }

    public synchronized void update(LongConsumer notify) {
        if (radius == 0 || turtle.isRemoved()) return;
        checkPose();
        if (dirty && !notified) {
            notified = true;
            notify.accept(++revision);
        }
    }

    public Map<String, Object> observe(int requestedRadius) {
        synchronized (WATCHERS) {
            WATCHERS.add(this);
            synchronized (this) {
                checkPose();
                if (radius != requestedRadius) {
                    radius = requestedRadius;
                    dirty = true;
                }
                if (!dirty && snapshot != null) return snapshot;
                var blocks = new ArrayList<Map<String, Object>>();
                var view = TurtleTarget.view(turtle);
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (x * x + y * y + z * z > radius * radius || (x == 0 && y == 0 && z == 0)) continue;
                            var pos = TurtleTarget.position(turtle, x, y, z);
                            if (!turtle.getLevel().isInWorldBounds(pos) || !turtle.getLevel().hasChunkAt(pos) || turtle.getLevel().isEmptyBlock(pos)) continue;
                            if (TurtleTarget.visible(turtle, view, pos) == null) continue;
                            var details = new HashMap<>(VanillaDetailRegistries.BLOCK_IN_WORLD.getDetails(new BlockReference(turtle.getLevel(), pos)));
                            details.put("x", x);
                            details.put("y", y);
                            details.put("z", z);
                            blocks.add(details);
                        }
                    }
                }
                if (!notified) revision++;
                snapshot = Map.of("revision", revision, "radius", radius, "facing", facing.getName(), "blocks", blocks);
                dirty = false;
                notified = false;
                return snapshot;
            }
        }
    }
}
