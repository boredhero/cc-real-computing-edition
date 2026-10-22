// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.gametest.core;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.TurtleInteraction;
import dan200.computercraft.api.turtle.TurtleInteractionContext;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@GameTestHolder("cctest")
@PrefixGameTestTemplate(false)
public class TurtleAdapterTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cctest", "machine");

    public static void register() {
        ComputerCraftAPI.registerTurtleInteraction(ID, new TurtleInteraction() {
            @Override
            public boolean supports(TurtleInteractionContext context) {
                var state = context.level().getBlockState(context.pos());
                return state.is(Blocks.STONE) || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.EMERALD_BLOCK);
            }

            @Override
            public Map<String, ?> inspect(TurtleInteractionContext context) {
                return Map.of("gold", context.level().getBlockState(context.pos()).is(Blocks.GOLD_BLOCK));
            }

            @Override
            public Set<String> actions(TurtleInteractionContext context) {
                return context.player().getMainHandItem().isEmpty() ? Set.of() : Set.of("set");
            }

            @Override
            public Map<String, ?> execute(TurtleInteractionContext context, String action, Map<?, ?> arguments) throws LuaException {
                if (!Boolean.TRUE.equals(arguments.get("confirm"))) throw new LuaException("Confirmation required");
                context.level().setBlockAndUpdate(context.pos(), Blocks.GOLD_BLOCK.defaultBlockState());
                context.player().getMainHandItem().shrink(1);
                return Map.of("face", context.hit().getDirection().getName(), "sneak", context.player().isShiftKeyDown());
            }
        });
    }

    @GameTest(template = "turtle_interactions")
    public void discoversAndExecutesMachineActions(GameTestHelper helper) {
        var turtle = (TurtleBrain) ((TurtleBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4))).getAccess();
        turtle.getInventory().clearContent();
        turtle.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
        var pos = new BlockPos(4, 3, 6);
        helper.setBlock(pos, Blocks.STONE);
        var options = new TurtleInteractionOptions("back", true);
        var discovery = new TurtleAdapterCommand(0, 0, 2, null, "", Map.of(), options).execute(turtle);
        helper.assertTrue(discovery.isSuccess(), "Discovery succeeds");
        var discovered = (Map<?, ?>) java.util.Objects.requireNonNull(discovery.getResults())[0];
        helper.assertTrue(discovered.containsKey(ID.toString()), "Public registration appears in discovery");
        helper.assertTrue(helper.getBlockState(pos).is(Blocks.STONE) && turtle.getInventory().getItem(0).getCount() == 3, "Discovery has no normal item/block use effects");
        var denied = new TurtleAdapterCommand(0, 0, 2, ID, "set", Map.of(), options).execute(turtle);
        helper.assertTrue(!denied.isSuccess() && "Confirmation required".equals(denied.getErrorMessage()), "Adapter returns a useful rejection reason");
        var result = new TurtleAdapterCommand(0, 0, 2, ID, "set", Map.of("confirm", true), options).execute(turtle);
        helper.assertTrue(result.isSuccess(), "Execute action");
        var values = (Map<?, ?>) java.util.Objects.requireNonNull(result.getResults())[0];
        helper.assertTrue("north".equals(values.get("face")) && Boolean.TRUE.equals(values.get("sneak")), "Adapter receives requested face and sneak state");
        helper.assertTrue(helper.getBlockState(pos).is(Blocks.GOLD_BLOCK) && turtle.getInventory().getItem(0).getCount() == 2, "Action changes machine and syncs inventory exactly once");
        helper.assertTrue(!TurtlePlayer.get(turtle).player().isShiftKeyDown(), "Fake-player sneak state is cleaned up");
        helper.succeed();
    }

    @GameTest(template = "turtle_interactions")
    public void rejectsProtectedHiddenAndUnavailableActions(GameTestHelper helper) {
        var turtle = (TurtleBrain) ((TurtleBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4))).getAccess();
        turtle.getInventory().clearContent();
        turtle.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));
        var pos = new BlockPos(4, 3, 6);
        var command = new TurtleAdapterCommand(0, 0, 2, ID, "set", Map.of("confirm", true), TurtleInteractionOptions.DEFAULT);
        helper.setBlock(pos, Blocks.EMERALD_BLOCK);
        helper.assertTrue(!command.execute(turtle).isSuccess(), "Protection event prevents handler execution");
        helper.assertTrue(!new TurtleAdapterCommand(0, 0, 2, null, "", Map.of(), TurtleInteractionOptions.DEFAULT).execute(turtle).isSuccess(), "Protection also prevents discovery");
        helper.assertTrue(helper.getBlockState(pos).is(Blocks.EMERALD_BLOCK) && turtle.getInventory().getItem(0).getCount() == 3, "Denied action changes nothing");
        helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.STONE);
        helper.assertTrue(!command.execute(turtle).isSuccess(), "Hidden target is rejected");
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.AIR);
        helper.assertTrue(!new TurtleAdapterCommand(0, 0, 2, ID, "set", Map.of("confirm", true), new TurtleInteractionOptions("front", false)).execute(turtle).isSuccess(), "Hidden face is rejected");
        helper.assertTrue(!new TurtleAdapterCommand(0, 0, 5, ID, "set", Map.of("confirm", true), TurtleInteractionOptions.DEFAULT).execute(turtle).isSuccess(), "Out-of-reach target is rejected");
        helper.assertTrue(!new TurtleAdapterCommand(0, 0, 2, ID, "unknown", Map.of(), TurtleInteractionOptions.DEFAULT).execute(turtle).isSuccess(), "Unadvertised action is rejected");
        turtle.getInventory().clearContent();
        helper.assertTrue(!command.execute(turtle).isSuccess(), "Available actions are checked at execution time");
        helper.setBlock(pos, Blocks.DIAMOND_BLOCK);
        helper.assertTrue(!command.execute(turtle).isSuccess(), "Unsupported machine is rejected");
        helper.succeed();
    }
}
