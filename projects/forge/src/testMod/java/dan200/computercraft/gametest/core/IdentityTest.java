// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.gametest.core;

import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.blocks.ComputerPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("cctest")
@PrefixGameTestTemplate(false)
public class IdentityTest {
    @GameTest(template = "default")
    public void persistentIdentityAndCopies(GameTestHelper helper) {
        var firstPos = new BlockPos(1, 2, 1);
        var secondPos = new BlockPos(3, 2, 1);
        var movedPos = new BlockPos(1, 2, 3);
        helper.setBlock(firstPos, ModRegistry.Blocks.COMPUTER_NORMAL.get());
        var first = (AbstractComputerBlockEntity) helper.getBlockEntity(firstPos);
        var uuid = first.getComputerUUID();
        var runtime = first.createServerComputer();
        var numeric = first.getComputerID();
        helper.assertTrue(uuid.equals(runtime.getComputerUUID()), "Runtime uses persistent UUID");
        helper.assertTrue(!uuid.equals(runtime.getInstanceUUID()), "Persistent and runtime identities differ");
        helper.assertTrue(uuid.toString().equals(new ComputerPeripheral("computer", first).getUUID()), "Peripheral exposes UUID");

        var saved = first.saveWithFullMetadata(helper.getLevel().registryAccess());
        first.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(uuid.equals(first.getComputerUUID()), "NBT reload preserves UUID");
        helper.assertTrue(numeric == first.getComputerID(), "Migration preserves numeric ID");

        var copy = new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get());
        copy.applyComponents(first.collectComponents());
        helper.setBlock(secondPos, ModRegistry.Blocks.COMPUTER_NORMAL.get());
        var second = (AbstractComputerBlockEntity) helper.getBlockEntity(secondPos);
        second.applyComponentsFromItemStack(copy);
        var other = second.getComputerUUID();
        helper.assertTrue(!uuid.equals(other), "Copied block gets a distinct UUID");
        helper.assertTrue(numeric == second.getComputerID(), "Legacy copy storage semantics are unchanged");

        var drops = Block.getDrops(first.getBlockState(), helper.getLevel(), first.getBlockPos(), first);
        helper.assertTrue(drops.size() == 1, "Computer drops exactly one item");
        var item = drops.getFirst();
        helper.assertTrue(uuid.equals(item.get(ModRegistry.DataComponents.COMPUTER_UUID.get())), "Drop retains UUID component");
        helper.setBlock(firstPos, Blocks.AIR);
        helper.setBlock(movedPos, ModRegistry.Blocks.COMPUTER_NORMAL.get());
        var restored = (AbstractComputerBlockEntity) helper.getBlockEntity(movedPos);
        restored.applyComponentsFromItemStack(item);
        helper.assertTrue(uuid.equals(restored.getComputerUUID()), "Pickup and replacement preserve UUID");
        helper.assertTrue(other.equals(second.getComputerUUID()), "Other identity remains distinct");
        helper.succeed();
    }
}
