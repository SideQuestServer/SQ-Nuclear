package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.storage.ComponentAssembler;
import com.sqc.nuclearsq.storage.ComponentAssemblerMenu;
import com.sqc.nuclearsq.storage.ItemKey;
import com.sqc.nuclearsq.storage.Pattern;
import com.sqc.nuclearsq.storage.PatternCardItem;
import com.sqc.nuclearsq.storage.PatternRecipes;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ComponentAssemblerTests {
    private ComponentAssemblerTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.COMPONENT_ASSEMBLER;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BigMachine m, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException(m.id + ": Controller ohne BlockEntity");
        return be;
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

    private static ItemStack writeCard(GameTestHelper h, ComponentAssembler a, Object... cells) {
        for (int i = 0; i < ComponentAssembler.GRID; i++) a.template().setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < cells.length; i += 2) a.template().setStackInSlot((Integer) cells[i], (ItemStack) cells[i + 1]);
        a.encode().setStackInSlot(0, new ItemStack(StorageRegistry.PATTERN_CARD.get()));
        a.encode().setStackInSlot(1, ItemStack.EMPTY);
        a.updatePreview(h.getLevel());
        if (!a.writeCard(h.getLevel())) throw new GameTestAssertException("Karte nicht beschrieben, Vorlage " + a.display().getStackInSlot(0));
        return a.encode().extractItem(1, 1, false);
    }

    @GameTest(template = FLOOR, batch = "component_assembler", timeoutTicks = 1200)
    public static void componentAssemblerAutocraftsSubRecipe(GameTestHelper h) {
        BigMachineBlockEntity rack = build(h, BigMachine.SERVER_RACK_SMALL, new BlockPos(2, 2, 2));
        BigMachineBlockEntity be = build(h, M, new BlockPos(7, 2, 7));
        BlockPos rc = rack.getBlockPos(), rp = rack.machine().structure.world(rc, 0, 0, 0, 1);
        BlockPos fromRack = rp.offset(rp.subtract(rc));
        BlockPos front = M.structure.world(be.getBlockPos(), 0, M.ax, M.ay, M.az - 1);
        cable(h, fromRack, front);
        BlockPos terminal = fromRack.above();
        h.getLevel().setBlockAndUpdate(terminal, StorageRegistry.TERMINAL.get().defaultBlockState());
        rack.rack().drives().setStackInSlot(0, new ItemStack(StorageRegistry.HARD_DRIVE_1.get()));
        charge(rack);
        ComponentAssembler a = be.assembler();
        if (a == null) throw new GameTestAssertException("Kein Assembler am Controller");
        ItemStack planks = writeCard(h, a, 0, new ItemStack(Items.OAK_LOG));
        ItemStack sticks = writeCard(h, a, 0, new ItemStack(Items.OAK_PLANKS), 3, new ItemStack(Items.OAK_PLANKS));
        ItemStack pick = writeCard(h, a, 0, new ItemStack(Items.OAK_PLANKS), 1, new ItemStack(Items.OAK_PLANKS), 2, new ItemStack(Items.OAK_PLANKS),
                4, new ItemStack(Items.STICK), 7, new ItemStack(Items.STICK));
        a.cards().setStackInSlot(0, pick);
        a.cards().setStackInSlot(1, sticks);
        a.cards().setStackInSlot(2, planks);
        h.runAfterDelay(25, () -> {
            if (!be.isFormed() || !rack.isFormed()) throw new GameTestAssertException("Assembler oder Rack nicht aufgebaut");
            if (!rack.rack().online()) throw new GameTestAssertException("Rack nicht online");
            StorageAccess s = StorageNet.access(h.getLevel(), terminal);
            if (s.insert(ItemKey.of(new ItemStack(Items.OAK_LOG)), 2, false) != 2) throw new GameTestAssertException("Staemme nicht eingelagert");
            if (StorageNet.assemblers(h.getLevel(), terminal).size() != 1) throw new GameTestAssertException("Terminal findet den Assembler nicht");
            Player p = FakePlayerFactory.getMinecraft(h.getLevel());
            StorageTerminalMenu menu = new StorageTerminalMenu(0, p.getInventory(), terminal);
            if (menu.order(new ItemStack(Items.WOODEN_PICKAXE), 5) || !a.jobs().isEmpty()) {
                throw new GameTestAssertException("5 Spitzhacken aus 2 Staemmen angenommen: " + menu.lastMessage().getString());
            }
            if (menu.order(new ItemStack(Items.DIAMOND), 1)) throw new GameTestAssertException("Bestellung ohne Karte angenommen");
            if (!menu.order(new ItemStack(Items.WOODEN_PICKAXE), 1)) throw new GameTestAssertException("Bestellung abgelehnt: " + menu.lastMessage().getString());
            if (a.jobs().size() != 1 || a.jobs().get(0).steps() != 4) {
                throw new GameTestAssertException("Auftrag mit 4 Schritten erwartet, ist " + (a.jobs().isEmpty() ? 0 : a.jobs().get(0).steps()));
            }
            h.startSequence()
                    .thenWaitUntil(() -> {
                        if (a.status() != ComponentAssembler.NO_POWER) throw new GameTestAssertException("ohne Strom: NO_POWER erwartet, ist " + a.status());
                    })
                    .thenExecute(() -> charge(be))
                    .thenWaitUntil(() -> {
                        StorageAccess t = StorageNet.access(h.getLevel(), terminal);
                        long pickaxes = t.count(ItemKey.of(new ItemStack(Items.WOODEN_PICKAXE)));
                        if (pickaxes != 1) throw new GameTestAssertException("Spitzhacken im Speicher: " + pickaxes + ", Status " + a.status());
                        if (t.count(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 0) throw new GameTestAssertException("Staemme nicht verbraucht");
                        if (t.count(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 3) throw new GameTestAssertException("Rest-Bretter " + t.count(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) + " statt 3");
                        if (t.count(ItemKey.of(new ItemStack(Items.STICK))) != 2) throw new GameTestAssertException("Rest-Stoecke " + t.count(ItemKey.of(new ItemStack(Items.STICK))) + " statt 2");
                        if (!a.jobs().isEmpty()) throw new GameTestAssertException("Auftrag nicht abgeschlossen");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = FLOOR, batch = "component_assembler_cards", timeoutTicks = 60)
    public static void componentAssemblerWritesCards(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, M, new BlockPos(5, 2, 5));
        ComponentAssembler a = be.assembler();
        if (a == null) throw new GameTestAssertException("Kein Assembler am Controller");
        ItemStack drive = writeCard(h, a, 0, new ItemStack(Items.IRON_INGOT, 1), 4, new ItemStack(Items.IRON_INGOT, 1),
                8, new ItemStack(Items.REDSTONE, 4), 1, new ItemStack(Items.COPPER_INGOT, 2), 2, new ItemStack(item("nuclearsq:control_board_1_sqc")));
        if (a.previewKind() != PatternRecipes.SMALL_PARTS) throw new GameTestAssertException("Festplatte nicht als Kleinteil-Rezept erkannt: " + a.previewKind());
        Pattern pc = PatternCardItem.pattern(drive);
        if (pc == null || !pc.output().item().is(item("nuclearsq:hard_drive_1_sqc")) || pc.inputs().size() != 4) {
            throw new GameTestAssertException("Karte Festplatte falsch: " + pc);
        }
        writeCard(h, a, 0, new ItemStack(item("nuclearsq:control_board_2_sqc"), 2), 1, new ItemStack(item("nuclearsq:copper_coil_sqc"), 8),
                2, new ItemStack(Items.ENDER_PEARL, 15), 3, new ItemStack(item("nuclearsq:reactor_casing_sqc"), 2), 4, new ItemStack(item("nuclearsq:data_cable_sqc"), 1), 5, new ItemStack(item("nuclearsq:chip_2_sqc")));
        if (a.previewKind() != PatternRecipes.ASSEMBLER) throw new GameTestAssertException("Assembler-Rezept nicht erkannt: " + a.previewKind());
        ItemStack cake = writeCard(h, a, 0, new ItemStack(Items.MILK_BUCKET), 1, new ItemStack(Items.MILK_BUCKET), 2, new ItemStack(Items.MILK_BUCKET),
                3, new ItemStack(Items.SUGAR), 4, new ItemStack(Items.EGG), 5, new ItemStack(Items.SUGAR),
                6, new ItemStack(Items.WHEAT), 7, new ItemStack(Items.WHEAT), 8, new ItemStack(Items.WHEAT));
        Pattern pk = PatternCardItem.pattern(cake);
        if (a.previewKind() != PatternRecipes.CRAFTING || pk == null || pk.remainders().size() != 1 || pk.remainders().get(0).count() != 3) {
            throw new GameTestAssertException("Kuchen: Werkbank mit 3 Eimern Rest erwartet: " + pk);
        }
        if (a.cards().insertItem(0, new ItemStack(StorageRegistry.PATTERN_CARD.get()), false).isEmpty()) throw new GameTestAssertException("leere Karte im Kartenschacht");
        if (!a.cards().insertItem(0, drive, false).isEmpty()) throw new GameTestAssertException("beschriebene Karte passt nicht in den Schacht");
        if (!a.hasPattern(ItemKey.of(new ItemStack(item("nuclearsq:hard_drive_1_sqc"))))) throw new GameTestAssertException("Muster nicht gefunden");
        Player p = FakePlayerFactory.getMinecraft(h.getLevel());
        ComponentAssemblerMenu menu = new ComponentAssemblerMenu(0, p.getInventory(), be.getBlockPos(), a, ComponentAssemblerMenu.data(be));
        menu.clickMenuButton(p, ComponentAssemblerMenu.BTN_CLEAR);
        menu.setCarried(new ItemStack(Items.OAK_LOG, 5));
        menu.clicked(4, 1, ClickType.PICKUP, p);
        menu.clicked(4, 1, ClickType.PICKUP, p);
        if (a.template().getStackInSlot(4).getCount() != 2 || menu.getCarried().getCount() != 5) {
            throw new GameTestAssertException("Rechtsklick legt kein Geister-Item dazu: " + a.template().getStackInSlot(4) + " / Hand " + menu.getCarried());
        }
        menu.clicked(0, 0, ClickType.PICKUP, p);
        if (a.template().getStackInSlot(0).getCount() != 5) throw new GameTestAssertException("Linksklick setzt nicht den Stapel");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, p);
        menu.clicked(4, 0, ClickType.PICKUP, p);
        if (!a.template().getStackInSlot(0).isEmpty() || !a.template().getStackInSlot(4).isEmpty()) throw new GameTestAssertException("leere Hand leert das Feld nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "component_assembler_kit", timeoutTicks = 60)
    public static void componentAssemblerKitPlaces(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("component_assembler"), h.absolutePos(new BlockPos(7, 4, 4)), Direction.SOUTH);
        List<BlockPos> blocked = MachineKits.blocked(h.getLevel(), pl, null);
        if (!blocked.isEmpty()) throw new GameTestAssertException("Platz gilt als belegt: " + blocked);
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(10, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || be.machine() != M) {
                throw new GameTestAssertException("Kein Bauteilassembler aufgestellt");
            }
            if (!be.isFormed()) throw new GameTestAssertException("Bauteilassembler nach dem Aufstellen nicht fertig");
            ItemStack card = PatternCardItem.write(new ItemStack(StorageRegistry.PATTERN_CARD.get()),
                    new Pattern(List.of(new Pattern.Part(new ItemStack(Items.OAK_LOG), 1)), new Pattern.Part(new ItemStack(Items.OAK_PLANKS), 4), List.of()));
            be.assembler().cards().setStackInSlot(3, card);
            BlockPos part = null;
            for (MultiblockStructure.Cell c : pl.cells()) if (!c.pos().equals(pl.anchor())) { part = c.pos(); break; }
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(part);
            AABB box = new AABB(pl.anchor()).inflate(8);
            if (h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(KitRegistry.KITS.get("component_assembler").get())).isEmpty()) {
                throw new GameTestAssertException("Kit faellt nicht heraus");
            }
            if (h.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> PatternCardItem.pattern(e.getItem()) != null).isEmpty()) {
                throw new GameTestAssertException("Musterkarte faellt nicht heraus");
            }
            if (h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity) throw new GameTestAssertException("Anlage steht noch");
            h.succeed();
        });
    }
}
