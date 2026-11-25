package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.ComputeNet;
import com.sqc.nuclearsq.data.DataRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ClusterTests {
    private ClusterTests() {}

    private static final String FLOOR = "floor";

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "cluster_d", timeoutTicks = 200)
    public static void clusterMachineFeedsDataNetwork(GameTestHelper h) {
        com.sqc.nuclearsq.machine.big.BigMachine m = com.sqc.nuclearsq.machine.big.BigMachine.COMPUTE_CLUSTER;
        BlockPos ctrl = h.absolutePos(new BlockPos(5, 2, 5)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        BlockPos lo = h.absolutePos(new BlockPos(5, 2, 5));
        for (var c : m.structure.cells(ctrl, 0)) {
            for (BlockPos p : BlockPos.betweenClosed(c.pos().offset(-2, 0, -2), c.pos().offset(2, 2, 2))) {
                if (p.getY() >= lo.getY()) h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
        }
        for (var c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be) || be.cluster() == null) {
            throw new GameTestAssertException("kein Cluster-Controller bei " + ctrl);
        }
        BlockPos port = m.structure.world(ctrl, 0, m.port("data").x(), m.port("data").y(), m.port("data").z());
        BlockPos terminal = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (d == net.minecraft.core.Direction.DOWN) continue;
            if (!h.getLevel().getBlockState(port.relative(d)).isAir() || !h.getLevel().getBlockState(port.relative(d, 2)).isAir()) continue;
            h.getLevel().setBlockAndUpdate(port.relative(d), DataRegistry.CABLE.get().defaultBlockState());
            terminal = port.relative(d, 2);
            h.getLevel().setBlockAndUpdate(terminal, DataRegistry.TERMINAL.get().defaultBlockState());
            break;
        }
        check(terminal != null, "kein Platz fuer Kabel und Terminal am Datenanschluss");
        final BlockPos term = terminal;
        be.energy().add(be.energy().getMaxEnergyStored());
        be.tank("water_in").fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(30, () -> {
            check(be.cluster().thermal().state() == com.sqc.nuclearsq.storage.ClusterThermal.RUNNING,
                    "Maschine laeuft nicht: " + com.sqc.nuclearsq.storage.ClusterThermal.stateName(be.cluster().thermal().state()));
            ComputeNet.Status st = ComputeNet.status(h.getLevel(), term);
            check(st.cpu() == 32 && st.ram() == 512 * 1024, "Netz sieht cpu " + st.cpu() + " ram " + st.ram() + ", erwartet 32 / 512");
            check(st.served(), "Terminal wird nicht bedient");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "cluster_e", timeoutTicks = 200)
    public static void clusterTestBuildWorks(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(3, 2, 3));
        com.sqc.nuclearsq.storage.ClusterTestBuild.machine(h.getLevel(), at);
        h.runAfterDelay(30, () -> {
            com.sqc.nuclearsq.machine.big.BigMachine m = com.sqc.nuclearsq.machine.big.BigMachine.COMPUTE_CLUSTER;
            BlockPos ctrl = at.subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
            if (!(h.getLevel().getBlockEntity(ctrl) instanceof com.sqc.nuclearsq.machine.big.BigMachineBlockEntity be) || be.cluster() == null) {
                throw new GameTestAssertException("kein Cluster-Controller bei " + ctrl);
            }
            check(be.cluster().thermal().state() == com.sqc.nuclearsq.storage.ClusterThermal.RUNNING, "Testmodell laeuft nicht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "cluster_f", timeoutTicks = 100)
    public static void clusterMachineOverheatsWithoutWater(GameTestHelper h) {
        com.sqc.nuclearsq.storage.ClusterThermal th = new com.sqc.nuclearsq.storage.ClusterThermal();
        net.neoforged.neoforge.fluids.capability.templates.FluidTank dry = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        boolean throttled = false;
        for (int i = 0; i < 20000 && th.state() != com.sqc.nuclearsq.storage.ClusterThermal.OVERHEATED; i++) {
            th.step(true, true, 780, n -> true, dry);
            if (th.throttle() < 1) throttled = true;
        }
        check(throttled, "Leistung wurde nie gedrosselt");
        check(th.state() == com.sqc.nuclearsq.storage.ClusterThermal.OVERHEATED, "ohne Wasser nie ueberhitzt");
        h.succeed();
    }
}
