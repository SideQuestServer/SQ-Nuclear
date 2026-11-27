package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.ConduitSide;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ItemPipeTests {
    private ItemPipeTests() {}

    private static final String FLOOR = "floor";

    private static Block itemPipe(TransportTier t) {
        return TransportRegistry.ITEM_PIPES.get(t).get();
    }

    private static ChestBlockEntity chest(GameTestHelper h, BlockPos abs, ItemStack content) {
        h.getLevel().setBlockAndUpdate(abs, Blocks.CHEST.defaultBlockState());
        if (!(h.getLevel().getBlockEntity(abs) instanceof ChestBlockEntity c)) throw new GameTestAssertException("Truhe ohne BlockEntity");
        if (!content.isEmpty()) c.setItem(0, content);
        return c;
    }

    private static int count(ChestBlockEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static void setPipe(GameTestHelper h, BlockPos abs, TransportTier t) {
        h.getLevel().setBlockAndUpdate(abs, itemPipe(t).defaultBlockState());
    }

    private static void pull(GameTestHelper h, BlockPos abs, BlockPos target) {
        BlockPos d = target.subtract(abs);
        Direction dir = Direction.getNearest(d.getX(), d.getY(), d.getZ());
        if (!(h.getLevel().getBlockEntity(abs) instanceof ConduitBlockEntity be)) throw new GameTestAssertException("Item-Rohr ohne BlockEntity");
        if (be.cycle(dir, true) != ConduitBlockEntity.PULL) throw new GameTestAssertException("Seite " + dir + " saugt nicht");
        ((ConduitBlock) h.getLevel().getBlockState(abs).getBlock()).refresh(h.getLevel(), abs);
    }

    @GameTest(template = FLOOR, batch = "item_pipe_chest", timeoutTicks = 300)
    public static void itemPipeMovesChestToChest(GameTestHelper h) {
        BlockPos src = h.absolutePos(new BlockPos(1, 2, 3)), dst = h.absolutePos(new BlockPos(5, 2, 3));
        ChestBlockEntity from = chest(h, src, new ItemStack(Items.COBBLESTONE, 32));
        ChestBlockEntity to = chest(h, dst, ItemStack.EMPTY);
        TransportTier[] tiers = {TransportTier.BASIC, TransportTier.REINFORCED, TransportTier.HARDENED};
        for (int i = 0; i < 3; i++) setPipe(h, src.east(i + 1), tiers[i]);
        pull(h, src.east(), src);
        h.runAfterDelay(200, () -> {
            for (int i = 0; i < 3; i++) {
                if (ConduitBlock.sideAt(h.getLevel(), src.east(i + 1), Direction.EAST) == ConduitSide.NONE) {
                    throw new GameTestAssertException("Item-Rohr " + (i + 1) + " nicht nach Osten verbunden");
                }
            }
            if (count(from, Items.COBBLESTONE) != 0) throw new GameTestAssertException("Quell-Truhe nicht leer: " + count(from, Items.COBBLESTONE));
            if (count(to, Items.COBBLESTONE) != 32) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                    BlockPos p = src.east(i + 1);
                    sb.append(" | Rohr ").append(i + 1).append(": ").append(h.getLevel().getBlockState(p))
                            .append(" Inhalt ").append(((ConduitBlockEntity) h.getLevel().getBlockEntity(p)).item());
                }
                throw new GameTestAssertException("Ziel-Truhe hat " + count(to, Items.COBBLESTONE) + " statt 32" + sb);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_pipe_chest", timeoutTicks = 1200)
    public static void itemPipeLineFullOfOneItemLetsSecondThrough(GameTestHelper h) {
        BlockPos src = h.absolutePos(new BlockPos(1, 2, 3)), dst = src.east(5);
        ChestBlockEntity from = chest(h, src, ItemStack.EMPTY);
        for (int i = 0; i < 5; i++) from.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        from.setItem(5, new ItemStack(Items.DIRT, 20));
        h.getLevel().setBlockAndUpdate(dst, Blocks.DROPPER.defaultBlockState());
        if (!(h.getLevel().getBlockEntity(dst) instanceof DispenserBlockEntity to)) throw new GameTestAssertException("kein Spender");
        for (int s = 0; s < 8; s++) to.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
        to.setItem(8, new ItemStack(Items.DIRT, 1));
        for (int i = 1; i <= 4; i++) setPipe(h, src.east(i), TransportTier.REINFORCED);
        pull(h, src.east(), src);
        h.succeedWhen(() -> {
            if (to.getItem(8).getCount() != 21 || count(from, Items.DIRT) != 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 4; i++) sb.append(" | Rohr ").append(i).append(": ").append(((ConduitBlockEntity) h.getLevel().getBlockEntity(src.east(i))).item());
                throw new GameTestAssertException("Erde im Ziel " + to.getItem(8).getCount() + ", in der Truhe " + count(from, Items.DIRT) + sb);
            }
        });
    }

    @GameTest(template = FLOOR, batch = "item_pipe_kinds", timeoutTicks = 60)
    public static void itemPipeIgnoresFluidPipesAndCables(GameTestHelper h) {
        BlockPos mid = h.absolutePos(new BlockPos(3, 2, 3));
        setPipe(h, mid, TransportTier.BASIC);
        h.getLevel().setBlockAndUpdate(mid.west(), TransportRegistry.PIPES.get(TransportTier.BASIC).get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(mid.east(), TransportRegistry.CABLES.get(TransportTier.BASIC).get().defaultBlockState());
        setPipe(h, mid.north(), TransportTier.HARDENED);
        h.runAfterDelay(5, () -> {
            var st = h.getLevel().getBlockState(mid);
            if (ConduitBlock.sideAt(h.getLevel(), mid, Direction.WEST) != ConduitSide.NONE) throw new GameTestAssertException("Item-Rohr haengt am Fluessigkeitsrohr");
            if (ConduitBlock.sideAt(h.getLevel(), mid, Direction.EAST) != ConduitSide.NONE) throw new GameTestAssertException("Item-Rohr haengt am Kabel");
            if (ConduitBlock.sideAt(h.getLevel(), mid, Direction.NORTH) != ConduitSide.ON) throw new GameTestAssertException("Item-Rohre verschiedener Stufe nicht verbunden");
            if (ConduitBlock.sideAt(h.getLevel(), mid.west(), Direction.EAST) != ConduitSide.NONE) {
                throw new GameTestAssertException("Fluessigkeitsrohr haengt am Item-Rohr");
            }
            var cable = h.getLevel().getBlockState(mid.east());
            if (ConduitBlock.sideAt(h.getLevel(), mid.east(), Direction.WEST) != ConduitSide.NONE) throw new GameTestAssertException("Kabel haengt am Item-Rohr");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "conduit_waterlog", timeoutTicks = 60)
    public static void conduitsCanBeWaterlogged(GameTestHelper h) {
        Block[] kinds = {TransportRegistry.PIPES.get(TransportTier.BASIC).get(), itemPipe(TransportTier.BASIC),
                TransportRegistry.CABLES.get(TransportTier.BASIC).get()};
        for (int k = 0; k < kinds.length; k++) {
            BlockPos a = h.absolutePos(new BlockPos(1 + 2 * k, 2, 2)), b = a.south();
            for (BlockPos p : new BlockPos[] {a, b}) {
                h.getLevel().setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
                h.getLevel().setBlockAndUpdate(p, com.sqc.nuclearsq.multiblock.Waterlog.at(kinds[k].defaultBlockState(), h.getLevel(), p));
            }
        }
        h.runAfterDelay(5, () -> {
            for (int k = 0; k < kinds.length; k++) {
                BlockPos a = h.absolutePos(new BlockPos(1 + 2 * k, 2, 2)), b = a.south();
                var st = h.getLevel().getBlockState(a);
                if (!st.is(kinds[k]) || !h.getLevel().getFluidState(a).is(net.minecraft.world.level.material.Fluids.WATER)) {
                    throw new GameTestAssertException(kinds[k] + " nicht geflutet: " + st);
                }
                ConduitBlock c = (ConduitBlock) kinds[k];
                if (ConduitBlock.sideAt(h.getLevel(), a, Direction.SOUTH) == ConduitSide.NONE) throw new GameTestAssertException(kinds[k] + " unter Wasser nicht verbunden: " + st);
                h.getLevel().destroyBlock(b, false);
                if (!h.getLevel().getBlockState(b).is(Blocks.WATER)) throw new GameTestAssertException(kinds[k] + ": nach Abbau kein Wasser, sondern " + h.getLevel().getBlockState(b));
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_pipe_drop", timeoutTicks = 60)
    public static void brokenItemPipeDropsContents(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 2, 3), abs = h.absolutePos(rel);
        setPipe(h, abs, TransportTier.BASIC);
        if (!(h.getLevel().getBlockEntity(abs) instanceof ConduitBlockEntity be)) throw new GameTestAssertException("Item-Rohr ohne BlockEntity");
        ItemStack rest = be.itemSide(null).insertItem(0, new ItemStack(Items.REDSTONE, 7), false);
        if (!rest.isEmpty() || be.item().getCount() != 7) throw new GameTestAssertException("Item-Rohr nimmt keine 7 Redstone an");
        h.getLevel().destroyBlock(abs, false);
        h.runAfterDelay(2, () -> {
            h.assertItemEntityCountIs(Items.REDSTONE, rel, 2.0, 7);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "item_pipe_centrifuge", timeoutTicks = 300)
    public static void centrifugeFedByItemPipes(GameTestHelper h) {
        BigMachine m = BigMachine.ENRICHMENT_CENTRIFUGE;
        BlockPos ctrl = h.absolutePos(new BlockPos(5, 4, 4)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Zentrifuge: kein Controller");
        BigMachine.Port in = m.port("item_in"), out = m.port("item_out");
        java.util.function.BiFunction<BigMachine.Port, int[], BlockPos> at = (p, d) -> m.structure.world(ctrl, 0, p.x() + d[0], p.y() + d[1], p.z() + d[2]);
        be.items().setStackInSlot(1, new ItemStack(ReactorRegistry.ENRICHED_URANIUM.get(), 3));
        BlockPos above = at.apply(in, new int[] {0, 1, 0}), feedPipe = at.apply(in, new int[] {0, 1, 1}), feedChest = at.apply(in, new int[] {0, 1, 2});
        ChestBlockEntity feed = chest(h, feedChest, new ItemStack(ReactorRegistry.YELLOWCAKE.get(), 8));
        setPipe(h, above, TransportTier.BASIC);
        setPipe(h, feedPipe, TransportTier.BASIC);
        pull(h, feedPipe, feedChest);
        BlockPos outPipe = at.apply(out, new int[] {1, 0, 0}), outChest = at.apply(out, new int[] {2, 0, 0});
        setPipe(h, outPipe, TransportTier.BASIC);
        ChestBlockEntity take = chest(h, outChest, ItemStack.EMPTY);
        h.runAfterDelay(200, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Zentrifuge nicht aufgebaut (" + be.matched() + "/" + be.total() + ")");
            Item cake = ReactorRegistry.YELLOWCAKE.get(), enriched = ReactorRegistry.ENRICHED_URANIUM.get();
            if (count(feed, cake) != 0) throw new GameTestAssertException("Truhe am Eingang nicht leer: " + count(feed, cake));
            int inside = 0;
            for (int i = 0; i < be.items().getSlots(); i++) if (be.items().getStackInSlot(i).is(cake)) inside += be.items().getStackInSlot(i).getCount();
            if (inside != 8) throw new GameTestAssertException("nur " + inside + " Yellowcake in der Zentrifuge");
            if (count(take, enriched) != 3) throw new GameTestAssertException("Truhe am Ausgang hat " + count(take, enriched) + " statt 3 angereichertes Uran");
            h.succeed();
        });
    }
}
