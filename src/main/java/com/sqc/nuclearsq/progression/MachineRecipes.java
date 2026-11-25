package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.EquipmentRecipe;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.particle.ParticleClasses;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

public final class MachineRecipes {
    private MachineRecipes() {}

    public record Row(List<RecipeSources.Need> needs, List<ReprocessingRecipe.Result> results, int time, int energyPerTick,
                      List<Component> notes, @Nullable ResourceLocation id) {}

    public static boolean has(BigMachine m) {
        if (m.recipeKind() != null) return true;
        return switch (m) {
            case REACTOR_ASSEMBLER, COMPUTE_ASSEMBLER, SMALL_PARTS_ASSEMBLER, MICRO_ASSEMBLER, CLEAN_ROOM, EQUIPMENT_ASSEMBLER, CNC_MILL, SOLDER_MACHINE,
                 HEAVY_ASSEMBLER, PARTICLE_ASSEMBLER, COMPONENT_ASSEMBLER, QUANTUM_COMPUTER -> true;
            default -> false;
        };
    }

    public static List<Row> rows(BigMachine m, RecipeManager recipes, HolderLookup.Provider regs) {
        List<Row> out = new ArrayList<>();
        if (m == BigMachine.PARTICLE_ASSEMBLER) {
            particle(out);
            return out;
        }
        if (!has(m)) return out;
        for (var holder : recipes.getRecipes()) {
            Recipe<?> r = holder.value();
            if (makes(m, r)) add(out, holder.id(), r, regs);
        }
        out.sort(Comparator.comparing(MachineRecipes::sortKey));
        return out;
    }

    public static List<ResourceLocation> ids(BigMachine m, RecipeManager recipes) {
        List<ResourceLocation> out = new ArrayList<>();
        if (m == BigMachine.PARTICLE_ASSEMBLER || !has(m)) return out;
        for (var holder : recipes.getRecipes()) if (makes(m, holder.value())) out.add(holder.id());
        out.sort(null);
        return out;
    }

    public static RecipeSources.Need[] needs(Recipe<?> r, int slots, HolderLookup.Provider regs) {
        RecipeSources.Need[] out = new RecipeSources.Need[slots];
        if (r instanceof HeavyAssemblyRecipe h) {
            for (int y = 0; y < h.height(); y++) {
                for (int x = 0; x < h.width(); x++) {
                    Ingredient in = h.key().get(String.valueOf(h.pattern().get(y).charAt(x)));
                    if (in != null) put(out, y * HeavyAssemblyRecipe.SIZE + x, new RecipeSources.Need(in, 1));
                }
            }
        } else if (r instanceof ReprocessingRecipe rr) {
            put(out, 0, new RecipeSources.Need(rr.ingredient(), rr.count()));
            if (rr.hasAux()) put(out, ReprocessingRecipe.AUX_SLOT, new RecipeSources.Need(rr.aux(), rr.auxCount()));
        } else {
            ItemStack res = r.getResultItem(regs);
            RecipeSources.Source s = res.isEmpty() ? null : RecipeSources.of(r, regs, res.getItem());
            if (s != null) for (int k = 0; k < s.needs().size(); k++) put(out, k, s.needs().get(k));
        }
        return out;
    }

    public static ItemStack[] ghost(Recipe<?> r, int slots, HolderLookup.Provider regs, long ms) {
        RecipeSources.Need[] n = needs(r, slots, regs);
        ItemStack[] out = new ItemStack[slots];
        for (int i = 0; i < slots; i++) out[i] = n[i] == null ? ItemStack.EMPTY : n[i].display(ms).copyWithCount(Math.max(1, n[i].count()));
        return out;
    }

    @Nullable
    public static ItemStack[] template(Recipe<?> r, int slots, HolderLookup.Provider regs) {
        ItemStack[] out = new ItemStack[slots];
        java.util.Arrays.fill(out, ItemStack.EMPTY);
        if (r instanceof net.minecraft.world.item.crafting.CraftingRecipe c) {
            var ing = c.getIngredients();
            boolean shaped = c instanceof net.minecraft.world.item.crafting.ShapedRecipe;
            int w = shaped ? ((net.minecraft.world.item.crafting.ShapedRecipe) c).getWidth() : 3;
            if (ing.isEmpty() || w > 3 || ing.size() > Math.min(9, slots)) return null;
            for (int i = 0; i < ing.size(); i++) {
                ItemStack[] all = ing.get(i).getItems();
                int slot = shaped ? i / w * 3 + i % w : i;
                if (all.length > 0 && slot < slots) out[slot] = all[0].copyWithCount(1);
            }
            return out;
        }
        RecipeSources.Need[] n = needs(r, slots, regs);
        for (int i = 0; i < slots; i++) {
            if (n[i] == null) continue;
            ItemStack[] all = n[i].ingredient().getItems();
            if (all.length > 0) out[i] = all[0].copyWithCount(Math.min(64, Math.max(1, n[i].count())));
        }
        return out;
    }

    private static void put(RecipeSources.Need[] out, int slot, RecipeSources.Need n) {
        if (slot < out.length) out[slot] = n;
    }

    public static int fill(IItemHandler h, RecipeSources.Need[] needs, List<ItemStack> inv, boolean all) {
        int slots = Math.min(needs.length, h.getSlots()), times = 1;
        if (all) {
            times = Integer.MAX_VALUE;
            for (int s = 0; s < slots; s++) {
                RecipeSources.Need n = needs[s];
                if (n == null || n.count() <= 0) continue;
                ItemStack cur = h.getStackInSlot(s);
                if (!cur.isEmpty() && !n.ingredient().test(cur)) continue;
                ItemStack kind = cur.isEmpty() ? inv.stream().filter(n.ingredient()).findFirst().orElse(ItemStack.EMPTY) : cur;
                if (kind.isEmpty()) continue;
                int have = 0, per = 0;
                for (ItemStack st : inv) if (ItemStack.isSameItemSameComponents(st, kind)) have += st.getCount();
                for (int o = 0; o < slots; o++) {
                    if (needs[o] == null || !needs[o].ingredient().test(kind)) continue;
                    ItemStack oc = h.getStackInSlot(o);
                    if (!oc.isEmpty() && !ItemStack.isSameItemSameComponents(oc, kind)) continue;
                    have += oc.getCount();
                    per += needs[o].count();
                }
                times = Math.min(times, Math.min(Math.min(h.getSlotLimit(s), kind.getMaxStackSize()) / n.count(), have / Math.max(1, per)));
            }
            times = times == Integer.MAX_VALUE ? 1 : Math.max(1, times);
        }
        int moved = 0;
        for (int s = 0; s < slots; s++) {
            RecipeSources.Need n = needs[s];
            if (n == null || !h.getStackInSlot(s).isEmpty() && !n.ingredient().test(h.getStackInSlot(s))) continue;
            int want = n.count() * times - h.getStackInSlot(s).getCount();
            for (int k = 0; k < inv.size() && want > 0; k++) {
                ItemStack cur = h.getStackInSlot(s), st = inv.get(k);
                if (st.isEmpty() || !n.ingredient().test(st)) continue;
                if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, st)) continue;
                int take = Math.min(want, st.getCount());
                int done = take - h.insertItem(s, st.copyWithCount(take), false).getCount();
                if (done <= 0) continue;
                st.shrink(done);
                want -= done;
                moved += done;
            }
        }
        return moved;
    }

    public static boolean makes(BigMachine m, Recipe<?> r) {
        if (r instanceof ReprocessingRecipe rr) return rr.kind() == m.recipeKind();
        boolean auto = m.isAutocrafter();
        if (r instanceof EquipmentRecipe) return m == BigMachine.EQUIPMENT_ASSEMBLER;
        if (r instanceof HeavyAssemblyRecipe) return m == BigMachine.HEAVY_ASSEMBLER;
        if (r instanceof SmallPartsRecipe s) {
            if (auto) return true;
            boolean micro = s.result().is(BigCrafter.MICRO_PRODUCTS), clean = s.result().is(BigCrafter.CLEAN_ROOM_PRODUCTS), cnc = s.result().is(BigCrafter.CNC_PRODUCTS), solder = s.result().is(BigCrafter.SOLDER_PRODUCTS);
            return m == BigMachine.SMALL_PARTS_ASSEMBLER && !micro && !clean && !cnc && !solder
                    || m == BigMachine.MICRO_ASSEMBLER && micro && !clean
                    || m == BigMachine.CLEAN_ROOM && clean && !micro
                    || m == BigMachine.CNC_MILL && !micro && !clean && (cnc || s.result().is(BigCrafter.CNC_SHARED))
                    || m == BigMachine.SOLDER_MACHINE && solder;
        }
        if (r instanceof AssemblyRecipe) return auto || m == BigMachine.REACTOR_ASSEMBLER || m == BigMachine.COMPUTE_ASSEMBLER;
        return auto && r.getType() == RecipeType.CRAFTING;
    }

    private static String sortKey(Row r) {
        if (r.results().isEmpty()) return "";
        return r.results().get(0).stack().getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static void add(List<Row> out, ResourceLocation id, Recipe<?> r, HolderLookup.Provider regs) {
        if (r instanceof ReprocessingRecipe rr) {
            List<RecipeSources.Need> in = new ArrayList<>();
            in.add(new RecipeSources.Need(rr.ingredient(), rr.count()));
            if (rr.hasAux()) in.add(new RecipeSources.Need(rr.aux(), rr.auxCount()));
            List<Component> notes = new ArrayList<>();
            for (ReprocessingRecipe.FluidCost c : rr.fluids())
                notes.add(Component.translatable("jei.nuclearsq.fluid_in", c.amount(), c.port()));
            for (ReprocessingRecipe.FluidOut o : rr.fluidResults()) {
                Fluid f = BuiltInRegistries.FLUID.get(o.fluid());
                if (f == Fluids.EMPTY) continue;
                notes.add(Component.translatable("gui.nuclearsq.term.recipes.fluid_out", o.amount(), f.getFluidType().getDescription(), o.port()));
            }
            out.add(new Row(in, rr.results(), rr.time(), rr.energyPerTick(), notes, id));
            return;
        }
        ItemStack res = r.getResultItem(regs);
        if (res.isEmpty()) return;
        RecipeSources.Source s = RecipeSources.of(r, regs, res.getItem());
        if (s == null) return;
        int time = 0, fe = 0;
        if (r instanceof AssemblyRecipe a) { time = a.time(); fe = a.energyPerTick(); }
        else if (r instanceof SmallPartsRecipe p) { time = p.time(); fe = p.energyPerTick(); }
        else if (r instanceof EquipmentRecipe e) { time = e.base().time(); fe = e.base().energyPerTick(); }
        else if (r instanceof HeavyAssemblyRecipe hv) { time = hv.time(); fe = hv.energyPerTick(); }
        out.add(new Row(s.needs(), List.of(new ReprocessingRecipe.Result(res, 1f)), time, fe, List.of(), id));
    }

    private static void particle(List<Row> out) {
        ParticleClasses pc = ParticleClasses.get();
        double classSum = 0;
        for (ParticleClasses.ItemClass c : pc.classes) classSum += c.weight();
        for (ParticleClasses.ItemClass c : pc.classes) {
            List<ParticleClasses.Entry> live = c.entries().stream().filter(e -> ParticleClasses.resolve(e.item()) != null).toList();
            double sum = live.stream().mapToInt(ParticleClasses.Entry::weight).sum();
            for (ParticleClasses.Entry e : live) {
                Item it = ParticleClasses.resolve(e.item());
                double pct = classSum * sum == 0 ? 0 : 100.0 * c.weight() / classSum * e.weight() / sum;
                out.add(new Row(List.of(), List.of(new ReprocessingRecipe.Result(new ItemStack(it, Math.max(1, e.max())), 1f)), 0, 0, List.of(
                        Component.translatable("jei.nuclearsq.particle.class", Component.translatable("jei.nuclearsq.particle.class." + c.id())),
                        Component.translatable("jei.nuclearsq.particle.count", e.min(), e.max()),
                        Component.translatable("jei.nuclearsq.particle.share", String.format("%.1f", pct))), null));
            }
        }
    }
}
