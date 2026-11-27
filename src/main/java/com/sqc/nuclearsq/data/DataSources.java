package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigBattery;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.machine.big.CoreChemistry;
import com.sqc.nuclearsq.machine.big.DriveCore;
import com.sqc.nuclearsq.machine.big.Substation;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.StorageValues;
import com.sqc.nuclearsq.reactor.PartStages;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;
import com.sqc.nuclearsq.turbine.TurbinePortBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class DataSources {
    private DataSources() {}

    public static boolean isPort(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof BigMachineBlockEntity || be instanceof BigPortBlockEntity
                || be instanceof TurbineControllerBlockEntity || be instanceof TurbinePortBlockEntity || SingleSources.is(be);
    }

    public static boolean controllerUnloaded(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        BlockPos c = be instanceof BigPortBlockEntity p ? p.controllerPos() : be instanceof TurbinePortBlockEntity t ? t.controllerPos() : null;
        return c != null && !ChunkGuard.loaded(level, c);
    }

    @Nullable
    public static DataSource at(Level level, BlockPos pos) {
        return of(level.getBlockEntity(pos));
    }

    @Nullable
    public static DataSource of(@Nullable BlockEntity be) {
        if (be instanceof BigPortBlockEntity p) {
            BlockEntity c = p.findController();
            be = c != null ? c : p.findTurbine();
        }
        else if (be instanceof TurbinePortBlockEntity p) be = p.findController();
        if (be instanceof BigMachineBlockEntity m) return new Big(m);
        if (be instanceof TurbineControllerBlockEntity t) return new Turbine(t);
        return SingleSources.of(be);
    }

    static Component label(String key) {
        return Component.translatable("data.nuclearsq.val." + key);
    }

    static Component stateLabel(String key) {
        return Component.translatable("data.nuclearsq.state." + key);
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static Component outOfRange(String key, int min, int max) {
        return Component.translatable("data.nuclearsq.err.range", key, min, max);
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
    }

    static final class Big extends Base<BigMachineBlockEntity> {
        Big(BigMachineBlockEntity be) {
            super(be);
        }

        private boolean rodDrive() {
            return be.machine() == BigMachine.CONTROL_ROD_DRIVE;
        }

        @Override
        public boolean mayControl(ServerPlayer player) {
            return be.access().can(player, be.owner(), com.sqc.nuclearsq.machine.MachineAccess.USE);
        }

        private boolean switchable() {
            return !rodDrive() && !be.machine().isCoolingTower();
        }

        @Nullable
        private String coreKey() {
            DriveCore c = be.core();
            if (!rodDrive() || c == null || !be.isFormed()) return null;
            return c.overheated() ? "overheated" : c.hot() ? "hot" : null;
        }

        @Nullable
        private String driveKey() {
            if (!rodDrive() || !be.isFormed() || be.stateKey().equals("no_power")) return null;
            if (be.vesselDry()) return "no_flow";
            if (be.rodFault()) return "no_hydraulic";
            return be.rodCount() == 0 ? "no_rods" : null;
        }

        @Nullable
        private String crafterKey() {
            BigCrafter c = be.crafter();
            if (c == null || !be.isFormed() || c.status() == BigCrafter.OK || c.status() == BigCrafter.INCOMPLETE) return null;
            String k = be.stateKey();
            if (!k.equals("running") && !k.equals("idle")) return null;
            return CRAFTER_STATES[Mth.clamp(c.status(), 0, CRAFTER_STATES.length - 1)];
        }

        @Nullable
        private String batteryKey() {
            BigBattery b = be.battery();
            if (b == null || !be.isFormed()) return null;
            String k = be.stateKey();
            if (!k.equals("running") && !k.equals("idle")) return null;
            return b.lastIn() > b.lastOut() ? "charging" : b.lastOut() > b.lastIn() ? "discharging" : "idle";
        }

        private static final String[] CRAFTER_STATES = {"ok", "incomplete", "noinput", "needmore", "outfull", "nopower", "off", "nowater", "nofeed", "shallow", "noacid", "noaux", "noarm"};

        @Override
        public int state() {
            if (!be.isFormed()) return ALARM;
            if (coreKey() != null) return ALARM;
            String drive = driveKey();
            if (drive != null) return drive.equals("no_flow") ? ALARM : WARN;
            String craft = crafterKey();
            if (craft != null) return switch (craft) {
                case "nopower" -> ALARM;
                case "off" -> OFF;
                default -> WARN;
            };
            String bat = batteryKey();
            if (bat != null) return bat.equals("idle") ? OFF : OK;
            return switch (be.stateKey()) {
                case "running" -> OK;
                case "idle" -> WARN;
                case "off", "standby" -> OFF;
                default -> ALARM;
            };
        }

        @Override
        public Component stateText() {
            String core = coreKey();
            if (core != null) return stateLabel(core);
            String drive = driveKey();
            if (drive != null) return stateLabel(drive);
            String craft = crafterKey();
            if (craft != null) {
                String k = "gui.nuclearsq.big_crafter.state." + (be.isGenerator() && craft.equals("outfull") ? "gen.outfull" : craft);
                return craft.equals("needmore") ? Component.translatable(k, be.crafter().needed()) : Component.translatable(k);
            }
            String bat = batteryKey();
            if (bat != null) return stateLabel(bat);
            return Component.translatable("gui.nuclearsq.device.big.state." + (be.isFormed() ? be.stateKey() : "unformed"));
        }

        @Override
        public PowerFlow power() {
            BigBattery b = be.battery();
            if (b != null) {
                MachineEnergy st = b.store();
                return PowerFlow.storage(st.getEnergyStored(), st.getMaxEnergyStored(), b.lastIn(), b.lastOut());
            }
            if (be.substation() != null) return null;
            com.sqc.nuclearsq.machine.big.FusionCore fu = be.fusion();
            if (fu != null) return PowerFlow.producer(Math.max(0, fu.lastNet()), fu.fullPower());
            if (be.isGenerator()) return PowerFlow.producer(be.lastOut(), be.generatorPeak());
            MachineEnergy e = be.energy();
            return e == null ? null : PowerFlow.consumer(e.drainedTotal(), 0);
        }

        @Override
        public List<String> priority() {
            return rodDrive() ? List.of("power", "heat", "fuel", "rod", "tank.primary_cold_in", "primary_flow") : List.of();
        }

        @Override
        public List<DataValue> values() {
            List<DataValue> out = new ArrayList<>();
            MachineEnergy e = be.energy();
            if (e != null && be.isGenerator()) out.add(new DataValue("out_rate", label("out_rate"), be.lastOut(), be.generatorPeak(), "FE/t"));
            else if (e != null) out.add(new DataValue("energy", label("energy"), e.getEnergyStored(), e.getMaxEnergyStored(), "FE"));
            BigCrafter c = be.crafter();
            if (c != null && c.maxProgress() > 0) {
                out.add(new DataValue("progress", label("progress"), Math.round(c.progress() * 100.0 / c.maxProgress()), 100, "%"));
            }
            extras(out);
            String id = be.machine().id;
            boolean signal = false;
            for (BigMachine.Port p : be.machine().ports) {
                FluidTank t = be.tank(p.name());
                if (t != null) {
                    out.add(new DataValue("tank." + p.name(), Component.translatable("port.nuclearsq." + id + "." + p.name()),
                            t.getFluidAmount(), t.getCapacity(), "mB"));
                }
                signal |= p.kind() == BigPortKind.SIGNAL;
            }
            if (rodDrive()) {
                out.add(new DataValue("rod", label("rod"), be.rod(), BigMachineBlockEntity.ROD_MAX, ""));
                out.add(new DataValue("rod_target", label("rod_target"), be.rodTarget(), BigMachineBlockEntity.ROD_MAX, ""));
                out.add(new DataValue("vessel_temp", label("vessel_temp"), be.vesselTemp(), 0, "°C"));
                out.add(new DataValue("rod_mode", label("rod_mode"), be.terminalMode() ? 1 : 0, 1, ""));
                out.add(new DataValue("channel", label("channel"), be.channel(), BigMachineBlockEntity.CHANNELS, ""));
                DriveCore core = be.core();
                if (core != null) {
                    out.add(new DataValue("power", label("power"), be.reactorPower(), DriveCore.MAX_POWER, "FE/t"));
                    out.add(new DataValue("heat", label("heat"), core.heatPercent(), 100, "%"));
                    out.add(new DataValue("fuel", label("fuel"), be.fuelPercent(), 100, "%"));
                    out.add(new DataValue("primary_flow", label("primary_flow"), be.vesselMb(), 0, "mB/t"));
                    out.add(new DataValue("cooling", label("cooling"), Math.round(core.cooling() * 100) / 100.0, 0, ""));
                    out.add(new DataValue("overheated", label("overheated"), core.overheated() ? 1 : 0, 1, ""));
                    out.add(new DataValue("gas", label("gas"), core.chem().gasPercent(), 100, "%"));
                    out.add(new DataValue("xenon", label("xenon"), core.chem().xenonPercent(), 100, "%"));
                    out.add(new DataValue("carbon_filter", label("carbon_filter"), core.chem().filters(), 0, ""));
                    out.add(new DataValue("recombiners", label("recombiners"), core.chem().recombiners(), 0, ""));
                    out.add(new DataValue("boron", label("boron"), core.chem().boron(), CoreChemistry.BORON_MAX, "ppm"));
                    out.add(new DataValue("crud", label("crud"), core.chem().crudPercent(), 100, "%"));
                }
                if (be.moderator() > 0) out.add(new DataValue("moderator", label("moderator"), be.moderator(), PartStages.MODERATORS.size(), ""));
                if (be.cooler() > 0) out.add(new DataValue("cooler", label("cooler"), be.cooler(), PartStages.COOLERS.size(), ""));
            }
            if (signal) out.add(new DataValue("signal", label("signal"), be.signalOut(), 15, ""));
            if (switchable()) out.add(new DataValue("enable", label("enable"), be.enabled() ? 1 : 0, 1, ""));
            if (be.machine().isEmergency()) {
                out.add(new DataValue("manual", label("manual"), be.manual() ? 1 : 0, 1, ""));
                out.add(new DataValue("low_pressure", label("low_pressure"), be.lowPressureStart() ? 1 : 0, 1, ""));
            }
            if (be.breeder() != null) {
                var br = be.breeder();
                out.add(new DataValue("breeder_load", label("breeder_load"), Math.round(br.load() * 100), 100, "%"));
                out.add(new DataValue("breeder_flux", label("breeder_flux"), Math.round(br.flux() * 100), 125, "%"));
                out.add(new DataValue("drivers", label("drivers"), br.activeDrivers(), 4, ""));
                out.add(new DataValue("sodium_leak", label("sodium_leak"), br.leak() ? 1 : 0, 1, ""));
                out.add(new DataValue("wear", label("wear"), br.wearPercent(), 100, "%"));
            }
            if (be.machine() == BigMachine.STEAM_GENERATOR) {
                out.add(new DataValue("tube_leak", label("tube_leak"), be.tubeLeak() ? 1 : 0, 1, ""));
                out.add(new DataValue("wear", label("wear"), be.wearPercent(), 100, "%"));
            }
            if (be.machine() == BigMachine.PRESSURIZER) {
                out.add(new DataValue("pressure", label("pressure"), Math.round(be.pressure() * 10) / 10.0, 100, "%"));
                out.add(new DataValue("pressure_bar", label("pressure_bar"), Math.round(be.pressure() * BigMachineBlockEntity.BAR_PER_PERCENT), 0, "bar"));
                out.add(new DataValue("venting", label("venting"), be.venting() ? 1 : 0, 1, ""));
            }
            com.sqc.nuclearsq.machine.big.FusionCore fu = be.fusion();
            if (fu != null) {
                out.add(new DataValue("plasma_temp", label("plasma_temp"), fu.temperature(), com.sqc.nuclearsq.machine.big.FusionCore.MAX_MK, "MK"));
                out.add(new DataValue("burn", label("burn"), fu.burnSetting(), 100, "%"));
                out.add(new DataValue("burn_now", label("burn_now"), fu.burnNow(), 100, "%"));
                out.add(new DataValue("gross_power", label("gross_power"), fu.lastGross(), fu.fullPower(), "FE/t"));
                out.add(new DataValue("net_power", label("net_power"), fu.lastNet(), fu.fullPower(), "FE/t"));
                out.add(new DataValue("ignition", label("ignition"), fu.ignitionPercent(), 100, "%"));
                out.add(new DataValue("wall_heat", label("wall_heat"), Math.min(100, 100 * fu.wallHeat() / Math.max(1, be.machine().extra("wall_heat_max"))), 100, "%"));
                out.add(new DataValue("wear", label("wear"), fu.wearPercent(), 100, "%"));
                out.add(new DataValue("dd_mode", label("dd_mode"), fu.ddMode() && fu.burning() ? 1 : 0, 1, ""));
            }
            if (rodDrive() && be.primaryPressure() >= 0) out.add(new DataValue("pressure", label("pressure"), Math.round(be.primaryPressure() * 10) / 10.0, 100, "%"));
            return out;
        }

        private void extras(List<DataValue> out) {
            BigBattery b = be.battery();
            if (b != null) {
                out.add(new DataValue("in_rate", label("in_rate"), b.lastIn(), b.rate(), "FE/t"));
                out.add(new DataValue("out_rate", label("out_rate"), b.lastOut(), b.rate(), "FE/t"));
            }
            if (be.machine().isWind()) {
                out.add(new DataValue("wind_height", label("wind_height"), be.windHeightPct(), 100, "%"));
                out.add(new DataValue("wind_surround", label("wind_surround"), be.windSurroundPct(), 100, "%"));
                out.add(new DataValue("wind_gust", label("wind_gust"), be.windGustPct(), 100, "%"));
            }
            Substation st = be.substation();
            if (st != null) {
                out.add(new DataValue("in_rate", label("in_rate"), st.inRate(), st.lineRate(), "FE/t"));
                out.add(new DataValue("line_rate", label("line_rate"), st.lineRateNow(), st.lineRate(), "FE/t"));
                out.add(new DataValue("out_rate", label("out_rate"), st.outRate(), st.lineRate(), "FE/t"));
            }
            com.sqc.nuclearsq.storage.ClusterMachine cluster = be.cluster();
            if (cluster != null) {
                var th = cluster.thermal();
                var mv = com.sqc.nuclearsq.storage.ClusterValues.machine();
                out.add(new DataValue("cpu", label("cpu"), cluster.supply().cpu(), mv.cpu(), ""));
                out.add(new DataValue("ram", label("ram"), cluster.supply().ram() / (double) StorageValues.MB_PER_GB, mv.ramMb() / (double) StorageValues.MB_PER_GB, "GB"));
                out.add(new DataValue("heat", label("heat"), Math.round(th.heat()), Math.round(com.sqc.nuclearsq.storage.ClusterValues.heat().shutdown()), ""));
                out.add(new DataValue("throttle", label("throttle"), Math.round(th.throttle() * 100), 100, "%"));
            }
            ServerRack rack = be.rack();
            if (rack != null) {
                if (rack.isPatternRack()) {
                    out.add(new DataValue("cards", label("cards"), rack.patterns().size(), rack.slots(), ""));
                } else {
                ServerRack.Space sp = rack.space();
                out.add(new DataValue("storage_used", label("storage_used"), sp.used(), sp.capacity(), ""));
                out.add(new DataValue("types", label("types"), sp.types(), sp.typeCapacity(), ""));
                out.add(new DataValue("drives", label("drives"), sp.drives(), rack.slots(), ""));
                out.add(new DataValue("priority", label("priority"), rack.priority(), ServerRack.MAX_PRIORITY, ""));
                ServerRack.Compute c = rack.compute();
                if (c.modules() > 0) {
                    out.add(new DataValue("cpu", label("cpu"), c.cpu(), 0, ""));
                    out.add(new DataValue("ram", label("ram"), c.ram() / (double) StorageValues.MB_PER_GB, 0, "GB"));
                }
                }
            }
            ComponentAssembler asm = be.assembler();
            if (asm != null) {
                out.add(new DataValue("progress", label("progress"), asm.progressPercent(), 100, "%"));
                out.add(new DataValue("jobs", label("jobs"), asm.jobs().size(), asm.maxJobs(), ""));
                out.add(new DataValue("lanes", label("lanes"), asm.busyLanes(), asm.laneCap(), ""));
                out.add(new DataValue("tempo", label("tempo"), asm.tempoLevel(), com.sqc.nuclearsq.storage.StorageValues.assembler().tempoMax(), ""));
            }
            if (be.machine() == BigMachine.SPENT_FUEL_RACK) {
                int rods = 0;
                for (com.sqc.nuclearsq.machine.big.RodShaft s : be.shafts()) if (s.headY() >= 0) rods++;
                out.add(new DataValue("rods", label("rods"), rods, be.shafts().length, ""));
            }
            if (flows()) out.add(new DataValue("rate", label("rate"), be.lastMb(), 0, "mB/t"));
        }

        private boolean flows() {
            if (rodDrive() || be.crafter() != null || be.isGenerator() || be.machine() == BigMachine.CONTROL_DESK) return false;
            for (BigMachine.Port p : be.machine().ports) if (be.tank(p.name()) != null) return true;
            return false;
        }

        @Override
        public List<String> settable() {
            List<String> out = new ArrayList<>();
            if (rodDrive()) {
                out.add("rod");
                out.add("scram");
                out.add("rod_mode");
                out.add("channel");
                for (int i = 0; i < be.shafts().length; i++) out.add("lock." + (i + 1));
            }
            if (switchable()) out.add("enable");
            if (be.machine().isEmergency()) out.add("manual");
            if (be.rack() != null && !be.rack().isPatternRack()) out.add("priority");
            if (be.hasWear()) out.add("repair");
            if (be.fusion() != null) out.add("burn");
            for (BigMachine.Port p : be.machine().ports) if (be.tank(p.name()) != null) out.add("dump." + p.name());
            return out;
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("enable") && switchable()) {
                be.setEnabled(value != 0);
                return null;
            }
            if (key.equals("rod") && rodDrive()) {
                if (!be.terminalMode()) return Component.translatable("data.nuclearsq.err.rod_redstone");
                int v = (int) Math.round(value);
                if (v < 0 || v > BigMachineBlockEntity.ROD_MAX) return outOfRange(key, 0, BigMachineBlockEntity.ROD_MAX);
                be.setRodTarget(v);
                return null;
            }
            if (key.equals("scram") && rodDrive()) {
                be.scram();
                return null;
            }
            if (key.equals("rod_mode") && rodDrive()) {
                be.setTerminalMode(value != 0);
                return null;
            }
            if (key.equals("channel") && rodDrive()) {
                int v = (int) Math.round(value);
                if (v < 0 || v > BigMachineBlockEntity.CHANNELS) return outOfRange(key, 0, BigMachineBlockEntity.CHANNELS);
                be.setChannel(v);
                return null;
            }
            if (key.startsWith("lock.") && rodDrive()) {
                int n = parse(key.substring(5)) - 1;
                if (n < 0 || n >= be.shafts().length) return outOfRange("lock", 1, be.shafts().length);
                be.setShaftLocked(n, value != 0);
                return null;
            }
            if (key.equals("priority") && be.rack() != null && !be.rack().isPatternRack()) {
                int v = (int) Math.round(value);
                if (v < 0 || v > ServerRack.MAX_PRIORITY) return outOfRange(key, 0, ServerRack.MAX_PRIORITY);
                be.rack().setPriority(v);
                be.setChanged();
                return null;
            }
            if (key.equals("manual") && be.machine().isEmergency()) {
                be.setManual(value != 0);
                return null;
            }
            if (key.equals("burn") && be.fusion() != null) {
                int v = (int) Math.round(value);
                if (v < 0 || v > 100) return outOfRange(key, 0, 100);
                be.setBurn(v);
                return null;
            }
            if (key.equals("repair") && be.hasWear()) {
                return be.repair() ? null : Component.translatable("data.nuclearsq.err.repair_running");
            }
            if (key.startsWith("dump.")) {
                List<BigMachine.Port> ports = be.machine().ports;
                for (int i = 0; i < ports.size(); i++) {
                    if (ports.get(i).name().equals(key.substring(5)) && be.tank(ports.get(i).name()) != null) {
                        be.dumpTank(i);
                        return null;
                    }
                }
            }
            return Component.translatable("data.nuclearsq.err.readonly", key);
        }

        @Override
        public void open(ServerPlayer player) {
            be.use(player);
        }
    }

    static final class Turbine extends Base<TurbineControllerBlockEntity> {
        Turbine(TurbineControllerBlockEntity be) {
            super(be);
        }

        private String key() {
            if (!be.isFormed()) return "unformed";
            if (!be.running()) return "off";
            return be.rpm() > 0 ? "running" : "no_steam";
        }

        @Override
        public int state() {
            return switch (key()) {
                case "running" -> OK;
                case "no_steam" -> WARN;
                case "off" -> OFF;
                default -> ALARM;
            };
        }

        @Override
        public Component stateText() {
            return stateLabel(key());
        }

        @Override
        public PowerFlow power() {
            return PowerFlow.producer(be.lastFe(), be.maxFe());
        }

        @Override
        public List<DataValue> values() {
            List<DataValue> out = new ArrayList<>();
            out.add(new DataValue("rpm", label("rpm"), be.rpm(), TurbineControllerBlockEntity.MAX_RPM, "U/min"));
            out.add(new DataValue("fe_rate", label("fe_rate"), be.lastFe(),
                    be.maxFe(), "FE/t"));
            if (be.translator() > 0) out.add(new DataValue("translator", label("translator"), be.translator(), PartStages.TRANSLATORS.size(), ""));
            out.add(new DataValue("steam", label("steam"), be.steam().getFluidAmount(), TurbineControllerBlockEntity.TANK, "mB"));
            out.add(new DataValue("energy", label("energy"), be.energyStored(), TurbineControllerBlockEntity.CAP, "FE"));
            out.add(new DataValue("throttle", label("throttle"), be.throttle(), 100, "%"));
            out.add(new DataValue("run", label("run"), be.running() ? 1 : 0, 1, ""));
            return out;
        }

        @Override
        public List<String> settable() {
            return List.of("run", "throttle");
        }

        @Override
        @Nullable
        public Component set(String key, double value) {
            if (key.equals("run")) {
                be.setControl(value != 0, be.throttle());
                return null;
            }
            if (key.equals("throttle")) {
                int v = (int) Math.round(value);
                if (v < 0 || v > 100) return outOfRange(key, 0, 100);
                be.setControl(be.running(), Mth.clamp(v, 0, 100));
                return null;
            }
            return Component.translatable("data.nuclearsq.err.readonly", key);
        }

        @Override
        public void open(ServerPlayer player) {
            be.openMonitor(player);
        }
    }
}
