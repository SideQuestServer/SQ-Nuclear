package com.sqc.nuclearsq.turbine;

import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.PartStages;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.MultiblockRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

public class TurbineControllerBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity, com.sqc.nuclearsq.machine.MonitorTerminal {
    public static final int TANK = 640000, MAX_MB = 400, FE_PER_MB = 256, SUPER_FE_PER_MB = 384, CAP = 128000000, OUT = 160000;
    public static final int MAX_RPM = 3000, DATA_COUNT = 18;
    public static final int COUPLING_X = 9, COUPLING_Y = 4, COUPLING_Z = 42;
    public static final int DATA_X = 7, DATA_Y = 1, DATA_Z = 0;
    public static final int WATER_TANK = 160000, WATER_PER_MB = 1, WATER_OUT = 1200;
    public static final double TURBINE_SV_H = com.sqc.nuclearsq.radiation.RadiationValues.TURBINE_SV_H;
    public static final int SPIN_STEPS = 10;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("running");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final class Gen extends EnergyStorage {
        Gen() { super(CAP, 0, OUT); }
        void add(int n) { energy = Math.min(capacity, energy + n); }
        void set(int n) { energy = Math.max(0, Math.min(capacity, n)); }
    }

    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> TURBINE_STEAM =
            net.minecraft.tags.FluidTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "turbine_steam"));

    private final FluidTank steam = new FluidTank(TANK, fs -> fs.is(TURBINE_STEAM)) {
        @Override
        protected void onContentsChanged() { setChanged(); }
    };
    private final Gen energy = new Gen();
    private final FluidTank water = new FluidTank(WATER_TANK, fs -> fs.is(ModFluids.EXHAUST_STEAM.source.get())) {
        @Override
        protected void onContentsChanged() { setChanged(); }
    };
    private final IFluidHandler condensateOut = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return water.getFluid(); }
        @Override public int getTankCapacity(int tank) { return WATER_TANK; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return water.drain(resource, action); }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return water.drain(maxDrain, action); }
    };
    private boolean formed, running = true, legacy;
    private int formedRot = -1;
    private int throttle = 100;
    private int matched, total, ticks, lastFe, lastMb, outputNow;
    private boolean coupled;
    private int lastShaft;
    private float rpm, temp = 25f;
    private int hiddenRot = -1;
    private int spin;
    private double animTime, lastRealTick = -1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> formed ? 1 : 0;
                case 1 -> running ? 1 : 0;
                case 2 -> throttle;
                case 3 -> Math.round(rpm);
                case 4 -> steam.getFluidAmount() & 0xFFFF;
                case 5 -> steam.getFluidAmount() >>> 16;
                case 6 -> energy.getEnergyStored() & 0xFFFF;
                case 7 -> energy.getEnergyStored() >>> 16;
                case 8 -> lastFe & 0xFFFF;
                case 9 -> lastMb;
                case 10 -> Math.round(temp);
                case 11 -> matched;
                case 12 -> total;
                case 13 -> outputNow & 0xFFFF;
                case 14 -> lastShaft & 0xFFFF;
                case 15 -> lastFe >>> 16;
                case 16 -> outputNow >>> 16;
                case 17 -> lastShaft >>> 16;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public TurbineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(MultiblockRegistry.TURBINE_BE.get(), pos, state);
    }

    public FluidTank steam() { return steam; }

    public int fePerMb() {
        return steam.getFluid().is(ModFluids.SUPERHEATED_STEAM.source.get()) ? SUPER_FE_PER_MB : FE_PER_MB;
    }
    private int translator;

    public int translator() { return translator; }

    public void setTranslator(int stage) {
        translator = Mth.clamp(stage, 0, PartStages.TRANSLATORS.size());
        setChanged();
    }

    public int maxMb() { return MAX_MB * PartStages.translatorFlowPct(translator) / 100; }

    public int fePerMbPct() { return fePerMb() * PartStages.translatorFePct(translator); }

    public int maxFe() { return maxMb() * fePerMbPct() / 100; }

    public boolean installPart(Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        int t = PartStages.translatorStage(held);
        if (t == 0) return false;
        net.minecraft.world.item.ItemStack back = PartStages.stack(PartStages.TRANSLATORS, translator);
        translator = t;
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
        }
        setChanged();
        player.displayClientMessage(Component.translatable("message.nuclearsq.part_translator", Component.translatable(held.getDescriptionId()),
                PartStages.translatorFePct(t), PartStages.translatorFlowPct(t)), true);
        return true;
    }

    public boolean removePart(Player player) {
        if (translator == 0) return false;
        net.minecraft.world.item.ItemStack back = PartStages.stack(PartStages.TRANSLATORS, translator);
        translator = 0;
        player.getInventory().placeItemBackInInventory(back);
        setChanged();
        player.displayClientMessage(Component.translatable("message.nuclearsq.part_removed", Component.translatable(back.getDescriptionId())), true);
        return true;
    }

    public void dropPart() {
        if (translator > 0 && level != null && !level.isClientSide) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    PartStages.stack(PartStages.TRANSLATORS, translator));
            translator = 0;
        }
    }

    public IEnergyStorage energy() { return energy; }
    public IFluidHandler condensateOut() { return condensateOut; }
    public boolean isFormed() { return formed; }
    public int matched() { return matched; }
    public int total() { return total; }
    public int lastFe() { return lastFe; }
    public int lastMb() { return lastMb; }
    public int lastShaft() { return lastShaft; }
    public boolean isCoupled() { return coupled; }

    private BlockPos shaftPos(int dz) {
        return MultiblockStructure.TURBINE.world(worldPosition, formedRot, COUPLING_X, COUPLING_Y, COUPLING_Z + dz);
    }

    @javax.annotation.Nullable
    private ShaftConsumer shaftConsumer() {
        if (!formed || formedRot < 0) return null;
        return level.getBlockEntity(shaftPos(coupled ? 1 : 0)) instanceof ShaftConsumer s && (coupled || s.direct()) ? s : null;
    }
    public int energyStored() { return energy.getEnergyStored(); }
    public int formedRot() { return formed ? formedRot : -1; }
    public int spin() { return spin; }
    public int rpm() { return Math.round(rpm); }
    public boolean running() { return running; }
    public int throttle() { return throttle; }

    @Override
    public double[] monitorBox() { return TurbineShapes.MONITOR; }

    @Override
    public int monitorRot() { return formedRot(); }

    @Override
    public void openMonitor(net.minecraft.server.level.ServerPlayer player) { player.openMenu(this, worldPosition); }

    private void setPartsHidden(int rot, boolean hidden) {
        if (rot < 0 || level == null) return;
        MultiblockStructure s = MultiblockStructure.TURBINE;
        for (MultiblockStructure.Cell c : s.cells(worldPosition, rot)) {
            BlockState st = level.getBlockState(c.pos());
            if (c.ok(st) && st.hasProperty(ReactorProps.FORMED) && st.getValue(ReactorProps.FORMED) != hidden) {
                level.setBlock(c.pos(), st.setValue(ReactorProps.FORMED, hidden), 3);
            }
        }
        BlockState shell = MultiblockRegistry.TURBINE_SHELL.get().defaultBlockState();
        for (int[] cell : TurbineShapes.CELLS) {
            if (s.charAt(cell[0], cell[1], cell[2]) != ' ') continue;
            BlockPos p = s.world(worldPosition, rot, cell[0], cell[1], cell[2]);
            BlockState st = level.getBlockState(p);
            if (hidden && st.isAir()) {
                level.setBlock(p, shell.setValue(TurbineShellBlock.SHAPE, cell[3]).setValue(TurbineShellBlock.ROT, rot), 3);
            } else if (!hidden && st.is(MultiblockRegistry.TURBINE_SHELL.get())) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public void unhideParts() {
        setPartsHidden(hiddenRot, false);
        hiddenRot = -1;
    }

    public void setControl(boolean run, int thr) {
        running = run;
        throttle = Mth.clamp(thr, 0, 100);
        setChanged();
    }

    void forgetLayout() {
        hiddenRot = -1;
        formedRot = -1;
        formed = false;
    }

    public void serverTick() {
        if (level == null) return;
        if (legacy && level instanceof net.minecraft.server.level.ServerLevel sl) {
            legacy = false;
            TurbineRefund.refund(sl, worldPosition, this, hiddenRot);
            return;
        }
        boolean wasFormed = formed;
        int wasRot = formedRot, wasSpin = spin;
        if (ticks++ % 20 == 0) {
            MultiblockStructure s = MultiblockStructure.TURBINE;
            int rot = formed && formedRot >= 0 ? formedRot : s.bestRot(level, worldPosition);
            matched = s.matched(level, worldPosition, rot);
            if (formed && matched != total) {
                rot = s.bestRot(level, worldPosition);
                matched = s.matched(level, worldPosition, rot);
            }
            if (formedRot >= 0 && rot != formedRot) linkPorts(null);
            formedRot = rot;
            total = s.size;
            formed = matched == total;
            linkPorts(formed ? worldPosition : null);
            if (formed && !wasFormed) for (TurbinePortType t : TurbinePortType.values()) {
                BlockPos p = portPos(t);
                if (p == null) continue;
                level.invalidateCapabilities(p);
                level.updateNeighborsAt(p, level.getBlockState(p).getBlock());
            }
            coupled = formed && level.getBlockState(shaftPos(0)).is(MultiblockRegistry.SHAFT_COUPLING.get());
            int want = formed ? formedRot : -1;
            if (want != hiddenRot) {
                setPartsHidden(hiddenRot, false);
                setPartsHidden(want, true);
                hiddenRot = want;
                setChanged();
            }
        }
        lastFe = 0;
        lastMb = 0;
        lastShaft = 0;
        ShaftConsumer shaft = formed && running ? shaftConsumer() : null;
        BlockPos coupling = shaft == null ? null : shaftPos(coupled ? 0 : -1);
        float speed = rpm / MAX_RPM;
        int ratio = fePerMbPct();
        int demand = shaft == null ? 0 : shaft.acceptShaft(coupling, maxMb() * ratio / 100, speed, true);
        int room = CAP - energy.getEnergyStored();
        if (formed && running && steam.getFluidAmount() > 0 && (room > 0 || demand > 0)) {
            int limit = maxMb() * throttle / 100;
            int mb = Math.min(Math.min(limit, steam.getFluidAmount()), (int) ((room + demand) * 100L / ratio));
            if (mb > 0) {
                FluidStack drained = steam.drain(mb, IFluidHandler.FluidAction.EXECUTE);
                int fe = mb * ratio / 100;
                lastShaft = shaft == null ? 0 : shaft.acceptShaft(coupling, Math.min(fe, demand), speed, false);
                int lost = Math.max(0, fe - lastShaft - room);
                int back = Math.min(mb, (int) (lost * 100L / ratio));
                if (back > 0) {
                    steam.fill(drained.copyWithAmount(back), IFluidHandler.FluidAction.EXECUTE);
                    mb -= back;
                    fe = mb * ratio / 100;
                }
                energy.add(fe - lastShaft);
                water.fill(new FluidStack(ModFluids.EXHAUST_STEAM.source.get(), mb * WATER_PER_MB), IFluidHandler.FluidAction.EXECUTE);
                lastFe = fe;
                lastMb = mb;
                setChanged();
            }
        }
        if (ticks % 20 == 0 && lastMb > 0 && level instanceof net.minecraft.server.level.ServerLevel sl) {
            com.sqc.nuclearsq.radiation.RadiationManager.emit(sl, worldPosition, TURBINE_SV_H * lastMb / maxMb());
        }
        float load = lastMb / (float) maxMb();
        rpm += ((lastMb > 0 ? 600 + (MAX_RPM - 600) * load : 0) - rpm) * 0.05f;
        temp += ((lastMb > 0 ? 70 + 230 * load : 25) - temp) * 0.02f;
        spin = formed ? Math.round(rpm / MAX_RPM * SPIN_STEPS) : 0;
        if (formed != wasFormed || formedRot != wasRot || spin != wasSpin) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        outputNow = 0;
        if (energy.getEnergyStored() > 0) outputNow = push();
        if (formed && water.getFluidAmount() > 0) pushWater();
        if (ticks % 20 == 0) setChanged();
    }

    private BlockPos portPos(TurbinePortType t) {
        if (formedRot < 0) return null;
        BlockPos p = MultiblockStructure.TURBINE.world(worldPosition, formedRot, t.x, t.y, t.z);
        return level.getBlockState(p).is(MultiblockRegistry.TURBINE_PORTS.get(t).get()) ? p : null;
    }

    void unlinkPorts() {
        if (level != null && !level.isClientSide && formedRot >= 0) linkPorts(null);
    }

    private void linkPorts(BlockPos controller) {
        for (TurbinePortType t : TurbinePortType.values()) {
            BlockPos p = portPos(t);
            if (p != null && level.getBlockEntity(p) instanceof TurbinePortBlockEntity be) be.setController(controller);
        }
        if (formedRot >= 0 && level.getBlockEntity(MultiblockStructure.TURBINE.world(worldPosition, formedRot, DATA_X, DATA_Y, DATA_Z))
                instanceof com.sqc.nuclearsq.machine.big.BigPortBlockEntity d) d.link(controller, "data");
    }

    private int push() {
        int sent = pushFrom(worldPosition);
        if (formed) {
            BlockPos p = portPos(TurbinePortType.ENERGY);
            if (p != null) sent += pushFrom(p);
        }
        return sent;
    }

    private int pushFrom(BlockPos from) {
        int sent = 0;
        for (Direction d : Direction.values()) {
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, from.relative(d), d.getOpposite());
            if (target == null || target == energy || !target.canReceive()) continue;
            int offer = energy.extractEnergy(OUT, true);
            if (offer <= 0) break;
            int taken = target.receiveEnergy(offer, false);
            if (taken > 0) {
                energy.extractEnergy(taken, false);
                sent += taken;
            }
        }
        return sent;
    }

    private void pushWater() {
        BlockPos p = portPos(TurbinePortType.WATER);
        if (p == null) return;
        for (Direction d : Direction.values()) {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, p.relative(d), d.getOpposite());
            if (h == null || h == condensateOut || h == steam) continue;
            FluidStack out = water.drain(Math.min(water.getFluidAmount(), WATER_OUT), IFluidHandler.FluidAction.SIMULATE);
            if (out.isEmpty()) break;
            int took = h.fill(out, IFluidHandler.FluidAction.EXECUTE);
            if (took > 0) water.drain(took, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nuclearsq.turbine_controller_sqc");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TurbineMenu(id, inv, worldPosition, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("fe", energy.getEnergyStored());
        tag.putBoolean("running", running);
        tag.putInt("throttle", throttle);
        tag.putInt("translator", translator);
        tag.putInt("hiddenRot", hiddenRot);
        tag.putInt("model", 2);
        steam.writeToNBT(registries, tag);
        tag.put("water", water.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("geo")) {
            CompoundTag g = tag.getCompound("geo");
            formed = g.getBoolean("formed");
            formedRot = g.getInt("rot");
            spin = g.getInt("spin");
            return;
        }
        hiddenRot = tag.contains("hiddenRot") ? tag.getInt("hiddenRot") : -1;
        legacy = tag.getInt("model") < 2;
        energy.set(tag.getInt("fe"));
        running = !tag.contains("running") || tag.getBoolean("running");
        throttle = tag.contains("throttle") ? tag.getInt("throttle") : 100;
        translator = Mth.clamp(tag.getInt("translator"), 0, PartStages.TRANSLATORS.size());
        steam.readFromNBT(registries, tag);
        water.readFromNBT(registries, tag.getCompound("water"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(), g = new CompoundTag();
        g.putBoolean("formed", formed);
        g.putInt("rot", formedRot);
        g.putInt("spin", spin);
        tag.put("geo", g);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rotor", 10,
                state -> state.setAndContinue(spin > 0 ? RUNNING : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    @Override
    public double getTick(Object object) {
        double real = RenderUtil.getCurrentTick();
        if (lastRealTick >= 0 && real > lastRealTick) {
            animTime += (real - lastRealTick) * (spin > 0 ? 2.0 * spin / SPIN_STEPS : 1.0);
        }
        lastRealTick = real;
        return animTime;
    }
}
