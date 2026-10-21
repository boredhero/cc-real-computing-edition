// SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.mixin;

import dan200.computercraft.shared.turtle.core.TurtleVision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void onBlockChanged(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> ci) {
        var level = ((LevelChunk) (Object) this).getLevel();
        if (!level.isClientSide && ci.getReturnValue() != null && ci.getReturnValue() != state) TurtleVision.blockChanged(level, pos);
    }
}
