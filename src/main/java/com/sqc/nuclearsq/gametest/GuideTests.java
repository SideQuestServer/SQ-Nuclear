package com.sqc.nuclearsq.gametest;

import java.util.HashSet;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.progression.Guide;
import com.sqc.nuclearsq.progression.GuideProgress;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class GuideTests {
    private GuideTests() {}

    private static final String FLOOR = "floor";

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    private static boolean exists(String id) {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void guideReferencesExist(GameTestHelper h) {
        Set<String> keys = new HashSet<>();
        Set<String> machines = new HashSet<>();
        for (BigMachine m : BigMachine.values()) machines.add(m.id);
        machines.add("turbine");
        for (Guide.Step s : Guide.all()) {
            if (!keys.add(s.key())) throw new GameTestAssertException("Schritt doppelt: " + s.key());
            for (String n : s.need()) {
                if (n.startsWith("i:") && !exists(n.substring(2))) throw new GameTestAssertException(s.key() + ": Item fehlt " + n);
                if (n.startsWith("m:formed:") && !machines.contains(n.substring(9))) throw new GameTestAssertException(s.key() + ": Maschine fehlt " + n);
                if (n.startsWith("m:power:") && !machines.contains(n.substring(8))) throw new GameTestAssertException(s.key() + ": Maschine fehlt " + n);
            }
            for (String i : s.show()) if (!exists(i)) throw new GameTestAssertException(s.key() + ": Bild-Item fehlt " + i);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void guideTextsExist(GameTestHelper h) {
        var de = com.sqc.nuclearsq.gametest.GuideTests.class.getResourceAsStream("/assets/nuclearsq/lang/de_de.json");
        var en = com.sqc.nuclearsq.gametest.GuideTests.class.getResourceAsStream("/assets/nuclearsq/lang/en_us.json");
        try (de; en) {
            String d = new String(de.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String e = new String(en.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<String> need = new java.util.ArrayList<>();
            need.add("compendium." + Guide.OVERVIEW + ".title");
            need.add("compendium." + Guide.OVERVIEW + ".text");
            for (Guide.Chapter c : Guide.CHAPTERS) {
                need.add("compendium." + c.page() + ".title");
                need.add("compendium." + c.page() + ".text");
                for (Guide.Step s : c.steps()) {
                    need.add("compendium.guide.step." + s.key() + ".title");
                    need.add("compendium.guide.step." + s.key() + ".text");
                }
            }
            for (String k : need) {
                if (!d.contains("\"" + k + "\"")) throw new GameTestAssertException("de_de fehlt " + k);
                if (!e.contains("\"" + k + "\"")) throw new GameTestAssertException("en_us fehlt " + k);
            }
        } catch (java.io.IOException ex) {
            throw new GameTestAssertException("Lang nicht lesbar: " + ex);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void guideFollowsRecipes(GameTestHelper h) {
        var level = h.getLevel();
        var sources = RecipeSources.all(level.getRecipeManager(), level.registryAccess());
        Set<Item> craftable = new HashSet<>();
        for (var s : sources) craftable.add(s.result().getItem());
        Set<RecipeSources.Where> open = java.util.EnumSet.of(RecipeSources.Where.WORKBENCH, RecipeSources.Where.FURNACE,
                RecipeSources.Where.BLAST_FURNACE);
        Set<Item> have = new HashSet<>();
        for (Guide.Step st : Guide.all()) {
            boolean more = true;
            while (more) {
                more = false;
                for (var s : sources) {
                    if (!open.contains(s.where()) || have.contains(s.result().getItem())) continue;
                    boolean ok = true;
                    for (var need : s.needs()) {
                        boolean any = false;
                        for (var stack : need.ingredient().getItems()) {
                            boolean mod = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(NuclearSQ.MODID);
                            if (!mod || !craftable.contains(stack.getItem()) || have.contains(stack.getItem())) { any = true; break; }
                        }
                        if (!any) { ok = false; break; }
                    }
                    if (ok) { have.add(s.result().getItem()); more = true; }
                }
            }
            for (String n : st.need()) {
                if (!n.startsWith("i:")) continue;
                Item it = item(n.substring(2));
                if (!have.contains(it) && craftable.contains(it)) {
                    throw new GameTestAssertException("Schritt " + st.key() + ": " + n + " laesst sich mit den Maschinen davor nicht herstellen");
                }
            }
            RecipeSources.Where w = switch (st.key()) {
                case "small_place" -> RecipeSources.Where.SMALL_PARTS_ASSEMBLER;
                case "reactor_place" -> RecipeSources.Where.REACTOR_ASSEMBLER;
                case "heavy_assembler" -> RecipeSources.Where.HEAVY_ASSEMBLER;
                case "ore_mill" -> RecipeSources.Where.ORE_MILLING;
                case "enrichment_centrifuge" -> RecipeSources.Where.ENRICHMENT;
                case "reprocessing_plant" -> RecipeSources.Where.REPROCESSING;
                case "acid_plant" -> RecipeSources.Where.ACID_PLANT;
                case "polymer_plant" -> RecipeSources.Where.POLYMER;
                case "plate_mill" -> RecipeSources.Where.PLATE_MILLING;
                case "cnc_mill" -> RecipeSources.Where.CNC_MILL;
                case "solder_machine" -> RecipeSources.Where.SOLDER_MACHINE;
                case "hammer_mill" -> RecipeSources.Where.DUST_MILLING;
                case "kiln" -> RecipeSources.Where.KILN_FIRING;
                case "blast_furnace" -> RecipeSources.Where.BLAST_SMELTING;
                case "foundry" -> RecipeSources.Where.FOUNDRY;
                case "chemical_lab" -> RecipeSources.Where.CHEMISTRY;
                case "pellet_factory" -> RecipeSources.Where.PELLETING;
                case "leaching_plant" -> RecipeSources.Where.LEACHING;
                case "uf6_converter" -> RecipeSources.Where.UF6_CONVERSION;
                case "equipment_assembler" -> RecipeSources.Where.EQUIPMENT_ASSEMBLER;
                default -> null;
            };
            if (w != null) open.add(w);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void guidePagesMatchStructure(GameTestHelper h) {
        java.util.List<String> want = new java.util.ArrayList<>();
        want.add(Guide.OVERVIEW);
        for (Guide.Chapter c : Guide.CHAPTERS) want.add(c.page());
        java.util.List<String> have = new java.util.ArrayList<>();
        try (var in = GuideTests.class.getResourceAsStream("/data/nuclearsq/compendium/kompendium_struktur.json")) {
            var root = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var sec : root.getAsJsonArray("sections"))
                for (var ch : sec.getAsJsonObject().getAsJsonArray("chapters"))
                    if (ch.getAsJsonObject().get("id").getAsString().equals("guide"))
                        for (var pg : ch.getAsJsonObject().getAsJsonArray("pages")) have.add(pg.getAsJsonObject().get("id").getAsString());
        } catch (java.io.IOException ex) {
            throw new GameTestAssertException("Struktur nicht lesbar: " + ex);
        }
        if (!want.equals(have)) throw new GameTestAssertException("Seiten des Guides stimmen nicht mit der Struktur ueberein: " + have + " statt " + want);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void guideMainPathEnds(GameTestHelper h) {
        if (!Guide.CHAPTERS.get(Guide.MAIN - 1).id().equals("run")) throw new GameTestAssertException("Hauptweg endet nicht bei run");
        var steps = Guide.CHAPTERS.get(Guide.MAIN - 1).steps();
        if (!steps.get(steps.size() - 1).key().equals("first_power")) throw new GameTestAssertException("letzter Schritt nicht first_power");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void guideMilestones(GameTestHelper h) {
        var p = FakePlayerFactory.getMinecraft(h.getLevel());
        var set = p.getData(GuideProgress.MILESTONES.get());
        set.clear();
        set.add("terminal");
        p.setData(GuideProgress.MILESTONES.get(), set);
        if (!GuideProgress.known(p, "terminal")) throw new GameTestAssertException("terminal nicht bekannt");
        if (GuideProgress.known(p, "console")) throw new GameTestAssertException("console faelschlich bekannt");
        if (!Guide.done(Guide.chapter("start").steps().get(5), n -> false, k -> GuideProgress.known(p, k))) {
            throw new GameTestAssertException("Schritt terminal nicht erledigt");
        }
        set.clear();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void recipeWindowShowsTheGrid(GameTestHelper h) {
        var level = h.getLevel();
        var coal = RecipeSources.forItem(level.getRecipeManager(), level.registryAccess(), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "coal_generator_kit_sqc")));
        if (coal.isEmpty() || coal.get(0).grid() == null) throw new GameTestAssertException("Kohlegenerator-Kit (Werkbank) ohne Raster");
        var g = coal.get(0).grid();
        long filled = g.cells().stream().filter(i -> !i.isEmpty()).count();
        if (g.width() != 3 || g.height() != 3 || filled != 9) throw new GameTestAssertException("Raster des Kohlegenerator-Kits: " + g.width() + "x" + g.height() + " mit " + filled + " Feldern statt 3x3 mit 9");
        var solar = RecipeSources.forItem(level.getRecipeManager(), level.registryAccess(), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "solar_field_kit_sqc")));
        if (solar.isEmpty() || solar.get(0).grid() == null) throw new GameTestAssertException("Solarfeld-Kit (Gross-Assembler) ohne Raster");
        var sg = solar.get(0).grid();
        if (sg.cells().size() != sg.width() * sg.height() || sg.width() < 3) throw new GameTestAssertException("Raster des Solarfeld-Kits: " + sg.width() + "x" + sg.height() + " mit " + sg.cells().size() + " Zellen");
        var plate = RecipeSources.forItem(level.getRecipeManager(), level.registryAccess(), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "reactor_plate_sqc")));
        if (plate.isEmpty() || plate.get(0).grid() != null) throw new GameTestAssertException("formloses Assembler-Rezept hat ein Raster");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void compendiumIsGivenOnce(GameTestHelper h) {
        var p = FakePlayerFactory.getMinecraft(h.getLevel());
        Item book = item("progression_book_sqc");
        p.getInventory().clearContent();
        p.setData(GuideProgress.BOOK_GIVEN.get(), false);
        if (!GuideProgress.giveBook(p) || p.getInventory().countItem(book) != 1) throw new GameTestAssertException("Kompendium beim ersten Beitritt nicht gegeben");
        p.getInventory().clearContent();
        if (GuideProgress.giveBook(p) || p.getInventory().countItem(book) != 0) throw new GameTestAssertException("Kompendium ein zweites Mal gegeben");
        p.setData(GuideProgress.BOOK_GIVEN.get(), false);
        p.getInventory().add(new ItemStack(book));
        if (GuideProgress.giveBook(p) || p.getInventory().countItem(book) != 1) throw new GameTestAssertException("Kompendium trotz vorhandenem Buch gegeben");
        p.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "guide", timeoutTicks = 20)
    public static void textureHintOnFirstMachineOnce(GameTestHelper h) {
        var p = FakePlayerFactory.getMinecraft(h.getLevel());
        BlockState cable = TransportRegistry.CABLES.get(TransportTier.values()[0]).get().defaultBlockState();
        if (GuideProgress.isMachine(cable)) throw new GameTestAssertException("Kabel zaehlt als Maschine");
        if (!GuideProgress.isMachine(BigMachineRegistry.CONTROLLERS.get(BigMachine.BATTERY_SMALL).get().defaultBlockState())) throw new GameTestAssertException("Controller zaehlt nicht als Maschine");
        p.setData(GuideProgress.TEXTURE_HINT_SHOWN.get(), false);
        BlockPos c = h.absolutePos(new BlockPos(1, 1, 1));
        BlockSnapshot snap = BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), c);
        h.getLevel().setBlock(c, cable, 3);
        EventHooks.onBlockPlace(p, snap, Direction.UP);
        if (p.getData(GuideProgress.TEXTURE_HINT_SHOWN.get())) throw new GameTestAssertException("Hinweis schon beim Kabel");
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def(BigMachine.BATTERY_SMALL.id), h.absolutePos(new BlockPos(4, 1, 4)), Direction.SOUTH);
        if (!MachineKits.place(h.getLevel(), pl, p)) throw new GameTestAssertException("Kit nicht gesetzt");
        if (!p.getData(GuideProgress.TEXTURE_HINT_SHOWN.get())) throw new GameTestAssertException("Kein Hinweis bei der ersten Maschine");
        if (GuideProgress.textureHint(p)) throw new GameTestAssertException("Hinweis ein zweites Mal gezeigt");
        MachineKits.dismantle(h.getLevel(), pl, false);
        p.setData(GuideProgress.TEXTURE_HINT_SHOWN.get(), false);
        if (!GuideProgress.textureHint(p) || GuideProgress.textureHint(p)) throw new GameTestAssertException("Hinweis nicht genau einmal");
        h.succeed();
    }
}
