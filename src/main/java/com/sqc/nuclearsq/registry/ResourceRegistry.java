package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ResourceRegistry {
    private ResourceRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, NuclearSQ.MODID);
    public static final DeferredHolder<Feature<?>, OreDepositFeature> ORE_DEPOSIT = FEATURES.register("ore_deposit", OreDepositFeature::new);

    private record Ore(String name, String drop, boolean gem) {}

    private static final List<Ore> ORES = List.of(
            new Ore("uranium", "raw_uranium_sqc", false),
            new Ore("lead", "raw_lead_sqc", false),
            new Ore("tin", "raw_tin_sqc", false),
            new Ore("osmium", "raw_osmium_sqc", false),
            new Ore("fluorite", "fluorite_gem_sqc", true),
            new Ore("zircon", "zircon_sqc", true),
            new Ore("borax", "borax_sqc", true));

    public static final Map<String, List<String>> ORE_PROVIDERS = Map.of(
            "uranium", List.of("mekanism", "immersiveengineering", "createnuclear"),
            "lead", List.of("mekanism", "thermal", "thermal_foundation", "immersiveengineering"),
            "tin", List.of("mekanism", "thermal", "thermal_foundation"),
            "osmium", List.of("mekanism"),
            "fluorite", List.of("mekanism"),
            "zircon", List.of(),
            "borax", List.of(),
            "sulfur", List.of());

    private static final List<String> POOR = List.of("uranium", "lead", "tin", "osmium", "fluorite", "zircon", "borax", "lithium", "iron", "copper", "gold");
    private static final List<String> NUGGETS = List.of("lead", "tin", "osmium", "lithium", "copper");

    private static final String NETHER_SULFUR = "nether_sulfur_ore_sqc";

    private static final List<String> ITEM_IDS = List.of(
            "lead_ingot_sqc", "tin_ingot_sqc", "osmium_ingot_sqc", "steel_ingot_sqc",
            "lead_dust_sqc", "tin_dust_sqc", "osmium_dust_sqc", "sulfur_dust_sqc",
            "basic_circuit_sqc", "elite_circuit_sqc", "ultimate_circuit_sqc",
            "hdpe_sheet_sqc", "enriched_carbon_sqc", "refined_obsidian_ingot_sqc", "refined_glowstone_ingot_sqc", "etched_obsidian_sqc");

    public static final Map<String, DeferredBlock<Block>> ORE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<BlockItem>> ORE_ITEMS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> ITEM_MAP = new LinkedHashMap<>();

    static {
        for (Ore o : ORES) {
            for (boolean deep : new boolean[] {false, true}) {
                String id = (deep ? "deepslate_" : "") + o.name + "_ore_sqc";
                DeferredBlock<Block> b = BLOCKS.register(id, () -> ore(o.gem, deep));
                ORE_BLOCKS.put(id, b);
                ORE_ITEMS.put(id, ITEMS.registerSimpleBlockItem(id, b));
            }
            ITEM_MAP.put(o.drop, ITEMS.registerSimpleItem(o.drop));
        }
        DeferredBlock<Block> ns = BLOCKS.register(NETHER_SULFUR, () -> new DropExperienceBlock(UniformInt.of(1, 3), BlockBehaviour.Properties.of()
                .mapColor(MapColor.NETHER).strength(3.0f, 3.0f).sound(SoundType.NETHER_ORE).requiresCorrectToolForDrops()));
        ORE_BLOCKS.put(NETHER_SULFUR, ns);
        ORE_ITEMS.put(NETHER_SULFUR, ITEMS.registerSimpleBlockItem(NETHER_SULFUR, ns));
        for (String id : ITEM_IDS) ITEM_MAP.put(id, ITEMS.registerSimpleItem(id));
        for (String n : POOR) {
            for (boolean deep : new boolean[] {false, true}) {
                String id = (deep ? "deepslate_" : "") + "poor_" + n + "_ore_sqc";
                DeferredBlock<Block> b = BLOCKS.register(id, () -> ore(false, deep));
                ORE_BLOCKS.put(id, b);
                ORE_ITEMS.put(id, ITEMS.registerSimpleBlockItem(id, b));
            }
        }
        for (String n : NUGGETS) ITEM_MAP.put(n + "_nugget_sqc", ITEMS.registerSimpleItem(n + "_nugget_sqc"));
    }

    private static Block ore(boolean gem, boolean deep) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                .mapColor(deep ? MapColor.DEEPSLATE : MapColor.STONE)
                .strength(deep ? 4.5f : 3.0f, 3.0f)
                .sound(deep ? SoundType.DEEPSLATE : SoundType.STONE)
                .requiresCorrectToolForDrops();
        return new DropExperienceBlock(gem ? UniformInt.of(2, 5) : ConstantInt.of(0), p);
    }

    public static boolean fromWorld(String id) {
        return ORE_ITEMS.containsKey(id) || ORES.stream().anyMatch(o -> o.drop.equals(id));
    }

    public static boolean replacedByMekanism(String id) {
        return MEKANISM_REPLACED.contains(id) && net.neoforged.fml.ModList.get().isLoaded("mekanism");
    }

    private static final java.util.Set<String> MEKANISM_REPLACED = java.util.Set.of(
            "lead_dust_sqc", "tin_dust_sqc", "osmium_dust_sqc", "sulfur_dust_sqc");

    public static Item item(String id) {
        return ITEM_MAP.get(id).get();
    }

    public static List<ItemLike> tabItems() {
        List<ItemLike> out = new ArrayList<>();
        ORE_ITEMS.values().forEach(d -> out.add(d.get()));
        ITEM_MAP.values().forEach(d -> out.add(d.get()));
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
    }
}
