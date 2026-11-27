package com.sqc.nuclearsq.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.KitMarks;
import com.sqc.nuclearsq.kit.KitRefund;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKitItem;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class KitShiftTests {
    private static final String FLOOR = "floor";

    private KitShiftTests() {}

    private static int indexOf(String id) {
        return new ArrayList<>(MachineKits.all().keySet()).indexOf(id);
    }

    private static Long2IntOpenHashMap ctrls(GameTestHelper h, BlockPos ctrl) {
        try {
            Field f = KitMarks.Data.class.getDeclaredField("ctrls");
            f.setAccessible(true);
            return (Long2IntOpenHashMap) f.get(h.getLevel().getChunkAt(ctrl).getData(KitMarks.MARKS));
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Markierung nicht lesbar: " + e);
        }
    }

    private static Set<Long> chunks(MachineKits.Placement pl) {
        Set<Long> out = new HashSet<>();
        for (BlockPos p : pl.footprint()) out.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        return out;
    }

    private static void check(GameTestHelper h, MachineKits.Placement pl, BigMachine machine) {
        for (MultiblockStructure.Cell c : pl.cells()) {
            BlockState st = h.getLevel().getBlockState(c.pos());
            if (!c.ok(st)) throw new GameTestAssertException(machine.id + " abgerissen, bei " + c.pos() + ": " + st);
        }
        BlockPos part = null;
        for (MultiblockStructure.Cell c : pl.cells()) if (c.c() != '@') part = c.pos();
        MachineKits.Placement own = MachineKits.owner(h.getLevel(), part);
        if (own == null || !own.def().id().equals(machine.id)) {
            throw new GameTestAssertException(machine.id + " wird gelesen als " + (own == null ? "nichts" : own.def().id()));
        }
        int stored = ctrls(h, pl.anchor()).get(pl.anchor().asLong()) >> 2;
        if (stored != indexOf(machine.id)) throw new GameTestAssertException(machine.id + ": Markierung nicht berichtigt (" + stored + ")");
    }

    @GameTest(template = FLOOR, batch = "kit_shift", timeoutTicks = 400)
    public static void shiftedKitIndexKeepsMachine(GameTestHelper h) {
        BigMachine[][] pairs = {{BigMachine.OIL_DERRICK, BigMachine.CNC_MILL}, {BigMachine.DISTILLATION_COLUMN, BigMachine.SOLDER_MACHINE}};
        BlockPos[] at = {new BlockPos(20, 4, 10), new BlockPos(50, 4, 10)};
        Set<Long> chunks = new HashSet<>();
        for (int i = 0; i < pairs.length; i++) {
            chunks.addAll(chunks(MachineKits.placement(MachineKits.def(pairs[i][0].id), h.absolutePos(at[i]), Direction.SOUTH)));
        }
        for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
        List<MachineKits.Placement> pl = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (long c : chunks) if (!h.getLevel().areEntitiesLoaded(c)) throw new GameTestAssertException("Chunks laden noch");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < pairs.length; i++) {
                        MachineKits.Placement p = MachineKits.placement(MachineKits.def(pairs[i][0].id), h.absolutePos(at[i]), Direction.SOUTH);
                        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), p, null);
                        if (!blocked.isEmpty()) throw new GameTestAssertException(pairs[i][0].id + ": " + blocked.size() + " Felder belegt");
                        MachineKits.place(h.getLevel(), p);
                        ctrls(h, p.anchor()).put(p.anchor().asLong(), indexOf(pairs[i][1].id) * 4 + (p.rot() & 3));
                        pl.add(p);
                    }
                })
                .thenExecuteAfter(5, () -> {
                    for (int i = 0; i < pairs.length; i++) check(h, pl.get(i), pairs[i][0]);
                    for (int i = 0; i < pairs.length; i++) {
                        MachineKits.Placement p = pl.get(i);
                        MachineKits.dismantle(h.getLevel(), MachineKits.owner(h.getLevel(), p.anchor()), true);
                    }
                })
                .thenExecuteAfter(2, () -> {
                    for (int i = 0; i < pairs.length; i++) {
                        Item want = KitRegistry.KITS.get(pairs[i][0].id).get();
                        int right = 0, wrong = 0;
                        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.get(i).anchor()).inflate(6))) {
                            if (!(e.getItem().getItem() instanceof MachineKitItem)) continue;
                            if (e.getItem().is(want)) right++;
                            else wrong++;
                            e.discard();
                        }
                        if (right != 1 || wrong != 0) throw new GameTestAssertException(pairs[i][0].id + ": " + right + " richtige und " + wrong + " falsche Kits");
                    }
                })
                .thenExecute(() -> {
                    for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
                })
                .thenSucceed();
    }

    private static ChestBlockEntity chest(GameTestHelper h, BlockPos rel, Item first) {
        BlockPos p = h.absolutePos(rel);
        h.getLevel().setBlock(p, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (!(h.getLevel().getBlockEntity(p) instanceof ChestBlockEntity c)) throw new GameTestAssertException("Keine Kiste bei " + p);
        c.setItem(0, new ItemStack(first));
        c.setItem(1, new ItemStack(Items.IRON_INGOT, 7));
        return c;
    }

    @Nullable
    private static ChestBlockEntity beside(GameTestHelper h, BlockPos rel) {
        BlockPos p = h.absolutePos(rel);
        List<BlockPos> around = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) around.add(p.relative(d));
        around.add(p.above());
        for (BlockPos q : around) if (h.getLevel().getBlockEntity(q) instanceof ChestBlockEntity c) return c;
        return null;
    }

    @GameTest(template = FLOOR, batch = "kit_refund", timeoutTicks = 40)
    public static void wrongKitChestGetsRefundBeside(GameTestHelper h) {
        BigMachine[][] pairs = {{BigMachine.CNC_MILL, BigMachine.OIL_DERRICK}, {BigMachine.SOLDER_MACHINE, BigMachine.DISTILLATION_COLUMN}};
        BlockPos[] at = {new BlockPos(3, 4, 3), new BlockPos(9, 4, 3)};
        for (int i = 0; i < pairs.length; i++) {
            Item wrong = KitRegistry.KITS.get(pairs[i][0].id).get();
            Item right = KitRegistry.KITS.get(pairs[i][1].id).get();
            ChestBlockEntity old = chest(h, at[i], wrong);
            if (!KitRefund.refund(h.getLevel(), h.absolutePos(at[i]))) throw new GameTestAssertException(pairs[i][0].id + ": keine Erstattung");
            ChestBlockEntity box = beside(h, at[i]);
            if (box == null) throw new GameTestAssertException(pairs[i][1].id + ": keine Kiste daneben");
            if (!box.getItem(0).is(right)) throw new GameTestAssertException(pairs[i][1].id + ": falsches Kit daneben: " + box.getItem(0));
            if (!old.getItem(0).is(wrong) || old.getItem(1).getCount() != 7) throw new GameTestAssertException("Alte Kiste veraendert");
        }
        BlockPos plain = new BlockPos(3, 4, 9);
        chest(h, plain, KitRegistry.KITS.get(BigMachine.OIL_DERRICK.id).get());
        if (KitRefund.refund(h.getLevel(), h.absolutePos(plain))) throw new GameTestAssertException("Bohrturm-Kit wurde erstattet");
        if (beside(h, plain) != null) throw new GameTestAssertException("Kiste neben normaler Kiste");
        h.succeed();
    }
}
