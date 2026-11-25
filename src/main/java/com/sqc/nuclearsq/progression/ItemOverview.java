package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

public final class ItemOverview {
    private ItemOverview() {}

    public record Entry(String item, int group, int stage, @Nullable String firstMachine) {}

    public static final List<String> GROUPS = List.of("ore_metal", "oil_plastic", "parts_modules", "chips_computing", "data_storage",
            "uranium_fuel", "power_grid", "pipes_ports", "protection", "reactor_parts", "waste_bombs", "robotics", "fusion");

    private static final String[][] RULES = {
            {"fusion", "lithium|tritium|deuterium|fusion|plasma|first_wall"},
            {"waste_bombs", "bomb|detonator|spent|waste|ash|castor|plutonium|neptunium|strontium|glass_canister|rubble|slag|weapons|tsar"},
            {"uranium_fuel", "uran|yellowcake|uf6|fuel_rod|mox|enriched|depleted|thorium|pellet"},
            {"robotics", "robot|robo_|nano|exo_|drone|link_tool|remote"},
            {"protection", "hazmat|respirator|geiger|dosimeter|anti_rad|medicine|decon|filter|lead_glass|sensor|area_monitor|survey"},
            {"chips_computing", "chip_|cpu_|ram_|circuit|silicon|wafer|quantum|compute"},
            {"data_storage", "data_|storage|hard_drive|terminal|wifi|bank|coin|card|monitor|rack|crate|chest|pattern|net_|control_wall|desk|tablet"},
            {"power_grid", "cable|battery|power|mast|wire|solar|wind|turbine|generator|translator|rtg"},
            {"pipes_ports", "pipe|pump|port|valve|tank|bucket|canister|fluid|nozzle|coolant|feedwater"},
            {"oil_plastic", "rubber|seal|hdpe|resin|photoresist|coke|^oil|polymer|lubricant|sulfur|acid|ethylene"},
            {"reactor_parts", "condenser|recombiner|purifier|water_|blanket|moderator|cooler|vent|heat|pressure|cooling|reactor|rod|control|graphite|boric|borax|heavy_water|containment"},
            {"ore_metal", "ore|raw|dust|ingot|plate|sheet|block|nugget|zircon|lead|tin|osmium|fluorite|steel|brass|copper|gold|iron"}};

    public static int group(String id) {
        for (String[] r : RULES) if (java.util.regex.Pattern.compile(r[1]).matcher(id).find()) return GROUPS.indexOf(r[0]);
        return GROUPS.indexOf("parts_modules");
    }

    public static boolean hidden(String id, Set<String> machines) {
        return id.endsWith("_kit_sqc") || id.endsWith("_controller_sqc") || machines.contains(id) || id.startsWith("header_")
                || id.equals("spacer_sqc") || id.startsWith("geiger_counter_needle_") || id.matches("exo_core_sqc_\\d+") || id.contains("creative");
    }

    public static boolean station(RecipeSources.Source s, Set<String> passed) {
        if (s.where() != RecipeSources.Where.SMALL_PARTS_ASSEMBLER) return true;
        ItemStack r = s.result();
        if (r.is(com.sqc.nuclearsq.machine.big.BigCrafter.MICRO_PRODUCTS)) return passed.contains("micro");
        if (r.is(com.sqc.nuclearsq.machine.big.BigCrafter.CLEAN_ROOM_PRODUCTS)) return passed.contains("clean_room") && passed.contains("robot_arm");
        if (r.is(com.sqc.nuclearsq.machine.big.BigCrafter.SOLDER_PRODUCTS)) return passed.contains("solder_machine");
        return true;
    }

    public static int stageAt(int k) {
        int stage = 0;
        for (int i = 0; i <= Math.min(k, AchievementTree.NODES.size() - 1); i++) if (AchievementTree.stageAt(i) != null) stage++;
        return Math.max(1, stage);
    }

    public static Map<Item, Integer> buildableAt(List<RecipeSources.Source> sources) {
        Set<Item> craftable = new HashSet<>();
        for (var s : sources) craftable.add(s.result().getItem());
        Set<RecipeSources.Where> open = java.util.EnumSet.of(RecipeSources.Where.WORKBENCH, RecipeSources.Where.FURNACE, RecipeSources.Where.BLAST_FURNACE);
        Map<Item, Integer> at = new HashMap<>();
        Set<String> passed = new HashSet<>();
        var nodes = AchievementTree.NODES;
        for (int k = 0; k <= nodes.size(); k++) {
            boolean more = true;
            while (more) {
                more = false;
                for (var s : sources) {
                    Item res = s.result().getItem();
                    if (!open.contains(s.where()) || at.containsKey(res) || !station(s, passed)) continue;
                    boolean ok = true;
                    for (var need : s.needs()) {
                        boolean any = false;
                        for (ItemStack st : need.ingredient().getItems()) {
                            Item it = st.getItem();
                            boolean mod = BuiltInRegistries.ITEM.getKey(it).getNamespace().equals(NuclearSQ.MODID);
                            if (!mod || !craftable.contains(it) || at.containsKey(it)) { any = true; break; }
                        }
                        if (!any) { ok = false; break; }
                    }
                    if (ok) { at.put(res, k); more = true; }
                }
            }
            if (k == nodes.size()) break;
            var unlock = AchievementTree.unlocks(nodes.get(k).key());
            if (unlock != null) open.add(unlock);
            passed.add(nodes.get(k).key());
        }
        return at;
    }

    private static List<Entry> cache;
    private static RecipeManager cacheFor;

    public static synchronized List<Entry> get(RecipeManager recipes, HolderLookup.Provider regs) {
        if (cache == null || cacheFor != recipes) {
            cache = compute(recipes, regs);
            cacheFor = recipes;
        }
        return cache;
    }

    public static List<Entry> compute(RecipeManager recipes, HolderLookup.Provider regs) {
        var sources = RecipeSources.all(recipes, regs);
        Map<Item, Integer> at = buildableAt(sources);
        Map<Item, RecipeSources.Source> first = new HashMap<>();
        for (var s : sources) first.merge(s.result().getItem(), s, (a, b) -> b.where().ordinal() < a.where().ordinal() ? b : a);
        Map<Item, Integer> need = new HashMap<>();
        Set<String> machines = new HashSet<>();
        var nodes = AchievementTree.NODES;
        for (int k = 0; k < nodes.size(); k++) {
            machines.add(nodes.get(k).item());
            List<Item> todo = new ArrayList<>();
            for (String id : AchievementTree.proofItems(nodes.get(k))) todo.add(item(id));
            Set<Item> seen = new HashSet<>(todo);
            while (!todo.isEmpty()) {
                var s = first.get(todo.remove(todo.size() - 1));
                if (s == null) continue;
                for (var n : s.needs())
                    for (ItemStack st : n.ingredient().getItems())
                        if (seen.add(st.getItem())) {
                            need.putIfAbsent(st.getItem(), k);
                            todo.add(st.getItem());
                        }
            }
        }
        Map<String, Entry> fam = new LinkedHashMap<>();
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(NuclearSQ.MODID) || hidden(id.getPath(), machines)) continue;
            String f = CompendiumItems.family(id.getPath());
            Item it = e.getValue();
            int build = at.getOrDefault(it, first.containsKey(it) ? nodes.size() : 0);
            Integer k = need.get(it);
            int stage = Math.max(stageAt(build), k == null ? 0 : stageAt(k));
            Entry en = new Entry(id.getPath(), group(id.getPath()), stage, k == null ? null : nodes.get(k).key());
            fam.merge(f, en, (a, b) -> b.stage() < a.stage() ? b : a);
        }
        List<Entry> out = new ArrayList<>(fam.values());
        out.sort(java.util.Comparator.comparingInt(Entry::stage).thenComparingInt(en -> en.firstMachine() == null ? Integer.MAX_VALUE : AchievementTree.index(en.firstMachine())));
        return List.copyOf(out);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id));
    }
}
