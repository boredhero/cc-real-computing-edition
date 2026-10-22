// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.api.turtle;

import dan200.computercraft.api.lua.LuaException;

import java.util.Map;
import java.util.Set;

/**
 * Server-side controls shared by a machine's GUI and turtles. Register once during mod setup with
 * {@link dan200.computercraft.api.ComputerCraftAPI#registerTurtleInteraction}.
 * All callbacks run on the server thread after reach, visibility and platform interaction checks.
 * Implementations must additionally enforce their machine's ownership, locks and action-specific permissions.
 * Do not retain the context or its player. Discovery methods must not mutate the world.
 */
public interface TurtleInteraction {
    /**
     * Whether this adapter supports the target and allows this player to inspect it.
     * @param context The current interaction.
     * @return Whether discovery/actions may proceed.
     */
    boolean supports(TurtleInteractionContext context);

    /**
     * Read the machine's public state. Return Lua-compatible values, not live mutable machine data.
     * @param context The current interaction.
     * @return A table of state, using strings, finite numbers, booleans, maps and lists.
     * @throws LuaException If the state cannot be read.
     */
    Map<String, ?> inspect(TurtleInteractionContext context) throws LuaException;

    /**
     * List the currently available action names. These are checked again before execution.
     * @param context The current interaction.
     * @return Available actions, for example "start" or "set_recipe".
     */
    Set<String> actions(TurtleInteractionContext context);

    /**
     * Execute one named action, using the same server-side logic as your GUI packet handler.
     * Validate every argument and permission before mutating. Exceptions do not roll back mutations.
     * @param context The current interaction.
     * @param action An action from {@link #actions}.
     * @param arguments An immutable, bounded snapshot of the Lua argument table.
     * @return A Lua-compatible result table (an empty map is allowed).
     * @throws LuaException A user-facing rejection reason.
     */
    Map<String, ?> execute(TurtleInteractionContext context, String action, Map<?, ?> arguments) throws LuaException;
}
