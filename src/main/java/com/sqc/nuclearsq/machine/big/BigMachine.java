package com.sqc.nuclearsq.machine.big;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.machine.shapes.BatteryLargeShapes;
import com.sqc.nuclearsq.machine.shapes.PatternRackShapes;
import com.sqc.nuclearsq.machine.shapes.AcidPlantShapes;
import com.sqc.nuclearsq.machine.shapes.BoricAcidPlantShapes;
import com.sqc.nuclearsq.machine.shapes.HeavyWaterPlantShapes;
import com.sqc.nuclearsq.machine.shapes.ChemicalLabShapes;
import com.sqc.nuclearsq.machine.shapes.ComputeClusterShapes;
import com.sqc.nuclearsq.machine.shapes.PolymerPlantShapes;
import com.sqc.nuclearsq.machine.shapes.VitrificationPlantShapes;
import com.sqc.nuclearsq.machine.shapes.IonExchangerShapes;
import com.sqc.nuclearsq.machine.shapes.ElectrolyzerShapes;
import com.sqc.nuclearsq.machine.shapes.ExoStationShapes;
import com.sqc.nuclearsq.machine.shapes.FuelCellShapes;
import com.sqc.nuclearsq.machine.shapes.CleanRoomShapes;
import com.sqc.nuclearsq.machine.shapes.EquipmentAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.BlastFurnaceShapes;
import com.sqc.nuclearsq.machine.shapes.FoundryShapes;
import com.sqc.nuclearsq.machine.shapes.KilnShapes;
import com.sqc.nuclearsq.machine.shapes.FluidBufferShapes;
import com.sqc.nuclearsq.machine.shapes.GasGeneratorShapes;
import com.sqc.nuclearsq.machine.shapes.CrackerShapes;
import com.sqc.nuclearsq.machine.shapes.DistillationColumnShapes;
import com.sqc.nuclearsq.machine.shapes.HammerMillShapes;
import com.sqc.nuclearsq.machine.shapes.OilDerrickShapes;
import com.sqc.nuclearsq.machine.shapes.PlateMillShapes;
import com.sqc.nuclearsq.machine.shapes.MicroAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.CncMillShapes;
import com.sqc.nuclearsq.machine.shapes.SolderMachineShapes;
import com.sqc.nuclearsq.machine.shapes.PelletFactoryShapes;
import com.sqc.nuclearsq.machine.shapes.SlagCrusherShapes;
import com.sqc.nuclearsq.machine.shapes.BatteryMediumShapes;
import com.sqc.nuclearsq.machine.shapes.BatterySmallShapes;
import com.sqc.nuclearsq.machine.shapes.ContainmentShapes;
import com.sqc.nuclearsq.machine.shapes.ControlDeskShapes;
import com.sqc.nuclearsq.machine.shapes.ControlRodDriveShapes;
import com.sqc.nuclearsq.machine.shapes.DeconDockShapes;
import com.sqc.nuclearsq.machine.shapes.DeuteriumSeparatorShapes;
import com.sqc.nuclearsq.machine.shapes.FusionReactorShapes;
import com.sqc.nuclearsq.machine.shapes.HighPressureInjectionShapes;
import com.sqc.nuclearsq.machine.shapes.MainCoolantPumpShapes;
import com.sqc.nuclearsq.machine.shapes.PressurizerShapes;
import com.sqc.nuclearsq.machine.shapes.CoalGeneratorShapes;
import com.sqc.nuclearsq.machine.shapes.SolarFieldShapes;
import com.sqc.nuclearsq.machine.shapes.SteamGeneratorShapes;
import com.sqc.nuclearsq.machine.shapes.EmergencyCoolingShapes;
import com.sqc.nuclearsq.machine.shapes.FastBreederShapes;
import com.sqc.nuclearsq.machine.shapes.EnrichmentCentrifugeShapes;
import com.sqc.nuclearsq.machine.shapes.HeavyAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.HighPressurePumpShapes;
import com.sqc.nuclearsq.machine.shapes.IntakePumpShapes;
import com.sqc.nuclearsq.machine.shapes.LeachingPlantShapes;
import com.sqc.nuclearsq.machine.shapes.Uf6ConverterShapes;
import com.sqc.nuclearsq.machine.shapes.OreMillShapes;
import com.sqc.nuclearsq.machine.shapes.ReprocessingPlantShapes;
import com.sqc.nuclearsq.machine.shapes.ShaftGeneratorShapes;
import com.sqc.nuclearsq.machine.shapes.SteamDryerShapes;
import com.sqc.nuclearsq.machine.shapes.SteamSeparatorShapes;
import com.sqc.nuclearsq.machine.shapes.ReliefTankShapes;
import com.sqc.nuclearsq.machine.shapes.WasteRepositoryShapes;
import com.sqc.nuclearsq.machine.shapes.WeaponsProcessingShapes;
import com.sqc.nuclearsq.machine.shapes.ReheaterShapes;
import com.sqc.nuclearsq.machine.shapes.FeedwaterHeaterShapes;
import com.sqc.nuclearsq.machine.shapes.CoolingTowerLargeShapes;
import com.sqc.nuclearsq.machine.shapes.CoolingTowerXlShapes;
import com.sqc.nuclearsq.machine.shapes.SpentFuelRackShapes;
import com.sqc.nuclearsq.machine.shapes.SteamPurifierShapes;
import com.sqc.nuclearsq.machine.shapes.CondenserShapes;
import com.sqc.nuclearsq.machine.shapes.WaterTreatmentShapes;
import com.sqc.nuclearsq.machine.shapes.WindTurbineLargeShapes;
import com.sqc.nuclearsq.machine.shapes.WindTurbineMediumShapes;
import com.sqc.nuclearsq.machine.shapes.WindTurbineSmallShapes;
import com.sqc.nuclearsq.machine.shapes.FeedwaterPumpShapes;
import com.sqc.nuclearsq.machine.shapes.ReactorAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.ParticleAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.ComponentAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.ComputeAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.QuantumComputerShapes;
import com.sqc.nuclearsq.machine.shapes.ServerRackLargeShapes;
import com.sqc.nuclearsq.machine.shapes.ServerRackShapes;
import com.sqc.nuclearsq.machine.shapes.ServerRackSmallShapes;
import com.sqc.nuclearsq.machine.shapes.SmallPartsAssemblerShapes;
import com.sqc.nuclearsq.machine.shapes.SubstationShapes;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public enum BigMachine {
    RELIEF_TANK("relief_tank", ReliefTankShapes.BOXES, ReliefTankShapes.CELLS, ReliefTankShapes.LADDER, ReliefTankShapes.MONITOR),
    STEAM_SEPARATOR("steam_separator", SteamSeparatorShapes.BOXES, SteamSeparatorShapes.CELLS, SteamSeparatorShapes.LADDER, SteamSeparatorShapes.MONITOR),
    STEAM_DRYER("steam_dryer", SteamDryerShapes.BOXES, SteamDryerShapes.CELLS, SteamDryerShapes.LADDER, SteamDryerShapes.MONITOR),
    STEAM_GENERATOR("steam_generator", SteamGeneratorShapes.BOXES, SteamGeneratorShapes.CELLS, SteamGeneratorShapes.LADDER, SteamGeneratorShapes.MONITOR),
    CONTROL_ROD_DRIVE("control_rod_drive", ControlRodDriveShapes.BOXES, ControlRodDriveShapes.CELLS, ControlRodDriveShapes.LADDER, ControlRodDriveShapes.MONITOR),
    EMERGENCY_COOLING("emergency_cooling", EmergencyCoolingShapes.BOXES, EmergencyCoolingShapes.CELLS, EmergencyCoolingShapes.LADDER, EmergencyCoolingShapes.MONITOR),
    CONTAINMENT("containment", ContainmentDoor.BOXES, ContainmentShapes.CELLS, ContainmentShapes.LADDER, ContainmentShapes.MONITOR),
    HEAVY_ASSEMBLER("heavy_assembler", HeavyAssemblerShapes.BOXES, HeavyAssemblerShapes.CELLS, HeavyAssemblerShapes.LADDER, HeavyAssemblerShapes.MONITOR),
    MAIN_COOLANT_PUMP("main_coolant_pump", MainCoolantPumpShapes.BOXES, MainCoolantPumpShapes.CELLS, MainCoolantPumpShapes.LADDER, MainCoolantPumpShapes.MONITOR),
    HIGH_PRESSURE_PUMP("high_pressure_pump", HighPressurePumpShapes.BOXES, HighPressurePumpShapes.CELLS, HighPressurePumpShapes.LADDER, HighPressurePumpShapes.MONITOR),
    REPROCESSING_PLANT("reprocessing_plant", ReprocessingPlantShapes.BOXES, ReprocessingPlantShapes.CELLS, ReprocessingPlantShapes.LADDER, ReprocessingPlantShapes.MONITOR),
    SHAFT_GENERATOR("shaft_generator", ShaftGeneratorShapes.BOXES, ShaftGeneratorShapes.CELLS, ShaftGeneratorShapes.LADDER, ShaftGeneratorShapes.MONITOR),
    ORE_MILL("ore_mill", OreMillShapes.BOXES, OreMillShapes.CELLS, OreMillShapes.LADDER, OreMillShapes.MONITOR),
    ENRICHMENT_CENTRIFUGE("enrichment_centrifuge", EnrichmentCentrifugeShapes.BOXES, EnrichmentCentrifugeShapes.CELLS,
            EnrichmentCentrifugeShapes.LADDER, EnrichmentCentrifugeShapes.MONITOR),
    COAL_GENERATOR("coal_generator", CoalGeneratorShapes.BOXES, CoalGeneratorShapes.CELLS, CoalGeneratorShapes.LADDER,
            CoalGeneratorShapes.MONITOR),
    SOLAR_FIELD("solar_field", SolarFieldShapes.BOXES, SolarFieldShapes.CELLS, SolarFieldShapes.LADDER, SolarFieldShapes.MONITOR),
    CONTROL_DESK("control_desk", ControlDeskShapes.BOXES, ControlDeskShapes.CELLS, ControlDeskShapes.LADDER, ControlDeskShapes.MONITOR),
    REHEATER("reheater", ReheaterShapes.BOXES, ReheaterShapes.CELLS, ReheaterShapes.LADDER, ReheaterShapes.MONITOR),
    FEEDWATER_HEATER("feedwater_heater", FeedwaterHeaterShapes.BOXES, FeedwaterHeaterShapes.CELLS, FeedwaterHeaterShapes.LADDER,
            FeedwaterHeaterShapes.MONITOR),
    COOLING_TOWER_LARGE("cooling_tower_large", CoolingTowerLargeShapes.BOXES, CoolingTowerLargeShapes.CELLS,
            CoolingTowerLargeShapes.LADDER, CoolingTowerLargeShapes.MONITOR),
    WASTE_REPOSITORY("waste_repository", WasteRepositoryShapes.BOXES, WasteRepositoryShapes.CELLS, WasteRepositoryShapes.LADDER,
            WasteRepositoryShapes.MONITOR),
    COOLING_TOWER_XL("cooling_tower_xl", CoolingTowerXlShapes.BOXES, CoolingTowerXlShapes.CELLS, CoolingTowerXlShapes.LADDER,
            CoolingTowerXlShapes.MONITOR),
    SPENT_FUEL_RACK("spent_fuel_rack", SpentFuelRackShapes.BOXES, SpentFuelRackShapes.CELLS, SpentFuelRackShapes.LADDER,
            SpentFuelRackShapes.MONITOR),
    BATTERY_SMALL("battery_small", BatterySmallShapes.BOXES, BatterySmallShapes.CELLS, BatterySmallShapes.LADDER,
            BatterySmallShapes.MONITOR),
    BATTERY_MEDIUM("battery_medium", BatteryMediumShapes.BOXES, BatteryMediumShapes.CELLS, BatteryMediumShapes.LADDER,
            BatteryMediumShapes.MONITOR),
    BATTERY_LARGE("battery_large", BatteryLargeShapes.BOXES, BatteryLargeShapes.CELLS, BatteryLargeShapes.LADDER,
            BatteryLargeShapes.MONITOR),
    STEAM_PURIFIER("steam_purifier", SteamPurifierShapes.BOXES, SteamPurifierShapes.CELLS, SteamPurifierShapes.LADDER,
            SteamPurifierShapes.MONITOR),
    CONDENSER("condenser", CondenserShapes.BOXES, CondenserShapes.CELLS, CondenserShapes.LADDER, CondenserShapes.MONITOR),
    WATER_TREATMENT("water_treatment", WaterTreatmentShapes.BOXES, WaterTreatmentShapes.CELLS, WaterTreatmentShapes.LADDER,
            WaterTreatmentShapes.MONITOR),
    FEEDWATER_PUMP("feedwater_pump", FeedwaterPumpShapes.BOXES, FeedwaterPumpShapes.CELLS, FeedwaterPumpShapes.LADDER,
            FeedwaterPumpShapes.MONITOR),
    REACTOR_ASSEMBLER("reactor_assembler", ReactorAssemblerShapes.BOXES, ReactorAssemblerShapes.CELLS, ReactorAssemblerShapes.LADDER,
            ReactorAssemblerShapes.MONITOR),
    PARTICLE_ASSEMBLER("particle_assembler", ParticleAssemblerShapes.BOXES, ParticleAssemblerShapes.CELLS,
            ParticleAssemblerShapes.LADDER, ParticleAssemblerShapes.MONITOR),
    SMALL_PARTS_ASSEMBLER("small_parts_assembler", SmallPartsAssemblerShapes.BOXES, SmallPartsAssemblerShapes.CELLS,
            SmallPartsAssemblerShapes.LADDER, SmallPartsAssemblerShapes.MONITOR),
    SERVER_RACK_SMALL("server_rack_small", ServerRackSmallShapes.BOXES, ServerRackSmallShapes.CELLS, ServerRackSmallShapes.LADDER,
            ServerRackSmallShapes.MONITOR),
    SERVER_RACK("server_rack", ServerRackShapes.BOXES, ServerRackShapes.CELLS, ServerRackShapes.LADDER, ServerRackShapes.MONITOR),
    SERVER_RACK_LARGE("server_rack_large", ServerRackLargeShapes.BOXES, ServerRackLargeShapes.CELLS, ServerRackLargeShapes.LADDER,
            ServerRackLargeShapes.MONITOR),
    COMPONENT_ASSEMBLER("component_assembler", ComponentAssemblerShapes.BOXES, ComponentAssemblerShapes.CELLS,
            ComponentAssemblerShapes.LADDER, ComponentAssemblerShapes.MONITOR),
    SUBSTATION("substation", SubstationShapes.BOXES, SubstationShapes.CELLS, SubstationShapes.LADDER, SubstationShapes.MONITOR),
    PRESSURIZER("pressurizer", PressurizerShapes.BOXES, PressurizerShapes.CELLS, PressurizerShapes.LADDER, PressurizerShapes.MONITOR),
    HIGH_PRESSURE_INJECTION("high_pressure_injection", HighPressureInjectionShapes.BOXES, HighPressureInjectionShapes.CELLS,
            HighPressureInjectionShapes.LADDER, HighPressureInjectionShapes.MONITOR),
    FAST_BREEDER("fast_breeder", FastBreederShapes.BOXES, FastBreederShapes.CELLS, FastBreederShapes.LADDER, FastBreederShapes.MONITOR),
    WEAPONS_PROCESSING("weapons_processing", WeaponsProcessingShapes.BOXES, WeaponsProcessingShapes.CELLS,
            WeaponsProcessingShapes.LADDER, WeaponsProcessingShapes.MONITOR),
    FUSION_REACTOR("fusion_reactor", FusionReactorShapes.BOXES, FusionReactorShapes.CELLS, FusionReactorShapes.LADDER,
            FusionReactorShapes.MONITOR),
    DEUTERIUM_SEPARATOR("deuterium_separator", DeuteriumSeparatorShapes.BOXES, DeuteriumSeparatorShapes.CELLS,
            DeuteriumSeparatorShapes.LADDER, DeuteriumSeparatorShapes.MONITOR),
    WIND_TURBINE_SMALL("wind_turbine_small", WindTurbineSmallShapes.BOXES, WindTurbineSmallShapes.CELLS, WindTurbineSmallShapes.LADDER,
            WindTurbineSmallShapes.MONITOR),
    WIND_TURBINE_MEDIUM("wind_turbine_medium", WindTurbineMediumShapes.BOXES, WindTurbineMediumShapes.CELLS, WindTurbineMediumShapes.LADDER,
            WindTurbineMediumShapes.MONITOR),
    WIND_TURBINE_LARGE("wind_turbine_large", WindTurbineLargeShapes.BOXES, WindTurbineLargeShapes.CELLS, WindTurbineLargeShapes.LADDER,
            WindTurbineLargeShapes.MONITOR),
    LEACHING_PLANT("leaching_plant", LeachingPlantShapes.BOXES, LeachingPlantShapes.CELLS, LeachingPlantShapes.LADDER,
            LeachingPlantShapes.MONITOR),
    UF6_CONVERTER("uf6_converter", Uf6ConverterShapes.BOXES, Uf6ConverterShapes.CELLS, Uf6ConverterShapes.LADDER,
            Uf6ConverterShapes.MONITOR),
    PELLET_FACTORY("pellet_factory", PelletFactoryShapes.BOXES, PelletFactoryShapes.CELLS, PelletFactoryShapes.LADDER,
            PelletFactoryShapes.MONITOR),
    FOUNDRY("foundry", FoundryShapes.BOXES, FoundryShapes.CELLS, FoundryShapes.LADDER, FoundryShapes.MONITOR),
    SLAG_CRUSHER("slag_crusher", SlagCrusherShapes.BOXES, SlagCrusherShapes.CELLS, SlagCrusherShapes.LADDER, SlagCrusherShapes.MONITOR),
    ELECTROLYZER("electrolyzer", ElectrolyzerShapes.BOXES, ElectrolyzerShapes.CELLS, ElectrolyzerShapes.LADDER, ElectrolyzerShapes.MONITOR),
    ACID_PLANT("acid_plant", AcidPlantShapes.BOXES, AcidPlantShapes.CELLS, AcidPlantShapes.LADDER, AcidPlantShapes.MONITOR),
    BORIC_ACID_PLANT("boric_acid_plant", BoricAcidPlantShapes.BOXES, BoricAcidPlantShapes.CELLS, BoricAcidPlantShapes.LADDER, BoricAcidPlantShapes.MONITOR),
    ION_EXCHANGER("ion_exchanger", IonExchangerShapes.BOXES, IonExchangerShapes.CELLS, IonExchangerShapes.LADDER, IonExchangerShapes.MONITOR),
    HEAVY_WATER_PLANT("heavy_water_plant", HeavyWaterPlantShapes.BOXES, HeavyWaterPlantShapes.CELLS, HeavyWaterPlantShapes.LADDER, HeavyWaterPlantShapes.MONITOR),
    POLYMER_PLANT("polymer_plant", PolymerPlantShapes.BOXES, PolymerPlantShapes.CELLS, PolymerPlantShapes.LADDER, PolymerPlantShapes.MONITOR),
    VITRIFICATION_PLANT("vitrification_plant", VitrificationPlantShapes.BOXES, VitrificationPlantShapes.CELLS, VitrificationPlantShapes.LADDER, VitrificationPlantShapes.MONITOR),
    CHEMICAL_LAB("chemical_lab", ChemicalLabShapes.BOXES, ChemicalLabShapes.CELLS, ChemicalLabShapes.LADDER, ChemicalLabShapes.MONITOR),
    COMPUTE_CLUSTER("compute_cluster", ComputeClusterShapes.BOXES, ComputeClusterShapes.CELLS, ComputeClusterShapes.LADDER,
            ComputeClusterShapes.MONITOR),
    COMPUTE_ASSEMBLER("compute_assembler", ComputeAssemblerShapes.BOXES, ComputeAssemblerShapes.CELLS, ComputeAssemblerShapes.LADDER,
            ComputeAssemblerShapes.MONITOR),
    QUANTUM_COMPUTER("quantum_computer", QuantumComputerShapes.BOXES, QuantumComputerShapes.CELLS, QuantumComputerShapes.LADDER,
            QuantumComputerShapes.MONITOR),
    PATTERN_RACK("pattern_rack", PatternRackShapes.BOXES, PatternRackShapes.CELLS, PatternRackShapes.LADDER, PatternRackShapes.MONITOR),
    MICRO_ASSEMBLER("micro_assembler", MicroAssemblerShapes.BOXES, MicroAssemblerShapes.CELLS, MicroAssemblerShapes.LADDER,
            MicroAssemblerShapes.MONITOR),
    CLEAN_ROOM("clean_room", CleanRoomShapes.BOXES, CleanRoomShapes.CELLS, CleanRoomShapes.LADDER, CleanRoomShapes.MONITOR),
    EQUIPMENT_ASSEMBLER("equipment_assembler", EquipmentAssemblerShapes.BOXES, EquipmentAssemblerShapes.CELLS, EquipmentAssemblerShapes.LADDER,
            EquipmentAssemblerShapes.MONITOR),
    FUEL_CELL("fuel_cell", FuelCellShapes.BOXES, FuelCellShapes.CELLS, FuelCellShapes.LADDER, FuelCellShapes.MONITOR),
    DECON_DOCK("decon_dock", DeconDockShapes.BOXES, DeconDockShapes.CELLS, DeconDockShapes.LADDER, DeconDockShapes.MONITOR),
    EXO_STATION("exo_station", ExoStationShapes.BOXES, ExoStationShapes.CELLS, ExoStationShapes.LADDER, ExoStationShapes.MONITOR),
    INTAKE_PUMP("intake_pump", IntakePumpShapes.BOXES, IntakePumpShapes.CELLS, IntakePumpShapes.LADDER, IntakePumpShapes.MONITOR),
    PLATE_MILL("plate_mill", PlateMillShapes.BOXES, PlateMillShapes.CELLS, PlateMillShapes.LADDER, PlateMillShapes.MONITOR),
    HAMMER_MILL("hammer_mill", HammerMillShapes.BOXES, HammerMillShapes.CELLS, HammerMillShapes.LADDER, HammerMillShapes.MONITOR),
    CNC_MILL("cnc_mill", CncMillShapes.BOXES, CncMillShapes.CELLS, CncMillShapes.LADDER, CncMillShapes.MONITOR),
    SOLDER_MACHINE("solder_machine", SolderMachineShapes.BOXES, SolderMachineShapes.CELLS, SolderMachineShapes.LADDER, SolderMachineShapes.MONITOR),
    OIL_DERRICK("oil_derrick", OilDerrickShapes.BOXES, OilDerrickShapes.CELLS, OilDerrickShapes.LADDER, OilDerrickShapes.MONITOR),
    DISTILLATION_COLUMN("distillation_column", DistillationColumnShapes.BOXES, DistillationColumnShapes.CELLS, DistillationColumnShapes.LADDER,
            DistillationColumnShapes.MONITOR),
    CRACKER("cracker", CrackerShapes.BOXES, CrackerShapes.CELLS, CrackerShapes.LADDER, CrackerShapes.MONITOR),
    GAS_GENERATOR("gas_generator", GasGeneratorShapes.BOXES, GasGeneratorShapes.CELLS, GasGeneratorShapes.LADDER, GasGeneratorShapes.MONITOR),
    FLUID_BUFFER("fluid_buffer", FluidBufferShapes.BOXES, FluidBufferShapes.CELLS, FluidBufferShapes.LADDER, FluidBufferShapes.MONITOR),
    KILN("kiln", KilnShapes.BOXES, KilnShapes.CELLS, KilnShapes.LADDER, KilnShapes.MONITOR),
    BLAST_FURNACE("blast_furnace", BlastFurnaceShapes.BOXES, BlastFurnaceShapes.CELLS, BlastFurnaceShapes.LADDER, BlastFurnaceShapes.MONITOR);

    public boolean isWind() {
        return this == WIND_TURBINE_SMALL || this == WIND_TURBINE_MEDIUM || this == WIND_TURBINE_LARGE;
    }


    public boolean isServerRack() {
        return this == SERVER_RACK_SMALL || this == SERVER_RACK || this == SERVER_RACK_LARGE || this == PATTERN_RACK;
    }

    public boolean isPatternRack() {
        return this == PATTERN_RACK;
    }

    public boolean smallGrid() {
        return this == REACTOR_ASSEMBLER || this == SMALL_PARTS_ASSEMBLER || this == COMPUTE_ASSEMBLER || this == MICRO_ASSEMBLER || this == CLEAN_ROOM || this == EQUIPMENT_ASSEMBLER || this == CNC_MILL || this == SOLDER_MACHINE;
    }

    public boolean isAutocrafter() {
        return this == COMPONENT_ASSEMBLER || this == QUANTUM_COMPUTER;
    }

    public boolean isCoolingTower() {
        return this == COOLING_TOWER_LARGE || this == COOLING_TOWER_XL;
    }

    public BigShellBlock shellBlock() {
        return com.sqc.nuclearsq.registry.BigMachineRegistry.SHELLS.get(this).get();
    }

    public record Port(String name, BigPortKind kind, String dir, int x, int y, int z, int rate, int tank, int slots) {
        public boolean in() { return !"out".equals(dir); }
        public boolean out() { return !"in".equals(dir); }
    }

    public record Energy(int capacity, int use, int maxReceive, int maxExtract) {}

    public final String id;
    public final double[][][] boxes;
    public final int[][] cells;
    public final boolean[] ladder;
    @Nullable
    public final double[] monitor;
    public final MultiblockStructure structure;
    public final int w, h, d, ax, ay, az;
    public volatile List<Port> ports;
    @Nullable
    public volatile Energy energy;
    private volatile Map<String, Integer> extra;
    private final List<Port> layoutPorts;

    public boolean chunkBaked() {
        return this != CONTROL_ROD_DRIVE && this != SPENT_FUEL_RACK && this != PARTICLE_ASSEMBLER && this != CONTROL_DESK
                && this != CONTAINMENT && this != EXO_STATION;
    }

    BigMachine(String id, double[][][] boxes, int[][] cells, boolean[] ladder, @Nullable double[] monitor) {
        this.id = id;
        this.monitor = monitor;
        this.boxes = boxes;
        this.cells = cells;
        this.ladder = ladder;
        this.structure = MultiblockStructure.load(id);
        if (structure == null) throw new IllegalStateException("Blockmuster data/nuclearsq/multiblocks/" + id + ".json fehlt");
        JsonObject layout = read("/data/nuclearsq/multiblocks/" + id + ".json");
        var size = layout.getAsJsonArray("size");
        w = size.get(0).getAsInt();
        h = size.get(1).getAsInt();
        d = size.get(2).getAsInt();
        var anchor = layout.getAsJsonArray("anchor");
        if (anchor == null) throw new IllegalStateException(id + ".json: kein anchor (Controller '@' im Bauplan fehlt?)");
        ax = anchor.get(0).getAsInt();
        ay = anchor.get(1).getAsInt();
        az = anchor.get(2).getAsInt();
        List<Port> list = new ArrayList<>();
        for (JsonElement e : layout.getAsJsonArray("ports")) {
            JsonObject p = e.getAsJsonObject();
            var cell = p.getAsJsonArray("cell");
            list.add(new Port(p.get("name").getAsString(), BigPortKind.of(p.get("type").getAsString()), p.get("dir").getAsString(),
                    cell.get(0).getAsInt(), cell.get(1).getAsInt(), cell.get(2).getAsInt(), 0, 0, 0));
        }
        layoutPorts = List.copyOf(list);
        apply(com.sqc.nuclearsq.gamedata.GameData.cooling());
    }

    public List<String> portNames() {
        return layoutPorts.stream().map(Port::name).toList();
    }

    public void apply(com.sqc.nuclearsq.gamedata.CoolingValues values) {
        var vals = values.machine(id);
        if (vals == null) throw new IllegalStateException("cooling_values.json: Maschine " + id + " fehlt");
        List<Port> list = new ArrayList<>();
        int energyRate = 0, energyOut = 0;
        for (Port g : layoutPorts) {
            var v = vals.ports().get(g.name());
            if (v == null) throw new IllegalStateException("cooling_values.json: " + id + "." + g.name() + " fehlt");
            Port port = new Port(g.name(), g.kind(), g.dir(), g.x(), g.y(), g.z(), v.rate(), v.tank(), v.slots());
            if (port.kind() == BigPortKind.ENERGY && port.in()) energyRate = port.rate();
            if (port.kind() == BigPortKind.ENERGY && port.out()) energyOut = port.rate();
            list.add(port);
        }
        ports = Collections.unmodifiableList(list);
        final int in = energyRate, out = energyOut;
        energy = vals.energy().map(en -> new Energy(en.capacity(), en.use(), in, out)).orElse(null);
        extra = vals.extra();
    }

    public boolean isEmergency() {
        return this == EMERGENCY_COOLING || this == HIGH_PRESSURE_INJECTION;
    }

    public boolean isPump() {
        return this == MAIN_COOLANT_PUMP || this == HIGH_PRESSURE_PUMP;
    }

    @Nullable
    public com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind recipeKind() {
        return switch (this) {
            case REPROCESSING_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.REPROCESSING;
            case ORE_MILL -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.ORE_MILLING;
            case ENRICHMENT_CENTRIFUGE -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.ENRICHMENT;
            case WEAPONS_PROCESSING -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.WEAPONS_PROCESSING;
            case LEACHING_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.LEACHING;
            case UF6_CONVERTER -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.UF6_CONVERSION;
            case PELLET_FACTORY -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.PELLETING;
            case FOUNDRY -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.FOUNDRY;
            case SLAG_CRUSHER -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.SLAG_CRUSHING;
            case ACID_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.ACID_PLANT;
            case BORIC_ACID_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.BORIC_ACID;
            case ION_EXCHANGER -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.ION_EXCHANGE;
            case HEAVY_WATER_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.HEAVY_WATER;
            case POLYMER_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.POLYMER;
            case VITRIFICATION_PLANT -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.VITRIFICATION;
            case CHEMICAL_LAB -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.CHEMISTRY;
            case PLATE_MILL -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.PLATE_MILLING;
            case HAMMER_MILL -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.DUST_MILLING;
            case KILN -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.KILN_FIRING;
            case BLAST_FURNACE -> com.sqc.nuclearsq.reprocessing.ReprocessingRecipe.Kind.BLAST_SMELTING;
            default -> null;
        };
    }

    public boolean hasFluidRows() {
        return this == LEACHING_PLANT || this == ACID_PLANT || this == UF6_CONVERTER || this == BORIC_ACID_PLANT || this == ION_EXCHANGER || this == HEAVY_WATER_PLANT || this == POLYMER_PLANT || this == VITRIFICATION_PLANT || this == CHEMICAL_LAB;
    }

    public boolean showsTank(Port p) {
        return p.kind().isFluid() && (p.in() || this == ACID_PLANT || this == ION_EXCHANGER || this == HEAVY_WATER_PLANT || this == POLYMER_PLANT || this == VITRIFICATION_PLANT || this == CHEMICAL_LAB);
    }

    public boolean hasAuxSlot() {
        return this == UF6_CONVERTER || this == PELLET_FACTORY || this == FOUNDRY || this == POLYMER_PLANT || this == PLATE_MILL || this == KILN || this == BLAST_FURNACE;
    }

    public String controllerId() {
        return id + "_controller_sqc";
    }

    @Nullable
    public Port port(String name) {
        for (Port p : ports) if (p.name().equals(name)) return p;
        return null;
    }

    public int extra(String key) {
        return extra.getOrDefault(key, 0);
    }

    private static JsonObject read(String res) {
        try (InputStream in = BigMachine.class.getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException(res + " fehlt");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(res + " nicht lesbar: " + e.getMessage(), e);
        }
    }
}
