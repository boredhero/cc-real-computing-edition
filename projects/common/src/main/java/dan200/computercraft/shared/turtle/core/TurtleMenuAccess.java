// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.turtle.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;

/** Menu creation is enabled only for the duration of a targeted inventory command. */
public interface TurtleMenuAccess {
    void setTurtleMenusAllowed(boolean allowed);

    static OptionalInt open(ServerPlayer player, @Nullable MenuProvider provider) {
        if (provider == null) return OptionalInt.empty();
        if (player.containerMenu != player.inventoryMenu) player.doCloseContainer();
        // The provider enforces locks and initialises the real menu, including output slots.
        var menu = provider.createMenu(1, player.getInventory(), player);
        if (menu == null) return OptionalInt.empty();
        player.containerMenu = menu;
        return OptionalInt.of(menu.containerId);
    }
}
