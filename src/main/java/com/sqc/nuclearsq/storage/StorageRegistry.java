package com.sqc.nuclearsq.storage;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StorageRegistry {
    private StorageRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredBlock<StorageTerminalBlock> TERMINAL = BLOCKS.register("storage_terminal_sqc",
            () -> new StorageTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5)));
    static {
        ITEMS.registerSimpleBlockItem("storage_terminal_sqc", TERMINAL);
    }

    public static final DeferredBlock<StorageMonitorBlock> MONITOR = BLOCKS.register("storage_monitor_sqc",
            () -> new StorageMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5)));
    static {
        ITEMS.registerSimpleBlockItem("storage_monitor_sqc", MONITOR);
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageMonitorBlockEntity>> MONITOR_BE =
            BLOCK_ENTITIES.register("storage_monitor", () -> BlockEntityType.Builder.of(StorageMonitorBlockEntity::new, MONITOR.get()).build(null));

    public static final DeferredBlock<CraftingTerminalBlock> CRAFTING_TERMINAL = BLOCKS.register("crafting_terminal_sqc",
            () -> new CraftingTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5)));
    static {
        ITEMS.registerSimpleBlockItem("crafting_terminal_sqc", CRAFTING_TERMINAL);
    }

    public static final DeferredBlock<CraftingTerminalBlock> CRAFTING_TERMINAL_PANEL = BLOCKS.register("crafting_terminal_panel_sqc",
            () -> new CraftingTerminalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5), true));
    static {
        ITEMS.registerSimpleBlockItem("crafting_terminal_panel_sqc", CRAFTING_TERMINAL_PANEL);
    }

    public static final DeferredBlock<WifiAdapterBlock> WIFI_ADAPTER = BLOCKS.register("wifi_adapter_sqc",
            () -> new WifiAdapterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 4)));
    static {
        ITEMS.registerSimpleBlockItem("wifi_adapter_sqc", WIFI_ADAPTER);
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WifiAdapterBlockEntity>> WIFI_ADAPTER_BE =
            BLOCK_ENTITIES.register("wifi_adapter", () -> BlockEntityType.Builder.of(WifiAdapterBlockEntity::new, WIFI_ADAPTER.get()).build(null));

    public static final DeferredBlock<ItemPortBlock> ITEM_PORT = BLOCKS.register("item_port_sqc",
            () -> new ItemPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 3)));
    static {
        ITEMS.registerSimpleBlockItem("item_port_sqc", ITEM_PORT);
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPortBlockEntity>> ITEM_PORT_BE =
            BLOCK_ENTITIES.register("item_port", () -> BlockEntityType.Builder.of(ItemPortBlockEntity::new, ITEM_PORT.get()).build(null));

    public static final DeferredBlock<NetPipeAdapterBlock> NET_PIPE_ADAPTER = BLOCKS.register("net_pipe_adapter_sqc",
            () -> new NetPipeAdapterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 3)));
    static {
        ITEMS.registerSimpleBlockItem("net_pipe_adapter_sqc", NET_PIPE_ADAPTER);
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetPipeAdapterBlockEntity>> NET_PIPE_ADAPTER_BE =
            BLOCK_ENTITIES.register("net_pipe_adapter", () -> BlockEntityType.Builder.of(NetPipeAdapterBlockEntity::new, NET_PIPE_ADAPTER.get()).build(null));

    public static final DeferredItem<WifiTerminalItem> WIFI_TERMINAL = ITEMS.register("wifi_terminal_sqc",
            () -> new WifiTerminalItem(new Item.Properties()));
    public static final DeferredItem<Item> WIFI_ANTENNA = ITEMS.register("wifi_antenna_sqc", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> WIFI_ANTENNA_SECTOR = ITEMS.register("wifi_antenna_sector_sqc", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> WIFI_ANTENNA_DISH = ITEMS.register("wifi_antenna_dish_sqc", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> WIFI_ANTENNA_QUANTUM = ITEMS.register("wifi_antenna_quantum_sqc", () -> new Item(new Item.Properties()));

    public static final DeferredItem<DriveItem> HARD_DRIVE_1 = drive("hard_drive_1_sqc", 1, "hard_drive_1");
    public static final DeferredItem<DriveItem> HARD_DRIVE_2 = drive("hard_drive_2_sqc", 2, "hard_drive_2");
    public static final DeferredItem<DriveItem> HARD_DRIVE_3 = drive("hard_drive_3_sqc", 3, "hard_drive_3");
    public static final DeferredItem<DriveItem> SOLID_STATE_DRIVE = drive("solid_state_drive_sqc", 4, "solid_state_drive");
    public static final DeferredItem<DriveItem> NVME_DRIVE = drive("nvme_drive_sqc", 5, "nvme_drive");

    private static final String[] CPU_IDS = {"cpu_module_1", "cpu_module_2", "cpu_module", "cpu_module_8", "cpu_module_16",
            "cpu_module_32", "cpu_module_64", "cpu_module_128"};
    private static final String[] RAM_IDS = {"ram_module_256mb", "ram_module_512mb", "ram_module_1gb", "ram_module_2gb",
            "ram_module_4gb", "ram_module_8gb", "ram_module", "ram_module_32gb"};

    @SuppressWarnings("unchecked")
    public static final DeferredItem<ComputeModuleItem>[] CPU_MODULES = new DeferredItem[CPU_IDS.length];
    @SuppressWarnings("unchecked")
    public static final DeferredItem<ComputeModuleItem>[] RAM_MODULES = new DeferredItem[RAM_IDS.length];

    static {
        for (int i = 0; i < CPU_IDS.length; i++) CPU_MODULES[i] = computeModule(CPU_IDS[i]);
        for (int i = 0; i < RAM_IDS.length; i++) RAM_MODULES[i] = computeModule(RAM_IDS[i]);
    }

    public static final DeferredItem<ComputeModuleItem> CPU_MODULE = CPU_MODULES[2];
    public static final DeferredItem<ComputeModuleItem> RAM_MODULE = RAM_MODULES[6];

    private static DeferredItem<ComputeModuleItem> computeModule(String valuesId) {
        return ITEMS.register(valuesId + "_sqc", () -> new ComputeModuleItem(new Item.Properties(), valuesId));
    }

    public static final DeferredItem<Item> QUANTUM_MODULE = ITEMS.register("quantum_module_sqc",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<AssemblerTempoItem> ASSEMBLER_TEMPO = ITEMS.register("assembler_tempo_sqc",
            () -> new AssemblerTempoItem(new Item.Properties()));

    public static final DeferredItem<PatternCardItem> PATTERN_CARD = ITEMS.register("pattern_card_sqc",
            () -> new PatternCardItem(new Item.Properties()));

    private static DeferredItem<DriveItem> drive(String id, int tier, String values) {
        return ITEMS.register(id, () -> new DriveItem(new Item.Properties(), tier, values));
    }

    public static final DeferredHolder<MenuType<?>, MenuType<StorageTerminalMenu>> TERMINAL_MENU =
            MENUS.register("storage_terminal", () -> IMenuTypeExtension.create(StorageTerminalMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingTerminalMenu>> CRAFTING_MENU =
            MENUS.register("crafting_terminal", () -> IMenuTypeExtension.create(CraftingTerminalMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ServerRackMenu>> RACK_MENU =
            MENUS.register("server_rack", () -> IMenuTypeExtension.create(ServerRackMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ComponentAssemblerMenu>> ASSEMBLER_MENU =
            MENUS.register("component_assembler", () -> IMenuTypeExtension.create(ComponentAssemblerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ItemPortMenu>> ITEM_PORT_MENU =
            MENUS.register("item_port", () -> IMenuTypeExtension.create(ItemPortMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<NetPipeAdapterMenu>> NET_PIPE_ADAPTER_MENU =
            MENUS.register("net_pipe_adapter", () -> IMenuTypeExtension.create(NetPipeAdapterMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<StorageMonitorMenu>> MONITOR_MENU =
            MENUS.register("storage_monitor", () -> IMenuTypeExtension.create(StorageMonitorMenu::new));

    public static List<DeferredItem<DriveItem>> drives() {
        return List.of(HARD_DRIVE_1, HARD_DRIVE_2, HARD_DRIVE_3, SOLID_STATE_DRIVE, NVME_DRIVE);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(StorageRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WIFI_ADAPTER_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ITEM_PORT_BE.get(), (be, side) -> be.handler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NET_PIPE_ADAPTER_BE.get(), NetPipeAdapterBlockEntity::handler);
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> WifiTerminalItem.storage(stack), WIFI_TERMINAL.get());
    }
}
