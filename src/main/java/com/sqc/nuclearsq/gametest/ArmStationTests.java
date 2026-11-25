package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

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
public final class ArmStationTests {
    private ArmStationTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.SOLDER_MACHINE;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static BlockPos controller(GameTestHelper h) {
        return h.absolutePos(new BlockPos(2, 2, 2)).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
    }

    private static BigMachineBlockEntity build(GameTestHelper h) {
        BlockPos ctrl = controller(h);
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        while (be.energy().receiveEnergy(1000, false) > 0) { }
        return be;
    }

    private static void chipTwoIngredients(BigMachineBlockEntity be) {
        be.items().insertItem(0, new ItemStack(item("chip_1_sqc")), false);
        be.items().insertItem(1, new ItemStack(item("silicon_pure_sqc")), false);
        be.items().insertItem(2, new ItemStack(item("rubber_sqc")), false);
        be.items().insertItem(4, new ItemStack(item("photoresist_sqc"), 2), false);
        be.items().insertItem(3, new ItemStack(Items.GOLD_INGOT, 2), false);
    }

    @GameTest(template = FLOOR, batch = "arm_station", timeoutTicks = 100)
    public static void chipTwoWithoutArm(GameTestHelper h) {
        BigMachineBlockEntity be = build(h);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Loetmaschine nicht aufgebaut");
            chipTwoIngredients(be);
            h.runAfterDelay(50, () -> {
                if (be.crafter().partsRecipe() == null) throw new GameTestAssertException("Chip II ohne Greifarm nicht erkannt");
                if (be.crafter().status() == com.sqc.nuclearsq.machine.big.BigCrafter.NO_ARM) throw new GameTestAssertException("Loetmaschine meldet GREIFARM FEHLT");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "arm_station", timeoutTicks = 20)
    public static void solderMachineOwnsArmProducts(GameTestHelper h) {
        for (String id : new String[] {"chip_2_sqc", "cpu_module_sqc", "cpu_module_8_sqc", "cpu_module_16_sqc", "ram_module_1gb_sqc", "ram_module_2gb_sqc", "ram_module_4gb_sqc"}) {
            if (!new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.SOLDER_PRODUCTS)) throw new GameTestAssertException(id + " fehlt im Tag solder_products");
        }
        for (String id : new String[] {"hard_drive_2_sqc", "hard_drive_3_sqc", "solid_state_drive_sqc", "nvme_drive_sqc"}) {
            ItemStack s = new ItemStack(item(id));
            if (!s.is(com.sqc.nuclearsq.machine.big.BigCrafter.SOLDER_PRODUCTS)) throw new GameTestAssertException(id + " fehlt im Tag solder_products");
            if (s.is(com.sqc.nuclearsq.machine.big.BigCrafter.ARM_PRODUCTS)) throw new GameTestAssertException(id + " braucht keinen Greifarm");
        }
        if (new ItemStack(item("hard_drive_1_sqc")).is(com.sqc.nuclearsq.machine.big.BigCrafter.SOLDER_PRODUCTS)) throw new GameTestAssertException("Festplatte I bleibt im Kleinteil-Assembler");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "arm_station", timeoutTicks = 20)
    public static void tagsDoNotOverlap(GameTestHelper h) {
        for (String id : new String[] {"chip_1_sqc", "cpu_module_1_sqc", "cpu_module_2_sqc", "ram_module_256mb_sqc", "ram_module_512mb_sqc"}) {
            if (new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.ARM_PRODUCTS)) throw new GameTestAssertException(id + " steht in beiden Tags");
        }
        for (String id : new String[] {"chip_2_sqc", "cpu_module_sqc", "cpu_module_16_sqc", "ram_module_1gb_sqc", "ram_module_4gb_sqc"}) {
            if (!new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.ARM_PRODUCTS)) throw new GameTestAssertException(id + " fehlt im Tag arm_products");
            if (new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.MICRO_PRODUCTS)
                    || new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.CLEAN_ROOM_PRODUCTS)) throw new GameTestAssertException(id + " steht in zwei Tags");
        }
        for (String id : new String[] {"chip_3_sqc", "cpu_module_32_sqc", "cpu_module_128_sqc", "ram_module_8gb_sqc", "ram_module_sqc", "ram_module_32gb_sqc"}) {
            if (!new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.CLEAN_ROOM_PRODUCTS)) throw new GameTestAssertException(id + " fehlt im Tag clean_room_products");
            if (new ItemStack(item(id)).is(com.sqc.nuclearsq.machine.big.BigCrafter.ARM_PRODUCTS)) throw new GameTestAssertException(id + " steht in zwei Tags");
        }
        h.succeed();
    }
}
