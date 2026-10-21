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
    private boolean entityDirty;
    private boolean notified;
    private @Nullable Level level;
    private BlockPos origin = BlockPos.ZERO;
    private Direction facing = Direction.NORTH;
    private long loadedChunks;
    private @Nullable Map<String, Object> snapshot;
    private @Nullable Map<String, Object> entitySnapshot;

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

    public static void entityChanged(net.minecraft.world.entity.Entity entity, double x, double y, double z) {
        var level = entity.level();
        if (level == null || level.isClientSide) return;
        synchronized (WATCHERS) {
            for (var watcher : WATCHERS) {
                synchronized (watcher) {
                    if (watcher.level != level) continue;
                    var origin = net.minecraft.world.phys.Vec3.atCenterOf(watcher.origin);
                    var range = watcher.radius + 4.0;
                    if (origin.distanceToSqr(entity.position()) <= range * range || origin.distanceToSqr(x, y, z) <= range * range) watcher.entityDirty = true;
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
                entitySnapshot = null;
                dirty = false;
                entityDirty = false;
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
        if ((dirty || entityDirty) && !notified) {
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
                if (!dirty && snapshot != null) {
                    if (entityDirty) {
                        if (!notified) revision++;
                        snapshot = new HashMap<>(snapshot);
                        snapshot.put("revision", revision);
                        entityDirty = false;
                        notified = false;
                    }
                    return withEntities(snapshot);
                }
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
                entityDirty = false;
                notified = false;
                return withEntities(snapshot);
            }
        }
    }

    private Map<String, Object> withEntities(Map<String, Object> blocks) {
        var result = new HashMap<>(blocks);
        var entities = new ArrayList<Map<String, Object>>();
        var origin = net.minecraft.world.phys.Vec3.atCenterOf(turtle.getPosition());
        var right = turtle.getDirection().getClockWise();
        var forward = turtle.getDirection();
        for (var entity : turtle.getLevel().getEntities((net.minecraft.world.entity.Entity) null,
            new net.minecraft.world.phys.AABB(origin, origin).inflate(radius), entity -> entity.isAlive() && !entity.isSpectator())) {
            if (TurtleTarget.visibleEntity(turtle, entity, radius) == null) continue;
            var details = new HashMap<>(VanillaDetailRegistries.ENTITY.getDetails(entity));
            var delta = entity.position().subtract(origin);
            details.put("uuid", entity.getUUID().toString());
            details.put("x", delta.x * right.getStepX() + delta.z * right.getStepZ());
            details.put("y", delta.y);
            details.put("z", delta.x * forward.getStepX() + delta.z * forward.getStepZ());
            details.put("kind", entity instanceof net.minecraft.world.entity.player.Player ? "player"
                : entity instanceof net.minecraft.world.entity.item.ItemEntity ? "item"
                : entity instanceof net.minecraft.world.entity.LivingEntity ? "living" : "entity");
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) details.put("item", VanillaDetailRegistries.ITEM_STACK.getDetails(item.getItem()));
            entities.add(details);
        }
        result.put("entities", entities);
        if (entitySnapshot != null && entitySnapshot.equals(result)) return entitySnapshot;
        entitySnapshot = result;
        return result;
    }

}
