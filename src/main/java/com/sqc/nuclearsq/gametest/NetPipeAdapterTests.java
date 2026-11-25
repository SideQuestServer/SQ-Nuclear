package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.NetPipeAdapterBlock;
import com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.ConduitSide;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class NetPipeAdapterTests {
    private NetPipeAdapterTests() {}

    private static final String FLOOR = "floor";

    private record Setup(BigMachineBlockEntity micro, BlockPos adapter, BlockPos cable, NetPipeAdapterBlockEntity be) {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static BigMachineBlockEntity machine(GameTestHelper h, BlockPos ctrl, BigMachine m) {
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + " ohne BlockEntity");
        return be;
    }

    private static Setup build(GameTestHelper h) {
        BigMachine micro = BigMachine.MICRO_ASSEMBLER, rack = BigMachine.SERVER_RACK_SMALL;
        BlockPos mctrl = h.absolutePos(new BlockPos(2, 2, 2)).subtract(micro.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        BigMachineBlockEntity mbe = machine(h, mctrl, micro);
        BigMachine.Port in = micro.port("item_in");
        BlockPos port = micro.structure.world(mctrl, 0, in.x(), in.y(), in.z());
        BlockPos adapter = port.above(), cable = adapter.above(), rackPort = cable.east();
        BlockPos rctrl = rackPort.subtract(rack.structure.world(BlockPos.ZERO, 0, 0, 1, 1));
        BigMachineBlockEntity rbe = machine(h, rctrl, rack);
        if (rbe.rack() == null) throw new GameTestAssertException("kein Rack-Controller");
        rbe.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        rbe.energy().add(rbe.energy().getMaxEnergyStored());
        h.getLevel().setBlockAndUpdate(cable, DataRegistry.CABLE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(adapter, StorageRegistry.NET_PIPE_ADAPTER.get().defaultBlockState()
                .setValue(NetPipeAdapterBlock.FACING, Direction.DOWN).setValue(NetPipeAdapterBlock.PANEL, Direction.NORTH));
        if (!(h.getLevel().getBlockEntity(adapter) instanceof NetPipeAdapterBlockEntity be)) throw new GameTestAssertException("kein Adapter");
        return new Setup(mbe, adapter, cable, be);
    }

    private static int inMachine(BigMachineBlockEntity be, Item it) {
        int n = 0;
        for (int i = 0; i < be.items().getSlots(); i++) if (be.items().getStackInSlot(i).is(it)) n += be.items().getStackInSlot(i).getCount();
        return n;
    }

    private static long net(GameTestHelper h, Setup s, Item it) {
        return StorageNet.access(h.getLevel(), s.cable()).count(ItemKey.of(new ItemStack(it)));
    }

    private static int chipIndex(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        List<ResourceLocation> ids = MachineRecipes.ids(BigMachine.MICRO_ASSEMBLER, rm);
        for (int i = 0; i < ids.size(); i++) {
            if (rm.byKey(ids.get(i)).orElseThrow().value().getResultItem(h.getLevel().registryAccess()).is(item("chip_1_sqc"))) return i + 1;
        }
        throw new GameTestAssertException("Kein Chip-Rezept");
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 200)
    public static void demandFillsTheMachineFromTheNetwork(GameTestHelper h) {
        Setup s = build(h);
        h.runAfterDelay(25, () -> {
            if (!s.micro().isFormed()) throw new GameTestAssertException("Mikro-Assembler nicht aufgebaut");
            var access = StorageNet.access(h.getLevel(), s.cable());
            if (access.drives() == 0) throw new GameTestAssertException("Netz ohne Festplatte");
            access.insert(ItemKey.of(new ItemStack(item("silicon_wafer_sqc"))), 10, false);
            access.insert(ItemKey.of(new ItemStack(Items.COPPER_INGOT)), 10, false);
            access.insert(ItemKey.of(new ItemStack(Items.REDSTONE)), 20, false);
            access.insert(ItemKey.of(new ItemStack(Items.DIRT)), 30, false);
            s.micro().setRecipeLock(chipIndex(h));
            h.runAfterDelay(80, () -> {
                if (inMachine(s.micro(), item("silicon_wafer_sqc")) != 10 || inMachine(s.micro(), Items.COPPER_INGOT) != 10 || inMachine(s.micro(), Items.REDSTONE) != 20)
                    throw new GameTestAssertException("Zutaten nicht geliefert: " + s.be().problem() + ", Wafer " + inMachine(s.micro(), item("silicon_wafer_sqc"))
                            + ", Kupfer " + inMachine(s.micro(), Items.COPPER_INGOT) + ", Redstone " + inMachine(s.micro(), Items.REDSTONE));
                if (net(h, s, item("silicon_wafer_sqc")) != 0 || net(h, s, Items.REDSTONE) != 0) throw new GameTestAssertException("Netz nicht geleert");
                if (net(h, s, Items.DIRT) != 30 || inMachine(s.micro(), Items.DIRT) != 0) throw new GameTestAssertException("Fremdes Item geliefert");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 200)
    public static void demandWaitsWithoutPattern(GameTestHelper h) {
        Setup s = build(h);
        h.runAfterDelay(25, () -> {
            StorageNet.access(h.getLevel(), s.cable()).insert(ItemKey.of(new ItemStack(Items.REDSTONE)), 20, false);
            h.runAfterDelay(40, () -> {
                if (inMachine(s.micro(), Items.REDSTONE) != 0) throw new GameTestAssertException("Ohne Muster geliefert");
                if (!s.be().problem().equals("no_pattern")) throw new GameTestAssertException("Zustand ohne Muster: " + s.be().problem());
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 200)
    public static void storeTakesWhatThePipeSidePushes(GameTestHelper h) {
        Setup s = build(h);
        h.runAfterDelay(25, () -> {
            s.be().setMode(NetPipeAdapterBlockEntity.STORE);
            IItemHandler front = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.adapter(), Direction.DOWN);
            if (front == null) throw new GameTestAssertException("Rohrseite ohne Item-Anschluss");
            if (h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.adapter(), Direction.NORTH) != null) throw new GameTestAssertException("Item-Anschluss an der Seite");
            if (h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.adapter(), Direction.UP) != null) throw new GameTestAssertException("Item-Anschluss an der Datenbuchse");
            ItemStack rest = front.insertItem(0, new ItemStack(Items.COBBLESTONE, 40), false);
            if (!rest.isEmpty() || net(h, s, Items.COBBLESTONE) != 40) throw new GameTestAssertException("Nicht sofort eingelagert: Rest " + rest.getCount());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 200)
    public static void exportSendsOnlyTheFilterItems(GameTestHelper h) {
        Setup s = build(h);
        h.runAfterDelay(25, () -> {
            var access = StorageNet.access(h.getLevel(), s.cable());
            access.insert(ItemKey.of(new ItemStack(Items.REDSTONE)), 20, false);
            access.insert(ItemKey.of(new ItemStack(Items.DIRT)), 30, false);
            s.be().setMode(NetPipeAdapterBlockEntity.EXPORT);
            s.be().filters().setItem(0, new ItemStack(Items.REDSTONE));
            h.runAfterDelay(60, () -> {
                if (inMachine(s.micro(), Items.REDSTONE) != 20 || net(h, s, Items.REDSTONE) != 0)
                    throw new GameTestAssertException("Filter nicht ausgegeben: " + s.be().problem() + ", Maschine " + inMachine(s.micro(), Items.REDSTONE));
                if (inMachine(s.micro(), Items.DIRT) != 0 || net(h, s, Items.DIRT) != 30) throw new GameTestAssertException("Fremdes Item ausgegeben");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 200)
    public static void exportLetsForeignPipesPullTheFilterItems(GameTestHelper h) {
        Setup s = build(h);
        h.runAfterDelay(25, () -> {
            StorageNet.access(h.getLevel(), s.cable()).insert(ItemKey.of(new ItemStack(Items.REDSTONE)), 20, false);
            IItemHandler front = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.adapter(), Direction.DOWN);
            if (front == null) throw new GameTestAssertException("Rohrseite ohne Item-Anschluss");
            if (!front.extractItem(1, 16, false).isEmpty()) throw new GameTestAssertException("Abholen ohne Ausgeben");
            s.be().setMode(NetPipeAdapterBlockEntity.EXPORT);
            s.be().filters().setItem(0, new ItemStack(Items.REDSTONE));
            ItemStack got = front.extractItem(1, 16, false);
            if (!got.is(Items.REDSTONE) || got.getCount() != 16 || net(h, s, Items.REDSTONE) != 4) throw new GameTestAssertException("Abholen falsch: " + got);
            if (!front.extractItem(2, 16, false).isEmpty()) throw new GameTestAssertException("Leeres Filterfeld liefert");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 200)
    public static void exportWaitsWithoutFilter(GameTestHelper h) {
        Setup s = build(h);
        h.runAfterDelay(25, () -> {
            StorageNet.access(h.getLevel(), s.cable()).insert(ItemKey.of(new ItemStack(Items.REDSTONE)), 20, false);
            s.be().setMode(NetPipeAdapterBlockEntity.EXPORT);
            h.runAfterDelay(30, () -> {
                if (inMachine(s.micro(), Items.REDSTONE) != 0) throw new GameTestAssertException("Ohne Filter ausgegeben");
                if (!s.be().problem().equals("no_filter")) throw new GameTestAssertException("Zustand ohne Filter: " + s.be().problem());
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 100)
    public static void dataCableOnlyAtTheBack(GameTestHelper h) {
        Setup s = build(h);
        BlockPos side = s.adapter().north();
        h.getLevel().setBlockAndUpdate(side, DataRegistry.CABLE.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            if (ConduitBlock.sideAt(h.getLevel(), s.cable(), Direction.DOWN) == ConduitSide.NONE) throw new GameTestAssertException("Kabel an der Datenbuchse nicht verbunden");
            if (ConduitBlock.sideAt(h.getLevel(), side, Direction.SOUTH) != ConduitSide.NONE) throw new GameTestAssertException("Kabel an der Seite verbunden");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "net_pipe_adapter", timeoutTicks = 20)
    public static void wrenchKeepsThePanelBesideTheAxis(GameTestHelper h) {
        BlockState st = StorageRegistry.NET_PIPE_ADAPTER.get().defaultBlockState();
        for (int i = 0; i < 12; i++) {
            st = NetPipeAdapterBlock.turned(st);
            Direction f = st.getValue(NetPipeAdapterBlock.FACING), p = st.getValue(NetPipeAdapterBlock.PANEL);
            if (p.getAxis() == f.getAxis()) throw new GameTestAssertException("Panel auf der Rohrachse bei " + f);
        }
        h.succeed();
    }
}
