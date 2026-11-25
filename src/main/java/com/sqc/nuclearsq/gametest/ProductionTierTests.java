package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.ProductionTier;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.StorageValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ProductionTierTests {
    private ProductionTierTests() {}

    private static final String FLOOR = "floor";

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    @GameTest(template = FLOOR, batch = "production_a", timeoutTicks = 20)
    public static void computeModuleTiers(GameTestHelper h) {
        int[] cores = {1, 2, 4, 8, 16, 32, 64, 128};
        int[] mb = {1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072};
        check(com.sqc.nuclearsq.storage.StorageRegistry.CPU_MODULES.length == 8 && com.sqc.nuclearsq.storage.StorageRegistry.RAM_MODULES.length == 8, "acht Stufen je Art");
        for (int i = 0; i < 8; i++) {
            StorageValues.Compute c = com.sqc.nuclearsq.storage.StorageRegistry.CPU_MODULES[i].get().values();
            StorageValues.Compute r = com.sqc.nuclearsq.storage.StorageRegistry.RAM_MODULES[i].get().values();
            check(c.cpu() == cores[i] && c.ram() == 0, "CPU-Stufe " + (i + 1) + ": " + c);
            check(r.ram() == mb[i] && r.cpu() == 0, "RAM-Stufe " + (i + 1) + ": " + r);
        }
        check(StorageValues.gb(256).equals("0.25") && StorageValues.gb(16384).equals("16") && StorageValues.gb(512).equals("0.5"), "Anzeige in GB");
        StorageValues.Assembler a = StorageValues.assembler();
        check(a.baseSize() == 64 && a.craftsPerGb() == 16, "Auftragsgroesse 64 + 16 je GB");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "production_a", timeoutTicks = 20)
    public static void productionTierTable(GameTestHelper h) {
        check(ProductionTier.stage(0, 0) == 0, "0/0 ist Stufe 0");
        check(ProductionTier.stage(4, 64 * 1024) == 1, "4/64 ist Stufe 1");
        check(ProductionTier.stage(4, 63 * 1024) == 0 && ProductionTier.stage(3, 64 * 1024) == 0, "beides muss reichen");
        check(ProductionTier.stage(1000, 0) == 0 && ProductionTier.stage(0, 1000 * 1024) == 0, "eine Haelfte allein reicht nicht");
        check(ProductionTier.stage(32, 512 * 1024) == 4, "ein Rechencluster (32/512) ist Stufe 4");
        check(ProductionTier.stage(128, 2048 * 1024) == 6 && ProductionTier.top() == 6, "128/2048 ist die hoechste Stufe");
        check(ProductionTier.speedPct(BigMachine.REACTOR_ASSEMBLER, 0) == 91, "normal ohne Rechner x0,91");
        check(ProductionTier.speedPct(BigMachine.SMALL_PARTS_ASSEMBLER, 6) == 120, "normal hoechstens x1,2");
        check(ProductionTier.speedPct(BigMachine.COMPUTE_ASSEMBLER, 0) == 100, "Turbo ohne Rechner x1");
        check(ProductionTier.speedPct(BigMachine.COMPUTE_ASSEMBLER, 6) == 12800, "Turbo hoechstens x128");
        int prev = 0;
        for (int s = 0; s <= ProductionTier.top(); s++) {
            int v = ProductionTier.speedPct(BigMachine.COMPUTE_ASSEMBLER, s);
            check(v > prev, "Turbo wird mit jeder Stufe schneller");
            prev = v;
            if (s > 0) {
                long pow = ProductionTier.powerPct(BigMachine.COMPUTE_ASSEMBLER, s), speed = v;
                check(pow < speed, "Stufe " + s + ": Strom " + pow + " % muss unter dem Tempo " + speed + " % liegen");
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "production_b", timeoutTicks = 20)
    public static void quantumComputerValues(GameTestHelper h) {
        BigMachine q = BigMachine.QUANTUM_COMPUTER;
        long perSecond = (long) q.extra("max_lanes") * q.extra("crafts_per_cycle") * 20 / q.extra("cycle_ticks");
        check(perSecond == 1024, "Quantencomputer schafft " + perSecond + " statt 1024 Teile pro Sekunde");
        StorageValues.Demand d = StorageValues.demand("quantum_computer_controller_sqc");
        check(d != null && d.cpu() == 320 && d.ram() == 5120 * 1024, "Bedarf ist " + d);
        long power = (long) q.extra("fe_per_tick") * q.extra("max_lanes");
        check(power == 855000, "Strom " + power + " FE/t statt 855000");
        check(q.isAutocrafter() && !BigMachine.COMPONENT_ASSEMBLER.equals(q), "Quantencomputer ist eine eigene Autocrafting-Maschine");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "production_c", timeoutTicks = 200)
    public static void quantumComputerNeedsFullCompute(GameTestHelper h) {
        BigMachineBlockEntity q = build(h, BigMachine.QUANTUM_COMPUTER, new BlockPos(2, 2, 2));
        BigMachineBlockEntity c = build(h, BigMachine.COMPONENT_ASSEMBLER, new BlockPos(9, 2, 9));
        q.energy().add(q.energy().getMaxEnergyStored());
        c.energy().add(c.energy().getMaxEnergyStored());
        h.runAfterDelay(60, () -> {
            check(q.assembler() != null && c.assembler() != null, "kein Autocrafter");
            check(q.assembler().laneCap() == 0, "Quantencomputer ohne Rechenleistung hat " + q.assembler().laneCap() + " Spuren");
            check(c.assembler().laneCap() == 1, "Bauteilassembler hat " + c.assembler().laneCap() + " Spuren statt 1");
            check(q.assembler().maxLanes() == 8 && q.assembler().tempoLevel() == 0, "Spuren oder Tempo-Stufe falsch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "production_d", timeoutTicks = 200)
    public static void computeAssemblerTurboIsFast(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.COMPUTE_ASSEMBLER, new BlockPos(2, 2, 2));
        h.runAfterDelay(10, () -> {
            check(be.isFormed(), "Rechen-Assembler wird nicht als aufgebaut erkannt");
            BigCrafter c = be.crafter();
            check(c != null && be.items() != null && be.items().getSlots() == 10, "kein 3x3-Raster mit Ergebnisfeld");
            check(c.tier() == 0 && c.tierSpeedPct() == 100, "ohne Rechner Stufe 0, Tempo x1: " + c.tier() + " / " + c.tierSpeedPct());
            var zItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("nuclearsq:zirconium_dust_sqc"));
            int dia = 1, quartz = 4;
            for (var holder : com.sqc.nuclearsq.assembler.AssemblyRecipe.all(h.getLevel().getRecipeManager())) {
                var zr = holder.value();
                if (!zr.result().is(zItem) || zr.ingredients().stream().noneMatch(n -> n.test(new ItemStack(Items.DIAMOND)))) continue;
                for (var need : zr.ingredients()) {
                    if (need.test(new ItemStack(Items.DIAMOND))) dia = need.count();
                    else if (need.test(new ItemStack(Items.QUARTZ))) quartz = need.count();
                }
                break;
            }
            be.items().insertItem(0, new ItemStack(Items.DIAMOND, dia), false);
            be.items().insertItem(4, new ItemStack(Items.QUARTZ, quartz), false);
            be.energy().add(be.energy().getMaxEnergyStored());
            c.setTier(ProductionTier.top());
            check(c.tierSpeedPct() == 12800, "Tempo " + c.tierSpeedPct());
            h.runAfterDelay(20, () -> {
                ItemStack out = be.items().getStackInSlot(9);
                check(out.is(zItem), "Nach 20 Ticks in der hoechsten Stufe kein Ergebnis: " + out + ", Fortschritt " + c.progress());
                h.succeed();
            });
        });
    }
}
