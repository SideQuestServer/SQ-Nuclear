package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class KilnSmeltingTests {
    private KilnSmeltingTests() {}

    @GameTest(template = "floor", batch = "kiln_smelting", timeoutTicks = 100)
    public static void kilnUsesSmeltingRecipesAsFallback(GameTestHelper h) {
        var level = h.getLevel();
        expect(h, BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.RAW_IRON)), Items.IRON_INGOT, 1);
        expect(h, BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.IRON_ORE)), Items.IRON_INGOT, 1);
        expect(h, BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.SAND)), Items.GLASS, 1);
        expect(h, BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.COAL)), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "coke_sqc")), 2);
        expect(h, BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.CLAY_BALL)), Items.BRICK, 1);
        h.assertTrue(!BigCrafter.auxFor(level, BigMachine.KILN, ItemStack.EMPTY, new ItemStack(Items.CLAY_BALL)), "Ton allein in den Eingang");
        h.assertTrue(BigCrafter.auxFor(level, BigMachine.KILN, new ItemStack(Items.GLASS), new ItemStack(Items.CLAY_BALL)), "Ton zu Glas als Zusatz");
        h.assertTrue(BigCrafter.auxFor(level, BigMachine.KILN, ItemStack.EMPTY, new ItemStack(Items.QUARTZ)), "Quarz bleibt Zusatz");
        h.assertTrue(BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.STICK)) == null, "Stock ohne Rezept");
        h.assertTrue(BigCrafter.findReprocessing(level, BigMachine.BLAST_FURNACE, new ItemStack(Items.SAND)) == null, "Hochofen ohne Ofenrezepte");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "kiln_smelting", timeoutTicks = 100)
    public static void kilnIsFasterAndGivesXp(GameTestHelper h) {
        var level = h.getLevel();
        ReprocessingRecipe r = BigCrafter.findReprocessing(level, BigMachine.KILN, new ItemStack(Items.RAW_IRON));
        h.assertTrue(r != null && r.time() == 140, "Ofenrezept dauert " + (r == null ? -1 : r.time()) + " statt 140 Ticks");
        h.assertTrue(BigCrafter.givesXp(BigMachine.KILN) && BigCrafter.givesXp(BigMachine.BLAST_FURNACE), "Brennofen und Hochofen geben XP");
        h.assertTrue(!BigCrafter.givesXp(BigMachine.FOUNDRY), "Giesserei gibt keine XP");
        h.assertTrue(Math.abs(BigCrafter.xpFor(level, new ItemStack(Items.IRON_INGOT)) - 0.7f) < 1e-4f, "Eisen wie im Ofen");
        Item steel = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "steel_ingot_sqc"));
        h.assertTrue(BigCrafter.xpFor(level, new ItemStack(steel)) == BigCrafter.OWN_XP, "Stahl 0,5");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "kiln_smelting", timeoutTicks = 100)
    public static void blastFurnaceSmeltsEverythingIntoIngots(GameTestHelper h) {
        blast(h, Items.IRON_ORE, Items.IRON_INGOT, 8, 8);
        blast(h, Items.DEEPSLATE_GOLD_ORE, Items.GOLD_INGOT, 8, 8);
        blast(h, Items.RAW_IRON_BLOCK, Items.IRON_INGOT, 1, 9);
        blast(h, Items.RAW_COPPER_BLOCK, Items.COPPER_INGOT, 1, 9);
        h.assertTrue(BigCrafter.findReprocessing(h.getLevel(), BigMachine.BLAST_FURNACE, new ItemStack(Items.IRON_BLOCK)) == null, "Eisenblock bleibt");
        h.assertTrue(BigCrafter.findReprocessing(h.getLevel(), BigMachine.BLAST_FURNACE, new ItemStack(Items.COAL)) == null, "Kohle bleibt Zusatz");
        h.succeed();
    }

    private static void blast(GameTestHelper h, Item in, Item out, int count, int made) {
        ReprocessingRecipe r = BigCrafter.findReprocessing(h.getLevel(), BigMachine.BLAST_FURNACE, new ItemStack(in));
        h.assertTrue(r != null, "kein Hochofenrezept fuer " + in);
        ItemStack res = r.getResultItem(h.getLevel().registryAccess());
        h.assertTrue(res.is(out) && res.getCount() == made && r.count() == count, in + " ergibt " + res + " aus " + r.count());
        h.assertTrue(r.energyPerTick() == BigCrafter.BLAST_SMELT_FE, "Strom " + r.energyPerTick());
    }

    private static void expect(GameTestHelper h, ReprocessingRecipe r, Item out, int count) {
        h.assertTrue(r != null, "kein Rezept fuer " + out);
        h.assertTrue(r.getResultItem(h.getLevel().registryAccess()).is(out), "falsches Ergebnis statt " + out);
        h.assertTrue(r.count() == count, "Menge " + r.count() + " statt " + count);
        h.assertTrue(r.energyPerTick() == BigCrafter.KILN_SMELT_FE, "Strom " + r.energyPerTick());
    }
}
