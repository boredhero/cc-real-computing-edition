// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.client.gui;

import dan200.computercraft.client.gui.widgets.TerminalWidget;
import dan200.computercraft.core.input.ComputerInput;
import dan200.computercraft.core.input.UserComputerInput;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.test.shared.WithMinecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@WithMinecraft
class TerminalWidgetTest {
    @Test
    void scaledMouseCoordinatesMatchRenderedCells() {
        var events = new ArrayList<String>();
        var input = (ComputerInput) Proxy.newProxyInstance(ComputerInput.class.getClassLoader(), new Class<?>[]{ ComputerInput.class }, (proxy, method, args) -> {
            events.add(method.getName() + ":" + java.util.Arrays.toString(args));
            return null;
        });
        var menu = new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }
            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        var terminal = new Terminal(47, 16, true);
        var widget = new TerminalWidget(terminal, new UserComputerInput(input, terminal), new ClientComputerActions(menu), 10, 20, 0.5f);
        // The margin is 2 pixels. Click the centres of the first and last cells.
        assertTrue(widget.mouseClicked(10 + (2 + 3) * 0.5, 20 + (2 + 4.5) * 0.5, 0));
        assertTrue(widget.mouseDragged(10 + (2 + 46 * 6 + 3) * 0.5, 20 + (2 + 15 * 9 + 4.5) * 0.5, 0, 0, 0));
        assertTrue(widget.mouseReleased(10 + (2 + 46 * 6 + 3) * 0.5, 20 + (2 + 15 * 9 + 4.5) * 0.5, 0));
        assertTrue(widget.mouseScrolled(12.5, 23.25, 0, -1));
        assertFalse(widget.mouseClicked(9, 20, 0));
        assertEquals(java.util.List.of("mouseClick:[1, 1, 1]", "mouseDrag:[1, 47, 16]", "mouseUp:[1, 47, 16]", "mouseScroll:[1, 1, 1]"), events);
        assertEquals(143, widget.getWidth());
        assertEquals(74, widget.getHeight());
    }
}
