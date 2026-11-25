package com.sqc.nuclearsq.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.gamedata.GameData;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.EquipmentRegistry;
import com.sqc.nuclearsq.registry.ModDataComponents;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;
import com.sqc.nuclearsq.reactor.FuelRodBlockItem;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;
import com.sqc.nuclearsq.turbine.ShaftValues;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class DataPackTests {
    private DataPackTests() {}

    private static final String FLOOR = "floor";
    public static final int ASSEMBLY_RECIPES = 224, MEKANISM_ONLY = 1, NO_MEKANISM = 0, POWERGRID_ONLY = 1;

    private static boolean onlyAbsentMods(AssemblyRecipe.Need n) {
        if (!(n.ingredient().getCustomIngredient() instanceof com.sqc.nuclearsq.assembler.OptionalIngredient o) || !o.fallback().isEmpty()) return false;
        var mods = net.neoforged.fml.ModList.get();
        for (ResourceLocation rl : o.items()) if (mods.isLoaded(rl.getNamespace())) return false;
        for (var t : o.tags()) if (!t.location().getNamespace().equals("c")) return false;
        return true;
    }

    private static List<ItemStack> grid(AssemblyRecipe r) {
        List<ItemStack> out = new ArrayList<>();
        for (AssemblyRecipe.Need n : r.ingredients()) {
            ItemStack[] items = n.ingredient().getItems();
            if (items.length == 0) return null;
            int left = n.count();
            while (left > 0) {
                ItemStack s = items[0].copyWithCount(Math.min(left, items[0].getMaxStackSize()));
                left -= s.getCount();
                out.add(s);
            }
        }
        if (out.size() > 9) return null;
        while (out.size() < 9) out.add(ItemStack.EMPTY);
        return out;
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void assemblyRecipesComeFromJson(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        List<AssemblyRecipe> all = AssemblyRecipe.all(rm).stream().map(net.minecraft.world.item.crafting.RecipeHolder::value).toList();
        int expected = ASSEMBLY_RECIPES - (net.neoforged.fml.ModList.get().isLoaded("mekanism") ? NO_MEKANISM : MEKANISM_ONLY)
            - (net.neoforged.fml.ModList.get().isLoaded("powergrid") ? 0 : POWERGRID_ONLY);
        if (all.size() != expected) throw new GameTestAssertException(all.size() + " Assembler-Rezepte, erwartet " + expected);
        List<String> bad = new ArrayList<>();
        int needMod = 0;
        for (AssemblyRecipe r : all) {
            String name = BuiltInRegistries.ITEM.getKey(r.result().getItem()).getPath();
            boolean absent = false;
            for (AssemblyRecipe.Need n : r.ingredients()) {
                if (n.ingredient().getItems().length > 0) continue;
                if (onlyAbsentMods(n)) absent = true;
                else bad.add(name + " (Zutat ohne Item)");
            }
            if (absent) { needMod++; continue; }
            List<ItemStack> g = grid(r);
            if (g == null) { bad.add(name + " (mehr als 9 Felder)"); continue; }
            if (r.plan(g) == null) { bad.add(name + " (eigenes Raster passt nicht)"); continue; }
            AssemblyRecipe found = AssemblyRecipe.find(rm, g);
            if (found == null) bad.add(name + " (find liefert nichts)");
            else if (found != r) bad.add(name + " (Raster ergibt " + BuiltInRegistries.ITEM.getKey(found.result().getItem()).getPath() + ")");
        }
        if (!bad.isEmpty()) throw new GameTestAssertException("Nicht baubar: " + String.join(", ", bad));
        if (needMod >= all.size() / 2) throw new GameTestAssertException(needMod + " Rezepte nur mit fremden Mods");
        NuclearSQ.LOGGER.info("Assembler-Rezepte: {} ohne Mods baubar, {} brauchen eine fehlende Mod", all.size() - needMod, needMod);
        h.succeed();
    }

    private static List<ItemStack> fullStacks(List<Ingredient> ingredients, List<Integer> counts) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack[] items = ingredients.get(i).getItems();
            if (items.length == 0) return null;
            out.add(items[0].copyWithCount(Math.max(counts.get(i), items[0].getMaxStackSize())));
        }
        while (out.size() < 9) out.add(ItemStack.EMPTY);
        return out;
    }

    private static String path(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    private static boolean sameMachine(SmallPartsRecipe a, SmallPartsRecipe b) {
        return a.result().is(BigCrafter.MICRO_PRODUCTS) == b.result().is(BigCrafter.MICRO_PRODUCTS)
                && a.result().is(BigCrafter.CLEAN_ROOM_PRODUCTS) == b.result().is(BigCrafter.CLEAN_ROOM_PRODUCTS)
                && a.result().is(BigCrafter.CNC_PRODUCTS) == b.result().is(BigCrafter.CNC_PRODUCTS)
                && a.result().is(BigCrafter.SOLDER_PRODUCTS) == b.result().is(BigCrafter.SOLDER_PRODUCTS);
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void fullStacksBuildTheirOwnRecipe(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        List<String> bad = new ArrayList<>();
        for (RecipeHolder<AssemblyRecipe> holder : AssemblyRecipe.all(rm)) {
            AssemblyRecipe r = holder.value();
            List<ItemStack> g = fullStacks(r.ingredients().stream().map(AssemblyRecipe.Need::ingredient).toList(),
                    r.ingredients().stream().map(AssemblyRecipe.Need::count).toList());
            if (g == null) continue;
            AssemblyRecipe found = AssemblyRecipe.find(rm, g);
            if (found != r) bad.add(path(r.result()) + " -> " + (found == null ? "nichts" : path(found.result())));
        }
        List<SmallPartsRecipe> parts = rm.getAllRecipesFor(SmallPartsRegistry.TYPE.get()).stream().map(RecipeHolder::value).toList();
        List<SmallPartsRecipe> equipment = rm.getAllRecipesFor(EquipmentRegistry.TYPE.get()).stream().map(x -> x.value().base()).toList();
        for (List<SmallPartsRecipe> group : List.of(parts, equipment)) {
            for (SmallPartsRecipe r : group) {
                List<ItemStack> g = fullStacks(r.ingredients().stream().map(SmallPartsRecipe.Need::ingredient).toList(),
                        r.ingredients().stream().map(SmallPartsRecipe.Need::count).toList());
                if (g == null) continue;
                SmallPartsRecipe.Input in = new SmallPartsRecipe.Input(g);
                for (SmallPartsRecipe o : group) {
                    if (o != r && sameMachine(r, o) && o.matches(in, h.getLevel())) bad.add(path(r.result()) + " -> " + path(o.result()));
                }
            }
        }
        if (!bad.isEmpty()) throw new GameTestAssertException("Volle Stapel ergeben ein anderes Rezept: " + String.join(", ", bad));
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void workbenchOnlyForTheStart(GameTestHelper h) {
        java.util.Set<String> allowed = java.util.Set.of("small_parts_assembler_kit_sqc", "coal_generator_kit_sqc", "kiln_kit_sqc", "cable_basic_sqc", "progression_book_sqc");
        java.util.Set<String> found = new java.util.TreeSet<>();
        for (var holder : h.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            if (holder.id().getNamespace().equals(NuclearSQ.MODID)) found.add(holder.id().getPath());
        }
        if (!found.equals(allowed)) throw new GameTestAssertException("Werkbank-Rezepte der Mod: " + found + ", erlaubt " + allowed);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void ingredientsAreNotCountedTwice(GameTestHelper h) {
        AssemblyRecipe r = new AssemblyRecipe(List.of(new AssemblyRecipe.Need(Ingredient.of(Items.IRON_INGOT), 2),
                new AssemblyRecipe.Need(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 2)), new ItemStack(Items.ANVIL), 0, 0);
        List<ItemStack> three = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT, 3)));
        while (three.size() < 9) three.add(ItemStack.EMPTY);
        if (r.plan(three) != null) throw new GameTestAssertException("3 Eisen zaehlen doppelt");
        List<ItemStack> mixed = new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.IRON_INGOT, 2)));
        while (mixed.size() < 9) mixed.add(ItemStack.EMPTY);
        int[][] plan = r.plan(mixed);
        if (plan == null) throw new GameTestAssertException("2 Eisen + 2 Gold passen nicht");
        List<ItemStack> foreign = new ArrayList<>(mixed);
        foreign.set(5, new ItemStack(Items.DIRT));
        if (r.plan(foreign) != null) throw new GameTestAssertException("Fremdes Item im Raster wird ignoriert");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void reprocessingHasOneTypePerMachine(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        for (BigMachine m : BigMachine.values()) {
            ReprocessingRecipe.Kind k = m.recipeKind();
            if (k == null) continue;
            var list = rm.getAllRecipesFor(k.type());
            if (list.isEmpty()) throw new GameTestAssertException(m.id + ": keine Rezepte vom Typ " + k.id);
            for (var holder : list) {
                if (holder.value().kind() != k) throw new GameTestAssertException(holder.id() + " hat Typ " + holder.value().kind());
            }
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void spentFuelGivesPlutoniumOxide(GameTestHelper h) {
        var oxide = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "plutonium_oxide_sqc"));
        var dust = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "uranium_oxide_dust_sqc"));
        var spent = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "spent_fuel_sqc")));
        var recipe = h.getLevel().getRecipeManager().getAllRecipesFor(ReprocessingRecipe.Kind.REPROCESSING.type()).stream()
                .map(holder -> holder.value()).filter(r -> r.accepts(spent)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("kein Rezept fuer abgeklungenen Brennstoff"));
        boolean hasOxide = false, hasDust = false;
        for (var res : recipe.results()) {
            if (res.stack().is(oxide)) {
                hasOxide = true;
                if (res.chance() <= 0f || res.chance() >= 1f) throw new GameTestAssertException("Plutoniumoxid braucht eine Chance zwischen 0 und 1, ist " + res.chance());
            }
            if (res.stack().is(dust)) hasDust = true;
        }
        if (!hasOxide) throw new GameTestAssertException("abgeklungener Brennstoff gibt kein Plutoniumoxid");
        if (!hasDust) throw new GameTestAssertException("abgeklungener Brennstoff gibt kein Uran-Oxid-Staub mehr");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void gasesHaveNoBucket(GameTestHelper h) {
        int gases = 0, sealedCount = 0;
        for (ModFluids.Entry e : ModFluids.ALL) {
            boolean bucketItem = BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, e.id + "_bucket_sqc"));
            boolean block = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, e.id + "_block_sqc"));
            boolean gas = e.id.contains("steam") || e.id.equals("deuterium") || e.id.equals("tritium") || e.id.equals("helium") || e.id.equals("hydrogen") || e.id.equals("oxygen") || e.id.equals("ethylene");
            boolean sealed = e.id.contains("primary_water") || e.id.equals("hydrofluoric_acid") || e.id.equals("heavy_water") || e.id.equals("hlw") || e.id.equals("lye") || e.id.equals("nitric_acid") || e.id.equals("crude_oil") || e.id.equals("naphtha") || e.id.equals("diesel") || e.id.equals("tar");
            if (gas != e.gas()) throw new GameTestAssertException(e.id + ": gas() = " + e.gas());
            if (sealed != e.sealed()) throw new GameTestAssertException(e.id + ": sealed() = " + e.sealed());
            if (gas || sealed) {
                if (gas) gases++;
                else sealedCount++;
                if (bucketItem || block || e.source.get().getBucket() != Items.AIR) throw new GameTestAssertException(e.id + " hat Eimer oder Block");
                if (e.source.get().defaultFluidState().createLegacyBlock().getBlock() != net.minecraft.world.level.block.Blocks.AIR) {
                    throw new GameTestAssertException(e.id + " setzt einen Block");
                }
            } else if (!bucketItem || !block) {
                throw new GameTestAssertException(e.id + " ohne Eimer oder Block");
            }
        }
        if (gases != 11) throw new GameTestAssertException(gases + " Gase, erwartet 11");
        if (sealedCount != 11) throw new GameTestAssertException(sealedCount + " Fluessigkeiten ohne Eimer, erwartet 11 (Primaerwasser kalt und heiss, Flusssaeure, Schweres Wasser, HLW, Natronlauge, Salpetersaeure, Rohoel, Leichtbenzin, Diesel, Teer)");
        if (Fluids.WATER.getBucket() != Items.WATER_BUCKET) throw new GameTestAssertException("Vanilla kaputt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void fuelChargeSurvivesPlaceAndDrop(GameTestHelper h) {
        ItemStack stack = new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK.get());
        if (FuelRodBlockItem.charge(stack) != com.sqc.nuclearsq.reactor.FuelRodBlockEntity.UNIT) throw new GameTestAssertException("Frisch nicht voll");
        stack.set(ModDataComponents.FUEL_CHARGE.get(), 321);
        BlockPos rel = new BlockPos(1, 2, 1), pos = h.absolutePos(rel);
        h.getLevel().setBlockAndUpdate(pos, ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState());
        var be = h.getLevel().getBlockEntity(pos);
        if (!(be instanceof com.sqc.nuclearsq.reactor.FuelRodBlockEntity rod)) throw new GameTestAssertException("Kein Brennstab");
        rod.applyComponentsFromItemStack(stack);
        if (rod.getRemaining() != 321) throw new GameTestAssertException("Ladung " + rod.getRemaining());
        List<ItemStack> drops = Block.getDrops(h.getLevel().getBlockState(pos), h.getLevel(), pos, rod);
        if (drops.size() != 1 || FuelRodBlockItem.charge(drops.get(0)) != 321) {
            throw new GameTestAssertException("Drop " + drops + " Ladung " + (drops.isEmpty() ? "-" : FuelRodBlockItem.charge(drops.get(0))));
        }
        GlobalPos gp = GlobalPos.of(Level.OVERWORLD, pos);
        var type = ModDataComponents.GRID_FROM.get();
        var enc = type.codecOrThrow().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, gp).getOrThrow();
        if (!type.codecOrThrow().parse(net.minecraft.nbt.NbtOps.INSTANCE, enc).getOrThrow().equals(gp)) throw new GameTestAssertException("GRID_FROM");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "s6_recipes", timeoutTicks = 20)
    public static void gameDataValidates(GameTestHelper h) {
        if (!GameData.errors().isEmpty()) throw new GameTestAssertException("Ladefehler: " + GameData.errors());
        JsonObject cv = builtIn("cooling_values.json");
        cv.getAsJsonObject("machines").getAsJsonObject("relief_tank").getAsJsonObject("ports").remove("primary_out");
        List<String> errors = new ArrayList<>();
        if (GameData.applyCooling(cv, errors)) throw new GameTestAssertException("cooling_values ohne Port uebernommen");
        cv = builtIn("cooling_values.json");
        cv.getAsJsonObject("shaft").addProperty("create_max_rpm", -5);
        if (GameData.applyCooling(cv, errors)) throw new GameTestAssertException("cooling_values mit Drehzahl -5 uebernommen");
        String all = String.join("\n", errors);
        if (!all.contains("cooling_values.json") || !all.contains("machines.relief_tank.ports.primary_out")
                || !all.contains("shaft")) {
            throw new GameTestAssertException("Fehlermeldung ohne Datei/Schluessel: " + all);
        }
        if (BigMachine.RELIEF_TANK.port("primary_out") == null || ShaftValues.createMaxRpm() <= 0) {
            throw new GameTestAssertException("Alter Stand nicht behalten");
        }
        MultiblockStructure s = BigMachine.RELIEF_TANK.structure;
        JsonObject mb = builtIn("multiblocks/relief_tank.json");
        mb.getAsJsonObject("legend").addProperty("W", "nuclearsq:gibt_es_nicht");
        var data = MultiblockStructure.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, mb).getOrThrow();
        List<String> mbErr = s.override(data, false);
        if (mbErr.stream().noneMatch(e -> e.contains("nuclearsq:gibt_es_nicht"))) throw new GameTestAssertException("Unbekannter Block nicht gemeldet: " + mbErr);
        mb = builtIn("multiblocks/relief_tank.json");
        var size = mb.getAsJsonArray("size");
        size.set(0, new com.google.gson.JsonPrimitive(size.get(0).getAsInt() + 1));
        data = MultiblockStructure.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, mb).getOrThrow();
        if (s.override(data, false).stream().noneMatch(e -> e.startsWith("size"))) throw new GameTestAssertException("Falsche Groesse nicht gemeldet");
        mb = builtIn("multiblocks/relief_tank.json");
        data = MultiblockStructure.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, mb).getOrThrow();
        if (!s.override(data, false).isEmpty()) throw new GameTestAssertException("Eingebautes Muster abgelehnt: " + s.override(data, false));
        JsonObject pc = builtIn("particle_classes.json");
        pc.getAsJsonArray("classes").get(0).getAsJsonObject().remove("weight");
        try {
            com.sqc.nuclearsq.particle.ParticleClasses.parse(pc);
            throw new GameTestAssertException("Kaputte particle_classes angenommen");
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().contains("weight")) throw new GameTestAssertException("Meldung ohne Schluessel: " + e.getMessage());
        }
        h.succeed();
    }

    private static final String PACK = "nuclearsq_gametest_values";
    private static CompletableFuture<Void> loadPack, unloadPack;
    private static Collection<String> before;
    private static String failure;
    private static int purifierRate;

    @GameTest(template = FLOOR, batch = "s6_datapack_reload", timeoutTicks = 400)
    public static void datapackOverridesCoolingValues(GameTestHelper h) {
        var server = h.getLevel().getServer();
        Path dir = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        purifierRate = BigMachine.STEAM_PURIFIER.port("steam_in").rate();
        try {
            JsonObject cv = builtIn("cooling_values.json");
            cv.getAsJsonObject("machines").getAsJsonObject("steam_purifier").getAsJsonObject("ports").getAsJsonObject("steam_in")
                    .addProperty("rate", purifierRate + 7);
            cv.getAsJsonObject("shaft").addProperty("create_max_rpm", 128);
            Files.createDirectories(dir.resolve("data/nuclearsq"));
            Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"NuclearSQ GameTest\"}}");
            Files.writeString(dir.resolve("data/nuclearsq/cooling_values.json"), cv.toString());
        } catch (IOException e) {
            throw new GameTestAssertException("Datenpaket nicht schreibbar: " + e.getMessage());
        }
        var repo = server.getPackRepository();
        repo.reload();
        before = List.copyOf(repo.getSelectedIds());
        List<String> with = new ArrayList<>(before);
        with.add("file/" + PACK);
        if (!repo.getAvailableIds().contains("file/" + PACK)) throw new GameTestAssertException("Paket nicht gefunden: " + repo.getAvailableIds());
        loadPack = server.reloadResources(with);
        unloadPack = null;
        failure = null;
        h.succeedWhen(() -> {
            if (!loadPack.isDone()) throw new GameTestAssertException("laedt");
            if (unloadPack == null) {
                List<String> problems = new ArrayList<>();
                if (loadPack.isCompletedExceptionally()) problems.add("Neuladen fehlgeschlagen");
                if (BigMachine.STEAM_PURIFIER.port("steam_in").rate() != purifierRate + 7) problems.add("Port-Rate " + BigMachine.STEAM_PURIFIER.port("steam_in").rate());
                if (ShaftValues.createMaxRpm() != 128) problems.add("Welle " + ShaftValues.createMaxRpm());
                if (!GameData.errors().isEmpty()) problems.add("Fehler " + GameData.errors());
                try {
                    deleteTree(dir);
                } catch (IOException e) {
                    problems.add("Aufraeumen: " + e.getMessage());
                }
                server.getPackRepository().reload();
                unloadPack = server.reloadResources(before);
                if (!problems.isEmpty()) failure = "Datenpaket wirkt nicht: " + problems;
            }
            if (!unloadPack.isDone()) throw new GameTestAssertException("entlaedt");
            if (failure != null) throw new GameTestAssertException(failure);
            if (BigMachine.STEAM_PURIFIER.port("steam_in").rate() != purifierRate || ShaftValues.createMaxRpm() != 256) {
                throw new GameTestAssertException("Stand der Mod nach dem Entfernen nicht zurueck");
            }
        });
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    private static JsonObject builtIn(String path) {
        String res = "/data/nuclearsq/" + path;
        try (InputStream in = DataPackTests.class.getResourceAsStream(res)) {
            if (in == null) throw new GameTestAssertException(res + " fehlt");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new GameTestAssertException(res + ": " + e.getMessage());
        }
    }

}
