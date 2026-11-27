package com.sqc.nuclearsq.gametest;

import java.util.HashMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class TurbineRefundTests {
    private TurbineRefundTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "turbine_refund", timeoutTicks = 60)
    public static void oldTurbineBecomesChestWithBlocks(GameTestHelper h) {
        MultiblockStructure old = MultiblockStructure.TURBINE_LEGACY;
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2)).subtract(old.world(BlockPos.ZERO, 0, 0, 0, 0));
        Map<Item, Integer> want = new HashMap<>();
        for (MultiblockStructure.Cell c : old.cells(ctrl, 0)) {
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            want.merge(c.block().asItem(), 1, Integer::sum);
        }
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof TurbineControllerBlockEntity be)) throw new GameTestAssertException("Kein Turbinen-Controller");
        be.loadCustomOnly(new CompoundTag(), h.getLevel().registryAccess());
        h.runAfterDelay(10, () -> {
            if (!(h.getLevel().getBlockEntity(ctrl) instanceof ChestBlockEntity chest)) throw new GameTestAssertException("Keine Kiste am Controller");
            Map<Item, Integer> got = new HashMap<>();
            for (int i = 0; i < chest.getContainerSize(); i++) {
                var s = chest.getItem(i);
                if (!s.isEmpty()) got.merge(s.getItem(), s.getCount(), Integer::sum);
            }
            if (!got.equals(want)) throw new GameTestAssertException("Kiste " + got + " statt " + want);
            for (MultiblockStructure.Cell c : old.cells(ctrl, 0)) {
                if (!c.pos().equals(ctrl) && !h.getLevel().getBlockState(c.pos()).isAir()) throw new GameTestAssertException("Block bleibt bei " + c.pos());
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "turbine_refund_new", timeoutTicks = 60)
    public static void newTurbineStays(GameTestHelper h) {
        MultiblockStructure t = MultiblockStructure.TURBINE;
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 2, 2)).subtract(t.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : t.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        h.runAfterDelay(45, () -> {
            if (!(h.getLevel().getBlockEntity(ctrl) instanceof TurbineControllerBlockEntity be)) throw new GameTestAssertException("Turbine wurde ersetzt");
            if (!be.isFormed()) throw new GameTestAssertException("Neue Turbine nicht aufgebaut: " + be.matched() + "/" + be.total());
            h.succeed();
        });
    }
}
