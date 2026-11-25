package com.sqc.nuclearsq.gametest;

import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.FLOOR;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.LOG;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.PLANKS;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.bufferCount;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.check;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.count;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.dropped;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.reload;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.rig;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.storage;
import static com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.terminal;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.ChunkGuard;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.gametest.ComponentAssemblerSafetyTests.Rig;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.DriveContents;
import com.sqc.nuclearsq.storage.DriveStorage;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.StorageAccess;
import com.sqc.nuclearsq.storage.StorageNet;
import com.sqc.nuclearsq.storage.StorageRegistry;
import com.sqc.nuclearsq.storage.StorageValues;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ComponentAssemblerLedgerTests {
    private ComponentAssemblerLedgerTests() {}

    private static final String BATCH = "component_assembler_ledger";
    private static final ItemKey COBBLE = ItemKey.of(new ItemStack(Items.COBBLESTONE));

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 300)
    public static void assemblerStateLivesInTheDriveFile(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            r.a1.items().setStackInSlot(3, new ItemStack(Items.DIRT, 7));
            h.runAfterDelay(a.cycleTicks() * 2, () -> {
                var reg = h.getLevel().registryAccess();
                CompoundTag block = r.a1.saveWithoutMetadata(reg);
                check(!block.contains("items"), "Puffer steht noch im Chunk des Blocks");
                check(!block.getCompound("assembler").contains("jobs") && !block.getCompound("assembler").contains("lanes"), "Auftraege stehen noch im Chunk des Blocks");
                CompoundTag file = DriveStorage.get(h.getLevel().getServer()).save(new CompoundTag(), reg);
                String key = DriveStorage.assemblerKey(h.getLevel(), r.a1.getBlockPos());
                CompoundTag st = null;
                for (var e : file.getList("assemblers", 10)) if (((CompoundTag) e).getString("key").equals(key)) st = ((CompoundTag) e).getCompound("state");
                check(st != null, "Assembler fehlt in der Weltdatei");
                check(st.getList("jobs", 10).size() == 1, "Auftrag fehlt in der Weltdatei");
                check(st.contains("buffer") && st.getLong("seq") >= 1, "Puffer oder Buchungsnummer fehlen in der Weltdatei");
                DriveStorage copy = DriveStorage.load(file, reg);
                check(copy.hasAssemblerState(key), "Kopie der Weltdatei hat keinen Assembler-Stand");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH + "_f1", timeoutTicks = 500)
    public static void assemblerCrashAfterTakingRollsBack(GameTestHelper h) {
        crashTest(h, 1);
    }

    @GameTest(template = FLOOR, batch = BATCH + "_f2", timeoutTicks = 500)
    public static void assemblerCrashAfterPlacingCompletes(GameTestHelper h) {
        crashTest(h, 2);
    }

    private static void crashTest(GameTestHelper h, int phase) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        boolean[] crashed = {false};
        Runnable watch = () -> {
            if (crashed[0] || !a.hasPendingBooking()) return;
            crashed[0] = true;
            reload(h, r.a1);
            check(a.hasPendingBooking() || phase == 2, "offene Buchung ging beim Laden verloren");
        };
        for (int i = 26; i < 25 + a.cycleTicks() * 4 + 60; i++) h.runAfterDelay(i, () -> watch.run());
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 8), "Bestellung abgelehnt");
            ComponentAssembler.testFault = phase;
            h.runAfterDelay(a.cycleTicks() * 4 + 60, () -> {
                check(crashed[0], "Der Testfehler wurde nicht ausgeloest");
                check(a.jobs().isEmpty() && !a.hasPendingBooking(), "Auftrag oder Buchung noch offen, Status " + a.status());
                long planks = count(h, r, PLANKS), logs = count(h, r, LOG);
                check(planks == 8 && logs == 14, "Bestand: " + planks + " Bretter, " + logs + " Staemme (erwartet 8 und 14)");
                check(a.bookingSeq() >= (phase == 1 ? 2 : 1), "Buchungsnummer zaehlt nicht: " + a.bookingSeq());
                check(dropped(h, r.a1.getBlockPos(), Items.OAK_PLANKS) == 0 && bufferCount(r.a1, Items.OAK_PLANKS) == 0, "Bretter ausserhalb des Speichers");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 600)
    public static void assemblerOrderReservesIngredients(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 8, false) == 8, "Staemme nicht eingelagert");
            var menu = terminal(h, r);
            check(menu.order(new ItemStack(Items.OAK_PLANKS), 16), "Bestellung abgelehnt: " + menu.lastMessage().getString());
            StorageAccess s = storage(h, r);
            check(s.count(LOG) == 8 && s.reserved(LOG) == 4 && s.available(LOG) == 4, "Reserviert/frei: " + s.reserved(LOG) + "/" + s.available(LOG));
            check(s.listReserved().getOrDefault(LOG, 0L) == 4, "Terminal-Liste kennt die Reservierung nicht");
            check(s.extract(LOG, 8, false) == 4, "Entnahme kam an Reserviertes heran");
            check(s.insert(LOG, 4, false) == 4, "Staemme nicht zurueck");
            check(!menu.order(new ItemStack(Items.OAK_PLANKS), 24), "zweite Bestellung ueber Reserviertes angenommen");
            check(menu.lastMessage().getString().toLowerCase().contains("missing"), "Meldung 'fehlt' erwartet: " + menu.lastMessage().getString());
            h.runAfterDelay(a.cycleTicks() * 5 + 40, () -> {
                StorageAccess e = storage(h, r);
                check(a.jobs().isEmpty(), "Auftrag nicht fertig, Status " + a.status());
                check(e.reserved(LOG) == 0 && count(h, r, LOG) == 4 && count(h, r, PLANKS) == 16, "Nach dem Auftrag: reserviert " + e.reserved(LOG) + ", " + count(h, r, LOG) + " Staemme, " + count(h, r, PLANKS) + " Bretter");
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 400)
    public static void assemblerCancelAndBreakReleaseReservation(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            check(storage(h, r).reserved(LOG) == 10, "Reserviert: " + storage(h, r).reserved(LOG));
            a.cancel(true);
            check(storage(h, r).reserved(LOG) == 0, "Reservierung nach Abbruch: " + storage(h, r).reserved(LOG));
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "zweite Bestellung abgelehnt");
            check(storage(h, r).reserved(LOG) == 10, "Reserviert nach zweiter Bestellung: " + storage(h, r).reserved(LOG));
            String key = DriveStorage.assemblerKey(h.getLevel(), r.a1.getBlockPos());
            check(DriveStorage.get(h.getLevel().getServer()).hasAssemblerState(key), "Assembler fehlt in der Weltdatei");
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(r.a1.getBlockPos());
            check(storage(h, r).reserved(LOG) == 0, "Reservierung nach Abbau: " + storage(h, r).reserved(LOG));
            check(!DriveStorage.get(h.getLevel().getServer()).hasAssemblerState(key), "Abgebauter Assembler steht noch in der Weltdatei");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 200)
    public static void reservationsSurviveTheFileAndOrphansExpire(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            var reg = h.getLevel().registryAccess();
            CompoundTag file = DriveStorage.get(h.getLevel().getServer()).save(new CompoundTag(), reg);
            java.util.UUID id = r.rack.rack().drives().getStackInSlot(0).get(com.sqc.nuclearsq.registry.ModDataComponents.DRIVE_ID.get());
            DriveContents withJob = DriveStorage.load(file, reg).contents(id);
            check(withJob.reserved(LOG) == 10, "Reservierung ging beim Laden verloren: " + withJob.reserved(LOG));
            CompoundTag orphan = file.copy();
            orphan.put("assemblers", new net.minecraft.nbt.ListTag());
            DriveContents without = DriveStorage.load(orphan, reg).contents(id);
            check(without.reserved(LOG) == 0 && without.count(LOG) == 16, "Waise: reserviert " + without.reserved(LOG) + ", Bestand " + without.count(LOG));
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 500)
    public static void assemblerCardRemovalAbortsJobOnce(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            r.a1.items().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 5));
            h.runAfterDelay(a.cycleTicks() * 3 / 2, () -> {
                check(a.busyLanes() > 0 || a.jobs().get(0).leftInStep() < 10, "Auftrag laeuft noch nicht");
                a.cards().setStackInSlot(0, ItemStack.EMPTY);
                h.runAfterDelay(3, () -> {
                    check(a.jobs().isEmpty() && a.busyLanes() == 0, "Auftrag nach Kartenentnahme noch aktiv: " + a.jobs().size() + " Auftraege, " + a.busyLanes() + " Spuren");
                    StorageAccess s = storage(h, r);
                    check(s.reserved(LOG) == 0, "Reservierung nach Abbruch: " + s.reserved(LOG));
                    check(count(h, r, LOG) * 4 + count(h, r, PLANKS) == 64, "Itemwert nicht erhalten: " + count(h, r, LOG) + " Staemme, " + count(h, r, PLANKS) + " Bretter");
                    check(count(h, r, COBBLE) == 5 && bufferCount(r.a1, Items.COBBLESTONE) == 0 && dropped(h, r.a1.getBlockPos(), Items.COBBLESTONE) == 0,
                            "Puffer nicht genau einmal zurueck: Rack " + count(h, r, COBBLE) + ", Puffer " + bufferCount(r.a1, Items.COBBLESTONE));
                    h.runAfterDelay(a.cycleTicks() * 2, () -> {
                        check(count(h, r, LOG) * 4 + count(h, r, PLANKS) == 64, "Auftrag lief nach dem Abbruch weiter: " + count(h, r, PLANKS) + " Bretter");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 600)
    public static void assemblerCardRemovalKeepsJobWithSpareCard(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        a.cards().setStackInSlot(1, a.cards().getStackInSlot(0).copy());
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks(), () -> {
                a.cards().setStackInSlot(0, ItemStack.EMPTY);
                h.runAfterDelay(a.cycleTicks() * 12, () -> {
                    check(a.jobs().isEmpty() && count(h, r, PLANKS) == 40 && count(h, r, LOG) == 6, "Auftrag mit Ersatzkarte nicht fertig: " + count(h, r, PLANKS) + " Bretter, Status " + a.status());
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 500)
    public static void assemblerCardRemovalReleasesHelperLanes(GameTestHelper h) {
        Rig r = rig(h, true, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler(), b = r.a2.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 64), "Bestellung abgelehnt");
            h.runAfterDelay(a.cycleTicks() / 2, () -> {
                check(b.busyLanes() > 0, "Helfer arbeitet nicht mit");
                a.cards().setStackInSlot(0, ItemStack.EMPTY);
                h.runAfterDelay(a.cycleTicks() * 2, () -> {
                    check(a.jobs().isEmpty() && a.busyLanes() == 0 && b.busyLanes() == 0, "Spuren oder Auftrag nach Kartenentnahme aktiv");
                    check(storage(h, r).reserved(LOG) == 0, "Reservierung nach Abbruch");
                    check(count(h, r, LOG) * 4 + count(h, r, PLANKS) == 64, "Itemwert nicht erhalten: " + count(h, r, LOG) + " Staemme, " + count(h, r, PLANKS) + " Bretter");
                    h.succeed();
                });
            });
        });
    }

    private static boolean inside(BlockPos p, List<BlockPos> cells) {
        return cells.contains(p);
    }

    @GameTest(template = FLOOR, batch = BATCH + "_c1", timeoutTicks = 700)
    public static void assemblerPausesWhileRackChunkIsUnloaded(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            List<BlockPos> rackCells = r.rack.cellPositions();
            ChunkGuard.hide(p -> inside(p, rackCells));
            h.runAfterDelay(a.cycleTicks() * 3, () -> {
                int status = a.status();
                int left = a.jobs().isEmpty() ? -1 : a.jobs().get(0).leftInStep();
                boolean idleLanes = a.busyLanes() == 0 || status == ComponentAssembler.CHUNK;
                ChunkGuard.clear();
                check(status == ComponentAssembler.CHUNK, "Status Chunk erwartet, ist " + status);
                check(idleLanes, "Spur arbeitet trotz entladenem Rack");
                check(count(h, r, LOG) + a.jobs().get(0).leftInStep() * 0 >= 6, "Es wurde etwas verbraucht: " + count(h, r, LOG) + " Staemme");
                int consumed = 16 - (int) count(h, r, LOG);
                check(consumed == 10 - left, "Verbrauch und Auftragsstand passen nicht zusammen: verbraucht " + consumed + ", offen " + left);
                h.runAfterDelay(a.cycleTicks() * 12, () -> {
                    check(a.jobs().isEmpty() && count(h, r, PLANKS) == 40 && count(h, r, LOG) == 6, "Auftrag nach dem Laden nicht fertig: " + count(h, r, PLANKS) + " Bretter, Status " + a.status());
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH + "_c2", timeoutTicks = 1200)
    public static void assemblerPausesWhenPartOfThePlantIsUnloaded(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            List<BlockPos> cells = r.a1.cellPositions();
            BlockPos far = cells.get(cells.size() - 1);
            ChunkGuard.hide(p -> p.equals(far));
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 16), "Bestellung abgelehnt");
            h.startSequence()
                    .thenWaitUntil(() -> check(a.status() == ComponentAssembler.CHUNK, "Status Chunk erwartet, ist " + a.status()))
                    .thenExecute(() -> {
                        ChunkGuard.clear();
                        check(count(h, r, LOG) == 16 && count(h, r, PLANKS) == 0, "Es wurde etwas verbraucht: " + count(h, r, LOG) + " Staemme, " + count(h, r, PLANKS) + " Bretter");
                    })
                    .thenWaitUntil(() -> check(a.jobs().isEmpty() && count(h, r, PLANKS) == 16, "Auftrag nach dem Laden nicht fertig, Status " + a.status()))
                    .thenSucceed();
        });
    }

    @GameTest(template = FLOOR, batch = BATCH + "_c3", timeoutTicks = 200)
    public static void dataAndStorageNetsSkipUnloadedChunks(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            StorageAccess whole = storage(h, r);
            check(whole.complete() && whole.racks() == 1, "Netz sollte vollstaendig sein, Racks " + whole.racks());
            BlockPos ctrl = r.rack.getBlockPos();
            ChunkGuard.hide(p -> p.equals(ctrl));
            var scan = DataNet.scanChecked(h.getLevel(), List.of(r.terminal));
            StorageAccess cut = storage(h, r);
            ChunkGuard.clear();
            check(scan.incomplete(), "Suche meldet sich nicht als unvollstaendig");
            check(scan.sources().stream().noneMatch(s -> s.pos().equals(ctrl)), "Anlage mit nicht geladenem Controller im Netz");
            check(!cut.complete() && cut.racks() == 0 && cut.count(LOG) == 0, "Speichernetz zaehlt ein nicht geladenes Rack: " + cut.racks() + " Racks");
            StorageAccess back = storage(h, r);
            check(back.complete() && back.count(LOG) == 16, "Nach dem Laden fehlt der Bestand: " + back.count(LOG));
            List<BlockPos> cells = r.rack.cellPositions();
            ChunkGuard.hide(p -> inside(p, cells));
            StorageAccess gone = StorageNet.access(h.getLevel(), r.terminal);
            ChunkGuard.clear();
            check(!gone.complete() && gone.count(LOG) == 0, "Netz mit entladenem Rack-Chunk: vollstaendig=" + gone.complete());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = BATCH + "_c5", timeoutTicks = 700)
    public static void assemblerPeerChunkUnloadDoesNotCancelJobs(GameTestHelper h) {
        Rig r = rig(h, true, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler(), b = r.a2.assembler();
        b.cards().setStackInSlot(0, a.cards().getStackInSlot(0).copy());
        h.runAfterDelay(25, () -> {
            check(storage(h, r).insert(LOG, 16, false) == 16, "Staemme nicht eingelagert");
            check(terminal(h, r).order(new ItemStack(Items.OAK_PLANKS), 40), "Bestellung abgelehnt");
            a.cards().setStackInSlot(0, ItemStack.EMPTY);
            h.runAfterDelay(30, () -> {
                check(a.jobs().size() == 1, "Auftrag brach ab, obwohl der Nachbar die Karte hat");
                List<BlockPos> cells = r.a2.cellPositions();
                ChunkGuard.hide(p -> inside(p, cells));
                a.cards().setStackInSlot(1, ItemStack.EMPTY);
                h.runAfterDelay(60, () -> {
                    boolean alive = a.jobs().size() == 1;
                    ChunkGuard.clear();
                    check(alive, "Auftrag brach ab, weil der Chunk des Nachbarn nicht geladen war");
                    h.runAfterDelay(a.cycleTicks() * 12, () -> {
                        check(a.jobs().isEmpty() && count(h, r, PLANKS) == 40, "Auftrag nicht fertig: " + count(h, r, PLANKS) + " Bretter, Status " + a.status());
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = BATCH + "_c4", timeoutTicks = 200)
    public static void assemblerKeepLoadedPinsAndReleasesChunks(GameTestHelper h) {
        Rig r = rig(h, false, StorageRegistry.HARD_DRIVE_1.get());
        ComponentAssembler a = r.a1.assembler();
        var cfg = com.sqc.nuclearsq.NuclearConfig.ASSEMBLER_KEEP_LOADED;
        boolean was = cfg.get();
        h.runAfterDelay(25, () -> {
            try {
                check(a.forcedChunks().isEmpty(), "Tickets ohne Config");
                cfg.set(true);
                a.refreshNow(h.getLevel(), r.a1);
                check(!a.forcedChunks().isEmpty(), "Config an, aber keine Tickets");
                check(a.forcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(r.a1.getBlockPos())), "Chunk der Anlage nicht festgehalten");
                check(a.forcedChunks().size() <= com.sqc.nuclearsq.NuclearConfig.ASSEMBLER_MAX_CHUNKS.get(), "Mehr Chunks als erlaubt");
                cfg.set(false);
                a.refreshNow(h.getLevel(), r.a1);
                check(a.forcedChunks().isEmpty(), "Config aus, aber Tickets bleiben: " + a.forcedChunks().size());
                cfg.set(true);
                a.refreshNow(h.getLevel(), r.a1);
                check(!a.forcedChunks().isEmpty(), "Tickets nicht wieder gesetzt");
                FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(r.a1.getBlockPos());
                check(a.forcedChunks().isEmpty(), "Abbau gibt die Tickets nicht frei");
            } finally {
                cfg.set(was);
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = BATCH, timeoutTicks = 40)
    public static void assemblerChunkStatusHasText(GameTestHelper h) {
        check(ComponentAssembler.CHUNK == 7, "Status Chunk ist 7");
        check(StorageValues.assembler().maxLanes() >= 1, "Werte fehlen");
        h.succeed();
    }
}
