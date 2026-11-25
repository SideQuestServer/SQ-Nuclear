package com.sqc.nuclearsq.transport;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;

public class ConduitBlockEntity extends BlockEntity {
    public static final int AUTO = 0, PULL = 1, OFF = 2;
    private static final Direction[] DIRS = Direction.values();
    public static final int ITEM_INTERVAL = 5;

    private final FluidTank tank;
    private int energy;
    private int modes;
    private int recv, recvPrev;
    private final long[] recvAt = new long[6];
    private final long[] sentAt = new long[6];
    private int flow;
    private int rot;
    private int dist = FAR;
    private int hops;
    @Nullable private BlockPos tgt;
    @Nullable private Direction tgtSide;
    private boolean bat;
    private int fillDist = FAR;
    public static final int FAR = 1 << 20;
    private static final int REACH_MAX = 4096;
    private final int[] reach = new int[6];
    private FluidStack probe = FluidStack.EMPTY;
    private int probeAge = REACH_MAX;
    private int conn;
    private boolean sidesChecked;
    private boolean hotPrimary;
    private ItemStack item = ItemStack.EMPTY;
    private int itemRecv, itemRecvPrev;
    private long fluidAt = -100;
    private boolean full;
    private int hold;
    private boolean framed;
    @Nullable private BlockState camo;

    public static final ModelProperty<Integer> CONN = new ModelProperty<>();
    public static final ModelProperty<Integer> POST = new ModelProperty<>();
    public static final ModelProperty<BlockState> CAMO = new ModelProperty<>();

    private final IFluidHandler[] fluidSides = new IFluidHandler[7];
    private final IEnergyStorage[] energySides = new IEnergyStorage[7];
    private final IItemHandler[] itemSides = new IItemHandler[7];

    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(TransportRegistry.CONDUIT_BE.get(), pos, state);
        tank = new FluidTank(block(state).rate()) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        java.util.Arrays.fill(reach, REACH_MAX);
        for (int i = 0; i < 7; i++) {
            Direction d = i < 6 ? DIRS[i] : null;
            fluidSides[i] = new SideFluid(d);
            energySides[i] = new SideEnergy(d);
            itemSides[i] = new SideItems(d);
        }
    }

    private static ConduitBlock block(BlockState state) {
        return (ConduitBlock) state.getBlock();
    }

    private ConduitBlock block() {
        return block(getBlockState());
    }

    public int rate() {
        return block().rate();
    }

    public int conn() {
        return conn;
    }

    public int dist() { return dist; }

    public int flow() { return flow; }

    public ConduitSide side(Direction d) {
        return ConduitSide.of(conn, d);
    }

    boolean setConn(int now) {
        if (now == conn) return false;
        conn = now;
        setChanged();
        return true;
    }

    public int hold() {
        return hold;
    }

    boolean setHold(int now) {
        if (now == hold) return false;
        hold = now;
        setChanged();
        return true;
    }

    public boolean framed() {
        return framed;
    }

    @Nullable
    public BlockState camo() {
        return camo;
    }

    void setFrame(boolean framed, @Nullable BlockState camo) {
        this.framed = framed;
        this.camo = framed ? camo : null;
        setChanged();
    }

    public static final int HOLD_SHIFT = 24;

    public static final int PLAIN_SHIFT = 16;

    private int plainMask() {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (ConduitSide.of(conn, d) != ConduitSide.ON) continue;
            int boundary = worldPosition.get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
            if (Math.floorMod(boundary, 3) != 0) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    @Override
    public ModelData getModelData() {
        ModelData.Builder b = ModelData.builder().with(CONN, shownConn() | plainMask() << PLAIN_SHIFT | (hold & 63) << HOLD_SHIFT)
                .with(POST, hold >>> 6);
        if (framed) b.with(CAMO, camo != null ? camo : TransportRegistry.CONDUIT_FRAME.get().defaultBlockState());
        return b.build();
    }

    private int shownConn() {
        if (level == null || !(getBlockState().getBlock() instanceof CableBlock)) return conn;
        int c = conn;
        for (Direction d : Direction.values()) {
            if (ConduitSide.of(c, d) == ConduitSide.ON && level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBlockEntity o && o.camo != null) {
                c = ConduitSide.with(c, d, ConduitSide.PORT);
            }
        }
        return c;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) requestModelDataUpdate();
    }

    public int mode(Direction d) {
        return (modes >> (2 * d.ordinal())) & 3;
    }

    public int cycle(Direction d, boolean fluid) {
        int m = mode(d);
        int next = m == AUTO ? (fluid ? PULL : OFF) : m == PULL ? OFF : AUTO;
        modes = (modes & ~(3 << (2 * d.ordinal()))) | (next << (2 * d.ordinal()));
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
        return next;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = saveCustomOnly(reg);
        tag.putBoolean("Full", full);
        tag.putInt("Flow", flow);
        return tag;
    }

    public boolean full() {
        return full;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    public IFluidHandler fluidSide(@Nullable Direction d) {
        if (!block().fluid || (d != null && mode(d) == OFF)) return null;
        return fluidSides[d == null ? 6 : d.ordinal()];
    }

    @Nullable
    public IEnergyStorage energySide(@Nullable Direction d) {
        if (block().fluid || block().items() || block().data() || (d != null && mode(d) == OFF)) return null;
        return energySides[d == null ? 6 : d.ordinal()];
    }

    @Nullable
    public IItemHandler itemSide(@Nullable Direction d) {
        if (!block().items() || (d != null && mode(d) == OFF)) return null;
        return itemSides[d == null ? 6 : d.ordinal()];
    }

    public ItemStack item() {
        return item;
    }

    public void clearItem() {
        item = ItemStack.EMPTY;
        setChanged();
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    public int energy() {
        return energy;
    }

    @Override
    public void setChanged() {
        if (level != null) level.blockEntityChanged(worldPosition);
    }

    private void received(@Nullable Direction d) {
        if (d != null) recv |= 1 << d.ordinal();
        if (d != null && level != null) recvAt[d.ordinal()] = level.getGameTime();
    }

    private boolean cameFrom(Direction d) {
        return ((recv | recvPrev) & (1 << d.ordinal())) != 0;
    }

    private final class SideFluid implements IFluidHandler {
        private final Direction side;

        SideFluid(@Nullable Direction side) { this.side = side; }

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return tank.isFluidValid(s); }

        @Override
        public int fill(FluidStack s, FluidAction a) {
            int n = tank.fill(s, a);
            if (n > 0 && a.execute()) received(side);
            return n;
        }

        @Override public FluidStack drain(FluidStack s, FluidAction a) { return tank.drain(s, a); }
        @Override public FluidStack drain(int max, FluidAction a) { return tank.drain(max, a); }
    }

    private final class SideEnergy implements IEnergyStorage {
        private final Direction side;

        SideEnergy(@Nullable Direction side) { this.side = side; }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int n = Math.max(0, Math.min(max, rate() - energy));
            if (!simulate && n > 0) {
                energy += n;
                received(side);
                setChanged();
            }
            return n;
        }

        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return rate(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }

    private final class SideItems implements IItemHandler {
        private final Direction side;

        SideItems(@Nullable Direction side) { this.side = side; }

        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return item; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return item.isEmpty() || ItemStack.isSameItemSameComponents(item, stack); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
            int n = Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize()) - item.getCount());
            if (n <= 0) return stack;
            if (!simulate) {
                if (item.isEmpty()) item = stack.copyWithCount(n);
                else item.grow(n);
                if (side != null) itemRecv |= 1 << side.ordinal();
                if (side != null && level != null) recvAt[side.ordinal()] = level.getGameTime();
                setChanged();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }
    }

    void serverTick() {
        if (!sidesChecked) {
            sidesChecked = true;
            block(getBlockState()).refresh(level, worldPosition);
        }
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof ConduitBlock) || block(state).data()) return;
        fillDist = (block(state).items() ? !item.isEmpty() : block(state).fluid ? !tank.isEmpty() : energy > 0) ? 0 : stauDist();
        showFill(fillDist < 256);
        if (Math.floorMod(level.getGameTime(), 10) == 0) updateFlow();
        if (block(state).items()) {
            if (Math.floorMod(level.getGameTime(), ITEM_INTERVAL) == 0) itemTick(state);
            return;
        }
        boolean fluid = block(state).fluid;
        if (!fluid) route();
        if (fluid) pull(state);
        if (fluid) radiateHotPrimary();
        if (fluid) reachScan();
        if (fluid ? !tank.isEmpty() : energy > 0) {
            int budget = rate();
            for (int pass = 0; pass < (fluid ? 2 : 3) && budget > 0; pass++) {
                if (pass == 2 && energy * 2 < rate()) break;
                for (int i = 0; i < 6 && budget > 0; i++) {
                    Direction d = DIRS[(rot + i) % 6];
                    if (side(d) == ConduitSide.NONE || side(d) == ConduitSide.PULL || (fluid ? cameFrom(d) : level.getGameTime() - recvAt[d.ordinal()] < 20)) continue;
                    BlockPos n = worldPosition.relative(d);
                    BlockEntity nb = NB[d.ordinal()];
                    boolean conduit = nb instanceof ConduitBlockEntity;
                    if (conduit != (pass >= 1)) continue;
                    if (fluid && conduit && reach[d.ordinal()] >= REACH_MAX) continue;
                    if (!fluid && !conduit && need(n, d.getOpposite()) > dist && energy * 2 < rate()) continue;
                    if (!fluid && conduit) {
                        ConduitBlockEntity c = (ConduitBlockEntity) nb;
                        if (pass == 1 ? c.dist >= dist : (c.dist < dist || c.dist >= FAR || c.energy >= energy)) continue;
                    }
                    budget -= fluid ? pushFluid(n, d, nb, budget) : pushEnergy(n, d, nb, budget);
                    if (fluid ? tank.isEmpty() : energy <= 0) break;
                }
            }
            rot = (rot + 1) % 6;
        }
        recvPrev = recv;
        recv = 0;
    }

    private static final BlockEntity[] NB = new BlockEntity[6];

    private void neighbours() {
        for (Direction d : DIRS) NB[d.ordinal()] = side(d) == ConduitSide.NONE ? null : level.getBlockEntity(worldPosition.relative(d));
    }

    private void reachScan() {
        neighbours();
        int age = tank.isEmpty() ? REACH_MAX : 0;
        if (age == 0 && !FluidStack.isSameFluidSameComponents(probe, tank.getFluid())) probe = tank.getFluid().copyWithAmount(rate());
        for (Direction d : DIRS) {
            int i = d.ordinal();
            reach[i] = REACH_MAX;
            ConduitSide s = side(d);
            if (s == ConduitSide.NONE || s == ConduitSide.PULL) continue;
            if (NB[i] instanceof ConduitBlockEntity c) {
                if (c.fluidSide(d.getOpposite()) == null) continue;
                if (c.probeAge + 1 < age) {
                    age = c.probeAge + 1;
                    probe = c.probe;
                }
                int back = d.getOpposite().ordinal(), best = REACH_MAX;
                for (int e = 0; e < 6; e++) if (e != back) best = Math.min(best, c.reach[e]);
                reach[i] = Math.min(REACH_MAX, best + 1);
                continue;
            }
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (h == null) continue;
            if (probe.isEmpty() && h.getTanks() > 0 && !h.getFluidInTank(0).isEmpty()) probe = h.getFluidInTank(0).copyWithAmount(rate());
            if (!probe.isEmpty() && h.fill(probe, IFluidHandler.FluidAction.SIMULATE) > 0) reach[i] = 0;
        }
        probeAge = age;
    }

    private void route() {
        neighbours();
        int best = FAR, hop = 0;
        BlockPos to = null;
        Direction toSide = null;
        boolean toBat = false;
        for (Direction d : DIRS) {
            if (side(d) == ConduitSide.NONE) continue;
            BlockPos n = worldPosition.relative(d);
            if (NB[d.ordinal()] instanceof ConduitBlockEntity c) {
                if (c.energySide(d.getOpposite()) != null && c.dist + 1 < best) {
                    best = c.dist + 1;
                    to = c.tgt;
                    toSide = c.tgtSide;
                    toBat = c.bat;
                    hop = c.hops + 1;
                }
                continue;
            }
            IEnergyStorage s = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite());
            if (s != null && s.canReceive() && s.receiveEnergy(rate(), true) > 0) {
                int v = need(n, d.getOpposite());
                if (v < best) {
                    best = v;
                    to = n;
                    toSide = d.getOpposite();
                    toBat = NB[d.ordinal()] instanceof com.sqc.nuclearsq.machine.big.BigPortBlockEntity pb && pb.findController() != null && pb.findController().battery() != null;
                    hop = 0;
                }
            }
        }
        if (to != null && hop > 0) best = level.isLoaded(to) ? Math.min(FAR, need(to, toSide) + hop) : FAR;
        tgt = to;
        tgtSide = toSide;
        bat = toBat;
        hops = hop;
        dist = Math.min(best, FAR);
    }

    public boolean toBattery() { return bat; }

    @Nullable
    public IEnergyStorage target() {
        return tgt != null && tgtSide != null && level != null && level.isLoaded(tgt) ? level.getCapability(Capabilities.EnergyStorage.BLOCK, tgt, tgtSide) : null;
    }

    private int need(BlockPos p, Direction side) {
        IEnergyStorage s = level.getCapability(Capabilities.EnergyStorage.BLOCK, p, side);
        if (s == null || !s.canReceive()) return FAR;
        long e = s.getEnergyStored(), max = s.getMaxEnergyStored();
        if (max <= 0) return s.receiveEnergy(rate(), true) > 0 ? needFor(0, 1) : FAR;
        return e < max ? needFor(e, max) : FAR;
    }

    public static int needFor(long e, long max) {
        return (int) (Math.max(0, e) * 10000 / Math.max(1, max)) * 64 + 2048;
    }

    private int stauDist() {
        int best = FAR;
        for (Direction d : DIRS) {
            if (side(d) != ConduitSide.NONE && level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBlockEntity c) best = Math.min(best, c.fillDist + 1);
        }
        return best;
    }

    private void showFill(boolean content) {
        if (content) fluidAt = level.getGameTime();
        boolean now = level.getGameTime() - fluidAt < 40;
        if (now == full) return;
        full = now;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void updateFlow() {
        long t = level.getGameTime();
        int now = 0;
        for (int i = 0; i < 6; i++) {
            if (t - recvAt[i] < 20) now |= 1 << i;
            if (t - sentAt[i] < 20) now |= 1 << (i + 6);
        }
        if (now == 0) {
            now = flow;
            for (int i = 0; i < 6; i++) if (side(DIRS[i]) == ConduitSide.NONE) now &= ~(1 << i | 1 << (i + 6));
        }
        if (now == flow) return;
        flow = now;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void radiateHotPrimary() {
        if (com.sqc.nuclearsq.registry.ModFluids.isHotPrimary(tank.getFluid())) hotPrimary = true;
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 20) != 0) return;
        if (hotPrimary && level instanceof net.minecraft.server.level.ServerLevel sl) {
            com.sqc.nuclearsq.radiation.RadiationManager.emit(sl, worldPosition, com.sqc.nuclearsq.radiation.RadiationValues.HOT_PRIMARY_SV_H);
        }
        hotPrimary = false;
    }

    private int pushFluid(BlockPos n, Direction d, BlockEntity nb, int budget) {
        IFluidHandler h = nb instanceof ConduitBlockEntity c ? c.fluidSide(d.getOpposite())
                : level.getCapability(Capabilities.FluidHandler.BLOCK, n, d.getOpposite());
        if (h == null) return 0;
        FluidStack out = tank.drain(budget, IFluidHandler.FluidAction.SIMULATE);
        if (out.isEmpty()) return 0;
        int took = h.fill(out, IFluidHandler.FluidAction.EXECUTE);
        if (took > 0) {
            tank.drain(took, IFluidHandler.FluidAction.EXECUTE);
            sentAt[d.ordinal()] = level.getGameTime();
        }
        return took;
    }

    private int pushEnergy(BlockPos n, Direction d, BlockEntity nb, int budget) {
        IEnergyStorage s = nb instanceof ConduitBlockEntity c ? c.energySide(d.getOpposite())
                : level.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite());
        if (s == null || !s.canReceive()) return 0;
        int took = s.receiveEnergy(Math.min(budget, energy), false);
        if (took > 0) {
            energy -= took;
            sentAt[d.ordinal()] = level.getGameTime();
            setChanged();
        }
        return took;
    }

    private void itemTick(BlockState state) {
        pullItems(state);
        int budget = rate();
        int from = itemRecv | itemRecvPrev;
        for (int pass = 0; pass < 2 && budget > 0 && !item.isEmpty(); pass++) {
            for (int i = 0; i < 6 && budget > 0 && !item.isEmpty(); i++) {
                Direction d = DIRS[(rot + i) % 6];
                if (side(d) == ConduitSide.NONE || (from & (1 << d.ordinal())) != 0) continue;
                BlockPos n = worldPosition.relative(d);
                BlockEntity nb = level.getBlockEntity(n);
                boolean conduit = nb instanceof ConduitBlockEntity;
                if (conduit != (pass == 1)) continue;
                IItemHandler h = nb instanceof ConduitBlockEntity c ? c.itemSide(d.getOpposite())
                        : level.getCapability(Capabilities.ItemHandler.BLOCK, n, d.getOpposite());
                if (h == null) continue;
                ItemStack offer = item.copyWithCount(Math.min(budget, item.getCount()));
                int moved = offer.getCount() - net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, offer, false).getCount();
                if (moved > 0) {
                    item.shrink(moved);
                    if (item.isEmpty()) item = ItemStack.EMPTY;
                    budget -= moved;
                    sentAt[d.ordinal()] = level.getGameTime();
                    setChanged();
                }
            }
        }
        rot = (rot + 1) % 6;
        itemRecvPrev = itemRecv;
        itemRecv = 0;
    }

    private void pullItems(BlockState state) {
        int budget = rate();
        for (Direction d : DIRS) {
            if (budget <= 0) return;
            if (side(d) != ConduitSide.PULL) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (h == null) continue;
            for (int slot = 0; slot < h.getSlots() && budget > 0; slot++) {
                ItemStack offer = h.extractItem(slot, budget, true);
                if (offer.isEmpty()) continue;
                int fit = offer.getCount() - itemSides[d.ordinal()].insertItem(0, offer, true).getCount();
                if (fit <= 0) continue;
                ItemStack got = h.extractItem(slot, fit, false);
                if (got.isEmpty()) continue;
                ItemStack rest = itemSides[d.ordinal()].insertItem(0, got, false);
                if (!rest.isEmpty()) h.insertItem(slot, rest, false);
                budget -= got.getCount() - rest.getCount();
            }
        }
    }

    private void pull(BlockState state) {
        for (Direction d : DIRS) {
            if (side(d) != ConduitSide.PULL) continue;
            int space = tank.getCapacity() - tank.getFluidAmount();
            if (space <= 0) return;
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (h == null) continue;
            FluidStack offer = tank.isEmpty() ? h.drain(space, IFluidHandler.FluidAction.SIMULATE)
                    : h.drain(tank.getFluid().copyWithAmount(space), IFluidHandler.FluidAction.SIMULATE);
            if (offer.isEmpty()) continue;
            int fit = tank.fill(offer, IFluidHandler.FluidAction.SIMULATE);
            if (fit <= 0) continue;
            FluidStack got = h.drain(offer.copyWithAmount(fit), IFluidHandler.FluidAction.EXECUTE);
            if (got.isEmpty()) continue;
            int took = tank.fill(got, IFluidHandler.FluidAction.EXECUTE);
            if (took < got.getAmount()) h.fill(got.copyWithAmount(got.getAmount() - took), IFluidHandler.FluidAction.EXECUTE);
            if (took > 0) received(d);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!tank.isEmpty()) tag.put("Tank", tank.writeToNBT(reg, new CompoundTag()));
        tag.putInt("Energy", energy);
        tag.putInt("Modes", modes);
        tag.putInt("Conn", conn);
        if (hold != 0) tag.putInt("Hold", hold);
        if (flow != 0) tag.putInt("Flow", flow);
        if (!item.isEmpty()) tag.put("Item", item.save(reg));
        if (framed) tag.putBoolean("Framed", true);
        if (camo != null) tag.put("Camo", NbtUtils.writeBlockState(camo));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Tank")) tank.readFromNBT(reg, tag.getCompound("Tank"));
        energy = Math.min(tag.getInt("Energy"), rate());
        modes = tag.getInt("Modes");
        int old = conn, oldHold = hold;
        boolean wasFull = full, wasFramed = framed;
        BlockState oldCamo = camo;
        conn = tag.getInt("Conn");
        hold = tag.getInt("Hold");
        full = tag.getBoolean("Full");
        flow = tag.getInt("Flow");
        framed = tag.getBoolean("Framed");
        camo = framed && tag.contains("Camo") ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Camo")) : null;
        if (camo != null && camo.isAir()) camo = null;
        if ((conn != old || hold != oldHold || full != wasFull || framed != wasFramed || camo != oldCamo) && level != null && level.isClientSide) {
            requestModelDataUpdate();
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_IMMEDIATE);
        }
        if (camo != oldCamo && level != null && level.isClientSide) {
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBlockEntity o) {
                    o.requestModelDataUpdate();
                    level.sendBlockUpdated(o.worldPosition, o.getBlockState(), o.getBlockState(), Block.UPDATE_IMMEDIATE);
                }
            }
        }
        item = tag.contains("Item") ? ItemStack.parseOptional(reg, tag.getCompound("Item")) : ItemStack.EMPTY;
    }
}
