package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.reactor.FuelRodBlockEntity;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.registry.ResourceRegistry;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ResourceTests {
    private ResourceTests() {}

    private static final String FLOOR = "floor";

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }

    @GameTest(template = FLOOR, batch = "resources", timeoutTicks = 20)
    public static void oresGenerateOnlyWithoutMekanism(GameTestHelper h) {
        var mods = h.getLevel().registryAccess().registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        for (var e : ResourceRegistry.ORE_PROVIDERS.entrySet()) {
            boolean provided = e.getValue().stream().anyMatch(m -> ModList.get().isLoaded(m));
            boolean loaded = mods.containsKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "ore_" + e.getKey()));
            if (loaded == provided) throw new GameTestAssertException("Erz-Spawn " + e.getKey() + (loaded ? " aktiv" : " aus") + ", Anbieter-Mod " + (provided ? "da" : "fehlt"));
        }
        for (String[] t : List.of(new String[] {"ores/uranium", "uranium_ore_sqc"}, new String[] {"ores/zircon", "deepslate_zircon_ore_sqc"},
                new String[] {"raw_materials/lead", "raw_lead_sqc"}, new String[] {"ingots/steel", "steel_ingot_sqc"},
                new String[] {"gems/fluorite", "fluorite_gem_sqc"}, new String[] {"gems/borax", "borax_sqc"}, new String[] {"ingots/tin", "tin_ingot_sqc"},
                new String[] {"ores/sulfur", "nether_sulfur_ore_sqc"})) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", t[0]));
            if (!new ItemStack(item(t[1])).is(tag)) throw new GameTestAssertException(t[1] + " fehlt in c:" + t[0]);
        }
        if (ResourceRegistry.tabItems().size() != 65) throw new GameTestAssertException("Rohstoffe: " + ResourceRegistry.tabItems().size());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "resources", timeoutTicks = 20)
    public static void replacementsAlwaysHaveRecipe(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        for (String id : List.of("steel_ingot_sqc", "elite_circuit_sqc", "ultimate_circuit_sqc", "hdpe_sheet_sqc",
                "enriched_carbon_sqc", "refined_obsidian_ingot_sqc", "refined_glowstone_ingot_sqc")) {
            boolean has = !com.sqc.nuclearsq.progression.RecipeSources.forItem(rm, h.getLevel().registryAccess(), item(id)).isEmpty();
            if (!has) throw new GameTestAssertException(id + ": Rezept fehlt");
        }
        if (rm.byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "small_parts_assembly/basic_circuit_sqc")).isEmpty())
            throw new GameTestAssertException("basic_circuit_sqc: Mikro-Rezept fehlt");
        if (AssemblyRecipe.forItem(rm, item("basic_circuit_sqc")) != null) throw new GameTestAssertException("basic_circuit_sqc hat noch ein Reaktor-Assembler-Rezept");
        if (!new ItemStack(item("basic_circuit_sqc")).is(com.sqc.nuclearsq.machine.big.BigCrafter.MICRO_PRODUCTS)) throw new GameTestAssertException("basic_circuit_sqc fehlt im Tag micro_products");
        var board = com.sqc.nuclearsq.assembler.SmallPartsRecipe.forItem(rm, item("control_board_1_sqc"));
        if (board == null) throw new GameTestAssertException("Steuerplatine I ohne Rezept");
        boolean takesOwn = board.ingredients().stream().anyMatch(n -> n.matches(new ItemStack(item("basic_circuit_sqc"))));
        if (!takesOwn) throw new GameTestAssertException("Steuerplatine I nimmt den eigenen Schaltkreis nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "resources", timeoutTicks = 20)
    public static void fuelBalancing(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        boolean core = false, spent = false, ash = false;
        for (var holder : rm.getRecipes()) {
            if (!(holder.value() instanceof ReprocessingRecipe r)) continue;
            if (r.results().isEmpty()) continue;
            ItemStack first = r.results().get(0).stack();
            if (first.is(com.sqc.nuclearsq.registry.CatalogRegistry.ITEM_MAP.get("uranium_core_sqc").get())
                    && r.ingredient().test(new ItemStack(ReactorRegistry.ENRICHED_URANIUM.get()))) {
                if (r.count() != 1) throw new GameTestAssertException("Uran-Kern braucht " + r.count() + " angereichertes Uran");
                core = true;
            }
            if (r.ingredient().test(new ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.SPENT_FUEL.get()))) {
                if (first.getCount() != 6) throw new GameTestAssertException("Abgebrannter Brennstoff gibt " + first.getCount() + " Staub");
                spent = true;
            }
            if (r.ingredient().test(new ItemStack(com.sqc.nuclearsq.registry.CatalogRegistry.ASH_ITEM.get()))) ash = true;
        }
        if (!core || !spent || !ash) throw new GameTestAssertException("Rezepte fehlen: Kern " + core + ", Brennstoff " + spent + ", Asche " + ash);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "resources", timeoutTicks = 20)
    public static void oldFuelRodsKeepTheirShare(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 2, 1);
        h.setBlock(p, ReactorRegistry.FUEL_ROD_BLOCK.get());
        if (!(h.getBlockEntity(p) instanceof FuelRodBlockEntity f)) throw new GameTestAssertException("ohne BlockEntity");
        CompoundTag old = new CompoundTag();
        old.putInt("remaining", 500);
        f.loadCustomOnly(old, h.getLevel().registryAccess());
        if (f.getRemaining() != FuelRodBlockEntity.UNIT / 2) throw new GameTestAssertException("alte halbe Ladung -> " + f.getRemaining());
        CompoundTag now = f.saveCustomOnly(h.getLevel().registryAccess());
        f.loadCustomOnly(now, h.getLevel().registryAccess());
        if (f.getRemaining() != FuelRodBlockEntity.UNIT / 2) throw new GameTestAssertException("neu gespeichert -> " + f.getRemaining());
        h.succeed();
    }
}
