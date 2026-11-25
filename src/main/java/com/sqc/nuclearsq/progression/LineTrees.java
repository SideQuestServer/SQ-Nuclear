package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.progression.PlantOverview.Edge;
import com.sqc.nuclearsq.progression.PlantOverview.Node;

public final class LineTrees {
    private LineTrees() {}

    public record Block(int stage, int cols, List<String> keys, List<Integer> rows, int out, List<Integer> joins, @javax.annotation.Nullable String made, @javax.annotation.Nullable String from) {}

    public record Line(String key, String chapter, List<Block> blocks, List<String> products) {
        public String page() { return "line_" + key; }
    }

    private static final String[] TABLE = {
            "assembly|assembly|1:4 kiln / +small_parts micro assembler *heavy / +blast_furnace;4:3 component particle equipment"
                    + "|steel_sheet_sqc andesite_housing_sqc brass_bearing_sqc basic_circuit_sqc chip_1_sqc cpu_module_1_sqc ram_module_256mb_sqc active_cooler_sqc anti_rad_medicine_sqc",
            "power|renewable|1:4 coal / solar_panel solar_collector solar / wind_small wind_medium wind_large;3:1 gas_generator;5:1 rtg;7:3 mast mast_large / battery battery_medium battery_large|",
            "storage|storage|2:5 rack_small rack_normal rack_large *storage_monitor / storage_terminal crafting_terminal wifi_adapter wifi_terminal / storage_crate pipe_adapter / bank_server bank_terminal bank_modem exchange;4:3 rack_pattern compute_cluster / quantum_chest|",
            "metal|metal|1:5 plate_mill cnc_mill hammer_mill kiln blast_furnace;4:1 foundry"
                    + "|copper_plate_sqc gold_plate_sqc iron_plate_sqc steel_sheet_sqc zircaloy_sheet_sqc copper_dust_sqc iron_dust_sqc gold_dust_sqc zirconium_ingot_sqc glass_plate_sqc glass_seal_sqc steel_ingot_sqc atomic_hull_plate_sqc lead_block_sqc refined_glowstone_ingot_sqc silicon_raw_sqc silicon_wafer_sqc",
            "oil|chem_oil|3:5 oil_derrick distillation_column cracker fluid_buffer / polymer|hdpe_sheet_sqc ion_resin_sqc coke_sqc photoresist_sqc rubber_sqc rubber_seal_sqc",
            "chips|chips|4:4 chemlab solder_machine clean_room compute_asm;10:1 quantum"
                    + "|lubricant_sqc silicon_pure_sqc chip_2_sqc chip_3_sqc cpu_module_32_sqc cpu_module_128_sqc ram_module_8gb_sqc ram_module_32gb_sqc",
            "robots|robots|4:3 robot_arm arm_upgrade heavy_arm;6:1 rod_gripper;9:2 decon_dock decon_robot;10:5 robo_dog robo_ball / robo_cat feather_wand / survey_drone|",
            "exo|robots|10:6 exo_station exo exo_core exo_module nano nano_upgrade|",
            "uranium|chem_uran|3:1 acid;5:6 ore leaching uf6 centrifuge / +pellets rod"
                    + "|sulfur_dust_sqc borax_sqc uranium_oxide_sqc uranium_sludge_sqc uf6_cylinder_sqc depleted_uranium_sqc enriched_uranium_sqc uranium_pellet_sqc uranium_core_sqc lithium_pellet_sqc",
            "reactor|reactor|6:4 containment drive / +pumps main_pump pressurizer *steam_generator / emergency relief_tank hp_injection / xenon_filter recombiner door airlock / desk image_monitor multi_display"
                    + ";7:4 separator dryer purifier reheater / +*turbine shaft substation;8:4 condenser tower tower_xl / intake_pump treatment preheater fwpump|",
            "reactor_chem|chem_reactor|6:3 boric exchanger heavywater|boric_acid_sqc spent_resin_sqc heavy_water_cell_sqc",
            "waste|chem_waste|9:4 rack castor reprocessing / +breeder weapons / slag_crusher vitrification repository / shower"
                    + "|lead_dust_sqc zirconium_dust_sqc nuclear_ash_sqc plutonium_oxide_sqc plutonium_238_oxide_sqc neptunium_oxide_sqc glass_canister_sqc strontium_cesium_sqc plutonium_metal_sqc",
            "fusion|fusion_core|10:5 electrolyzer fuel_cell / deuterium_separator fusion superconductor|"};

    public static final List<Line> LINES = parse();

    private static List<Line> parse() {
        List<Line> out = new ArrayList<>();
        for (String row : TABLE) out.add(line(row));
        return List.copyOf(out);
    }

    public static Line line(String row) {
        String[] f = row.split("\\|", -1);
        List<Block> blocks = new ArrayList<>();
        for (String b : f[2].split(";")) {
            String[] w = b.trim().split("\\s+");
            String[] hf = w[0].split("@"), sc = hf[0].split(":");
            boolean num = sc[0].chars().allMatch(Character::isDigit);
            int cols = Integer.parseInt(sc[1]), exit = -1, len = 0;
            List<String> keys = new ArrayList<>();
            List<Integer> rows = new ArrayList<>(), joins = new ArrayList<>();
            for (int k = 1; k < w.length; k++) {
                if (w[k].equals("/")) {
                    rows.add(len);
                    len = 0;
                    continue;
                }
                if (w[k].contains("*")) exit = keys.size();
                if (w[k].contains("+")) joins.add(keys.size());
                keys.add(w[k].replace("*", "").replace("+", ""));
                len++;
            }
            rows.add(len);
            if (rows.size() == 1) {
                rows.clear();
                int c = Math.min(Math.min(cols, MAX_COLS), keys.size());
                for (int k = 0; k < keys.size(); k += c) rows.add(Math.min(c, keys.size() - k));
            }
            blocks.add(new Block(num ? Integer.parseInt(sc[0]) : 0, cols, List.copyOf(keys), List.copyOf(rows), exit < 0 ? keys.size() - 1 : exit, List.copyOf(joins),
                    num ? null : sc[0], hf.length > 1 ? hf[1] : null));
        }
        return new Line(f[0], f[1], List.copyOf(blocks), f[3].isBlank() ? List.of() : List.of(f[3].trim().split("\\s+")));
    }

    @javax.annotation.Nullable
    public static Line byPage(String page) {
        for (Line l : LINES) if (l.page().equals(page)) return l;
        return null;
    }

    public static final int MAX_COLS = 4;

    public static int[][] blockPos(Line l) {
        int[][] p = new int[l.blocks().size()][];
        int col = 0, row = 0, shelf = 0;
        for (int i = 0; i < p.length; i++) {
            Block b = l.blocks().get(i);
            int c = b.rows().stream().mapToInt(Integer::intValue).max().orElse(1), r = b.rows().size();
            if (col > 0 && col + c > MAX_COLS) {
                row += shelf + 1;
                col = 0;
                shelf = 0;
            }
            p[i] = new int[] {col, row, c, r};
            col += c;
            shelf = Math.max(shelf, r);
        }
        return p;
    }

    public static List<Node> nodes(Line l) {
        List<Node> out = new ArrayList<>();
        int[][] pos = blockPos(l);
        for (int i = 0; i < l.blocks().size(); i++) {
            Block b = l.blocks().get(i);
            for (int r = 0, k = 0; r < b.rows().size(); r++)
                for (int c = 0; c < b.rows().get(r); c++, k++) {
                    Node a = AchievementTree.NODES.get(AchievementTree.index(b.keys().get(k)));
                    out.add(new Node(a.key(), a.item(), a.page(), a.group(), pos[i][0] + c, pos[i][1] + r));
                }
        }
        return List.copyOf(out);
    }

    public static List<Edge> edges(Line l) {
        List<Edge> out = new ArrayList<>();
        int n = 0, exit = -1;
        for (int i = 0; i < l.blocks().size(); i++) {
            Block b = l.blocks().get(i);
            for (int r = 0, k = 0; r < b.rows().size(); k += b.rows().get(r), r++)
                for (int c = b.joins().contains(k) ? 0 : 1; c < b.rows().get(r); c++) out.add(new Edge(n + k + c - 1, n + k + c, false));
            int src = exit;
            if (b.from() != null)
                for (int j = 0, m = 0; j < i; m += l.blocks().get(j).keys().size(), j++)
                    if (l.blocks().get(j).keys().contains(b.from())) src = m + l.blocks().get(j).keys().indexOf(b.from());
            if (src >= 0) out.add(new Edge(src, n, false));
            exit = n + b.out();
            n += b.keys().size();
        }
        return List.copyOf(out);
    }
}
