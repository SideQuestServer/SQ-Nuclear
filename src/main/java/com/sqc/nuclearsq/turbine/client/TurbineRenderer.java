package com.sqc.nuclearsq.turbine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sqc.nuclearsq.machine.big.client.BakedMachines;
import com.sqc.nuclearsq.machine.big.client.RtxBatchBufferSource;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TurbineRenderer extends GeoBlockRenderer<TurbineControllerBlockEntity> {
    private static final Direction[] FACING = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public TurbineRenderer(BlockEntityRendererProvider.Context context) {
        super(new TurbineGeoModel());
        addRenderLayer(new TurbineGlowLayer(this));
    }

    @Override
    public void renderCube(PoseStack poseStack, software.bernie.geckolib.cache.object.GeoCube cube, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                           int packedLight, int packedOverlay, int colour) {
        if (com.sqc.nuclearsq.machine.big.client.GlowCubes.skip(cube)) return;
        super.renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void render(TurbineControllerBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        bufferSource = RtxBatchBufferSource.wrap(bufferSource);
        int rot = be.formedRot();
        if (rot < 0 || be.getLevel() == null) return;
        if (!com.sqc.nuclearsq.client.OcclusionCull.visibleBig(be.getLevel(), be.getBlockPos(), getRenderBoundingBox(be))) return;
        int[] front = MultiblockStructure.rotate(0, -1, rot);
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().offset(front[0], 0, front[1]));
        BakedMachines.Dyn dyn = null;
        if (BakedMachines.active()) {
            var geo = getGeoModel();
            dyn = BakedMachines.dyn("turbine", geo.getBakedModel(geo.getModelResource(be, this)), geo.getAnimationResource(be));
        }
        final BakedMachines.Dyn d = dyn;
        RenderType glow = d == null ? null : TurbineGlowLayer.current(be);
        if (d != null && BakedMachines.draw(be.getLevel(), be.getBlockPos(), rot, "turbine", poseStack, bufferSource, light, glow,
                (pose, buffers) -> {
                    d.hideDynamic();
                    try {
                        super.render(be, partialTick, pose, buffers, light, packedOverlay);
                    } finally {
                        d.reset();
                    }
                })) {
            if (!d.any()) return;
            d.showOnlyDynamic();
            try {
                super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
            } finally {
                d.reset();
            }
            return;
        }
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
    }

    @Override
    protected Direction getFacing(TurbineControllerBlockEntity be) {
        int rot = be.formedRot();
        return FACING[Math.max(rot, 0)];
    }

    @Override
    public AABB getRenderBoundingBox(TurbineControllerBlockEntity be) {
        int rot = Math.max(be.formedRot(), 0);
        MultiblockStructure s = MultiblockStructure.TURBINE;
        BlockPos a = s.world(be.getBlockPos(), rot, 0, 0, 0), b = s.world(be.getBlockPos(), rot, s.width() - 1, s.height() - 1, s.depth() - 1);
        return AABB.encapsulatingFullBlocks(a, b);
    }

    @Override
    public boolean shouldRenderOffScreen(TurbineControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
