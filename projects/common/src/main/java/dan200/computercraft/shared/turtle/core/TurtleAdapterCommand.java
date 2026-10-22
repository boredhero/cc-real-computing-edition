// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.*;
import dan200.computercraft.impl.TurtleInteractions;
import dan200.computercraft.shared.platform.PlatformHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named adapter call, or discovery when adapter is null. All target checks occur at execution time.
 * @param x Right offset.
 * @param y Up offset.
 * @param z Forward offset.
 * @param adapter Adapter ID, or null for discovery.
 * @param action Action name (unused for discovery).
 * @param arguments Detached Lua arguments.
 * @param options Face and sneaking options.
 */
public record TurtleAdapterCommand(int x, int y, int z, @Nullable ResourceLocation adapter, String action,
                                   Map<?, ?> arguments, TurtleInteractionOptions options) implements TurtleCommand {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TurtleAdapterCommand.class);

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        var pos = TurtleTarget.position(turtle, x, y, z);
        if (!TurtleTarget.inReach(turtle, pos)) return TurtleCommandResult.failure("Target is out of reach");
        var level = (ServerLevel) turtle.getLevel();
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return TurtleCommandResult.failure("Target is not loaded");
        var hit = TurtleTarget.visibleFace(turtle, TurtleTarget.view(turtle), pos, options.direction(turtle));
        if (hit == null) return TurtleCommandResult.failure("Target is obstructed");
        if (hit.getLocation().distanceToSqr(Vec3.atCenterOf(turtle.getPosition())) > TurtleTarget.REACH * TurtleTarget.REACH) return TurtleCommandResult.failure("Target face is out of reach");
        var player = TurtlePlayer.get(turtle);
        player.aimAt(turtle, hit.getLocation());
        if (player.isBlockProtected(level, pos)) return TurtleCommandResult.failure("Cannot interact in protected area");
        player.player().setShiftKeyDown(options.sneak());
        player.loadInventory(turtle);
        try {
            if (!PlatformHelper.get().canUseBlock(player.player(), hit)) return TurtleCommandResult.failure("Interaction denied");
            var context = new TurtleInteractionContext(turtle, level, hit, player.player());
            var adapters = TurtleInteractions.snapshot();
            if (adapter == null) {
                var result = new LinkedHashMap<String, Object>();
                for (var entry : adapters.entrySet()) {
                    var handler = entry.getValue();
                    if (handler.supports(context)) {
                        result.put(entry.getKey().toString(), Map.of(
                            "actions", handler.actions(context).stream().sorted().toList(), "state", handler.inspect(context)));
                    }
                }
                return TurtleCommandResult.success(new Object[]{ TurtleInteractionData.copy(result) });
            }
            var handler = adapters.get(adapter);
            if (handler == null) return TurtleCommandResult.failure("Unknown interaction adapter");
            if (!handler.supports(context)) return TurtleCommandResult.failure("Adapter does not support or permit this target");
            if (!handler.actions(context).contains(action)) return TurtleCommandResult.failure("Action is not available");
            var result = TurtleInteractionData.copy(handler.execute(context, action, arguments));
            turtle.playAnimation(TurtleAnimation.WAIT);
            return TurtleCommandResult.success(new Object[]{ result });
        } catch (LuaException e) {
            return TurtleCommandResult.failure(e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Turtle interaction adapter failed at {} (adapter {}, action {})", pos, adapter, action, e);
            return TurtleCommandResult.failure("Interaction adapter failed; see server log");
        } finally {
            if (player.player().containerMenu != player.player().inventoryMenu) player.player().doCloseContainer();
            player.unloadInventory(turtle);
            player.player().setShiftKeyDown(false);
        }
    }
}
