package com.sqc.nuclearsq.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.ChunkGuard;
import com.sqc.nuclearsq.data.DataCableBlock;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.ConduitSide;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class NetPipeAdapterBlockEntity extends BlockEntity {
    public static final int DEMAND = 0, STORE = 1, EXPORT = 2, MODES = 3, FILTER_SLOTS = 5;
    public static final int DIRECT = 16, MAX_PIPES = 512;
    public static final String[] PROBLEMS = {"", "paused", "no_target", "no_pattern", "no_storage", "missing", "idle", "no_filter"};

    private int mode = DEMAND;
    private boolean enabled = true;
    private boolean paused;
    private int moved, lastMoved;
    private int targets;
    private int rot;
    private String lastProblem = "idle";
    private final SimpleContainer shown = new SimpleContainer(2);
    private final SimpleContainer filters = new SimpleContainer(FILTER_SLOTS);
    private final int[] stock = new int[FILTER_SLOTS];
    private final Handler handler = new Handler();
    @Nullable
    private StorageAccess cached;
    private long cachedAt = Long.MIN_VALUE;

    private record Target(BigMachineBlockEntity machine, IItemHandler handler, RecipeSources.Need[] needs, ItemStack result) {}

    public NetPipeAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(StorageRegistry.NET_PIPE_ADAPTER_BE.get(), pos, state);
        filters.addListener(c -> setChanged());
    }

    @Nullable
    public IItemHandler handler(@Nullable Direction side) {
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof NetPipeAdapterBlock) || side != st.getValue(NetPipeAdapterBlock.FACING)) return null;
        return handler;
    }

    public int mode() {
        return mode;
    }

    public void setMode(int m) {
        mode = m >= 0 && m < MODES ? m : DEMAND;
        setChanged();
        updateLook();
    }

    public void cycleMode() {
        setMode((mode + 1) % MODES);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        setChanged();
        updateLook();
    }

    public int lastMoved() {
        return lastMoved;
    }

    public int targets() {
        return targets;
    }

    public String problem() {
        return lastProblem;
    }

    public SimpleContainer shown() {
        return shown;
    }

    public SimpleContainer filters() {
        return filters;
    }

    public int filterCount() {
        int n = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) if (!filters.getItem(i).isEmpty()) n++;
        return n;
    }

    public int problemIndex() {
        for (int i = 0; i < PROBLEMS.length; i++) if (PROBLEMS[i].equals(lastProblem)) return i;
        return 0;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        long t = sl.getGameTime();
        if (t % 20 == 0) {
            lastMoved = moved;
            moved = 0;
            paused = sl.hasNeighborSignal(worldPosition);
            if (mode == STORE) checkStore();
            if (mode == EXPORT) refreshStock();
            updateLook();
        }
        if (Math.floorMod(t, ConduitBlockEntity.ITEM_INTERVAL) == 0) {
            if (mode == DEMAND) step(sl);
            else if (mode == EXPORT) export(sl);
        }
    }

    private void export(ServerLevel sl) {
        clearShown();
        targets = 0;
        if (!enabled || paused) {
            lastProblem = "paused";
            return;
        }
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof NetPipeAdapterBlock)) return;
        Direction f = st.getValue(NetPipeAdapterBlock.FACING);
        BlockPos front = worldPosition.relative(f);
        if (!ChunkGuard.loaded(sl, front)) {
            lastProblem = "no_target";
            return;
        }
        IItemHandler dest = sl.getCapability(Capabilities.ItemHandler.BLOCK, front, f.getOpposite());
        if (dest == null) {
            lastProblem = "no_target";
            return;
        }
        Map<ItemKey, Long> want = new LinkedHashMap<>();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack fs = filters.getItem(i);
            if (!fs.isEmpty()) want.put(ItemKey.of(fs), Long.MAX_VALUE);
        }
        if (want.isEmpty()) {
            lastProblem = "no_filter";
            return;
        }
        StorageAccess net = access();
        if (net == null || net.drives() == 0) {
            lastProblem = "no_storage";
            return;
        }
        int budget = sl.getBlockEntity(front) instanceof ConduitBlockEntity c ? c.rate() : DIRECT;
        int sent = deliver(net, dest, want, budget);
        moved += sent;
        if (sent > 0) lastProblem = "";
        else {
            boolean any = false;
            for (ItemKey k : want.keySet()) if (net.available(k) > 0) any = true;
            lastProblem = any ? "idle" : "missing";
        }
    }

    private void refreshStock() {
        StorageAccess net = null;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack fs = filters.getItem(i);
            if (fs.isEmpty()) {
                stock[i] = 0;
                continue;
            }
            if (net == null) net = access();
            ItemKey k = ItemKey.of(fs);
            stock[i] = net == null ? 0 : (int) Math.min(k.maxStack(), net.available(k));
        }
    }

    private void checkStore() {
        clearShown();
        targets = 0;
        if (!enabled || paused) {
            lastProblem = "paused";
            return;
        }
        StorageAccess net = access();
        lastProblem = net == null || net.drives() == 0 ? "no_storage" : lastMoved > 0 ? "" : "idle";
    }

    private void clearShown() {
        if (!shown.isEmpty()) shown.clearContent();
    }

    private void step(ServerLevel sl) {
        if (!enabled || paused) {
            lastProblem = "paused";
            return;
        }
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof NetPipeAdapterBlock)) return;
        Direction f = st.getValue(NetPipeAdapterBlock.FACING);
        BlockPos front = worldPosition.relative(f);
        if (!ChunkGuard.loaded(sl, front)) {
            lastProblem = "no_target";
            return;
        }
        BlockEntity fb = sl.getBlockEntity(front);
        List<Target> found = new ArrayList<>();
        Map<ItemKey, Integer> transit = new HashMap<>();
        IItemHandler dest;
        int budget;
        if (fb instanceof ConduitBlockEntity c && c.getBlockState().getBlock() instanceof ConduitBlock cb && cb.items()) {
            dest = c.itemSide(f.getOpposite());
            budget = c.rate();
            scan(sl, front, found, transit);
        } else if (fb instanceof BigPortBlockEntity bp) {
            Target tg = target(sl, bp);
            if (tg != null) found.add(tg);
            dest = tg == null ? null : tg.handler();
            budget = DIRECT;
        } else {
            dest = null;
            budget = 0;
        }
        targets = found.size();
        if (found.isEmpty() || dest == null) {
            clearShown();
            lastProblem = fb instanceof ConduitBlockEntity || fb instanceof BigPortBlockEntity ? "no_pattern" : "no_target";
            return;
        }
        Target first = found.get(0);
        shown.setItem(0, new ItemStack(first.machine().getBlockState().getBlock()));
        shown.setItem(1, first.result().copy());
        StorageAccess net = access();
        if (net == null || net.drives() == 0) {
            lastProblem = "no_storage";
            return;
        }
        Map<ItemKey, Long> want = new LinkedHashMap<>();
        for (Target tg : found) wants(tg, net, want);
        for (Map.Entry<ItemKey, Integer> e : transit.entrySet()) want.computeIfPresent(e.getKey(), (k, v) -> v - e.getValue());
        want.values().removeIf(v -> v <= 0);
        if (want.isEmpty()) {
            lastProblem = "idle";
            return;
        }
        int sent = deliver(net, dest, want, budget);
        moved += sent;
        if (sent > 0) lastProblem = "";
        else {
            boolean any = false;
            for (ItemKey k : want.keySet()) if (net.available(k) > 0) any = true;
            lastProblem = any ? "idle" : "missing";
        }
    }

    private void scan(ServerLevel sl, BlockPos start, List<Target> out, Map<ItemKey, Integer> transit) {
        Set<BlockPos> seen = new HashSet<>();
        seen.add(worldPosition);
        seen.add(start);
        Set<BigMachineBlockEntity> machines = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int pipes = 0;
        while (!queue.isEmpty() && pipes++ < MAX_PIPES) {
            BlockPos p = queue.poll();
            if (!(sl.getBlockEntity(p) instanceof ConduitBlockEntity c)) continue;
            ItemStack in = c.item();
            if (!in.isEmpty()) transit.merge(ItemKey.of(in), in.getCount(), Integer::sum);
            for (Direction d : Direction.values()) {
                ConduitSide side = c.side(d);
                if (side == ConduitSide.NONE) continue;
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !ChunkGuard.loaded(sl, n)) continue;
                BlockEntity nb = sl.getBlockEntity(n);
                if (side == ConduitSide.ON) {
                    if (nb instanceof ConduitBlockEntity) {
                        seen.add(n);
                        queue.add(n);
                    }
                } else if (nb instanceof BigPortBlockEntity bp) {
                    seen.add(n);
                    Target tg = target(sl, bp);
                    if (tg != null && machines.add(tg.machine())) out.add(tg);
                }
            }
        }
    }

    @Nullable
    private Target target(ServerLevel sl, BigPortBlockEntity bp) {
        BigMachineBlockEntity c = bp.findController();
        if (c == null || c.crafter() == null || c.items() == null) return null;
        BigMachine.Port port = c.machine().port(bp.portName());
        if (port == null || !port.in()) return null;
        ResourceLocation id = c.crafter().lockId();
        if (id == null) return null;
        Recipe<?> r = sl.getRecipeManager().byKey(id).map(RecipeHolder::value).orElse(null);
        IItemHandler h = c.itemsAt(bp.portName());
        if (r == null || h == null) return null;
        return new Target(c, h, MachineRecipes.needs(r, c.items().getSlots(), sl.registryAccess()), r.getResultItem(sl.registryAccess()));
    }

    private static void wants(Target tg, StorageAccess net, Map<ItemKey, Long> want) {
        IItemHandler h = tg.handler();
        Map<ItemKey, Integer> per = new LinkedHashMap<>();
        int times = Integer.MAX_VALUE;
        for (RecipeSources.Need n : tg.needs()) {
            if (n == null || n.count() <= 0) continue;
            ItemKey k = kind(n, h, net);
            if (k == null) continue;
            per.merge(k, n.count(), Integer::sum);
            times = Math.min(times, Math.max(1, Math.min(64, k.maxStack()) / n.count()));
        }
        if (per.isEmpty()) return;
        for (Map.Entry<ItemKey, Integer> e : per.entrySet()) {
            long have = 0;
            for (int s = 0; s < h.getSlots(); s++) {
                ItemStack st = h.getStackInSlot(s);
                if (!st.isEmpty() && e.getKey().matches(st) && h.isItemValid(s, st)) have += st.getCount();
            }
            long need = (long) e.getValue() * times - have;
            if (need > 0) want.merge(e.getKey(), need, Long::sum);
        }
    }

    @Nullable
    private static ItemKey kind(RecipeSources.Need n, IItemHandler h, StorageAccess net) {
        for (int s = 0; s < h.getSlots(); s++) {
            ItemStack st = h.getStackInSlot(s);
            if (!st.isEmpty() && n.ingredient().test(st) && h.isItemValid(s, st)) return ItemKey.of(st);
        }
        ItemStack[] all = n.ingredient().getItems();
        ItemKey best = null;
        long most = 0;
        for (ItemStack c : all) {
            ItemKey k = ItemKey.of(c);
            if (k == null) continue;
            long a = net.available(k);
            if (a > most) {
                most = a;
                best = k;
            }
        }
        return best != null ? best : all.length > 0 ? ItemKey.of(all[0]) : null;
    }

    private int deliver(StorageAccess net, IItemHandler dest, Map<ItemKey, Long> want, int budget) {
        List<ItemKey> keys = new ArrayList<>(want.keySet());
        int sent = 0;
        for (int i = 0; i < keys.size() && sent < budget; i++) {
            ItemKey k = keys.get(Math.floorMod(rot + i, keys.size()));
            int n = (int) Math.min(Math.min(want.get(k), budget - sent), k.maxStack());
            ItemStack offer = net.extractStack(k, n, true);
            if (offer.isEmpty()) continue;
            int fits = offer.getCount() - ItemHandlerHelper.insertItem(dest, offer, true).getCount();
            if (fits <= 0) continue;
            ItemStack real = net.extractStack(k, fits, false);
            if (real.isEmpty()) continue;
            ItemStack left = ItemHandlerHelper.insertItem(dest, real, false);
            if (!left.isEmpty()) net.insert(left, false);
            sent += real.getCount() - left.getCount();
        }
        rot++;
        return sent;
    }

    private void updateLook() {
        if (level == null || level.isClientSide) return;
        BlockState st = getBlockState();
        if (!(st.getBlock() instanceof NetPipeAdapterBlock)) return;
        NetPipeAdapterBlock.Status s = !enabled || paused ? NetPipeAdapterBlock.Status.OFF
                : lastMoved > 0 ? NetPipeAdapterBlock.Status.OK
                : lastProblem.equals("no_storage") ? NetPipeAdapterBlock.Status.ERR : NetPipeAdapterBlock.Status.WAIT;
        NetPipeAdapterBlock.Mode m = mode == STORE ? NetPipeAdapterBlock.Mode.STORE : mode == EXPORT ? NetPipeAdapterBlock.Mode.EXPORT : NetPipeAdapterBlock.Mode.DEMAND;
        if (st.getValue(NetPipeAdapterBlock.STATUS) != s || st.getValue(NetPipeAdapterBlock.MODE) != m) {
            level.setBlock(worldPosition, st.setValue(NetPipeAdapterBlock.STATUS, s).setValue(NetPipeAdapterBlock.MODE, m), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    StorageAccess access() {
        if (level == null) return null;
        long t = level.getGameTime();
        if (cached == null || cachedAt != t) {
            BlockState st = getBlockState();
            BlockPos start = worldPosition;
            if (st.getBlock() instanceof NetPipeAdapterBlock) {
                BlockPos b = worldPosition.relative(NetPipeAdapterBlock.back(st));
                if (level.getBlockState(b).getBlock() instanceof DataCableBlock) start = b;
            }
            cached = StorageNet.access(level, start);
            cachedAt = t;
        }
        return cached;
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> mode;
                    case 1 -> enabled ? 1 : 0;
                    case 2 -> lastMoved;
                    case 3 -> problemIndex();
                    case 4 -> targets;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) { }

            @Override
            public int getCount() {
                return NetPipeAdapterMenu.DATA;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putByte("mode", (byte) mode);
        tag.putBoolean("enabled", enabled);
        if (!filters.isEmpty()) tag.put("filters", filters.createTag(reg));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        int m = tag.getByte("mode");
        mode = m >= 0 && m < MODES ? m : DEMAND;
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        filters.clearContent();
        if (tag.contains("filters", Tag.TAG_LIST)) filters.fromTag(tag.getList("filters", Tag.TAG_COMPOUND), reg);
    }

    private final class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + FILTER_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot <= 0 || slot > FILTER_SLOTS || mode != EXPORT || !enabled) return ItemStack.EMPTY;
            ItemStack fs = filters.getItem(slot - 1);
            int n = stock[slot - 1];
            return fs.isEmpty() || n <= 0 ? ItemStack.EMPTY : fs.copyWithCount(n);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !enabled || level == null || level.isClientSide) return stack;
            StorageAccess net = access();
            if (net == null || net.drives() == 0) return stack;
            ItemStack left = net.insert(stack, simulate);
            if (!simulate) moved += stack.getCount() - left.getCount();
            return left;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot <= 0 || slot > FILTER_SLOTS || amount <= 0 || mode != EXPORT || !enabled || paused || level == null || level.isClientSide) return ItemStack.EMPTY;
            ItemStack fs = filters.getItem(slot - 1);
            if (fs.isEmpty()) return ItemStack.EMPTY;
            StorageAccess net = access();
            if (net == null) return ItemStack.EMPTY;
            ItemKey k = ItemKey.of(fs);
            ItemStack out = net.extractStack(k, Math.min(amount, k.maxStack()), simulate);
            if (!simulate) moved += out.getCount();
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0;
        }
    }
}
