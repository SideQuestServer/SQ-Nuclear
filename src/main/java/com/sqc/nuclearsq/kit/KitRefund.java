package com.sqc.nuclearsq.kit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.big.BigMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class KitRefund {
    private KitRefund() {}

    private static final Map<String, String> SWAP = Map.of(
            BigMachine.CNC_MILL.id, BigMachine.OIL_DERRICK.id,
            BigMachine.SOLDER_MACHINE.id, BigMachine.DISTILLATION_COLUMN.id);

    private record Job(ServerLevel level, ChunkPos pos, boolean fresh) {}

    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();

    static void onLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || !(e.getChunk() instanceof LevelChunk ch) || ch.hasData(KitMarks.REFUNDED)) return;
        JOBS.add(new Job(sl, ch.getPos(), e.isNewChunk()));
    }

    static void tick() {
        int n = JOBS.size();
        while (n-- > 0) {
            Job j = JOBS.poll();
            LevelChunk ch = j.level().getChunkSource().getChunkNow(j.pos().x, j.pos().z);
            if (ch == null || ch.hasData(KitMarks.REFUNDED)) continue;
            ch.setData(KitMarks.REFUNDED, true);
            ch.setUnsaved(true);
            if (j.fresh()) continue;
            for (BlockPos p : new ArrayList<>(ch.getBlockEntitiesPos())) refund(j.level(), p);
        }
    }

    public static boolean refund(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return false;
        ItemStack first = chest.getItem(0);
        if (!(first.getItem() instanceof MachineKitItem)) return false;
        String to = null;
        for (var e : SWAP.entrySet()) {
            var kit = KitRegistry.KITS.get(e.getKey());
            if (kit != null && first.is(kit.get())) to = e.getValue();
        }
        var give = to == null ? null : KitRegistry.KITS.get(to);
        if (give == null) return false;
        ItemStack stack = new ItemStack(give.get(), first.getCount());
        BlockPos at = spot(level, pos);
        if (at != null && level.setBlock(at, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL)
                && level.getBlockEntity(at) instanceof ChestBlockEntity box) {
            box.setItem(0, stack);
            box.setChanged();
        } else {
            Block.popResource(level, pos.above(), stack);
        }
        return true;
    }

    @Nullable
    private static BlockPos spot(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(d);
            if (level.isLoaded(p) && MachineKits.free(level, p) && !KitMarks.marked(level, p)) return p;
        }
        BlockPos p = pos.above();
        while (!level.isOutsideBuildHeight(p) && (!MachineKits.free(level, p) || KitMarks.marked(level, p))) p = p.above();
        return level.isOutsideBuildHeight(p) ? null : p;
    }

    static void clear() {
        JOBS.clear();
    }
}
