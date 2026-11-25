package com.sqc.nuclearsq.data;

import com.sqc.nuclearsq.transport.CableBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

public class DataCableBlock extends CableBlock {
    public DataCableBlock(Properties props) {
        super(props, TransportTier.BASIC);
    }

    @Override
    public boolean data() {
        return true;
    }

    @Override
    protected boolean hasTarget(Level level, BlockPos n, Direction side) {
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(n);
        if (st.getBlock() instanceof com.sqc.nuclearsq.storage.NetPipeAdapterBlock) return side == com.sqc.nuclearsq.storage.NetPipeAdapterBlock.back(st);
        return st.getBlock() instanceof DataDevice || DataSources.isPort(level, n);
    }

    public static boolean off(BlockGetter level, BlockPos pos, Direction d) {
        return level.getBlockEntity(pos) instanceof ConduitBlockEntity be && be.mode(d) == ConduitBlockEntity.OFF;
    }
}
