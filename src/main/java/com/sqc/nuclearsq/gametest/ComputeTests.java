package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.ComputeNet;
import com.sqc.nuclearsq.data.ControlWallBlockEntity;
import com.sqc.nuclearsq.data.DataConsole;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.StorageRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ComputeTests {
    private ComputeTests() {}

    private static final String FLOOR = "floor";

    private static List<BlockPos> rackWithCable(GameTestHelper h, BlockPos min, BigMachineBlockEntity[] rack) {
        BigMachine m = BigMachine.SERVER_RACK_SMALL;
        BlockPos ctrl = h.absolutePos(min);
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) || be.rack() == null) {
            throw new GameTestAssertException("kein Rack-Controller");
        }
        rack[0] = be;
        BlockPos port = m.structure.world(ctrl, 0, 0, 0, 1), behind = port.offset(port.subtract(ctrl));
        Direction out = Direction.getNearest(port.subtract(ctrl).getX(), 0, port.subtract(ctrl).getZ());
        Direction side = out.getClockWise();
        List<BlockPos> cable = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            BlockPos p = behind.relative(side, i);
            h.getLevel().setBlockAndUpdate(p, DataRegistry.CABLE.get().defaultBlockState());
            cable.add(p);
        }
        return cable;
    }

    private static void charge(BigMachineBlockEntity be) {
        be.energy().add(be.energy().getMaxEnergyStored());
    }

    private static void pair(BigMachineBlockEntity be, int slot) {
        be.rack().drives().setStackInSlot(slot, new ItemStack(StorageRegistry.CPU_MODULE.get()));
        be.rack().drives().setStackInSlot(slot + 1, new ItemStack(StorageRegistry.RAM_MODULE.get()));
    }

    private static boolean says(List<Component> out, String key) {
        for (Component c : out) if (c.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) return true;
        return false;
    }

    @GameTest(template = FLOOR, batch = "compute", timeoutTicks = 100)
    public static void computeModuleSuppliesConsoles(GameTestHelper h) {
        BigMachineBlockEntity[] r = new BigMachineBlockEntity[1];
        List<BlockPos> cable = rackWithCable(h, new BlockPos(7, 2, 7), r);
        BigMachineBlockEntity rack = r[0];
        BlockPos console = cable.get(2).above(), terminal = cable.get(3).above(), storage = cable.get(4).above();
        h.getLevel().setBlockAndUpdate(console, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
        h.getLevel().setBlockAndUpdate(terminal, DataRegistry.TERMINAL.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(storage, StorageRegistry.TERMINAL.get().defaultBlockState());
        charge(rack);
        h.runAfterDelay(5, () -> {
            ComputeNet.Status none = ComputeNet.status(h.getLevel(), console);
            if (none.served() || none.modules() != 0 || none.users() != 3) throw new GameTestAssertException("ohne Modul: " + none);
            if (!says(DataConsole.runChecked(h.getLevel(), console, null, "list"), "data.nuclearsq.compute.none")) {
                throw new GameTestAssertException("Konsole arbeitet ohne Rechenleistung");
            }
            if (!says(DataConsole.runChecked(h.getLevel(), console, null, "rechen"), "data.nuclearsq.compute.title")) {
                throw new GameTestAssertException("rechen geht ohne Rechenleistung nicht");
            }
            int before = rack.rack().powerUse();
            rack.rack().drives().setStackInSlot(1, new ItemStack(StorageRegistry.CPU_MODULE.get()));
            if (rack.rack().powerUse() != before + StorageRegistry.CPU_MODULE.get().values().fePerTick()) {
                throw new GameTestAssertException("CPU-Modul braucht keinen Strom: " + before + " -> " + rack.rack().powerUse());
            }
            charge(rack);
        });
        h.runAfterDelay(15, () -> {
            ComputeNet.Status cpuOnly = ComputeNet.status(h.getLevel(), console);
            if (cpuOnly.served() || cpuOnly.cpu() != 4 || cpuOnly.ram() != 0 || cpuOnly.modules() != 1) {
                throw new GameTestAssertException("nur CPU-Modul: " + cpuOnly);
            }
            if (!says(DataConsole.runChecked(h.getLevel(), console, null, "list"), "data.nuclearsq.compute.short")) {
                throw new GameTestAssertException("Konsole meldet fehlendes RAM nicht");
            }
            int before = rack.rack().powerUse();
            rack.rack().drives().setStackInSlot(2, new ItemStack(StorageRegistry.RAM_MODULE.get()));
            if (rack.rack().powerUse() != before + StorageRegistry.RAM_MODULE.get().values().fePerTick()) {
                throw new GameTestAssertException("RAM-Modul braucht keinen Strom: " + before + " -> " + rack.rack().powerUse());
            }
            charge(rack);
        });
        h.runAfterDelay(25, () -> {
            if (!rack.rack().online()) throw new GameTestAssertException("Rack nicht online");
            for (BlockPos p : List.of(console, terminal, storage)) {
                ComputeNet.Status s = ComputeNet.status(h.getLevel(), p);
                if (!s.served()) throw new GameTestAssertException("Geraet " + p + " ohne Rechenleistung: " + s);
            }
            ComputeNet.Status s = ComputeNet.status(h.getLevel(), console);
            if (s.cpu() != 4 || s.ram() != 64 * 1024 || s.cpuUsed() != 3 || s.ramUsed() != 40 * 1024) throw new GameTestAssertException("Werte: " + s);
            if (says(DataConsole.runChecked(h.getLevel(), console, null, "list"), "data.nuclearsq.compute.none")) {
                throw new GameTestAssertException("Konsole meldet trotz Modul keine Rechenleistung");
            }
            DataSource src = DataSources.of(rack);
            if (src == null || src.value("cpu") == null || src.value("cpu").value() != 4 || src.value("ram").value() != 64) {
                throw new GameTestAssertException("Rack zeigt CPU/RAM nicht als Wert");
            }
            int[] codes = rack.rack().syncDue();
            for (int slot = 1; slot <= 2; slot++) {
                int code = codes != null ? codes[slot] : rack.rack().codes()[slot];
                if (ServerRack.tier(code) != ServerRack.COMPUTE) throw new GameTestAssertException("Schacht " + slot + " zeigt das Modul nicht: " + code);
            }
            rack.setEnabled(false);
        });
        h.runAfterDelay(35, () -> {
            if (ComputeNet.status(h.getLevel(), console).served()) throw new GameTestAssertException("Rack aus, Konsole rechnet weiter");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "compute", timeoutTicks = 60)
    public static void computeIsSharedInFixedOrder(GameTestHelper h) {
        BigMachineBlockEntity[] r = new BigMachineBlockEntity[1];
        List<BlockPos> cable = rackWithCable(h, new BlockPos(7, 2, 7), r);
        List<BlockPos> consoles = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BlockPos p = cable.get(i).above();
            h.getLevel().setBlockAndUpdate(p, DataRegistry.CONSOLE.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.NORTH));
            consoles.add(p);
        }
        pair(r[0], 0);
        charge(r[0]);
        h.runAfterDelay(10, () -> {
            consoles.sort(BlockPos::compareTo);
            for (int i = 0; i < consoles.size(); i++) {
                boolean served = ComputeNet.status(h.getLevel(), consoles.get(i)).served();
                if (served != (i < 4)) throw new GameTestAssertException("Konsole " + i + " served=" + served);
            }
            boolean old = NuclearConfig.COMPUTE_REQUIRED.get();
            try {
                NuclearConfig.COMPUTE_REQUIRED.set(false);
                if (!ComputeNet.status(h.getLevel(), consoles.get(4)).served()) throw new GameTestAssertException("Config aus, Konsole steht trotzdem");
            } finally {
                NuclearConfig.COMPUTE_REQUIRED.set(old);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "compute", timeoutTicks = 100)
    public static void wallShowsComputeTileWithoutModule(GameTestHelper h) {
        BigMachineBlockEntity[] r = new BigMachineBlockEntity[1];
        List<BlockPos> cable = rackWithCable(h, new BlockPos(7, 2, 7), r);
        BlockPos origin = cable.get(5).above();
        Direction right = Direction.getNearest(cable.get(4).subtract(cable.get(5)).getX(), 0, cable.get(4).subtract(cable.get(5)).getZ());
        Direction facing = right.getClockWise();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                h.getLevel().setBlockAndUpdate(origin.relative(right, x).above(y),
                        DataRegistry.WALL.get().defaultBlockState().setValue(DataPanelBlock.FACING, facing));
            }
        }
        charge(r[0]);
        h.runAfterDelay(45, () -> {
            if (!(h.getLevel().getBlockEntity(origin) instanceof ControlWallBlockEntity w)) throw new GameTestAssertException("kein Leitstand am Ursprung");
            if (w.tiles().size() != 1 || !(w.tiles().get(0).name().getContents() instanceof TranslatableContents t)
                    || !t.getKey().equals("data.nuclearsq.compute.title")) {
                throw new GameTestAssertException("Leitstand ohne Modul zeigt " + w.tiles().size() + " Kacheln");
            }
            pair(r[0], 0);
            charge(r[0]);
        });
        h.runAfterDelay(90, () -> {
            ControlWallBlockEntity w = (ControlWallBlockEntity) h.getLevel().getBlockEntity(origin);
            if (w.tiles().isEmpty() || !w.tiles().get(0).pos().equals(r[0].getBlockPos())) {
                throw new GameTestAssertException("Leitstand mit Modul zeigt das Rack nicht: " + w.tiles().size());
            }
            h.succeed();
        });
    }
}
