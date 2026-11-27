package com.sqc.nuclearsq.kit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KitRegistry {
    private KitRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final List<String> IDS = ids();
    public static final Map<String, DeferredItem<MachineKitItem>> KITS = new LinkedHashMap<>();

    private static List<String> ids() {
        java.util.ArrayList<String> l = new java.util.ArrayList<>(List.of("turbine"));
        for (BigMachine m : BigMachine.values()) l.add(m.id);
        return List.copyOf(l);
    }

    static {
        for (String id : IDS) {
            KITS.put(id, ITEMS.registerItem(id + "_kit_sqc", p -> new MachineKitItem(id, p.stacksTo(1))));
        }
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        KitMarks.ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> KitMarks.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> KitMarks.clear());
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> KitRefund.onLoad(e));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> KitRefund.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> KitRefund.clear());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, KitRegistry::onBreak);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !MachineKits.isPart(e.getState())) return;
        MachineKits.Placement pl = MachineKits.owner(level, e.getPos());
        if (pl == null) return;
        e.setCanceled(true);
        if (MachineKits.mayTake(level, pl, e.getPlayer())) MachineKits.dismantle(level, pl, true);
    }
}
