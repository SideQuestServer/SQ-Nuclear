package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.network.client.ClientPayloadHandler;
import com.sqc.nuclearsq.radiation.RadiationState;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;
import com.sqc.nuclearsq.turbine.TurbineMenu;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(com.sqc.nuclearsq.gamedata.GameDataPayload.TYPE, com.sqc.nuclearsq.gamedata.GameDataPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.gameData(payload)));
        registrar.playToClient(ExplosionFlashPayload.TYPE, ExplosionFlashPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.explosionFlash(payload)));
        registrar.playToServer(TurbineControlPayload.TYPE, TurbineControlPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player().containerMenu instanceof TurbineMenu tm
                            && ctx.player().level().getBlockEntity(tm.pos) instanceof TurbineControllerBlockEntity be) {
                        be.setControl(payload.running(), payload.throttle());
                    }
                }));
        registrar.playToServer(DeviceControlPayload.TYPE, DeviceControlPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player().containerMenu instanceof DeviceMenu dm && dm.kind == DeviceMenu.ROD_DRIVE
                            && ctx.player().level().getBlockEntity(dm.pos) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be) {
                        switch (payload.action()) {
                            case DeviceControlPayload.ROD_TARGET -> be.setRodTarget(payload.value());
                            case DeviceControlPayload.SCRAM -> be.scram();
                            case DeviceControlPayload.ROD_MODE -> be.setTerminalMode(payload.value() == 1);
                            case DeviceControlPayload.ROD_CHANNEL -> be.setChannel(payload.value());
                            case DeviceControlPayload.ROD_LOCK -> be.setShaftLocked(payload.value() / 2, payload.value() % 2 == 1);
                            default -> { }
                        }
                    }
                    if (ctx.player().containerMenu instanceof DeviceMenu dm && dm.kind == DeviceMenu.BIG
                            && ctx.player().level().getBlockEntity(dm.pos) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be) {
                        switch (payload.action()) {
                            case DeviceControlPayload.BIG_ENABLE -> be.setEnabled(payload.value() == 1);
                            case DeviceControlPayload.BIG_MANUAL -> be.setManual(payload.value() == 1);
                            case DeviceControlPayload.BIG_DUMP -> be.dumpTank(payload.value());
                            case DeviceControlPayload.BIG_REPAIR -> be.repair();
                            case DeviceControlPayload.BIG_BURN -> be.setBurn(payload.value());
                            default -> { }
                        }
                    }
                    if (payload.action() == DeviceControlPayload.GATE_SET && ctx.player().containerMenu instanceof DeviceMenu dm
                            && dm.kind == DeviceMenu.POWER_GATE && com.sqc.nuclearsq.data.RemoteAccess.inReach(ctx.player(), dm.pos)
                            && ctx.player().level().getBlockEntity(dm.pos) instanceof com.sqc.nuclearsq.transport.PowerGateBlockEntity gate) {
                        int which = payload.value() / 128, pct = payload.value() % 128;
                        if (which == 0) gate.setRatePct(pct);
                        else gate.setThreshold(which - 1, pct);
                    }
                    if (payload.action() == DeviceControlPayload.DOOR_OPEN && ctx.player().containerMenu instanceof DeviceMenu dm
                            && dm.kind == DeviceMenu.DOOR && com.sqc.nuclearsq.data.RemoteAccess.inReach(ctx.player(), dm.pos)
                            && ctx.player().level().getBlockEntity(dm.pos) instanceof com.sqc.nuclearsq.door.DoorBlockEntity door) {
                        net.minecraft.network.chat.Component err = door.setOpen(payload.value() == 1);
                        if (err != null) ctx.player().displayClientMessage(err, true);
                    }
                    if (payload.action() == DeviceControlPayload.AIRLOCK_DOOR && ctx.player().containerMenu instanceof DeviceMenu dm
                            && dm.kind == DeviceMenu.AIRLOCK && com.sqc.nuclearsq.data.RemoteAccess.inReach(ctx.player(), dm.pos)
                            && ctx.player().level().getBlockEntity(dm.pos) instanceof com.sqc.nuclearsq.door.AirlockBlockEntity airlock) {
                        airlock.request(Math.max(0, Math.min(2, payload.value())) - 1);
                    }
                    if (ctx.player().containerMenu instanceof DeviceMenu dm && dm.kind == DeviceMenu.BIG_BATTERY
                            && ctx.player().level().getBlockEntity(dm.pos) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be
                            && be.battery() != null) {
                        switch (payload.action()) {
                            case DeviceControlPayload.BATTERY_PORT -> be.battery().setPortOn(payload.value() / 2, payload.value() % 2 == 1);
                            case DeviceControlPayload.BATTERY_MODE -> be.battery().setMode(payload.value());
                            case DeviceControlPayload.BATTERY_PORT_DIR -> be.battery().setPortOut(payload.value() / 2, payload.value() % 2 == 1);
                            case DeviceControlPayload.BATTERY_THRESH -> be.battery().setThreshold(payload.value() / 128, payload.value() % 128);
                            default -> { }
                        }
                    }
                    if (ctx.player().containerMenu instanceof com.sqc.nuclearsq.machine.big.BigCrafterMenu cm
                            && ctx.player().level().getBlockEntity(cm.pos) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be) {
                        if (payload.action() == DeviceControlPayload.BIG_ENABLE) be.setEnabled(payload.value() == 1);
                        if (payload.action() == DeviceControlPayload.RECIPE_LOCK && cm.lockable()) be.setRecipeLock(payload.value());
                        if (payload.action() == DeviceControlPayload.RECIPE_FILL && cm.lockable() && cm.stillValid(ctx.player())
                                && PacketLimit.allow(ctx.player(), "recipe_fill", 10)) be.fillRecipe(ctx.player(), payload.value() == 1);
                        if (payload.action() == DeviceControlPayload.RACK_TOGGLE && be.machine() == com.sqc.nuclearsq.machine.big.BigMachine.SPENT_FUEL_RACK) {
                            be.setRackReleased(payload.value(), !be.rackReleased(payload.value()));
                        }
                    }
                }));
        registrar.playToServer(com.sqc.nuclearsq.data.DataPayloads.Select.TYPE, com.sqc.nuclearsq.data.DataPayloads.Select.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.data.DataTerminalMenu m
                            && PacketLimit.allow(sp, "data_select", 5)) {
                        com.sqc.nuclearsq.data.DataTerminalBlock.select(sp, m.pos, payload.source());
                    }
                }));
        registrar.playToServer(com.sqc.nuclearsq.data.DataPayloads.Control.TYPE, com.sqc.nuclearsq.data.DataPayloads.Control.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.data.DataTerminalMenu m
                            && m.stillValid(sp) && PacketLimit.allow(sp, "data_control", 10)) {
                        var err = com.sqc.nuclearsq.data.DataTree.control(sp.level(), m.pos, payload.source(), payload.key(), payload.value(), sp);
                        if (err != null) sp.displayClientMessage(err, true);
                        m.refresh();
                    }
                }));
        registrar.playToServer(com.sqc.nuclearsq.data.DataPayloads.Locate.TYPE, com.sqc.nuclearsq.data.DataPayloads.Locate.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.data.DataTerminalMenu m
                            && m.stillValid(sp) && PacketLimit.allow(sp, "data_locate", 4)) {
                        com.sqc.nuclearsq.data.DataTree.locate(sp, m.pos, payload.source());
                    }
                }));
        registrar.playToClient(com.sqc.nuclearsq.data.DataPayloads.Tree.TYPE, com.sqc.nuclearsq.data.DataPayloads.Tree.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.dataTree(payload)));
        registrar.playToServer(com.sqc.nuclearsq.data.DataPayloads.Command.TYPE, com.sqc.nuclearsq.data.DataPayloads.Command.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.data.DataConsoleMenu m
                            && m.allowCommand(sp.level().getGameTime())) {
                        var lines = com.sqc.nuclearsq.data.DataConsole.runChecked(sp.level(), m.pos, sp, payload.line());
                        if (sp.containerMenu == m) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new com.sqc.nuclearsq.data.DataPayloads.Output(lines));
                    }
                }));
        registrar.playToClient(com.sqc.nuclearsq.data.DataPayloads.Output.TYPE, com.sqc.nuclearsq.data.DataPayloads.Output.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.dataOutput(payload)));
        registrar.playToClient(com.sqc.nuclearsq.data.LogbookPayload.TYPE, com.sqc.nuclearsq.data.LogbookPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.logbook(payload)));
        registrar.playToClient(com.sqc.nuclearsq.data.PowerPayload.TYPE, com.sqc.nuclearsq.data.PowerPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.power(payload)));
        registrar.playToServer(com.sqc.nuclearsq.storage.StoragePayloads.Action.TYPE, com.sqc.nuclearsq.storage.StoragePayloads.Action.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.storage.StorageTerminalMenu m
                            && m.containerId == payload.containerId() && m.stillValid(sp) && PacketLimit.allow(sp, "storage_action", 40)) {
                        m.action(sp, payload.proto(), payload.mode());
                    }
                }));
        registrar.playToClient(com.sqc.nuclearsq.storage.StoragePayloads.Items.TYPE, com.sqc.nuclearsq.storage.StoragePayloads.Items.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.storageItems(payload)));
        registrar.playToServer(com.sqc.nuclearsq.storage.StoragePayloads.Order.TYPE, com.sqc.nuclearsq.storage.StoragePayloads.Order.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.storage.StorageTerminalMenu m
                            && m.containerId == payload.containerId() && m.stillValid(sp) && PacketLimit.allow(sp, "storage_order", 4)) {
                        m.order(payload.proto(), payload.amount());
                    }
                }));
        registrar.playToServer(com.sqc.nuclearsq.storage.CraftingPayloads.Clear.TYPE, com.sqc.nuclearsq.storage.CraftingPayloads.Clear.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.storage.CraftingTerminalMenu m
                            && m.containerId == payload.containerId() && m.stillValid(sp) && PacketLimit.allow(sp, "crafting_clear", 5)) {
                        m.clearGrid();
                    }
                }));
        registrar.playToServer(com.sqc.nuclearsq.storage.CraftingPayloads.Fill.TYPE, com.sqc.nuclearsq.storage.CraftingPayloads.Fill.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.storage.CraftingTerminalMenu m
                            && m.containerId == payload.containerId() && m.stillValid(sp) && PacketLimit.allow(sp, "crafting_fill", 5)) {
                        var msg = m.fillRecipe(payload.recipe());
                        if (!msg.getString().isEmpty()) sp.displayClientMessage(msg, true);
                    }
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.storage.ComponentAssemblerMenu m
                            && m.containerId == payload.containerId() && m.stillValid(sp) && PacketLimit.allow(sp, "assembler_template", 5)) {
                        m.templateRecipe(payload.recipe());
                    }
                }));
        registrar.playToClient(com.sqc.nuclearsq.storage.StoragePayloads.Craftables.TYPE, com.sqc.nuclearsq.storage.StoragePayloads.Craftables.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.storageCraftables(payload)));
        registrar.playToServer(VeinMinePayload.TYPE, VeinMinePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) com.sqc.nuclearsq.VeinMiner.setHeld(sp, payload.held());
                }));
        registrar.playToServer(com.sqc.nuclearsq.exo.ExoJetpackPayload.TYPE, com.sqc.nuclearsq.exo.ExoJetpackPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) com.sqc.nuclearsq.exo.ExoJetpack.setInput(sp, payload.bits());
                }));
        registrar.playToServer(com.sqc.nuclearsq.exo.ExoHotbarPayload.TYPE, com.sqc.nuclearsq.exo.ExoHotbarPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) payload.swap(sp);
                }));
        registrar.playToServer(CherenkovPayload.TYPE, CherenkovPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (PacketLimit.allow(ctx.player(), "cherenkov", 20)) com.sqc.nuclearsq.reactor.Cherenkov.apply(ctx.player(), payload.pos(), payload.level());
                }));
        registrar.playToClient(DosimeterPayload.TYPE,DosimeterPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.dosimeter(payload)));
        registrar.playToClient(GeigerPayload.TYPE, GeigerPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.geiger(payload)));
        registrar.playToClient(RadiationDebugPayload.TYPE, RadiationDebugPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.radiationDebug(payload)));
        registrar.playToClient(com.sqc.nuclearsq.data.NetScanPayload.TYPE, com.sqc.nuclearsq.data.NetScanPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.netScan(payload)));
        registrar.playToServer(com.sqc.nuclearsq.nanobot.NanoControlPayload.TYPE, com.sqc.nuclearsq.nanobot.NanoControlPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof com.sqc.nuclearsq.nanobot.NanoCanisterMenu m
                            && m.stillValid(sp)) {
                        m.handleAction(sp, payload.action(), payload.value());
                    }
                }));
        registrar.playToServer(com.sqc.nuclearsq.nanobot.GadgetKeyPayload.TYPE, com.sqc.nuclearsq.nanobot.GadgetKeyPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) com.sqc.nuclearsq.nanobot.NanoGadget.handleKey(sp, payload.action(), payload.value());
                }));
        registrar.playToClient(com.sqc.nuclearsq.nanobot.GadgetTemplatePayload.TYPE, com.sqc.nuclearsq.nanobot.GadgetTemplatePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.gadgetTemplate(payload)));
        registrar.playToClient(com.sqc.nuclearsq.nanobot.NanoSwarmPayload.TYPE, com.sqc.nuclearsq.nanobot.NanoSwarmPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.nanoSwarm(payload)));
        registrar.playToClient(RadiationDosePayload.TYPE, RadiationDosePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> RadiationState.set(payload.dose())));
        registrar.playToClient(com.sqc.nuclearsq.progression.Discovered.Payload.TYPE, com.sqc.nuclearsq.progression.Discovered.Payload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sqc.nuclearsq.progression.Discovered.receive(payload)));
        registrar.playToClient(com.sqc.nuclearsq.progression.GuideProgress.Payload.TYPE, com.sqc.nuclearsq.progression.GuideProgress.Payload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sqc.nuclearsq.progression.GuideProgress.receive(payload)));
        registrar.playToServer(com.sqc.nuclearsq.monitor.ImageMonitorInputPayload.TYPE, com.sqc.nuclearsq.monitor.ImageMonitorInputPayload.STREAM_CODEC,
                com.sqc.nuclearsq.monitor.ImageMonitorInputPayload::handle);
        registrar.playToClient(com.sqc.nuclearsq.monitor.ImageMonitorPayload.TYPE, com.sqc.nuclearsq.monitor.ImageMonitorPayload.STREAM_CODEC,
                com.sqc.nuclearsq.monitor.ImageMonitorPayload::handle);
        registrar.playToServer(com.sqc.nuclearsq.apps.AppPayloads.Action.TYPE, com.sqc.nuclearsq.apps.AppPayloads.Action.STREAM_CODEC,
                com.sqc.nuclearsq.apps.LoginMonitor::handle);
        registrar.playToClient(com.sqc.nuclearsq.apps.AppPayloads.State.TYPE, com.sqc.nuclearsq.apps.AppPayloads.State.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.appLogin(payload)));
        registrar.playToServer(com.sqc.nuclearsq.apps.AuctionPayloads.Action.TYPE, com.sqc.nuclearsq.apps.AuctionPayloads.Action.STREAM_CODEC,
                com.sqc.nuclearsq.apps.AuctionMonitor::handle);
        registrar.playToClient(com.sqc.nuclearsq.apps.AuctionPayloads.State.TYPE, com.sqc.nuclearsq.apps.AuctionPayloads.State.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.appAuction(payload)));
        registrar.playToClient(com.sqc.nuclearsq.apps.AppPayloads.Session.TYPE, com.sqc.nuclearsq.apps.AppPayloads.Session.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.appSession(payload)));
        registrar.playToServer(com.sqc.nuclearsq.machine.AccessPayloads.Action.TYPE, com.sqc.nuclearsq.machine.AccessPayloads.Action.STREAM_CODEC,
                com.sqc.nuclearsq.machine.AccessPayloads::handle);
        registrar.playToClient(com.sqc.nuclearsq.machine.AccessPayloads.State.TYPE, com.sqc.nuclearsq.machine.AccessPayloads.State.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.access(payload)));
        registrar.playToClient(MushroomCloudPayload.TYPE, MushroomCloudPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.mushroomCloud(payload)));
        registrar.playToClient(com.sqc.nuclearsq.robotics.decon.RobotPayloads.ControlState.TYPE, com.sqc.nuclearsq.robotics.decon.RobotPayloads.ControlState.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sqc.nuclearsq.robotics.decon.client.DeconRobotClient.setControl(payload.robotId())));
        registrar.playToServer(com.sqc.nuclearsq.robotics.decon.RobotPayloads.ControlInput.TYPE, com.sqc.nuclearsq.robotics.decon.RobotPayloads.ControlInput.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp
                            && com.sqc.nuclearsq.robotics.decon.DeconRobotEntity.controlledBy(sp) instanceof com.sqc.nuclearsq.robotics.decon.DeconRobotEntity robot) {
                        if (payload.stop()) robot.stopControl("stopped");
                        else robot.input(payload.forward(), payload.turn(), payload.pitch());
                    }
                }));
        registrar.playToClient(com.sqc.nuclearsq.robotics.drone.DronePayloads.State.TYPE, com.sqc.nuclearsq.robotics.drone.DronePayloads.State.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sqc.nuclearsq.robotics.drone.client.SurveyDroneControlClient.setControl(payload.droneId())));
        registrar.playToClient(com.sqc.nuclearsq.robotics.drone.DronePayloads.Scan.TYPE, com.sqc.nuclearsq.robotics.drone.DronePayloads.Scan.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sqc.nuclearsq.robotics.drone.client.SurveyDroneControlClient.receive(payload)));
        registrar.playToServer(com.sqc.nuclearsq.robotics.drone.DronePayloads.Fly.TYPE, com.sqc.nuclearsq.robotics.drone.DronePayloads.Fly.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) com.sqc.nuclearsq.robotics.drone.SurveyTabletItem.fly(sp, payload.mainHand());
                }));
        registrar.playToServer(com.sqc.nuclearsq.robotics.drone.DronePayloads.Input.TYPE, com.sqc.nuclearsq.robotics.drone.DronePayloads.Input.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp
                            && com.sqc.nuclearsq.robotics.drone.SurveyDroneEntity.controlledBy(sp) instanceof com.sqc.nuclearsq.robotics.drone.SurveyDroneEntity drone) {
                        if (payload.stop()) drone.stopControl("stopped");
                        else drone.input(payload.forward(), payload.strafe(), payload.up(), payload.yaw(), payload.pitch());
                    }
                }));
    }
}
