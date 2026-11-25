package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.progression.PlantOverview.Group;
import com.sqc.nuclearsq.progression.PlantOverview.Node;

public final class AchievementTree {
    public static final List<Node> NODES = build(
            n("coal", "coal_generator_controller_sqc", "energy_start", Group.START),
            n("small_parts", "small_parts_assembler_controller_sqc", "small_parts_assembler", Group.ASSEMBLY),
            n("micro", "micro_assembler_controller_sqc", "micro_assembler", Group.ASSEMBLY),
            n("assembler", PlantOverview.ASSEMBLER_ITEM, "reactor_assembler", Group.ASSEMBLY),
            n("plate_mill", "plate_mill_controller_sqc", "plate_mill", Group.ASSEMBLY),
            n("cnc_mill", "cnc_mill_controller_sqc", "cnc_mill", Group.ASSEMBLY),
            n("hammer_mill", "hammer_mill_controller_sqc", "hammer_mill", Group.ASSEMBLY),
            n("kiln", "kiln_controller_sqc", "kiln", Group.ASSEMBLY),
            n("solar_panel", "solar_panel_sqc", "energy_solar", Group.START),
            n("solar_collector", "solar_collector_sqc", "energy_solar", Group.START),
            n("heavy", "heavy_assembler_controller_sqc", "mach_heavy", Group.ASSEMBLY),
            n("blast_furnace", "blast_furnace_controller_sqc", "blast_furnace", Group.ASSEMBLY),
            n("solar", "solar_field_controller_sqc", "energy_solar", Group.START),
            n("wind_small", "wind_turbine_small_controller_sqc", "energy_wind", Group.POWER),
            n("wind_medium", "wind_turbine_medium_controller_sqc", "energy_wind", Group.POWER),
            n("wind_large", "wind_turbine_large_controller_sqc", "energy_wind", Group.POWER),
            n("rack_small", "server_rack_small_controller_sqc", "storage_racks", Group.STORAGE),
            n("rack_normal", "server_rack_controller_sqc", "storage_racks", Group.STORAGE),
            n("rack_large", "server_rack_large_controller_sqc", "storage_racks", Group.STORAGE),
            n("storage_terminal", "storage_terminal_sqc", "storage_terminal", Group.STORAGE),
            n("storage_monitor", "storage_monitor_sqc", "storage_monitor", Group.STORAGE),
            n("crafting_terminal", "crafting_terminal_sqc", "storage_crafting", Group.STORAGE),
            n("wifi_adapter", "wifi_adapter_sqc", "storage_wifi", Group.STORAGE),
            n("wifi_terminal", "wifi_terminal_sqc", "storage_wifi", Group.STORAGE),
            n("storage_crate", "storage_crate_sqc", "storage_crate", Group.STORAGE),
            n("pipe_adapter", "net_pipe_adapter_sqc", "storage_pipe_adapter", Group.STORAGE),
            n("bank_server", "bank_server_sqc", "bank_server", Group.STORAGE),
            n("bank_terminal", "bank_terminal_sqc", "bank_terminal", Group.STORAGE),
            n("bank_modem", "bank_modem_sqc", "bank_modem", Group.STORAGE),
            n("exchange", "bank_terminal_sqc", "bank_exchange", Group.STORAGE),
            n("acid", "acid_plant_controller_sqc", "rod_acid", Group.FUEL),
            n("polymer", "polymer_plant_controller_sqc", "rod_polymer", Group.ASSEMBLY),
            n("oil_derrick", "oil_derrick_controller_sqc", "oil_derrick", Group.FUEL),
            n("distillation_column", "distillation_column_controller_sqc", "distillation_column", Group.FUEL),
            n("cracker", "cracker_controller_sqc", "cracker", Group.FUEL),
            n("gas_generator", "gas_generator_controller_sqc", "gas_generator", Group.POWER),
            n("fluid_buffer", "fluid_buffer_controller_sqc", "fluid_buffer", Group.FUEL),
            n("foundry", "foundry_controller_sqc", "rod_foundry", Group.ASSEMBLY),
            n("chemlab", "chemical_lab_controller_sqc", "rod_chemlab", Group.CYCLE),
            n("robot_arm", "robot_arm_sqc", "robot_arm", Group.ASSEMBLY),
            n("arm_upgrade", "robot_arm_upgrade_sqc", "robot_arm", Group.ASSEMBLY),
            n("solder_machine", "solder_machine_controller_sqc", "solder_machine", Group.ASSEMBLY),
            n("clean_room", "clean_room_controller_sqc", "clean_room", Group.ASSEMBLY),
            n("component", "component_assembler_controller_sqc", "component_assembler", Group.ASSEMBLY),
            n("rack_pattern", "pattern_rack_controller_sqc", "pattern_rack", Group.STORAGE),
            n("particle", "particle_assembler_controller_sqc", "particle_assembler", Group.ASSEMBLY),
            n("compute_asm", "compute_assembler_controller_sqc", "compute_assembler", Group.ASSEMBLY),
            n("equipment", "equipment_assembler_controller_sqc", "equipment_assembler", Group.ASSEMBLY),
            n("compute_cluster", "compute_cluster_controller_sqc", "storage_cluster", Group.STORAGE),
            n("heavy_arm", "heavy_robot_arm_sqc", "robot_arm", Group.ASSEMBLY),
            n("quantum_chest", "future_chest_sqc", "storage_crate", Group.STORAGE),
            n("ore", "ore_mill_controller_sqc", "rod_ore_mill", Group.FUEL),
            n("leaching", "leaching_plant_controller_sqc", "rod_leaching", Group.FUEL),
            n("uf6", "uf6_converter_controller_sqc", "rod_uf6", Group.FUEL),
            n("centrifuge", "enrichment_centrifuge_controller_sqc", "rod_centrifuge", Group.FUEL),
            n("pellets", "pellet_factory_controller_sqc", "rod_pellets", Group.FUEL),
            n("rod", "fuel_rod_head_sqc", "rod_build", Group.FUEL),
            n("rtg", "rtg_sqc", "energy_rtg", Group.START),
            n("containment", "containment_controller_sqc", "big_containment", Group.SAFETY),
            n("drive", "control_rod_drive_controller_sqc", "rod_drive", Group.CORE),
            n("desk", "control_desk_controller_sqc", "data_desk", Group.SAFETY),
            n("image_monitor", "image_monitor_sqc", "data_monitor", Group.SAFETY),
            n("multi_display", "data_multi_sqc", "data_instruments", Group.SAFETY),
            n("pumps", "high_pressure_pump_controller_sqc", "cool_hppump", Group.CORE),
            n("main_pump", "main_coolant_pump_controller_sqc", "cool_bigpumps", Group.CORE),
            n("pressurizer", "pressurizer_controller_sqc", "big_pressurizer", Group.CORE),
            n("steam_generator", "steam_generator_controller_sqc", "big_steam_generator", Group.CORE),
            n("emergency", "emergency_cooling_controller_sqc", "big_emergency", Group.SAFETY),
            n("relief_tank", "relief_tank_controller_sqc", "big_relief_tank", Group.SAFETY),
            n("hp_injection", "high_pressure_injection_controller_sqc", "big_hp_injection", Group.SAFETY),
            n("boric", "boric_acid_plant_controller_sqc", "rod_boric", Group.CORE),
            n("exchanger", "ion_exchanger_controller_sqc", "rod_exchanger", Group.CORE),
            n("heavywater", "heavy_water_plant_controller_sqc", "rod_heavywater", Group.CORE),
            n("rod_gripper", "rod_gripper_sqc", "rod_gripper", Group.CORE),
            n("xenon_filter", "xenon_filter_sqc", "reactor_xenon", Group.SAFETY),
            n("recombiner", "recombiner_sqc", "reactor_xenon", Group.SAFETY),
            n("door", "door_control_sqc", "prot_doors", Group.SAFETY),
            n("airlock", "airlock_control_sqc", "prot_airlock", Group.SAFETY),
            n("separator", "steam_separator_controller_sqc", "big_separator", Group.STEAM),
            n("dryer", "steam_dryer_controller_sqc", "big_dryer", Group.STEAM),
            n("purifier", "steam_purifier_controller_sqc", "plant_purifier", Group.STEAM),
            n("reheater", "reheater_controller_sqc", "eff_reheater", Group.STEAM),
            n("turbine", "turbine_controller_sqc", "energy_turbine", Group.STEAM),
            n("shaft", "shaft_generator_controller_sqc", "energy_shaft", Group.POWER),
            n("substation", "substation_controller_sqc", "transport_substation", Group.POWER),
            n("mast", "power_mast_sqc", "transport_grid", Group.POWER),
            n("mast_large", "power_mast_large_sqc", "transport_grid", Group.POWER),
            n("battery", "battery_small_controller_sqc", "transport_battery", Group.POWER),
            n("battery_medium", "battery_medium_controller_sqc", "transport_battery", Group.POWER),
            n("battery_large", "battery_large_controller_sqc", "transport_battery", Group.POWER),
            n("condenser", "condenser_controller_sqc", "plant_condenser", Group.STEAM),
            n("tower", "cooling_tower_large_controller_sqc", "big_tower", Group.CORE),
            n("tower_xl", "cooling_tower_xl_controller_sqc", "big_tower", Group.CORE),
            n("intake_pump", "intake_pump_controller_sqc", "plant_intake", Group.STEAM),
            n("treatment", "water_treatment_controller_sqc", "plant_treatment", Group.STEAM),
            n("preheater", "feedwater_heater_controller_sqc", "eff_preheater", Group.STEAM),
            n("fwpump", "feedwater_pump_controller_sqc", "plant_fwpump", Group.STEAM),
            n("rack", "spent_fuel_rack_controller_sqc", "rod_spent", Group.CYCLE),
            n("shower", "decon_shower_sqc", "prot_shower", Group.CYCLE),
            n("castor", "castor_large_sqc", "rod_castor", Group.CYCLE),
            n("reprocessing", "reprocessing_plant_controller_sqc", "reprocessing_plant", Group.CYCLE),
            n("slag_crusher", "slag_crusher_controller_sqc", "rod_slag", Group.CYCLE),
            n("vitrification", "vitrification_plant_controller_sqc", "rod_vitrify", Group.CYCLE),
            n("repository", "waste_repository_controller_sqc", "rod_repository", Group.CYCLE),
            n("breeder", "fast_breeder_controller_sqc", "fast_breeder", Group.CYCLE),
            n("weapons", "weapons_processing_controller_sqc", "weapons_processing", Group.CYCLE),
            n("decon_dock", "decon_dock_controller_sqc", "decon_dock", Group.ASSEMBLY),
            n("decon_robot", "decon_robot_sqc", "decon_dock", Group.ASSEMBLY),
            n("electrolyzer", "electrolyzer_controller_sqc", "chem_electrolysis", Group.POWER),
            n("fuel_cell", "fuel_cell_controller_sqc", "chem_fuel_cell", Group.POWER),
            n("deuterium_separator", "deuterium_separator_controller_sqc", "fusion_deuterium", Group.POWER),
            n("fusion", "fusion_reactor_controller_sqc", "fusion_reactor", Group.POWER),
            n("superconductor", "cable_superconductor_sqc", "transport_cables", Group.POWER),
            n("quantum", "quantum_computer_controller_sqc", "quantum_computer", Group.ASSEMBLY),
            n("exo_station", "exo_station_controller_sqc", "exo_station", Group.ASSEMBLY),
            n("exo", "exo_chestplate_sqc", "exo", Group.ASSEMBLY),
            n("exo_core", "exo_core_sqc", "exo", Group.ASSEMBLY),
            n("exo_module", "exo_module_speed_1_sqc", "exo_modules", Group.ASSEMBLY),
            n("nano", "nano_swarm_canister_sqc", "tools_nanobots", Group.ASSEMBLY),
            n("nano_upgrade", "nano_upgrade_speed_1_sqc", "tools_nanobots", Group.ASSEMBLY),
            n("robo_dog", "robo_dog_kit_sqc", "robo_pets", Group.ASSEMBLY),
            n("robo_cat", "robo_cat_kit_sqc", "robo_pets", Group.ASSEMBLY),
            n("robo_ball", "robo_ball_sqc", "robo_pets", Group.ASSEMBLY),
            n("feather_wand", "robo_feather_wand_sqc", "robo_pets", Group.ASSEMBLY),
            n("survey_drone", "survey_drone_sqc", "survey_drone", Group.ASSEMBLY));

    public record Stage(String key, String first) {}

    public static final List<Stage> STAGES = List.of(
            new Stage("start", "coal"),
            new Stage("storage", "rack_small"),
            new Stage("chem", "acid"),
            new Stage("chips", "foundry"),
            new Stage("fuel", "ore"),
            new Stage("reactor", "containment"),
            new Stage("steam", "separator"),
            new Stage("loop", "condenser"),
            new Stage("cycle", "rack"),
            new Stage("beyond", "electrolyzer"));

    @javax.annotation.Nullable
    public static Stage stageAt(int k) {
        String key = NODES.get(k).key();
        for (Stage s : STAGES) if (s.first().equals(key)) return s;
        return null;
    }

    @javax.annotation.Nullable
    public static RecipeSources.Where unlocks(String key) {
        return switch (key) {
            case "small_parts" -> RecipeSources.Where.SMALL_PARTS_ASSEMBLER;
            case "assembler" -> RecipeSources.Where.REACTOR_ASSEMBLER;
            case "equipment" -> RecipeSources.Where.EQUIPMENT_ASSEMBLER;
            case "plate_mill" -> RecipeSources.Where.PLATE_MILLING;
            case "cnc_mill" -> RecipeSources.Where.CNC_MILL;
            case "solder_machine" -> RecipeSources.Where.SOLDER_MACHINE;
            case "hammer_mill" -> RecipeSources.Where.DUST_MILLING;
            case "kiln" -> RecipeSources.Where.KILN_FIRING;
            case "blast_furnace" -> RecipeSources.Where.BLAST_SMELTING;
            case "heavy" -> RecipeSources.Where.HEAVY_ASSEMBLER;
            case "ore" -> RecipeSources.Where.ORE_MILLING;
            case "acid" -> RecipeSources.Where.ACID_PLANT;
            case "boric" -> RecipeSources.Where.BORIC_ACID;
            case "exchanger" -> RecipeSources.Where.ION_EXCHANGE;
            case "heavywater" -> RecipeSources.Where.HEAVY_WATER;
            case "polymer" -> RecipeSources.Where.POLYMER;
            case "vitrification" -> RecipeSources.Where.VITRIFICATION;
            case "chemlab" -> RecipeSources.Where.CHEMISTRY;
            case "leaching" -> RecipeSources.Where.LEACHING;
            case "uf6" -> RecipeSources.Where.UF6_CONVERSION;
            case "foundry" -> RecipeSources.Where.FOUNDRY;
            case "pellets" -> RecipeSources.Where.PELLETING;
            case "slag_crusher" -> RecipeSources.Where.SLAG_CRUSHING;
            case "centrifuge" -> RecipeSources.Where.ENRICHMENT;
            case "reprocessing" -> RecipeSources.Where.REPROCESSING;
            default -> null;
        };
    }

    private AchievementTree() {}

    private record Entry(String key, String item, String page, Group group) {}

    private static Entry n(String key, String item, String page, Group group) {
        return new Entry(key, item, page, group);
    }

    private static List<Node> build(Entry... entries) {
        List<Node> out = new ArrayList<>();
        for (Entry e : entries) out.add(new Node(e.key(), e.item(), e.page(), e.group(), out.size(), 0));
        return List.copyOf(out);
    }

    public static List<String> proofItems(Node n) {
        if (n.item().endsWith("_controller_sqc")) return List.of(n.item(), n.item().replace("_controller_sqc", "_kit_sqc"));
        return List.of(n.item());
    }

    public static int index(String key) {
        for (int i = 0; i < NODES.size(); i++) if (NODES.get(i).key().equals(key)) return i;
        throw new IllegalArgumentException("Kein Knoten " + key);
    }
}
