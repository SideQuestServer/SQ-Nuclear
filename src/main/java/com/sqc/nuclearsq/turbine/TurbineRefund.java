package com.sqc.nuclearsq.turbine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqc.nuclearsq.kit.KitMarks;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.multiblock.Waterlog;
import com.sqc.nuclearsq.registry.MultiblockRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TurbineRefund {
    private TurbineRefund() {}

    private static final String[][] KIT = {
            {"turbine_controller_sqc", "1"}, {"reactor_casing_sqc", "8"}, {"pressure_chamber_sqc", "8"}, {"turbine_basic_sqc", "4"},
            {"turbine_brass_rotor_sqc", "2"}, {"steel_sheet_sqc", "8"}, {"glass_seal_sqc", "2"}};

    public static List<ItemStack> kitMaterials() {
        List<ItemStack> out = new ArrayList<>();
        for (String[] e : KIT) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("nuclearsq", e[0]));
            if (item != Items.AIR) out.add(new ItemStack(item, Integer.parseInt(e[1])));
        }
        return out;
    }

    static void refund(ServerLevel level, BlockPos ctrl, TurbineControllerBlockEntity be, int savedRot) {
        MultiblockStructure old = MultiblockStructure.TURBINE_LEGACY;
        int rot = savedRot >= 0 ? savedRot : old.bestRot(level, ctrl);
        boolean kit = KitMarks.owner(level, ctrl) != null;
        List<BlockPos> area = new ArrayList<>();
        for (int y = 0; y < old.height(); y++)
            for (int z = 0; z < old.depth(); z++)
                for (int x = 0; x < old.width(); x++) area.add(old.world(ctrl, rot, x, y, z));
        KitMarks.forget(level, ctrl, area);
        be.dropPart();
        be.forgetLayout();
        Map<Item, Integer> found = new LinkedHashMap<>();
        found.put(be.getBlockState().getBlock().asItem(), 1);
        for (MultiblockStructure.Cell c : old.cells(ctrl, rot)) {
            if (c.c() == '@') continue;
            BlockState st = level.getBlockState(c.pos());
            if (!c.ok(st)) continue;
            found.merge(st.getBlock().asItem(), 1, Integer::sum);
            level.setBlock(c.pos(), Waterlog.leftover(st), Block.UPDATE_ALL);
        }
        for (BlockPos p : area) {
            BlockState st = level.getBlockState(p);
            if (st.is(MultiblockRegistry.TURBINE_SHELL.get())) level.setBlock(p, Waterlog.leftover(st), Block.UPDATE_ALL);
        }
        List<ItemStack> give = new ArrayList<>();
        if (kit) {
            give.addAll(kitMaterials());
        } else {
            for (var e : found.entrySet()) {
                int n = e.getValue();
                while (n > 0) {
                    int k = Math.min(n, e.getKey().getDefaultMaxStackSize());
                    give.add(new ItemStack(e.getKey(), k));
                    n -= k;
                }
            }
        }
        level.setBlock(ctrl, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        int slot = 0;
        ChestBlockEntity chest = level.getBlockEntity(ctrl) instanceof ChestBlockEntity c ? c : null;
        for (ItemStack s : give) {
            if (chest != null && slot < chest.getContainerSize()) chest.setItem(slot++, s);
            else Block.popResource(level, ctrl.above(), s);
        }
        if (chest != null) chest.setChanged();
        resendLight(level, area);
    }

    private static void resendLight(ServerLevel level, List<BlockPos> area) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (BlockPos p : area) chunks.add(new ChunkPos(p));
        ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
        for (ChunkPos cp : chunks) {
            light.waitForPendingTasks(cp.x, cp.z).thenRunAsync(() -> {
                if (!level.hasChunk(cp.x, cp.z)) return;
                ClientboundLightUpdatePacket pkt = new ClientboundLightUpdatePacket(cp, light, null, null);
                for (ServerPlayer pl : level.getChunkSource().chunkMap.getPlayers(cp, false)) pl.connection.send(pkt);
            }, level.getServer());
        }
    }
}
