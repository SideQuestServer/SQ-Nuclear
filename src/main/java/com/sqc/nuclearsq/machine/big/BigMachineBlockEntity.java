package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.machine.CoolantTypes;
import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.reactor.PartStages;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.ModFluids;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BigMachineBlockEntity extends BlockEntity implements GeoBlockEntity, com.sqc.nuclearsq.machine.MonitorTerminal,
        com.sqc.nuclearsq.turbine.ShaftConsumer, com.sqc.nuclearsq.reactor.Cherenkov.Holder, com.sqc.nuclearsq.machine.MachineAccess.Holder {
    public static final int ROD_MAX = 15, ROD_STEP_TICKS = 10;
    public static final int CHANNELS = 16, LINK_RANGE = 64;
    private static final java.util.Set<BigMachineBlockEntity> DRIVES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<BigMachineBlockEntity> BAYS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<BigMachineBlockEntity> CLIENT_DRIVES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<BigMachineBlockEntity> HULLS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<BigMachineBlockEntity> CLIENT_HULLS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<BigMachineBlockEntity> NO_MOBS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final int CONTAINMENT_DRIVE_Y = BigMachine.CONTAINMENT.h - 1 - BigMachine.CONTROL_ROD_DRIVE.h;
    public static final double REACTOR_RANGE = 9;
    private static final int[][] ROD_SHAFTS = {{1, 1}, {2, 1}, {3, 1}, {1, 2}, {2, 2}, {3, 2}, {1, 3}, {2, 3}, {3, 3}};
    private static final int[][] RACK_SHAFTS = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};
    private static final int RUN_HOLD = 20;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("running");
    private static final RawAnimation BLOCKED = RawAnimation.begin().thenLoop("blocked");
    private static final RawAnimation DOOR_OPENING = RawAnimation.begin().thenPlay("door_open").thenLoop("door_opened");
    private static final RawAnimation DOOR_CLOSING = RawAnimation.begin().thenPlay("door_close").thenLoop("door_closed");
    private static final RawAnimation DOOR_OPENED = RawAnimation.begin().thenLoop("door_opened");
    private static final RawAnimation DOOR_CLOSED = RawAnimation.begin().thenLoop("door_closed");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private final BigMachine machine;
    private final Map<String, FluidTank> tanks = new LinkedHashMap<>();
    private final Map<String, IFluidHandler> portFluids = new HashMap<>();
    @Nullable
    private final MachineEnergy energy;
    @Nullable
    private final ItemStackHandler items;
    @Nullable private final BreederCore breeder;
    @Nullable
    private final BigBattery battery;
    private int batteryFlow, charge;

    private boolean formed, running, blocked;
    private boolean resizeChecked;
    private boolean reassert;
    private boolean doorOpen, doorPowered;
    @Nullable private Boolean doorShown;
    private int formedRot = -1, hiddenRot = -1, matched, total, ticks, idle = RUN_HOLD;
    public AABB renderBoxCache;
    public int renderBoxRot = -2;
    public long occTick = Long.MIN_VALUE;
    public boolean occVal;
    private int lastMb, signalIn, signalOut, rod;
    private boolean powered;
    private boolean rodFault;
    private boolean vesselDry;
    private int vesselDryTicks, vesselMb, vesselWant;
    private float vesselAcc, vesselTemp = 25f;
    private final float[] vesselBurn = new float[ROD_SHAFTS.length];
    private int target, channel;
    private boolean terminalMode;
    private boolean enabled = true, manual;
    public static final String[] STATES = {"running", "idle", "no_power", "standby", "no_reactor", "off", "no_shaft", "no_sun", "residue_full", "no_primary", "venting", "low_pressure", "tube_leak", "no_sodium", "no_driver", "sodium_leak", "no_fuel", "no_coolant", "ash_full", "wall_worn", "disrupted", "heating", "plasma", "no_wind"};
    public static final int FUSION_DATA = 16, FUSION_MAX_MK = FusionCore.MAX_MK;
    public static final int[] FUSION_PORTS = {5, 6, 7, 8, 9};
    public static int termRows(BigMachine m) {
        return m == BigMachine.FUSION_REACTOR ? FUSION_PORTS.length : Math.min(m.ports.size(), MAX_TERM_PORTS);
    }

    public static final int TERM_PORTS = 24, PORT_VALUES = 5, MAX_TERM_PORTS = 6;
    public static final int FUSION_EXTRA = TERM_PORTS + PORT_VALUES * FUSION_PORTS.length;
    private final RodShaft[] shafts;
    private final boolean[] released = new boolean[BigCrafter.SHAFTS];
    private int cherenkov = com.sqc.nuclearsq.reactor.Cherenkov.DEFAULT;
    @Nullable
    private final BigCrafter crafter;
    @Nullable
    private final com.sqc.nuclearsq.data.ControlDesk desk;
    @Nullable
    private final com.sqc.nuclearsq.storage.ServerRack rack;
    private final com.sqc.nuclearsq.storage.ClusterMachine cluster;
    @Nullable
    private final com.sqc.nuclearsq.exo.ExoChamber exo;
    @Nullable private java.util.UUID owner;
    private final com.sqc.nuclearsq.machine.MachineAccess access = new com.sqc.nuclearsq.machine.MachineAccess();
    private int tierSync;
    @Nullable
    private final com.sqc.nuclearsq.storage.ComponentAssembler assembler;
    @Nullable
    private final Substation substation;
    @Nullable
    private final DeconDock dock;
    private boolean noReactor;
    private int shaftIn;
    private int solarRate;
    private int windRate, windHeightPct, windSurroundPct, windGustPct;
    private float windOpen = 1f;
    private int lastOut;
    private float preheatAcc;
    private long disposed;
    @Nullable
    private final DriveCore core;
    private int vesselMissing;
    @Nullable
    private final FusionCore fusion;
    private int sepAcc;
    private int elecAcc;
    private int residueAcc;
    private int evaporated;

    public BigMachineBlockEntity(BlockPos pos, BlockState state) {
        super(BigMachineRegistry.CONTROLLER_BE.get(), pos, state);
        machine = ((BigMachineControllerBlock) state.getBlock()).machine();
        int[][] cells = machine == BigMachine.CONTROL_ROD_DRIVE ? ROD_SHAFTS : machine == BigMachine.SPENT_FUEL_RACK ? RACK_SHAFTS : new int[0][];
        shafts = java.util.Arrays.stream(cells).map(c -> new RodShaft(c[0], c[1])).toArray(RodShaft[]::new);
        int slots = 0;
        for (BigMachine.Port p : machine.ports) {
            if (p.kind() == BigPortKind.ITEM) slots = Math.max(slots, p.slots());
            if (!p.kind().isFluid() || p.tank() <= 0) continue;
            FluidTank t = new FluidTank(p.tank(), accepts(machine, p)) {
                @Override
                protected void onContentsChanged() { setChanged(); }
            };
            tanks.put(p.name(), t);
            portFluids.put(p.name(), p.in() && p.out() ? t : p.in() ? fillOnly(t) : drainOnly(t));
        }
        if (machine == BigMachine.FLUID_BUFFER) portFluids.put("fluid_out", drainOnly(tanks.get("fluid_in")));
        BigMachine.Energy e = machine.energy;
        energy = e == null ? null : new MachineEnergy(e.capacity(), machine == BigMachine.CONTAINMENT ? Integer.MAX_VALUE : e.maxReceive(), e.maxExtract());
        battery = BigBattery.handles(machine) && energy != null ? new BigBattery(machine, energy, this::setChanged) : null;
        items = slots <= 0 ? null : new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (machine == BigMachine.STEAM_PURIFIER) return false;
                if (machine == BigMachine.FAST_BREEDER) return BreederCore.accepts(slot, stack);
                if (machine == BigMachine.FUSION_REACTOR) return FusionCore.valid(slot, stack);
                if (machine == BigMachine.COAL_GENERATOR && slot > 3) return BigCrafter.isFuel(stack);
                if (machine == BigMachine.COAL_GENERATOR && slot > 0) return stack.is(com.sqc.nuclearsq.registry.PowerRegistry.COAL_CARDS.get(slot - 1).get());
                if (machine == BigMachine.PARTICLE_ASSEMBLER && slot == com.sqc.nuclearsq.particle.ParticleClasses.CARD) {
                    return com.sqc.nuclearsq.particle.ParticleClassCardItem.tierOf(stack) > 0;
                }
                if (machine == BigMachine.PARTICLE_ASSEMBLER && slot >= com.sqc.nuclearsq.particle.ParticleClasses.OUTPUTS) {
                    var kind = com.sqc.nuclearsq.particle.ParticleUpgradeItem.kindOf(stack);
                    return kind != null && kind.slot() == slot;
                }
                return machine != BigMachine.WASTE_REPOSITORY || com.sqc.nuclearsq.spent.SpentFuel.isWaste(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                if (machine == BigMachine.PARTICLE_ASSEMBLER && slot == com.sqc.nuclearsq.particle.ParticleClasses.CARD) return 1;
                if (machine == BigMachine.PARTICLE_ASSEMBLER && slot >= com.sqc.nuclearsq.particle.ParticleClasses.OUTPUTS) {
                    return com.sqc.nuclearsq.particle.ParticleClasses.get().max(
                            com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.values()[slot - com.sqc.nuclearsq.particle.ParticleClasses.OUTPUTS]);
                }
                return super.getSlotLimit(slot);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (crafter != null) crafter.dirty = true;
            }
        };
        breeder = machine == BigMachine.FAST_BREEDER && items != null ? new BreederCore(machine) : null;
        crafter = BigCrafter.handles(machine) && (machine == BigMachine.SPENT_FUEL_RACK || items != null && energy != null)
                ? new BigCrafter(machine, items, energy, tanks) : null;
        if (crafter != null && machine == BigMachine.SPENT_FUEL_RACK) crafter.setRack(rackView());
        desk = machine == BigMachine.CONTROL_DESK ? new com.sqc.nuclearsq.data.ControlDesk() : null;
        rack = com.sqc.nuclearsq.storage.ServerRack.handles(machine) && energy != null
                ? new com.sqc.nuclearsq.storage.ServerRack(machine, energy, this::setChanged) : null;
        cluster = com.sqc.nuclearsq.storage.ClusterMachine.handles(machine) && energy != null ? new com.sqc.nuclearsq.storage.ClusterMachine(energy) : null;
        exo = com.sqc.nuclearsq.exo.ExoChamber.handles(machine) && energy != null ? new com.sqc.nuclearsq.exo.ExoChamber(energy, this::setChanged) : null;
        assembler = com.sqc.nuclearsq.storage.ComponentAssembler.handles(machine) && energy != null && items != null
                ? new com.sqc.nuclearsq.storage.ComponentAssembler(machine, energy, items, this::setChanged) : null;
        core = machine == BigMachine.CONTROL_ROD_DRIVE ? new DriveCore() : null;
        substation = Substation.handles(machine) ? new Substation(this) : null;
        dock = DeconDock.handles(machine) && energy != null ? new DeconDock(this, energy, tanks.get("lye_in"), tanks.get("diesel_in")) : null;
        fusion = FusionCore.handles(machine) && energy != null ? new FusionCore(machine, this::setChanged) : null;
    }

    public static Predicate<FluidStack> accepts(BigMachine machine, BigMachine.Port p) {
        return switch (p.kind()) {
            case WET_STEAM -> machine == BigMachine.STEAM_DRYER && p.in()
                    ? fs -> fs.is(ModFluids.WET_STEAM.source.get()) || fs.is(ModFluids.CONTAMINATED_STEAM.source.get()) || fs.is(ModFluids.STEAM.source.get())
                    : machine == BigMachine.STEAM_GENERATOR || machine == BigMachine.STEAM_SEPARATOR || machine == BigMachine.STEAM_DRYER || machine == BigMachine.CONTAINMENT
                    ? fs -> fs.is(ModFluids.WET_STEAM.source.get()) || fs.is(ModFluids.CONTAMINATED_STEAM.source.get())
                    : fs -> fs.is(ModFluids.WET_STEAM.source.get());
            case FEEDWATER -> machine == BigMachine.STEAM_GENERATOR || machine == BigMachine.FEEDWATER_PUMP || machine == BigMachine.CONTAINMENT || machine == BigMachine.FAST_BREEDER
                    || machine == BigMachine.FEEDWATER_HEATER && p.out()
                    ? fs -> fs.is(ModFluids.FEEDWATER.source.get()) || fs.is(ModFluids.PREHEATED_FEEDWATER.source.get())
                    : fs -> fs.is(ModFluids.FEEDWATER.source.get());
            case WATER -> p.name().contains("heavy") ? fs -> fs.is(ModFluids.HEAVY_WATER.source.get())
                    : machine == BigMachine.DEUTERIUM_SEPARATOR ? fs -> fs.is(FluidTags.WATER) || fs.is(ModFluids.HEAVY_WATER.source.get())
                    : p.name().equals("hydraulic_in") ? fs -> fs.is(FluidTags.WATER) || fs.is(ModFluids.FEEDWATER.source.get()) || fs.is(ModFluids.PREHEATED_FEEDWATER.source.get())
                    : machine == BigMachine.EMERGENCY_COOLING ? fs -> fs.is(FluidTags.WATER) || fs.is(ModFluids.FEEDWATER.source.get())
                    : fs -> fs.is(FluidTags.WATER);
            case COOLANT -> machine == BigMachine.FAST_BREEDER ? fs -> fs.is(ModFluids.SODIUM_COOLANT.source.get())
                    : p.out() == (machine == BigMachine.CONDENSER || machine == BigMachine.RELIEF_TANK || machine == BigMachine.FUSION_REACTOR) ? fs -> fs.is(ModFluids.HOT_COOLANT.source.get())
                    : machine == BigMachine.CONDENSER || machine == BigMachine.RELIEF_TANK || machine == BigMachine.FUSION_REACTOR ? fs -> CoolantTypes.heatFactor(fs) > 0
                    : fs -> fs.is(ModFluids.COOLANT.source.get());
            case STEAM -> switch (machine) {
                case REHEATER -> p.out() ? fs -> fs.is(ModFluids.SUPERHEATED_STEAM.source.get()) : fs -> fs.is(ModFluids.STEAM.source.get());
                case FEEDWATER_HEATER, CONDENSER -> fs -> fs.is(ModFluids.EXHAUST_STEAM.source.get());
                case STEAM_PURIFIER -> p.in() ? fs -> fs.is(ModFluids.CONTAMINATED_STEAM.source.get()) || fs.is(ModFluids.STEAM.source.get())
                        : fs -> fs.is(ModFluids.STEAM.source.get());
                case STEAM_SEPARATOR, STEAM_DRYER -> fs -> fs.is(ModFluids.STEAM.source.get()) || fs.is(ModFluids.CONTAMINATED_STEAM.source.get());
                default -> fs -> fs.is(ModFluids.STEAM.source.get());
            };
            case PRIMARY -> p.name().contains("hot") || p.name().contains("relief") ? fs -> fs.is(ModFluids.HOT_PRIMARY_WATER.source.get())
                    : fs -> fs.is(ModFluids.PRIMARY_WATER.source.get());
            case BIG_WATER -> fs -> fs.is(FluidTags.WATER);
            case ACID -> machine == BigMachine.FLUID_BUFFER ? fs -> true
                    : p.name().contains("crude") ? fs -> fs.is(ModFluids.CRUDE_OIL.source.get())
                    : p.name().contains("naphtha") ? fs -> fs.is(ModFluids.NAPHTHA.source.get())
                    : p.name().contains("diesel") ? fs -> fs.is(ModFluids.DIESEL.source.get())
                    : p.name().startsWith("tar") ? fs -> fs.is(ModFluids.TAR.source.get())
                    : p.name().contains("lye") ? fs -> fs.is(ModFluids.LYE.source.get())
                    : p.name().contains("nitric") ? fs -> fs.is(ModFluids.NITRIC_ACID.source.get())
                    : p.name().contains("hlw") ? fs -> fs.is(ModFluids.HLW.source.get())
                    : p.name().contains("hf") ? fs -> fs.is(ModFluids.HYDROFLUORIC_ACID.source.get())
                    : fs -> fs.is(ModFluids.SULFURIC_ACID.source.get());
            case GAS -> p.name().contains("ethylene") ? fs -> fs.is(ModFluids.ETHYLENE.source.get()) || machine == BigMachine.GAS_GENERATOR && fs.is(ModFluids.DIESEL.source.get())
                    : p.name().contains("hydrogen") ? fs -> fs.is(ModFluids.HYDROGEN.source.get())
                    : p.name().contains("oxygen") ? fs -> fs.is(ModFluids.OXYGEN.source.get())
                    : p.name().contains("deuterium") ? fs -> fs.is(ModFluids.DEUTERIUM.source.get())
                    : p.name().contains("tritium") ? fs -> fs.is(ModFluids.TRITIUM.source.get())
                    : fs -> fs.is(ModFluids.HELIUM.source.get());
            default -> fs -> false;
        };
    }

    private static IFluidHandler fillOnly(FluidTank t) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return t.getFluid(); }
            @Override public int getTankCapacity(int i) { return t.getCapacity(); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return t.isFluidValid(s); }
            @Override public int fill(FluidStack s, FluidAction a) { return t.fill(s, a); }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int n, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    private static IFluidHandler drainOnly(FluidTank t) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return t.getFluid(); }
            @Override public int getTankCapacity(int i) { return t.getCapacity(); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return false; }
            @Override public int fill(FluidStack s, FluidAction a) { return 0; }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return t.drain(s, a); }
            @Override public FluidStack drain(int n, FluidAction a) { return t.drain(n, a); }
        };
    }

    public BigMachine machine() { return machine; }
    public boolean isFormed() { return formed; }
    public boolean isRunning() { return running; }
    public int formedRot() { return formed ? formedRot : -1; }
    public int rod() { return rod; }
    public int rodTarget() { return target; }
    public boolean terminalMode() { return terminalMode; }
    public int channel() { return channel; }
    public RodShaft[] shafts() { return shafts; }
    @Nullable public BigCrafter crafter() { return crafter; }
    @Nullable public com.sqc.nuclearsq.data.ControlDesk desk() { return desk; }
    @Nullable public com.sqc.nuclearsq.storage.ServerRack rack() { return rack; }
    @Nullable public com.sqc.nuclearsq.storage.ClusterMachine cluster() { return cluster; }
    @Nullable public java.util.UUID owner() { return owner; }
    public com.sqc.nuclearsq.machine.MachineAccess access() { return access; }

    public void setOwner(@Nullable java.util.UUID id) {
        owner = id;
        setChanged();
    }
    @Nullable public com.sqc.nuclearsq.storage.ComponentAssembler assembler() { return assembler; }

    public java.util.List<BlockPos> dataStarts() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        out.add(worldPosition);
        if (formed) for (BigMachine.Port p : machine.ports) {
            BlockPos pos = portPos(p);
            if (pos != null) out.add(pos);
        }
        return out;
    }

    public void syncDesk() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public java.util.List<BlockPos> cellPositions() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        out.add(worldPosition);
        int rot = formedRot();
        if (rot < 0) return out;
        MultiblockStructure s = structure();
        for (int[] c : machine.cells) {
            BlockPos p = s.world(worldPosition, rot, c[0], c[1], c[2]);
            if (!p.equals(worldPosition)) out.add(p);
        }
        return out;
    }
    @Nullable public ItemStackHandler items() { return items; }
    public int matched() { return matched; }
    public int total() { return total; }

    public int statusLamp() {
        if (!formed || rodFault || vesselDry) return 2;
        boolean any = false, busy = false;
        for (RodShaft s : shafts) {
            if (s.headY() < 0) continue;
            any = true;
            busy |= !s.locked() || s.busy(rod);
        }
        return !any ? 2 : busy ? 1 : 0;
    }
    public boolean isClient() { return level != null && level.isClientSide; }

    public BlockPos rodBase(RodShaft s) {
        return structure().world(worldPosition, Math.max(formedRot, 0), s.cellX, machine.ay, s.cellZ);
    }
    public int signalOut() { return signalOut; }
    public int lastMb() { return lastMb; }
    @Nullable public FluidTank tank(String port) { return tanks.get(port); }
    @Nullable public MachineEnergy energy() { return energy; }

    @Nullable public com.sqc.nuclearsq.exo.ExoChamber exo() { return exo; }

    public boolean isGenerator() {
        return machine == BigMachine.SOLAR_FIELD || machine.isWind() || machine == BigMachine.COAL_GENERATOR || machine == BigMachine.FUEL_CELL || machine == BigMachine.GAS_GENERATOR;
    }

    public int generatorPeak() {
        return machine == BigMachine.SOLAR_FIELD || machine.isWind() ? machine.extra("fe_per_tick_peak") : machine.extra("fe_per_tick");
    }

    public int windHeightPct() { return windHeightPct; }
    public int windSurroundPct() { return windSurroundPct; }
    public int windGustPct() { return windGustPct; }

    public int lastOut() { return lastOut; }
    @Nullable public BigBattery battery() { return battery; }
    @Nullable public Substation substation() { return substation; }

    public int chargeLevel() {
        if (isClient()) return charge;
        return battery == null ? 0 : battery.level();
    }

    @Nullable
    public IFluidHandler fluidsAt(String port) { return portFluids.get(port); }

    @Nullable
    public IEnergyStorage energyAt(String port) {
        if (battery != null) return battery.at(port);
        if (substation != null) return substation.at(port);
        BigMachine.Port p = machine.port(port);
        if (p == null || p.kind() != BigPortKind.ENERGY) return null;
        if (fusion != null) return p.in() ? FusionCore.receiveOnly(energy) : fusion.out();
        if (machine == BigMachine.CONTAINMENT) return p.in() ? receiveOnly(energy) : null;
        return energy;
    }

    private static IEnergyStorage receiveOnly(IEnergyStorage s) {
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int max, boolean simulate) { return s.receiveEnergy(max, simulate); }
            @Override public int extractEnergy(int max, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return s.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return s.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return true; }
        };
    }

    @Nullable
    public IItemHandler itemsAt(String port) {
        BigMachine.Port p = machine.port(port);
        if (p == null || p.kind() != BigPortKind.ITEM || items == null) return null;
        if (assembler != null && p.in()) return assembler.input(this);
        if (fusion != null) return FusionCore.view(items, p.in());
        IItemHandler h = machine == BigMachine.WASTE_REPOSITORY ? insertOnly(items)
                : breeder != null ? breederView()
                : crafter != null && level != null ? crafter.automation(level) : items;
        return p.in() && p.out() ? h : p.in() ? insertOnly(h) : extractOnly(h);
    }

    private IItemHandler breederView() {
        return new IItemHandler() {
            @Override public int getSlots() { return items.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return BreederCore.accepts(slot, stack) ? items.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot < BreederCore.OUT_FIRST ? ItemStack.EMPTY : items.extractItem(slot, amount, simulate);
            }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return BreederCore.accepts(slot, stack); }
        };
    }

    private static IItemHandler insertOnly(IItemHandler h) {
        return new IItemHandler() {
            @Override public int getSlots() { return h.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return h.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return h.insertItem(slot, stack, simulate); }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return h.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return h.isItemValid(slot, stack); }
        };
    }

    private static IItemHandler extractOnly(IItemHandler h) {
        return new IItemHandler() {
            @Override public int getSlots() { return h.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return h.getStackInSlot(slot); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return h.extractItem(slot, amount, simulate); }
            @Override public int getSlotLimit(int slot) { return h.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
    }

    public boolean acceptHeld(Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (machine == BigMachine.SPENT_FUEL_RACK && com.sqc.nuclearsq.spent.SpentFuel.isSpentRod(held)) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.rack.assembled_only"), true);
            return true;
        }
        if (machine == BigMachine.FLUID_BUFFER) return formed && net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, tanks.get("fluid_in"));
        if (breeder != null && formed) {
            FluidTank sodium = tanks.get("sodium_in");
            if (sodium != null && net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, portFluids.get("sodium_in"))) return true;
            ItemStack rest = BreederCore.insert(items, held.copy());
            if (rest.getCount() == held.getCount()) return false;
            player.setItemInHand(hand, rest);
            return true;
        }
        if (machine != BigMachine.WASTE_REPOSITORY || items == null || !formed || !com.sqc.nuclearsq.spent.SpentFuel.isWaste(held)) return false;
        ItemStack rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(items, held.copy(), false);
        if (rest.getCount() == held.getCount()) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.repository.full"), true);
            return true;
        }
        player.setItemInHand(hand, rest);
        player.displayClientMessage(Component.translatable("message.nuclearsq.repository.added", held.getCount() - rest.getCount()), true);
        return true;
    }

    public long disposed() { return disposed; }

    private int waterColumn() {
        if (level == null || formedRot < 0 || crafter == null) return 0;
        int need = crafter.columnNeeded(), min = need;
        for (int x = 0; x < structure().width(); x++) {
            for (int z = 0; z < structure().depth(); z++) {
                int n = 0;
                while (n < need && wet(structure().world(worldPosition, formedRot, x, structure().height() + n, z))) n++;
                min = Math.min(min, n);
            }
        }
        return min;
    }

    private boolean wet(BlockPos p) {
        if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) return true;
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof com.sqc.nuclearsq.reactor.FuelRodBlock || st.getBlock() instanceof com.sqc.nuclearsq.reactor.FuelRodHeadBlock)) return false;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    public Component rackLine() {
        String k = "message.nuclearsq.rack.";
        int rods = 0;
        for (RodShaft s : shafts) if (s.headY() >= 0) rods++;
        Component feed = Component.translatable(k + (crafter != null && crafter.feedOk() ? "feed_ok" : "feed_missing"),
                crafter == null ? 0 : crafter.waterAmount());
        return Component.translatable(k + "line", feed, crafter == null ? 0 : crafter.column(),
                crafter == null ? 0 : crafter.columnNeeded(), rods, shafts.length);
    }

    @Nullable
    private FuelRodBlockEntity[] rodBlocks(RodShaft s) {
        if (s.headY() < 0 || level == null) return null;
        FuelRodBlockEntity[] out = new FuelRodBlockEntity[com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS];
        BlockPos base = rodBase(s);
        for (int i = 1; i <= out.length; i++) {
            if (!(level.getBlockEntity(base.above(s.headY() - i)) instanceof FuelRodBlockEntity f)) return null;
            out[i - 1] = f;
        }
        return out;
    }

    public int rackState(int shaft) {
        if (shaft < 0 || shaft >= shafts.length) return BigCrafter.RackView.EMPTY;
        FuelRodBlockEntity[] b = rodBlocks(shafts[shaft]);
        if (b == null) return BigCrafter.RackView.EMPTY;
        boolean cooled = true;
        for (FuelRodBlockEntity f : b) {
            if (f.getRemaining() > 0 || f.isUnloaded()) return BigCrafter.RackView.FRESH;
            cooled &= f.isCooled();
        }
        return cooled ? BigCrafter.RackView.COOLED : BigCrafter.RackView.HOT;
    }

    public static boolean rodClamped(net.minecraft.world.level.Level level, BlockPos head) {
        for (BigMachineBlockEntity m : BAYS) {
            if (m.isRemoved() || m.level != level || !m.formed) continue;
            for (RodShaft s : m.shafts) {
                BlockPos base = m.rodBase(s);
                int dy = head.getY() - base.getY();
                if (s.locked() && head.getX() == base.getX() && head.getZ() == base.getZ() && dy >= RodShaft.FULL_HEAD_Y && dy <= m.maxHeadY()) return true;
            }
        }
        return false;
    }

    public boolean rackInPlace(int shaft) {
        RodShaft s = shafts[shaft];
        return s.headY() >= 0 && s.locked() && s.shown() >= s.headY() - RodShaft.FULL_HEAD_Y - 0.001f;
    }

    private BigCrafter.RackView rackView() {
        return new BigCrafter.RackView() {
            @Override public int state(int shaft) { return rackState(shaft); }
            @Override public boolean inPlace(int shaft) { return rackInPlace(shaft); }

            @Override
            public void cooled(int shaft) {
                FuelRodBlockEntity[] b = rodBlocks(shafts[shaft]);
                if (b != null) for (FuelRodBlockEntity f : b) f.setCooled(true);
                setRackReleased(shaft, true);
            }
        };
    }

    public void setRackReleased(int shaft, boolean value) {
        if (shaft < 0 || shaft >= shafts.length || shaft >= released.length || level == null) return;
        RodShaft s = shafts[shaft];
        released[shaft] = value && s.headY() >= 0;
        if (s.setLocked(!released[shaft] && s.headY() >= 0)) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean rackReleased(int shaft) { return shaft >= 0 && shaft < released.length && released[shaft]; }

    private void rackScan() {
        for (int i = 0; i < shafts.length; i++) {
            RodShaft s = shafts[i];
            if (s.headY() < 0) released[i] = false;
            else if (!released[i] && !s.locked()) s.setLocked(true);
        }
    }

    @Override
    public int cherenkov() { return cherenkov; }

    @Override
    public void setCherenkov(int value) {
        if (value == cherenkov || level == null) return;
        cherenkov = value;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public boolean cherenkovCovers(BlockPos p) {
        if (shafts.length == 0 || !formed || formedRot < 0) return false;
        MultiblockStructure s = structure();
        BlockPos a = s.world(worldPosition, formedRot, 0, 0, 0), b = s.world(worldPosition, formedRot, machine.w - 1, machine.h - 1, machine.d - 1);
        return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                && p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY())
                && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    private static final int SHAFT_SCAN_TICKS = 4;

    public int inletY() {
        if (machine == BigMachine.SPENT_FUEL_RACK) return machine.h - machine.ay;
        return RodShaft.FULL_HEAD_Y + (containmentOnTop() != null ? BigMachine.CONTAINMENT.h - CONTAINMENT_DRIVE_Y - BigMachine.CONTROL_ROD_DRIVE.h + 1 : 0);
    }

    public int maxHeadY() {
        if (machine == BigMachine.SPENT_FUEL_RACK) return RodShaft.MAX_HEAD_Y;
        return inletY() + RodShaft.MAX_HEAD_Y - RodShaft.FULL_HEAD_Y;
    }

    @Nullable
    public BigMachineBlockEntity containmentOnTop() {
        if (machine != BigMachine.CONTROL_ROD_DRIVE || level == null || !formed || formedRot < 0) return null;
        BlockPos at = containmentAnchor(worldPosition, formedRot);
        return level.isLoaded(at) && level.getBlockEntity(at) instanceof BigMachineBlockEntity c && c.machine == BigMachine.CONTAINMENT
                && c.formed && c.formedRot == formedRot ? c : null;
    }

    public static BlockPos containmentAnchor(BlockPos drive, int rot) {
        BigMachine d = BigMachine.CONTROL_ROD_DRIVE, c = BigMachine.CONTAINMENT;
        int dx = (c.w - d.w) / 2, dz = (c.d - d.d) / 2;
        return d.structure.world(drive, rot, c.ax - dx, c.ay - CONTAINMENT_DRIVE_Y, c.az - dz);
    }

    public static BlockPos driveAnchorIn(BlockPos hull, int rot) {
        BigMachine d = BigMachine.CONTROL_ROD_DRIVE, c = BigMachine.CONTAINMENT;
        int dx = (c.w - d.w) / 2, dz = (c.d - d.d) / 2;
        return c.structure.world(hull, rot, d.ax + dx, d.ay + CONTAINMENT_DRIVE_Y, d.az + dz);
    }

    @Nullable
    public static BigMachineBlockEntity containmentAround(net.minecraft.world.level.Level level, BlockPos target) {
        BigMachine d = BigMachine.CONTROL_ROD_DRIVE, c = BigMachine.CONTAINMENT;
        int dx = (c.w - d.w) / 2, dz = (c.d - d.d) / 2;
        for (BigMachineBlockEntity h : level.isClientSide ? CLIENT_HULLS : HULLS) {
            if (h.isRemoved() || h.level != level || !h.formed || h.formedRot < 0) continue;
            BlockPos a = c.structure.world(h.worldPosition, h.formedRot, dx, CONTAINMENT_DRIVE_Y - 1, dz);
            BlockPos b = c.structure.world(h.worldPosition, h.formedRot, dx + d.w - 1, CONTAINMENT_DRIVE_Y - 1, dz + d.d - 1);
            if (target.getX() >= Math.min(a.getX(), b.getX()) && target.getX() <= Math.max(a.getX(), b.getX())
                    && target.getZ() >= Math.min(a.getZ(), b.getZ()) && target.getZ() <= Math.max(a.getZ(), b.getZ())
                    && target.getY() >= a.getY() && target.getY() <= a.getY() + 1) return h;
        }
        return null;
    }

    @Nullable
    public static BigMachineBlockEntity driveUnder(net.minecraft.world.level.Level level, BlockPos target) {
        for (BigMachineBlockEntity d : level.isClientSide ? CLIENT_DRIVES : DRIVES) {
            if (d.isRemoved() || d.level != level || !d.formed || d.formedRot < 0) continue;
            MultiblockStructure s = d.structure();
            BlockPos a = s.world(d.worldPosition, d.formedRot, 0, 0, 0), b = s.world(d.worldPosition, d.formedRot, d.machine.w - 1, d.machine.h - 1, d.machine.d - 1);
            int top = Math.max(a.getY(), b.getY());
            if (target.getX() >= Math.min(a.getX(), b.getX()) && target.getX() <= Math.max(a.getX(), b.getX())
                    && target.getZ() >= Math.min(a.getZ(), b.getZ()) && target.getZ() <= Math.max(a.getZ(), b.getZ())
                    && target.getY() >= top - 1 && target.getY() <= top + 1) return d;
        }
        return null;
    }

    private int stroke() {
        return machine == BigMachine.SPENT_FUEL_RACK ? ROD_MAX : rod;
    }

    private MultiblockStructure structure() { return machine.structure; }

    public static final int QUIET_CELLS = 2000;
    public static final int QUIET = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

    @Nullable
    BlockPos portPos(BigMachine.Port p) {
        if (formedRot < 0 || level == null) return null;
        BlockPos pos = structure().world(worldPosition, formedRot, p.x(), p.y(), p.z());
        return level.getBlockState(pos).is(BigMachineRegistry.PORTS.get(p.kind()).get()) ? pos : null;
    }

    @Nullable
    public BlockPos dataBridge(String from) {
        if (machine != BigMachine.CONTAINMENT || formedRot < 0 || level == null) return null;
        BigMachine.Port p = machine.port(from.equals("data") ? "data_inner" : from.equals("data_inner") ? "data" : "");
        if (p == null) return null;
        BlockPos pos = structure().world(worldPosition, formedRot, p.x(), p.y(), p.z());
        if (!com.sqc.nuclearsq.data.ChunkGuard.loaded(level, pos)) return null;
        return level.getBlockState(pos).is(BigMachineRegistry.PORTS.get(p.kind()).get()) ? pos : null;
    }

    private void notifyPorts() {
        for (BigMachine.Port p : machine.ports) {
            BlockPos pos = portPos(p);
            if (pos == null) continue;
            level.invalidateCapabilities(pos);
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }

    private void linkPorts(boolean link) {
        for (BigMachine.Port p : machine.ports) {
            BlockPos pos = portPos(p);
            if (pos != null && level.getBlockEntity(pos) instanceof BigPortBlockEntity be) {
                be.link(link ? worldPosition : null, p.name());
            }
        }
    }

    private void setPartsHidden(int rot, boolean hidden) {
        if (rot < 0 || level == null) return;
        MultiblockStructure s = structure();
        int flags = s.size >= QUIET_CELLS ? QUIET : net.minecraft.world.level.block.Block.UPDATE_ALL;
        boolean sky = hidden && machine == BigMachine.SOLAR_FIELD;
        for (MultiblockStructure.Cell c : s.cells(worldPosition, rot)) {
            BlockState st = level.getBlockState(c.pos());
            if (!c.ok(st) || !st.hasProperty(ReactorProps.FORMED)) continue;
            BlockState want = st.setValue(ReactorProps.FORMED, hidden);
            if (want.hasProperty(com.sqc.nuclearsq.reactor.ReactorCasingBlock.SKY)) want = want.setValue(com.sqc.nuclearsq.reactor.ReactorCasingBlock.SKY, sky);
            if (want != st) level.setBlock(c.pos(), want, flags);
        }
        BigShellBlock shell = machine.shellBlock();
        for (int[] cell : machine.cells) {
            if (s.charAt(cell[0], cell[1], cell[2]) != ' ') continue;
            BlockPos p = s.world(worldPosition, rot, cell[0], cell[1], cell[2]);
            BlockState st = level.getBlockState(p);
            if (hidden && (st.isAir() || st.is(Blocks.WATER))) {
                level.setBlock(p, com.sqc.nuclearsq.multiblock.Waterlog.at(
                        shell.state(doorShape(cell), rot), level, p), flags);
            } else if (!hidden && st.is(shell)) {
                level.setBlock(p, com.sqc.nuclearsq.multiblock.Waterlog.leftover(st), flags);
            }
        }
    }

    private int doorShape(int[] cell) {
        return doorOpen && machine == BigMachine.CONTAINMENT && ContainmentDoor.isDoor(cell[0], cell[1], cell[2])
                ? ContainmentDoor.open(cell[3]) : cell[3];
    }

    public boolean doorOpen() { return doorOpen; }

    public boolean isDoorPos(BlockPos p) {
        if (machine != BigMachine.CONTAINMENT || !formed || formedRot < 0) return false;
        for (int x = ContainmentDoor.X0; x <= ContainmentDoor.X1; x++) {
            for (int y = 0; y < ContainmentDoor.HEIGHT; y++) {
                for (int z = 0; z < ContainmentDoor.DEPTH; z++) {
                    if (structure().world(worldPosition, formedRot, x, y, z).equals(p)) return true;
                }
            }
        }
        return false;
    }

    public boolean setDoor(boolean open) {
        if (machine != BigMachine.CONTAINMENT || !formed || formedRot < 0 || level == null || level.isClientSide) return false;
        if (open == doorOpen) return true;
        MultiblockStructure s = structure();
        if (!open) {
            net.minecraft.world.phys.AABB box = net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                    s.world(worldPosition, formedRot, ContainmentDoor.X0, 0, 0),
                    s.world(worldPosition, formedRot, ContainmentDoor.X1, ContainmentDoor.HEIGHT - 1, ContainmentDoor.DEPTH - 1));
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box).isEmpty()) return false;
        }
        doorOpen = open;
        BigShellBlock shell = machine.shellBlock();
        for (int[] cell : machine.cells) {
            if (!ContainmentDoor.isDoor(cell[0], cell[1], cell[2])) continue;
            BlockPos p = s.world(worldPosition, formedRot, cell[0], cell[1], cell[2]);
            if (!level.getBlockState(p).is(shell)) continue;
            level.setBlock(p, com.sqc.nuclearsq.multiblock.Waterlog.at(shell.state(doorShape(cell), formedRot), level, p), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        BlockPos mid = s.world(worldPosition, formedRot, ContainmentDoor.X0, 1, 0);
        level.playSound(null, mid, open ? net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN : net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.7f);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    public void toggleDoor(net.minecraft.world.entity.player.Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer rp && !access.can(rp, owner, com.sqc.nuclearsq.machine.MachineAccess.USE)) {
            rp.displayClientMessage(Component.translatable("message.nuclearsq.access.denied"), true);
        } else if (!formed) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.containment.door_unformed"), true);
        } else if (!setDoor(!doorOpen)) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.containment.door_blocked"), true);
        }
    }

    public void doorRedstone() {
        if (level == null) return;
        boolean on = level.hasNeighborSignal(worldPosition);
        if (machine == BigMachine.CONTAINMENT && formed && formedRot >= 0) {
            for (int x = ContainmentDoor.X0; x <= ContainmentDoor.X1; x++) {
                for (int y = 0; y < ContainmentDoor.HEIGHT; y++) {
                    for (int z = 0; z < ContainmentDoor.DEPTH; z++) on |= level.hasNeighborSignal(structure().world(worldPosition, formedRot, x, y, z));
                }
            }
        }
        doorRedstone(on);
    }

    public void doorRedstone(boolean powered) {
        if (machine != BigMachine.CONTAINMENT || powered == doorPowered) return;
        doorPowered = powered;
        setChanged();
        if (formed) setDoor(powered);
    }

    public void onBroken() {
        if (level == null) return;
        if (assembler != null) assembler.returnBuffer(level, this);
        if (exo != null) for (ItemStack st : exo.contents()) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), st);
        for (ItemStack part : new ItemStack[] {PartStages.stack(PartStages.MODERATORS, moderator), PartStages.stack(PartStages.COOLERS, cooler)}) {
            if (!part.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), part);
        }
        moderator = 0;
        cooler = 0;
        for (RodShaft s : shafts) s.syncDriven(level, rodBase(s), false);
        setPartsHidden(hiddenRot, false);
        hiddenRot = -1;
        if (formedRot >= 0) linkPorts(false);
        if (substation != null) {
            int wires = com.sqc.nuclearsq.transport.GridNode.unlinkAll(level, substation);
            while (wires > 0) {
                int n = Math.min(64, wires);
                wires -= n;
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                        new ItemStack(com.sqc.nuclearsq.transport.GridRegistry.WIRE.get(), n));
            }
        }
        if (items != null) {
            for (int i = 0; i < items.getSlots(); i++) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), items.getStackInSlot(i));
                items.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (crafter != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                    worldPosition.getZ(), crafter.takePending());
        }
        if (assembler != null) {
            for (ItemStack st : assembler.drops()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), st);
            }
        }
        if (rack != null) {
            for (int i = 0; i < rack.slots(); i++) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), rack.drives().getStackInSlot(i).copy());
                rack.drives().setStackInSlot(i, ItemStack.EMPTY);
            }
            if (level instanceof net.minecraft.server.level.ServerLevel sl) rack.releaseAll(sl, worldPosition);
        }
    }

    private boolean loaded(MultiblockStructure s, int rot) {
        if (Math.max(machine.w, machine.d) <= 16) return true;
        BlockPos a = s.world(worldPosition, rot, 0, 0, 0), b = s.world(worldPosition, rot, machine.w - 1, 0, machine.d - 1);
        int y = level.getMinBuildHeight();
        for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++)
            for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++)
                if (!level.isLoaded(new BlockPos(cx << 4, y, cz << 4))) return false;
        return true;
    }

    private int bestLoadedRot(MultiblockStructure s) {
        int best = -1, bestN = -1;
        for (int r = 0; r < 4; r++) {
            if (!loaded(s, r)) continue;
            int n = s.matched(level, worldPosition, r);
            if (n > bestN) { bestN = n; best = r; }
        }
        return best;
    }

    private static final int UNFORMED_MAX_DELAY = 40, UNFORMED_MAX_DELAY_HUGE = 100;
    private int unformedDelay = 20, unformedWait, unformedChecks;

    private void checkStructure() {
        MultiblockStructure s = structure();
        if (!formed && (unformedWait -= 20) > 0) return;
        int before = matched;
        if (formed && formedRot >= 0 && !loaded(s, formedRot)) return;
        int rot;
        if (formed && formedRot >= 0) rot = formedRot;
        else if (formedRot >= 0 && unformedChecks++ % 4 != 0 && loaded(s, formedRot)) rot = formedRot;
        else rot = bestLoadedRot(s);
        if (rot < 0) return;
        matched = s.matched(level, worldPosition, rot);
        if (formed && matched != s.size) {
            rot = bestLoadedRot(s);
            if (rot < 0) return;
            matched = s.matched(level, worldPosition, rot);
        }
        if (formedRot >= 0 && rot != formedRot) linkPorts(false);
        formedRot = rot;
        total = s.size;
        boolean wasFormed = formed;
        formed = matched == total;
        if (formed || matched != before) {
            unformedDelay = 20;
            unformedChecks = 0;
        } else {
            unformedDelay = Math.min(unformedDelay * 2, s.size >= QUIET_CELLS ? UNFORMED_MAX_DELAY_HUGE : UNFORMED_MAX_DELAY);
        }
        unformedWait = unformedDelay;
        linkPorts(formed);
        if (formed && !wasFormed) notifyPorts();
        int want = formed ? formedRot : -1;
        if (reassert && want >= 0 && want == hiddenRot) setPartsHidden(want, true);
        reassert = hiddenRot >= 0;
        if (want != hiddenRot) {
            setPartsHidden(hiddenRot, false);
            setPartsHidden(want, true);
            hiddenRot = want;
            setChanged();
        }
    }

    public void serverTick() {
        if (level == null) return;
        if (!resizeChecked) resizeChecked = com.sqc.nuclearsq.kit.LegacyResize.check(this);
        boolean wasFormed = formed, wasRunning = running, wasFault = rodFault, wasDry = vesselDry, wasBlocked = blocked;
        boolean wasHot = core != null && core.hot();
        int wasRot = formedRot, wasRod = rod, wasHeads = rodHeads(), wasCharge = chargeLevel();
        boolean full = ticks++ % 20 == 0;
        if (full) checkStructure();
        if (full && formed && noMobs()) clearMobs();
        if (full && crafter != null && BigCrafter.usesModules(machine)) scanModules();
        if (shafts.length > 0 && (full || ticks % SHAFT_SCAN_TICKS == 0)) {
            int maxHead = maxHeadY();
            for (RodShaft s : shafts) s.scan(level, rodBase(s), formed, maxHead);
            if (machine == BigMachine.SPENT_FUEL_RACK && crafter != null) rackScan();
        }
        if (full && machine == BigMachine.SPENT_FUEL_RACK && crafter != null) {
            crafter.setColumn(formed ? waterColumn() : 0);
        }
        if (rack != null && level instanceof net.minecraft.server.level.ServerLevel sl) rack.tick(sl, worldPosition, formed, enabled);
        lastMb = formed && (enabled || machine == BigMachine.CONTROL_ROD_DRIVE || machine == BigMachine.CONTAINMENT) ? work() : 0;
        if (core != null && !formed) core.idle();
        if (!enabled && machine != BigMachine.CONTROL_ROD_DRIVE) powered = false;
        idle = lastMb > 0 ? 0 : Math.min(idle + 1, RUN_HOLD);
        running = formed && idle < RUN_HOLD;
        blocked = machine == BigMachine.BLAST_FURNACE && formed && !running && crafter != null && crafter.status() == BigCrafter.OUTPUT_FULL;
        if (formed) pushOutputs();
        if (formed && owner != null) {
            String mark = rankMark();
            if (mark != null) com.sqc.nuclearsq.progression.Titles.mark(level, owner, mark);
            if (lastOut > 0 && (isGenerator() || machine == BigMachine.SHAFT_GENERATOR || machine == BigMachine.FUSION_REACTOR))
                com.sqc.nuclearsq.progression.Titles.power(level, owner, lastOut);
        }
        if (battery != null) batteryFlow = battery.tick(level.hasNeighborSignal(worldPosition));
        if (machine == BigMachine.SPENT_FUEL_RACK && crafter != null && ticks % 20 == 0 && level instanceof net.minecraft.server.level.ServerLevel sl) {
            com.sqc.nuclearsq.radiation.RadiationManager.emit(sl, worldPosition, crafter.poolRadiation(formed));
        }
        if (machine == BigMachine.CONTROL_ROD_DRIVE) {
            FluidTank hyd = tanks.get("hydraulic_in");
            rodFault = formed && (!powered || hyd == null || hyd.getFluidAmount() < rate("hydraulic_in") * ROD_STEP_TICKS);
        }
        boolean rodSync = false;
        for (RodShaft s : shafts) rodSync |= s.update(level, rodBase(s), formed, stroke());
        if (formed != wasFormed || formedRot != wasRot || running != wasRunning || blocked != wasBlocked || rodFault != wasFault || vesselDry != wasDry || rod != wasRod || rodHeads() != wasHeads || rodSync
                || (core != null && core.hot() != wasHot) || chargeLevel() != wasCharge
                || (machine == BigMachine.PARTICLE_ASSEMBLER && crafter != null && crafter.particleSyncDue())
                || (rack != null && ticks % 5 == 0 && rack.syncDue() != null) || (exo != null && exo.takeDirty())) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (ticks % 20 == 0) setChanged();
        if (ticks % 10 == 0 && comparator() != lastComparator) {
            lastComparator = comparator();
            BlockPos dp = machine.port("data") == null ? null : portPos(machine.port("data"));
            if (dp != null) level.updateNeighbourForOutputSignal(dp, level.getBlockState(dp).getBlock());
        }
    }

    private int lastComparator;

    public int comparator() {
        FluidTank t = machine == BigMachine.FLUID_BUFFER ? tanks.get("fluid_in") : null;
        long v = battery != null ? energy.getEnergyStored() : t != null ? t.getFluidAmount() : -1;
        long cap = battery != null ? energy.getMaxEnergyStored() : t != null ? t.getCapacity() : 1;
        if (v < 0) return running ? 15 : 0;
        return v == 0 ? 0 : (int) Math.min(15, 1 + v * 14 / Math.max(1, cap));
    }

    private final com.sqc.nuclearsq.module.MachineModules modules = new com.sqc.nuclearsq.module.MachineModules();

    private void scanModules() {
        if (!formed || formedRot < 0) {
            crafter.setModules(com.sqc.nuclearsq.module.ModuleBonus.NONE);
            return;
        }
        crafter.setModules(modules.scan(level, structure().world(worldPosition, formedRot, 0, 0, 0),
                structure().world(worldPosition, formedRot, machine.w - 1, machine.h - 1, machine.d - 1), getBlockState().getBlock().getName()));
    }

    private int work() {
        return switch (machine) {
            case COOLING_TOWER_LARGE, COOLING_TOWER_XL -> {
                BigMachine.Port in = machine.port("coolant_in");
                int n = convert("coolant_in", in.rate(), "coolant_out", ModFluids.COOLANT.source.get(), 1, 1, null, null);
                evaporated += n * machine.extra("evaporation_mb_t");
                int lost = evaporated / Math.max(1, in.rate());
                evaporated -= lost * in.rate();
                if (lost > 0) tanks.get("coolant_out").drain(lost, IFluidHandler.FluidAction.EXECUTE);
                if (n > 0 && ticks % 4 == 0 && level instanceof net.minecraft.server.level.ServerLevel sl) plume(sl, Math.min(1.0, n / (double) Math.max(1, in.rate())));
                yield n;
            }
            case RELIEF_TANK -> reliefTank();
            case HIGH_PRESSURE_INJECTION -> {
                checkLowPressure();
                powered = emergencyActive() && usePower();
                yield powered ? convert("primary_in", rate("primary_in"), "primary_out", ModFluids.PRIMARY_WATER.source.get(),
                        1, 1, null, null) : 0;
            }
            case PRESSURIZER -> pressurizer();
            case STEAM_SEPARATOR, STEAM_DRYER -> {
                if (tanks.get("wet_steam_in") != null && tanks.get("wet_steam_in").getFluid().is(ModFluids.STEAM.source.get())) {
                    yield convert("wet_steam_in", rate("wet_steam_in"), "steam_out", ModFluids.STEAM.source.get(), 1, 1, null, null);
                }
                boolean dirty = tanks.get("wet_steam_in") != null && tanks.get("wet_steam_in").getFluid().is(ModFluids.CONTAMINATED_STEAM.source.get());
                yield convert("wet_steam_in", rate("wet_steam_in"), "steam_out", (dirty ? ModFluids.CONTAMINATED_STEAM : ModFluids.STEAM).source.get(),
                        rate("steam_out"), rate("wet_steam_in"), "water_out", Fluids.WATER);
            }
            case STEAM_GENERATOR -> steamGenerator();
            case FAST_BREEDER -> {
                powered = usePower();
                if (!powered) yield 0;
                yield breeder.tick(level, worldPosition, items, tanks.get("sodium_in"), tanks.get("feedwater_in"), tanks.get("wet_steam_out"),
                        rate("wet_steam_out"), ticks % 20 == 0);
            }
            case CONTROL_ROD_DRIVE -> controlRods();
            case EMERGENCY_COOLING -> {
                checkLowPressure();
                powered = emergencyActive() && usePower();
                if (powered && (manual || triggered()) && boronStock > 0 && ticks % 40 == 0) {
                    BigMachineBlockEntity r = reactor(q -> true);
                    if (r != null && r.core != null && r.core.chem().dose()) {
                        boronStock--;
                        setChanged();
                    }
                }
                yield powered && !loopFull() ? convert("water_in", rate("water_in"), "primary_out", ModFluids.PRIMARY_WATER.source.get(),
                        rate("primary_out"), rate("water_in"), null, null) : 0;
            }
            case CONTAINMENT -> containment();
            case HEAVY_ASSEMBLER, REACTOR_ASSEMBLER, COMPUTE_ASSEMBLER, REPROCESSING_PLANT, ORE_MILL, ENRICHMENT_CENTRIFUGE, COAL_GENERATOR, SPENT_FUEL_RACK,
                 PARTICLE_ASSEMBLER, SMALL_PARTS_ASSEMBLER, MICRO_ASSEMBLER, CLEAN_ROOM, EQUIPMENT_ASSEMBLER, WEAPONS_PROCESSING, LEACHING_PLANT, UF6_CONVERTER, PELLET_FACTORY, FOUNDRY, SLAG_CRUSHER, ACID_PLANT, BORIC_ACID_PLANT, ION_EXCHANGER, HEAVY_WATER_PLANT, POLYMER_PLANT, VITRIFICATION_PLANT, CHEMICAL_LAB, PLATE_MILL, HAMMER_MILL, KILN, BLAST_FURNACE, CNC_MILL, SOLDER_MACHINE -> {
                if (crafter != null && formed && ProductionTier.handles(machine) && --tierSync <= 0) {
                    tierSync = 40;
                    crafter.setTier(ProductionTier.stageOf(level, this));
                }
                int n = crafter == null ? 0 : crafter.tick(level, worldPosition, formed, enabled);
                if (machine == BigMachine.ION_EXCHANGER && crafter != null) {
                    int runs = crafter.takeRuns();
                    BigMachineBlockEntity r = runs > 0 ? reactor(q -> true) : null;
                    if (r != null && r.core != null) for (int i = 0; i < runs; i++) r.core.chem().exchange();
                }
                powered = crafter != null && crafter.status() != BigCrafter.NO_POWER;
                yield n;
            }
            case EXO_STATION -> {
                powered = true;
                yield exo.tick() ? 1 : 0;
            }
            case MAIN_COOLANT_PUMP, HIGH_PRESSURE_PUMP -> pump();
            case SOLAR_FIELD -> {
                if (ticks % 20 == 0) {
                    BlockPos sky = structure().world(worldPosition, Math.max(formedRot, 0), 2, 2, 2);
                    solarRate = Math.round(machine.extra("fe_per_tick_peak") * com.sqc.nuclearsq.power.SolarMath.factor(level, sky, true));
                }
                powered = true;
                if (energy != null && solarRate > 0) energy.add(solarRate);
                yield solarRate;
            }
            case WIND_TURBINE_SMALL, WIND_TURBINE_MEDIUM, WIND_TURBINE_LARGE -> {
                if (ticks % 20 == 0) windTick();
                powered = true;
                if (energy != null) {
                    energy.setStored(0);
                    if (windRate > 0) energy.add(windRate);
                }
                yield windRate;
            }
            case CONTROL_DESK -> {
                powered = usePower();
                if (desk != null && (powered ? desk.tick(level, this, level.registryAccess()) : desk.clear(level.registryAccess()))) syncDesk();
                yield powered ? 1 : 0;
            }
            case REHEATER -> convert("steam_in", rate("steam_in"), "steam_out", ModFluids.SUPERHEATED_STEAM.source.get(),
                    rate("steam_out"), rate("steam_in"), "water_out", Fluids.WATER);
            case FEEDWATER_HEATER -> feedwaterHeater();
            case STEAM_PURIFIER -> purifier();
            case CONDENSER -> condenser();
            case INTAKE_PUMP -> intake();
            case WATER_TREATMENT -> {
                mergeBigInlets();
                spreadFeedwater();
                if (!powered("water_in", "feedwater_out")) yield 0;
                int max = 4 * rate("water_in"), done = 0;
                for (String src : new String[] {"water_in_9", "water_in_10", "water_in"}) {
                    if (done < max) done += convert(src, max - done, "feedwater_out", ModFluids.FEEDWATER.source.get(), 1, 1, null, null);
                }
                yield done;
            }
            case FEEDWATER_PUMP -> feedwaterPump();
            case WASTE_REPOSITORY -> repository();
            case BATTERY_SMALL, BATTERY_MEDIUM, BATTERY_LARGE -> {
                powered = true;
                yield batteryFlow;
            }
            case COMPONENT_ASSEMBLER, QUANTUM_COMPUTER -> {
                int n = assembler == null ? 0 : assembler.tick(level, this, formed, enabled);
                powered = assembler != null && assembler.status() != com.sqc.nuclearsq.storage.ComponentAssembler.NO_POWER;
                yield n;
            }
            case COMPUTE_CLUSTER -> {
                int n = cluster == null ? 0 : cluster.tick(this, formed, enabled);
                powered = n > 0;
                yield n;
            }
            case SERVER_RACK_SMALL, SERVER_RACK, SERVER_RACK_LARGE, PATTERN_RACK -> {
                powered = rack != null && rack.online();
                yield powered ? 1 : 0;
            }
            case SUBSTATION -> {
                powered = true;
                yield substation == null ? 0 : substation.tick(level, formed, enabled);
            }
            case FUSION_REACTOR -> fusionTick();
            case DEUTERIUM_SEPARATOR -> separator();
            case ELECTROLYZER -> electrolysis();
            case OIL_DERRICK -> derrick();
            case DISTILLATION_COLUMN -> distill();
            case CRACKER -> cracker();
            case FUEL_CELL -> fuelCell();
            case GAS_GENERATOR -> gasGenerator();
            case FLUID_BUFFER -> {
                powered = true;
                yield 0;
            }
            case DECON_DOCK -> {
                powered = true;
                yield dock == null ? 0 : dock.tick(level, formedRot, enabled);
            }
            case SHAFT_GENERATOR -> {
                int n = shaftIn;
                shaftIn = 0;
                powered = true;
                yield n;
            }
        };
    }

    private int fusionTick() {
        if (fusion == null || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return 0;
        BigMachine.Port sig = machine.port("control_signal");
        BlockPos sp = sig == null ? null : portPos(sig);
        signalIn = sp == null ? 0 : level.getBestNeighborSignal(sp);
        int n = fusion.tick(sl, worldPosition, tanks, energy, items, enabled, signalIn);
        powered = !"no_power".equals(fusion.reason());
        signalOut = fusion.signalOut();
        setSignal("plasma_signal", signalOut);
        return n;
    }

    private com.sqc.nuclearsq.oil.OilWellBlockEntity well;
    private int sulfurAcc;

    private int derrick() {
        FluidTank out = tanks.get("crude_out");
        if (out == null) return 0;
        if (ticks % 20 == 1 || level.getGameTime() < 40) {
            well = null;
            for (int x = 0; formedRot >= 0 && well == null && x < machine.w; x++) {
                for (int z = 0; well == null && z < machine.d; z++) {
                    if (level.getBlockEntity(structure().world(worldPosition, formedRot, x, -1, z)) instanceof com.sqc.nuclearsq.oil.OilWellBlockEntity be) well = be;
                }
            }
        }
        int want = Math.min(machine.extra("pump_mb_t"), out.getSpace());
        if (well == null || well.isRemoved() || want <= 0) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        if (!powered) return 0;
        int n = well.drain(level, want, false);
        if (n > 0) out.fill(new FluidStack(ModFluids.CRUDE_OIL.source.get(), n), IFluidHandler.FluidAction.EXECUTE);
        return n;
    }

    private int distill() {
        FluidTank in = tanks.get("crude_in"), na = tanks.get("naphtha_out"), di = tanks.get("diesel_out"), ta = tanks.get("tar_out");
        int n = machine.extra("crude_mb_t"), a = n * machine.extra("naphtha_pm") / 1000, b = n * machine.extra("diesel_pm") / 1000, c = n * machine.extra("tar_pm") / 1000;
        boolean sulfur = sulfurAcc + n >= machine.extra("crude_per_sulfur");
        ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "sulfur_dust_sqc")));
        if (in == null || na == null || di == null || ta == null || items == null || in.getFluidAmount() < n || na.getSpace() < a || di.getSpace() < b
                || ta.getSpace() < c || sulfur && !net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(items, s, true).isEmpty()) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        if (!powered) return 0;
        in.drain(n, IFluidHandler.FluidAction.EXECUTE);
        na.fill(new FluidStack(ModFluids.NAPHTHA.source.get(), a), IFluidHandler.FluidAction.EXECUTE);
        di.fill(new FluidStack(ModFluids.DIESEL.source.get(), b), IFluidHandler.FluidAction.EXECUTE);
        ta.fill(new FluidStack(ModFluids.TAR.source.get(), c), IFluidHandler.FluidAction.EXECUTE);
        sulfurAcc += n;
        if (sulfur) {
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(items, s, false);
            sulfurAcc -= machine.extra("crude_per_sulfur");
        }
        return n;
    }

    private int cracker() {
        int n = machine.extra("naphtha_mb_t");
        FluidTank in = tanks.get("naphtha_in"), out = tanks.get("ethylene_out");
        if (in == null || out == null || in.getFluidAmount() < n || out.getSpace() < n * machine.extra("ethylene_pm") / 1000) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        return powered ? convert("naphtha_in", n, "ethylene_out", ModFluids.ETHYLENE.source.get(), machine.extra("ethylene_pm"), 1000, null, null) : 0;
    }

    private int separator() {
        FluidTank water = tanks.get("water_in"), deut = tanks.get("deuterium_out");
        if (water == null || deut == null) return 0;
        int ratio = Math.max(1, machine.extra("water_ratio")), outCmb = machine.extra("out_cmb_t");
        if (water.getFluid().is(ModFluids.HEAVY_WATER.source.get())) ratio = Math.max(1, ratio / Math.max(1, machine.extra("heavy_factor")));
        int need = ratio * outCmb / 100;
        if (water.getFluidAmount() < need || deut.getSpace() < 3) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        if (!powered) return 0;
        water.drain(need, IFluidHandler.FluidAction.EXECUTE);
        sepAcc += outCmb;
        int mb = sepAcc / 100;
        if (mb > 0) {
            deut.fill(new FluidStack(ModFluids.DEUTERIUM.source.get(), mb), IFluidHandler.FluidAction.EXECUTE);
            sepAcc -= mb * 100;
        }
        return need;
    }

    private int fuelCell() {
        FluidTank h2 = tanks.get("hydrogen_in"), o2 = tanks.get("oxygen_in");
        int mb = machine.extra("h2_mb_t");
        boolean run = energy != null && h2 != null && o2 != null && h2.getFluidAmount() >= mb && o2.getFluidAmount() >= mb / 2;
        powered = true;
        if (energy == null) return 0;
        energy.setStored(0);
        if (!run) return 0;
        h2.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        o2.drain(mb / 2, IFluidHandler.FluidAction.EXECUTE);
        energy.add(machine.extra("fe_per_tick"));
        return mb;
    }

    private int gasGenerator() {
        FluidTank gas = tanks.get("ethylene_in");
        int mb = machine.extra("ethylene_mb_t");
        powered = true;
        if (energy == null) return 0;
        energy.setStored(0);
        if (gas == null || gas.getFluidAmount() < mb) return 0;
        gas.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        energy.add(machine.extra("fe_per_tick"));
        return mb;
    }

    private int electrolysis() {
        FluidTank water = tanks.get("water_in"), h2 = tanks.get("hydrogen_out"), o2 = tanks.get("oxygen_out");
        if (water == null || h2 == null || o2 == null) return 0;
        int h2Cmb = Math.max(1, machine.extra("h2_cmb_t"));
        int need = Math.max(1, (h2Cmb + 99) / 100);
        if (water.getFluidAmount() < need || h2.getSpace() < 4 || o2.getSpace() < 2) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        if (!powered) return 0;
        water.drain(need, IFluidHandler.FluidAction.EXECUTE);
        elecAcc += h2Cmb;
        int mb = elecAcc / 100;
        if (mb > 0) {
            h2.fill(new FluidStack(ModFluids.HYDROGEN.source.get(), mb), IFluidHandler.FluidAction.EXECUTE);
            o2.fill(new FluidStack(ModFluids.OXYGEN.source.get(), Math.max(1, mb / 2)), IFluidHandler.FluidAction.EXECUTE);
            elecAcc -= mb * 100;
        }
        return need;
    }

    @Nullable
    private String rankMark() {
        return switch (machine) {
            case OIL_DERRICK -> lastMb > 0 ? "rank:oil" : null;
            case FUSION_REACTOR -> fusion != null && fusion.phase() == FusionCore.PLASMA ? "rank:fusion" : null;
            case WASTE_REPOSITORY -> disposed > 0 ? "rank:waste" : null;
            case FAST_BREEDER -> items != null && ticks % 20 == 0 && BreederCore.bredCount(items) > 0 ? "rank:breeder" : null;
            case CLEAN_ROOM -> crafter != null && crafter.takeRuns() > 0 ? "rank:chip" : null;
            case SERVER_RACK_SMALL, SERVER_RACK, SERVER_RACK_LARGE -> rack != null && ticks % 20 == 0
                    && rack.space().used() >= com.sqc.nuclearsq.progression.Titles.DATA_HOARDER_ITEMS ? "rank:storage" : null;
            default -> null;
        };
    }

    private int repository() {
        if (items == null) return 0;
        int slot = -1;
        for (int i = 0; i < items.getSlots() && slot < 0; i++) if (!items.getStackInSlot(i).isEmpty()) slot = i;
        if (slot < 0) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        if (!powered) return 0;
        if (ticks % 20 == 0) {
            int n = items.extractItem(slot, REPOSITORY_BATCH, false).getCount();
            disposed += n;
            setChanged();
        }
        return 1;
    }

    public static final int REPOSITORY_BATCH = 4;

    private boolean emergencyActive() {
        return manual || machine == BigMachine.EMERGENCY_COOLING || triggered();
    }

    private boolean triggered() {
        return lowPressure || level.hasNeighborSignal(worldPosition);
    }

    private boolean lowPressure;

    private void checkLowPressure() {
        if (ticks % 20 != 0) return;
        BigMachineBlockEntity r = reactor(q -> true);
        float p = r == null ? -1 : r.primaryPressure();
        lowPressure = p >= 0 && p < PRESSURE_LOW;
    }

    public boolean lowPressureStart() { return lowPressure; }

    private boolean loopFull() {
        BigMachineBlockEntity r = reactor(q -> true);
        if (r == null) return false;
        for (String n : new String[] {"primary_cold_in", "primary_hot_out"}) {
            FluidTank t = r.tanks.get(n);
            if (t != null && t.getFluidAmount() * 4 >= t.getCapacity() * 3) return true;
        }
        return false;
    }

    private int rate(String port) {
        BigMachine.Port p = machine.port(port);
        return p == null ? 0 : p.rate();
    }

    private boolean usePower() {
        if (energy == null) return true;
        int use = machine.energy.use();
        if (energy.getEnergyStored() < use) return false;
        energy.drain(use);
        return true;
    }

    private int convert(String in, int max, String out1, Fluid f1, int a, int b, @Nullable String out2, @Nullable Fluid f2) {
        FluidTank tin = tanks.get(in), t1 = tanks.get(out1), t2 = out2 == null ? null : tanks.get(out2);
        if (tin == null || t1 == null || b <= 0) return 0;
        int n = Math.min(max, tin.getFluidAmount());
        if (a > 0) n = Math.min(n, (t1.getSpace() * b) / a);
        while (n > 0 && t2 != null && n - n * a / b > t2.getSpace()) n--;
        if (n <= 0) return 0;
        int o1 = n * a / b, o2 = n - o1;
        if (o1 > 0 && t1.fill(new FluidStack(f1, o1), IFluidHandler.FluidAction.SIMULATE) < o1) return 0;
        if (t2 != null && o2 > 0 && t2.fill(new FluidStack(f2, o2), IFluidHandler.FluidAction.SIMULATE) < o2) return 0;
        tin.drain(n, IFluidHandler.FluidAction.EXECUTE);
        if (o1 > 0) t1.fill(new FluidStack(f1, o1), IFluidHandler.FluidAction.EXECUTE);
        if (t2 != null && o2 > 0) t2.fill(new FluidStack(f2, o2), IFluidHandler.FluidAction.EXECUTE);
        return n;
    }

    private BlockPos center() {
        return structure().world(worldPosition, Math.max(formedRot, 0), machine.w / 2, machine.h / 2, machine.d / 2);
    }

    @Nullable
    private BigMachineBlockEntity reactor(Predicate<BlockPos> where) {
        java.util.List<BigMachineBlockEntity> l = reactors(where);
        return l.isEmpty() ? null : l.get(0);
    }

    private java.util.List<BigMachineBlockEntity> reactors(Predicate<BlockPos> where) {
        double range = REACTOR_RANGE + Math.max(machine.w, machine.d) / 2.0
                + Math.max(BigMachine.CONTROL_ROD_DRIVE.w, BigMachine.CONTROL_ROD_DRIVE.d) / 2.0;
        double hr = BigMachine.CONTAINMENT.w / 2.0 + REACTOR_RANGE + Math.max(machine.w, machine.d) / 2.0;
        net.minecraft.world.phys.Vec3 o = net.minecraft.world.phys.Vec3.atCenterOf(center());
        java.util.Map<BigMachineBlockEntity, Double> found = new java.util.HashMap<>();
        for (BigMachineBlockEntity d : level.isClientSide ? CLIENT_DRIVES : DRIVES) {
            if (d.isRemoved() || d.level != level || !d.formed || !where.test(BlockPos.containing(d.coreCenter()))) continue;
            net.minecraft.world.phys.Vec3 k = d.coreCenter();
            double dd = k.distanceToSqr(o), flat = (k.x - o.x) * (k.x - o.x) + (k.z - o.z) * (k.z - o.z);
            BigMachineBlockEntity hull = dd <= range * range ? null : d.containmentOnTop();
            if (hull != null && flat <= hr * hr && o.y >= hull.getBlockPos().getY() - REACTOR_RANGE
                    && o.y <= hull.getBlockPos().getY() + BigMachine.CONTAINMENT.h) dd = range * range + flat;
            else if (dd > range * range) continue;
            found.put(d, dd);
        }
        java.util.List<BigMachineBlockEntity> l = new java.util.ArrayList<>(found.keySet());
        l.sort(java.util.Comparator.comparingDouble(found::get));
        return l;
    }

    @Nullable
    public static BigMachineBlockEntity nearestDrive(net.minecraft.world.level.Level level, BlockPos origin, double range,
                                                     Predicate<BigMachineBlockEntity> filter) {
        BigMachineBlockEntity best = null;
        double bestD = range * range;
        net.minecraft.world.phys.Vec3 o = net.minecraft.world.phys.Vec3.atCenterOf(origin);
        for (BigMachineBlockEntity d : level.isClientSide ? CLIENT_DRIVES : DRIVES) {
            if (d.isRemoved() || d.level != level) continue;
            double dist = d.coreCenter().distanceToSqr(o);
            if (dist <= bestD && filter.test(d)) {
                bestD = dist;
                best = d;
            }
        }
        return best;
    }

    public static void clearDrives(boolean client) {
        (client ? CLIENT_DRIVES : DRIVES).clear();
        BAYS.clear();
        (client ? CLIENT_HULLS : HULLS).clear();
        if (!client) NO_MOBS.clear();
    }

    private boolean noMobs() {
        return machine == BigMachine.FUSION_REACTOR || machine == BigMachine.CONTAINMENT;
    }

    private AABB machineBox() {
        return AABB.encapsulatingFullBlocks(structure().world(worldPosition, formedRot, 0, 0, 0),
                structure().world(worldPosition, formedRot, machine.w - 1, machine.h - 1, machine.d - 1));
    }

    private boolean holds(net.minecraft.world.phys.Vec3 pos) {
        AABB b = machineBox();
        return b.contains(pos) && (machine != BigMachine.CONTAINMENT || Math.hypot(pos.x - b.getCenter().x, pos.z - b.getCenter().z) < machine.w / 2.0);
    }

    public static boolean noMobsAt(net.minecraft.world.level.LevelAccessor level, net.minecraft.world.phys.Vec3 pos) {
        for (BigMachineBlockEntity f : NO_MOBS) if (f.level == level && f.formed && f.formedRot >= 0 && f.holds(pos)) return true;
        return false;
    }

    private void clearMobs() {
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, machineBox(),
                e -> holds(e.position()) && !e.isPersistenceRequired() && !e.requiresCustomPersistence() && e.removeWhenFarAway(1024))) m.discard();
    }

    public net.minecraft.world.phys.Vec3 coreCenter() {
        if (machine != BigMachine.CONTROL_ROD_DRIVE) return net.minecraft.world.phys.Vec3.atCenterOf(center());
        return net.minecraft.world.phys.Vec3.atBottomCenterOf(structure().world(worldPosition, Math.max(formedRot, 0), 2, machine.ay, 2))
                .add(0, 1 + com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS / 2.0, 0);
    }

    @Nullable
    public DriveCore core() { return core; }

    public int reactorPower() {
        return core == null || !formed || vesselDry ? 0 : core.power();
    }

    public void addCooling(float amount) {
        if (core != null && formed) core.addCooling(amount);
    }

    public void scram() {
        if (powered) setRodTarget(0);
    }

    private int pump() {
        java.util.List<BigMachineBlockEntity> rs = reactors(q -> true);
        noReactor = rs.isEmpty();
        FluidTank in = tanks.get("primary_in"), out = tanks.get("primary_out");
        if (in == null || out == null) return 0;
        powered = usePower();
        if (!powered) return 0;
        boolean dry = in.isEmpty();
        int n = Math.min(Math.min(rate("primary_in"), in.getFluidAmount()), out.getSpace());
        if (n > 0) {
            in.drain(n, IFluidHandler.FluidAction.EXECUTE);
            out.fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), n), IFluidHandler.FluidAction.EXECUTE);
        }
        if (!dry) pumpDryTicks = 0;
        else if (rs.stream().anyMatch(r -> r.vesselWant > 0)) pumpDryTicks = Math.min(PUMP_DRY_TICKS, pumpDryTicks + 1);
        if (pumpDryTicks < PUMP_DRY_TICKS) for (BigMachineBlockEntity r : rs) r.addPrimaryFlow(rate("primary_in"));
        if (n > 0 || rs.isEmpty()) return Math.max(0, n);
        return dry && out.isEmpty() ? 0 : rate("primary_in");
    }

    public static final int PUMP_DRY_TICKS = 200;
    private int pumpDryTicks;
    private long pumpTick = -1;
    private int pumpNow, pumpLast;

    public void addPrimaryFlow(int mb) {
        long t = level.getGameTime();
        if (t != pumpTick) {
            pumpLast = pumpTick == t - 1 ? pumpNow : 0;
            pumpTick = t;
            pumpNow = 0;
        }
        pumpNow += mb;
    }

    public int pumpFlow() {
        if (level == null) return 0;
        long t = level.getGameTime();
        if (pumpTick == t) return Math.max(pumpNow, pumpLast);
        return pumpTick == t - 1 ? pumpNow : 0;
    }

    private int steamGenerator() {
        FluidTank hot = tanks.get("primary_hot_in"), cold = tanks.get("primary_cold_out");
        FluidTank feed = tanks.get("feedwater_in"), steam = tanks.get("wet_steam_out");
        if (hot == null || cold == null || feed == null || steam == null) return 0;
        boolean pre = feed.getFluid().is(ModFluids.PREHEATED_FEEDWATER.source.get());
        int num = Math.round(STEAM_PER_PRIMARY * 200 * (pre ? 1f + PREHEAT_BONUS : 1f)), acc = (int) preheatAcc;
        int prim = Math.min(Math.min(rate("primary_hot_in"), hot.getFluidAmount()), cold.getSpace());
        int s = Math.min((prim * num + acc) / 200, Math.min(feed.getFluidAmount(), Math.min(steam.getSpace(), rate("wet_steam_out"))));
        if (s <= 0) return 0;
        int used = Math.min(prim, Math.max(0, (s * 200 - acc + num - 1) / num));
        preheatAcc = Math.max(0, Math.min(199, acc + used * num - s * 200));
        FluidStack out = new FluidStack((tubeLeak ? ModFluids.CONTAMINATED_STEAM : ModFluids.WET_STEAM).source.get(), s);
        if (steam.fill(out, IFluidHandler.FluidAction.SIMULATE) < s) return 0;
        hot.drain(used, IFluidHandler.FluidAction.EXECUTE);
        cold.fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), used), IFluidHandler.FluidAction.EXECUTE);
        feed.drain(s, IFluidHandler.FluidAction.EXECUTE);
        steam.fill(out, IFluidHandler.FluidAction.EXECUTE);
        if (ticks % 20 == 0) wearTick();
        return s;
    }

    private boolean tubeLeak;
    private int wearSec;
    private static final double LEAK_CHANCE = 1.0 / 200_000;
    private static final int WEAR_STEP_SEC = 7200;
    public static final int WEAR_FULL_SEC = 72_000;

    private void wearTick() {
        if (wearSec < Integer.MAX_VALUE) wearSec++;
        if (tubeLeak) return;
        BigMachineBlockEntity r = reactor(q -> true);
        boolean stress = r != null && (r.primaryPressure() >= 100 || (r.core() != null && r.core().overheated()));
        if (stress || level.random.nextDouble() < LEAK_CHANCE * (1 + wearSec / (double) WEAR_STEP_SEC)) startTubeLeak();
    }

    public void startTubeLeak() {
        if (machine != BigMachine.STEAM_GENERATOR || tubeLeak) return;
        tubeLeak = true;
        setChanged();
    }

    public boolean tubeLeak() { return breeder != null ? breeder.leak() : tubeLeak; }

    public boolean hasWear() { return machine == BigMachine.STEAM_GENERATOR || breeder != null; }

    @Nullable public BreederCore breeder() { return breeder; }

    public int wearPercent() { return breeder != null ? breeder.wearPercent() : Math.min(100, (int) (100L * wearSec / WEAR_FULL_SEC)); }

    public boolean repair() {
        if (breeder != null && !enabled) {
            breeder.repair();
            setChanged();
            return true;
        }
        if (machine != BigMachine.STEAM_GENERATOR || enabled) return false;
        tubeLeak = false;
        wearSec = 0;
        setChanged();
        return true;
    }

    public static final float STEAM_PER_PRIMARY = 0.5f;

    public static final float PREHEAT_BONUS = DriveCore.PREHEAT_BONUS;

    private int feedwaterHeater() {
        FluidTank fin = tanks.get("feedwater_in"), ex = tanks.get("exhaust_in"), fout = tanks.get("feedwater_out"), wout = tanks.get("water_out");
        if (fin == null || ex == null || fout == null || wout == null) return 0;
        int perFeed = rate("feedwater_in"), perEx = rate("exhaust_in");
        int n = Math.min(Math.min(perFeed, fin.getFluidAmount()), fout.getSpace());
        if (perEx > 0) n = Math.min(n, ex.getFluidAmount() * perFeed / perEx);
        int steamUse = perFeed <= 0 ? 0 : (int) Math.ceil(n * (double) perEx / perFeed);
        if (steamUse > wout.getSpace()) {
            n = wout.getSpace() * perFeed / Math.max(1, perEx);
            steamUse = (int) Math.ceil(n * (double) perEx / perFeed);
        }
        if (fout.getFluid().is(ModFluids.FEEDWATER.source.get())) n = 0;
        if (n <= 0) {
            if (!(ex.isEmpty() || wout.getSpace() <= 0) || fout.getFluid().is(ModFluids.PREHEATED_FEEDWATER.source.get())) return 0;
            n = Math.min(Math.min(perFeed, fin.getFluidAmount()), fout.getSpace());
            if (n <= 0) return 0;
            fin.drain(n, IFluidHandler.FluidAction.EXECUTE);
            fout.fill(new FluidStack(ModFluids.FEEDWATER.source.get(), n), IFluidHandler.FluidAction.EXECUTE);
            return n;
        }
        fin.drain(n, IFluidHandler.FluidAction.EXECUTE);
        ex.drain(steamUse, IFluidHandler.FluidAction.EXECUTE);
        fout.fill(new FluidStack(ModFluids.PREHEATED_FEEDWATER.source.get(), n), IFluidHandler.FluidAction.EXECUTE);
        if (steamUse > 0) wout.fill(new FluidStack(Fluids.WATER, steamUse), IFluidHandler.FluidAction.EXECUTE);
        return n;
    }

    private boolean powered(String in, String out) {
        FluidTank tin = tanks.get(in), tout = tanks.get(out);
        if (tin == null || tout == null || tin.isEmpty() || tout.getSpace() <= 0) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return false;
        }
        powered = usePower();
        return powered;
    }

    public static final double PURIFIER_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.PURIFIER_SV_H, RESIDUE_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.RESIDUE_SV_H;

    public int residue() {
        return items == null ? 0 : items.getStackInSlot(0).getCount();
    }

    public boolean residueFull() {
        return machine == BigMachine.STEAM_PURIFIER && residue() >= machine.extra("residue_max");
    }

    private int purifier() {
        int n = 0;
        FluidTank sin = tanks.get("steam_in");
        if (sin != null && sin.getFluid().is(ModFluids.STEAM.source.get())) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return convert("steam_in", rate("steam_in"), "steam_out", ModFluids.STEAM.source.get(), 1, 1, null, null);
        }
        if (!residueFull() && powered("steam_in", "steam_out")) {
            n = convert("steam_in", rate("steam_in"), "steam_out", ModFluids.STEAM.source.get(), 1, 1, null, null);
            int every = Math.max(1, machine.extra("residue_every_mb"));
            residueAcc += n;
            while (residueAcc >= every && items != null && !residueFull()) {
                residueAcc -= every;
                ItemStack st = items.getStackInSlot(0);
                items.setStackInSlot(0, st.isEmpty() ? new ItemStack(com.sqc.nuclearsq.registry.CatalogRegistry.ITEM_MAP.get("filter_residue_sqc").get())
                        : st.copyWithCount(st.getCount() + 1));
            }
        } else if (residueFull()) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
        }
        if (ticks % 20 == 0 && level instanceof net.minecraft.server.level.ServerLevel sl) {
            double sv = (n > 0 ? PURIFIER_SV_H : 0) + residue() * RESIDUE_SV_H;
            if (sv > 0) com.sqc.nuclearsq.radiation.RadiationManager.emit(sl, worldPosition, sv);
        }
        return n;
    }

    private int condenser() {
        FluidTank ex = tanks.get("exhaust_in"), wout = tanks.get("water_out"), cin = tanks.get("coolant_in"), cout = tanks.get("coolant_out");
        if (ex == null || wout == null || cin == null || cout == null) return 0;
        if (!powered("exhaust_in", "water_out")) return 0;
        int n = Math.min(rate("exhaust_in"), Math.min(ex.getFluidAmount(), wout.getSpace()));
        int cooled = Math.min(n, coolCapacity(cin, cout));
        n = cooled + Math.min(n - cooled, machine.extra("air_rate_mb_t"));
        if (n <= 0) return 0;
        ex.drain(n, IFluidHandler.FluidAction.EXECUTE);
        wout.fill(new FluidStack(Fluids.WATER, n), IFluidHandler.FluidAction.EXECUTE);
        useCoolant(cin, cout, cooled);
        return n;
    }

    private int reliefTank() {
        FluidTank in = tanks.get("relief_in"), out = tanks.get("primary_out"), cin = tanks.get("coolant_in"), cout = tanks.get("coolant_out");
        if (in == null || out == null || cin == null || cout == null) return 0;
        int n = Math.min(Math.min(rate("relief_in"), in.getFluidAmount()), Math.min(out.getSpace(), coolCapacity(cin, cout)));
        if (n <= 0) return 0;
        in.drain(n, IFluidHandler.FluidAction.EXECUTE);
        out.fill(new FluidStack(ModFluids.PRIMARY_WATER.source.get(), n), IFluidHandler.FluidAction.EXECUTE);
        useCoolant(cin, cout, n);
        return n;
    }

    private static int coolCapacity(FluidTank cin, FluidTank cout) {
        int f = CoolantTypes.heatFactor(cin.getFluid());
        return f <= 0 ? 0 : f * Math.min(cin.getFluidAmount(), cout.getSpace());
    }

    private static void useCoolant(FluidTank cin, FluidTank cout, int heat) {
        int f = CoolantTypes.heatFactor(cin.getFluid());
        if (heat <= 0 || f <= 0) return;
        int used = (heat + f - 1) / f;
        cin.drain(used, IFluidHandler.FluidAction.EXECUTE);
        cout.fill(new FluidStack(ModFluids.HOT_COOLANT.source.get(), used), IFluidHandler.FluidAction.EXECUTE);
    }

    public static final float PRESSURE_SET = 50, PRESSURE_RELIEF = 90, PRESSURE_LOW = 20;
    private static final float HEATER_STEP = 0.25f, RELIEF_STEP = 1f, EMPTY_STEP = 0.5f, COOL_STEP = 0.05f;
    private static final int HOT_HEAT = 60;
    public static final float BAR_PER_PERCENT = 3.1f;
    private float pressure;
    private boolean venting;

    private int pressurizer() {
        BigMachineBlockEntity r = reactor(q -> true);
        noReactor = r == null;
        FluidTank in = tanks.get("primary_in"), relief = tanks.get("relief_out");
        if (in == null || relief == null) return 0;
        boolean filled = in.getFluidAmount() >= in.getCapacity() / 20;
        int coreHeat = r != null && r.core() != null ? r.core().heatPercent() : 0;
        float dp = coreHeat >= HOT_HEAT ? (coreHeat - HOT_HEAT) / 100f : -COOL_STEP;
        boolean heat = filled && pressure < PRESSURE_SET;
        if (heat) {
            powered = usePower();
            if (powered) dp += HEATER_STEP;
        } else {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
        }
        if (!filled) dp -= EMPTY_STEP;
        venting = pressure > PRESSURE_RELIEF || (venting && pressure > PRESSURE_RELIEF - 5);
        int vented = 0;
        if (venting) {
            vented = Math.min(Math.min(rate("relief_out"), in.getFluidAmount()), relief.getSpace());
            if (vented > 0) {
                in.drain(vented, IFluidHandler.FluidAction.EXECUTE);
                relief.fill(new FluidStack(ModFluids.HOT_PRIMARY_WATER.source.get(), vented), IFluidHandler.FluidAction.EXECUTE);
                dp -= RELIEF_STEP;
            }
        }
        pressure = Math.max(0, Math.min(100, pressure + dp));
        signalOut = Math.round(15f * pressure / 100f);
        setSignal("pressure_signal", signalOut);
        if (r != null) r.reportPressure(pressure);
        return Math.max(vented, filled ? 1 : 0);
    }

    public float pressure() { return pressure; }

    public void setPressure(float p) { pressure = Math.max(0, Math.min(100, p)); }
    public boolean venting() { return venting; }

    private long pressureTick = -100;
    private float reportedPressure;

    public void reportPressure(float p) {
        pressureTick = level.getGameTime();
        reportedPressure = p;
    }

    public float primaryPressure() {
        return level != null && level.getGameTime() - pressureTick <= 1 ? reportedPressure : -1;
    }

    private static final int[][] INTAKE_FEET = {{2, 0, 1}, {3, 0, 1}, {2, 0, 2}, {3, 0, 2}, {5, 0, 1}, {6, 0, 1}, {5, 0, 2}, {6, 0, 2}};
    private boolean intakeWet;

    public boolean intakeWet() {
        return intakeWet;
    }

    private int intake() {
        if (ticks % 20 == 1 || level.getGameTime() < 40) {
            boolean wet = formedRot >= 0;
            for (int i = 0; wet && i < INTAKE_FEET.length; i++) {
                int[] f = INTAKE_FEET[i];
                var fs = level.getFluidState(structure().world(worldPosition, formedRot, f[0], f[1], f[2]));
                wet = fs.is(FluidTags.WATER) && fs.isSource();
            }
            intakeWet = wet;
        }
        boolean space = false;
        for (BigMachine.Port p : machine.ports) {
            FluidTank t = tanks.get(p.name());
            if (t != null && p.out() && t.getSpace() > 0) space = true;
        }
        if (!intakeWet || !space) {
            powered = energy == null || energy.getEnergyStored() >= machine.energy.use();
            return 0;
        }
        powered = usePower();
        if (!powered) return 0;
        int n = 0;
        for (BigMachine.Port p : machine.ports) {
            FluidTank t = tanks.get(p.name());
            if (t != null && p.out()) n += t.fill(new FluidStack(Fluids.WATER, Math.min(p.rate(), t.getSpace())), IFluidHandler.FluidAction.EXECUTE);
        }
        pushToNeighbours();
        return n;
    }

    private void pushToNeighbours() {
        for (BigMachine.Port p : machine.ports) {
            FluidTank t = tanks.get(p.name());
            if (t == null || !p.out() || t.isEmpty()) continue;
            BlockPos pos = portPos(p);
            for (Direction d : Direction.values()) {
                if (!(level.getBlockEntity(pos.relative(d)) instanceof BigPortBlockEntity pe) || pe.kind() != BigPortKind.BIG_WATER) continue;
                BigMachineBlockEntity c = pe.findController();
                IFluidHandler h = c == null || c == this ? null : c.fluidsAt(pe.portName());
                if (h == null) continue;
                FluidStack offer = t.drain(Math.min(p.rate(), t.getFluidAmount()), IFluidHandler.FluidAction.SIMULATE);
                t.drain(h.fill(offer, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private int mergeBigInlets() {
        FluidTank main = tanks.get("water_in");
        if (main == null) return 0;
        int n = 0;
        for (int k = 0; k < 9; k++) {
            FluidTank t = tanks.get("water_in_" + (k < 2 ? 9 + k : k));
            if (t == null || t.isEmpty()) continue;
            int moved = main.fill(t.getFluid(), IFluidHandler.FluidAction.EXECUTE);
            if (moved > 0) {
                t.drain(moved, IFluidHandler.FluidAction.EXECUTE);
                n += moved;
            }
        }
        return n;
    }

    private void spreadFeedwater() {
        FluidTank main = tanks.get("feedwater_out");
        if (main == null) return;
        for (int i = 2; i <= 4; i++) {
            FluidTank t = tanks.get("feedwater_out_" + i);
            if (t == null) continue;
            int diff = (main.getFluidAmount() - t.getFluidAmount()) / 2;
            if (diff <= 0) continue;
            FluidStack part = main.drain(Math.min(diff, t.getSpace()), IFluidHandler.FluidAction.SIMULATE);
            if (!part.isEmpty()) main.drain(t.fill(part, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private int feedwaterPump() {
        FluidTank in = tanks.get("feedwater_in"), out = tanks.get("feedwater_out");
        if (in == null || out == null) return 0;
        pullInput(machine.port("feedwater_in"), in);
        if (!powered("feedwater_in", "feedwater_out")) return 0;
        FluidStack offer = in.drain(rate("feedwater_out"), IFluidHandler.FluidAction.SIMULATE);
        int n = out.fill(offer, IFluidHandler.FluidAction.EXECUTE);
        if (n > 0) in.drain(n, IFluidHandler.FluidAction.EXECUTE);
        return n;
    }

    private void pullInput(@Nullable BigMachine.Port p, FluidTank t) {
        BlockPos pos = p == null ? null : portPos(p);
        if (pos == null) return;
        int left = Math.min(p.rate(), t.getSpace());
        for (Direction dir : Direction.values()) {
            if (left <= 0) break;
            BlockPos n = pos.relative(dir);
            if (level.getBlockEntity(n) instanceof BigPortBlockEntity || level.getBlockEntity(n) instanceof BigMachineBlockEntity) continue;
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, n, dir.getOpposite());
            if (h == null) continue;
            FluidStack got = h.drain(left, IFluidHandler.FluidAction.SIMULATE);
            if (got.isEmpty() || !t.isFluidValid(got)) continue;
            left -= FluidUtil.tryFluidTransfer(t, h, got.copyWithAmount(left), true).getAmount();
        }
    }

    private int controlRods() {
        vessel();
        if (core != null && level instanceof net.minecraft.server.level.ServerLevel sl) {
            float max = Math.max(1, machine.extra("vessel_flow_mb_t"));
            core.configure(machine.extra("meltdown_ticks"), machine.extra("heat_rise_pct"));
            int missing = Math.round(vesselMissing / PartStages.moderatorPower(moderator));
            float insertion = vesselWant / (max * PartStages.moderatorPower(moderator));
            core.chem().tick(sl, coreCenter(), ticks, insertion, 1f + (CoreChemistry.MOX_XENON - 1f) * moxShare);
            float norm = Math.min(1f, max * PartStages.moderatorPower(moderator) / Math.max(1f, vesselWant));
            if (core.tick(sl, coreCenter(), ticks, insertion, Math.round(vesselMb * norm), Math.round(missing * norm),
                    PartStages.moderatorHeat(moderator) * (1f + (CoreChemistry.MOX_HEAT - 1f) * moxShare), PartStages.coolerCooling(cooler))) {
                com.sqc.nuclearsq.explosion.NuclearExplosion.detonate(sl, coreCenter());
                return 0;
            }
        }
        signalOut = Math.round(15f * reactorPower() / DriveCore.MAX_POWER);
        setSignal("flux_signal", signalOut);
        BigMachine.Port sig = machine.port("control_signal");
        BlockPos sp = sig == null ? null : portPos(sig);
        signalIn = sp == null ? 0 : level.getBestNeighborSignal(sp);
        powered = usePower();
        if (!powered) return 0;
        int want = wantedRod();
        if (rod != want && ticks % ROD_STEP_TICKS == 0) {
            FluidTank hyd = tanks.get("hydraulic_in");
            int cost = rate("hydraulic_in") * ROD_STEP_TICKS;
            if (hyd != null && hyd.getFluidAmount() >= cost) {
                hyd.drain(cost, IFluidHandler.FluidAction.EXECUTE);
                rod += Integer.signum(want - rod);
            }
        }
        return 1;
    }

    private void vessel() {
        FluidTank in = tanks.get("primary_cold_in"), out = tanks.get("primary_hot_out");
        if (in == null || out == null) return;
        float perRod = machine.extra("vessel_flow_mb_t") / (float) shafts.length;
        float want = 0f, moxAcc = 0f, moxN = 0f;
        float[] depth = new float[shafts.length];
        for (int i = 0; i < shafts.length; i++) {
            RodShaft s = shafts[i];
            int travel = s.headY() - RodShaft.FULL_HEAD_Y;
            if (s.headY() < 0 || travel <= 0 || s.shown() <= 0f) continue;
            float fuel = rodOutput(s);
            if (fuel <= 0) continue;
            depth[i] = Math.min(1f, s.shown() / travel);
            want += perRod * depth[i] * fuel / (4f * FuelRodBlockEntity.UNIT);
            moxAcc += rodMoxShare(s) * depth[i];
            moxN += depth[i];
        }
        moxShare = moxN > 0f ? moxAcc / moxN : 0f;
        float power = PartStages.moderatorPower(moderator);
        want *= power;
        float geometry = 1f;
        float chem = core != null ? core.chem().powerFactor() : 1f;
        want *= chem;
        vesselWant = Math.round(want);
        vesselMb = 0;
        vesselMissing = 0;
        if (want > 0f) {
            vesselAcc += want;
            int whole = (int) vesselAcc;
            if (whole > 0) {
                int n = Math.min(Math.min(Math.min(whole, in.getFluidAmount()), out.getSpace()), pumpFlow());
                if (n < whole) {
                    if (ticks > 5) vesselDryTicks = 40;
                    vesselMissing = whole - n;
                }
                if (n > 0) {
                    vesselAcc -= n;
                    in.drain(n, IFluidHandler.FluidAction.EXECUTE);
                    out.fill(new FluidStack(ModFluids.HOT_PRIMARY_WATER.source.get(), n), IFluidHandler.FluidAction.EXECUTE);
                    vesselMb = n;
                    float done = n / (float) whole;
                    for (int i = 0; i < shafts.length; i++) {
                        vesselBurn[i] += depth[i] * done * power * chem * geometry;
                        while (vesselBurn[i] >= 1f) {
                            vesselBurn[i] -= 1f;
                            burnRod(shafts[i]);
                        }
                    }
                }
                vesselAcc -= (int) vesselAcc;
            }
        } else {
            vesselAcc = 0f;
        }
        if (vesselDryTicks > 0) vesselDryTicks--;
        vesselDry = vesselDryTicks > 0 && want > 0f;
        float max = Math.max(1, machine.extra("vessel_flow_mb_t"));
        float goal = vesselDry ? 380f : want > 0f ? 290f + 35f * Math.min(1f, vesselMb / max) : 25f;
        vesselTemp += (goal - vesselTemp) * 0.02f;
    }

    private int rodFuel(RodShaft s) {
        int sum = 0;
        BlockPos base = rodBase(s);
        for (int i = 1; i <= com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS; i++) {
            if (level.getBlockEntity(base.above(s.headY() - i)) instanceof FuelRodBlockEntity f) sum += f.getRemaining();
        }
        return sum;
    }

    private float rodOutput(RodShaft s) {
        float sum = 0f;
        BlockPos base = rodBase(s);
        for (int i = 1; i <= com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS; i++) {
            BlockPos p = base.above(s.headY() - i);
            if (level.getBlockEntity(p) instanceof FuelRodBlockEntity f) {
                float mox = f.isMox() ? CoreChemistry.MOX_POWER : 1f;
                sum += f.getRemaining() * com.sqc.nuclearsq.reactor.FuelRodBlock.output(level.getBlockState(p)) * mox;
            }
        }
        return sum;
    }

    private float rodMoxShare(RodShaft s) {
        int total = 0, mox = 0;
        BlockPos base = rodBase(s);
        for (int i = 1; i <= com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS; i++) {
            if (level.getBlockEntity(base.above(s.headY() - i)) instanceof FuelRodBlockEntity f && f.getRemaining() > 0) {
                total += f.getRemaining();
                if (f.isMox()) mox += f.getRemaining();
            }
        }
        return total == 0 ? 0f : mox / (float) total;
    }

    private void burnRod(RodShaft s) {
        BlockPos base = rodBase(s);
        for (int i = 1; i <= com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS; i++) {
            if (level.getBlockEntity(base.above(s.headY() - i)) instanceof FuelRodBlockEntity f && f.burnOne()) return;
        }
    }

    private float moxShare;

    public float moxShare() { return moxShare; }

    private int moderator, cooler;

    private int boronStock;
    public static final int BORON_STOCK_MAX = 8;

    public void stockBoron(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (boronStock >= BORON_STOCK_MAX) {
            player.displayClientMessage(Component.translatable("message.nuclearsq.boron_stock_full", boronStock), true);
            return;
        }
        int n = player.getAbilities().instabuild ? 1 : Math.min(held.getCount(), BORON_STOCK_MAX - boronStock);
        boronStock += n;
        if (!player.getAbilities().instabuild) held.shrink(n);
        setChanged();
        player.displayClientMessage(Component.translatable("message.nuclearsq.boron_stocked", boronStock, BORON_STOCK_MAX), true);
    }

    public int moderator() { return moderator; }
    public int cooler() { return cooler; }

    public void setParts(int moderatorStage, int coolerStage) {
        moderator = Mth.clamp(moderatorStage, 0, PartStages.MODERATORS.size());
        cooler = Mth.clamp(coolerStage, 0, PartStages.COOLERS.size());
        setChanged();
    }

    public boolean installPart(Player player, InteractionHand hand) {
        if (machine != BigMachine.CONTROL_ROD_DRIVE) return false;
        ItemStack held = player.getItemInHand(hand);
        if (core != null && held.is(com.sqc.nuclearsq.registry.CatalogRegistry.ITEM_MAP.get("boric_acid_sqc").get())) {
            if (!core.chem().dose()) {
                player.displayClientMessage(Component.translatable("message.nuclearsq.boron_full", core.chem().boron()), true);
                return true;
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
            setChanged();
            player.displayClientMessage(Component.translatable("message.nuclearsq.boron_dosed", core.chem().boron()), true);
            return true;
        }
        int m = PartStages.moderatorStage(held), c = PartStages.coolerStage(held);
        if (m == 0 && c == 0) return false;
        ItemStack back;
        if (m > 0) {
            back = PartStages.stack(PartStages.MODERATORS, moderator);
            moderator = m;
        } else {
            back = PartStages.stack(PartStages.COOLERS, cooler);
            cooler = c;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        if (!back.isEmpty() && !player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(back);
        setChanged();
        player.displayClientMessage(Component.translatable(m > 0 ? "message.nuclearsq.part_moderator" : "message.nuclearsq.part_cooler",
                Component.translatable(held.getDescriptionId()), m > 0 ? (Object) Math.round(PartStages.moderatorPower(m) * 100) : (Object) PartStages.coolerCooling(c)), true);
        return true;
    }

    public boolean removePart(Player player) {
        if (machine != BigMachine.CONTROL_ROD_DRIVE || moderator == 0 && cooler == 0) return false;
        ItemStack back;
        if (moderator > 0) {
            back = PartStages.stack(PartStages.MODERATORS, moderator);
            moderator = 0;
        } else {
            back = PartStages.stack(PartStages.COOLERS, cooler);
            cooler = 0;
        }
        player.getInventory().placeItemBackInInventory(back);
        setChanged();
        player.displayClientMessage(Component.translatable("message.nuclearsq.part_removed", Component.translatable(back.getDescriptionId())), true);
        return true;
    }

    public boolean vesselDry() { return vesselDry; }
    public boolean rodFault() { return rodFault; }
    public int vesselMb() { return vesselMb; }
    public int vesselTemp() { return Math.round(vesselTemp); }

    public int rodCount() {
        int n = 0;
        for (RodShaft s : shafts) if (s.headY() >= 0) n++;
        return n;
    }

    public int fuelPercent() {
        if (level == null) return 0;
        int n = 0, sum = 0;
        for (RodShaft s : shafts) {
            if (s.headY() < 0) continue;
            n++;
            sum += rodFuel(s);
        }
        return n == 0 ? 0 : Math.round(sum * 100f / (n * com.sqc.nuclearsq.reactor.FuelRodHeadBlock.ROD_BLOCKS * FuelRodBlockEntity.UNIT));
    }

    public int wantedRod() {
        return terminalMode ? target : groupSignal();
    }

    public int groupSignal() {
        int s = 0;
        for (BigMachineBlockEntity m : linked()) s = Math.max(s, m.signalIn);
        return s;
    }

    public java.util.List<BigMachineBlockEntity> linked() {
        java.util.List<BigMachineBlockEntity> out = new java.util.ArrayList<>();
        out.add(this);
        if (channel <= 0 || level == null) return out;
        for (BigMachineBlockEntity m : DRIVES) {
            if (m != this && !m.isRemoved() && m.level == level && m.channel == channel && m.formed
                    && com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(level, m.worldPosition, worldPosition) < (double) LINK_RANGE * LINK_RANGE) out.add(m);
        }
        return out;
    }

    public void setRodTarget(int value) {
        int v = Math.max(0, Math.min(ROD_MAX, value));
        for (BigMachineBlockEntity m : linked()) {
            m.target = v;
            m.terminalMode = true;
            m.setChanged();
        }
    }

    public void setTerminalMode(boolean terminal) {
        for (BigMachineBlockEntity m : linked()) {
            m.terminalMode = terminal;
            m.setChanged();
        }
    }

    public void setChannel(int value) {
        channel = Math.max(0, Math.min(CHANNELS, value));
        for (BigMachineBlockEntity m : linked()) {
            if (m == this) continue;
            target = m.target;
            terminalMode = m.terminalMode;
            break;
        }
        setChanged();
    }

    public void setShaftLocked(int shaft, boolean value) {
        if (shaft < 0 || shaft >= shafts.length || shaft >= released.length || level == null) return;
        if (shafts[shaft].setLocked(value)) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void use(net.minecraft.world.entity.player.Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer rp && !access.can(rp, owner, com.sqc.nuclearsq.machine.MachineAccess.USE)) {
            rp.displayClientMessage(Component.translatable("message.nuclearsq.access.denied"), true);
            return;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp) || machine.isCoolingTower()) {
            sendStatus(player);
            return;
        }
        if (!formed) sendStatus(player);
        if (machine == BigMachine.CONTROL_DESK) {
            if (formed) com.sqc.nuclearsq.data.DataConsoleMenu.open(sp, worldPosition);
        } else if (machine == BigMachine.CONTROL_ROD_DRIVE) openTerminal(sp);
        else if (machine == BigMachine.WASTE_REPOSITORY) {
            openMachineTerminal(sp);
            if (sp.connection != null) sp.sendSystemMessage(Component.translatable("message.nuclearsq.repository.stored", disposed));
        }
        else if (exo != null) {
            if (formed) {
                if (exo.insert(sp.getMainHandItem())) sp.level().playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
                else sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.sqc.nuclearsq.exo.ExoStationMenu(id, inv, worldPosition, exo),
                        getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
            }
        }
        else if (rack != null) openRack(sp);
        else if (assembler != null) openAssembler(sp);
        else if (BigCrafter.handles(machine)) {
            openCrafter(sp);
            if (formed && machine == BigMachine.SPENT_FUEL_RACK && sp.connection != null) sp.displayClientMessage(rackLine(), true);
        }
        else openMachineTerminal(sp);
    }

    @Override
    @Nullable
    public double[] monitorBox() { return machine.monitor; }

    @Override
    public int monitorRot() { return formedRot(); }

    @Override
    public void openMonitor(net.minecraft.server.level.ServerPlayer player) { use(player); }

    public void openTerminal(net.minecraft.server.level.ServerPlayer sp) {
        DeviceMenu.open(sp, worldPosition, DeviceMenu.ROD_DRIVE, terminalData(), Component.translatable("gui.nuclearsq.device.rod.title"));
    }

    private ContainerData terminalData() {
        final int V = DeviceMenu.ROD_VESSEL;
        return new ContainerData() {
            @Override
            public int get(int i) {
                FluidTank hyd = tanks.get("hydraulic_in");
                return switch (i) {
                    case 0 -> formed ? 1 : 0;
                    case 1 -> rod;
                    case 2 -> target;
                    case 3 -> groupSignal();
                    case 4 -> terminalMode ? 1 : 0;
                    case 5 -> channel;
                    case 6 -> linked().size();
                    case 7 -> hyd == null ? 0 : hyd.getFluidAmount();
                    case 8 -> hyd == null ? 0 : hyd.getCapacity();
                    case 9 -> energy == null ? 100 : Math.round(100f * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored()));
                    case 10 -> powered ? 1 : 0;
                    case 11 -> {
                        int n = 0;
                        for (RodShaft s : shafts) if (s.headY() >= 0) n++;
                        yield n;
                    }
                    case V + 8 -> core == null ? 0 : core.heatPercent();
                    case V + 9 -> core == null ? 0 : Math.round(core.cooling() * 100f);
                    case V + 10 -> reactorPower();
                    case V + 11 -> core != null && core.overheated() ? 1 : 0;
                    case V + 12 -> core == null ? 0 : core.meltdownLeft();
                    case V + 13 -> core == null ? 0 : core.chem().xenonPercent();
                    case V + 14 -> core == null ? 0 : core.chem().filters();
                    case V + 15 -> core == null ? 0 : core.chem().gasPercent();
                    case V, V + 1, V + 2, V + 3, V + 4, V + 5, V + 6, V + 7 -> {
                        FluidTank w = tanks.get("primary_cold_in"), st = tanks.get("primary_hot_out");
                        yield switch (i) {
                            case V + 0 -> w == null ? 0 : w.getFluidAmount();
                            case V + 1 -> w == null ? 0 : w.getCapacity();
                            case V + 2 -> st == null ? 0 : st.getFluidAmount();
                            case V + 3 -> st == null ? 0 : st.getCapacity();
                            case V + 4 -> vesselMb;
                            case V + 5 -> Math.round(vesselTemp);
                            case V + 6 -> vesselDry ? 1 : 0;
                            default -> vesselWant;
                        };
                    }
                    default -> {
                        int k = i - 12, si = k / 4;
                        if (k < 0 || si >= shafts.length) yield 0;
                        RodShaft s = shafts[si];
                        int travel = Math.max(0, s.headY() - RodShaft.FULL_HEAD_Y);
                        yield switch (k % 4) {
                            case 0 -> s.headY() >= 0 ? 1 : 0;
                            case 1 -> s.locked() ? 1 : 0;
                            case 2 -> travel == 0 ? 0 : Math.round(100f * s.shown() / travel);
                            default -> Math.round(100f * s.open());
                        };
                    }
                };
            }

            @Override
            public void set(int i, int v) { }

            @Override
            public int getCount() { return DeviceMenu.count(DeviceMenu.ROD_DRIVE); }
        };
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (machine == BigMachine.CONTROL_ROD_DRIVE && level != null) (level.isClientSide ? CLIENT_DRIVES : DRIVES).add(this);
        if (shafts.length > 0 && level != null && !level.isClientSide) BAYS.add(this);
        if (machine == BigMachine.CONTAINMENT && level != null) (level.isClientSide ? CLIENT_HULLS : HULLS).add(this);
        if (noMobs() && level != null && !level.isClientSide) NO_MOBS.add(this);
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        return machine.chunkBaked() && level != null && level.isClientSide
                ? com.sqc.nuclearsq.machine.big.client.ChunkMachines.modelData(this)
                : net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    }

    @Override
    public void setRemoved() {
        if (machine.chunkBaked() && level != null && level.isClientSide) com.sqc.nuclearsq.machine.big.client.ChunkMachines.forget(worldPosition);
        DRIVES.remove(this);
        BAYS.remove(this);
        CLIENT_DRIVES.remove(this);
        HULLS.remove(this);
        CLIENT_HULLS.remove(this);
        NO_MOBS.remove(this);
        if (!unloading && assembler != null && level != null) assembler.discard(level);
        if (!unloading && level != null && !level.isClientSide && formedRot >= 0) {
            for (BigMachine.Port p : machine.ports) {
                BlockPos pos = portPos(p);
                if (pos != null) level.invalidateCapabilities(pos);
            }
        }
        super.setRemoved();
    }

    private boolean unloading;

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        if (assembler != null && level != null && !level.isClientSide) assembler.unbind(level);
        DRIVES.remove(this);
        BAYS.remove(this);
        CLIENT_DRIVES.remove(this);
        HULLS.remove(this);
        CLIENT_HULLS.remove(this);
        NO_MOBS.remove(this);
        super.onChunkUnloaded();
    }

    private int rodHeads() {
        int h = 0;
        for (RodShaft s : shafts) h = h * 31 + s.headY();
        return h;
    }

    public void clientTick() {
        for (RodShaft s : shafts) s.clientTick(stroke());
        if (machine == BigMachine.PARTICLE_ASSEMBLER) {
            if (particleFx == null) particleFx = new ParticleAssemblerFx();
            particleFx.tick(this);
        }
    }

    @Nullable private ParticleAssemblerFx particleFx;

    @Nullable public ParticleAssemblerFx particleFx() { return particleFx; }

    private int containment() {
        passThrough("feedwater_in", "feedwater_out");
        passThrough("wet_steam_in", "wet_steam_out");
        passThrough("relief_in", "relief_out");
        passThrough("hpi_in", "hpi_out");
        passThrough("emc_in", "emc_out");
        passThrough("hyd_in", "hyd_out");
        passThrough("treatment_in", "treatment_out");
        powered = enabled && usePower();
        return powered ? 1 : 0;
    }

    private void plume(net.minecraft.server.level.ServerLevel sl, double load) {
        BlockPos top = structure().world(worldPosition, Math.max(formedRot, 0), machine.w / 2, machine.h - 1, machine.d / 2);
        boolean xl = machine == BigMachine.COOLING_TOWER_XL;
        int count = (int) Math.round((xl ? 2.0 : 1.0) * (0.6 + 1.4 * load));
        for (int i = Math.max(1, count); i > 0; i--) {
            double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * machine.w * 0.22;
            double rise = (xl ? 0.34 : 0.17) * (0.85 + 0.3 * sl.random.nextDouble());
            DriveCore.far(sl, com.sqc.nuclearsq.registry.ModParticles.COOLING_STEAM.get(),
                    top.getX() + 0.5 + Math.cos(a) * d, top.getY() + 1.0, top.getZ() + 0.5 + Math.sin(a) * d,
                    Math.cos(a) * 0.012 + 0.03, rise, Math.sin(a) * 0.012 + 0.012);
        }
    }

    private int passThrough(String from, String to) {
        FluidTank in = tanks.get(from), out = tanks.get(to);
        if (in == null || out == null || in.isEmpty()) return 0;
        FluidStack fs = in.getFluid().copy();
        int n = out.fill(fs, IFluidHandler.FluidAction.EXECUTE);
        if (n > 0) in.drain(n, IFluidHandler.FluidAction.EXECUTE);
        return n;
    }

    private void setSignal(String port, int value) {
        BigMachine.Port p = machine.port(port);
        BlockPos pos = p == null ? null : portPos(p);
        if (pos != null && level.getBlockEntity(pos) instanceof BigPortBlockEntity be) be.setSignal(value);
    }

    @Override
    public int acceptShaft(BlockPos coupling, int fe, float speed, boolean simulate) {
        if (machine != BigMachine.SHAFT_GENERATOR || !formed || !enabled || energy == null || fe <= 0) return 0;
        if (!structure().world(worldPosition, formedRot, machine.ax, machine.ay, machine.az - 1).equals(coupling)) return 0;
        int eff = Math.max(1, machine.extra("efficiency_pct"));
        int take = (int) Math.min(fe, (energy.getMaxEnergyStored() - energy.getEnergyStored()) * 100L / eff);
        if (take <= 0) return 0;
        if (!simulate) {
            energy.add(take * eff / 100);
            shaftIn += take;
            setChanged();
        }
        return take;
    }

    private void pushOutputs() {
        lastOut = 0;
        for (BigMachine.Port p : machine.ports) {
            if (p.kind() == BigPortKind.ENERGY && battery != null) {
                if (battery.isOutput(p)) pushBattery(p);
                continue;
            }
            if (p.kind() == BigPortKind.ENERGY && fusion != null) {
                if (!p.in()) pushEnergy(p, fusion.out());
                continue;
            }
            if (p.kind() == BigPortKind.ENERGY && !p.in() && energy != null && energy.getEnergyStored() > 0) {
                pushEnergy(p, energy);
                continue;
            }
            if (p.kind() == BigPortKind.ITEM && !p.in() && items != null) {
                pushItems(p);
                continue;
            }
            if (p.in() || !p.kind().isFluid()) continue;
            FluidTank t = tanks.get(machine == BigMachine.FLUID_BUFFER ? "fluid_in" : p.name());
            if (t == null || t.isEmpty()) continue;
            BlockPos pos = portPos(p);
            if (pos == null) continue;
            int left = machine == BigMachine.CONTAINMENT ? Integer.MAX_VALUE : p.rate();
            for (Direction dir : Direction.values()) {
                if (left <= 0 || t.isEmpty()) break;
                BlockPos n = pos.relative(dir);
                BlockEntity nb = level.getBlockEntity(n);
                if (nb instanceof BigPortBlockEntity || nb instanceof BigMachineBlockEntity) continue;
                IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, n, dir.getOpposite());
                if (h == null) continue;
                FluidStack out = t.drain(left, IFluidHandler.FluidAction.SIMULATE);
                int took = h.fill(out, IFluidHandler.FluidAction.EXECUTE);
                if (took > 0) {
                    t.drain(took, IFluidHandler.FluidAction.EXECUTE);
                    left -= took;
                }
            }
        }
    }

    private void pushItems(BigMachine.Port p) {
        if (ticks % 20 != 0) return;
        IItemHandler src = itemsAt(p.name());
        BlockPos pos = portPos(p);
        if (src == null || pos == null) return;
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (level.getBlockEntity(n) instanceof BigPortBlockEntity || level.getBlockEntity(n) instanceof BigMachineBlockEntity) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, n, dir.getOpposite());
            if (h == null) continue;
            for (int slot = 0; slot < src.getSlots(); slot++) {
                ItemStack st = src.extractItem(slot, 64, true);
                if (st.isEmpty()) continue;
                int moved = st.getCount() - net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, st, false).getCount();
                if (moved > 0) src.extractItem(slot, moved, false);
            }
        }
    }

    private void pushEnergy(BigMachine.Port p, MachineEnergy src) {
        BlockPos pos = portPos(p);
        if (pos == null) return;
        int left = machine == BigMachine.CONTAINMENT ? Integer.MAX_VALUE : p.rate();
        for (Direction dir : Direction.values()) {
            if (left <= 0 || src.getEnergyStored() <= 0) break;
            BlockPos n = pos.relative(dir);
            BlockEntity nb = level.getBlockEntity(n);
            if (nb instanceof BigMachineBlockEntity || nb instanceof BigPortBlockEntity pb && pb.findController() == this) continue;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
            if (target == null || target == src || !target.canReceive()) continue;
            int took = target.receiveEnergy(Math.min(left, src.getEnergyStored()), false);
            if (took > 0) {
                src.drain(took);
                left -= took;
                lastOut += took;
            }
        }
    }

    private void pushBattery(BigMachine.Port p) {
        BlockPos pos = portPos(p);
        if (pos == null) return;
        for (Direction dir : Direction.values()) {
            int left = battery.budget(p);
            if (left <= 0) break;
            BlockPos n = pos.relative(dir);
            BlockEntity nb = level.getBlockEntity(n);
            if (nb instanceof BigMachineBlockEntity || nb instanceof BigPortBlockEntity pb && pb.findController() == this) continue;
            if (nb instanceof com.sqc.nuclearsq.transport.ConduitBlockEntity c && (c.dist() >= com.sqc.nuclearsq.transport.ConduitBlockEntity.FAR || c.toBattery() && c.dist() >= com.sqc.nuclearsq.transport.ConduitBlockEntity.needFor(battery.store().getEnergyStored(), battery.store().getMaxEnergyStored()))) continue;
            if (nb instanceof com.sqc.nuclearsq.transport.ConduitBlockEntity c && c.toBattery()) left = Math.min(left, balance(c.target()));
            if (left <= 0) continue;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            battery.took(p, target.receiveEnergy(left, false));
        }
    }

    private int balance(@Nullable IEnergyStorage other) {
        if (other == null) return 0;
        long e1 = battery.store().getEnergyStored(), c1 = Math.max(1, battery.store().getMaxEnergyStored());
        long e2 = other.getEnergyStored(), c2 = Math.max(1, other.getMaxEnergyStored());
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (e1 * c2 - e2 * c1) / (c1 + c2) / 4));
    }

    public boolean enabled() { return enabled; }
    @Nullable public FusionCore fusion() { return fusion; }

    public void setBurn(int percent) {
        if (fusion == null) return;
        fusion.setBurn(percent);
        setChanged();
    }
    public boolean manual() { return manual; }

    public void setEnabled(boolean value) {
        enabled = value;
        setChanged();
    }

    public void setManual(boolean value) {
        manual = value;
        setChanged();
    }

    public void dumpTank(int portIndex) {
        if (portIndex < 0 || portIndex >= machine.ports.size()) return;
        FluidTank t = tanks.get(machine.ports.get(portIndex).name());
        if (t != null) t.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    public void openRack(net.minecraft.server.level.ServerPlayer sp) {
        if (rack == null) return;
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.sqc.nuclearsq.storage.ServerRackMenu(id, inv,
                worldPosition, rack.drives(), com.sqc.nuclearsq.storage.ServerRackMenu.data(this), rack.isPatternRack()),
                Component.translatable("gui.nuclearsq.storage.rack." + machine.id)), buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(rack.slots());
            buf.writeBoolean(rack.isPatternRack());
        });
    }

    public void openAssembler(net.minecraft.server.level.ServerPlayer sp) {
        if (assembler == null || level == null) return;
        assembler.updatePreview(level);
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.sqc.nuclearsq.storage.ComponentAssemblerMenu(id, inv,
                worldPosition, assembler, com.sqc.nuclearsq.storage.ComponentAssemblerMenu.data(this)),
                Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> buf.writeBlockPos(worldPosition));
    }

    public void setRecipeLock(int index) {
        if (crafter == null || level == null) return;
        crafter.setLock(level, index);
        setChanged();
    }

    public int fillRecipe(net.minecraft.world.entity.player.Player p, boolean all) {
        if (crafter == null || items == null || level == null || crafter.lockIndex() <= 0) return 0;
        var rm = level.getRecipeManager();
        var ids = com.sqc.nuclearsq.progression.MachineRecipes.ids(machine, rm);
        if (crafter.lockIndex() > ids.size()) return 0;
        var r = rm.byKey(ids.get(crafter.lockIndex() - 1)).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
        if (r == null) return 0;
        int moved = com.sqc.nuclearsq.progression.MachineRecipes.fill(items,
                com.sqc.nuclearsq.progression.MachineRecipes.needs(r, items.getSlots(), level.registryAccess()), p.getInventory().items, all);
        if (moved > 0) p.getInventory().setChanged();
        return moved;
    }

    public void openCrafter(net.minecraft.server.level.ServerPlayer sp) {
        if (crafter == null || items == null && machine != BigMachine.SPENT_FUEL_RACK) return;
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new BigCrafterMenu(id, inv, worldPosition, machine,
                items, BigCrafterMenu.data(this)), Component.translatable(getBlockState().getBlock().getDescriptionId())), buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeVarInt(machine.ordinal());
        });
    }

    public void openMachineTerminal(net.minecraft.server.level.ServerPlayer sp) {
        if (battery != null) {
            DeviceMenu.open(sp, worldPosition, DeviceMenu.BIG_BATTERY, battery.menuData(machine),
                    Component.translatable(getBlockState().getBlock().getDescriptionId()));
            return;
        }
        DeviceMenu.open(sp, worldPosition, DeviceMenu.BIG, machineData(), Component.translatable(getBlockState().getBlock().getDescriptionId()));
    }

    private ContainerData machineData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                if (substation != null && i >= FUSION_DATA + 6 && i < FUSION_DATA + 8) {
                    return i == FUSION_DATA + 6 ? substation.feedPermille() : substation.supplyPermille();
                }
                if (substation != null && i >= FUSION_DATA && i < FUSION_DATA + 6) {
                    int v = i < FUSION_DATA + 2 ? substation.inRate() : i < FUSION_DATA + 4 ? substation.outRate() : substation.lineInRate();
                    return (i - FUSION_DATA) % 2 == 0 ? DeviceMenu.lo(v) : DeviceMenu.hi(v);
                }
                if (fusion != null && i >= FUSION_EXTRA) {
                    return switch (i - FUSION_EXTRA) {
                        case 0 -> fusion.chargePermille();
                        case 1 -> DeviceMenu.lo(fusion.lastDraw());
                        case 2 -> DeviceMenu.hi(fusion.lastDraw());
                        case 3 -> fusion.burnLeft();
                        default -> (int) Math.min(0xFFFF, fusion.ignitionCost() / 1_000_000);
                    };
                }
                if (i >= TERM_PORTS) {
                    int k = i - TERM_PORTS, pi = k / PORT_VALUES;
                    if (machine == BigMachine.FUSION_REACTOR) pi = pi < FUSION_PORTS.length ? FUSION_PORTS[pi] : Integer.MAX_VALUE;
                    if (pi >= machine.ports.size()) return 0;
                    FluidTank t = tanks.get(machine.ports.get(pi).name());
                    if (t == null) return 0;
                    return switch (k % PORT_VALUES) {
                        case 0 -> DeviceMenu.lo(t.getFluidAmount());
                        case 1 -> DeviceMenu.hi(t.getFluidAmount());
                        case 2 -> DeviceMenu.lo(t.getCapacity());
                        case 3 -> DeviceMenu.hi(t.getCapacity());
                        default -> t.isEmpty() ? 0 : net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(t.getFluid().getFluid());
                    };
                }
                return switch (i) {
                    case 0 -> formed ? 1 : 0;
                    case 1 -> running ? 1 : 0;
                    case 2 -> enabled ? 1 : 0;
                    case 3 -> java.util.Arrays.asList(STATES).indexOf(level == null ? "idle" : stateKey());
                    case 4 -> machine == BigMachine.PRESSURIZER ? Math.round(pressure) : fusion != null ? fusion.temperature() : lastMb;
                    case 5 -> isGenerator() ? lastOut : energy == null ? 0 : Math.round(100f * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored()));
                    case 6 -> powered ? 1 : 0;
                    case 7 -> signalIn;
                    case 8 -> signalOut;
                    case 9 -> hasWear() ? (tubeLeak() ? 1 : 0) : fusion != null ? fusion.burnSetting() : manual ? 1 : 0;
                    case 10 -> matched;
                    case 11 -> total;
                    case 12 -> machine.ordinal();
                    case 13 -> {
                        int used = 0;
                        if (items != null) for (int s = 0; s < items.getSlots(); s++) if (!items.getStackInSlot(s).isEmpty()) used++;
                        yield used;
                    }
                    case 14 -> hasWear() ? wearPercent() : fusion != null ? fusion.wearPercent() : level != null && triggered() ? 1 : 0;
                    case FUSION_DATA -> fusion == null ? 0 : DeviceMenu.lo(fusion.lastNet());
                    case FUSION_DATA + 1 -> fusion == null ? 0 : DeviceMenu.hi(fusion.lastNet());
                    case FUSION_DATA + 2 -> fusion == null ? 0 : Math.min(100, 100 * fusion.wallHeat() / Math.max(1, machine.extra("wall_heat_max")));
                    case FUSION_DATA + 3 -> fusion == null ? 0 : fusion.ignitionPercent();
                    case FUSION_DATA + 4 -> fusion == null ? 0 : fusion.burnNow();
                    case FUSION_DATA + 5 -> fusion != null && fusion.ddMode() && fusion.burning() ? 1 : 0;
                    case FUSION_DATA + 6 -> fusion == null ? 0 : fusion.phase();
                    case FUSION_DATA + 7 -> machine == BigMachine.SOLAR_FIELD && energy != null ? Math.round(1000f * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored())) : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) { }

            @Override
            public int getCount() { return DeviceMenu.count(DeviceMenu.BIG); }
        };
    }

    public void sendStatus(Player player) {
        String k = "message.nuclearsq.big.";
        player.sendSystemMessage(Component.translatable(k + "title", Component.translatable(getBlockState().getBlock().getDescriptionId()),
                matched, total));
        if (!formed) {
            player.sendSystemMessage(Component.translatable(k + "incomplete"));
            return;
        }
        player.sendSystemMessage(Component.translatable(k + "state." + stateKey()));
        if (machine == BigMachine.SPENT_FUEL_RACK) player.sendSystemMessage(rackLine());
        if (substation != null) {
            player.sendSystemMessage(Component.translatable(k + "substation", substation.targets().size(), substation.networkNodes(),
                    substation.inRate(), substation.lineRateNow(), substation.outRate()));
            player.sendSystemMessage(Component.translatable(k + "substation_buffers", substation.feed(), substation.supply(), substation.lineRate()));
        }
        for (BigMachine.Port p : machine.ports) player.sendSystemMessage(portLine(p));
    }

    private void windTick() {
        if (level == null) return;
        BlockPos hub = structure().world(worldPosition, Math.max(formedRot, 0), machine.extra("hub_x"), machine.extra("hub_y"), machine.extra("hub_z"));
        if (!com.sqc.nuclearsq.power.WindMath.hasWind(level) || com.sqc.nuclearsq.power.WindMath.covered(level, structure().world(worldPosition, Math.max(formedRot, 0), machine.extra("hub_x"), machine.h - 1, machine.extra("hub_z")))) {
            windRate = 0;
            windHeightPct = windSurroundPct = windGustPct = 0;
            return;
        }
        if (ticks % 100 == 0 || windSurroundPct == 0) {
            windOpen = com.sqc.nuclearsq.power.WindMath.openness(level, hub, machine.extra("ray_range"), machine.extra("ray_skip"));
        }
        float height = com.sqc.nuclearsq.power.WindMath.height(hub.getY());
        float surround = com.sqc.nuclearsq.power.WindMath.surround(windOpen);
        float wind = com.sqc.nuclearsq.power.WindMath.wind(level, level.getGameTime());
        windHeightPct = Math.round(height * 100);
        windSurroundPct = Math.round(surround * 100);
        windGustPct = Math.round(wind * 100);
        windRate = com.sqc.nuclearsq.power.WindMath.rate(machine.extra("fe_per_tick_peak"), height, surround, wind);
    }

    public String stateKey() {
        if (!enabled && machine != BigMachine.CONTROL_ROD_DRIVE) return "off";
        if (fusion != null) {
            String r = fusion.state();
            return !r.isEmpty() ? r : fusion.phase() == FusionCore.HEATING ? "heating" : fusion.burning() ? "plasma" : "idle";
        }
        if (energy != null && !powered && !(machine.isEmergency() && !emergencyActive())) return "no_power";
        if (machine.isEmergency() && !emergencyActive()) return "standby";
        if (residueFull()) return "residue_full";
        if (breeder != null) {
            if (breeder.leak()) return "sodium_leak";
            if (breeder.noSodium()) return "no_sodium";
            if (breeder.noDriver() && !running) return "no_driver";
        }
        if (machine == BigMachine.STEAM_GENERATOR && tubeLeak) return "tube_leak";
        if (machine == BigMachine.STEAM_GENERATOR && tank("primary_hot_in").isEmpty() && !running) return "no_primary";
        if (machine == BigMachine.PRESSURIZER && venting) return "venting";
        if ((machine.isPump() || machine == BigMachine.PRESSURIZER) && noReactor) return "no_reactor";
        if (machine == BigMachine.PRESSURIZER && pressure < PRESSURE_LOW) return "low_pressure";
        if (machine == BigMachine.SHAFT_GENERATOR && !running) return "no_shaft";
        if (machine == BigMachine.SOLAR_FIELD && !running) return "no_sun";
        if (machine.isWind() && !running) return "no_wind";
        return running ? "running" : "idle";
    }

    public Component portLine(BigMachine.Port p) {
        String k = "message.nuclearsq.big.";
        Component name = Component.translatable("port.nuclearsq." + machine.id + "." + p.name());
        FluidTank t = tanks.get(p.name());
        if (t != null) {
            Component fluid = t.isEmpty() ? Component.translatable(k + "empty") : t.getFluid().getHoverName();
            Component line = Component.translatable(k + "fluid", name, t.getFluidAmount(), t.getCapacity(), fluid, p.rate());
            return machine == BigMachine.INTAKE_PUMP && formed && !intakeWet ? line.copy().append(Component.translatable(k + "dry")) : line;
        }
        return switch (p.kind()) {
            case ENERGY -> energy == null ? name : machine == BigMachine.CONTAINMENT ? Component.translatable(k + "energy_pass", name, energy.getEnergyStored(), energy.getMaxEnergyStored(), lastOut) : isGenerator() ? Component.translatable(k + "energy_gen", name, lastOut, generatorPeak())
                    : Component.translatable(k + "energy", name, energy.getEnergyStored(),
                    energy.getMaxEnergyStored(), machine.energy.use());
            case SIGNAL -> Component.translatable(k + "signal", name, p.in() ? signalIn : signalOut,
                    machine == BigMachine.CONTROL_ROD_DRIVE ? Component.translatable(k + "rods", rod, ROD_MAX) : Component.empty());
            case ITEM -> {
                int used = 0;
                if (items != null) for (int i = 0; i < items.getSlots(); i++) if (!items.getStackInSlot(i).isEmpty()) used++;
                yield Component.translatable(k + "items", name, used, p.slots());
            }
            default -> name;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (exo != null) exo.save(tag, registries);
        tag.putInt("hiddenRot", hiddenRot);
        tag.putInt("rod", rod);
        tag.putInt("rodTarget", target);
        tag.putBoolean("terminalMode", terminalMode);
        tag.putInt("channel", channel);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("manual", manual);
        if (machine == BigMachine.STEAM_GENERATOR) {
            tag.putBoolean("tube_leak", tubeLeak);
            tag.putInt("wear", wearSec);
        }
        if (breeder != null) {
            CompoundTag b = new CompoundTag();
            breeder.save(b);
            tag.put("breeder", b);
        }
        if (fusion != null) fusion.save(tag);
        if (sepAcc > 0) tag.putInt("sepAcc", sepAcc);
        if (machine == BigMachine.PRESSURIZER) {
            tag.putFloat("pressure", pressure);
            tag.putBoolean("venting", venting);
        }
        for (int i = 0; i < shafts.length; i++) tag.put("shaft" + i, shafts[i].save());
        if (machine == BigMachine.SPENT_FUEL_RACK) for (int i = 0; i < released.length; i++) if (released[i]) tag.putBoolean("released" + i, true);
        CompoundTag tk = new CompoundTag();
        tanks.forEach((n, t) -> tk.put(n, t.writeToNBT(registries, new CompoundTag())));
        tag.put("tanks", tk);
        if (energy != null) tag.putInt("fe", energy.getEnergyStored());
        if (items != null && assembler == null) tag.put("items", items.serializeNBT(registries));
        if (crafter != null) tag.put("crafter", crafter.save(registries));
        if (desk != null) tag.put("desk", desk.save(registries));
        if (battery != null) tag.put("battery", battery.save());
        if (rack != null) tag.put("rack", rack.save(registries));
        if (cluster != null) tag.put("cluster", cluster.save(registries));
        if (owner != null) tag.putUUID("owner", owner);
        if (!access.grants().isEmpty()) tag.put("access", access.save());
        if (assembler != null) tag.put("assembler", assembler.save(registries));
        if (disposed > 0) tag.putLong("disposed", disposed);
        if (residueAcc > 0) tag.putInt("residueAcc", residueAcc);
        tag.putInt("cherenkov", cherenkov);
        if (machine == BigMachine.CONTAINMENT) {
            tag.putBoolean("doorOpen", doorOpen);
            tag.putBoolean("doorPowered", doorPowered);
        }
        if (core != null) core.save(tag);
        if (substation != null) {
            CompoundTag st = new CompoundTag();
            substation.save(st);
            tag.put("substation", st);
        }
        if (machine == BigMachine.EMERGENCY_COOLING) tag.putInt("boron_stock", boronStock);
        if (machine == BigMachine.CONTROL_ROD_DRIVE) {
            tag.putInt("moderator", moderator);
            tag.putInt("cooler", cooler);
        }
        if (machine == BigMachine.CONTROL_ROD_DRIVE || machine == BigMachine.STEAM_GENERATOR) {
            tag.putFloat("vesselTemp", vesselTemp);
            tag.putFloat("vesselAcc", vesselAcc);
            tag.putFloat("preheatAcc", preheatAcc);
            for (int i = 0; i < vesselBurn.length; i++) tag.putFloat("vesselBurn" + i, vesselBurn[i]);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("geo")) {
            CompoundTag g = tag.getCompound("geo");
            boolean wasFormed = formed;
            int wasRot = formedRot;
            formed = g.getBoolean("formed");
            formedRot = g.getInt("rot");
            if (machine.chunkBaked() && level != null && level.isClientSide && (wasFormed != formed || wasRot != formedRot)) {
                com.sqc.nuclearsq.machine.big.client.ChunkMachines.forget(worldPosition);
            }
            running = g.getBoolean("running");
            blocked = g.getBoolean("blocked");
            rod = g.getInt("rod");
            rodFault = g.getBoolean("rodFault");
            vesselDry = g.getBoolean("vesselDry");
            cherenkov = g.getInt("cherenkov");
            doorOpen = g.getBoolean("door");
            for (int i = 0; i < shafts.length; i++) shafts[i].readSync(g.getCompound("shaft" + i));
            if (desk != null) desk.load(g.getCompound("desk"), registries);
            charge = g.getInt("charge");
            if (rack != null) rack.readCodes(g.getIntArray("rack"));
            if (crafter != null && g.contains("particle")) crafter.readParticleSync(g.getCompound("particle"), registries);
            if (substation != null) substation.loadLinks(g.getCompound("grid"));
            if (exo != null) exo.readPieceSync(g.get("exo"), registries);
            return;
        }
        if (exo != null) exo.load(tag, registries);
        hiddenRot = tag.contains("hiddenRot") ? tag.getInt("hiddenRot") : -1;
        reassert = hiddenRot >= 0;
        doorOpen = tag.getBoolean("doorOpen");
        doorPowered = tag.getBoolean("doorPowered");
        rod = tag.getInt("rod");
        target = tag.getInt("rodTarget");
        terminalMode = tag.getBoolean("terminalMode");
        channel = tag.getInt("channel");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        manual = tag.getBoolean("manual");
        if (fusion != null) fusion.load(tag);
        sepAcc = tag.getInt("sepAcc");
        pressure = tag.getFloat("pressure");
        venting = tag.getBoolean("venting");
        tubeLeak = tag.getBoolean("tube_leak");
        wearSec = tag.getInt("wear");
        if (breeder != null) breeder.load(tag.getCompound("breeder"));
        for (int i = 0; i < shafts.length; i++) shafts[i].load(tag.getCompound("shaft" + i));
        for (int i = 0; i < released.length; i++) released[i] = tag.getBoolean("released" + i);
        CompoundTag tk = tag.getCompound("tanks");
        tanks.forEach((n, t) -> {
            t.readFromNBT(registries, tk.getCompound(n));
            if (t.getFluidAmount() > t.getCapacity()) t.getFluid().setAmount(t.getCapacity());
        });
        if (energy != null) energy.setStored(tag.getInt("fe"));
        if (items != null && tag.contains("items")) {
            int size = items.getSlots();
            items.deserializeNBT(registries, tag.getCompound("items"));
            if (items.getSlots() < size) {
                java.util.List<net.minecraft.world.item.ItemStack> old = new java.util.ArrayList<>();
                for (int i = 0; i < items.getSlots(); i++) old.add(items.getStackInSlot(i));
                items.setSize(size);
                for (int i = 0; i < old.size(); i++) items.setStackInSlot(i, old.get(i));
            }
        }
        if (crafter != null) crafter.load(tag.getCompound("crafter"), registries);
        if (desk != null) desk.load(tag.getCompound("desk"), registries);
        if (battery != null) battery.load(tag.getCompound("battery"));
        if (rack != null) rack.load(tag.getCompound("rack"), registries);
        if (cluster != null) cluster.load(tag.getCompound("cluster"), registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        access.load(tag.getCompound("access"));
        if (assembler != null) assembler.load(tag.getCompound("assembler"), registries);
        disposed = tag.getLong("disposed");
        residueAcc = tag.getInt("residueAcc");
        cherenkov = tag.contains("cherenkov") ? net.minecraft.util.Mth.clamp(tag.getInt("cherenkov"), 0, com.sqc.nuclearsq.reactor.Cherenkov.MAX)
                : com.sqc.nuclearsq.reactor.Cherenkov.DEFAULT;
        if (core != null) core.load(tag);
        if (substation != null) substation.load(tag.getCompound("substation"));
        boronStock = Mth.clamp(tag.getInt("boron_stock"), 0, BORON_STOCK_MAX);
        moderator = Mth.clamp(tag.getInt("moderator"), 0, PartStages.MODERATORS.size());
        cooler = Mth.clamp(tag.getInt("cooler"), 0, PartStages.COOLERS.size());
        if (tag.contains("vesselTemp")) vesselTemp = tag.getFloat("vesselTemp");
        vesselAcc = tag.getFloat("vesselAcc");
        preheatAcc = tag.getFloat("preheatAcc");
        for (int i = 0; i < vesselBurn.length; i++) vesselBurn[i] = tag.getFloat("vesselBurn" + i);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(), g = new CompoundTag();
        g.putBoolean("formed", formed);
        g.putInt("rot", formedRot);
        g.putBoolean("running", running);
        g.putBoolean("blocked", blocked);
        g.putInt("rod", rod);
        g.putBoolean("rodFault", rodFault);
        g.putBoolean("vesselDry", vesselDry);
        g.putInt("cherenkov", cherenkov);
        g.putBoolean("door", doorOpen);
        for (int i = 0; i < shafts.length; i++) g.put("shaft" + i, shafts[i].syncTag());
        if (desk != null) g.put("desk", desk.save(registries));
        if (battery != null) g.putInt("charge", battery.level());
        if (rack != null) g.putIntArray("rack", rack.codes());
        if (machine == BigMachine.PARTICLE_ASSEMBLER && crafter != null) g.put("particle", crafter.particleSync(registries));
        if (substation != null) {
            CompoundTag grid = new CompoundTag();
            substation.saveLinks(grid);
            g.put("grid", grid);
        }
        if (exo != null) g.put("exo", exo.pieceSync(registries));
        tag.put("geo", g);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 10,
                state -> state.setAndContinue(running ? RUNNING : blocked ? BLOCKED : IDLE)));
        if (machine == BigMachine.CONTAINMENT) {
            controllers.add(new AnimationController<>(this, "door", 0, state -> {
                boolean open = doorOpen;
                if (doorShown == null) state.getController().setAnimation(open ? DOOR_OPENED : DOOR_CLOSED);
                else if (doorShown != open) state.getController().setAnimation(open ? DOOR_OPENING : DOOR_CLOSING);
                doorShown = open;
                return software.bernie.geckolib.animation.PlayState.CONTINUE;
            }));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
