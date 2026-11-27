package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortKind;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ItemPortTests {
    private ItemPortTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": kein Controller");
        return be;
    }

    private static BigMachine.Port port(BigMachine m, String name) {
        BigMachine.Port p = m.port(name);
        if (p == null || p.kind() != BigPortKind.ITEM) throw new GameTestAssertException(m.id + ": Item-Port " + name + " fehlt");
        return p;
    }

    private static BlockPos at(BigMachineBlockEntity be, BigMachine.Port p, int dx, int dy, int dz) {
        return be.machine().structure.world(be.getBlockPos(), 0, p.x() + dx, p.y() + dy, p.z() + dz);
    }

    private static HopperBlockEntity hopper(GameTestHelper h, BlockPos pos, BlockPos target, ItemStack content) {
        BlockPos d = target.subtract(pos);
        Direction face = Direction.getNearest(d.getX(), d.getY(), d.getZ());
        if (face == Direction.UP) throw new GameTestAssertException("Trichter kann nicht nach oben zeigen");
        h.getLevel().setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, face));
        if (!(h.getLevel().getBlockEntity(pos) instanceof HopperBlockEntity hb)) throw new GameTestAssertException("Trichter ohne BlockEntity");
        if (!content.isEmpty()) hb.setItem(0, content);
        return hb;
    }

    private static int count(HopperBlockEntity hb, Item item) {
        int n = 0;
        for (int i = 0; i < hb.getContainerSize(); i++) if (hb.getItem(i).is(item)) n += hb.getItem(i).getCount();
        return n;
    }

    private static void roundTrip(GameTestHelper h, BigMachine m, BlockPos min, int inDx, int inDy, int inDz, ItemStack input,
                                  int outSlot, ItemStack result, boolean checkInside) {
        roundTrip(h, m, min, inDx, inDy, inDz, input, outSlot, result, checkInside, be -> {});
    }

    private static void roundTrip(GameTestHelper h, BigMachine m, BlockPos min, int inDx, int inDy, int inDz, ItemStack input,
                                  int outSlot, ItemStack result, boolean checkInside,
                                  java.util.function.Consumer<BigMachineBlockEntity> prep) {
        BigMachineBlockEntity be = build(h, m, min);
        prep.accept(be);
        BigMachine.Port in = port(m, "item_in"), out = port(m, "item_out");
        if (!in.in() || in.out() || !out.out() || out.in()) throw new GameTestAssertException(m.id + ": Richtungen falsch (" + in.dir() + "/" + out.dir() + ")");
        be.items().setStackInSlot(outSlot, result.copy());
        Item inItem = input.getItem(), outItem = result.getItem();
        HopperBlockEntity feed = hopper(h, at(be, in, inDx, inDy, inDz), at(be, in, 0, 0, 0), input.copy());
        HopperBlockEntity take = hopper(h, at(be, out, 0, -1, 0), at(be, out, 0, -2, 0), ItemStack.EMPTY);
        h.runAfterDelay(100, () -> {
            if (!be.isFormed()) throw new GameTestAssertException(m.id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            IItemHandler inCap = be.itemsAt("item_in"), outCap = be.itemsAt("item_out");
            for (int i = 0; i < inCap.getSlots(); i++) {
                if (!inCap.extractItem(i, 64, true).isEmpty()) throw new GameTestAssertException(m.id + ": Eingang gibt Platz " + i + " her");
            }
            for (int i = 0; i < outCap.getSlots(); i++) {
                if (outCap.insertItem(i, input.copy(), true).isEmpty()) throw new GameTestAssertException(m.id + ": Ausgang nimmt in Platz " + i + " an");
            }
            if (count(feed, inItem) != 0) throw new GameTestAssertException(m.id + ": Eingangs-Trichter nicht leer (" + count(feed, inItem) + " uebrig)");
            if (checkInside) {
                int inside = 0;
                for (int i = 0; i < be.items().getSlots(); i++) if (be.items().getStackInSlot(i).is(inItem)) inside += be.items().getStackInSlot(i).getCount();
                if (inside < input.getCount()) throw new GameTestAssertException(m.id + ": nur " + inside + " " + inItem + " in der Maschine");
            }
            if (count(take, outItem) != result.getCount()) {
                throw new GameTestAssertException(m.id + ": Ausgangs-Trichter hat " + count(take, outItem) + " statt " + result.getCount() + " " + outItem);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_ports_reactor_assembler", timeoutTicks = 200)
    public static void reactorAssemblerHopperInOut(GameTestHelper h) {
        roundTrip(h, BigMachine.REACTOR_ASSEMBLER, new BlockPos(4, 4, 4), 0, 1, 0, new ItemStack(Items.IRON_INGOT, 2),
                BigCrafter.SMALL_OUT, new ItemStack(Items.DIAMOND), true);
    }

    @GameTest(template = FLOOR, batch = "item_ports_heavy_assembler", timeoutTicks = 200)
    public static void heavyAssemblerHopperInOut(GameTestHelper h) {
        roundTrip(h, BigMachine.HEAVY_ASSEMBLER, new BlockPos(2, 4, 2), 0, 1, 0, new ItemStack(Items.IRON_INGOT, 2),
                BigCrafter.ASM_OUT, new ItemStack(Items.DIAMOND), true,
                be -> be.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT)));
    }

    @GameTest(template = FLOOR, batch = "item_ports_ore_mill", timeoutTicks = 200)
    public static void oreMillHopperInOut(GameTestHelper h) {
        roundTrip(h, BigMachine.ORE_MILL, new BlockPos(5, 4, 4), 0, 1, 0, new ItemStack(ReactorRegistry.URANIUM_FRAGMENT.get(), 2),
                1, new ItemStack(ReactorRegistry.YELLOWCAKE.get()), true);
    }

    @GameTest(template = FLOOR, batch = "item_ports_centrifuge", timeoutTicks = 200)
    public static void centrifugeHopperInOut(GameTestHelper h) {
        roundTrip(h, BigMachine.ENRICHMENT_CENTRIFUGE, new BlockPos(5, 4, 4), 0, 1, 0, new ItemStack(ReactorRegistry.YELLOWCAKE.get(), 2),
                1, new ItemStack(ReactorRegistry.ENRICHED_URANIUM.get()), true);
    }

    @GameTest(template = FLOOR, batch = "item_ports_reprocessing", timeoutTicks = 200)
    public static void reprocessingPlantHopperInOut(GameTestHelper h) {
        roundTrip(h, BigMachine.REPROCESSING_PLANT, new BlockPos(2, 4, 2), 0, 1, 0,
                new ItemStack(CatalogRegistry.ITEM_MAP.get("filter_residue_sqc").get(), 2), 1, new ItemStack(CatalogRegistry.ASH_ITEM.get()), true);
    }

    @GameTest(template = FLOOR, batch = "item_ports_coal_generator", timeoutTicks = 200)
    public static void coalGeneratorHopperInOut(GameTestHelper h) {
        roundTrip(h, BigMachine.COAL_GENERATOR, new BlockPos(4, 4, 4), -1, 0, 0, new ItemStack(Items.COAL, 2),
                0, new ItemStack(Items.BUCKET), false);
    }

    private static void sideAndTop(GameTestHelper h, BigMachine m, BlockPos min, BlockPos inSide, BlockPos outSide, ItemStack input,
                                   int outSlot, ItemStack result, java.util.function.Consumer<BigMachineBlockEntity> prep) {
        BigMachineBlockEntity be = build(h, m, min);
        prep.accept(be);
        BigMachine.Port in = port(m, "item_in"), out = port(m, "item_out");
        be.items().setStackInSlot(outSlot, result.copy());
        Item inItem = input.getItem(), outItem = result.getItem();
        BlockPos inCell = at(be, in, 0, 0, 0), outCell = at(be, out, 0, 0, 0);
        HopperBlockEntity top = hopper(h, at(be, in, 0, 1, 0), inCell, input.copy());
        HopperBlockEntity side = hopper(h, at(be, in, inSide.getX(), inSide.getY(), inSide.getZ()), inCell, input.copy());
        BlockPos takePos = at(be, out, outSide.getX(), outSide.getY(), outSide.getZ());
        HopperBlockEntity take = hopper(h, takePos, takePos.offset(takePos.subtract(outCell)), ItemStack.EMPTY);
        h.runAfterDelay(100, () -> {
            if (!be.isFormed()) throw new GameTestAssertException(m.id + " nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            if (count(top, inItem) != 0) throw new GameTestAssertException(m.id + ": Trichter oben nicht leer (" + count(top, inItem) + ")");
            if (count(side, inItem) != 0) throw new GameTestAssertException(m.id + ": Trichter an der Seite nicht leer (" + count(side, inItem) + ")");
            int inside = 0;
            for (int i = 0; i < be.items().getSlots(); i++) if (be.items().getStackInSlot(i).is(inItem)) inside += be.items().getStackInSlot(i).getCount();
            if (inside < 2 * input.getCount()) throw new GameTestAssertException(m.id + ": nur " + inside + " " + inItem + " in der Maschine");
            if (count(take, outItem) != result.getCount()) {
                throw new GameTestAssertException(m.id + ": Trichter an der Ausgangs-Wand hat " + count(take, outItem) + " statt " + result.getCount());
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_ports_wall_ore_mill", timeoutTicks = 200)
    public static void oreMillHopperTopAndSide(GameTestHelper h) {
        sideAndTop(h, BigMachine.ORE_MILL, new BlockPos(5, 4, 5), new BlockPos(0, 0, -1), new BlockPos(1, 0, 0),
                new ItemStack(ReactorRegistry.URANIUM_FRAGMENT.get(), 2), 1, new ItemStack(ReactorRegistry.YELLOWCAKE.get()), be -> {});
    }

    @GameTest(template = FLOOR, batch = "item_ports_wall_heavy_assembler", timeoutTicks = 200)
    public static void heavyAssemblerHopperTopAndSide(GameTestHelper h) {
        sideAndTop(h, BigMachine.HEAVY_ASSEMBLER, new BlockPos(2, 4, 2), new BlockPos(1, 0, 0), new BlockPos(1, 0, 0),
                new ItemStack(Items.IRON_INGOT, 2), BigCrafter.ASM_OUT, new ItemStack(Items.DIAMOND),
                be -> be.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT)));
    }

    @GameTest(template = FLOOR, batch = "item_ports_heavy_pattern", timeoutTicks = 100)
    public static void heavyAssemblerPatternFillsEmptySlots(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, BigMachine.HEAVY_ASSEMBLER, new BlockPos(2, 4, 2));
        h.runAfterDelay(20, () -> {
            var rm = h.getLevel().getRecipeManager();
            var ids = com.sqc.nuclearsq.progression.MachineRecipes.ids(BigMachine.HEAVY_ASSEMBLER, rm);
            if (ids.isEmpty()) throw new GameTestAssertException("Kein Rezept fuer den Heavy Assembler");
            var n = com.sqc.nuclearsq.progression.MachineRecipes.needs(rm.byKey(ids.get(0)).orElseThrow().value(), BigCrafter.GRID, h.getLevel().registryAccess());
            int need = -1, free = -1;
            for (int i = 0; i < BigCrafter.GRID; i++) {
                if (n[i] == null) {
                    if (free < 0) free = i;
                } else if (need < 0 && n[i].ingredient().getItems().length > 0) need = i;
            }
            if (need < 0) throw new GameTestAssertException("Rezept ohne Zutat");
            ItemStack want = n[need].ingredient().getItems()[0].copyWithCount(1);
            IItemHandler cap = be.itemsAt("item_in");
            if (cap.insertItem(need, want.copy(), true).isEmpty()) throw new GameTestAssertException("Ohne Muster nimmt ein leeres Feld an");
            be.setRecipeLock(1);
            if (!cap.insertItem(need, want.copy(), true).isEmpty()) throw new GameTestAssertException("Mit Muster nimmt Feld " + need + " " + want + " nicht an");
            if (free >= 0) {
                if (cap.insertItem(free, want.copy(), true).isEmpty()) throw new GameTestAssertException("Feld " + free + " ausserhalb des Musters nimmt an");
                be.items().setStackInSlot(free, new ItemStack(Items.DIRT));
                if (cap.insertItem(need, want.copy(), true).isEmpty()) throw new GameTestAssertException("Fremdes Item im Raster, Muster nimmt trotzdem an");
            }
            h.succeed();
        });
    }
}
