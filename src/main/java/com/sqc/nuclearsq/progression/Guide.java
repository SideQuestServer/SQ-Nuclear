package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class Guide {
    private Guide() {}

    public record Step(String key, String page, List<String> need, List<String> show) {}

    public record Chapter(String id, List<Step> steps) {
        public String page() {
            return "guide_" + id;
        }
    }

    public static final String OVERVIEW = "guide_overview";

    private static Step machine(String id, String page) {
        return new Step(id, page, List.of("i:" + id + "_kit_sqc", "m:formed:" + id), List.of(id + "_kit_sqc"));
    }

    private static Step item(String key, String page, String... items) {
        List<String> need = new ArrayList<>();
        for (String i : items) need.add("i:" + i);
        return new Step(key, page, need, List.of(items));
    }

    private static Step circuit() {
        return item("circuit", "micro_assembler", "basic_circuit_sqc");
    }

    private static Step step(String key, String page, List<String> need, List<String> show) {
        return new Step(key, page, need, show);
    }

    private static Chapter chapter(String id, Step... steps) {
        return new Chapter(id, List.of(steps));
    }

    public static final List<Chapter> CHAPTERS = List.of(
            chapter("start",
                    item("small_kit", "small_parts_assembler", "small_parts_assembler_kit_sqc"),
                    item("coal_kit", "energy_start", "coal_generator_kit_sqc"),
                    step("coal_place", "energy_start", List.of("m:formed:coal_generator"), List.of("coal_generator_kit_sqc")),
                    step("small_place", "small_parts_assembler", List.of("m:formed:small_parts_assembler"), List.of("small_parts_assembler_kit_sqc")),
                    step("power_small", "energy_start", List.of("m:power:coal_generator"), List.of("cable_basic_sqc", "coal_generator_kit_sqc")),
                    step("terminal", "mach_terminals", List.of("m:terminal"), List.of()),
                    item("wrench", "mach_blueprint", "wrench_sqc"),
                    machine("kiln", "kiln")),
            chapter("first",
                    machine("micro_assembler", "micro_assembler"),
                    circuit(),
                    item("steel", "intro_materials", "steel_sheet_sqc"),
                    item("plate", "intro_materials", "reactor_plate_sqc"),
                    item("casing", "intro_materials", "reactor_casing_sqc"),
                    item("board", "intro_materials", "control_board_1_sqc"),
                    item("ports", "cool_plant", "machine_port_energy_sqc", "machine_port_item_sqc"),
                    machine("plate_mill", "plate_mill"),
                    machine("cnc_mill", "cnc_mill"),
                    machine("hammer_mill", "hammer_mill"),
                    item("reactor_kit", "reactor_assembler", "reactor_assembler_kit_sqc"),
                    step("reactor_place", "reactor_assembler", List.of("m:formed:reactor_assembler"), List.of("reactor_assembler_kit_sqc"))),
            chapter("materials",
                    item("zirc_dust", "intro_materials", "zirconium_dust_sqc"),
                    item("zirc_ingot", "intro_materials", "zirconium_ingot_sqc"),
                    item("zircaloy", "intro_materials", "zircaloy_sheet_sqc"),
                    machine("heavy_assembler", "mach_heavy"),
                    machine("blast_furnace", "blast_furnace")),
            chapter("chem",
                    machine("acid_plant", "rod_acid"),
                    machine("polymer_plant", "rod_polymer"),
                    machine("oil_derrick", "oil_derrick"),
                    machine("distillation_column", "distillation_column"),
                    machine("cracker", "cracker"),
                    machine("gas_generator", "gas_generator"),
                    machine("fluid_buffer", "fluid_buffer")),
            chapter("chips",
                    machine("foundry", "rod_foundry"),
                    machine("chemical_lab", "rod_chemlab"),
                    item("silicon", "chips_chain", "silicon_raw_sqc", "silicon_pure_sqc"),
                    item("wafer", "chips_chain", "silicon_wafer_sqc"),
                    item("chip_1", "chips_chain", "chip_1_sqc"),
                    item("arm", "robot_arm", "robot_arm_sqc"),
                    machine("solder_machine", "solder_machine"),
                    item("chip_2", "chips_chain", "chip_2_sqc"),
                    machine("clean_room", "clean_room"),
                    item("chip_3", "chips_chain", "chip_3_sqc")),
            chapter("storage",
                    item("cable", "data_net", "data_cable_sqc"),
                    machine("server_rack_small", "storage_racks"),
                    item("compute", "storage_compute", "cpu_module_1_sqc", "ram_module_512mb_sqc"),
                    item("drive", "storage_drives", "hard_drive_1_sqc"),
                    item("storage_terminal", "storage_terminal", "storage_terminal_sqc"),
                    item("storage_monitor", "storage_monitor", "storage_monitor_sqc")),
            chapter("control",
                    step("terminal_open", "data_net", List.of("i:data_terminal_sqc", "m:data_terminal"), List.of("data_terminal_sqc")),
                    step("terminal_net", "data_net", List.of("m:data_terminal_net"), List.of("data_cable_sqc", "data_terminal_sqc")),
                    item("scanner", "data_scanner", "net_scanner_sqc"),
                    step("console", "data_console", List.of("i:data_console_sqc", "m:console"), List.of("data_console_sqc")),
                    step("gauge", "data_instruments", List.of("i:data_gauge_sqc"), List.of("data_gauge_sqc", "data_bar_sqc", "data_display_sqc")),
                    step("controls", "data_instruments", List.of("i:data_button_sqc"), List.of("data_button_sqc", "data_lever_sqc", "data_slider_sqc")),
                    machine("control_desk", "data_desk"),
                    item("wall", "data_wall", "control_wall_sqc")),
            chapter("assemblers",
                    machine("component_assembler", "component_assembler"),
                    item("crafting", "storage_crafting", "crafting_terminal_sqc"),
                    machine("pattern_rack", "pattern_rack"),
                    machine("particle_assembler", "particle_assembler"),
                    machine("compute_assembler", "compute_assembler"),
                    machine("equipment_assembler", "equipment_assembler")),
            chapter("fuel",
                    machine("ore_mill", "rod_ore_mill"),
                    machine("enrichment_centrifuge", "rod_centrifuge"),
                    item("yellowcake", "rod_ore_mill", "yellowcake_sqc"),
                    item("enriched", "rod_centrifuge", "enriched_uranium_sqc"),
                    machine("leaching_plant", "rod_leaching"),
                    machine("uf6_converter", "rod_uf6"),
                    machine("pellet_factory", "rod_pellets"),
                    item("core", "rod_pellets", "uranium_core_sqc"),
                    item("rod_hull", "rod_build", "fuel_rod_block_sqc"),
                    item("rod_head", "rod_build", "fuel_rod_head_sqc")),
            chapter("safety",
                    item("geiger", "prot_geiger", "geiger_counter_sqc"),
                    item("dosimeter", "prot_dosimeter", "dosimeter_sqc"),
                    item("medicine", "prot_sickness", "anti_rad_medicine_sqc"),
                    item("suit", "prot_suit", "hazmat_1_helmet_sqc", "hazmat_1_chestplate_sqc", "hazmat_1_leggings_sqc", "hazmat_1_boots_sqc"),
                    item("mask", "prot_mask", "respirator_mask_sqc", "respirator_filter_sqc"),
                    item("shower", "prot_shower", "decon_shower_sqc"),
                    machine("decon_dock", "decon_dock")),
            chapter("reactor",
                    machine("control_rod_drive", "rod_drive"),
                    machine("steam_generator", "big_steam_generator"),
                    machine("main_coolant_pump", "cool_bigpumps"),
                    machine("pressurizer", "big_pressurizer"),
                    machine("emergency_cooling", "big_emergency"),
                    machine("relief_tank", "big_relief_tank"),
                    machine("high_pressure_injection", "big_hp_injection"),
                    item("pipes", "transport_pipes", "pipe_basic_sqc", "pipe_reinforced_sqc"),
                    machine("containment", "big_containment")),
            chapter("steam",
                    machine("steam_separator", "big_separator"),
                    machine("steam_dryer", "big_dryer"),
                    machine("turbine", "energy_turbine"),
                    step("shaft", "energy_shaft", List.of("i:shaft_generator_kit_sqc", "i:shaft_coupling_sqc", "m:formed:shaft_generator"),
                            List.of("shaft_generator_kit_sqc", "shaft_coupling_sqc")),
                    item("power_cable", "transport_cables", "cable_reinforced_sqc")),
            chapter("loop",
                    machine("condenser", "plant_condenser"),
                    machine("cooling_tower_large", "big_tower"),
                    machine("water_treatment", "plant_treatment"),
                    machine("feedwater_pump", "plant_fwpump")),
            chapter("run",
                    step("rods", "rod_drive", List.of("i:fuel_rod_block_sqc", "i:fuel_rod_head_sqc", "m:rods"), List.of("fuel_rod_block_sqc", "fuel_rod_head_sqc")),
                    step("heat", "reactor_core", List.of("m:reactor_power"), List.of()),
                    step("first_power", "energy_chain", List.of("m:power:turbine"), List.of("turbine_kit_sqc"))),
            chapter("distribute",
                    machine("battery_small", "transport_battery"),
                    item("mast", "transport_grid", "power_mast_sqc", "overhead_wire_sqc"),
                    machine("substation", "transport_substation")),
            chapter("cycle",
                    machine("spent_fuel_rack", "rod_spent"),
                    machine("reprocessing_plant", "reprocessing_plant"),
                    item("castor", "rod_castor", "castor_large_sqc"),
                    machine("waste_repository", "rod_repository"),
                    machine("fast_breeder", "fast_breeder")),
            chapter("fusion",
                    machine("deuterium_separator", "fusion_deuterium"),
                    item("pellet", "fusion_fuel", "lithium_pellet_sqc"),
                    machine("fusion_reactor", "fusion_reactor"),
                    step("fusion_power", "fusion_reactor", List.of("m:power:fusion_reactor"), List.of("fusion_reactor_kit_sqc"))));

    public static final int MAIN = 14;

    public static List<Step> all() {
        List<Step> out = new ArrayList<>();
        for (Chapter c : CHAPTERS) out.addAll(c.steps());
        return out;
    }

    @javax.annotation.Nullable
    public static Chapter chapter(String id) {
        for (Chapter c : CHAPTERS) if (c.id().equals(id)) return c;
        return null;
    }

    public static boolean met(String need, Predicate<String> item, Predicate<String> milestone) {
        if (need.startsWith("i:")) return item.test(need.substring(2));
        return milestone.test(need.substring(2));
    }

    public static boolean done(Step s, Predicate<String> item, Predicate<String> milestone) {
        for (String n : s.need()) if (!met(n, item, milestone)) return false;
        return true;
    }

    public static int next(Chapter c, Predicate<String> item, Predicate<String> milestone) {
        for (int i = 0; i < c.steps().size(); i++) if (!done(c.steps().get(i), item, milestone)) return i;
        return -1;
    }

    public static int count(Chapter c, Predicate<String> item, Predicate<String> milestone) {
        int n = 0;
        for (Step s : c.steps()) if (done(s, item, milestone)) n++;
        return n;
    }
}
