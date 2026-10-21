// SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.gametest.core;

import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.core.TurtleInteractAtCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("cctest")
@PrefixGameTestTemplate(false)
public class TargetedProtectionTest {
    @GameTest(template = "turtle_test.vision_changes_and_occlusion")
    public void deniedInteractions(GameTestHelper helper) {
        var turtle = ((TurtleBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4))).getAccess();
        var target = new BlockPos(4, 2, 5);
        helper.setBlock(target, Blocks.EMERALD_BLOCK);
        turtle.getInventory().setItem(0, new ItemStack(Items.STONE, 2));
        var dig = new TurtleInteractAtCommand(0, -1, 1, TurtleInteractAtCommand.Action.DIG).execute(turtle);
        helper.assertTrue(!dig.isSuccess(), "Break event cancellation must reject digAt");
        var use = new TurtleInteractAtCommand(0, -1, 1, TurtleInteractAtCommand.Action.USE).execute(turtle);
        helper.assertTrue(!use.isSuccess(), "Right-click event cancellation must reject useAt");
        var place = new TurtleInteractAtCommand(0, 0, 1, TurtleInteractAtCommand.Action.PLACE).execute(turtle);
        helper.assertTrue(!place.isSuccess(), "Right-click event cancellation must reject placeAt");
        helper.assertTrue("Interaction denied or had no effect".equals(place.getErrorMessage()), "Placement must reach the protection hook");
        helper.assertBlockPresent(Blocks.EMERALD_BLOCK, target);
        helper.assertBlockPresent(Blocks.AIR, target.above());
        helper.assertTrue(turtle.getInventory().getItem(0).getCount() == 2, "Denied actions must preserve items");
        helper.succeed();
    }
}
