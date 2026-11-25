package com.sqc.nuclearsq.compat.cc;

import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.MultiblockRegistry;
import com.sqc.nuclearsq.registry.PowerRegistry;
import com.sqc.nuclearsq.registry.SpentFuelRegistry;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class DataCcCompat {
    private DataCcCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> new DataNetworkPeripheral(level, pos),
                DataRegistry.TERMINAL.get(), DataRegistry.CONSOLE.get(), BigMachineRegistry.CONTROLLERS.get(BigMachine.CONTROL_DESK).get());
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> {
            if (!(be instanceof BigPortBlockEntity port)) return null;
            var c = port.findController();
            return c != null && c.machine() == BigMachine.CONTROL_DESK ? new DataNetworkPeripheral(level, c.getBlockPos()) : null;
        }, BigMachineRegistry.PORTS.get(BigPortKind.TERMINAL).get());
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> new LogbookPeripheral(level, pos),
                DataRegistry.LOGBOOK.get());
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> new PowerPeripheral(level, pos),
                DataRegistry.POWER.get());
        event.registerBlock(PeripheralCapability.get(), (level, pos, state, be, side) -> new StoragePeripheral(level, pos),
                com.sqc.nuclearsq.storage.StorageRegistry.TERMINAL.get());
        registerMachines(event);
        event.registerBlockEntity(PeripheralCapability.get(), DataRegistry.CONTROL_BE.get(), (be, side) -> new ControlPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), com.sqc.nuclearsq.monitor.ImageMonitorRegistry.MONITOR_BE.get(),
                (be, side) -> new ImageMonitorPeripheral(be));
    }

    private static void registerMachines(RegisterCapabilitiesEvent event) {
        machine(event, BigMachineRegistry.CONTROLLER_BE.get());
        machine(event, BigMachineRegistry.PORT_BE.get());
        machine(event, MultiblockRegistry.TURBINE_BE.get());
        machine(event, MultiblockRegistry.TURBINE_PORT_BE.get());
        machine(event, PowerRegistry.SOLAR_PANEL_BE.get());
        machine(event, PowerRegistry.SOLAR_COLLECTOR_BE.get());
        machine(event, PowerRegistry.RTG_BE.get());
        machine(event, SpentFuelRegistry.LARGE_CASTOR_BE.get());
        machine(event, ProtectionRegistry.SHOWER_BE.get());
        machine(event, DataRegistry.AREA_MONITOR_BE.get());
        machine(event, DataRegistry.AREA_SENSOR_BE.get());
        machine(event, com.sqc.nuclearsq.robotics.RoboticsRegistry.ARM_BE.get());
        machine(event, com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ADAPTER_BE.get());
        machine(event, com.sqc.nuclearsq.storage.StorageRegistry.ITEM_PORT_BE.get());
        machine(event, com.sqc.nuclearsq.storage.StorageRegistry.NET_PIPE_ADAPTER_BE.get());
        machine(event, com.sqc.nuclearsq.crate.CrateRegistry.BLOCK_ENTITY.get());
        machine(event, ProtectionRegistry.MONITOR_BE.get());
    }

    private static <T extends BlockEntity> void machine(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(PeripheralCapability.get(), type, (be, side) -> new MachinePeripheral(be.getLevel(), be.getBlockPos()));
    }

    public static String peripheralType(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        IPeripheral p = level.getCapability(PeripheralCapability.get(), pos, null);
        return p == null ? null : p.getType();
    }
}
