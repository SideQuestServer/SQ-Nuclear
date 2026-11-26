package com.sqc.nuclearsq.machine.big.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.CreativeSourceBlock;
import com.sqc.nuclearsq.machine.big.BigMachineControllerBlock;
import com.sqc.nuclearsq.machine.big.BigPortBlock;
import com.sqc.nuclearsq.machine.big.BigShellBlock;
import com.sqc.nuclearsq.oil.OilWellBlock;
import com.sqc.nuclearsq.power.RtgBlock;
import com.sqc.nuclearsq.power.SolarCollectorBlock;
import com.sqc.nuclearsq.power.SolarPanelBlock;
import com.sqc.nuclearsq.radiation.DeconShowerBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.reactor.RecombinerBlock;
import com.sqc.nuclearsq.reactor.XenonFilterBlock;
import com.sqc.nuclearsq.robotics.RobotArmBlock;
import com.sqc.nuclearsq.robotics.RodGripperBlock;
import com.sqc.nuclearsq.turbine.TurbineControllerBlock;
import com.sqc.nuclearsq.turbine.TurbinePortBlock;
import com.sqc.nuclearsq.turbine.TurbineShellBlock;
import com.sqc.nuclearsq.registry.BigMachineRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class BigMachineClient {
    private BigMachineClient() {}

    public static final ModelResourceLocation CLAMP_FRAME = model("rod_clamp_frame_sqc"), CLAMP_JAW = model("rod_clamp_jaw_sqc");
    public static final ModelResourceLocation CLAMP_JAW_RACK = model("rod_clamp_jaw_rack_sqc");

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/" + name));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BigMachineRegistry.CONTROLLER_BE.get(), BigMachineRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(CLAMP_FRAME);
        event.register(CLAMP_JAW);
        event.register(CLAMP_JAW_RACK);
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(event.getTarget().getBlockPos());
        if (isMachine(state.getBlock()) || isHiddenPart(state)) {
            event.setCanceled(true);
        }
    }

    private static boolean isHiddenPart(BlockState state) {
        if (state.hasProperty(ReactorProps.FORMED) && state.getValue(ReactorProps.FORMED)) {
            return true;
        }
        return state.getRenderShape() != RenderShape.MODEL
                && NuclearSQ.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    private static boolean isMachine(net.minecraft.world.level.block.Block b) {
        return b instanceof BigShellBlock || b instanceof BigMachineControllerBlock || b instanceof BigPortBlock
                || b instanceof TurbineControllerBlock || b instanceof TurbinePortBlock || b instanceof TurbineShellBlock
                || b instanceof RtgBlock || b instanceof SolarPanelBlock || b instanceof SolarCollectorBlock
                || b instanceof RecombinerBlock || b instanceof XenonFilterBlock || b instanceof OilWellBlock
                || b instanceof DeconShowerBlock || b instanceof CreativeSourceBlock
                || b instanceof RobotArmBlock || b instanceof RodGripperBlock;
    }
}
