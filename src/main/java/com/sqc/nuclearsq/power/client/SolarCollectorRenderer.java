package com.sqc.nuclearsq.power.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.power.SolarCollectorBlock;
import com.sqc.nuclearsq.power.SolarCollectorBlockEntity;
import com.sqc.nuclearsq.power.SolarMath;
import com.sqc.nuclearsq.registry.PowerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public class SolarCollectorRenderer implements BlockEntityRenderer<SolarCollectorBlockEntity> {
    public static final ModelResourceLocation PLATE = model("solar_collector_sqc_plate"), BIG_PLATE = model("solar_collector_sqc_big_plate");

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/" + name));
    }

    public SolarCollectorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PowerRegistry.SOLAR_COLLECTOR_BE.get(), SolarCollectorRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(PLATE);
        event.register(BIG_PLATE);
    }

    @Override
    public void render(SolarCollectorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        int part = state.getValue(SolarCollectorBlock.PART);
        if ((part != 0 && part != SolarCollectorBlock.SINGLE) || be.getLevel() == null) return;
        boolean big = part == 0;
        float ox = big ? 1f : 0.5f, oy = big ? 14f / 16f : 10f / 16f;
        pose.pushPose();
        pose.translate(ox, oy, ox);
        pose.mulPose(Axis.ZP.rotation(SolarMath.track(be.getLevel(), partialTick)));
        pose.translate(-ox, -oy, -ox);
        Minecraft mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().tesselateWithoutAO(be.getLevel(), mc.getModelManager().getModel(big ? BIG_PLATE : PLATE), state,
                be.getBlockPos(), pose, buffers.getBuffer(RenderType.cutout()), false, RandomSource.create(), 42L, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SolarCollectorBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(1, 0, 1);
    }
}
