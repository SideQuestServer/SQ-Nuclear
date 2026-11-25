package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ProcessingBTests {
    private ProcessingBTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m) {
        BlockPos ctrl = h.absolutePos(new BlockPos(2, 4, 2)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein BlockEntity");
        return be;
    }

    private static void fill(BigMachineBlockEntity be, String port, net.minecraft.world.level.material.Fluid f, int mb) {
        int n = be.tank(port).fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
        if (n != mb) throw new GameTestAssertException(be.machine().id + "." + port + " nimmt " + n + " statt " + mb + " mB an");
    }

    private static void feedPower(GameTestHelper h, BigMachineBlockEntity be) {
        h.onEachTick(() -> be.energy().receiveEnergy(be.energy().getMaxEnergyStored(), false));
    }

    private static void formed(BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException(be.machine().id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
    }

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
        if (it == net.minecraft.world.item.Items.AIR) throw new GameTestAssertException("Item " + id + " nicht registriert");
        return it;
    }

    private static int count(BigMachineBlockEntity be, Item it) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (be.items().getStackInSlot(i).is(it)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    private static void insert(BigMachineBlockEntity be, int slot, ItemStack s) {
        if (!be.itemsAt("item_in").insertItem(slot, s, false).isEmpty()) throw new GameTestAssertException(be.machine().id + ": Platz " + slot + " nimmt " + s + " nicht an");
    }

    @GameTest(template = FLOOR, batch = "processing_b", timeoutTicks = 800)
    public static void pelletFactoryPressesAndSinters(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.PELLET_FACTORY);
        feedPower(h, be);
        insert(be, 5, new ItemStack(ReactorRegistry.ENRICHED_URANIUM.get()));
        insert(be, 0, new ItemStack(ReactorRegistry.URANIUM_OXIDE_DUST.get(), 2));
        h.runAfterDelay(300, () -> {
            formed(be);
            if (count(be, ReactorRegistry.URANIUM_PELLET.get()) != 3 || !be.items().getStackInSlot(5).isEmpty()) {
                throw new GameTestAssertException("Erwartet 3 Pellets, ist " + count(be, ReactorRegistry.URANIUM_PELLET.get()) + " (Zustand " + be.crafter().status() + ")");
            }
            for (int i = 1; i <= 4; i++) be.items().setStackInSlot(i, ItemStack.EMPTY);
            insert(be, 0, new ItemStack(ReactorRegistry.URANIUM_PELLET.get(), 2));
            h.runAfterDelay(400, () -> {
                if (count(be, item("uranium_core_sqc")) != 1) throw new GameTestAssertException("Erwartet 1 Uran-Kern, Zustand " + be.crafter().status());
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "processing_b", timeoutTicks = 200)
    public static void pelletPressNeedsEnrichedUranium(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.PELLET_FACTORY);
        feedPower(h, be);
        insert(be, 0, new ItemStack(ReactorRegistry.URANIUM_OXIDE_DUST.get(), 2));
        h.runAfterDelay(80, () -> {
            formed(be);
            if (be.crafter().status() != BigCrafter.NO_AUX) throw new GameTestAssertException("Zustand " + be.crafter().status() + " statt ZUSATZ FEHLT");
            if (be.items().getStackInSlot(0).getCount() != 2) throw new GameTestAssertException("Eingang " + be.items().getStackInSlot(0));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "processing_b", timeoutTicks = 400)
    public static void slagCrusherGrindsAsh(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.SLAG_CRUSHER);
        feedPower(h, be);
        insert(be, 0, new ItemStack(item("nuclear_ash_sqc"), 3));
        h.runAfterDelay(260, () -> {
            formed(be);
            int lead = 0;
            for (int i = 1; i <= 4; i++) {
                ItemStack s = be.items().getStackInSlot(i);
                var key = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                if (key.equals("lead_dust_sqc") || key.equals("dust_lead")) lead += s.getCount();
            }
            if (lead != 2 || !be.items().getStackInSlot(0).isEmpty()) throw new GameTestAssertException("Erwartet 2 Bleistaub, ist " + lead + " (Zustand " + be.crafter().status() + ")");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "processing_b", timeoutTicks = 200)
    public static void electrolyzerSplitsWater(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.ELECTROLYZER);
        feedPower(h, be);
        fill(be, "water_in", Fluids.WATER, 4000);
        h.runAfterDelay(100, () -> {
            formed(be);
            int h2 = be.tank("hydrogen_out").getFluidAmount(), o2 = be.tank("oxygen_out").getFluidAmount(), water = be.tank("water_in").getFluidAmount();
            if (h2 < 100 || o2 < 50 || Math.abs(h2 - 2 * o2) > 2) throw new GameTestAssertException("Wasserstoff " + h2 + ", Sauerstoff " + o2);
            if (4000 - water != h2) throw new GameTestAssertException("Wasser " + water + " passt nicht zu Wasserstoff " + h2);
            if (!be.tank("hydrogen_out").getFluid().is(ModFluids.HYDROGEN.source.get()) || !be.tank("oxygen_out").getFluid().is(ModFluids.OXYGEN.source.get())) {
                throw new GameTestAssertException("Falsche Gase in den Tanks");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "processing_b_data", timeoutTicks = 20)
    public static void processingBIsInTheDataPack(GameTestHelper h) {
        int pel = 0, foundry = 0, slag = 0;
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r)) continue;
            switch (r.kind()) {
                case PELLETING -> pel++;
                case FOUNDRY -> foundry++;
                case SLAG_CRUSHING -> slag++;
                default -> { }
            }
        }
        if (pel != 3) throw new GameTestAssertException("Pellet-Rezepte: " + pel);
        if (foundry < 2) throw new GameTestAssertException("Giesserei-Rezepte: " + foundry);
        if (slag < 2) throw new GameTestAssertException("Zerkleinerer-Rezepte: " + slag);
        for (String id : new String[] {"pellet_factory_kit_sqc", "foundry_kit_sqc", "slag_crusher_kit_sqc", "electrolyzer_kit_sqc",
                "pellet_factory_controller_sqc", "foundry_controller_sqc", "slag_crusher_controller_sqc", "electrolyzer_controller_sqc", "uranium_pellet_sqc"}) item(id);
        h.succeed();
    }
}
