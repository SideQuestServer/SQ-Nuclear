package com.sqc.nuclearsq.kit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.Waterlog;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class LegacyResize {
    private LegacyResize() {}

    static final Set<BigMachine> RESIZED = Set.of(BigMachine.OIL_DERRICK, BigMachine.DISTILLATION_COLUMN, BigMachine.CNC_MILL, BigMachine.SOLDER_MACHINE);

    private record Job(ServerLevel level, BlockPos ctrl) {}

    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();

    public static boolean check(BigMachineBlockEntity be) {
        if (!RESIZED.contains(be.machine()) || !(be.getLevel() instanceof ServerLevel sl)) return true;
        BlockPos ctrl = be.getBlockPos();
        Boolean old = outdated(sl, ctrl);
        if (old == null) return false;
        if (old) JOBS.add(new Job(sl, ctrl.immutable()));
        return true;
    }

    public static Boolean outdated(Level level, BlockPos ctrl) {
        MachineKits.Placement pl = KitMarks.at(level, ctrl);
        if (pl == null) return false;
        long c = ctrl.asLong();
        for (BlockPos p : pl.footprint()) {
            if (!level.isLoaded(p)) return null;
            LevelChunk ch = level.getChunkAt(p);
            if (!ch.hasData(KitMarks.MARKS)) return true;
            KitMarks.Data d = ch.getData(KitMarks.MARKS);
            if (!d.parts.containsKey(p.asLong()) || d.parts.get(p.asLong()) != c) return true;
        }
        return false;
    }

    static void tick() {
        while (!JOBS.isEmpty()) {
            Job j = JOBS.poll();
            if (Boolean.TRUE.equals(outdated(j.level(), j.ctrl()))) convert(j.level(), j.ctrl());
        }
    }

    public static void convert(ServerLevel level, BlockPos ctrl) {
        MachineKits.Placement pl = KitMarks.at(level, ctrl);
        if (pl == null) return;
        List<ItemStack> loot = new ArrayList<>();
        var kit = KitRegistry.KITS.get(pl.def().id());
        if (kit != null) {
            ItemStack stack = new ItemStack(kit.get());
            int fe = MachineKits.storedEnergy(level, ctrl);
            if (fe > 0) stack.set(ModDataComponents.STORED_ENERGY.get(), fe);
            loot.add(stack);
        }
        if (level.getBlockEntity(ctrl) instanceof BigMachineBlockEntity be && be.items() != null) {
            ItemStackHandler h = be.items();
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack st = h.getStackInSlot(i);
                if (!st.isEmpty()) loot.add(st.copy());
                h.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        List<BlockPos> parts = new ArrayList<>();
        long c = ctrl.asLong();
        for (int cx = (ctrl.getX() >> 4) - 1; cx <= (ctrl.getX() >> 4) + 1; cx++) {
            for (int cz = (ctrl.getZ() >> 4) - 1; cz <= (ctrl.getZ() >> 4) + 1; cz++) {
                LevelChunk ch = level.getChunk(cx, cz);
                if (!ch.hasData(KitMarks.MARKS)) continue;
                KitMarks.Data d = ch.getData(KitMarks.MARKS);
                var it = d.parts.long2LongEntrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    if (e.getLongValue() != c) continue;
                    parts.add(BlockPos.of(e.getLongKey()));
                    it.remove();
                }
                d.ctrls.remove(c);
                if (d.isEmpty()) ch.removeData(KitMarks.MARKS);
                ch.setUnsaved(true);
            }
        }
        level.setBlock(ctrl, Waterlog.leftover(level.getBlockState(ctrl)), Block.UPDATE_ALL);
        for (BlockPos p : parts) {
            BlockState st = level.getBlockState(p);
            if (!p.equals(ctrl) && MachineKits.isPart(st)) level.setBlock(p, Waterlog.leftover(st), Block.UPDATE_ALL);
        }
        store(level, ctrl, loot);
    }

    static void store(ServerLevel level, BlockPos at, List<ItemStack> loot) {
        BlockPos p = at;
        while (!level.isOutsideBuildHeight(p) && !MachineKits.free(level, p)) p = p.above();
        if (!level.isOutsideBuildHeight(p) && level.setBlock(p, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL)
                && level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
            int slot = 0;
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack st : loot) {
                if (slot < chest.getContainerSize()) chest.setItem(slot++, st);
                else rest.add(st);
            }
            chest.setChanged();
            loot = rest;
        }
        for (ItemStack st : loot) Block.popResource(level, at, st);
    }

    static void clear() {
        JOBS.clear();
    }
}
