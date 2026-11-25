package com.sqc.nuclearsq.gametest;

import java.util.Arrays;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.PatternRecipes;

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

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeTemplateTests {
    private RecipeTemplateTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    @GameTest(template = "floor", batch = "recipe_template", timeoutTicks = 20)
    public static void shapedCraftingKeepsLayout(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        ResourceLocation id = ResourceLocation.withDefaultNamespace("chest");
        if (!MachineRecipes.ids(BigMachine.COMPONENT_ASSEMBLER, rm).contains(id)) throw new GameTestAssertException("Truhe fehlt in der Liste");
        ItemStack[] grid = MachineRecipes.template(rm.byKey(id).orElseThrow().value(), ComponentAssembler.GRID, h.getLevel().registryAccess());
        if (grid == null || !grid[4].isEmpty()) throw new GameTestAssertException("Mitte der Truhe nicht leer");
        for (int i = 0; i < 9; i++) if (i != 4 && grid[i].getCount() != 1) throw new GameTestAssertException("Feld " + i + " falsch: " + grid[i]);
        PatternRecipes.Match m = PatternRecipes.match(h.getLevel(), Arrays.asList(grid));
        if (m == null || !m.pattern().output().item().is(Items.CHEST)) throw new GameTestAssertException("Vorlage ergibt keine Truhe");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "recipe_template", timeoutTicks = 20)
    public static void partsRecipeUsesCounts(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        for (ResourceLocation id : MachineRecipes.ids(BigMachine.COMPONENT_ASSEMBLER, rm)) {
            Recipe<?> r = rm.byKey(id).orElseThrow().value();
            if (!r.getResultItem(h.getLevel().registryAccess()).is(item("chip_1_sqc"))) continue;
            ItemStack[] grid = MachineRecipes.template(r, ComponentAssembler.GRID, h.getLevel().registryAccess());
            RecipeSources.Need[] needs = MachineRecipes.needs(r, ComponentAssembler.GRID, h.getLevel().registryAccess());
            if (grid == null) throw new GameTestAssertException("Keine Vorlage");
            for (int s = 0; s < needs.length; s++) {
                if (needs[s] == null ? !grid[s].isEmpty() : grid[s].getCount() != Math.min(64, needs[s].count()) || !needs[s].ingredient().test(grid[s]))
                    throw new GameTestAssertException("Feld " + s + " falsch: " + grid[s]);
            }
            h.succeed();
            return;
        }
        throw new GameTestAssertException("Kein Chip-Rezept");
    }
}
