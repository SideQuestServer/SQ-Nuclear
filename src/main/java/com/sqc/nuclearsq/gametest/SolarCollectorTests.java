package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.power.SolarCollectorBlock;
import com.sqc.nuclearsq.power.SolarCollectorBlockEntity;
import com.sqc.nuclearsq.registry.PowerRegistry;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SolarCollectorTests {
    private SolarCollectorTests() {}

    private static final String FLOOR = "floor";

    private static SolarCollectorBlockEntity collector(GameTestHelper h, BlockPos rel) {
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof SolarCollectorBlockEntity c)) {
            throw new GameTestAssertException("Kein Solar-Kollektor bei " + rel);
        }
        return c;
    }

    @GameTest(template = FLOOR, batch = "solar_collector", timeoutTicks = 260)
    public static void collectorGridFeedsOneSink(GameTestHelper h) {
        var lvl = h.getLevel();
        lvl.setWeatherParameters(12000, 0, false, false);
        lvl.setDayTime(6000);
        if (SolarCollectorBlockEntity.peakPerTick() != 312) {
            throw new GameTestAssertException("Spitze " + SolarCollectorBlockEntity.peakPerTick() + " statt 312 FE/t");
        }
        BlockPos sink = new BlockPos(2, 2, 3), branch = new BlockPos(5, 2, 4);
        BigMachineBlockEntity bat = TestSinks.battery(h, sink, Direction.EAST);
        List<BlockPos> row = List.of(new BlockPos(3, 2, 3), new BlockPos(4, 2, 3), new BlockPos(5, 2, 3), new BlockPos(6, 2, 3), new BlockPos(7, 2, 3));
        for (BlockPos p : row) h.setBlock(p, PowerRegistry.SOLAR_COLLECTOR.get());
        h.setBlock(branch, PowerRegistry.SOLAR_COLLECTOR.get());
        BlockPos panel = new BlockPos(9, 2, 6);
        h.setBlock(panel, PowerRegistry.SOLAR_PANEL.get());
        h.runAfterDelay(40, () -> {
            for (int i = 0; i < row.size(); i++) {
                SolarCollectorBlockEntity c = collector(h, row.get(i));
                if (c.distance() != i) throw new GameTestAssertException("Kollektor " + i + ": Abstand " + c.distance());
                if (lvl.canSeeSky(c.getBlockPos().above()) && c.rate() <= 0) throw new GameTestAssertException("Kollektor " + i + " mittags ohne Strom");
            }
            if (collector(h, branch).distance() != 3) throw new GameTestAssertException("Seitlicher Kollektor: Abstand " + collector(h, branch).distance());
            for (BlockPos rel : List.of(row.get(2), panel)) {
                BlockPos abs = h.absolutePos(rel);
                for (Direction d : SolarCollectorBlockEntity.SIDES) {
                    IEnergyStorage s = lvl.getCapability(Capabilities.EnergyStorage.BLOCK, abs, d);
                    if (s == null || s.canReceive()) throw new GameTestAssertException("Anschluss " + d + " bei " + rel + " fehlt oder nimmt Strom an");
                }
                if (lvl.getCapability(Capabilities.EnergyStorage.BLOCK, abs, Direction.UP) != null) throw new GameTestAssertException("Oben ist ein Anschluss bei " + rel);
            }
            int before = bat.energy().getEnergyStored();
            SolarCollectorBlockEntity far = collector(h, row.get(4)), side = collector(h, branch);
            if (far.deliver(1000) != 1000 || side.deliver(500) != 500) throw new GameTestAssertException("Raster reicht nicht durch");
            int got = bat.energy().getEnergyStored() - before;
            if (got != 1500) throw new GameTestAssertException("Akku bekam " + got + " statt 1500 FE");
            if (far.port(Direction.NORTH).getMaxEnergyStored() != 0) throw new GameTestAssertException("Kollektor hat einen Speicher");
            h.runAfterDelay(1, () -> {
                h.setBlock(sink, Blocks.AIR);
                h.runAfterDelay(45, () -> {
                    for (BlockPos p : row) {
                        if (collector(h, p).distance() != SolarCollectorBlockEntity.NO_ROUTE) throw new GameTestAssertException("Ohne Akku noch Weg bei " + p);
                    }
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "solar_collector", timeoutTicks = 200)
    public static void collectorIsTwoByTwo(GameTestHelper h) {
        var lvl = h.getLevel();
        lvl.setWeatherParameters(12000, 0, false, false);
        lvl.setDayTime(6000);
        BlockPos sink = new BlockPos(3, 2, 2), m = new BlockPos(3, 2, 3);
        BigMachineBlockEntity bat = TestSinks.battery(h, sink, Direction.SOUTH);
        for (int p = 0; p < 4; p++) {
            h.setBlock(m.offset(SolarCollectorBlock.dx(p), 0, SolarCollectorBlock.dz(p)),
                    PowerRegistry.SOLAR_COLLECTOR.get().defaultBlockState().setValue(SolarCollectorBlock.PART, p).setValue(SolarCollectorBlock.PORT, 0));
        }
        h.runAfterDelay(40, () -> {
            SolarCollectorBlockEntity master = collector(h, m);
            if (master.peak() != 312 || master.distance() != 0) throw new GameTestAssertException("Hauptteil: Spitze " + master.peak() + ", Abstand " + master.distance());
            for (int p = 1; p < 4; p++) {
                SolarCollectorBlockEntity c = collector(h, m.offset(SolarCollectorBlock.dx(p), 0, SolarCollectorBlock.dz(p)));
                if (c.peak() != 0) throw new GameTestAssertException("Teil " + p + " erzeugt selbst " + c.peak() + " FE/t");
                if (c.distance() == SolarCollectorBlockEntity.NO_ROUTE) throw new GameTestAssertException("Teil " + p + " ohne Weg");
            }
            for (int p = 0; p < 4; p++) {
                BlockPos q = h.absolutePos(m.offset(SolarCollectorBlock.dx(p), 0, SolarCollectorBlock.dz(p)));
                for (Direction d : SolarCollectorBlockEntity.SIDES) {
                    boolean port = p == 0 && d == Direction.NORTH;
                    if ((lvl.getCapability(Capabilities.EnergyStorage.BLOCK, q, d) != null) != port) throw new GameTestAssertException("Teil " + p + " Seite " + d + ": Port " + !port);
                }
            }
            int before = bat.energy().getEnergyStored();
            SolarCollectorBlockEntity far = collector(h, m.offset(1, 0, 1));
            if (far.deliver(800) != 800) throw new GameTestAssertException("Teil reicht nicht durch");
            if (bat.energy().getEnergyStored() - before != 800) throw new GameTestAssertException("Akku bekam " + (bat.energy().getEnergyStored() - before) + " statt 800 FE");
            h.setBlock(m.offset(1, 0, 1), Blocks.AIR);
            h.runAfterDelay(2, () -> {
                for (int p = 0; p < 4; p++) {
                    BlockPos q = m.offset(SolarCollectorBlock.dx(p), 0, SolarCollectorBlock.dz(p));
                    if (!h.getBlockState(q).isAir()) throw new GameTestAssertException("Teil " + p + " blieb nach dem Abbau stehen");
                }
                h.succeed();
            });
        });
    }
}
