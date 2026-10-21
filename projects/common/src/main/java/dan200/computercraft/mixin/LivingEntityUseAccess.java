// SPDX-FileCopyrightText: 2026 boredhero
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityUseAccess {
    @Invoker("updatingUsingItem")
    void computercraft$tickItemUse();
}
