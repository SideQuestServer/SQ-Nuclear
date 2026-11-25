package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.registry.PowerRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class SolarPanelBlockEntity extends BlockEntity {
    public static final int PEAK = 69;
    public static final int BUFFER_TICKS = 400;
    public static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};

    private static final IEnergyStorage PORT = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return false; }
    };

    private final int peak;
    private int timer, rate;
    protected int delivered;
    private int stored;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        this(PowerRegistry.SOLAR_PANEL_BE.get(), pos, state, PEAK);
    }

    protected SolarPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int peak) {
        super(type, pos, state);
        this.peak = peak;
    }

    public IEnergyStorage port(Direction side) {
        return side == Direction.UP || side != null && !outputs(side) ? null : PORT;
    }

    public int peak() { return peak; }

    public int rate() { return rate; }

    public int delivered() { return delivered; }

    public int stored() { return stored; }

    public int capacity() { return peak * BUFFER_TICKS; }

    public void serverTick() {
        if (level == null) return;
        if (timer-- <= 0) {
            timer = 20;
            rate = Math.round(peak * SolarMath.factor(level, worldPosition.above(), tracks()));
            onSecond();
            setChanged();
        }
        int offer = rate + Math.min(stored, peak);
        delivered = offer > 0 ? deliver(offer) : 0;
        stored = Math.max(0, Math.min(capacity(), stored + rate - delivered));
    }

    protected void onSecond() {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("fe", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getInt("fe");
    }

    protected boolean tracks() { return false; }

    protected boolean outputs(Direction d) { return true; }

    public int deliver(int amount) {
        return pushToSinks(amount, amount);
    }

    protected int pushToSinks(int amount, int limit) {
        int left = Math.min(amount, limit);
        int given = 0;
        for (Direction dir : SIDES) {
            if (left <= 0) break;
            if (!outputs(dir)) continue;
            BlockPos n = worldPosition.relative(dir);
            if (!level.isLoaded(n) || level.getBlockEntity(n) instanceof SolarPanelBlockEntity) continue;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int took = target.receiveEnergy(left, false);
            left -= took;
            given += took;
        }
        return given;
    }

    protected boolean hasSink() {
        for (Direction dir : SIDES) {
            if (!outputs(dir)) continue;
            BlockPos n = worldPosition.relative(dir);
            if (!level.isLoaded(n) || level.getBlockEntity(n) instanceof SolarPanelBlockEntity) continue;
            IEnergyStorage t = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, dir.getOpposite());
            if (t != null && t.canReceive()) return true;
        }
        return false;
    }
}
