// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.gametest.core;

import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.blocks.ComputerPeripheral;
import dan200.computercraft.shared.pocket.core.PocketHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@GameTestHolder("cctest")
@PrefixGameTestTemplate(false)
public class IdentityTest {
    @GameTest(template = "default")
    public void persistentIdentityAndCopies(GameTestHelper helper) throws IOException {
        var firstPos = new BlockPos(1, 2, 1);
        var secondPos = new BlockPos(3, 2, 1);
        var movedPos = new BlockPos(1, 2, 3);
        helper.setBlock(firstPos, ModRegistry.Blocks.COMPUTER_NORMAL.get());
        var first = (AbstractComputerBlockEntity) helper.getBlockEntity(firstPos);
        var uuid = first.getComputerUUID();
        var runtime = first.createServerComputer();
        var numeric = first.getComputerID();
        var originalDisk = runtime.createRootMount();
        try (var channel = originalDisk.openFile("data", dan200.computercraft.api.filesystem.MountConstants.WRITE_OPTIONS)) {
            channel.write(ByteBuffer.wrap("original".getBytes(StandardCharsets.UTF_8)));
        }
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
        helper.assertTrue(numeric == second.getComputerID(), "Numeric alias remains compatible");

        var cloneDisk = second.createServerComputer().createRootMount();
        helper.assertTrue(cloneDisk.exists("data"), "Clone inherits original UUID disk files");
        cloneDisk.delete("data");
        helper.assertTrue(originalDisk.exists("data"), "Clone deletion does not affect original storage");

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

    @GameTest(template = "default")
    public void pocketIdentityTransferCopyAndReload(GameTestHelper helper) throws IOException {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var item = ModRegistry.Items.POCKET_COMPUTER_NORMAL.get();
        var entity = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(item));
        helper.getLevel().addFreshEntity(entity);
        var holder = new PocketHolder.ItemEntityHolder(entity);
        var brain = item.getOrCreateBrain(holder, entity.getItem());
        var uuid = java.util.Objects.requireNonNull(brain.computer().getComputerUUID());
        helper.assertTrue(uuid.equals(entity.getItem().get(ModRegistry.DataComponents.COMPUTER_UUID.get())), "Pocket has persistent UUID");
        var disk = brain.computer().createRootMount();
        try (var channel = disk.openFile("data", dan200.computercraft.api.filesystem.MountConstants.WRITE_OPTIONS)) {
            channel.write(ByteBuffer.wrap("pocket".getBytes(StandardCharsets.UTF_8)));
        }

        var copiedEntity = new ItemEntity(helper.getLevel(), pos.getX() + 1, pos.getY(), pos.getZ(), entity.getItem().copy());
        helper.getLevel().addFreshEntity(copiedEntity);
        var copy = item.getOrCreateBrain(new PocketHolder.ItemEntityHolder(copiedEntity), copiedEntity.getItem());
        helper.assertTrue(!uuid.equals(copy.computer().getComputerUUID()), "Copied runtime reference gets independent identity");
        helper.assertTrue(brain != copy, "Copy does not steal the original running computer");
        var copyDisk = copy.computer().createRootMount();
        helper.assertTrue(copyDisk.exists("data"), "Pocket copy inherits files");
        copyDisk.delete("data");
        helper.assertTrue(disk.exists("data"), "Pocket copy has independent storage");

        var transferred = entity.getItem().copy();
        entity.setItem(ItemStack.EMPTY);
        var newEntity = new ItemEntity(helper.getLevel(), pos.getX() + 2, pos.getY(), pos.getZ(), transferred);
        helper.getLevel().addFreshEntity(newEntity);
        var newHolder = new PocketHolder.ItemEntityHolder(newEntity);
        var moved = item.getOrCreateBrain(newHolder, newEntity.getItem());
        helper.assertTrue(moved == brain, "Transfer retains running computer");
        brain.computer().close();
        var saved = newEntity.getItem().save(helper.getLevel().registryAccess());
        newEntity.setItem(ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) saved));
        var reloaded = item.getOrCreateBrain(newHolder, newEntity.getItem());
        helper.assertTrue(uuid.equals(reloaded.computer().getComputerUUID()), "Item save/reload preserves UUID");
        helper.assertTrue(reloaded.computer().createRootMount().exists("data"), "Reload preserves UUID files");
        helper.succeed();
    }

    @GameTest(template = "default")
    public void offlinePocketCopiesHaveIndependentStorage(GameTestHelper helper) throws IOException {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var item = ModRegistry.Items.POCKET_COMPUTER_NORMAL.get();
        var firstEntity = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(item));
        helper.getLevel().addFreshEntity(firstEntity);
        var firstHolder = new PocketHolder.ItemEntityHolder(firstEntity);
        var first = item.getOrCreateBrain(firstHolder, firstEntity.getItem());
        var uuid = first.computer().getComputerUUID();
        var disk = first.computer().createRootMount();
        try (var channel = disk.openFile("data", dan200.computercraft.api.filesystem.MountConstants.WRITE_OPTIONS)) {
            channel.write(ByteBuffer.wrap("offline".getBytes(StandardCharsets.UTF_8)));
        }
        first.computer().close();
        var saved = (net.minecraft.nbt.CompoundTag) firstEntity.getItem().save(helper.getLevel().registryAccess());
        firstEntity.setItem(ItemStack.parseOptional(helper.getLevel().registryAccess(), saved));
        var restarted = item.getOrCreateBrain(firstHolder, firstEntity.getItem());
        helper.assertTrue(java.util.Objects.equals(uuid, restarted.computer().getComputerUUID()), "First activation preserves UUID");
        restarted.computer().close();
        firstEntity.discard();

        // Neither source runtime is alive when a separately deserialised offline copy starts.
        var secondEntity = new ItemEntity(helper.getLevel(), pos.getX() + 1, pos.getY(), pos.getZ(), ItemStack.parseOptional(helper.getLevel().registryAccess(), saved));
        helper.getLevel().addFreshEntity(secondEntity);
        var second = item.getOrCreateBrain(new PocketHolder.ItemEntityHolder(secondEntity), secondEntity.getItem());
        helper.assertTrue(!java.util.Objects.equals(uuid, second.computer().getComputerUUID()), "Offline copy gets independent UUID");
        var copyDisk = second.computer().createRootMount();
        helper.assertTrue(copyDisk.exists("data"), "Offline copy inherits files");
        copyDisk.delete("data");
        helper.assertTrue(disk.exists("data"), "Offline copy cannot delete source files");
        second.computer().close();
        var explicit = dan200.computercraft.api.ComputerCraftAPI.copyPocketComputer(helper.getLevel(), secondEntity.getItem());
        var thirdEntity = new ItemEntity(helper.getLevel(), pos.getX() + 2, pos.getY(), pos.getZ(), explicit);
        helper.getLevel().addFreshEntity(thirdEntity);
        var third = item.getOrCreateBrain(new PocketHolder.ItemEntityHolder(thirdEntity), thirdEntity.getItem());
        helper.assertTrue(!java.util.Objects.equals(second.computer().getComputerUUID(), third.computer().getComputerUUID()), "Explicit copy is independent before either copy reactivates");
        helper.succeed();
    }

    @GameTest(template = "default")
    public void pocketTransferInvalidatesOfflineCopies(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var item = ModRegistry.Items.POCKET_COMPUTER_NORMAL.get();
        var original = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(item));
        helper.getLevel().addFreshEntity(original);
        var brain = item.getOrCreateBrain(new PocketHolder.ItemEntityHolder(original), original.getItem());
        var uuid = brain.computer().getComputerUUID();
        var snapshot = original.getItem().copy();
        var transferred = original.getItem().copy();
        original.setItem(ItemStack.EMPTY);
        var moved = new ItemEntity(helper.getLevel(), pos.getX() + 1, pos.getY(), pos.getZ(), transferred);
        helper.getLevel().addFreshEntity(moved);
        helper.assertTrue(item.getBrain(new PocketHolder.ItemEntityHolder(moved), moved.getItem()) == brain, "Transfer keeps live runtime");
        helper.assertTrue(java.util.Objects.equals(uuid, moved.getItem().get(ModRegistry.DataComponents.COMPUTER_UUID.get())), "Transfer keeps UUID");
        helper.assertTrue(!java.util.Objects.equals(snapshot.get(ModRegistry.DataComponents.POCKET_TOKEN.get()), moved.getItem().get(ModRegistry.DataComponents.POCKET_TOKEN.get())), "Transfer rotates token even without a new runtime");
        brain.computer().close();
        moved.discard();
        var copied = new ItemEntity(helper.getLevel(), pos.getX() + 2, pos.getY(), pos.getZ(), snapshot);
        helper.getLevel().addFreshEntity(copied);
        var clone = item.getOrCreateBrain(new PocketHolder.ItemEntityHolder(copied), copied.getItem());
        helper.assertTrue(!java.util.Objects.equals(uuid, clone.computer().getComputerUUID()), "Pre-transfer saved copy splits with no source runtime alive");
        helper.succeed();
    }

}
