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
public final class ChemLabTests {
    private ChemLabTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(java.util.UUID.fromString("7f405163-ae97-4dc1-9041-5c6d7e8f90a3"), "nsq_chemlab");

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

    private static BigMachineBlockEntity lab(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.CHEMICAL_LAB, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        return be;
    }

    @GameTest(template = FLOOR, batch = "chemlab_lye", timeoutTicks = 500)
    public static void kelpAndWaterMakeLye(GameTestHelper h) {
        BigMachineBlockEntity be = lab(h);
        fill(be, "water_in", Fluids.WATER, 2000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(Items.DRIED_KELP, 6), false);
        h.runAfterDelay(300, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Chemielabor nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            var t = be.tank("lye_out");
            if (t.getFluidAmount() != 500 || !t.getFluid().is(ModFluids.LYE.source.get())) throw new GameTestAssertException("Natronlauge: " + t.getFluid() + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("water_in").getFluidAmount() != 1600) throw new GameTestAssertException("Wasserverbrauch falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemlab_nitric", timeoutTicks = 500)
    public static void gunpowderAndSulfuricAcidMakeNitricAcid(GameTestHelper h) {
        BigMachineBlockEntity be = lab(h);
        fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), 2000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(Items.GUNPOWDER, 4), false);
        h.runAfterDelay(340, () -> {
            var t = be.tank("nitric_out");
            if (t.getFluidAmount() != 400 || !t.getFluid().is(ModFluids.NITRIC_ACID.source.get())) throw new GameTestAssertException("Salpetersaeure: " + t.getFluid() + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("acid_in").getFluidAmount() != 1700) throw new GameTestAssertException("Saeureverbrauch falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemlab_resin", timeoutTicks = 500)
    public static void lyeRegeneratesSpentResin(GameTestHelper h) {
        BigMachineBlockEntity be = lab(h);
        fill(be, "lye_out", ModFluids.LYE.source.get(), 1000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("spent_resin_sqc"), 2), false);
        h.runAfterDelay(260, () -> {
            int n = out(be, item("ion_resin_sqc"));
            if (n != 2) throw new GameTestAssertException("Erwartet 2 Ionenharz, ist " + n + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("lye_out").getFluidAmount() != 800) throw new GameTestAssertException("Laugenverbrauch falsch: " + be.tank("lye_out").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemlab_grease", timeoutTicks = 500)
    public static void lithiumAndDieselMakeGrease(GameTestHelper h) {
        BigMachineBlockEntity be = lab(h);
        fill(be, "diesel_in", ModFluids.DIESEL.source.get(), 2000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("lithium_ingot_sqc")), false);
        h.runAfterDelay(300, () -> {
            int n = out(be, item("lubricant_sqc"));
            if (n != 4) throw new GameTestAssertException("Erwartet 4 Schmierfett, ist " + n + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("diesel_in").getFluidAmount() != 1500) throw new GameTestAssertException("Dieselverbrauch falsch: " + be.tank("diesel_in").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemlab_silicon", timeoutTicks = 500)
    public static void rawSiliconAndSulfuricAcidMakePureSilicon(GameTestHelper h) {
        BigMachineBlockEntity be = lab(h);
        fill(be, "acid_in", ModFluids.SULFURIC_ACID.source.get(), 2000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("silicon_raw_sqc"), 3), false);
        h.runAfterDelay(340, () -> {
            int n = out(be, item("silicon_pure_sqc"));
            if (n != 2) throw new GameTestAssertException("Erwartet 2 Reinsilizium, ist " + n + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("acid_in").getFluidAmount() != 1700) throw new GameTestAssertException("Saeureverbrauch falsch: " + be.tank("acid_in").getFluidAmount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemlab_vitrify", timeoutTicks = 500)
    public static void nitricAcidDissolvesMoreWaste(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.VITRIFICATION_PLANT, new BlockPos(2, 4, 2));
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
        if (be.tank("nitric_in").fill(new FluidStack(ModFluids.SULFURIC_ACID.source.get(), 100), IFluidHandler.FluidAction.SIMULATE) != 0) throw new GameTestAssertException("nitric_in nimmt Schwefelsaeure an");
        fill(be, "nitric_in", ModFluids.NITRIC_ACID.source.get(), 1000);
        be.itemsAt("item_in").insertItem(0, new ItemStack(item("filter_residue_sqc"), 8), false);
        h.runAfterDelay(300, () -> {
            var t = be.tank("hlw_out");
            if (t.getFluidAmount() != 800 || !t.getFluid().is(ModFluids.HLW.source.get())) throw new GameTestAssertException("HLW: " + t.getFluid() + " (Zustand " + be.crafter().status() + ")");
            if (be.tank("nitric_in").getFluidAmount() != 800) throw new GameTestAssertException("Salpetersaeure-Verbrauch falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "chemlab_data", timeoutTicks = 20)
    public static void chemistryIsInTheDataPack(GameTestHelper h) {
        int chem = 0, vitrify = 0;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r)) continue;
            if (r.kind() == ReprocessingRecipe.Kind.CHEMISTRY) chem++;
            if (r.kind() == ReprocessingRecipe.Kind.VITRIFICATION) vitrify++;
        }
        if (chem != 9) throw new GameTestAssertException("Chemie-Rezepte: " + chem + " statt 9");
        if (vitrify != 5) throw new GameTestAssertException("Verglasungs-Rezepte: " + vitrify + " statt 5");
        for (String id : new String[] {"chemical_lab_kit_sqc", "chemical_lab_controller_sqc"}) item(id);
        h.succeed();
    }
}
