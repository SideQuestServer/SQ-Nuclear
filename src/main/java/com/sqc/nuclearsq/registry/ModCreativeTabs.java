package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.progression.ProgressionBookItem;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final DeferredItem<Item> SPACER = ITEMS.registerSimpleItem("spacer_sqc");
    private static final DeferredItem<Item> H_MANUFACTURING = ITEMS.registerSimpleItem("header_manufacturing_sqc");
    private static final DeferredItem<Item> H_CHEMISTRY = ITEMS.registerSimpleItem("header_chemistry_sqc");
    private static final DeferredItem<Item> H_ROBOTICS = ITEMS.registerSimpleItem("header_robotics_sqc");
    private static final DeferredItem<Item> H_COMPUTING = ITEMS.registerSimpleItem("header_computing_sqc");
    private static final DeferredItem<Item> H_ENERGY = ITEMS.registerSimpleItem("header_energy_sqc");
    private static final DeferredItem<Item> H_REACTOR = ITEMS.registerSimpleItem("header_reactor_sqc");
    private static final DeferredItem<Item> H_PROTECTION = ITEMS.registerSimpleItem("header_protection_sqc");
    private static final DeferredItem<Item> H_WEAPONS = ITEMS.registerSimpleItem("header_weapons_sqc");

    public static final DeferredItem<Item> PROGRESSION_BOOK = ITEMS.register("progression_book_sqc",
            () -> new ProgressionBookItem(new Item.Properties().stacksTo(1)));

    private static int count;
    private static int spacerId;

    private static void pad(CreativeModeTab.Output out, String group) {
        ItemStack st = new ItemStack(SPACER.get());
        final int n = spacerId++;
        CustomData.update(DataComponents.CUSTOM_DATA, st, tag -> {
            tag.putInt("n", n);
            if (group != null) tag.putString("g", group);
        });
        out.accept(st, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
    }

    private static ItemLike b(String id) { return CatalogRegistry.BLOCK_ITEM_MAP.get(id).get(); }
    private static ItemLike i(String id) { return CatalogRegistry.ITEM_MAP.get(id).get(); }
    private static ItemLike kit(String id) { return com.sqc.nuclearsq.kit.KitRegistry.KITS.get(id).get(); }

    private static void section(CreativeModeTab.Output out, DeferredItem<Item> header, ItemLike... items) {
        while (count % 9 != 0) { pad(out, null); count++; }
        String group = header.getId().getPath().replace("header_", "").replace("_sqc", "");
        out.accept(header.get(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY); count++;
        while (count % 9 != 0) { pad(out, group); count++; }
        for (ItemLike it : items) { out.accept(it); count++; }
    }

    private static void row(CreativeModeTab.Output out) {
        while (count % 9 != 0) { pad(out, null); count++; }
    }

    private static void groups(CreativeModeTab.Output out, DeferredItem<Item> header, java.util.List<java.util.List<ItemLike>> groups) {
        section(out, header);
        for (java.util.List<ItemLike> g : groups) {
            row(out);
            for (ItemLike it : g) { out.accept(it); count++; }
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.nuclearsq.main"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()))
            .displayItems((params, out) -> {
                count = 0;
                spacerId = 0;
                java.util.List<ItemLike> assembly = new java.util.ArrayList<>(java.util.List.of(PROGRESSION_BOOK.get(),
                        com.sqc.nuclearsq.transport.TransportRegistry.WRENCH.get(),
                        kit("small_parts_assembler"), AssemblyRegistry.ASSEMBLER_ITEM.get(), kit("equipment_assembler"), kit("heavy_assembler"),
                        kit("component_assembler"), kit("particle_assembler")));
                assembly.addAll(com.sqc.nuclearsq.particle.ParticleRegistry.UPGRADES.values().stream().map(d -> (ItemLike) d.get()).toList());
                assembly.addAll(com.sqc.nuclearsq.particle.ParticleRegistry.CARDS.stream().map(d -> (ItemLike) d.get()).toList());
                java.util.List<ItemLike> metal = new java.util.ArrayList<>(java.util.List.of(kit("ore_mill"), kit("hammer_mill"), kit("plate_mill"), kit("cnc_mill"), kit("kiln"), kit("blast_furnace"), kit("foundry")));
                metal.addAll(ResourceRegistry.tabItems());
                metal.addAll(FusionRegistry.tabItems());
                metal.addAll(java.util.List.of(ReactorRegistry.IRON_DUST.get(), ReactorRegistry.GOLD_DUST.get(), ReactorRegistry.COPPER_DUST.get(),
                        i("zirconium_dust_sqc"), i("zirconium_ingot_sqc"), i("coke_sqc")));
                java.util.List<ItemLike> plant = new java.util.ArrayList<>();
                plant.add(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get());
                for (com.sqc.nuclearsq.machine.big.BigMachine m : com.sqc.nuclearsq.machine.big.BigMachine.values()) {
                    if (m == com.sqc.nuclearsq.machine.big.BigMachine.REACTOR_ASSEMBLER || m == com.sqc.nuclearsq.machine.big.BigMachine.COAL_GENERATOR
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.SMALL_PARTS_ASSEMBLER
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.MICRO_ASSEMBLER
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.EQUIPMENT_ASSEMBLER
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.EXO_STATION) continue;
                    plant.add(BigMachineRegistry.CONTROLLER_ITEMS.get(m).get());
                }
                BigMachineRegistry.PORT_ITEMS.values().forEach(it -> plant.add(it.get()));
                java.util.List<ItemLike> chest = new java.util.ArrayList<>();
                chest.add(com.sqc.nuclearsq.futurechest.FutureChestRegistry.ITEM.get());
                com.sqc.nuclearsq.futurechest.FutureChestRegistry.UPGRADE_ITEMS.values().forEach(d -> chest.add(d.get()));
                chest.addAll(java.util.List.of(TestSourceRegistry.ENERGY_SOURCE_ITEM.get(), TestSourceRegistry.FLUID_SOURCE_ITEM.get()));
                groups(out, H_MANUFACTURING, java.util.List.of(assembly, metal,
                        com.sqc.nuclearsq.module.ModuleRegistry.ITEM.values().stream().map(d -> (ItemLike) d.get()).toList(),
                        java.util.List.of(i("zircaloy_sheet_sqc"), i("steel_sheet_sqc"), i("iron_plate_sqc"), i("copper_plate_sqc"), i("gold_plate_sqc"), i("glass_plate_sqc"), i("reactor_plate_sqc"), i("advanced_reactor_casing_sqc"),
                                i("lcp_module_casing_sqc"), i("pbt_module_casing_sqc"), i("hdpe_module_casing_sqc"), i("polycarbonate_module_casing_sqc"),
                                i("osmium_heat_plate_sqc"), i("obsidian_composite_plate_sqc"), i("atomic_hull_plate_sqc"),
                                i("glass_seal_sqc"), i("hdpe_seal_sqc"), i("lead_shielding_sqc")),
                        java.util.List.of(i("uranium_core_sqc"), i("fluorite_cooling_core_sqc"), i("plutonium_core_sqc"),
                                i("mox_core_sqc"), i("carbon_filter_sqc"), i("boric_acid_sqc"), i("ion_resin_sqc"), i("spent_resin_sqc"), ReactorRegistry.RECOMBINER_ITEM.get(), ReactorRegistry.XENON_FILTER_ITEM.get(),
                                i("control_board_1_sqc"), i("control_board_2_sqc"), i("control_board_3_sqc")),
                        java.util.List.of(i("copper_coil_sqc"), i("andesite_housing_sqc"), i("brass_bearing_sqc"), i("glowstone_conductor_sqc")),
                        java.util.List.of(i("superconducting_coil_sqc"), i("first_wall_tile_sqc"), i("worn_wall_tile_sqc"), i("lithium_pellet_sqc"),
                                i("plasma_optimizer_sqc")),
                        java.util.List.of(b("vent_grille_sqc"), b("heat_exchanger_sqc"), b("active_cooler_sqc"),
                                b("translator_basic_sqc"), b("translator_brass_sqc"), b("translator_composite_sqc"),
                                b("moderator_coal_sqc"), b("moderator_carbon_sqc"), b("moderator_obsidian_sqc"),
                                b("pump_basic_sqc"), b("pump_electric_sqc"), b("pump_high_pressure_sqc"), b("pump_sodium_sqc"),
                                b("pressure_chamber_sqc"), b("pressure_chamber_reinforced_sqc"), b("pressure_chamber_hardened_sqc"),
                                ReactorRegistry.CASING_ITEM.get()),
                        java.util.List.of(b("steam_purifier_sqc"), b("turbine_basic_sqc"), b("turbine_brass_rotor_sqc"), b("turbine_steel_blades_sqc"),
                                b("condenser_sqc"), b("cooling_tower_segment_sqc"), b("cooling_tower_plated_sqc"), b("cooling_tower_hardened_sqc"),
                                b("water_treatment_sqc"), b("feedwater_pump_sqc"), b("water_inlet_sqc")),
                        plant, chest));
                groups(out, H_CHEMISTRY, java.util.List.of(
                        java.util.List.of(kit("acid_plant"), ModFluids.SULFURIC_ACID.bucket.get(), kit("leaching_plant"), ReactorRegistry.URANIUM_FRAGMENT.get(), ReactorRegistry.URANIUM_SLUDGE.get(),
                                ReactorRegistry.YELLOWCAKE.get(), kit("uf6_converter"), ReactorRegistry.UF6_CYLINDER.get(), kit("enrichment_centrifuge"), ReactorRegistry.ENRICHED_URANIUM.get(),
                                i("depleted_uranium_sqc"), ReactorRegistry.URANIUM_OXIDE_DUST.get(), ReactorRegistry.URANIUM_OXIDE.get(), kit("pellet_factory"), ReactorRegistry.URANIUM_PELLET.get(),
                                ReactorRegistry.FUEL_ROD.get()),
                        java.util.List.of(kit("boric_acid_plant"), kit("ion_exchanger"), kit("heavy_water_plant"), i("heavy_water_cell_sqc"), kit("electrolyzer"), kit("fuel_cell"),
                                i("glass_canister_sqc"), i("lubricant_sqc")),
                        java.util.List.of(kit("reprocessing_plant"), kit("slag_crusher"), kit("vitrification_plant")),
                        java.util.List.of(com.sqc.nuclearsq.oil.OilRegistry.WELL_ITEM.get(), kit("oil_derrick"), kit("distillation_column"), kit("cracker"), kit("polymer_plant"),
                                i("rubber_sqc"), i("rubber_seal_sqc"), i("photoresist_sqc"), i("lcp_granulate_sqc"), i("pbt_granulate_sqc"), i("polycarbonate_granulate_sqc"), kit("gas_generator"), kit("fluid_buffer"))));
                java.util.List<ItemLike> robots = new java.util.ArrayList<>(java.util.List.of(com.sqc.nuclearsq.robotics.RoboticsRegistry.ARM_ITEM.get(),
                        com.sqc.nuclearsq.robotics.RoboticsRegistry.HEAVY_ARM_ITEM.get(), com.sqc.nuclearsq.robotics.RoboticsRegistry.ROD_GRIPPER_ITEM.get(), com.sqc.nuclearsq.robotics.RoboticsRegistry.LINK_TOOL.get(),
                        com.sqc.nuclearsq.robotics.RoboticsRegistry.ARM_UPGRADE.get(), com.sqc.nuclearsq.nanobot.NanobotRegistry.CANISTER.get()));
                com.sqc.nuclearsq.nanobot.NanobotRegistry.POCKET_BATTERIES.forEach(d -> robots.add(d.get()));
                robots.add(com.sqc.nuclearsq.nanobot.NanobotRegistry.DRONE_ITEM_PORT_ITEM.get());
                robots.addAll(com.sqc.nuclearsq.nanobot.NanobotRegistry.UPGRADE_ITEMS.values().stream().map(d -> (ItemLike) d.get()).toList());
                robots.addAll(java.util.List.of(com.sqc.nuclearsq.robotics.decon.DeconRegistry.ROBOT_ITEM.get(), com.sqc.nuclearsq.robotics.decon.DeconRegistry.REMOTE.get(), kit("decon_dock"),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.DOG_KIT.get(), com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.CAT_KIT.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.BALL.get(), com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.WAND.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.COLLAR_SNIFFER.get(), com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.COLLAR_TIDY.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.COLLAR_LAMP.get(),
                        com.sqc.nuclearsq.robotics.drone.DroneRegistry.DRONE_ITEM.get(), com.sqc.nuclearsq.robotics.drone.DroneRegistry.TABLET.get()));
                java.util.List<ItemLike> exo = new java.util.ArrayList<>(java.util.List.of(kit("exo_station")));
                exo.addAll(com.sqc.nuclearsq.exo.ExoRegistry.tabItems());
                groups(out, H_ROBOTICS, java.util.List.of(robots, exo));
                java.util.List<ItemLike> data = new java.util.ArrayList<>();
                com.sqc.nuclearsq.data.DataRegistry.blocks().stream()
                        .filter(d -> d != com.sqc.nuclearsq.data.DataRegistry.AREA_MONITOR && d != com.sqc.nuclearsq.data.DataRegistry.AREA_SENSOR)
                        .forEach(d -> data.add(d.get()));
                data.addAll(java.util.List.of(kit("control_desk"), CatalogRegistry.NET_SCANNER.get(), com.sqc.nuclearsq.monitor.ImageMonitorRegistry.MONITOR.get()));
                java.util.List<ItemLike> storage = new java.util.ArrayList<>(java.util.List.of(kit("server_rack_small"), kit("server_rack"), kit("server_rack_large"),
                        kit("compute_cluster")));
                com.sqc.nuclearsq.storage.StorageRegistry.drives().forEach(d -> storage.add(d.get()));
                for (var cpu : com.sqc.nuclearsq.storage.StorageRegistry.CPU_MODULES) storage.add(cpu.get());
                for (var ram : com.sqc.nuclearsq.storage.StorageRegistry.RAM_MODULES) storage.add(ram.get());
                storage.addAll(java.util.List.of(com.sqc.nuclearsq.storage.StorageRegistry.QUANTUM_MODULE.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.TERMINAL.get(), com.sqc.nuclearsq.storage.StorageRegistry.MONITOR.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.CRAFTING_TERMINAL.get(), com.sqc.nuclearsq.storage.StorageRegistry.ITEM_PORT.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.NET_PIPE_ADAPTER.get(),
                        com.sqc.nuclearsq.crate.CrateRegistry.ITEM.get(), com.sqc.nuclearsq.crate.CrateRegistry.UPGRADE_1.get(), com.sqc.nuclearsq.crate.CrateRegistry.UPGRADE_2.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ADAPTER.get(), com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ANTENNA.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ANTENNA_SECTOR.get(), com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ANTENNA_DISH.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ANTENNA_QUANTUM.get(),
                        com.sqc.nuclearsq.storage.StorageRegistry.WIFI_TERMINAL.get(), com.sqc.nuclearsq.storage.StorageRegistry.ASSEMBLER_TEMPO.get()));
                groups(out, H_COMPUTING, java.util.List.of(
                        java.util.List.of(kit("chemical_lab"), i("silicon_raw_sqc"), i("silicon_pure_sqc"), i("silicon_wafer_sqc"), i("chip_1_sqc"), i("chip_2_sqc"), i("chip_3_sqc"),
                                kit("micro_assembler"), kit("solder_machine"), kit("clean_room"), kit("pattern_rack"), com.sqc.nuclearsq.storage.StorageRegistry.PATTERN_CARD.get(),
                                kit("compute_assembler"), kit("quantum_computer")),
                        data, storage,
                        java.util.List.of(com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get(), com.sqc.nuclearsq.economy.EconomyRegistry.SERVER.get(),
                                com.sqc.nuclearsq.economy.EconomyRegistry.MODEM.get(), com.sqc.nuclearsq.apps.AppRegistry.LOGIN_MONITOR.get(),
                                com.sqc.nuclearsq.apps.AppRegistry.AUCTION_MONITOR.get())));
                java.util.List<ItemLike> grid = new java.util.ArrayList<>();
                com.sqc.nuclearsq.transport.TransportRegistry.BLOCK_ITEMS.forEach(d -> grid.add(d.get()));
                grid.addAll(java.util.List.of(kit("battery_small"), kit("battery_medium"), kit("battery_large")));
                grid.addAll(com.sqc.nuclearsq.transport.GridRegistry.tabItems());
                grid.add(kit("substation"));
                var pgTerminal = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "powergrid_terminal_sqc"));
                if (pgTerminal != net.minecraft.world.item.Items.AIR) grid.add(pgTerminal);
                groups(out, H_ENERGY, java.util.List.of(
                        java.util.List.of(kit("coal_generator"), PowerRegistry.COAL_CARDS.get(0).get(), PowerRegistry.COAL_CARDS.get(1).get(),
                                PowerRegistry.COAL_CARDS.get(2).get(), PowerRegistry.SOLAR_PANEL_ITEM.get(), PowerRegistry.SOLAR_COLLECTOR_ITEM.get(), kit("solar_field"),
                                kit("wind_turbine_small"), kit("wind_turbine_medium"), kit("wind_turbine_large"),
                                PowerRegistry.RTG_ITEM.get(), PowerRegistry.ISOTOPE_CAPSULE.get(), PowerRegistry.PLUTONIUM_CAPSULE.get(), PowerRegistry.STRONTIUM_CAPSULE.get()),
                        grid));
                java.util.List<ItemLike> loop = new java.util.ArrayList<>(java.util.List.of(
                        kit("control_rod_drive"), kit("steam_generator"), kit("pressurizer"),
                        kit("steam_purifier"), kit("steam_separator"), kit("steam_dryer"), kit("reheater"),
                        kit("turbine"), MultiblockRegistry.SHAFT_COUPLING_ITEM.get()));
                var createOut = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "create_shaft_output_sqc"));
                if (createOut != net.minecraft.world.item.Items.AIR) loop.add(createOut);
                loop.addAll(java.util.List.of(kit("shaft_generator"),
                        kit("condenser"), kit("cooling_tower_large"), kit("cooling_tower_xl"),
                        kit("intake_pump"), com.sqc.nuclearsq.transport.TransportRegistry.BIG_PIPE_ITEM.get(),
                        kit("water_treatment"), i("filter_residue_sqc"), kit("feedwater_heater"),
                        kit("feedwater_pump"), kit("main_coolant_pump"), kit("high_pressure_pump"),
                        kit("containment"), kit("relief_tank"), kit("emergency_cooling"), kit("high_pressure_injection"),
                        ModFluids.COOLANT.bucket.get(), ModFluids.SODIUM_COOLANT.bucket.get(), ModFluids.CRYO_COOLANT.bucket.get(), ModFluids.HOT_COOLANT.bucket.get(),
                        ModFluids.FEEDWATER.bucket.get(), ModFluids.PREHEATED_FEEDWATER.bucket.get()));
                groups(out, H_REACTOR, java.util.List.of(
                        java.util.List.of(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_ITEM.get(),
                                ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_REINFORCED_ITEM.get(),
                                ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_HARDENED_ITEM.get(),
                                ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL_ITEM.get()),
                        loop,
                        java.util.List.of(kit("spent_fuel_rack"), SpentFuelRegistry.SPENT_FUEL.get(), SpentFuelRegistry.LARGE_CASTOR_ITEM.get(),
                                kit("fast_breeder"), i("breeder_blanket_sqc"), i("bred_blanket_sqc"), i("plutonium_oxide_sqc"),
                                i("neptunium_oxide_sqc"), i("neptunium_blanket_sqc"), i("bred_neptunium_sqc"), i("plutonium_238_oxide_sqc"),
                                kit("waste_repository")),
                        java.util.List.of(kit("deuterium_separator"), kit("fusion_reactor"))));
                java.util.List<ItemLike> prot = new java.util.ArrayList<>(java.util.List.of(CatalogRegistry.GEIGER_COUNTER.get(),
                        com.sqc.nuclearsq.data.DataRegistry.AREA_MONITOR.get(), com.sqc.nuclearsq.data.DataRegistry.AREA_SENSOR.get(),
                        CatalogRegistry.ANTI_RAD.get()));
                prot.addAll(com.sqc.nuclearsq.radiation.ProtectionRegistry.tabItems());
                prot.addAll(java.util.List.of(com.sqc.nuclearsq.door.DoorRegistry.CONTROLLER_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.HANGAR_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.AIRLOCK_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.VAULT_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.SECTIONAL_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.GRILLE_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.FOLDING_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.LAB_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.GLASS_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.SHIELD_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.COLD_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.CAGE_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.SLIDE_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.INDUSTRIAL_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.PERFORATED_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.SPEED_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.LIFT_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.AIRLOCK_ITEM.get()));
                section(out, H_PROTECTION, prot.toArray(new ItemLike[0]));
                section(out, H_WEAPONS, kit("weapons_processing"), i("plutonium_metal_sqc"), i("weapons_plutonium_core_sqc"),
                        BombRegistry.BOMB_ITEM.get(), BombRegistry.LARGE_BOMB_ITEM.get(), BombRegistry.TSAR_BOMB_ITEM.get(), AssemblyRegistry.DETONATOR_ITEM.get());
            }).build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
