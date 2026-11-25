package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.multiblock.PartBlockItem;
import com.sqc.nuclearsq.turbine.TurbineControllerBlock;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;
import com.sqc.nuclearsq.turbine.TurbineMenu;
import com.sqc.nuclearsq.turbine.TurbinePortBlock;
import com.sqc.nuclearsq.turbine.TurbinePortBlockEntity;
import com.sqc.nuclearsq.turbine.TurbinePortType;
import com.sqc.nuclearsq.turbine.TurbineShellBlock;

import java.util.EnumMap;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MultiblockRegistry {
    private MultiblockRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NuclearSQ.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NuclearSQ.MODID);

    public static final DeferredBlock<Block> TURBINE_CONTROLLER = BLOCKS.register("turbine_controller_sqc",
            () -> new TurbineControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 8.0f)));
    public static final DeferredBlock<Block> TURBINE_SHELL = BLOCKS.register("turbine_shell_sqc",
            () -> new TurbineShellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(5.0f, 3600000.0f).noLootTable().noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> TURBINE_CONTROLLER_ITEM = ITEMS.register("turbine_controller_sqc",
            () -> new PartBlockItem(TURBINE_CONTROLLER.get(), new Item.Properties()));
    public static final DeferredBlock<Block> SHAFT_COUPLING = BLOCKS.register("shaft_coupling_sqc",
            () -> new com.sqc.nuclearsq.turbine.ShaftCouplingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 8.0f).noOcclusion()));
    public static final DeferredItem<BlockItem> SHAFT_COUPLING_ITEM = ITEMS.registerSimpleBlockItem("shaft_coupling_sqc", SHAFT_COUPLING);

    public static final Map<TurbinePortType, DeferredBlock<TurbinePortBlock>> TURBINE_PORTS = new EnumMap<>(TurbinePortType.class);
    public static final Map<TurbinePortType, DeferredItem<BlockItem>> TURBINE_PORT_ITEMS = new EnumMap<>(TurbinePortType.class);

    static {
        for (TurbinePortType t : TurbinePortType.values()) {
            String name = "turbine_port_" + t.id() + "_sqc";
            DeferredBlock<TurbinePortBlock> b = BLOCKS.register(name, () -> new TurbinePortBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 12.0f), t));
            TURBINE_PORTS.put(t, b);
            TURBINE_PORT_ITEMS.put(t, ITEMS.<BlockItem>register(name, () -> new PartBlockItem(b.get(), new Item.Properties())));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbineControllerBlockEntity>> TURBINE_BE =
            BLOCK_ENTITIES.register("turbine_controller",
                    () -> BlockEntityType.Builder.of(TurbineControllerBlockEntity::new, TURBINE_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbinePortBlockEntity>> TURBINE_PORT_BE =
            BLOCK_ENTITIES.register("turbine_port",
                    () -> BlockEntityType.Builder.of(TurbinePortBlockEntity::new,
                            TURBINE_PORTS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<TurbineMenu>> TURBINE_MENU =
            MENUS.register("turbine", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(TurbineMenu::new));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<DeviceMenu>> DEVICE_MENU =
            MENUS.register("device", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(DeviceMenu::new));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(MultiblockRegistry::registerCapabilities);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(com.sqc.nuclearsq.client.ZoomClient::registerKeys);
            modBus.addListener(com.sqc.nuclearsq.client.VeinMinerClient::registerKeys);
            modBus.addListener(com.sqc.nuclearsq.progression.client.GuideKeysClient::registerKeys);
            modBus.addListener(com.sqc.nuclearsq.nanobot.client.GadgetKeysClient::registerKeys);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TURBINE_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TURBINE_BE.get(), (be, side) -> be.steam());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TURBINE_PORT_BE.get(), (be, side) -> {
            if (be.type() != TurbinePortType.ENERGY) return null;
            var c = be.findController();
            return c == null ? null : c.energy();
        });
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TURBINE_PORT_BE.get(), (be, side) -> {
            var c = be.type() == TurbinePortType.ENERGY ? null : be.findController();
            if (c == null) return null;
            return be.type() == TurbinePortType.STEAM ? c.steam() : c.condensateOut();
        });
    }
}
