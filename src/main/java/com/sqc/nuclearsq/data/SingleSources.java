package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.power.RtgBlockEntity;
import com.sqc.nuclearsq.power.SolarPanelBlockEntity;
import com.sqc.nuclearsq.radiation.ContaminationMonitorBlock;
import com.sqc.nuclearsq.radiation.ContaminationMonitorBlockEntity;
import com.sqc.nuclearsq.radiation.DeconShowerBlock;
import com.sqc.nuclearsq.radiation.DeconShowerBlockEntity;
import com.sqc.nuclearsq.spent.CastorCaskBlockEntity;
import com.sqc.nuclearsq.storage.WifiAdapterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class SingleSources {
    private SingleSources() {}

    public static boolean is(@Nullable BlockEntity be) {
        return be instanceof SolarPanelBlockEntity || be instanceof RtgBlockEntity
                || be instanceof CastorCaskBlockEntity || be instanceof DeconShowerBlockEntity
                || be instanceof AreaMonitorBlockEntity || be instanceof WifiAdapterBlockEntity
                || be instanceof com.sqc.nuclearsq.robotics.RobotArmBlockEntity || be instanceof com.sqc.nuclearsq.robotics.RodGripperBlockEntity
                || be instanceof ContaminationMonitorBlockEntity || be instanceof com.sqc.nuclearsq.storage.ItemPortBlockEntity
                || be instanceof com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity
                || be instanceof com.sqc.nuclearsq.reactor.XenonFilterBlockEntity || be instanceof com.sqc.nuclearsq.crate.StorageCrateBlockEntity
                || be instanceof com.sqc.nuclearsq.door.DoorBlockEntity || be instanceof com.sqc.nuclearsq.door.AirlockBlockEntity;
    }

    @Nullable
    public static DataSource of(@Nullable BlockEntity be) {
        if (be instanceof SolarPanelBlockEntity m) return new Solar(m);
        if (be instanceof RtgBlockEntity m) return new Rtg(m);
        if (be instanceof CastorCaskBlockEntity m) return new Castor(m);
        if (be instanceof DeconShowerBlockEntity m) return new Shower(m);
        if (be instanceof AreaSensorBlockEntity m) return new AreaSensor(m);
        if (be instanceof AreaMonitorBlockEntity m) return new AreaMonitor(m);
        if (be instanceof WifiAdapterBlockEntity m) return new WifiAdapter(m);
        if (be instanceof com.sqc.nuclearsq.robotics.RobotArmBlockEntity m) return new RobotArm(m);
        if (be instanceof com.sqc.nuclearsq.robotics.RodGripperBlockEntity m) return new RodGripper(m);
        if (be instanceof ContaminationMonitorBlockEntity m) return new ContaminationMon(m);
        if (be instanceof com.sqc.nuclearsq.storage.ItemPortBlockEntity m) return new ItemPort(m);
        if (be instanceof com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity m) return new NetPipeAdapter(m);
        if (be instanceof com.sqc.nuclearsq.reactor.XenonFilterBlockEntity m) return new XenonFilter(m);
        if (be instanceof com.sqc.nuclearsq.crate.StorageCrateBlockEntity m) return new Crate(m);
        if (be instanceof com.sqc.nuclearsq.door.DoorBlockEntity m) return new Door(m);
        if (be instanceof com.sqc.nuclearsq.door.AirlockBlockEntity m) return new Airlock(m);
        return null;
    }

    static Component label(String key) {
        return DataSources.label(key);
    }

    static DataValue tank(String key, Component label, FluidTank t) {
        return new DataValue("tank." + key, label, t.getFluidAmount(), t.getCapacity(), "mB");
    }

    static DataValue flag(String key, boolean on) {
        return new DataValue(key, label(key), on ? 1 : 0, 1, "");
    }

    static Component fluidName(FluidStack fs, String fallbackKey) {
        return fs.isEmpty() ? Component.translatable(fallbackKey) : fs.getHoverName();
    }

    private abstract static class Base<T extends BlockEntity> implements DataSource {
        final T be;

        Base(T be) {
            this.be = be;
        }

        @Override
        public BlockPos pos() {
            return be.getBlockPos();
        }

        @Override
        public Component name() {
            return be.getBlockState().getBlock().getName();
        }

        @Override
        public String type() {
            return BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath().replace("_sqc", "");
        }

        @Nullable
        Boolean enabled() {
            return null;
        }

        void setEnabled(boolean on) {}

        List<String> dumpable() {
            return List.of();
        }

        void dump(String tank) {}

        abstract void collect(List<DataValue> out);

        @Override
        public final List<DataValue> values() {
            List<DataValue> out = new ArrayList<>();
            collect(out);
            Boolean on = enabled();
            if (on != null) out.add(flag("enable", on));
            return out;
        }

        @Override
        public List<String> settable() {
            List<String> out = new ArrayList<>();
            if (enabled() != null) out.add("enable");
            for (String t : dumpable()) out.add("dump." + t);
            return out;
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("enable") && enabled() != null) {
                setEnabled(value != 0);
                return null;
            }
            if (key.startsWith("dump.") && dumpable().contains(key.substring(5))) {
                dump(key.substring(5));
                return null;
            }
            return Component.translatable("data.nuclearsq.err.readonly", key);
        }

        @Override
        public void open(ServerPlayer player) {
            BlockPos p = pos();
            be.getBlockState().useWithoutItem(be.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(p), net.minecraft.core.Direction.UP, p, false));
        }

        void openDevice(ServerPlayer player, int kind, net.minecraft.world.inventory.ContainerData data) {
            DeviceMenu.open(player, pos(), kind, data, Component.translatable(be.getBlockState().getBlock().getDescriptionId()));
        }
    }

    static final class Solar extends Base<SolarPanelBlockEntity> {
        Solar(SolarPanelBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return be.rate() > 0 ? OK : OFF;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(be.rate() > 0 ? "running" : "no_sun");
        }

        @Override
        public PowerFlow power() {
            return PowerFlow.producer(be.rate(), be.peak());
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("fe_rate", label("fe_rate"), be.rate(), be.peak(), "FE/t"));
            out.add(new DataValue("out_rate", label("out_rate"), be.delivered(), be.peak(), "FE/t"));
        }
    }

    static final class Rtg extends Base<RtgBlockEntity> {
        Rtg(RtgBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return be.rate() > 0 ? OK : OFF;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(!be.hasCapsule() ? "no_capsule" : be.rate() > 0 ? "running" : "spent");
        }

        @Override
        public PowerFlow power() {
            return PowerFlow.producer(be.rate(), RtgBlockEntity.PEAK_PU238);
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("fe_rate", label("fe_rate"), be.rate(), RtgBlockEntity.PEAK_PU238, "FE/t"));
            out.add(new DataValue("out_rate", label("out_rate"), be.delivered(), RtgBlockEntity.PEAK_PU238, "FE/t"));
            out.add(new DataValue("capsule", label("capsule"), be.remainingPercent(), 100, "%"));
        }
    }

    static final class WifiAdapter extends Base<WifiAdapterBlockEntity> {
        WifiAdapter(WifiAdapterBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return !be.enabled() ? OFF : be.online() ? OK : WARN;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(!be.enabled() ? "off" : !be.online() ? "no_power" : be.charging() > 0 ? "charging" : "running");
        }

        @Override
        void collect(List<DataValue> out) {
            int max = com.sqc.nuclearsq.storage.WifiValues.adapter().maxAntennas();
            out.add(new DataValue("range", label("range"), be.unlimited() ? Double.POSITIVE_INFINITY : be.range(),
                    com.sqc.nuclearsq.storage.WifiValues.adapter().maxFinite(), "m"));
            out.add(new DataValue("antennas", label("antennas"), be.antennas(), max, ""));
            out.add(new DataValue("energy", label("energy"), be.stored(), be.capacity(), "FE"));
            out.add(new DataValue("charging", label("charging"), be.charging(), 0, ""));
        }

        @Override
        Boolean enabled() {
            return be.enabled();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEnabled(on);
        }
    }

    static final class XenonFilter extends Base<com.sqc.nuclearsq.reactor.XenonFilterBlockEntity> {
        XenonFilter(com.sqc.nuclearsq.reactor.XenonFilterBlockEntity be) {
            super(be);
        }

        private boolean powered() {
            return be.energy().getEnergyStored() >= com.sqc.nuclearsq.reactor.XenonFilterBlockEntity.FE_PER_TICK * 20;
        }

        @Override
        public int state() {
            return be.filters() > 0 && powered() ? OK : WARN;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(be.filters() == 0 ? "no_filter" : !powered() ? "no_power" : "running");
        }

        @Override
        public PowerFlow power() {
            return PowerFlow.consumer(be.energy().drainedTotal(), com.sqc.nuclearsq.reactor.XenonFilterBlockEntity.FE_PER_TICK);
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("filter_cartridges", label("filter_cartridges"), be.filters(), com.sqc.nuclearsq.reactor.XenonFilterBlockEntity.SLOTS, ""));
            out.add(new DataValue("filter_left", label("filter_left"), be.leftPercent(), 100, "%"));
            out.add(new DataValue("energy", label("energy"), be.energy().getEnergyStored(), be.energy().getMaxEnergyStored(), "FE"));
        }
    }

    static final class Door extends Base<com.sqc.nuclearsq.door.DoorBlockEntity> {
        Door(com.sqc.nuclearsq.door.DoorBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return be.moving() ? WARN : be.open() ? OK : OFF;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(be.moving() ? be.open() ? "door_opening" : "door_closing" : be.open() ? "door_open" : "door_closed");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(flag("open", be.open()));
            out.add(flag("door_moving", be.moving()));
        }

        @Override
        public List<String> settable() {
            return List.of("open");
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            return key.equals("open") ? be.setOpen(value != 0) : super.set(key, value);
        }

        @Override
        public void open(ServerPlayer player) {
            be.openTerminal(player);
        }
    }

    static final class Airlock extends Base<com.sqc.nuclearsq.door.AirlockBlockEntity> {
        Airlock(com.sqc.nuclearsq.door.AirlockBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return be.busy() ? WARN : be.openDoor() > 0 ? OK : OFF;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(be.busy() ? "airlock_cycling" : be.openDoor() > 0 ? "door_open" : "door_closed");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("door", label("airlock_door"), be.openDoor(), 2, ""));
            out.add(flag("airlock_busy", be.busy()));
        }

        @Override
        public List<String> settable() {
            return List.of("cycle", "door");
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("cycle")) be.cycle();
            else if (key.equals("door")) be.request((int) Math.max(0, Math.min(2, value)) - 1);
            else return super.set(key, value);
            return null;
        }

        @Override
        public void open(ServerPlayer player) {
            be.openTerminal(player);
        }
    }

    static final class RobotArm extends Base<com.sqc.nuclearsq.robotics.RobotArmBlockEntity> {
        RobotArm(com.sqc.nuclearsq.robotics.RobotArmBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            if (!be.enabled()) return OFF;
            String p = be.problem();
            return p.isEmpty() ? OK : p.equals("power") ? ALARM : p.equals("idle") || p.equals("paused") ? OFF : WARN;
        }

        @Override
        public Component stateText() {
            String p = be.problem();
            return DataSources.stateLabel(!be.enabled() || p.equals("paused") ? "off" : p.equals("power") ? "no_power" : p.equals("idle") ? "idle" : "running");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("energy", label("energy"), be.stored(), com.sqc.nuclearsq.robotics.RobotArmBlockEntity.BUFFER, "FE"));
            out.add(new DataValue("moved", label("moved"), be.lastMoved(), 64, ""));
        }

        @Override
        Boolean enabled() {
            return be.enabled();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEnabled(on);
        }
    }

    static final class RodGripper extends Base<com.sqc.nuclearsq.robotics.RodGripperBlockEntity> {
        RodGripper(com.sqc.nuclearsq.robotics.RodGripperBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            if (!be.enabled()) return OFF;
            String p = be.problem();
            return p.isEmpty() ? OK : p.equals("power") ? ALARM : p.equals("idle") ? OFF : WARN;
        }

        @Override
        public Component stateText() {
            String p = be.problem();
            return DataSources.stateLabel(!be.enabled() || p.equals("paused") ? "off" : p.equals("power") ? "no_power" : p.equals("idle") ? "idle"
                    : p.equals("no_rod") ? "no_rod" : p.equals("blocked") ? "hook_blocked" : p.equals("clamped") ? "clamped" : "running");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("energy", label("energy"), be.stored(), com.sqc.nuclearsq.robotics.RodGripperBlockEntity.BUFFER, "FE"));
            out.add(new DataValue("hook", label("hook"), be.depth(), com.sqc.nuclearsq.robotics.RodGripperBlockEntity.MAX_DEPTH, ""));
            out.add(flag("grip", be.holding()));
        }

        @Override
        Boolean enabled() {
            return be.enabled();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEnabled(on);
        }
    }

    static final class Crate extends Base<com.sqc.nuclearsq.crate.StorageCrateBlockEntity> {
        Crate(com.sqc.nuclearsq.crate.StorageCrateBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return be.eject() && be.lastMoved() > 0 ? OK : OFF;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(be.eject() && be.lastMoved() > 0 ? "running" : "idle");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("fill", label("fill"), be.used(), be.capacity(), ""));
            out.add(new DataValue("types", label("types"), be.types(), be.capacity(), ""));
            out.add(new DataValue("moved", label("moved"), be.lastMoved(), com.sqc.nuclearsq.crate.StorageCrateBlockEntity.EJECT, ""));
        }

        @Override
        Boolean enabled() {
            return be.eject();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEject(on);
        }
    }

    static final class ItemPort extends Base<com.sqc.nuclearsq.storage.ItemPortBlockEntity> {
        ItemPort(com.sqc.nuclearsq.storage.ItemPortBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            if (!be.enabled()) return OFF;
            String p = be.problem();
            return p.isEmpty() ? OK : p.equals("no_inventory") || p.equals("no_storage") || p.equals("no_filter") ? WARN : OFF;
        }

        @Override
        public Component stateText() {
            String p = be.problem();
            return DataSources.stateLabel(!be.enabled() || p.equals("paused") ? "off" : p.equals("no_inventory") ? "no_inventory"
                    : p.equals("no_storage") ? "no_storage" : p.equals("no_filter") ? "no_filter" : p.equals("idle") ? "idle" : "running");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("mode", label("item_port_mode"), be.mode(), 1, ""));
            out.add(new DataValue("moved", label("moved"), be.lastMoved(), com.sqc.nuclearsq.storage.ItemPortBlockEntity.AMOUNT, ""));
            out.add(new DataValue("filters", label("filters"), be.filterCount(), com.sqc.nuclearsq.storage.ItemPortBlockEntity.FILTER_SLOTS, ""));
        }

        @Override
        public List<String> settable() {
            List<String> out = new ArrayList<>(super.settable());
            out.add("mode");
            return out;
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("mode")) {
                if (value != 0 && value != 1) return DataSources.outOfRange(key, 0, 1);
                be.setMode((int) value);
                return null;
            }
            return super.set(key, value);
        }

        @Override
        Boolean enabled() {
            return be.enabled();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEnabled(on);
        }
    }

    static final class NetPipeAdapter extends Base<com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity> {
        NetPipeAdapter(com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            if (!be.enabled()) return OFF;
            String p = be.problem();
            return p.isEmpty() || p.equals("idle") ? OK : p.equals("paused") ? OFF : WARN;
        }

        @Override
        public Component stateText() {
            String p = be.problem();
            return DataSources.stateLabel(!be.enabled() || p.equals("paused") ? "off" : p.equals("no_target") || p.equals("no_pattern") ? "no_inventory"
                    : p.equals("no_storage") ? "no_storage" : p.equals("no_filter") ? "no_filter" : p.equals("idle") || p.equals("missing") ? "idle" : "running");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(new DataValue("mode", label("net_pipe_adapter_mode"), be.mode(), com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity.MODES - 1, ""));
            out.add(new DataValue("moved", label("moved"), be.lastMoved(), 256, ""));
            out.add(new DataValue("filters", label("filters"), be.filterCount(), com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity.FILTER_SLOTS, ""));
        }

        @Override
        public List<String> settable() {
            List<String> out = new ArrayList<>(super.settable());
            out.add("mode");
            return out;
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("mode")) {
                int max = com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity.MODES - 1;
                if (value != Math.floor(value) || value < 0 || value > max) return DataSources.outOfRange(key, 0, max);
                be.setMode((int) value);
                return null;
            }
            return super.set(key, value);
        }

        @Override
        Boolean enabled() {
            return be.enabled();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEnabled(on);
        }
    }

    static final class ContaminationMon extends Base<ContaminationMonitorBlockEntity> {
        ContaminationMon(ContaminationMonitorBlockEntity be) {
            super(be);
        }

        private boolean alarm() {
            return be.getBlockState().getValue(ContaminationMonitorBlock.ALARM);
        }

        @Override
        public int state() {
            return alarm() ? ALARM : OK;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(alarm() ? "contamination_alarm" : "contamination_clear");
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(flag("alarm", alarm()));
            out.add(new DataValue("contamination_threshold", label("contamination_threshold"),
                    ContaminationMonitorBlockEntity.THRESHOLD_SV_H, 0, "Sv/h"));
        }
    }

    static final class Castor extends Base<CastorCaskBlockEntity> {
        Castor(CastorCaskBlockEntity be) {
            super(be);
        }

        private int total() {
            int[] c = be.counts();
            return c[0] + c[1];
        }

        @Override
        public int state() {
            return total() > 0 ? OK : OFF;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(total() > 0 ? "loaded" : "empty");
        }

        @Override
        void collect(List<DataValue> out) {
            int[] c = be.counts();
            out.add(new DataValue("rods", label("rods"), c[0], be.capacity(), ""));
            out.add(new DataValue("cooled", label("cooled"), c[1], be.capacity(), ""));
            out.add(new DataValue("fill", label("fill"), c[0] + c[1], be.capacity(), ""));
        }

        @Override
        public void open(ServerPlayer player) {
            com.sqc.nuclearsq.spent.LargeCastorBlock.status(player, be);
        }
    }

    static final class Shower extends Base<DeconShowerBlockEntity> {
        Shower(DeconShowerBlockEntity be) {
            super(be);
        }

        private String key() {
            if (!be.enabled()) return "off";
            if (be.getBlockState().getValue(DeconShowerBlock.ACTIVE)) return "running";
            return be.tank().getFluidAmount() < DeconShowerBlockEntity.USE ? "no_water" : "idle";
        }

        @Override
        public int state() {
            return switch (key()) {
                case "running" -> OK;
                case "no_water" -> WARN;
                default -> OFF;
            };
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(key());
        }

        @Override
        void collect(List<DataValue> out) {
            out.add(tank("water", Component.translatable("block.minecraft.water"), be.tank()));
        }

        @Override
        Boolean enabled() {
            return be.enabled();
        }

        @Override
        void setEnabled(boolean on) {
            be.setEnabled(on);
        }

        @Override
        List<String> dumpable() {
            return List.of("water");
        }

        @Override
        void dump(String tank) {
            be.tank().setFluid(FluidStack.EMPTY);
            be.setChanged();
        }
    }

    static class AreaMonitor extends Base<AreaMonitorBlockEntity> {
        private static final String USV = "\u00B5Sv/h";

        AreaMonitor(AreaMonitorBlockEntity be) {
            super(be);
        }

        @Override
        public int state() {
            return be.alarm() ? ALARM : OK;
        }

        @Override
        public Component stateText() {
            return DataSources.stateLabel(be.alarm() ? "radiation_alarm" : "measuring");
        }

        @Override
        void collect(List<DataValue> out) {
            double t = be.threshold();
            out.add(new DataValue("dose_rate_usv", label("dose_rate_usv"), be.rate() * 1e6, t * 1e6, USV));
            out.add(new DataValue("dose_rate", label("dose_rate"), be.rate(), t, "Sv/h"));
            out.add(new DataValue("peak_usv", label("peak_usv"), be.peak() * 1e6, t * 1e6, USV));
            out.add(new DataValue("peak", label("peak"), be.peak(), t, "Sv/h"));
            out.add(new DataValue("threshold_usv", label("threshold_usv"), t * 1e6, 0, USV));
            out.add(new DataValue("threshold", label("threshold"), t, 0, "Sv/h"));
            out.add(flag("alarm", be.alarm()));
        }

        @Override
        public List<String> settable() {
            return List.of("threshold", "threshold_usv", "reset_peak");
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            switch (key) {
                case "threshold", "threshold_usv" -> {
                    double sv = key.equals("threshold") ? value : value * 1e-6;
                    if (!(sv > 0) || Double.isInfinite(sv)) return Component.translatable("data.nuclearsq.err.positive", key);
                    be.setThreshold(sv);
                    return null;
                }
                case "reset_peak" -> {
                    be.resetPeak();
                    return null;
                }
                default -> {
                    return super.set(key, value);
                }
            }
        }
    }

    static final class AreaSensor extends AreaMonitor {
        private static final String USV = "\u00B5Sv/h";
        private final AreaSensorBlockEntity sensor;

        AreaSensor(AreaSensorBlockEntity be) {
            super(be);
            sensor = be;
        }

        @Override
        void collect(List<DataValue> out) {
            super.collect(out);
            double t = sensor.threshold();
            BlockPos h = sensor.hottest();
            out.add(new DataValue("dose_rate_max_usv", label("dose_rate_max_usv"), sensor.rate() * 1e6, t * 1e6, USV));
            out.add(new DataValue("dose_rate_avg_usv", label("dose_rate_avg_usv"), sensor.average() * 1e6, t * 1e6, USV));
            out.add(new DataValue("radius", label("radius"), sensor.radius(), AreaSensorBlockEntity.MAX_RADIUS, ""));
            out.add(new DataValue("hottest_x", label("hottest_x"), h.getX(), 0, ""));
            out.add(new DataValue("hottest_y", label("hottest_y"), h.getY(), 0, ""));
            out.add(new DataValue("hottest_z", label("hottest_z"), h.getZ(), 0, ""));
        }

        @Override
        public List<String> settable() {
            return List.of("threshold", "threshold_usv", "reset_peak", "radius");
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("radius")) {
                if (!(value > 0) || Double.isInfinite(value)) return Component.translatable("data.nuclearsq.err.positive", key);
                sensor.setRadius((int) Math.round(value));
                return null;
            }
            return super.set(key, value);
        }
    }
}
