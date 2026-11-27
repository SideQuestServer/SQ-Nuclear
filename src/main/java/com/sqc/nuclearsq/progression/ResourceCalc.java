package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.progression.RecipeSources.Need;
import com.sqc.nuclearsq.progression.RecipeSources.Source;
import com.sqc.nuclearsq.progression.RecipeSources.Where;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class ResourceCalc {
    public static final int MAX_DEPTH = 24;

    public record Res(@Nullable Item item, @Nullable Fluid fluid) {
        public static Res of(Item item) { return new Res(item, null); }
        public static Res of(Fluid fluid) { return new Res(null, fluid); }
        public boolean isFluid() { return fluid != null; }
        public ItemStack icon() {
            if (item != null) return new ItemStack(item);
            Item b = fluid.getBucket();
            return new ItemStack(b == Items.AIR ? Items.BUCKET : b);
        }
        public Component name() {
            return item != null ? new ItemStack(item).getHoverName() : fluid.getFluidType().getDescription();
        }
        public String key() {
            return item != null ? "i:" + BuiltInRegistries.ITEM.getKey(item) : "f:" + BuiltInRegistries.FLUID.getKey(fluid);
        }
        @Nullable
        public static Res parse(String key) {
            if (key.length() < 3) return null;
            ResourceLocation id = ResourceLocation.tryParse(key.substring(2));
            if (id == null) return null;
            if (key.startsWith("i:")) {
                Item it = BuiltInRegistries.ITEM.get(id);
                return it == Items.AIR ? null : of(it);
            }
            if (key.startsWith("f:")) {
                Fluid f = BuiltInRegistries.FLUID.get(id);
                return f == Fluids.EMPTY ? null : of(f);
            }
            return null;
        }
    }

    public record FluidNeed(Fluid fluid, int amount) {}

    public record Src(Res out, int perRun, float chance, List<Need> needs, List<FluidNeed> fluids, @Nullable Where where,
                      ResourceLocation type, ItemStack machine) {}

    public record Line(Item item, long count) {}

    public record Result(List<Res> order, Map<Res, Long> demand, Map<Res, Long> runs, Map<Res, Long> left, Set<Res> raw,
                         Map<Res, Src> chosen, Map<Res, Integer> options, Map<Res, Item> pickOf, Map<Item, Long> machines,
                         Set<Res> targets) {
        public List<Res> rawItems() { return raw.stream().filter(r -> !r.isFluid()).sorted(byDemand()).toList(); }
        public List<Res> rawFluids() { return raw.stream().filter(Res::isFluid).sorted(byDemand()).toList(); }
        public List<Res> intermediates() { return order.stream().filter(r -> chosen.containsKey(r) && !targets.contains(r)).toList(); }
        private Comparator<Res> byDemand() {
            return Comparator.comparingLong((Res r) -> -demand.getOrDefault(r, 0L)).thenComparing(Res::key);
        }
    }

    private final Map<Res, List<Src>> sources = new HashMap<>();

    private ResourceCalc() {}

    public static ResourceCalc of(RecipeManager recipes, HolderLookup.Provider regs) {
        ResourceCalc c = new ResourceCalc();
        for (var holder : recipes.getRecipes()) {
            try {
                c.add(holder.value(), regs);
            } catch (RuntimeException e) {
            }
        }
        c.addMachines();
        for (List<Src> l : c.sources.values())
            l.sort(Comparator.comparingInt((Src s) -> s.where() == null ? Integer.MAX_VALUE : s.where().ordinal()).thenComparing(s -> -s.chance()));
        return c;
    }

    private void put(Src s) {
        sources.computeIfAbsent(s.out(), k -> new ArrayList<>()).add(s);
    }

    private static ItemStack machineOf(Where where, ResourceLocation type) {
        return where == Where.OTHER ? RecipeSources.otherMachine(type) : where.iconStack();
    }

    private void add(Recipe<?> r, HolderLookup.Provider regs) {
        Where where = RecipeSources.where(r);
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
        if (type == null) type = ResourceLocation.withDefaultNamespace("unknown");
        ItemStack machine = machineOf(where, type);
        if (r instanceof ReprocessingRecipe rr) {
            List<Need> in = new ArrayList<>();
            in.add(new Need(rr.ingredient(), rr.count()));
            if (rr.hasAux()) in.add(new Need(rr.aux(), rr.auxCount()));
            List<FluidNeed> fl = new ArrayList<>();
            for (var f : rr.fluids()) {
                Fluid fluid = fluid(f.port());
                if (fluid != null) fl.add(new FluidNeed(fluid, f.amount()));
            }
            for (var res : rr.results())
                if (!res.stack().isEmpty()) put(new Src(Res.of(res.stack().getItem()), res.stack().getCount(), res.chance(), List.copyOf(in), List.copyOf(fl), where, type, machine));
            for (var fo : rr.fluidResults()) {
                Fluid f = BuiltInRegistries.FLUID.get(fo.fluid());
                if (f != Fluids.EMPTY && fo.amount() > 0) put(new Src(Res.of(f), fo.amount(), 1f, List.copyOf(in), List.copyOf(fl), where, type, machine));
            }
            return;
        }
        ItemStack out = r.getResultItem(regs);
        if (out.isEmpty()) return;
        Source s = RecipeSources.of(r, regs, out.getItem());
        if (s == null) return;
        put(new Src(Res.of(out.getItem()), Math.max(1, out.getCount()), 1f, s.needs(), List.of(), where, type, machine));
    }

    private void addMachines() {
        ResourceLocation type = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "machine");
        BigMachine col = BigMachine.DISTILLATION_COLUMN;
        int crude = col.extra("crude_mb_t");
        ItemStack colItem = controller(col);
        Fluid crudeOil = ModFluids.CRUDE_OIL.source.get();
        for (var e : List.of(Map.entry(ModFluids.NAPHTHA, "naphtha_pm"), Map.entry(ModFluids.DIESEL, "diesel_pm"), Map.entry(ModFluids.TAR, "tar_pm"))) {
            int per = crude * col.extra(e.getValue()) / 1000;
            if (crude > 0 && per > 0)
                put(new Src(Res.of(e.getKey().source.get()), per, 1f, List.of(), List.of(new FluidNeed(crudeOil, crude)), null, type, colItem));
        }
        BigMachine cr = BigMachine.CRACKER;
        int naphtha = cr.extra("naphtha_mb_t"), eth = naphtha * cr.extra("ethylene_pm") / 1000;
        if (naphtha > 0 && eth > 0)
            put(new Src(Res.of(ModFluids.ETHYLENE.source.get()), eth, 1f, List.of(), List.of(new FluidNeed(ModFluids.NAPHTHA.source.get(), naphtha)), null, type, controller(cr)));
        BigMachine wt = BigMachine.WATER_TREATMENT;
        var win = wt.port("water_in");
        var pout = wt.port("primary_out");
        if (win != null && pout != null && win.rate() > 0 && pout.rate() > 0)
            put(new Src(Res.of(ModFluids.PRIMARY_WATER.source.get()), pout.rate(), 1f, List.of(), List.of(new FluidNeed(Fluids.WATER, win.rate())), null, type, controller(wt)));
    }

    private static ItemStack controller(BigMachine m) {
        var h = BigMachineRegistry.CONTROLLER_ITEMS.get(m);
        return h == null ? ItemStack.EMPTY : new ItemStack(h.get());
    }

    @Nullable
    public static Fluid fluid(String port) {
        String p = port.replace("_in", "").replace("_out", "");
        if (p.contains("primary")) return ModFluids.PRIMARY_WATER.source.get();
        if (p.contains("heavy")) return ModFluids.HEAVY_WATER.source.get();
        if (p.contains("water")) return Fluids.WATER;
        if (p.contains("crude")) return ModFluids.CRUDE_OIL.source.get();
        if (p.contains("naphtha")) return ModFluids.NAPHTHA.source.get();
        if (p.contains("diesel")) return ModFluids.DIESEL.source.get();
        if (p.startsWith("tar")) return ModFluids.TAR.source.get();
        if (p.contains("lye")) return ModFluids.LYE.source.get();
        if (p.contains("nitric")) return ModFluids.NITRIC_ACID.source.get();
        if (p.contains("hlw")) return ModFluids.HLW.source.get();
        if (p.contains("hf")) return ModFluids.HYDROFLUORIC_ACID.source.get();
        if (p.contains("ethylene")) return ModFluids.ETHYLENE.source.get();
        if (p.contains("acid")) return ModFluids.SULFURIC_ACID.source.get();
        return null;
    }

    public List<Src> candidates(Res r, boolean top) {
        List<Src> all = sources.getOrDefault(r, List.of());
        if (r.isFluid()) return all;
        String ns = BuiltInRegistries.ITEM.getKey(r.item()).getNamespace();
        boolean mod = ns.equals(NuclearSQ.MODID), vanilla = ns.equals("minecraft");
        List<Src> out = new ArrayList<>();
        for (Src s : all) {
            boolean vt = vanilla && s.type().getNamespace().equals("minecraft");
            if (!mod && !vt && !s.type().getNamespace().equals(NuclearSQ.MODID)) continue;
            if (vt && reverts(s, r.item())) continue;
            if (!top && (s.where() == Where.FURNACE || s.where() == Where.BLAST_FURNACE || !mod && s.where() == Where.ORE_MILLING)) continue;
            out.add(s);
        }
        return out;
    }

    private boolean reverts(Src s, Item item) {
        for (Need n : s.needs())
            for (ItemStack st : n.ingredient().getItems())
                for (Src o : sources.getOrDefault(Res.of(st.getItem()), List.of()))
                    for (Need m : o.needs()) if (m.ingredient().test(new ItemStack(item))) return true;
        return false;
    }

    public static Item pick(Ingredient in, Map<Item, Integer> picks) {
        ItemStack[] items = in.getItems();
        if (items.length == 0) return Items.BARRIER;
        Item first = items[0].getItem();
        int k = Math.floorMod(picks.getOrDefault(first, 0), items.length);
        return items[k].getItem();
    }

    public static int optionCount(Ingredient in) {
        return in.getItems().length;
    }

    private List<Res> inputs(Src s, Map<Item, Integer> picks) {
        List<Res> out = new ArrayList<>();
        for (Need n : s.needs()) out.add(Res.of(pick(n.ingredient(), picks)));
        for (FluidNeed f : s.fluids()) out.add(Res.of(f.fluid()));
        return out;
    }

    public Result compute(List<Line> lines, Map<Res, Integer> choice, Map<Item, Integer> picks) {
        Map<Res, Long> targets = new LinkedHashMap<>();
        for (Line l : lines) if (l.count() > 0 && l.item() != Items.AIR) targets.merge(Res.of(l.item()), l.count(), ResourceCalc::sum);
        Map<Res, Src> chosen = new HashMap<>();
        Map<Res, Integer> options = new HashMap<>();
        Map<Res, Item> pickOf = new HashMap<>();
        Set<Res> raw = new HashSet<>();
        for (Res t : targets.keySet()) walk(t, true, new HashSet<>(), 0, chosen, options, raw, pickOf, choice, picks);
        List<Res> order = new ArrayList<>();
        Set<Res> done = new HashSet<>(), visiting = new HashSet<>();
        for (Res t : targets.keySet()) topo(t, chosen, picks, done, visiting, order);
        java.util.Collections.reverse(order);
        Map<Res, Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) pos.put(order.get(i), i);
        Map<Res, Long> demand = new HashMap<>(targets), runs = new HashMap<>(), left = new HashMap<>();
        Map<Item, Long> machines = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) {
            Res r = order.get(i);
            Src s = chosen.get(r);
            long d = demand.getOrDefault(r, 0L);
            if (s == null || d <= 0) continue;
            double per = s.perRun() * (double) Math.max(0.0001f, Math.min(1f, s.chance()));
            long n = (long) Math.ceil(d / per - 1e-9);
            runs.put(r, n);
            if (s.chance() >= 1f) left.put(r, n * s.perRun() - d);
            if (!s.machine().isEmpty()) machines.merge(s.machine().getItem(), n, ResourceCalc::sum);
            List<Res> in = inputs(s, picks);
            for (int k = 0; k < in.size(); k++) {
                Integer p = pos.get(in.get(k));
                if (p == null || p <= i) continue;
                long amount = k < s.needs().size() ? s.needs().get(k).count() : s.fluids().get(k - s.needs().size()).amount();
                demand.merge(in.get(k), mul(n, amount), ResourceCalc::sum);
            }
        }
        Map<Item, Long> sorted = new LinkedHashMap<>();
        machines.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new Result(List.copyOf(order), demand, runs, left, raw, chosen, options, pickOf, sorted, targets.keySet());
    }

    private void walk(Res r, boolean top, Set<Res> path, int depth, Map<Res, Src> chosen, Map<Res, Integer> options, Set<Res> raw,
                      Map<Res, Item> pickOf, Map<Res, Integer> choice, Map<Item, Integer> picks) {
        if (chosen.containsKey(r) || raw.contains(r)) return;
        List<Src> c = depth >= MAX_DEPTH ? List.of() : candidates(r, top);
        options.put(r, c.size());
        if (c.isEmpty()) {
            raw.add(r);
            return;
        }
        Src s = c.get(Math.floorMod(choice.getOrDefault(r, 0), c.size()));
        chosen.put(r, s);
        path.add(r);
        for (Need n : s.needs()) {
            Res k = Res.of(pick(n.ingredient(), picks));
            if (optionCount(n.ingredient()) > 1) pickOf.putIfAbsent(k, n.ingredient().getItems()[0].getItem());
            if (!path.contains(k)) walk(k, false, path, depth + 1, chosen, options, raw, pickOf, choice, picks);
        }
        for (FluidNeed f : s.fluids()) {
            Res k = Res.of(f.fluid());
            if (!path.contains(k)) walk(k, false, path, depth + 1, chosen, options, raw, pickOf, choice, picks);
        }
        path.remove(r);
    }

    private void topo(Res r, Map<Res, Src> chosen, Map<Item, Integer> picks, Set<Res> done, Set<Res> visiting, List<Res> out) {
        if (done.contains(r) || visiting.contains(r)) return;
        visiting.add(r);
        Src s = chosen.get(r);
        if (s != null) for (Res k : inputs(s, picks)) topo(k, chosen, picks, done, visiting, out);
        visiting.remove(r);
        done.add(r);
        out.add(r);
    }

    private static long sum(long a, long b) {
        long s = a + b;
        return s < 0 ? Long.MAX_VALUE : s;
    }

    private static long mul(long a, long b) {
        if (a == 0 || b == 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
}
