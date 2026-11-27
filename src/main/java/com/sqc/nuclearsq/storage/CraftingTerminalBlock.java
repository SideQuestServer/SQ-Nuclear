package com.sqc.nuclearsq.storage;

import com.sqc.nuclearsq.data.DataPanelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CraftingTerminalBlock extends DataPanelBlock {
    public CraftingTerminalBlock(Properties props) {
        this(props, false);
    }

    public CraftingTerminalBlock(Properties props, boolean panel) {
        super(props, panel, panel ? 0 : 1, 0, panel ? 0 : 2, panel ? 16 : 15, panel ? 16 : 15, panel ? 3 : 15);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && com.sqc.nuclearsq.data.ComputeNet.allow(sp, level, pos)) {
            CraftingTerminalMenu.openBlock(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
