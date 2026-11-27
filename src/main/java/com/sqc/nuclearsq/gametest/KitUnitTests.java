package com.sqc.nuclearsq.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKitItem;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class KitUnitTests {
    private static final String FLOOR = "floor";

    private KitUnitTests() {}

    private static MachineKits.Placement place(GameTestHelper h, String kit, BlockPos rel) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(kit), h.absolutePos(rel), Direction.SOUTH);
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) {
            BlockPos b = blocked.get(0);
            throw new GameTestAssertException(kit + ": " + blocked.size() + " Felder belegt, z. B. " + b + " " + h.getLevel().getBlockState(b)
                    + (h.getLevel().isLoaded(b) ? "" : " (nicht geladen)"));
        }
        MachineKits.place(h.getLevel(), pl);
        return pl;
    }

    private static BlockPos part(MachineKits.Placement pl) {
        for (MultiblockStructure.Cell c : pl.cells()) if (c.c() != '@') return c.pos();
        throw new GameTestAssertException("Anlage ohne Bauteile");
    }

    private static void assertAtMostOneKit(GameTestHelper h, MachineKits.Placement pl, int radius, boolean kitExpected) {
        List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.anchor()).inflate(radius));
        int kits = 0, parts = 0;
        StringBuilder names = new StringBuilder();
        for (ItemEntity e : items) {
            ItemStack s = e.getItem();
            if (s.getItem() instanceof MachineKitItem) kits += s.getCount();
            else if (BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals(NuclearSQ.MODID)) {
                parts += s.getCount();
                names.append(' ').append(s.getCount()).append('x').append(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
            }
        }
        if (kits > 1) throw new GameTestAssertException(kits + " Kits fallen heraus");
        if (kitExpected && kits == 0) throw new GameTestAssertException("Kit faellt nicht heraus");
        if (parts > 0) throw new GameTestAssertException(parts + " Einzelteile fallen heraus:" + names);
    }

    private static void assertGone(GameTestHelper h, MachineKits.Placement pl) {
        for (BlockPos p : pl.footprint()) {
            BlockState st = h.getLevel().getBlockState(p);
            if (!st.isAir() && !st.is(Blocks.WATER)) throw new GameTestAssertException("Rest bei " + p + ": " + st);
        }
    }

    @GameTest(template = FLOOR, batch = "kit_unit_xl", timeoutTicks = 1200)
    public static void topOfXlTowerDismantlesWholeTower(GameTestHelper h) {
        BlockPos at = new BlockPos(80, 4, 40);
        MachineKits.Placement probe = MachineKits.placement(MachineKits.def(BigMachine.COOLING_TOWER_XL.id), h.absolutePos(at), Direction.SOUTH);
        Set<Long> chunks = new HashSet<>();
        for (BlockPos p : probe.footprint()) chunks.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
        MachineKits.Placement[] pl = new MachineKits.Placement[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    int open = 0;
                    for (long c : chunks) if (!h.getLevel().areEntitiesLoaded(c)) open++;
                    if (open > 0) throw new GameTestAssertException("Chunks des Turms laden noch (" + open + " von " + chunks.size() + " ohne Entities)");
                })
                .thenExecute(() -> pl[0] = place(h, BigMachine.COOLING_TOWER_XL.id, at))
                .thenExecuteAfter(2, () -> {
                    BlockPos top = pl[0].anchor();
                    for (MultiblockStructure.Cell c : pl[0].cells()) if (c.pos().getY() > top.getY()) top = c.pos();
                    if (top.getY() - pl[0].anchor().getY() < 100) throw new GameTestAssertException("Testannahme falsch: Turm nur " + (top.getY() - pl[0].anchor().getY()) + " hoch");
                    if (MachineKits.owner(h.getLevel(), top) == null) throw new GameTestAssertException("Oberstes Teil gehoert zu keiner Anlage");
                    FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(top);
                })
                .thenWaitUntil(() -> {
                    assertGone(h, pl[0]);
                    assertAtMostOneKit(h, pl[0], 130, true);
                })
                .thenExecute(() -> {
                    for (long c : chunks) h.getLevel().setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, batch = "kit_unit_piston", timeoutTicks = 60)
    public static void pistonCannotPushKitPart(GameTestHelper h) {
        MachineKits.Placement pl = place(h, "main_coolant_pump", new BlockPos(7, 4, 4));
        Set<BlockPos> fp = new HashSet<>(pl.footprint());
        BlockPos target = null, piston = null;
        Direction from = null;
        search:
        for (MultiblockStructure.Cell c : pl.cells()) {
            if (c.c() == '@') continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = c.pos().relative(d);
                if (!fp.contains(n) && h.getLevel().getBlockState(n).isAir() && h.getLevel().getBlockState(n.relative(d)).isAir()) {
                    target = c.pos();
                    piston = n;
                    from = d;
                    break search;
                }
            }
        }
        if (target == null) throw new GameTestAssertException("Kein Bauteil mit freier Aussenseite");
        BlockState before = h.getLevel().getBlockState(target);
        h.getLevel().setBlockAndUpdate(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, from.getOpposite()));
        h.getLevel().setBlockAndUpdate(piston.relative(from), Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockPos t = target, pp = piston;
        h.runAfterDelay(10, () -> {
            if (!h.getLevel().getBlockState(t).is(before.getBlock())) throw new GameTestAssertException("Bauteil wurde verschoben: " + h.getLevel().getBlockState(t));
            if (h.getLevel().getBlockState(pp).getValue(PistonBaseBlock.EXTENDED)) throw new GameTestAssertException("Kolben ist ausgefahren");
            if (MachineKits.owner(h.getLevel(), t) == null) throw new GameTestAssertException("Anlage wurde zerlegt");
            assertAtMostOneKit(h, pl, 8, false);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "kit_unit_explode", timeoutTicks = 60)
    public static void explosionDropsAtMostOneKit(GameTestHelper h) {
        MachineKits.Placement pl = place(h, "main_coolant_pump", new BlockPos(7, 4, 4));
        h.runAfterDelay(5, () -> {
            BlockPos c = pl.anchor();
            h.getLevel().explode(null, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 6f, Level.ExplosionInteraction.BLOCK);
            h.runAfterDelay(3, () -> {
                assertGone(h, pl);
                assertAtMostOneKit(h, pl, 12, false);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "kit_unit_foreign", timeoutTicks = 60)
    public static void foreignBreakDismantlesWithOneKit(GameTestHelper h) {
        MachineKits.Placement pl = place(h, "main_coolant_pump", new BlockPos(7, 4, 4));
        h.runAfterDelay(5, () -> {
            BlockPos p = part(pl);
            BlockState st = h.getLevel().getBlockState(p);
            List<ItemStack> drops = Block.getDrops(st, h.getLevel(), p, h.getLevel().getBlockEntity(p));
            if (!drops.isEmpty()) throw new GameTestAssertException("Bauteil droppt " + drops);
            h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            h.runAfterDelay(2, () -> {
                assertGone(h, pl);
                assertAtMostOneKit(h, pl, 8, true);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "kit_unit_nuke", timeoutTicks = 60)
    public static void nukeDestroysKitWithoutKit(GameTestHelper h) {
        MachineKits.Placement pl = place(h, "main_coolant_pump", new BlockPos(7, 4, 4));
        h.runAfterDelay(5, () -> {
            com.sqc.nuclearsq.kit.KitMarks.nuke(h.getLevel(), pl.anchor(), 4);
            h.runAfterDelay(2, () -> {
                assertGone(h, pl);
                long kits = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.anchor()).inflate(12),
                        e -> e.getItem().getItem() instanceof MachineKitItem).size();
                if (kits > 0) throw new GameTestAssertException("Atomexplosion gibt ein Kit");
                assertAtMostOneKit(h, pl, 12, false);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "kit_unit_states", timeoutTicks = 20)
    public static void shellBlocksHaveFewStates(GameTestHelper h) {
        int total = 0;
        Set<Block> seen = new HashSet<>();
        for (BigMachine m : BigMachine.values()) {
            var shell = m.shellBlock();
            if (!seen.add(shell)) throw new GameTestAssertException(m.id + ": Hilfsblock doppelt vergeben");
            if (!BuiltInRegistries.BLOCK.getKey(shell).getPath().equals(m.id + "_shell_sqc")) throw new GameTestAssertException(m.id + ": falsche ID " + BuiltInRegistries.BLOCK.getKey(shell));
            int states = shell.getStateDefinition().getPossibleStates().size();
            int expect = Math.max(2, m.boxes.length) * 4 * 2;
            if (states != expect) throw new GameTestAssertException(m.id + ": " + states + " Zustaende statt " + expect);
            BlockState last = shell.state(m.boxes.length - 1, 3);
            if (last.getShape(h.getLevel(), BlockPos.ZERO) != shell.shape(m.boxes.length - 1, 3)) throw new GameTestAssertException(m.id + ": letzte Form fehlt");
            total += states;
        }
        if (total >= 20000) throw new GameTestAssertException("Hilfsbloecke haben zusammen " + total + " Zustaende");
        h.succeed();
    }
}
