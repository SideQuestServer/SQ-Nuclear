package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.progression.RecipeSources;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeFillTests {
    private RecipeFillTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static Recipe<?> chip(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        for (ResourceLocation id : MachineRecipes.ids(BigMachine.MICRO_ASSEMBLER, rm)) {
            Recipe<?> r = rm.byKey(id).orElseThrow().value();
            if (r.getResultItem(h.getLevel().registryAccess()).is(item("chip_1_sqc"))) return r;
        }
        throw new GameTestAssertException("Kein Chip-Rezept");
    }

    private static List<ItemStack> inventory() {
        List<ItemStack> inv = new ArrayList<>();
        inv.add(new ItemStack(item("silicon_wafer_sqc"), 10));
        inv.add(new ItemStack(Items.COPPER_INGOT, 10));
        inv.add(new ItemStack(Items.REDSTONE, 10));
        inv.add(new ItemStack(Items.DIRT, 5));
        return inv;
    }

    private static int total(ItemStackHandler h, List<ItemStack> inv) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) n += h.getStackInSlot(i).getCount();
        for (ItemStack s : inv) n += s.getCount();
        return n;
    }

    @GameTest(template = "floor", batch = "recipe_fill", timeoutTicks = 20)
    public static void fillsOneSetAndKeepsCount(GameTestHelper h) {
        RecipeSources.Need[] needs = MachineRecipes.needs(chip(h), BigCrafter.SMALL_GRID + 1, h.getLevel().registryAccess());
        ItemStackHandler grid = new ItemStackHandler(BigCrafter.SMALL_GRID + 1);
        List<ItemStack> inv = inventory();
        int before = total(grid, inv);
        MachineRecipes.fill(grid, needs, inv, false);
        if (total(grid, inv) != before) throw new GameTestAssertException("Itemzahl veraendert");
        for (int s = 0; s < needs.length; s++) {
            ItemStack st = grid.getStackInSlot(s);
            if (needs[s] == null ? !st.isEmpty() : st.getCount() != needs[s].count() || !needs[s].ingredient().test(st))
                throw new GameTestAssertException("Feld " + s + " falsch: " + st);
        }
        if (inv.get(3).getCount() != 5) throw new GameTestAssertException("Fremdes Item bewegt");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "recipe_fill", timeoutTicks = 20)
    public static void fillAllStaysInRatio(GameTestHelper h) {
        RecipeSources.Need[] needs = MachineRecipes.needs(chip(h), BigCrafter.SMALL_GRID + 1, h.getLevel().registryAccess());
        ItemStackHandler grid = new ItemStackHandler(BigCrafter.SMALL_GRID + 1);
        List<ItemStack> inv = inventory();
        int before = total(grid, inv);
        MachineRecipes.fill(grid, needs, inv, true);
        if (total(grid, inv) != before) throw new GameTestAssertException("Itemzahl veraendert");
        int runs = -1;
        for (int s = 0; s < needs.length; s++) {
            if (needs[s] == null) continue;
            int c = grid.getStackInSlot(s).getCount();
            if (c % needs[s].count() != 0 || runs >= 0 && c / needs[s].count() != runs) throw new GameTestAssertException("Verhaeltnis falsch in Feld " + s + ": " + c);
            runs = c / needs[s].count();
        }
        if (runs < 2) throw new GameTestAssertException("Shift-Fuellen holt zu wenig: " + runs);
        h.succeed();
    }

    @GameTest(template = "floor", batch = "recipe_fill", timeoutTicks = 20)
    public static void foreignSlotStaysUntouched(GameTestHelper h) {
        RecipeSources.Need[] needs = MachineRecipes.needs(chip(h), BigCrafter.SMALL_GRID + 1, h.getLevel().registryAccess());
        ItemStackHandler grid = new ItemStackHandler(BigCrafter.SMALL_GRID + 1);
        grid.setStackInSlot(0, new ItemStack(Items.DIRT, 3));
        List<ItemStack> inv = inventory();
        int before = total(grid, inv);
        MachineRecipes.fill(grid, needs, inv, true);
        if (total(grid, inv) != before) throw new GameTestAssertException("Itemzahl veraendert");
        if (!grid.getStackInSlot(0).is(Items.DIRT) || grid.getStackInSlot(0).getCount() != 3) throw new GameTestAssertException("Fremdes Feld veraendert");
        h.succeed();
    }
}
