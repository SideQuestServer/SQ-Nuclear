package com.sqc.nuclearsq.gametest;

import java.util.Arrays;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.progression.MachineRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeLockTests {
    private RecipeLockTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.MICRO_ASSEMBLER;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BlockPos min, BigMachine m) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        return be;
    }

    private static int indexOf(GameTestHelper h, Item result) {
        var rm = h.getLevel().getRecipeManager();
        List<ResourceLocation> ids = MachineRecipes.ids(M, rm);
        for (int i = 0; i < ids.size(); i++) {
            var r = rm.byKey(ids.get(i)).orElseThrow().value();
            if (r.getResultItem(h.getLevel().registryAccess()).is(result)) return i + 1;
        }
        throw new GameTestAssertException("Kein Rezept fuer " + result);
    }

    @GameTest(template = FLOOR, batch = "recipe_lock", timeoutTicks = 200)
    public static void lockedRecipeIsTheOnlyOneMade(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, new BlockPos(2, 2, 2), M);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Mikro-Assembler wird nicht als aufgebaut erkannt");
            BigCrafter c = be.crafter();
            int chip = indexOf(h, item("chip_1_sqc"));
            int other = chip == 1 ? 2 : 1;
            be.items().insertItem(0, new ItemStack(item("silicon_wafer_sqc")), false);
            be.items().insertItem(1, new ItemStack(Items.COPPER_INGOT), false);
            be.items().insertItem(2, new ItemStack(Items.REDSTONE, 2), false);
            while (be.energy().receiveEnergy(1000, false) > 0) { }
            be.setRecipeLock(other);
            h.runAfterDelay(5, () -> {
                if (c.lockIndex() != other) throw new GameTestAssertException("Sperre nicht gesetzt: " + c.lockIndex());
                if (c.partsRecipe() != null) throw new GameTestAssertException("Gesperrte Maschine stellt fremdes Rezept her");
                be.setRecipeLock(chip);
                h.runAfterDelay(5, () -> {
                    if (c.partsRecipe() == null) throw new GameTestAssertException("Gesperrtes Rezept wird nicht erkannt");
                    be.setRecipeLock(0);
                    h.runAfterDelay(5, () -> {
                        if (c.lockIndex() != 0 || c.partsRecipe() == null) throw new GameTestAssertException("Sperre laesst sich nicht aufheben");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "recipe_lock", timeoutTicks = 20)
    public static void ghostShowsIngredients(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        int chip = indexOf(h, item("chip_1_sqc"));
        var r = rm.byKey(MachineRecipes.ids(M, rm).get(chip - 1)).orElseThrow().value();
        ItemStack[] st = MachineRecipes.ghost(r, BigCrafter.SMALL_GRID + 1, h.getLevel().registryAccess(), 0);
        if (Arrays.stream(st).noneMatch(s -> s.is(item("silicon_wafer_sqc")))) throw new GameTestAssertException("Geister-Raster ohne Wafer: " + Arrays.toString(st));
        if (!st[BigCrafter.SMALL_OUT].isEmpty()) throw new GameTestAssertException("Geist im Ergebnisfeld");
        h.succeed();
    }
}
