package com.sqc.nuclearsq.gametest;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class PolymerTests {
    private PolymerTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("5d2e3f41-8c75-4baf-9e2f-3a4b5c6d7e81"), "nsq_polymer");

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static int out(BigMachineBlockEntity be, Item it) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(it)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    private static void run(GameTestHelper h, ItemStack in, ItemStack aux, int water, Item result, int expect, int wait) {
        BigMachineBlockEntity be = build(h, BigMachine.POLYMER_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        if (water > 0) fill(be, "water_in", Fluids.WATER, 4000);
        be.itemsAt("item_in").insertItem(0, in, false);
        if (!aux.isEmpty()) be.itemsAt("item_in").insertItem(5, aux, false);
        h.runAfterDelay(wait, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Polymeranlage nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            int n = out(be, result);
            if (n != expect) throw new GameTestAssertException("Erwartet " + expect + " x " + result + ", ist " + n + " (Zustand " + be.crafter().status() + ")");
            if (water > 0 && be.tank("water_in").getFluidAmount() != 4000 - water) throw new GameTestAssertException("Wasserverbrauch falsch: " + be.tank("water_in").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "polymer_slime", timeoutTicks = 500)
    public static void slimeVulcanizesToRubber(GameTestHelper h) {
        run(h, new ItemStack(Items.SLIME_BALL, 4), new ItemStack(item("sulfur_dust_sqc")), 500, item("rubber_sqc"), 4, 330);
    }

    @GameTest(template = FLOOR, batch = "polymer_kelp", timeoutTicks = 500)
    public static void kelpVulcanizesToRubber(GameTestHelper h) {
        run(h, new ItemStack(Items.DRIED_KELP, 8), new ItemStack(item("sulfur_dust_sqc")), 500, item("rubber_sqc"), 2, 380);
    }

    @GameTest(template = FLOOR, batch = "polymer_seal", timeoutTicks = 400)
    public static void rubberPressesToSeals(GameTestHelper h) {
        run(h, new ItemStack(item("rubber_sqc"), 3), new ItemStack(item("glass_seal_sqc")), 0, item("rubber_seal_sqc"), 2, 250);
    }

    @GameTest(template = FLOOR, batch = "polymer_resin", timeoutTicks = 500)
    public static void rubberBecomesIonResin(GameTestHelper h) {
        run(h, new ItemStack(item("rubber_sqc"), 2), ItemStack.EMPTY, 500, item("ion_resin_sqc"), 4, 300);
    }

    private static void runTar(GameTestHelper h, ItemStack in, Item result, int expect, int tar, int wait) {
        runFluid(h, "tar_in", ModFluids.TAR.source.get(), in, result, expect, tar, wait);
    }

    private static void runFluid(GameTestHelper h, String port, Fluid f, ItemStack in, Item result, int expect, int mb, int wait) {
        BigMachineBlockEntity be = build(h, BigMachine.POLYMER_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        fill(be, port, f, 4000);
        var items = be.itemsAt("item_in");
        ItemStack rest = in;
        for (int i = 0; i < items.getSlots() && !rest.isEmpty(); i++) rest = items.insertItem(i, rest, false);
        h.runAfterDelay(wait, () -> {
            if (out(be, result) != expect) throw new GameTestAssertException("Erwartet " + expect + " x " + result + ", ist " + out(be, result));
            if (be.tank(port).getFluidAmount() != 4000 - mb) throw new GameTestAssertException("Verbrauch an " + port + " falsch: " + be.tank(port).getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "polymer_tar_rubber", timeoutTicks = 500)
    public static void tarVulcanizesToRubber(GameTestHelper h) {
        runTar(h, new ItemStack(item("sulfur_dust_sqc"), 2), item("rubber_sqc"), 6, 400, 330);
    }

    @GameTest(template = FLOOR, batch = "polymer_tar_seal", timeoutTicks = 400)
    public static void tarSealsRubber(GameTestHelper h) {
        runTar(h, new ItemStack(item("rubber_sqc"), 2), item("rubber_seal_sqc"), 4, 200, 250);
    }

    @GameTest(template = FLOOR, batch = "polymer_photoresist", timeoutTicks = 400)
    public static void naphthaMakesPhotoresist(GameTestHelper h) {
        runFluid(h, "naphtha_in", ModFluids.NAPHTHA.source.get(), new ItemStack(item("sulfur_dust_sqc")), item("photoresist_sqc"), 4, 250, 250);
    }

    @GameTest(template = FLOOR, batch = "polymer_hdpe", timeoutTicks = 550)
    public static void ethyleneMakesHdpe(GameTestHelper h) {
        runFluid(h, "ethylene_in", ModFluids.ETHYLENE.source.get(), new ItemStack(Items.IRON_NUGGET), item("hdpe_sheet_sqc"), 2, 1000, 400);
    }

    @GameTest(template = FLOOR, batch = "polymer_tar_coke", timeoutTicks = 400)
    public static void tarMakesCoke(GameTestHelper h) {
        if (!new ItemStack(item("coke_sqc")).is(net.minecraft.tags.ItemTags.COALS)) throw new GameTestAssertException("Koks fehlt im Tag minecraft:coals");
        runTar(h, new ItemStack(Items.COAL), item("coke_sqc"), 2, 200, 300);
    }

    @GameTest(template = FLOOR, batch = "polymer_data", timeoutTicks = 20)
    public static void polymerChainIsInTheDataPack(GameTestHelper h) {
        int polymer = 0;
        boolean sealUse = false, maskUse = false, cableUse = false, resinAssembler = false;
        Item seal = item("rubber_seal_sqc"), rubber = item("rubber_sqc");
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (holder.value() instanceof ReprocessingRecipe r) {
                if (r.kind() == ReprocessingRecipe.Kind.POLYMER) polymer++;
                continue;
            }
            if (holder.value() instanceof com.sqc.nuclearsq.assembler.EquipmentRecipe e) {
                boolean seals = e.base().ingredients().stream().anyMatch(n -> n.matches(new ItemStack(seal)));
                if (e.base().result().is(item("hazmat_2_helmet_sqc"))) sealUse |= seals;
                if (e.base().result().is(item("respirator_mask_sqc"))) maskUse |= seals;
                continue;
            }
            if (!(holder.value() instanceof com.sqc.nuclearsq.assembler.AssemblyRecipe a)) continue;
            var result = a.result();
            boolean usesSeal = a.ingredients().stream().anyMatch(n -> n.test(new ItemStack(seal)));
            boolean usesRubber = a.ingredients().stream().anyMatch(n -> n.test(new ItemStack(rubber)));
            if (result.is(item("hazmat_2_helmet_sqc"))) sealUse |= usesSeal;
            if (result.is(item("respirator_mask_sqc"))) maskUse |= usesSeal;
            if (result.is(item("cable_hardened_sqc"))) cableUse |= usesRubber;
            if (result.is(item("ion_resin_sqc"))) resinAssembler = true;
        }
        int want = 11;
        if (polymer != want) throw new GameTestAssertException("Polymer-Rezepte: " + polymer + " statt " + want);
        if (!sealUse) throw new GameTestAssertException("Schutzanzug II braucht keine Gummidichtung");
        if (!maskUse) throw new GameTestAssertException("Maske braucht keine Gummidichtung");
        if (!cableUse) throw new GameTestAssertException("Gehaertetes Kabel braucht keinen Gummi");
        if (resinAssembler) throw new GameTestAssertException("Ionenharz gibt es noch im Assembler");
        for (String id : new String[] {"polymer_plant_kit_sqc", "polymer_plant_controller_sqc", "rubber_sqc", "rubber_seal_sqc"}) item(id);
        h.succeed();
    }
}
