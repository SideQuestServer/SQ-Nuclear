package com.sqc.nuclearsq.storage;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.IntFunction;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ServerRack {
    public static final int COMPUTE = 7;
    public static final int MAX_PRIORITY = 9;
    private static final int ACTIVE_TICKS = 8;
    private static final double FULL = 0.98;

    private final BigMachine machine;
    private final MachineEnergy energy;
    private final boolean cards;
    private final ItemStackHandler drives;
    private final Runnable changed;
    private final int[] active;
    private final UUID[] seated;
    private final boolean[] recheck;
    private int priority;
    private boolean online;
    private int[] codes;
    private boolean codesDirty = true;

    public ServerRack(BigMachine machine, MachineEnergy energy, Runnable changed) {
        this.machine = machine;
        this.energy = energy;
        this.changed = changed;
        this.cards = machine.isPatternRack();
        int slots = Math.max(1, machine.extra("slots"));
        active = new int[slots];
        codes = new int[slots];
        seated = new UUID[slots];
        recheck = new boolean[slots];
        Arrays.fill(recheck, true);
        drives = new ItemStackHandler(slots) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (cards) return PatternCardItem.pattern(stack) != null;
                return stack.getItem() instanceof DriveItem || stack.getItem() instanceof ComputeModuleItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                codesDirty = true;
                if (slot < recheck.length) recheck[slot] = true;
                changed.run();
            }
        };
    }

    public record Space(long used, long capacity, int types, int typeCapacity, int drives) {
        public long free() {
            return Math.max(0, capacity - used);
        }

        public float fraction() {
            return capacity <= 0 ? 0 : Math.min(1f, used / (float) capacity);
        }
    }

    public static Space space(int slots, IntFunction<ItemStack> stack) {
        long used = 0, capacity = 0;
        int types = 0, typeCapacity = 0, n = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack s = stack.apply(i);
            if (!(s.getItem() instanceof DriveItem d)) continue;
            StorageValues.Drive lim = d.limits();
            DriveItem.Fill f = s.get(ModDataComponents.DRIVE_FILL.get());
            n++;
            capacity += lim.items();
            typeCapacity += lim.types();
            if (f != null) {
                used += f.used();
                types += f.types();
            }
        }
        return new Space(used, capacity, types, typeCapacity, n);
    }

    public Space space() {
        return space(drives.getSlots(), drives::getStackInSlot);
    }

    public record Compute(int cpu, int ram, int modules) {
        public static final Compute NONE = new Compute(0, 0, 0);

        public Compute plus(Compute o) {
            return new Compute(cpu + o.cpu, ram + o.ram, modules + o.modules);
        }
    }

    public static Compute compute(int slots, IntFunction<ItemStack> stack) {
        int cpu = 0, ram = 0, n = 0;
        for (int i = 0; i < slots; i++) {
            if (!(stack.apply(i).getItem() instanceof ComputeModuleItem m)) continue;
            StorageValues.Compute c = m.values();
            cpu += c.cpu();
            ram += c.ram();
            n++;
        }
        return new Compute(cpu, ram, n);
    }

    public Compute compute() {
        return compute(drives.getSlots(), drives::getStackInSlot);
    }

    public boolean isPatternRack() {
        return cards;
    }

    public java.util.List<Pattern> patterns() {
        java.util.List<Pattern> out = new java.util.ArrayList<>();
        if (!cards) return out;
        for (int i = 0; i < drives.getSlots(); i++) {
            Pattern p = PatternCardItem.pattern(drives.getStackInSlot(i));
            if (p != null) out.add(p);
        }
        return out;
    }

    public static boolean handles(BigMachine m) {
        return m.isServerRack();
    }

    public ItemStackHandler drives() {
        return drives;
    }

    public int slots() {
        return drives.getSlots();
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int value) {
        priority = Mth.clamp(value, 0, MAX_PRIORITY);
    }

    public boolean online() {
        return online;
    }

    public int powerUse() {
        int use = machine.energy == null ? 0 : machine.energy.use();
        for (int i = 0; i < drives.getSlots(); i++) {
            if (drives.getStackInSlot(i).getItem() instanceof DriveItem d) use += d.limits().fePerTick();
            else if (drives.getStackInSlot(i).getItem() instanceof ComputeModuleItem m) use += m.values().fePerTick();
        }
        return use;
    }

    public boolean tick(ServerLevel level, BlockPos pos, boolean formed, boolean enabled) {
        claimDrives(level, pos);
        int use = powerUse();
        boolean on = formed && enabled && energy.getEnergyStored() >= use;
        if (on) energy.drain(use);
        if (on != online) codesDirty = true;
        online = on;
        for (int i = 0; i < active.length; i++) {
            if (active[i] > 0 && --active[i] == 0) codesDirty = true;
        }
        return online;
    }

    private void claimDrives(ServerLevel level, BlockPos pos) {
        DriveStorage store = null;
        for (int i = 0; i < recheck.length; i++) {
            if (!recheck[i]) continue;
            recheck[i] = false;
            if (store == null) store = DriveStorage.get(level.getServer());
            DriveStorage.Seat here = new DriveStorage.Seat(level.dimension(), pos.immutable(), i);
            ItemStack s = drives.getStackInSlot(i);
            UUID id = s.getItem() instanceof DriveItem ? DriveItem.id(s) : null;
            if (seated[i] != null && !seated[i].equals(id)) store.release(seated[i], here);
            seated[i] = id;
            if (id == null) continue;
            DriveStorage.Seat other = store.seat(id);
            if (other != null && !other.equals(here) && holds(level.getServer(), other, id)) {
                id = UUID.randomUUID();
                s.set(ModDataComponents.DRIVE_ID.get(), id);
                s.remove(ModDataComponents.DRIVE_FILL.get());
                seated[i] = id;
                codesDirty = true;
                changed.run();
            }
            store.seat(id, here);
        }
    }

    private static boolean holds(MinecraftServer server, DriveStorage.Seat seat, UUID id) {
        ServerLevel level = server.getLevel(seat.dim());
        if (level == null) return false;
        if (!level.isLoaded(seat.pos())) return true;
        if (!(level.getBlockEntity(seat.pos()) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be) || be.rack() == null) return false;
        ServerRack r = be.rack();
        if (seat.slot() >= r.slots()) return false;
        ItemStack s = r.drives.getStackInSlot(seat.slot());
        return s.getItem() instanceof DriveItem && id.equals(s.get(ModDataComponents.DRIVE_ID.get()));
    }

    public void releaseAll(ServerLevel level, BlockPos pos) {
        DriveStorage store = DriveStorage.get(level.getServer());
        for (int i = 0; i < seated.length; i++) {
            if (seated[i] != null) store.release(seated[i], new DriveStorage.Seat(level.dimension(), pos.immutable(), i));
            seated[i] = null;
            recheck[i] = true;
        }
    }

    public void touch(int slot) {
        if (active[slot] == 0) codesDirty = true;
        active[slot] = ACTIVE_TICKS;
    }

    @Nullable
    public DriveContents contents(MinecraftServer server, int slot) {
        ItemStack s = drives.getStackInSlot(slot);
        if (!(s.getItem() instanceof DriveItem)) return null;
        return DriveStorage.get(server).contents(DriveItem.id(s));
    }

    @Nullable
    public StorageValues.Drive limits(int slot) {
        return drives.getStackInSlot(slot).getItem() instanceof DriveItem d ? d.limits() : null;
    }

    public void updateFill(MinecraftServer server, int slot) {
        DriveContents c = contents(server, slot);
        if (c == null) return;
        ItemStack s = drives.getStackInSlot(slot);
        DriveItem.Fill f = new DriveItem.Fill(c.total(), c.types());
        if (!f.equals(s.get(ModDataComponents.DRIVE_FILL.get()))) {
            s.set(ModDataComponents.DRIVE_FILL.get(), f);
            codesDirty = true;
        }
    }

    private int code(int slot) {
        ItemStack s = drives.getStackInSlot(slot);
        if (cards) return PatternCardItem.pattern(s) == null ? 0 : 1;
        if (s.getItem() instanceof ComputeModuleItem) return COMPUTE | (online ? 16 : 0);
        if (!(s.getItem() instanceof DriveItem d)) return 0;
        int c = d.tier;
        DriveItem.Fill f = s.get(ModDataComponents.DRIVE_FILL.get());
        StorageValues.Drive lim = d.limits();
        if (f != null && (f.used() >= lim.items() * FULL || f.types() >= lim.types())) c |= 8;
        if (active[slot] > 0 && online) c |= 16;
        return c;
    }

    @Nullable
    public int[] syncDue() {
        if (!codesDirty) return null;
        codesDirty = false;
        int[] n = new int[drives.getSlots()];
        for (int i = 0; i < n.length; i++) n[i] = code(i);
        if (Arrays.equals(n, codes)) return null;
        codes = n;
        return n;
    }

    public int[] codes() {
        return codes;
    }

    public void readCodes(int[] c) {
        codes = Arrays.copyOf(c, drives.getSlots());
    }

    public static int tier(int code) {
        return code & 7;
    }

    public static boolean full(int code) {
        return (code & 8) != 0;
    }

    public static boolean busy(int code) {
        return (code & 16) != 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.put("drives", drives.serializeNBT(registries));
        t.putInt("priority", priority);
        return t;
    }

    public void load(CompoundTag t, HolderLookup.Provider registries) {
        int size = drives.getSlots();
        if (t.contains("drives")) drives.deserializeNBT(registries, t.getCompound("drives"));
        if (drives.getSlots() != size) {
            ItemStack[] old = new ItemStack[drives.getSlots()];
            for (int i = 0; i < old.length; i++) old[i] = drives.getStackInSlot(i);
            drives.setSize(size);
            for (int i = 0; i < Math.min(size, old.length); i++) drives.setStackInSlot(i, old[i]);
        }
        priority = Mth.clamp(t.getInt("priority"), 0, MAX_PRIORITY);
        codesDirty = true;
        Arrays.fill(recheck, true);
    }
}
