package com.sqc.nuclearsq.client;

import com.sqc.nuclearsq.explosion.client.ExplosionFlash;
import com.sqc.nuclearsq.machine.client.DeviceScreen;
import com.sqc.nuclearsq.registry.MultiblockRegistry;
import com.sqc.nuclearsq.turbine.client.TurbineScreen;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class NuclearClient {
    private NuclearClient() {}

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(MultiblockRegistry.TURBINE_MENU.get(), TurbineScreen::new);
        event.register(MultiblockRegistry.DEVICE_MENU.get(), (com.sqc.nuclearsq.machine.DeviceMenu m, net.minecraft.world.entity.player.Inventory inv,
                net.minecraft.network.chat.Component title) -> m.kind == com.sqc.nuclearsq.machine.DeviceMenu.ROD_DRIVE
                ? new com.sqc.nuclearsq.machine.client.RodDriveScreen(m, inv, title)
                : m.kind == com.sqc.nuclearsq.machine.DeviceMenu.AIRLOCK
                ? new com.sqc.nuclearsq.door.client.AirlockScreen(m, inv, title)
                : m.kind == com.sqc.nuclearsq.machine.DeviceMenu.DOOR
                ? new com.sqc.nuclearsq.door.client.DoorScreen(m, inv, title)
                : m.kind == com.sqc.nuclearsq.machine.DeviceMenu.POWER_GATE
                ? new com.sqc.nuclearsq.transport.client.PowerGateScreen(m, inv, title)
                : m.kind == com.sqc.nuclearsq.machine.DeviceMenu.BIG
                ? new com.sqc.nuclearsq.machine.client.BigMachineScreen(m, inv, title) : new DeviceScreen(m, inv, title));
        event.register(com.sqc.nuclearsq.registry.HeavyAssemblyRegistry.CRAFTER_MENU.get(), com.sqc.nuclearsq.machine.client.BigCrafterScreen::new);
        event.register(com.sqc.nuclearsq.nanobot.NanobotRegistry.MENU.get(), com.sqc.nuclearsq.nanobot.client.NanoCanisterScreen::new);
        event.register(com.sqc.nuclearsq.data.DataRegistry.TERMINAL_MENU.get(), com.sqc.nuclearsq.data.client.DataTerminalScreen::new);
        event.register(com.sqc.nuclearsq.data.DataRegistry.CONSOLE_MENU.get(), com.sqc.nuclearsq.data.client.DataConsoleScreen::new);
        event.register(com.sqc.nuclearsq.data.DataRegistry.LOGBOOK_MENU.get(), com.sqc.nuclearsq.data.client.LogbookScreen::new);
        event.register(com.sqc.nuclearsq.data.DataRegistry.POWER_MENU.get(), com.sqc.nuclearsq.data.client.PowerMonitorScreen::new);
        event.register(com.sqc.nuclearsq.data.DataRegistry.MULTI_MENU.get(), com.sqc.nuclearsq.data.client.DataMultiScreen::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.TERMINAL_MENU.get(), com.sqc.nuclearsq.storage.client.StorageTerminalScreen<com.sqc.nuclearsq.storage.StorageTerminalMenu>::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.CRAFTING_MENU.get(), com.sqc.nuclearsq.storage.client.CraftingTerminalScreen::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.RACK_MENU.get(), com.sqc.nuclearsq.storage.client.ServerRackScreen::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.ASSEMBLER_MENU.get(), com.sqc.nuclearsq.storage.client.ComponentAssemblerScreen::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.MONITOR_MENU.get(), com.sqc.nuclearsq.storage.client.StorageMonitorScreen::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.ITEM_PORT_MENU.get(), com.sqc.nuclearsq.storage.client.ItemPortScreen::new);
        event.register(com.sqc.nuclearsq.storage.StorageRegistry.NET_PIPE_ADAPTER_MENU.get(), com.sqc.nuclearsq.storage.client.NetPipeAdapterScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "explosion_flash"), NuclearClient::renderFlash);
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.clearDrives(true);
        com.sqc.nuclearsq.reactor.client.RodGlow.clearCache();
        OcclusionCull.clear();
        ExplosionFlash.ticksLeft = 0;
        com.sqc.nuclearsq.data.client.DataConsoleScreen.clearSession();
        if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) com.sqc.nuclearsq.gamedata.GameData.resetToBuiltIn();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ExplosionFlash.ticksLeft > 0) ExplosionFlash.ticksLeft--;
    }

    private static void renderFlash(GuiGraphics g, DeltaTracker delta) {
        if (ExplosionFlash.ticksLeft <= 0) return;
        float t = ExplosionFlash.ticksLeft - delta.getGameTimeDeltaPartialTick(false);
        float alpha = ExplosionFlash.alpha(t);
        int a = (int) (alpha * 255f);
        if (a <= 0) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFFFFF);
    }
}
