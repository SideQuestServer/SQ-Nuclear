package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.DriveContents;
import com.sqc.nuclearsq.storage.DriveItem;
import com.sqc.nuclearsq.storage.DriveStorage;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.ServerRack;
import com.sqc.nuclearsq.storage.ServerRackMenu;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;
import com.sqc.nuclearsq.storage.StorageTerminalMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class StorageTests {
    private StorageTests() {}

    private static final String FLOOR = "floor";

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min);
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) || be.rack() == null) {
            throw new GameTestAssertException(m.id + ": kein Rack-Controller");
        }
        return be;
    }

    private static BlockPos behind(BigMachineBlockEntity be) {
        BlockPos c = be.getBlockPos(), port = be.machine().structure.world(c, 0, 0, 0, 1);
        return port.offset(port.subtract(c));
    }

    private static void cable(GameTestHelper h, BlockPos a, BlockPos b) {
        BlockPos p = a;
        while (true) {
            h.getLevel().setBlockAndUpdate(p, DataRegistry.CABLE.get().defaultBlockState());
            if (p.equals(b)) return;
            p = p.getX() != b.getX() ? p.offset(Integer.signum(b.getX() - p.getX()), 0, 0)
                    : p.getZ() != b.getZ() ? p.offset(0, 0, Integer.signum(b.getZ() - p.getZ()))
                    : p.offset(0, Integer.signum(b.getY() - p.getY()), 0);
        }
    }

    private static void charge(BigMachineBlockEntity be) {
        be.energy().add(be.energy().getMaxEnergyStored());
    }

    private static ItemStack drive(ItemStack s) {
        if (!(s.getItem() instanceof DriveItem)) throw new GameTestAssertException("keine Festplatte: " + s);
        return s;
    }

    @GameTest(template = FLOOR, batch = "storage_net", timeoutTicks = 120)
    public static void storageRacksShareNetwork(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BigMachineBlockEntity b = build(h, BigMachine.SERVER_RACK, new BlockPos(6, 2, 2));
        BlockPos ba = behind(a), bb = behind(b);
        cable(h, ba, bb);
        BlockPos terminal = ba.offset(ba.subtract(a.machine().structure.world(a.getBlockPos(), 0, 0, 0, 1)));
        h.getLevel().setBlockAndUpdate(terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        a.rack().drives().setStackInSlot(0, drive(new ItemStack(StorageRegistry.HARD_DRIVE_1.get())));
        b.rack().drives().setStackInSlot(0, drive(new ItemStack(StorageRegistry.HARD_DRIVE_2.get())));
        charge(a);
        charge(b);
        h.runAfterDelay(25, () -> {
            if (!a.isFormed() || !b.isFormed()) throw new GameTestAssertException("Racks nicht aufgebaut");
            if (!a.rack().online() || !b.rack().online()) throw new GameTestAssertException("Racks mit Strom nicht online");
            StorageAccess s = StorageNet.access(h.getLevel(), terminal);
            if (s.racks() != 2 || s.onlineRacks() != 2 || s.drives() != 2) {
                throw new GameTestAssertException("Terminal sieht " + s.racks() + " Racks / " + s.onlineRacks() + " online / " + s.drives() + " Platten, erwartet 2/2/2");
            }
            ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
            if (s.insert(cobble, 5000, false) != 5000) throw new GameTestAssertException("5000 Bruchstein passen nicht in 4096 + 16384");
            if (s.count(cobble) != 5000) throw new GameTestAssertException("Bestand " + s.count(cobble) + " statt 5000");
            if (s.extract(cobble, 1200, false) != 1200 || s.count(cobble) != 3800) throw new GameTestAssertException("Auslagern stimmt nicht");
            b.rack().setPriority(5);
            StorageAccess s2 = StorageNet.access(h.getLevel(), terminal);
            s2.insert(ItemKey.of(new ItemStack(Items.DIAMOND)), 10, false);
            DriveContents cb = b.rack().contents(h.getLevel().getServer(), 0);
            if (cb == null || cb.count(ItemKey.of(new ItemStack(Items.DIAMOND))) != 10) throw new GameTestAssertException("Diamanten nicht im Rack mit Prioritaet 5");
            long before = s2.count(cobble);
            ItemStack moved = a.rack().drives().extractItem(0, 1, false);
            long onA = DriveStorage.get(h.getLevel().getServer()).contents(DriveItem.id(moved)).total();
            if (StorageNet.access(h.getLevel(), terminal).count(cobble) != before - onA) throw new GameTestAssertException("ausgesteckte Platte zaehlt noch");
            b.rack().drives().setStackInSlot(1, moved);
            if (StorageNet.access(h.getLevel(), terminal).count(cobble) != before) throw new GameTestAssertException("umgesteckte Platte hat ihren Inhalt verloren");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_row", timeoutTicks = 200)
    public static void storageTerminalSeesAllRacksInARow(GameTestHelper h) {
        int n = 6;
        BigMachineBlockEntity[] r = new BigMachineBlockEntity[n];
        for (int i = 0; i < n; i++) r[i] = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2 + i, 2, 2));
        BigMachineBlockEntity big = build(h, BigMachine.SERVER_RACK, new BlockPos(2 + n, 2, 2));
        BlockPos out = r[0].machine().structure.world(r[0].getBlockPos(), 0, 0, 0, 1).subtract(r[0].getBlockPos());
        BlockPos start = r[0].machine().structure.world(r[0].getBlockPos(), 0, 0, 1, 1).offset(out).offset(-1, 0, 0);
        BlockPos end = big.machine().structure.world(big.getBlockPos(), 0, 1, 0, 1).offset(out);
        cable(h, start, end);
        BlockPos terminal = start.below(), monitor = start.above();
        h.getLevel().setBlockAndUpdate(terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(monitor, StorageRegistry.MONITOR.get().defaultBlockState());
        for (BigMachineBlockEntity be : r) charge(be);
        charge(big);
        big.rack().drives().setStackInSlot(0, drive(new ItemStack(StorageRegistry.HARD_DRIVE_2.get())));
        ItemStack original = drive(new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        r[0].rack().drives().setStackInSlot(0, original);
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        h.runAfterDelay(25, () -> {
            if (StorageNet.access(h.getLevel(), terminal).insert(cobble, 100, false) != 100) throw new GameTestAssertException("Einlagern klappt nicht");
            ItemStack inRack = r[0].rack().drives().getStackInSlot(0);
            if (!(inRack.getItem() instanceof DriveItem)) throw new GameTestAssertException("Platte nicht im ersten Rack");
            for (int i = 1; i < n; i++) r[i].rack().drives().setStackInSlot(0, inRack.copy());
        });
        h.runAfterDelay(70, () -> {
            for (BigMachineBlockEntity be : r) if (!be.isFormed() || !be.rack().online()) throw new GameTestAssertException("Rack " + be.getBlockPos() + " nicht aufgebaut oder ohne Strom");
            java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
            for (BigMachineBlockEntity be : r) ids.add(DriveItem.id(be.rack().drives().getStackInSlot(0)));
            if (ids.size() != n) throw new GameTestAssertException("Kopierte Platten teilen sich noch Kennungen: " + ids.size() + " statt " + n);
            StorageAccess s = StorageNet.access(h.getLevel(), terminal);
            if (s.racks() != n + 1 || s.onlineRacks() != n + 1 || s.drives() != n + 1) {
                throw new GameTestAssertException("Terminal sieht " + s.racks() + " Racks / " + s.onlineRacks() + " online / " + s.drives() + " Platten, erwartet " + (n + 1));
            }
            if (s.capacity() != n * 4096L + 16384) throw new GameTestAssertException("Platz " + s.capacity() + " statt " + (n * 4096L + 16384));
            if (s.count(cobble) != 100) throw new GameTestAssertException("Kopien haben Items dazubekommen: " + s.count(cobble) + " statt 100");
            DriveContents c0 = r[0].rack().contents(h.getLevel().getServer(), 0);
            if (c0 == null || c0.count(cobble) != 100) throw new GameTestAssertException("Original hat seinen Inhalt verloren");
            if (!(h.getLevel().getBlockEntity(monitor) instanceof com.sqc.nuclearsq.storage.StorageMonitorBlockEntity mon)) throw new GameTestAssertException("kein Speicher-Monitor");
            if (mon.rows().size() != n + 1 || mon.online() != n + 1) throw new GameTestAssertException("Monitor listet " + mon.rows().size() + " Racks (" + mon.online() + " online), erwartet " + (n + 1));
            if (mon.capacity() != n * 4096L + 16384) throw new GameTestAssertException("Monitor: Gesamtspeicher " + mon.capacity());
            if (mon.used() != 100) throw new GameTestAssertException("Monitor: belegt " + mon.used() + " statt 100");
            if (mon.comparator() != 1) throw new GameTestAssertException("Komparator am Monitor " + mon.comparator() + " statt 1");
            ItemStack moved = r[n - 1].rack().drives().extractItem(0, 1, false);
            java.util.UUID id = DriveItem.id(moved);
            r[1].rack().drives().setStackInSlot(1, moved);
            h.runAfterDelay(5, () -> {
                if (!id.equals(DriveItem.id(r[1].rack().drives().getStackInSlot(1)))) throw new GameTestAssertException("umgesteckte Platte hat eine neue Kennung bekommen");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "storage_monitor", timeoutTicks = 80)
    public static void storageMonitorsFormOneScreen(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BlockPos ba = behind(a);
        cable(h, ba, ba.offset(4, 0, 0));
        net.minecraft.world.level.block.state.BlockState st = StorageRegistry.MONITOR.get().defaultBlockState()
                .setValue(com.sqc.nuclearsq.data.DataPanelBlock.FACING, Direction.SOUTH);
        BlockPos o = ba.offset(1, 1, 0);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) h.getLevel().setBlockAndUpdate(o.offset(x, y, 0), st);
        charge(a);
        h.runAfterDelay(45, () -> {
            var l = com.sqc.nuclearsq.storage.StorageMonitorBlock.layout(h.getLevel(), o.offset(1, 1, 0));
            if (l.w() != 2 || l.h() != 2) throw new GameTestAssertException("Monitore bilden keinen 2x2-Bildschirm: " + l.w() + "x" + l.h());
            if (!(h.getLevel().getBlockEntity(l.origin()) instanceof com.sqc.nuclearsq.storage.StorageMonitorBlockEntity mon) || mon.rows().size() != 1) {
                throw new GameTestAssertException("Ursprung " + l.origin() + " listet das Rack nicht");
            }
            for (BlockPos p : l.cells()) {
                if (!p.equals(l.origin()) && h.getLevel().getBlockEntity(p) instanceof com.sqc.nuclearsq.storage.StorageMonitorBlockEntity other && !other.rows().isEmpty()) {
                    throw new GameTestAssertException("Monitor " + p + " sammelt auch");
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_monitor", timeoutTicks = 80)
    public static void storageMonitorMenuKeepsSettings(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BlockPos ba = behind(a);
        cable(h, ba, ba.offset(2, 0, 0));
        BlockPos o = ba.offset(1, 1, 0);
        h.getLevel().setBlockAndUpdate(o, StorageRegistry.MONITOR.get().defaultBlockState()
                .setValue(com.sqc.nuclearsq.data.DataPanelBlock.FACING, Direction.SOUTH));
        h.runAfterDelay(45, () -> {
            if (!(h.getLevel().getBlockEntity(o) instanceof com.sqc.nuclearsq.storage.StorageMonitorBlockEntity mon) || mon.rows().size() != 1 || mon.online() != 0) {
                throw new GameTestAssertException("Monitor sieht das Rack ohne Strom nicht");
            }
            ServerPlayer p = AuditFixTests.testPlayer(h);
            var menu = new com.sqc.nuclearsq.storage.StorageMonitorMenu(0, o);
            if (!menu.clickMenuButton(p, com.sqc.nuclearsq.storage.StorageMonitorBlockEntity.HIDE * 4 + 1)) throw new GameTestAssertException("Ausblenden abgelehnt");
            if (!mon.sorted(false).isEmpty() || mon.sorted(true).size() != 1) throw new GameTestAssertException("Rack ohne Strom nicht ausgeblendet");
            if (mon.comparator() != 0 || mon.rows().size() != 1) throw new GameTestAssertException("Ausblenden aendert die Daten");
            menu.clickMenuButton(p, com.sqc.nuclearsq.storage.StorageMonitorBlockEntity.PAGE * 4 + 2);
            menu.clickMenuButton(p, com.sqc.nuclearsq.storage.StorageMonitorBlockEntity.WARN * 4 + 1);
            if (menu.clickMenuButton(p, com.sqc.nuclearsq.storage.StorageMonitorBlockEntity.HIDE * 4 + 2)) throw new GameTestAssertException("ungueltiger Wert angenommen");
            if (mon.pageTicks() != 600 || mon.warnFrom() != 0.8f) throw new GameTestAssertException("Einstellung nicht gesetzt");
            var reg = h.getLevel().registryAccess();
            var copy = new com.sqc.nuclearsq.storage.StorageMonitorBlockEntity(o, mon.getBlockState());
            copy.loadWithComponents(mon.saveWithoutMetadata(reg), reg);
            if (copy.setting(com.sqc.nuclearsq.storage.StorageMonitorBlockEntity.HIDE) != 1 || copy.pageTicks() != 600 || copy.warnFrom() != 0.8f || copy.rows().size() != 1) {
                throw new GameTestAssertException("Einstellungen nach Neuladen weg");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_space", timeoutTicks = 100)
    public static void serverRackShowsSpace(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BlockPos ba = behind(a);
        cable(h, ba, ba);
        BlockPos terminal = ba.offset(ba.subtract(a.machine().structure.world(a.getBlockPos(), 0, 0, 0, 1)));
        h.getLevel().setBlockAndUpdate(terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        ServerRack.Space empty = a.rack().space();
        if (empty.drives() != 0 || empty.capacity() != 0 || empty.free() != 0) throw new GameTestAssertException("leeres Rack zeigt Platz: " + empty);
        ItemStack hd1 = new ItemStack(StorageRegistry.HARD_DRIVE_1.get()), hd2 = new ItemStack(StorageRegistry.HARD_DRIVE_2.get());
        long cap = ((DriveItem) hd1.getItem()).limits().items() + ((DriveItem) hd2.getItem()).limits().items();
        int types = ((DriveItem) hd1.getItem()).limits().types() + ((DriveItem) hd2.getItem()).limits().types();
        a.rack().drives().setStackInSlot(0, hd1);
        a.rack().drives().setStackInSlot(3, hd2);
        charge(a);
        h.runAfterDelay(25, () -> {
            if (!a.rack().online()) throw new GameTestAssertException("Rack mit Strom nicht online");
            ServerRack.Space before = a.rack().space();
            if (before.drives() != 2 || before.capacity() != cap || before.used() != 0 || before.free() != cap || before.typeCapacity() != types) {
                throw new GameTestAssertException("Platz mit zwei leeren Platten: " + before + ", erwartet " + cap + " frei");
            }
            StorageAccess s = StorageNet.access(h.getLevel(), terminal);
            if (s.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 5000, false) != 5000
                    || s.insert(ItemKey.of(new ItemStack(Items.DIRT)), 300, false) != 300) {
                throw new GameTestAssertException("Einlagern ging nicht");
            }
            ServerRack.Space after = a.rack().space();
            if (after.used() != 5300 || after.free() != cap - 5300 || after.types() != 3) {
                throw new GameTestAssertException("Platz nach 5300 Items (Sortenplaetze 3): " + after);
            }
            ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
            ServerRackMenu menu = new ServerRackMenu(0, p.getInventory(), a.getBlockPos(), a.rack().drives(), ServerRackMenu.data(a));
            if (!menu.space().equals(after)) throw new GameTestAssertException("Fenster zeigt " + menu.space() + " statt " + after);
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_power", timeoutTicks = 100)
    public static void serverRackNeedsPower(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        a.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.SOLID_STATE_DRIVE.get()));
        BlockPos c = a.getBlockPos();
        if (a.rack().powerUse() != BigMachine.SERVER_RACK_SMALL.energy.use() + 24) throw new GameTestAssertException("Verbrauch mit SSD: " + a.rack().powerUse());
        h.runAfterDelay(20, () -> {
            if (a.rack().online()) throw new GameTestAssertException("ohne Strom online");
            if (StorageNet.access(h.getLevel(), c).insert(ItemKey.of(new ItemStack(Items.DIRT)), 10, false) != 0) {
                throw new GameTestAssertException("ohne Strom eingelagert");
            }
            charge(a);
            h.runAfterDelay(5, () -> {
                if (!a.rack().online()) throw new GameTestAssertException("mit Strom nicht online");
                if (StorageNet.access(h.getLevel(), c).insert(ItemKey.of(new ItemStack(Items.DIRT)), 10, false) != 10) {
                    throw new GameTestAssertException("mit Strom nicht eingelagert");
                }
                a.energy().setStored(0);
                h.runAfterDelay(5, () -> {
                    if (a.rack().online()) throw new GameTestAssertException("nach Stromausfall noch online");
                    DriveContents d = a.rack().contents(h.getLevel().getServer(), 0);
                    if (d == null || d.total() != 10) throw new GameTestAssertException("Inhalt nach Stromausfall weg");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "storage_limits", timeoutTicks = 20)
    public static void driveLimits(GameTestHelper h) {
        DriveItem d = StorageRegistry.HARD_DRIVE_1.get();
        var lim = d.limits();
        if (lim.items() != 4096 || lim.types() != 32) throw new GameTestAssertException("Werte Festplatte I: " + lim);
        DriveContents c = new DriveContents();
        int n = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || n > lim.types()) continue;
            long got = c.insert(ItemKey.of(new ItemStack(item)), 1, lim, false);
            if (n < lim.types() && got != 1) throw new GameTestAssertException("Sorte " + n + " nicht angenommen");
            if (n == lim.types() && got != 0) throw new GameTestAssertException("33. Sorte angenommen");
            n++;
        }
        ItemKey first = c.items().keySet().iterator().next();
        long got = c.insert(first, 10_000, lim, false);
        if (c.total() != lim.items() || got != lim.items() - lim.types()) throw new GameTestAssertException("Item-Grenze: " + c.total());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "storage_menu", timeoutTicks = 60)
    public static void storageTerminalMenu(GameTestHelper h) {
        BigMachineBlockEntity a = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        a.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        charge(a);
        BlockPos term = behind(a);
        h.getLevel().setBlockAndUpdate(term, StorageRegistry.TERMINAL.get().defaultBlockState());
        h.runAfterDelay(10, () -> {
            ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            StorageTerminalMenu m = new StorageTerminalMenu(1, p.getInventory(), term);
            int hotbar0 = 27;
            m.quickMoveStack(p, hotbar0);
            if (!p.getInventory().getItem(0).isEmpty()) throw new GameTestAssertException("Stapel nicht eingelagert");
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            if (StorageNet.access(h.getLevel(), term).count(iron) != 64) throw new GameTestAssertException("Netz hat keine 64 Eisen");
            m.action(p, new ItemStack(Items.IRON_INGOT), StorageTerminalMenu.TAKE_SHIFT);
            if (p.getInventory().countItem(Items.IRON_INGOT) != 64) throw new GameTestAssertException("Stapel nicht im Inventar");
            if (StorageNet.access(h.getLevel(), term).count(iron) != 0) throw new GameTestAssertException("Eisen noch im Netz");
            m.setCarried(new ItemStack(Items.GOLD_INGOT, 5));
            m.action(p, ItemStack.EMPTY, StorageTerminalMenu.INSERT_ONE);
            if (m.getCarried().getCount() != 4) throw new GameTestAssertException("Rechtsklick lagert nicht genau eins ein");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "storage_kit", timeoutTicks = 60)
    public static void serverRackKitAndRecipes(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        for (String id : new String[] {"assembly/storage_terminal_sqc", "small_parts_assembly/server_rack_small_kit_sqc", "heavy_assembly/server_rack_kit_sqc", "heavy_assembly/server_rack_large_kit_sqc",
                "small_parts_assembly/hard_drive_1_sqc", "small_parts_assembly/hard_drive_2_sqc", "small_parts_assembly/hard_drive_3_sqc",
                "small_parts_assembly/solid_state_drive_sqc", "small_parts_assembly/nvme_drive_sqc"}) {
            if (rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id)).isEmpty()) throw new GameTestAssertException("Rezept fehlt: " + id);
        }
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("server_rack_large"), h.absolutePos(new BlockPos(7, 4, 4)), Direction.SOUTH);
        var blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException("Platz gilt als belegt: " + blocked);
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(10, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || be.rack() == null) {
                throw new GameTestAssertException("Kein Server-Rack aufgestellt");
            }
            if (!be.isFormed() || be.rack().slots() != 40) throw new GameTestAssertException("Grosses Rack nicht fertig oder nicht 40 Schaechte");
            be.rack().drives().setStackInSlot(3, new ItemStack(StorageRegistry.HARD_DRIVE_3.get()));
            if (ServerRack.tier(be.rack().syncDue()[3]) != 3) throw new GameTestAssertException("Anzeige zeigt die Stufe nicht");
            be.rack().drives().setStackInSlot(4, new ItemStack(StorageRegistry.NVME_DRIVE.get()));
            if (ServerRack.tier(be.rack().syncDue()[4]) != 5) throw new GameTestAssertException("Anzeige zeigt die NVMe nicht");
            var nvme = StorageRegistry.NVME_DRIVE.get().limits();
            if (nvme.items() != 1048576 || nvme.types() != 63 || nvme.fePerTick() != 48) throw new GameTestAssertException("NVMe-Werte: " + nvme);
            BlockPos part = null;
            for (MultiblockStructure.Cell c : pl.cells()) if (!c.pos().equals(pl.anchor())) { part = c.pos(); break; }
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(part);
            var near = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.anchor()).inflate(8));
            if (near.stream().noneMatch(e -> e.getItem().is(KitRegistry.KITS.get("server_rack_large").get()))) throw new GameTestAssertException("Kit faellt nicht heraus");
            if (near.stream().noneMatch(e -> e.getItem().is(StorageRegistry.HARD_DRIVE_3.get()))) throw new GameTestAssertException("Festplatte faellt nicht heraus");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "compendium_achievements", timeoutTicks = 20)
    public static void achievementTreeIsComplete(GameTestHelper h) {
        var nodes = com.sqc.nuclearsq.progression.AchievementTree.NODES;
        java.util.Set<String> keys = new java.util.HashSet<>(), cells = new java.util.HashSet<>();
        for (var n : nodes) {
            if (!keys.add(n.key())) throw new GameTestAssertException("Knoten " + n.key() + " doppelt");
            if (!cells.add(n.col() + "," + n.row())) throw new GameTestAssertException(n.key() + ": Feld schon belegt");
            if (BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, n.item())) == Items.AIR) {
                throw new GameTestAssertException(n.key() + ": Item " + n.item() + " ist nicht registriert");
            }
        }
        java.util.Set<String> items = new java.util.HashSet<>();
        for (var n : nodes) items.add(n.item());
        for (String kit : com.sqc.nuclearsq.kit.KitRegistry.IDS) {
            if (!items.contains(kit + "_controller_sqc")) throw new GameTestAssertException("Maschine " + kit + " fehlt in der Errungenschaften-Liste");
        }
        java.util.List<com.sqc.nuclearsq.progression.PlantOverview.Node> others = new java.util.ArrayList<>(com.sqc.nuclearsq.progression.PlantOverview.NODES);
        others.addAll(com.sqc.nuclearsq.progression.AssemblyTree.NODES);
        for (var n : others) if (!items.contains(n.item())) throw new GameTestAssertException(n.key() + " fehlt in der Errungenschaften-Liste");
        for (var n : nodes) {
            if (n.group() == com.sqc.nuclearsq.progression.PlantOverview.Group.WEAPON) throw new GameTestAssertException(n.key() + ": Waffe gehoert in den Secret-Weapons-Baum");
            for (String proof : com.sqc.nuclearsq.progression.AchievementTree.proofItems(n)) {
                if (BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, proof)) == Items.AIR) {
                    throw new GameTestAssertException(n.key() + ": " + proof + " ist nicht registriert");
                }
            }
        }
        for (String lang : new String[] {"de_de", "en_us"}) {
            com.google.gson.JsonObject l;
            try (var in = StorageTests.class.getResourceAsStream("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json")) {
                l = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                throw new GameTestAssertException(lang + " nicht lesbar: " + ex);
            }
            for (String k : new String[] {"compendium.achievements.chapter", "compendium.ach_overview.title", "compendium.ach_overview.text",
                    "compendium.catalog_all.title", "compendium.ach.progress", "compendium.ach.items_progress", "compendium.ach.built",
                    "compendium.ach.missing", "compendium.made_in.other", "compendium.rod_reinforced.title", "compendium.rod_reinforced.text",
                    "compendium.anim.hulls_reinforced", "compendium.section.grp_manufacturing", "compendium.storage.chapter", "compendium.ach.next",
                    "compendium.search.machines", "compendium.catalog.misc"}) {
                if (!l.has(k)) throw new GameTestAssertException(lang + ": " + k + " fehlt");
            }
            for (var st : com.sqc.nuclearsq.progression.AchievementTree.STAGES) {
                String k = "compendium.ach.stage." + st.key();
                if (!l.has(k)) throw new GameTestAssertException(lang + ": " + k + " fehlt");
                if (nodes.stream().noneMatch(n -> n.key().equals(st.first()))) throw new GameTestAssertException("Etappe " + st.key() + ": Knoten " + st.first() + " fehlt");
            }
            for (var n : nodes) {
                for (String part : new String[] {"name", "short"}) {
                    if (!l.has("compendium.plant." + n.key() + "." + part)) throw new GameTestAssertException(lang + ": compendium.plant." + n.key() + "." + part + " fehlt");
                }
                if (!l.has("compendium." + n.page() + ".title") || !l.has("compendium." + n.page() + ".text")) {
                    throw new GameTestAssertException(lang + ": Seite " + n.page() + " ohne Titel oder Text");
                }
                String g = "compendium.plant.group." + n.group().name().toLowerCase(java.util.Locale.ROOT);
                if (!l.has(g)) throw new GameTestAssertException(lang + ": " + g + " fehlt");
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_achievements", timeoutTicks = 20)
    public static void discoveredItemsStayKnown(GameTestHelper h) {
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.getInventory().clearContent();
        sp.setData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get(), new java.util.LinkedHashSet<>());
        if (com.sqc.nuclearsq.progression.Discovered.known(sp, "solar_panel_sqc")) throw new GameTestAssertException("Solarmodul schon bekannt");
        sp.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "solar_panel_sqc"))));
        sp.getInventory().add(new ItemStack(Items.DIRT));
        if (!com.sqc.nuclearsq.progression.Discovered.scan(sp)) throw new GameTestAssertException("Neues Item nicht erkannt");
        if (com.sqc.nuclearsq.progression.Discovered.scan(sp)) throw new GameTestAssertException("Zweiter Blick meldet Neues");
        sp.getInventory().clearContent();
        if (!com.sqc.nuclearsq.progression.Discovered.known(sp, "solar_panel_sqc")) throw new GameTestAssertException("Solarmodul vergessen");
        if (sp.getData(com.sqc.nuclearsq.progression.Discovered.ITEMS.get()).contains("dirt")) throw new GameTestAssertException("Fremdes Item gezaehlt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "compendium_achievements", timeoutTicks = 20)
    public static void achievementOrderFollowsRecipes(GameTestHelper h) {
        var level = h.getLevel();
        var sources = com.sqc.nuclearsq.progression.RecipeSources.all(level.getRecipeManager(), level.registryAccess());
        java.util.Set<net.minecraft.world.item.Item> craftable = new java.util.HashSet<>();
        for (var s : sources) craftable.add(s.result().getItem());
        java.util.Set<com.sqc.nuclearsq.progression.RecipeSources.Where> open = java.util.EnumSet.of(
                com.sqc.nuclearsq.progression.RecipeSources.Where.WORKBENCH, com.sqc.nuclearsq.progression.RecipeSources.Where.FURNACE,
                com.sqc.nuclearsq.progression.RecipeSources.Where.BLAST_FURNACE);
        java.util.Set<net.minecraft.world.item.Item> have = new java.util.HashSet<>();
        java.util.Set<String> passed = new java.util.HashSet<>();
        var nodes = com.sqc.nuclearsq.progression.AchievementTree.NODES;
        for (int k = 0; k < nodes.size(); k++) {
            boolean more = true;
            while (more) {
                more = false;
                for (var s : sources) {
                    if (!open.contains(s.where()) || have.contains(s.result().getItem()) || !station(s, passed)) continue;
                    boolean ok = true;
                    for (var need : s.needs()) {
                        boolean any = false;
                        for (ItemStack st : need.ingredient().getItems()) {
                            boolean mod = BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals(NuclearSQ.MODID);
                            if (!mod || !craftable.contains(st.getItem()) || have.contains(st.getItem())) { any = true; break; }
                        }
                        if (!any) { ok = false; break; }
                    }
                    if (ok) { have.add(s.result().getItem()); more = true; }
                }
            }
            var n = nodes.get(k);
            boolean built = false;
            for (String proof : com.sqc.nuclearsq.progression.AchievementTree.proofItems(n)) {
                var it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, proof));
                if (have.contains(it) || !craftable.contains(it)) built = true;
            }
            if (!built) throw new GameTestAssertException((k + 1) + ". " + n.key() + " laesst sich mit den Maschinen davor nicht herstellen");
            var unlock = com.sqc.nuclearsq.progression.AchievementTree.unlocks(n.key());
            if (unlock != null) open.add(unlock);
            passed.add(n.key());
        }
        h.succeed();
    }

    private static boolean station(com.sqc.nuclearsq.progression.RecipeSources.Source s, java.util.Set<String> passed) {
        if (s.where() != com.sqc.nuclearsq.progression.RecipeSources.Where.SMALL_PARTS_ASSEMBLER) return true;
        ItemStack r = s.result();
        if (r.is(com.sqc.nuclearsq.machine.big.BigCrafter.MICRO_PRODUCTS)) return passed.contains("micro");
        if (r.is(com.sqc.nuclearsq.machine.big.BigCrafter.CLEAN_ROOM_PRODUCTS)) return passed.contains("clean_room") && passed.contains("robot_arm");
        if (r.is(com.sqc.nuclearsq.machine.big.BigCrafter.SOLDER_PRODUCTS)) return passed.contains("solder_machine");
        return true;
    }
}
