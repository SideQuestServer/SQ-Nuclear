package com.sqc.nuclearsq.mixin;

import com.sqc.nuclearsq.machine.big.BigMachineControllerBlock;
import com.sqc.nuclearsq.machine.big.BigShellBlock;
import com.sqc.nuclearsq.reactor.ReactorCasingBlock;
import com.sqc.nuclearsq.turbine.TurbineControllerBlock;
import com.sqc.nuclearsq.turbine.TurbinePartBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpreadingSnowyDirtBlock.class)
public abstract class SpreadingSnowyDirtBlockMixin {
    @Inject(method = "canBeGrass", at = @At("HEAD"), cancellable = true)
    private static void nuclearsq$keepGrassUnderMachines(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState above = level.getBlockState(pos.above());
        Block b = above.getBlock();
        if ((b instanceof ReactorCasingBlock || b instanceof TurbinePartBlock || b instanceof BigShellBlock
                || b instanceof BigMachineControllerBlock || b instanceof TurbineControllerBlock)
                && above.getFluidState().getAmount() != 8) {
            cir.setReturnValue(true);
        }
    }
}
