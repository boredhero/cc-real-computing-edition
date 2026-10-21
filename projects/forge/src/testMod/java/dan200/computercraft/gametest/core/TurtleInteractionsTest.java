// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.gametest.core;

import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder("cctest")
@PrefixGameTestTemplate(false)
public class TurtleInteractionsTest {
    private static TurtleBrain turtle(GameTestHelper helper) {
        var turtle = ((TurtleBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4))).getAccess();
        turtle.getInventory().clearContent();
        return (TurtleBrain) turtle;
    }

    private static boolean sees(Map<String, Object> snapshot, java.util.UUID uuid) {
        return ((List<?>) java.util.Objects.requireNonNull(snapshot.get("entities"))).stream().anyMatch(entry -> uuid.toString().equals(((Map<?, ?>) entry).get("uuid")));
    }

    @GameTest(template = "turtle_interactions")
    public void entityVisionAndOcclusion(GameTestHelper helper) {
        var turtle = turtle(helper);
        var sheep = helper.spawn(EntityType.SHEEP, new Vec3(4.5, 3, 6.5));
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        var vision = new TurtleVision(turtle);
        var initial = vision.observe(4);
        helper.assertTrue(sees(initial, sheep.getUUID()), "Visible sheep has a stable UUID");
        var revision = new AtomicLong();
        sheep.setPos(sheep.position().add(0.1, 0, 0));
        vision.update(revision::set);
        helper.assertTrue(revision.get() > 0, "Entity movement invalidates subscribed vision");
        helper.assertTrue(initial.get("blocks") == vision.observe(4).get("blocks"), "Entity movement must reuse cached block observations");
        for (int x = 3; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
        }
        helper.assertTrue(!sees(vision.observe(4), sheep.getUUID()), "Walls hide entities");
        helper.assertTrue(!new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.USE, false).execute(turtle).isSuccess(), "Interaction cannot cross a wall");
        sheep.discard();
        helper.assertTrue(!new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.ATTACK, false).execute(turtle).isSuccess(), "Stale UUIDs fail safely");
        vision.reset();
        helper.succeed();
    }

    @GameTest(template = "turtle_interactions")
    public void feedsShearsAndCollects(GameTestHelper helper) {
        var turtle = turtle(helper);
        var sheep = helper.spawn(EntityType.SHEEP, new Vec3(4.5, 3, 6));
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        turtle.getInventory().setItem(0, new ItemStack(Items.WHEAT, 2));
        helper.assertTrue(new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.USE, false).execute(turtle).isSuccess(), "Feed sheep by UUID");
        helper.assertTrue(sheep.isInLove() && turtle.getInventory().getItem(0).getCount() == 1, "Feeding consumes one wheat");
        turtle.getInventory().setItem(0, new ItemStack(Items.SHEARS));
        helper.assertTrue(new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.USE, false).execute(turtle).isSuccess(), "Shear sheep by UUID");
        helper.assertTrue(sheep.isSheared(), "Sheep was sheared");
        helper.assertTrue(turtle.getInventory().getItem(0).getDamageValue() > 0, "Shears take normal durability damage");
        var item = new ItemEntity(helper.getLevel(), sheep.getX() + 0.5, sheep.getY(), sheep.getZ(), new ItemStack(Items.DIAMOND, 3));
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        helper.assertTrue(new TurtleEntityCommand(item.getUUID(), TurtleEntityCommand.Action.COLLECT, false).execute(turtle).isSuccess(), "Pick up dropped items");
        helper.assertTrue(item.isRemoved() && turtle.getInventory().countItem(Items.DIAMOND) == 3, "Pickup transfers exactly once");
        helper.succeed();
    }

    @GameTest(template = "turtle_interactions")
    public void entityProtectionAndAttack(GameTestHelper helper) {
        var turtle = turtle(helper);
        var sheep = helper.spawn(EntityType.SHEEP, new Vec3(4.5, 3, 6));
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        sheep.addTag("rce_protected");
        turtle.getInventory().setItem(0, new ItemStack(Items.WHEAT, 2));
        helper.assertTrue(!new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.USE, false).execute(turtle).isSuccess(), "Interact-at cancellation cannot fall through to feeding");
        helper.assertTrue(!sheep.isInLove() && turtle.getInventory().getItem(0).getCount() == 2, "Denied feeding preserves item and entity");
        turtle.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(new TurtleEquipCommand(TurtleSide.LEFT).execute(turtle).isSuccess(), "Equip sword");
        var health = sheep.getHealth();
        helper.assertTrue(!new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.ATTACK, false).execute(turtle).isSuccess(), "Attack cancellation is reported as failure");
        helper.assertTrue(sheep.getHealth() == health, "Denied attack does no damage");
        sheep.removeTag("rce_protected");
        helper.assertTrue(new TurtleEntityCommand(sheep.getUUID(), TurtleEntityCommand.Action.ATTACK, false).execute(turtle).isSuccess(), "Attack visible UUID");
        helper.assertTrue(sheep.getHealth() < health, "Attack damages target");
        helper.succeed();
    }

    @GameTest(template = "turtle_interactions")
    public void facesSneakingAndContainers(GameTestHelper helper) {
        var turtle = turtle(helper);
        var chestPos = new BlockPos(4, 3, 6);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        var options = new TurtleInteractionOptions("back", false);
        turtle.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));
        var push = new TurtleInteractAtCommand(0, 0, 2, TurtleInteractAtCommand.Action.PUSH, options, 5, 1).execute(turtle);
        helper.assertTrue(push.isSuccess(), "Push through the chest's menu: " + push.getErrorMessage());
        helper.assertTrue(chest.getItem(0).getCount() == 5 && turtle.getInventory().getItem(0).getCount() == 7, "Push obeys count and slot");
        var listing = new TurtleInteractAtCommand(0, 0, 2, TurtleInteractAtCommand.Action.LIST, options, 0, 0).execute(turtle);
        helper.assertTrue(listing.isSuccess(), "Read menu inventory");
        helper.assertTrue(new TurtleInteractAtCommand(0, 0, 2, TurtleInteractAtCommand.Action.PULL, options, 3, 1).execute(turtle).isSuccess(), "Pull through chest menu");
        helper.assertTrue(chest.getItem(0).getCount() == 2 && turtle.getInventory().getItem(0).getCount() == 10, "Pull preserves total items");
        var saved = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.putString("Lock", "secret-key");
        chest.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!new TurtleInteractAtCommand(0, 0, 2, TurtleInteractAtCommand.Action.LIST, options, 0, 0).execute(turtle).isSuccess(), "Locked chest requires its key");
        saved.remove("Lock");
        saved.putString("CustomName", "{\"text\":\"rce_protected\"}");
        chest.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!new TurtleInteractAtCommand(0, 0, 2, TurtleInteractAtCommand.Action.PUSH, options, 5, 1).execute(turtle).isSuccess(), "Protection blocks opening inventory for transfers");
        helper.assertTrue(chest.getItem(0).getCount() == 2 && turtle.getInventory().getItem(0).getCount() == 10, "Denied transfer preserves both inventories");
        helper.assertTrue(!new TurtleInteractAtCommand(0, 0, 2, TurtleInteractAtCommand.Action.LIST, new TurtleInteractionOptions("front", false), 0, 0).execute(turtle).isSuccess(), "Cannot click the hidden far face");
        var leverPos = new BlockPos(5, 3, 5);
        var lever = helper.getBlockState(leverPos);
        helper.assertTrue(lever.is(Blocks.LEVER), "Template has lever");
        var sneaking = new TurtleInteractionOptions(null, true);
        new TurtleInteractAtCommand(-1, 0, 1, TurtleInteractAtCommand.Action.USE, sneaking, 0, 0).execute(turtle);
        helper.assertTrue(!helper.getBlockState(leverPos).getValue(BlockStateProperties.POWERED), "Sneak with an item bypasses lever activation");
        helper.succeed();
    }

    @GameTest(template = "turtle_interactions", timeoutTicks = 100)
    public void holdsAndReleasesBow(GameTestHelper helper) {
        var turtle = turtle(helper);
        turtle.getInventory().setItem(0, new ItemStack(Items.BOW));
        turtle.getInventory().setItem(1, new ItemStack(Items.ARROW, 3));
        helper.assertTrue(turtle.itemUse.start(20, 0, -20, false).isSuccess(), "Start drawing bow");
        helper.assertTrue(turtle.itemUse.active(), "Held action is asynchronous");
        helper.runAfterDelay(5, () -> helper.assertTrue(turtle.itemUse.active() && turtle.getInventory().getItem(1).getCount() == 3, "Charging advances over real ticks"));
        helper.runAfterDelay(23, () -> {
            helper.assertTrue(!turtle.itemUse.active(), "Bow released after requested ticks");
            helper.assertTrue(turtle.getInventory().getItem(1).getCount() == 2, "Shot consumes one arrow");
            var box = new AABB(turtle.getPosition()).inflate(32);
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AbstractArrow.class, box).isEmpty(), "Released bow creates a projectile outside the turtle");
            helper.succeed();
        });
    }

    @GameTest(template = "turtle_interactions", timeoutTicks = 100)
    public void cancelsUseWithoutLosingInventory(GameTestHelper helper) {
        var turtle = turtle(helper);
        turtle.getInventory().setItem(0, new ItemStack(Items.BOW));
        turtle.getInventory().setItem(1, new ItemStack(Items.ARROW, 3));
        helper.assertTrue(turtle.itemUse.start(40, 0, 0, false).isSuccess(), "Start charge");
        helper.runAfterDelay(5, () -> {
            turtle.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 7));
            turtle.itemUse.requestCancel();
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!turtle.itemUse.active(), "Shutdown cancellation ends use");
            helper.assertTrue(turtle.getInventory().getItem(1).getCount() == 3, "Cancellation does not fire bow");
            helper.assertTrue(turtle.getInventory().getItem(2).getCount() == 7, "External inventory changes survive charge");
            helper.assertTrue(turtle.getInventory().getItem(0).is(Items.BOW), "Held item remains in turtle inventory");
            helper.succeed();
        });
    }

    @GameTest(template = "turtle_interactions")
    public void playersItemsAndVisibility(GameTestHelper helper) {
        var turtle = turtle(helper);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 3, 6))));
        var vision = new TurtleVision(turtle);
        try {
            helper.assertTrue(sees(vision.observe(4), player.getUUID()), "Player is visible by UUID");
            player.setInvisible(true);
            var revision = new AtomicLong();
            vision.update(revision::set);
            helper.assertTrue(revision.get() > 0 && !sees(vision.observe(4), player.getUUID()), "Invisible players disappear and notify vision");
            var item = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
            item.setNoPickUpDelay();
            helper.getLevel().addFreshEntity(item);
            helper.assertTrue(sees(vision.observe(4), item.getUUID()), "Dropped items are observable");
            for (int i = 0; i < 16; i++) turtle.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            helper.assertTrue(!new TurtleEntityCommand(item.getUUID(), TurtleEntityCommand.Action.COLLECT, false).execute(turtle).isSuccess(), "Full turtle cannot pick up into phantom player slots");
            helper.assertTrue(item.isAlive() && item.getItem().getCount() == 1, "Failed pickup preserves entity");
        } finally {
            vision.reset();
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "turtle_interactions", timeoutTicks = 100)
    public void finishesConsumableUse(GameTestHelper helper) {
        var turtle = turtle(helper);
        turtle.getInventory().setItem(0, new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(turtle.itemUse.start(60, 0, 0, false).isSuccess(), "Begin drinking");
        helper.runAfterDelay(36, () -> {
            helper.assertTrue(!turtle.itemUse.active(), "Consumable finishes before hold deadline");
            helper.assertTrue(turtle.getInventory().getItem(0).is(Items.BUCKET), "Completion returns the empty bucket exactly once");
            helper.succeed();
        });
    }

}
