package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.EquipmentRecipe;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

public final class RecipeSources {
    private RecipeSources() {}

    public enum Where {
        WORKBENCH("workbench", "minecraft:crafting_table", true),
        REACTOR_ASSEMBLER("reactor_assembler", "nuclearsq:reactor_assembler_controller_sqc", false),
        SMALL_PARTS_ASSEMBLER("small_parts_assembler", "nuclearsq:small_parts_assembler_controller_sqc", false),
        EQUIPMENT_ASSEMBLER("equipment_assembler", "nuclearsq:equipment_assembler_controller_sqc", false),
        HEAVY_ASSEMBLER("heavy_assembler", "nuclearsq:heavy_assembler_controller_sqc", true),
        ORE_MILLING("ore_milling", "nuclearsq:ore_mill_controller_sqc", false),
        ENRICHMENT("enrichment", "nuclearsq:enrichment_centrifuge_controller_sqc", false),
        REPROCESSING("reprocessing", "nuclearsq:reprocessing_plant_controller_sqc", false),
        WEAPONS_PROCESSING("weapons_processing", "nuclearsq:weapons_processing_controller_sqc", false),
        ACID_PLANT("acid_plant", "nuclearsq:acid_plant_controller_sqc", false),
        BORIC_ACID("boric_acid", "nuclearsq:boric_acid_plant_controller_sqc", false),
        ION_EXCHANGE("ion_exchange", "nuclearsq:ion_exchanger_controller_sqc", false),
        HEAVY_WATER("heavy_water", "nuclearsq:heavy_water_plant_controller_sqc", false),
        POLYMER("polymer", "nuclearsq:polymer_plant_controller_sqc", false),
        VITRIFICATION("vitrification", "nuclearsq:vitrification_plant_controller_sqc", false),
        CHEMISTRY("chemistry", "nuclearsq:chemical_lab_controller_sqc", false),
        LEACHING("leaching", "nuclearsq:leaching_plant_controller_sqc", false),
        UF6_CONVERSION("uf6_conversion", "nuclearsq:uf6_converter_controller_sqc", false),
        PELLETING("pelleting", "nuclearsq:pellet_factory_controller_sqc", false),
        FOUNDRY("foundry", "nuclearsq:foundry_controller_sqc", false),
        SLAG_CRUSHING("slag_crushing", "nuclearsq:slag_crusher_controller_sqc", false),
        PLATE_MILLING("plate_milling", "nuclearsq:plate_mill_controller_sqc", false),
        DUST_MILLING("dust_milling", "nuclearsq:hammer_mill_controller_sqc", false),
        KILN_FIRING("kiln_firing", "nuclearsq:kiln_controller_sqc", false),
        BLAST_SMELTING("blast_smelting", "nuclearsq:blast_furnace_controller_sqc", false),
        CNC_MILL("cnc_mill", "nuclearsq:cnc_mill_controller_sqc", false),
        SOLDER_MACHINE("solder_machine", "nuclearsq:solder_machine_controller_sqc", false),
        FURNACE("furnace", "minecraft:furnace", false),
        BLAST_FURNACE("blast_furnace", "minecraft:blast_furnace", false),
        OTHER("other", "minecraft:air", false);

        public final String key;
        public final ResourceLocation icon;
        public final boolean shaped;

        Where(String key, String icon, boolean shaped) {
            this.key = key;
            this.icon = ResourceLocation.parse(icon);
            this.shaped = shaped;
        }

        public String langKey() {
            return "compendium.made_in." + key;
        }

        public ItemStack iconStack() {
            Item it = BuiltInRegistries.ITEM.get(icon);
            return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
        }

        public boolean assembler() {
            return this == REACTOR_ASSEMBLER || this == SMALL_PARTS_ASSEMBLER || this == EQUIPMENT_ASSEMBLER;
        }
    }

    public static final List<Where> CATALOG_STAGES = List.of(Where.WORKBENCH, Where.FURNACE,
            Where.BLAST_FURNACE, Where.SMALL_PARTS_ASSEMBLER, Where.REACTOR_ASSEMBLER, Where.EQUIPMENT_ASSEMBLER,
            Where.HEAVY_ASSEMBLER, Where.ORE_MILLING, Where.PLATE_MILLING, Where.CNC_MILL, Where.SOLDER_MACHINE, Where.DUST_MILLING, Where.KILN_FIRING, Where.BLAST_SMELTING, Where.ACID_PLANT, Where.LEACHING, Where.UF6_CONVERSION, Where.ENRICHMENT, Where.PELLETING, Where.FOUNDRY, Where.SLAG_CRUSHING,
            Where.BORIC_ACID, Where.ION_EXCHANGE, Where.HEAVY_WATER, Where.POLYMER, Where.VITRIFICATION, Where.CHEMISTRY, Where.REPROCESSING, Where.WEAPONS_PROCESSING, Where.OTHER);

    private static final java.util.Map<String, String> OTHER_MACHINES = java.util.Map.of(
            "mekanism:enriching", "mekanism:enrichment_chamber");

    public static ItemStack otherMachine(ResourceLocation type) {
        String id = OTHER_MACHINES.get(type.toString());
        if (id == null) return ItemStack.EMPTY;
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    public record Need(Ingredient ingredient, int count) {
        public ItemStack display(long ms) {
            ItemStack[] all = ingredient.getItems();
            if (all.length == 0) return new ItemStack(Items.BARRIER);
            return all[(int) ((ms / 1000) % all.length)].copyWithCount(1);
        }
    }

    public record Grid(int width, int height, List<Ingredient> cells) {
        public Ingredient cell(int row, int col) {
            return cells.get(row * width + col);
        }
    }

    public record Source(Where where, ResourceLocation type, List<Need> needs, ItemStack result, float chance,
                         @javax.annotation.Nullable Grid grid) {}

    public static List<Source> forItem(RecipeManager recipes, HolderLookup.Provider regs, Item item) {
        List<Source> out = new ArrayList<>();
        for (var holder : recipes.getRecipes()) {
            Source s;
            try {
                s = of(holder.value(), regs, item);
            } catch (RuntimeException e) {
                continue;
            }
            if (s != null) out.add(s);
        }
        out.sort(Comparator.comparingInt((Source s) -> s.where().ordinal()).thenComparing(s -> -s.chance()));
        return out;
    }

    public static List<Source> all(RecipeManager recipes, HolderLookup.Provider regs) {
        List<Source> out = new ArrayList<>();
        for (var holder : recipes.getRecipes()) {
            Recipe<?> r = holder.value();
            try {
                if (r instanceof ReprocessingRecipe rr) {
                    for (var res : rr.results()) {
                        Source s = of(r, regs, res.stack().getItem());
                        if (s != null && out.stream().noneMatch(o -> o.result() == s.result())) out.add(s);
                    }
                } else {
                    ItemStack res = r.getResultItem(regs);
                    Source s = res.isEmpty() ? null : of(r, regs, res.getItem());
                    if (s != null) out.add(s);
                }
            } catch (RuntimeException e) {
            }
        }
        return out;
    }

    public static List<Item> craftable(RecipeManager recipes, HolderLookup.Provider regs) {
        java.util.Set<Item> made = new java.util.HashSet<>();
        for (var holder : recipes.getRecipes()) {
            Recipe<?> r = holder.value();
            if (r instanceof ReprocessingRecipe rr) {
                for (var res : rr.results()) made.add(res.stack().getItem());
            } else {
                ItemStack res;
                try {
                    res = r.getResultItem(regs);
                } catch (RuntimeException e) {
                    continue;
                }
                if (!res.isEmpty()) made.add(res.getItem());
            }
        }
        List<Item> out = new ArrayList<>();
        for (var e : BuiltInRegistries.ITEM.entrySet())
            if (e.getKey().location().getNamespace().equals(NuclearSQ.MODID) && made.contains(e.getValue())) out.add(e.getValue());
        return out;
    }

    public static Where where(Recipe<?> r) {
        if (r instanceof HeavyAssemblyRecipe) return Where.HEAVY_ASSEMBLER;
        if (r instanceof SmallPartsRecipe s) return s.result().is(com.sqc.nuclearsq.machine.big.BigCrafter.CNC_PRODUCTS) ? Where.CNC_MILL
                : s.result().is(com.sqc.nuclearsq.machine.big.BigCrafter.SOLDER_PRODUCTS) ? Where.SOLDER_MACHINE : Where.SMALL_PARTS_ASSEMBLER;
        if (r instanceof EquipmentRecipe) return Where.EQUIPMENT_ASSEMBLER;
        if (r instanceof AssemblyRecipe) return Where.REACTOR_ASSEMBLER;
        if (r instanceof ReprocessingRecipe rr) return switch (rr.kind()) {
            case REPROCESSING -> Where.REPROCESSING;
            case ORE_MILLING -> Where.ORE_MILLING;
            case ENRICHMENT -> Where.ENRICHMENT;
            case WEAPONS_PROCESSING -> Where.WEAPONS_PROCESSING;
            case ACID_PLANT -> Where.ACID_PLANT;
            case BORIC_ACID -> Where.BORIC_ACID;
            case ION_EXCHANGE -> Where.ION_EXCHANGE;
            case HEAVY_WATER -> Where.HEAVY_WATER;
            case POLYMER -> Where.POLYMER;
            case VITRIFICATION -> Where.VITRIFICATION;
            case CHEMISTRY -> Where.CHEMISTRY;
            case LEACHING -> Where.LEACHING;
            case UF6_CONVERSION -> Where.UF6_CONVERSION;
            case PELLETING -> Where.PELLETING;
            case FOUNDRY -> Where.FOUNDRY;
            case SLAG_CRUSHING -> Where.SLAG_CRUSHING;
            case PLATE_MILLING -> Where.PLATE_MILLING;
            case DUST_MILLING -> Where.DUST_MILLING;
            case KILN_FIRING -> Where.KILN_FIRING;
            case BLAST_SMELTING -> Where.BLAST_SMELTING;
        };
        if (r.getType() == RecipeType.CRAFTING) return Where.WORKBENCH;
        if (r.getType() == RecipeType.SMELTING) return Where.FURNACE;
        if (r.getType() == RecipeType.BLASTING) return Where.BLAST_FURNACE;
        return Where.OTHER;
    }

    @javax.annotation.Nullable
    static Source of(Recipe<?> r, HolderLookup.Provider regs, Item item) {
        Where where = where(r);
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
        if (type == null) type = ResourceLocation.withDefaultNamespace("unknown");
        if (r instanceof ReprocessingRecipe rr) {
            for (var res : rr.results()) {
                if (res.stack().is(item)) {
                    List<Need> in = new ArrayList<>();
                    in.add(new Need(rr.ingredient(), rr.count()));
                    if (rr.hasAux()) in.add(new Need(rr.aux(), rr.auxCount()));
                    return new Source(where, type, in, res.stack(), res.chance(), null);
                }
            }
            return null;
        }
        ItemStack result = r.getResultItem(regs);
        if (result.isEmpty() || !result.is(item)) return null;
        List<Need> needs = new ArrayList<>();
        if (r instanceof AssemblyRecipe a) {
            for (var n : a.ingredients()) needs.add(new Need(n.ingredient(), n.count()));
        } else if (r instanceof SmallPartsRecipe s) {
            for (var n : s.ingredients()) needs.add(new Need(n.ingredient(), n.count()));
        } else if (r instanceof EquipmentRecipe e) {
            for (var n : e.base().ingredients()) needs.add(new Need(n.ingredient(), n.count()));
        } else if (r instanceof HeavyAssemblyRecipe hv) {
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (String row : hv.pattern())
                for (char c : row.toCharArray()) if (c != ' ') counts.merge(String.valueOf(c), 1, Integer::sum);
            for (var e : counts.entrySet()) {
                Ingredient in = hv.key().get(e.getKey());
                if (in != null) needs.add(new Need(in, e.getValue()));
            }
        } else {
            for (Ingredient in : r.getIngredients()) add(needs, in);
        }
        return new Source(where, type, needs, result, 1f, gridOf(r));
    }

    @javax.annotation.Nullable
    public static Grid gridOf(Recipe<?> r) {
        if (r instanceof HeavyAssemblyRecipe hv) {
            List<Ingredient> cells = new ArrayList<>();
            for (String row : hv.pattern()) {
                for (int c = 0; c < hv.width(); c++) {
                    char ch = c < row.length() ? row.charAt(c) : ' ';
                    Ingredient in = ch == ' ' ? null : hv.key().get(String.valueOf(ch));
                    cells.add(in == null ? Ingredient.EMPTY : in);
                }
            }
            return new Grid(hv.width(), hv.height(), List.copyOf(cells));
        }
        if (r instanceof net.minecraft.world.item.crafting.ShapedRecipe sh) {
            return new Grid(sh.getWidth(), sh.getHeight(), List.copyOf(sh.getIngredients()));
        }
        return null;
    }

    private static void add(List<Need> needs, Ingredient in) {
        if (in.isEmpty()) return;
        for (int i = 0; i < needs.size(); i++) {
            if (same(needs.get(i).ingredient(), in)) {
                needs.set(i, new Need(in, needs.get(i).count() + 1));
                return;
            }
        }
        needs.add(new Need(in, 1));
    }

    private static boolean same(Ingredient a, Ingredient b) {
        if (a.equals(b)) return true;
        ItemStack[] x = a.getItems(), y = b.getItems();
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (!ItemStack.isSameItemSameComponents(x[i], y[i])) return false;
        return true;
    }
}
