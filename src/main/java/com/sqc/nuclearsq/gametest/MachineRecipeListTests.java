package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.registry.AssemblyRegistry;
import com.sqc.nuclearsq.registry.EquipmentRegistry;
import com.sqc.nuclearsq.registry.HeavyAssemblyRegistry;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class MachineRecipeListTests {
    private MachineRecipeListTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "machine_recipe_list", timeoutTicks = 200)
    public static void everyRecipeMachineListsAllItsRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        var regs = h.getLevel().registryAccess();
        List<String> problems = new ArrayList<>();
        for (BigMachine m : BigMachine.values()) {
            var kind = m.recipeKind();
            if (kind == null) continue;
            int want = recipes.getAllRecipesFor(kind.type()).size(), got = MachineRecipes.rows(m, recipes, regs).size();
            if (!MachineRecipes.has(m)) problems.add(m + ": kein Rezepte-Knopf");
            if (want == 0 || got != want) problems.add(m + ": " + got + " statt " + want);
        }
        var parts = recipes.getAllRecipesFor(SmallPartsRegistry.TYPE.get());
        long plain = parts.stream().filter(r -> !r.value().result().is(BigCrafter.MICRO_PRODUCTS) && !r.value().result().is(BigCrafter.CLEAN_ROOM_PRODUCTS) && !r.value().result().is(BigCrafter.CNC_PRODUCTS) && !r.value().result().is(BigCrafter.SOLDER_PRODUCTS)).count();
        long solder = parts.stream().filter(r -> r.value().result().is(BigCrafter.SOLDER_PRODUCTS)).count();
        long cnc = parts.stream().filter(r -> r.value().result().is(BigCrafter.CNC_PRODUCTS) || r.value().result().is(BigCrafter.CNC_SHARED)).count();
        long micro = parts.stream().filter(r -> r.value().result().is(BigCrafter.MICRO_PRODUCTS) && !r.value().result().is(BigCrafter.CLEAN_ROOM_PRODUCTS)).count();
        long clean = parts.stream().filter(r -> r.value().result().is(BigCrafter.CLEAN_ROOM_PRODUCTS) && !r.value().result().is(BigCrafter.MICRO_PRODUCTS)).count();
        int assembly = recipes.getAllRecipesFor(AssemblyRegistry.TYPE.get()).size();
        check(problems, BigMachine.REACTOR_ASSEMBLER, MachineRecipes.rows(BigMachine.REACTOR_ASSEMBLER, recipes, regs).size(), assembly);
        check(problems, BigMachine.COMPUTE_ASSEMBLER, MachineRecipes.rows(BigMachine.COMPUTE_ASSEMBLER, recipes, regs).size(), assembly);
        check(problems, BigMachine.SMALL_PARTS_ASSEMBLER, MachineRecipes.rows(BigMachine.SMALL_PARTS_ASSEMBLER, recipes, regs).size(), plain);
        check(problems, BigMachine.MICRO_ASSEMBLER, MachineRecipes.rows(BigMachine.MICRO_ASSEMBLER, recipes, regs).size(), micro);
        check(problems, BigMachine.CLEAN_ROOM, MachineRecipes.rows(BigMachine.CLEAN_ROOM, recipes, regs).size(), clean);
        check(problems, BigMachine.CNC_MILL, MachineRecipes.rows(BigMachine.CNC_MILL, recipes, regs).size(), cnc);
        check(problems, BigMachine.SOLDER_MACHINE, MachineRecipes.rows(BigMachine.SOLDER_MACHINE, recipes, regs).size(), solder);
        check(problems, BigMachine.EQUIPMENT_ASSEMBLER, MachineRecipes.rows(BigMachine.EQUIPMENT_ASSEMBLER, recipes, regs).size(),
                recipes.getAllRecipesFor(EquipmentRegistry.TYPE.get()).size());
        check(problems, BigMachine.HEAVY_ASSEMBLER, MachineRecipes.rows(BigMachine.HEAVY_ASSEMBLER, recipes, regs).size(),
                recipes.getAllRecipesFor(HeavyAssemblyRegistry.TYPE.get()).size());
        long crafting = recipes.getAllRecipesFor(RecipeType.CRAFTING).stream().filter(r -> !r.value().getResultItem(regs).isEmpty()).count();
        check(problems, BigMachine.COMPONENT_ASSEMBLER, MachineRecipes.rows(BigMachine.COMPONENT_ASSEMBLER, recipes, regs).size(),
                assembly + parts.size() + crafting);
        if (MachineRecipes.rows(BigMachine.PARTICLE_ASSEMBLER, recipes, regs).isEmpty()) problems.add("PARTICLE_ASSEMBLER: leer");
        if (MachineRecipes.has(BigMachine.COOLING_TOWER_LARGE) || !MachineRecipes.rows(BigMachine.COOLING_TOWER_LARGE, recipes, regs).isEmpty())
            problems.add("COOLING_TOWER_LARGE: Knopf ohne Rezepte");
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }

    private static void check(List<String> problems, BigMachine m, int got, long want) {
        if (!MachineRecipes.has(m)) problems.add(m + ": kein Rezepte-Knopf");
        if (got != want) problems.add(m + ": " + got + " statt " + want);
    }
}
