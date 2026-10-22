package com.example.examplemod;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.TurtleInteraction;
import dan200.computercraft.api.turtle.TurtleInteractionContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;
import java.util.Set;

/** Minimal adapter using the same server-side operation as a player's lever click. */
public final class ExampleTurtleInteraction implements TurtleInteraction {
    @Override
    public boolean supports(TurtleInteractionContext context) {
        return context.level().getBlockState(context.pos()).is(Blocks.LEVER);
    }

    @Override
    public Map<String, ?> inspect(TurtleInteractionContext context) {
        return Map.of("powered", context.level().getBlockState(context.pos()).getValue(BlockStateProperties.POWERED));
    }

    @Override
    public Set<String> actions(TurtleInteractionContext context) {
        return Set.of("toggle");
    }

    @Override
    public Map<String, ?> execute(TurtleInteractionContext context, String action, Map<?, ?> arguments) throws LuaException {
        // For your machine, validate arguments and ownership, then call the same method as your GUI handler.
        var state = context.level().getBlockState(context.pos());
        if (!state.useWithoutItem(context.level(), context.player(), context.hit()).consumesAction()) {
            throw new LuaException("Lever could not be toggled");
        }
        return inspect(context);
    }
}
