package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.reactor.ReactorCasingBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class GrassUnderMachineTests {
    private GrassUnderMachineTests() {}

    @GameTest(template = "floor", batch = "grass_under_machine")
    public static void grassStaysUnderFormedParts(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockState casing = ReactorRegistry.CASING.get().defaultBlockState()
                .setValue(ReactorProps.FORMED, true).setValue(ReactorCasingBlock.SKY, false);
        BlockState port = BigMachineRegistry.PORTS.get(BigPortKind.ENERGY).get().defaultBlockState().setValue(ReactorProps.FORMED, true);
        BlockPos[] grass = {new BlockPos(2, 2, 2), new BlockPos(5, 2, 2), new BlockPos(8, 2, 2)};
        BlockState[] above = {casing, port, Blocks.STONE.defaultBlockState()};
        for (int i = 0; i < grass.length; i++) {
            h.setBlock(grass[i], Blocks.GRASS_BLOCK.defaultBlockState());
            h.setBlock(grass[i].above(), above[i]);
        }
        for (int i = 0; i < grass.length; i++) {
            BlockPos p = h.absolutePos(grass[i]);
            for (int n = 0; n < 8; n++) {
                BlockState st = level.getBlockState(p);
                if (st.is(Blocks.GRASS_BLOCK)) st.randomTick(level, p, level.random);
            }
        }
        if (!h.getBlockState(grass[0]).is(Blocks.GRASS_BLOCK)) throw new GameTestAssertException("Gras unter aufgebautem Gehaeuse wurde " + h.getBlockState(grass[0]));
        if (!h.getBlockState(grass[1]).is(Blocks.GRASS_BLOCK)) throw new GameTestAssertException("Gras unter aufgebautem Port wurde " + h.getBlockState(grass[1]));
        if (!h.getBlockState(grass[2]).is(Blocks.DIRT)) throw new GameTestAssertException("Gras unter Stein blieb " + h.getBlockState(grass[2]));
        h.succeed();
    }
}
