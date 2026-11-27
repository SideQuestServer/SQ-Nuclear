package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.assembler.EquipmentRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.particle.ParticleClassCardItem;
import com.sqc.nuclearsq.particle.ParticleClasses;
import com.sqc.nuclearsq.particle.ParticleUpgradeItem;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.HeavyAssemblyRegistry;
import com.sqc.nuclearsq.registry.EquipmentRegistry;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;
import com.sqc.nuclearsq.spent.SpentFuel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class BigCrafter {
    public static final int GRID = HeavyAssemblyRecipe.SIZE * HeavyAssemblyRecipe.SIZE, ASM_OUT = GRID, PLANT_OUTPUTS = 4;
    public static final int SMALL_GRID = 9, SMALL_OUT = SMALL_GRID;
    public static final int OK = 0, INCOMPLETE = 1, NO_INPUT = 2, NEED_MORE = 3, OUTPUT_FULL = 4, NO_POWER = 5, OFF = 6, NO_WATER = 7,
            NO_FEED = 8, SHALLOW = 9, NO_ACID = 10, NO_AUX = 11, NO_ARM = 12;
    public static final int SHAFTS = 9;

    public interface RackView {
        int EMPTY = 0, HOT = 1, COOLED = 2, FRESH = 3;

        int state(int shaft);

        boolean inPlace(int shaft);

        void cooled(int shaft);
    }

    private final BigMachine machine;
    @Nullable
    private final ItemStackHandler items;
    @Nullable
    private final MachineEnergy energy;
    @Nullable
    private final FluidTank water;
    private final Map<String, FluidTank> tanks;
    private final int[] cool = new int[SHAFTS];
    @Nullable
    private RackView rackView;
    private int column;
    private int progress, maxProgress, needed, energyPerTick, status = INCOMPLETE, timer;
    private int burn, burnTotal;
    boolean dirty = true;
    private boolean loaded;
    @Nullable
    private HeavyAssemblyRecipe heavy;
    @Nullable
    private AssemblyRecipe small;
    @Nullable
    private int[] offset;
    private ItemStack pending = ItemStack.EMPTY;
    private RecipeHolder<SmeltingRecipe> smeltFrom;
    private RecipeHolder<ReprocessingRecipe> smeltAs;
    public static final int KILN_SMELT_FE = 300, BLAST_SMELT_FE = 3600;
    private static final Map<net.minecraft.world.item.crafting.RecipeManager, Map<net.minecraft.world.item.Item, java.util.Optional<RecipeHolder<ReprocessingRecipe>>>> BLAST =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static final float OWN_XP = 0.5f;
    private float xp;

    public ItemStack takePending() {
        ItemStack out = pending;
        pending = ItemStack.EMPTY;
        return out;
    }
    private int lastClass = -1, cycles;
    private ItemStack lastResult = ItemStack.EMPTY;
    private int syncWork, syncTicks;
    private boolean syncNow;
    private float rate;
    private long syncTime = -1;

    BigCrafter(BigMachine machine, @Nullable ItemStackHandler items, @Nullable MachineEnergy energy, Map<String, FluidTank> tanks) {
        this.machine = machine;
        this.items = items;
        this.energy = energy;
        this.tanks = tanks;
        this.water = tanks.get("water_in");
        setTier(0);
    }

    void setRack(RackView view) { rackView = view; }

    private com.sqc.nuclearsq.module.ModuleBonus modules = com.sqc.nuclearsq.module.ModuleBonus.NONE;
    private int workAcc;

    void setModules(com.sqc.nuclearsq.module.ModuleBonus b) { modules = b; }

    public com.sqc.nuclearsq.module.ModuleBonus modules() { return modules; }

    public static boolean usesModules(BigMachine m) {
        return handles(m) && m != BigMachine.COAL_GENERATOR && m != BigMachine.SPENT_FUEL_RACK;
    }

    private int tier;
    private int tierSpeed = 100, tierPower = 100;

    public void setTier(int stage) {
        if (!ProductionTier.handles(machine)) return;
        tier = stage;
        tierSpeed = ProductionTier.speedPct(machine, stage);
        tierPower = ProductionTier.powerPct(machine, stage);
    }

    public int tier() { return tier; }

    public int tierSpeedPct() { return tierSpeed; }

    private int fe(int base) {
        if (base <= 0) return base;
        int e = modules.energy(base);
        return tierPower == 100 ? e : (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) e * tierPower / 100));
    }

    private int steps() {
        workAcc += (int) ((long) modules.speedPct() * tierSpeed / 100);
        int n = workAcc / 100;
        workAcc %= 100;
        return n;
    }

    public static boolean handles(BigMachine m) {
        return m == BigMachine.HEAVY_ASSEMBLER || m == BigMachine.REACTOR_ASSEMBLER || m == BigMachine.COMPUTE_ASSEMBLER || m == BigMachine.COAL_GENERATOR
                || m == BigMachine.SPENT_FUEL_RACK || m == BigMachine.PARTICLE_ASSEMBLER || m == BigMachine.SMALL_PARTS_ASSEMBLER
                || m == BigMachine.MICRO_ASSEMBLER || m == BigMachine.CLEAN_ROOM || m == BigMachine.EQUIPMENT_ASSEMBLER || m == BigMachine.CNC_MILL || m == BigMachine.SOLDER_MACHINE || m.recipeKind() != null;
    }

    private int runs;
    @Nullable
    private net.minecraft.resources.ResourceLocation current;
    private boolean advance;
    @Nullable
    private net.minecraft.resources.ResourceLocation lock;
    private int lockIndex;

    public int lockIndex() { return lockIndex; }

    @Nullable
    public net.minecraft.resources.ResourceLocation lockId() { return lock; }

    public void setLock(Level level, int index) {
        List<net.minecraft.resources.ResourceLocation> ids = com.sqc.nuclearsq.progression.MachineRecipes.ids(machine, level.getRecipeManager());
        lock = index > 0 && index <= ids.size() ? ids.get(index - 1) : null;
        lockIndex = lock == null ? 0 : index;
        dirty = true;
    }

    private boolean blocked(net.minecraft.resources.ResourceLocation id) {
        return lock != null && !lock.equals(id);
    }

    public int takeRuns() {
        int n = runs;
        runs = 0;
        return n;
    }

    public int progress() { return progress; }
    public int maxProgress() { return maxProgress; }
    public int needed() { return needed; }
    public int status() { return status; }
    public int energyPerTick() { return energyPerTick; }

    int tick(Level level, BlockPos pos, boolean formed, boolean enabled) {
        where = pos;
        if (lock != null && lockIndex == 0) {
            int i = com.sqc.nuclearsq.progression.MachineRecipes.ids(machine, level.getRecipeManager()).indexOf(lock);
            if (i < 0) lock = null;
            lockIndex = i + 1;
            dirty = true;
        }
        if (machine == BigMachine.SPENT_FUEL_RACK) return rack(formed, enabled);
        if (machine == BigMachine.COAL_GENERATOR) return generate(formed, enabled);
        if (machine == BigMachine.REACTOR_ASSEMBLER || machine == BigMachine.COMPUTE_ASSEMBLER) return assembleSmall(level, formed, enabled);
        if (machine == BigMachine.SMALL_PARTS_ASSEMBLER || machine == BigMachine.MICRO_ASSEMBLER || machine == BigMachine.CLEAN_ROOM
                || machine == BigMachine.EQUIPMENT_ASSEMBLER || machine == BigMachine.CNC_MILL || machine == BigMachine.SOLDER_MACHINE) return assembleParts(level, formed, enabled);
        if (machine == BigMachine.PARTICLE_ASSEMBLER) return particle(level, pos, formed, enabled);
        return machine == BigMachine.HEAVY_ASSEMBLER ? assemble(level, formed, enabled) : reprocess(level, pos, formed, enabled);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public static final List<String> COAL_CARDS = List.of("output", "efficiency", "buffer");

    private int generate(boolean formed, boolean enabled) {
        int up = 8000 + items.getStackInSlot(1).getCount() * machine.extra("card_output_max_pct");
        int eff = 8000 + items.getStackInSlot(2).getCount() * machine.extra("card_efficiency_max_pct");
        int fe = Math.max(1, machine.extra("fe_per_tick") * up / 8000);
        needed = 0;
        for (int i = 4; i < items.getSlots(); i++) {
            ItemStack s = items.extractItem(i, 64, true);
            int moved = s.getCount() - items.insertItem(0, s, true).getCount();
            if (moved > 0) items.insertItem(0, items.extractItem(i, moved, false), false);
            needed += items.getStackInSlot(i).getCount();
        }
        ItemStack fuel = items.getStackInSlot(0);
        energyPerTick = fe;
        if (!formed) status = INCOMPLETE;
        else if (!enabled) status = OFF;
        else if (energy.getEnergyStored() > 0) status = OUTPUT_FULL;
        else if (burn <= 0 && !isFuel(fuel)) status = NO_INPUT;
        else status = OK;
        if (status == OK && burn <= 0) {
            burnTotal = burn = (int) Math.max(1, (long) fuel.getBurnTime(RecipeType.SMELTING) * Math.max(1, machine.extra("burn_pct")) * eff / (100L * up));
            ItemStack rest = fuel.getCraftingRemainingItem();
            items.extractItem(0, 1, false);
            if (!rest.isEmpty() && items.getStackInSlot(0).isEmpty()) items.setStackInSlot(0, rest);
        }
        maxProgress = burnTotal;
        progress = burn;
        if (status != OK) return 0;
        burn--;
        energy.add(fe);
        return 1;
    }

    public int coolTicks() {
        return Math.max(1, machine.extra("cool_ticks"));
    }

    public int coolPercent(int shaft) {
        if (rackView == null) return 0;
        int st = rackView.state(shaft);
        return st == RackView.COOLED ? 100 : st == RackView.HOT ? Math.min(100, cool[shaft] * 100 / coolTicks()) : 0;
    }

    public int flow() {
        return Math.max(1, machine.extra("flow_mb_t"));
    }

    public int columnNeeded() {
        return Math.max(1, machine.extra("column_min"));
    }

    public int column() { return column; }

    void setColumn(int blocks) { column = blocks; }

    public boolean feedOk() {
        return waterAmount() >= flow();
    }

    public boolean submerged(boolean formed) {
        return formed && feedOk() && column >= columnNeeded();
    }

    private int rack(boolean formed, boolean enabled) {
        int hot = 0, sum = 0, done = coolTicks();
        for (int i = 0; i < SHAFTS; i++) {
            if (rackView == null || rackView.state(i) != RackView.HOT) {
                cool[i] = 0;
            } else {
                hot++;
                sum += coolPercent(i);
            }
        }
        maxProgress = 100;
        progress = hot == 0 ? 0 : sum / hot;
        needed = 0;
        energyPerTick = 0;
        if (!formed) status = INCOMPLETE;
        else if (!enabled) status = OFF;
        else if (hot == 0) status = NO_INPUT;
        else if (!feedOk()) status = NO_FEED;
        else if (column < columnNeeded()) status = SHALLOW;
        else status = OK;
        if (status != OK) return 0;
        boolean drained = false;
        for (int i = 0; i < SHAFTS; i++) {
            if (rackView.state(i) != RackView.HOT || !rackView.inPlace(i)) continue;
            if (!drained) water.drain(flow(), IFluidHandler.FluidAction.EXECUTE);
            drained = true;
            if (++cool[i] >= done) {
                rackView.cooled(i);
                cool[i] = 0;
            }
        }
        return 1;
    }

    public int waterAmount() { return water == null ? 0 : water.getFluidAmount(); }
    public int waterCapacity() { return water == null ? 0 : water.getCapacity(); }

    double poolRadiation(boolean formed) {
        if (rackView == null) return 0;
        boolean wet = submerged(formed);
        double sv = 0;
        for (int i = 0; i < SHAFTS; i++) {
            if (rackView.state(i) == RackView.HOT) sv += (1.0 - coolPercent(i) / 100.0) * (wet ? SpentFuel.POOL_SV_H : SpentFuel.DRY_SV_H);
        }
        return sv;
    }

    private HeavyAssemblyRecipe.Grid grid() {
        List<ItemStack> l = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) l.add(items.getStackInSlot(i));
        return new HeavyAssemblyRecipe.Grid(l);
    }

    private void findHeavy(Level level) {
        HeavyAssemblyRecipe.Grid g = grid();
        HeavyAssemblyRecipe found = null;
        int[] off = null;
        for (RecipeHolder<HeavyAssemblyRecipe> h : level.getRecipeManager().getAllRecipesFor(HeavyAssemblyRegistry.TYPE.get())) {
            if (blocked(h.id())) continue;
            off = h.value().offset(g);
            if (off != null) {
                found = h.value();
                break;
            }
        }
        if (found != heavy && !loaded) progress = 0;
        loaded = false;
        heavy = found;
        offset = off;
        dirty = false;
    }

    private int assemble(Level level, boolean formed, boolean enabled) {
        if (dirty) findHeavy(level);
        HeavyAssemblyRecipe r = heavy;
        maxProgress = r == null ? 0 : r.time();
        energyPerTick = r == null ? 0 : fe(r.energyPerTick());
        needed = 0;
        if (!formed) status = INCOMPLETE;
        else if (!enabled) status = OFF;
        else if (r == null) status = NO_INPUT;
        else if (!items.insertItem(ASM_OUT, r.result().copy(), true).isEmpty()) status = OUTPUT_FULL;
        else if (energy.getEnergyStored() < energyPerTick) status = NO_POWER;
        else status = OK;
        if (status != OK) {
            if (r == null) progress = 0;
            return 0;
        }
        energy.drain(energyPerTick);
        if ((progress += steps()) >= r.time()) {
            progress = 0;
            for (int slot : r.usedSlots(offset)) items.extractItem(slot, 1, false);
            items.insertItem(ASM_OUT, r.result().copy(), false);
            dirty = true;
        }
        return 1;
    }

    private List<ItemStack> smallGrid() {
        List<ItemStack> l = new ArrayList<>(SMALL_GRID);
        for (int i = 0; i < SMALL_GRID; i++) l.add(items.getStackInSlot(i));
        return l;
    }

    private void consume(@Nullable int[][] plan) {
        if (plan == null) return;
        for (int[] need : plan) {
            for (int i = 0; i < SMALL_GRID && i < need.length; i++) if (need[i] > 0) items.extractItem(i, need[i], false);
        }
    }

    private int assembleSmall(Level level, boolean formed, boolean enabled) {
        AssemblyRecipe r = !dirty ? small : lock == null ? AssemblyRecipe.find(level.getRecipeManager(), smallGrid())
                : level.getRecipeManager().byKey(lock).map(RecipeHolder::value).filter(v -> v instanceof AssemblyRecipe a && a.plan(smallGrid()) != null)
                .map(v -> (AssemblyRecipe) v).orElse(null);
        if (dirty) {
            if (r != small && !loaded) progress = 0;
            loaded = false;
            small = r;
            dirty = false;
        }
        int fe = fe(smallEnergy(r)), time = r != null && r.time() > 0 ? r.time() : Math.max(1, machine.extra("craft_ticks"));
        return craft(formed, enabled, r == null ? null : r.result(), fe, time, () -> r.plan(smallGrid()));
    }

    private int craft(boolean formed, boolean enabled, @Nullable ItemStack result, int fe, int time, java.util.function.Supplier<int[][]> plan) {
        maxProgress = result == null ? 0 : time;
        energyPerTick = fe;
        needed = 0;
        if (!formed) status = INCOMPLETE;
        else if (!enabled) status = OFF;
        else if (result == null) status = armBlocked ? NO_ARM : NO_INPUT;
        else if (!items.insertItem(SMALL_OUT, result.copy(), true).isEmpty()) status = OUTPUT_FULL;
        else if (energy.getEnergyStored() < fe) status = NO_POWER;
        else status = OK;
        if (status != OK) {
            if (result == null || status == OUTPUT_FULL) progress = 0;
            return 0;
        }
        energy.drain(fe);
        if ((progress += steps()) >= time) {
            progress = Math.min(progress - time, time - 1);
            consume(plan.get());
            items.insertItem(SMALL_OUT, result.copy(), false);
            dirty = true;
        }
        return 1;
    }

    private int smallEnergy(@Nullable AssemblyRecipe r) {
        if (r != null && r.energyPerTick() > 0) return r.energyPerTick();
        boolean reactor = r != null && r.result().is(AssemblyRecipe.REACTOR_PARTS);
        return Math.max(1, machine.extra(reactor ? "fe_per_tick_reactor" : "fe_per_tick"));
    }

    @Nullable
    private SmallPartsRecipe parts;

    @Nullable
    public SmallPartsRecipe partsRecipe() { return parts; }

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> MICRO_PRODUCTS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "micro_products"));

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> ARM_PRODUCTS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "arm_products"));

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> CLEAN_ROOM_PRODUCTS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "clean_room_products"));

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> CNC_PRODUCTS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "cnc_products"));

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> CNC_SHARED = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "cnc_shared"));

    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> SOLDER_PRODUCTS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "solder_products"));

    private boolean armBlocked;
    @Nullable
    private BlockPos where;

    private boolean armDocked(Level level) {
        if (where == null) return false;
        for (BlockPos p : BlockPos.betweenClosed(where.offset(-7, -3, -7), where.offset(7, 3, 7))) {
            if (!(level.getBlockEntity(p) instanceof com.sqc.nuclearsq.robotics.RobotArmBlockEntity arm)) continue;
            for (BlockPos t : arm.targets()) if (Math.abs(t.getX() - where.getX()) <= 2 && Math.abs(t.getZ() - where.getZ()) <= 2
                    && t.getY() - where.getY() >= 0 && t.getY() - where.getY() <= 2) return true;
        }
        return false;
    }

    @Nullable
    private SmallPartsRecipe findParts(Level level) {
        armBlocked = false;
        SmallPartsRecipe.Input in = new SmallPartsRecipe.Input(smallGrid());
        if (machine == BigMachine.EQUIPMENT_ASSEMBLER) {
            for (RecipeHolder<EquipmentRecipe> h : level.getRecipeManager().getAllRecipesFor(EquipmentRegistry.TYPE.get()))
                if (!blocked(h.id()) && h.value().base().matches(in, level)) return h.value().base();
            return null;
        }
        boolean micro = machine == BigMachine.MICRO_ASSEMBLER, clean = machine == BigMachine.CLEAN_ROOM, cnc = machine == BigMachine.CNC_MILL, solder = machine == BigMachine.SOLDER_MACHINE;
        SmallPartsRecipe best = null;
        for (RecipeHolder<SmallPartsRecipe> h : level.getRecipeManager().getAllRecipesFor(SmallPartsRegistry.TYPE.get())) {
            boolean isMicro = h.value().result().is(MICRO_PRODUCTS), isClean = h.value().result().is(CLEAN_ROOM_PRODUCTS), isCnc = h.value().result().is(CNC_PRODUCTS);
            if (micro != isMicro || clean != isClean) continue;
            if (cnc ? !isCnc && !h.value().result().is(CNC_SHARED) : isCnc) continue;
            if (solder != h.value().result().is(SOLDER_PRODUCTS) || blocked(h.id())) continue;
            if (best != null && SmallPartsRecipe.SPECIFIC_FIRST.compare(h.value(), best) >= 0) continue;
            if (h.value().matches(in, level)) {
                if (clean && !armDocked(level)) {
                    armBlocked = true;
                    continue;
                }
                best = h.value();
            }
        }
        return best;
    }

    private int assembleParts(Level level, boolean formed, boolean enabled) {
        if (!dirty && armBlocked && level.getGameTime() % 40 == 0) dirty = true;
        if (dirty) {
            SmallPartsRecipe r = findParts(level);
            if (r != parts && !loaded) progress = 0;
            loaded = false;
            parts = r;
            dirty = false;
        }
        SmallPartsRecipe r = parts;
        int fe = fe(r != null && r.energyPerTick() > 0 ? r.energyPerTick() : Math.max(1, machine.extra("fe_per_tick")));
        int time = r != null && r.time() > 0 ? r.time() : Math.max(1, machine.extra("craft_ticks"));
        return craft(formed, enabled, r == null ? null : r.result(), fe, time, () -> r.plan(smallGrid()));
    }

    public int cardTier() {
        return ParticleClassCardItem.tierOf(items.getStackInSlot(ParticleClasses.CARD));
    }

    public int[] upgradeLevels() {
        ParticleClasses pc = ParticleClasses.get();
        int[] lv = new int[ParticleClasses.Upgrade.values().length];
        for (ParticleClasses.Upgrade u : ParticleClasses.Upgrade.values()) {
            ItemStack s = items.getStackInSlot(u.slot());
            lv[u.ordinal()] = ParticleUpgradeItem.kindOf(s) == u ? Math.min(s.getCount(), pc.max(u)) : 0;
        }
        return lv;
    }

    public int particleTime() {
        return ParticleClasses.get().time(Math.max(1, machine.extra("cycle_ticks")), upgradeLevels());
    }

    public int particleEnergy() {
        return ParticleClasses.get().energy(Math.max(1, machine.extra("fe_per_tick")), upgradeLevels(), cardTier());
    }

    private int particle(Level level, BlockPos pos, boolean formed, boolean enabled) {
        syncTicks++;
        if (!pending.isEmpty()) pending = insertParticle(items, pending);
        int fe = fe(particleEnergy()), time = particleTime();
        maxProgress = time;
        energyPerTick = fe;
        needed = 0;
        if (!formed) status = INCOMPLETE;
        else if (!enabled) status = OFF;
        else if (!pending.isEmpty()) status = OUTPUT_FULL;
        else if (energy.getEnergyStored() < fe) status = NO_POWER;
        else status = OK;
        if (status != OK) return 0;
        energy.drain(fe);
        syncWork++;
        if (progress == 0) syncNow = true;
        if ((progress += steps()) >= time) {
            progress = 0;
            ParticleClasses.Roll roll = ParticleClasses.get().roll(level.random, upgradeLevels(), cardTier());
            if (roll != null) {
                lastClass = roll.cls();
                lastResult = roll.stack().copy();
                pending = insertParticle(items, roll.stack());
            }
            cycles++;
            syncNow = true;
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.6f);
        }
        return 1;
    }

    private static ItemStack insertParticle(ItemStackHandler h, ItemStack stack) {
        for (int i = 0; i < ParticleClasses.OUTPUTS && !stack.isEmpty(); i++) stack = h.insertItem(i, stack, false);
        return stack;
    }

    public int lastClass() { return lastClass; }
    public ItemStack lastResult() { return lastResult; }
    public int cycles() { return cycles; }

    boolean particleSyncDue() {
        return syncNow || (syncTicks >= 20 && syncWork > 0);
    }

    CompoundTag particleSync(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("p", progress);
        t.putInt("max", maxProgress);
        t.putFloat("rate", syncTicks <= 0 ? 0f : Math.min(1f, (float) syncWork / syncTicks));
        t.putInt("cycles", cycles);
        t.putInt("cls", lastClass);
        if (!lastResult.isEmpty()) t.put("last", lastResult.save(registries));
        syncNow = false;
        syncWork = 0;
        syncTicks = 0;
        return t;
    }

    void readParticleSync(CompoundTag t, net.minecraft.core.HolderLookup.Provider registries) {
        progress = t.getInt("p");
        maxProgress = t.getInt("max");
        rate = t.getFloat("rate");
        cycles = t.getInt("cycles");
        lastClass = t.getInt("cls");
        lastResult = t.contains("last") ? ItemStack.parseOptional(registries, t.getCompound("last")) : ItemStack.EMPTY;
        syncTime = -1;
    }

    public float phase(long gameTime, float partialTick) {
        if (maxProgress <= 0) return 0f;
        if (syncTime < 0) syncTime = gameTime;
        float p = progress + rate * (gameTime - syncTime + partialTick);
        return Math.min(p, maxProgress) / maxProgress;
    }

    @Nullable
    public static ReprocessingRecipe findReprocessing(@Nullable Level level, BigMachine machine, ItemStack stack) {
        if (level == null || stack.isEmpty() || machine.recipeKind() == null) return null;
        for (RecipeHolder<ReprocessingRecipe> h : level.getRecipeManager().getAllRecipesFor(machine.recipeKind().type())) {
            if (h.value().accepts(stack)) return h.value();
        }
        if (!smelts(level, machine, stack)) return null;
        RecipeHolder<ReprocessingRecipe> s = machine == BigMachine.BLAST_FURNACE ? blast(level, stack)
                : level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).map(h -> asKiln(level, h)).orElse(null);
        return s == null ? null : s.value();
    }

    private static boolean smelts(Level level, BigMachine machine, ItemStack stack) {
        return (machine == BigMachine.KILN || machine == BigMachine.BLAST_FURNACE) && !stack.isEmpty();
    }

    @Nullable
    private static RecipeHolder<ReprocessingRecipe> blast(Level level, ItemStack stack) {
        return BLAST.computeIfAbsent(level.getRecipeManager(), m -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(stack.getItem(), i -> java.util.Optional.ofNullable(asBlast(level, stack))).orElse(null);
    }

    @Nullable
    private static RecipeHolder<ReprocessingRecipe> asBlast(Level level, ItemStack stack) {
        ItemStack out = ingotFrom(level, stack);
        int count = 8, n = 8;
        if (out.isEmpty()) {
            ItemStack part = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                            net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(stack.copyWithCount(1))), level)
                    .map(h -> h.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            if (part.getCount() < 2) return null;
            out = ingotFrom(level, part);
            count = 1;
            n = part.getCount();
        }
        if (out.isEmpty()) return null;
        int total = Math.min(64, out.getCount() * n);
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new RecipeHolder<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", "blast_auto/" + key.getNamespace() + "/" + key.getPath()),
                new ReprocessingRecipe(net.minecraft.world.item.crafting.Ingredient.of(stack.getItem()), count, 25 * total, BLAST_SMELT_FE,
                        List.of(new ReprocessingRecipe.Result(out.copyWithCount(total), 1f)), ReprocessingRecipe.Kind.BLAST_SMELTING));
    }

    private static ItemStack ingotFrom(Level level, ItemStack stack) {
        SingleRecipeInput in = new SingleRecipeInput(stack);
        ItemStack a = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, in, level)
                .map(h -> h.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
        if (a.is(net.neoforged.neoforge.common.Tags.Items.INGOTS)) return a;
        ItemStack b = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, in, level)
                .map(h -> h.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
        return b.is(net.neoforged.neoforge.common.Tags.Items.INGOTS) ? b : ItemStack.EMPTY;
    }

    public static boolean auxFor(@Nullable Level level, BigMachine machine, ItemStack main, ItemStack stack) {
        if (!findAux(level, machine, stack)) return false;
        if (machine != BigMachine.KILN || findReprocessing(level, machine, stack) == null) return true;
        for (RecipeHolder<ReprocessingRecipe> h : level.getRecipeManager().getAllRecipesFor(machine.recipeKind().type())) {
            if (h.value().accepts(main) && h.value().acceptsAux(stack)) return true;
        }
        return false;
    }

    @Nullable
    private static RecipeHolder<ReprocessingRecipe> asKiln(Level level, RecipeHolder<SmeltingRecipe> h) {
        ItemStack out = h.value().getResultItem(level.registryAccess());
        if (out.isEmpty() || h.value().getIngredients().isEmpty()) return null;
        return new RecipeHolder<>(h.id(), new ReprocessingRecipe(h.value().getIngredients().get(0), 1, Math.max(1, h.value().getCookingTime() * 7 / 10),
                KILN_SMELT_FE, List.of(new ReprocessingRecipe.Result(out.copy(), 1f)), ReprocessingRecipe.Kind.KILN_FIRING));
    }

    @Nullable
    private ReprocessingRecipe pickReprocessing(Level level, ItemStack in, boolean fromAux) {
        if (in.isEmpty() || machine.recipeKind() == null) return null;
        ReprocessingRecipe first = null;
        List<RecipeHolder<ReprocessingRecipe>> ready = new ArrayList<>();
        int best = Integer.MIN_VALUE;
        for (RecipeHolder<ReprocessingRecipe> h : level.getRecipeManager().getAllRecipesFor(machine.recipeKind().type())) {
            ReprocessingRecipe c = h.value();
            if (!c.accepts(in) || fromAux && c.hasAux() || blocked(h.id())) continue;
            if (first == null || c.hasAux() && !first.hasAux()) first = c;
            if (in.getCount() >= c.count() && auxOk(c) && missingFluid(c) == null) {
                if (c.priority() > best) {
                    best = c.priority();
                    ready.clear();
                }
                if (c.priority() == best) ready.add(h);
            }
        }
        if (first == null && !fromAux && lock == null && smelts(level, machine, in)) {
            if (machine == BigMachine.BLAST_FURNACE) smeltAs = blast(level, in);
            else {
                RecipeHolder<SmeltingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(in), level, smeltFrom).orElse(null);
                if (found != smeltFrom) {
                    smeltFrom = found;
                    smeltAs = found == null ? null : asKiln(level, found);
                }
            }
            if (smeltAs == null) return null;
            current = smeltAs.id();
            advance = false;
            return smeltAs.value();
        }
        if (ready.isEmpty()) return fromAux ? null : first;
        int at = -1;
        for (int i = 0; i < ready.size(); i++) if (ready.get(i).id().equals(current)) at = i;
        RecipeHolder<ReprocessingRecipe> pick = at < 0 ? ready.get(0) : ready.get(advance ? (at + 1) % ready.size() : at);
        current = pick.id();
        advance = false;
        return pick.value();
    }

    private int reprocess(Level level, BlockPos pos, boolean formed, boolean enabled) {
        if (machine == BigMachine.LEACHING_PLANT) recycleOwnInput(level);
        ItemStack in = items.getStackInSlot(0);
        int src = 0;
        if (in.isEmpty() && machine.hasAuxSlot() && items.getSlots() > ReprocessingRecipe.AUX_SLOT) {
            in = items.getStackInSlot(ReprocessingRecipe.AUX_SLOT);
            src = ReprocessingRecipe.AUX_SLOT;
        }
        if (timer-- <= 0) {
            timer = 20;
            double sv = machine == BigMachine.REPROCESSING_PLANT || machine == BigMachine.WEAPONS_PROCESSING ? in.getCount() * com.sqc.nuclearsq.radiation.RadiationValues.RESIDUE_SV_H : 0;
            if (sv > 0 && level instanceof ServerLevel sl) RadiationManager.emit(sl, pos, sv);
        }
        ReprocessingRecipe r = pickReprocessing(level, in, src == ReprocessingRecipe.AUX_SLOT);
        needed = r == null ? 0 : r.count();
        maxProgress = r == null ? 0 : r.time();
        energyPerTick = r == null ? 0 : fe(r.energyPerTick());
        if (!formed) status = INCOMPLETE;
        else if (!enabled) status = OFF;
        else if (r == null) status = NO_INPUT;
        else if (in.getCount() < r.count()) status = NEED_MORE;
        else if (!auxOk(r)) {
            status = NO_AUX;
            needed = r.auxCount();
        } else if (missingFluid(r) != null) status = missingFluid(r).contains("water") || missingFluid(r).contains("primary") ? NO_WATER : NO_ACID;
        else if (!fits(r) || !fluidFits(r)) status = OUTPUT_FULL;
        else if (energy.getEnergyStored() < energyPerTick) status = NO_POWER;
        else status = OK;
        if (status != OK) {
            if (r == null || status == NEED_MORE || status == NO_AUX) progress = 0;
            return 0;
        }
        energy.drain(energyPerTick);
        if ((progress += steps()) >= r.time()) {
            progress = 0;
            advance = true;
            items.extractItem(src, r.count(), false);
            if (r.hasAux()) items.extractItem(ReprocessingRecipe.AUX_SLOT, r.auxCount(), false);
            for (ReprocessingRecipe.FluidCost c : r.fluids()) {
                FluidTank tank = tanks.get(c.port());
                if (tank != null) tank.drain(c.amount(), IFluidHandler.FluidAction.EXECUTE);
            }
            for (ReprocessingRecipe.Result res : r.results()) {
                float ch = machine == BigMachine.ORE_MILL && res.chance() == 0.05f || machine == BigMachine.HAMMER_MILL && res.chance() < 1f ? Math.min(1f, res.chance() * (1 << modules.activeOverclocks())) : res.chance();
                if (ch >= 1f || level.random.nextFloat() < ch) {
                    if (givesXp(machine)) xp += xpFor(level, res.stack()) * res.stack().getCount();
                    insertOutputs(items, res.stack().copy());
                }
            }
            runs++;
            for (ReprocessingRecipe.FluidOut o : r.fluidResults()) {
                FluidTank tank = tanks.get(o.port());
                net.minecraft.world.level.material.Fluid f = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(o.fluid());
                if (tank != null) tank.fill(new net.neoforged.neoforge.fluids.FluidStack(f, o.amount()), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return 1;
    }

    public static boolean givesXp(BigMachine m) {
        return m == BigMachine.KILN || m == BigMachine.BLAST_FURNACE;
    }

    public static float xpFor(Level level, ItemStack out) {
        float best = -1f;
        for (RecipeHolder<SmeltingRecipe> h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING))
            if (h.value().getResultItem(level.registryAccess()).is(out.getItem())) best = Math.max(best, h.value().getExperience());
        return best < 0f ? OWN_XP : best;
    }

    public void dropXp(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        int n = net.minecraft.util.Mth.floor(xp);
        float f = net.minecraft.util.Mth.frac(xp);
        if (f > 0f && level.random.nextFloat() < f) n++;
        xp = 0f;
        if (n > 0) net.minecraft.world.entity.ExperienceOrb.award(level, player.position(), n);
    }

    private void recycleOwnInput(Level level) {
        for (int i = 1; i <= PLANT_OUTPUTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || findReprocessing(level, machine, s) == null) continue;
            ItemStack in = items.getStackInSlot(0);
            if (!in.isEmpty() && !ItemStack.isSameItemSameComponents(in, s)) continue;
            ItemStack rest = items.insertItem(0, s.copy(), false);
            items.setStackInSlot(i, rest);
        }
    }

    private boolean fluidFits(ReprocessingRecipe r) {
        for (ReprocessingRecipe.FluidOut o : r.fluidResults()) {
            FluidTank tank = tanks.get(o.port());
            if (tank == null) return false;
            net.minecraft.world.level.material.Fluid f = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(o.fluid());
            if (tank.fill(new net.neoforged.neoforge.fluids.FluidStack(f, o.amount()), IFluidHandler.FluidAction.SIMULATE) < o.amount()) return false;
        }
        return true;
    }

    private boolean auxOk(ReprocessingRecipe r) {
        if (!r.hasAux()) return true;
        if (items.getSlots() <= ReprocessingRecipe.AUX_SLOT) return false;
        ItemStack aux = items.getStackInSlot(ReprocessingRecipe.AUX_SLOT);
        return r.acceptsAux(aux) && aux.getCount() >= r.auxCount();
    }

    @Nullable
    private String missingFluid(ReprocessingRecipe r) {
        for (ReprocessingRecipe.FluidCost c : r.fluids()) {
            FluidTank tank = tanks.get(c.port());
            if (tank == null || tank.getFluidAmount() < c.amount()) return c.port();
        }
        return null;
    }

    public List<String> fluidTankNames() {
        List<String> out = new ArrayList<>();
        for (BigMachine.Port p : machine.ports) if (machine.showsTank(p) && tanks.containsKey(p.name())) out.add(p.name());
        return out;
    }

    public int fluidAmount(int i) {
        List<String> n = fluidTankNames();
        return i < n.size() ? tanks.get(n.get(i)).getFluidAmount() : 0;
    }

    public int fluidCapacity(int i) {
        List<String> n = fluidTankNames();
        return i < n.size() ? tanks.get(n.get(i)).getCapacity() : 0;
    }

    public static boolean findAux(@Nullable Level level, BigMachine machine, ItemStack stack) {
        if (level == null || stack.isEmpty() || machine.recipeKind() == null || !machine.hasAuxSlot()) return false;
        for (RecipeHolder<ReprocessingRecipe> h : level.getRecipeManager().getAllRecipesFor(machine.recipeKind().type())) {
            if (h.value().acceptsAux(stack)) return true;
        }
        return false;
    }

    private boolean fits(ReprocessingRecipe r) {
        ItemStackHandler copy = new ItemStackHandler(1 + PLANT_OUTPUTS);
        for (int i = 1; i <= PLANT_OUTPUTS; i++) copy.setStackInSlot(i, items.getStackInSlot(i).copy());
        for (ReprocessingRecipe.Result res : r.results()) {
            if (!insertOutputs(copy, res.stack().copy()).isEmpty()) return false;
        }
        return true;
    }

    private static ItemStack insertOutputs(ItemStackHandler h, ItemStack stack) {
        for (int i = 1; i <= PLANT_OUTPUTS && !stack.isEmpty(); i++) stack = h.insertItem(i, stack, false);
        return stack;
    }

    IItemHandler automation(Level level) {
        boolean asm = machine == BigMachine.HEAVY_ASSEMBLER, gen = machine == BigMachine.COAL_GENERATOR,
                small = machine.smallGrid(), part = machine == BigMachine.PARTICLE_ASSEMBLER;
        return new IItemHandler() {
            @Override
            public int getSlots() { return items.getSlots(); }

            @Override
            public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack)) return stack;
                return items.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                boolean out = gen ? slot == 0 && !isFuel(items.getStackInSlot(slot))
                        : part ? slot < ParticleClasses.OUTPUTS
                        : asm ? slot == ASM_OUT : small ? slot == SMALL_OUT : slot >= 1 && slot <= PLANT_OUTPUTS;
                if (out && machine == BigMachine.LEACHING_PLANT && findReprocessing(level, machine, items.getStackInSlot(slot)) != null) out = false;
                return out ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (asm) {
                    ItemStack have = slot < GRID ? items.getStackInSlot(slot) : ItemStack.EMPTY;
                    if (!have.isEmpty()) return ItemStack.isSameItemSameComponents(have, stack);
                    return slot < GRID && pattern(level, slot, stack);
                }
                if (small) return slot < SMALL_GRID;
                if (part) return slot >= ParticleClasses.OUTPUTS && items.isItemValid(slot, stack);
                if (gen) return (slot == 0 || slot >= 4 && slot < 4 + items.getStackInSlot(3).getCount()) && isFuel(stack);
                boolean aux = auxFor(level, machine, items.getStackInSlot(0), stack);
                if (slot == ReprocessingRecipe.AUX_SLOT) return aux;
                return slot == 0 && findReprocessing(level, machine, stack) != null && !aux;
            }
        };
    }

    private boolean pattern(Level level, int slot, ItemStack stack) {
        net.minecraft.world.item.crafting.Recipe<?> r = lock == null ? null : level.getRecipeManager().byKey(lock).map(RecipeHolder::value).orElse(null);
        if (r == null) return false;
        com.sqc.nuclearsq.progression.RecipeSources.Need[] n = com.sqc.nuclearsq.progression.MachineRecipes.needs(r, GRID, level.registryAccess());
        for (int i = 0; i < GRID; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty() && (n[i] == null || !n[i].ingredient().test(s))) return false;
        }
        return n[slot] != null && n[slot].ingredient().test(stack);
    }

    CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("progress", progress);
        if (machine == BigMachine.PARTICLE_ASSEMBLER) {
            if (!pending.isEmpty()) t.put("pending", pending.save(registries));
            t.putInt("cycles", cycles);
        }
        if (lock != null) t.putString("lock", lock.toString());
        if (xp > 0f) t.putFloat("xp", xp);
        if (machine == BigMachine.SPENT_FUEL_RACK) t.putIntArray("cool", cool);
        if (machine == BigMachine.COAL_GENERATOR) {
            t.putInt("burn", burn);
            t.putInt("burn_total", burnTotal);
        }
        return t;
    }

    void load(CompoundTag t, net.minecraft.core.HolderLookup.Provider registries) {
        progress = t.getInt("progress");
        pending = t.contains("pending") ? ItemStack.parseOptional(registries, t.getCompound("pending")) : ItemStack.EMPTY;
        cycles = t.getInt("cycles");
        int[] c = t.getIntArray("cool");
        System.arraycopy(c, 0, cool, 0, Math.min(c.length, cool.length));
        burn = t.getInt("burn");
        burnTotal = t.getInt("burn_total");
        xp = t.getFloat("xp");
        lock = t.contains("lock") ? net.minecraft.resources.ResourceLocation.tryParse(t.getString("lock")) : null;
        lockIndex = 0;
        loaded = true;
        dirty = true;
    }
}
