// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.mixin;

import dan200.computercraft.shared.turtle.core.TurtleVision;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
class EntityVisionMixin {
    @Inject(method = "setPosRaw", at = @At("HEAD"))
    private void onPosition(double x, double y, double z, CallbackInfo ci) {
        var entity = (Entity) (Object) this;
        if (entity.getX() != x || entity.getY() != y || entity.getZ() != z) TurtleVision.entityChanged(entity, x, y, z);
    }

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("RETURN"))
    private void onTrackedData(net.minecraft.network.syncher.EntityDataAccessor<?> key, CallbackInfo ci) {
        var entity = (Entity) (Object) this;
        TurtleVision.entityChanged(entity, entity.getX(), entity.getY(), entity.getZ());
    }

    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void onRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        var entity = (Entity) (Object) this;
        TurtleVision.entityChanged(entity, entity.getX(), entity.getY(), entity.getZ());
    }
}
