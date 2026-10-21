// SPDX-FileCopyrightText: 2022 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;
import java.util.function.Consumer;

class FakePlayerExt extends FakePlayer implements dan200.computercraft.shared.turtle.core.TurtleMenuAccess {
    private boolean turtleMenusAllowed;
    private static final EntityDimensions DIMENSIONS = EntityDimensions.fixed(0, 0);

    FakePlayerExt(ServerLevel serverLevel, GameProfile profile) {
        super(serverLevel, profile);
        refreshDimensions();
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return super.canHarmPlayer(other);
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        return openMenu(menu, (java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf>) null);
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu, @Nullable Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extraData) {
        if (!turtleMenusAllowed) return OptionalInt.empty();
        var result = dan200.computercraft.shared.turtle.core.TurtleMenuAccess.open(this, menu);
        if (result.isPresent()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(this, containerMenu));
        return result;
    }

    @Override
    public void setTurtleMenusAllowed(boolean allowed) {
        turtleMenusAllowed = allowed;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return DIMENSIONS;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return false;
    }
}
