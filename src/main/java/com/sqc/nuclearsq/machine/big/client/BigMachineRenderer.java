package com.sqc.nuclearsq.machine.big.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.RodShaft;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.ReactorProps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class BigMachineRenderer extends GeoBlockRenderer<BigMachineBlockEntity> {
    private static final Direction[] FACING = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final float RACK_GLOW_CLIP = 0.305f;

    public BigMachineRenderer(BlockEntityRendererProvider.Context context) {
        super(new BigMachineGeoModel());
        addRenderLayer(new BigMachineGlowLayer(this));
    }

    @Override
    public void renderCube(PoseStack poseStack, software.bernie.geckolib.cache.object.GeoCube cube, VertexConsumer buffer, int packedLight,
                           int packedOverlay, int colour) {
        if (com.sqc.nuclearsq.machine.big.client.GlowCubes.skip(cube)) return;
        super.renderCube(poseStack, cube, faceLit != null && packedLight != net.minecraft.client.renderer.LightTexture.FULL_BRIGHT
                ? faceLit.to(buffer) : buffer, packedLight, packedOverlay, colour);
    }

    private FaceLitBuffer faceLit;

    @Override
    public void render(BigMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        long t0 = com.sqc.nuclearsq.client.PerfProbe.start();
        try {
            renderMachine(be, partialTick, poseStack, RtxBatchBufferSource.wrap(bufferSource), packedLight, packedOverlay);
        } finally {
            com.sqc.nuclearsq.client.PerfProbe.end(t0);
        }
    }

    private static final double DYNAMIC_MAX_DIST = 224;

    private void renderMachine(BigMachineBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (com.sqc.nuclearsq.NuclearClientConfig.noShadow()) return;
        int rot = be.formedRot();
        if (rot < 0 || be.getLevel() == null) return;
        long tp = com.sqc.nuclearsq.client.PerfProbe.start();
        final boolean sub = be.substation() != null;
        final AABB box = getRenderBoundingBox(be);
        final boolean vis = sub || com.sqc.nuclearsq.client.OcclusionCull.visible(be.getLevel(), be.getBlockPos(), box);
        if (!sub) {
            long gt = be.getLevel().getGameTime();
            if (be.occTick != gt || be.occVal != vis) {
                be.occTick = gt;
                be.occVal = vis;
                ChunkMachines.occluded(be, !vis);
            }
        }
        if (ChunkMachines.idle(be)) {
            if (com.sqc.nuclearsq.client.PerfProbe.on) {
                com.sqc.nuclearsq.client.PerfProbe.chunkedIdle++;
                com.sqc.nuclearsq.client.PerfProbe.pre(tp);
            }
            return;
        }
        if (be.machine() == BigMachine.CONTAINMENT) com.sqc.nuclearsq.client.ContainmentCull.register(be, getRenderBoundingBox(be));
        else if (com.sqc.nuclearsq.client.ContainmentCull.hidden(be)) {
            if (com.sqc.nuclearsq.client.PerfProbe.on) com.sqc.nuclearsq.client.PerfProbe.hullCulled++;
            return;
        }
        if (!sub && !(vis && com.sqc.nuclearsq.client.OcclusionCull.inFrustum(box))) {
            if (com.sqc.nuclearsq.client.PerfProbe.on) {
                com.sqc.nuclearsq.client.PerfProbe.viewCulled++;
                com.sqc.nuclearsq.client.PerfProbe.pre(tp);
            }
            return;
        }
        if (com.sqc.nuclearsq.client.PerfProbe.on) {
            com.sqc.nuclearsq.client.PerfProbe.drawn++;
            com.sqc.nuclearsq.client.PerfProbe.pre(tp);
        }
        com.sqc.nuclearsq.client.PerfProbe.noteDrawn(be, ChunkMachines.why(be));
        BigMachine m = be.machine();
        BlockPos pos = be.getBlockPos(), anchor = m.structure.world(pos, rot, m.ax, m.ay, m.az);
        int light = LevelRenderer.getLightColor(be.getLevel(), anchor.above(m.h));
        if ((m == BigMachine.CONTAINMENT || m == BigMachine.CONTROL_ROD_DRIVE) && !com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass()) {
            faceLit = new FaceLitBuffer(be.getLevel(), net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        }
        try {
            renderModel(be, rot, partialTick, poseStack, bufferSource, light, packedOverlay, pos, anchor);
        } finally {
            faceLit = null;
        }
        if (m == BigMachine.PARTICLE_ASSEMBLER) {
            renderParticleHalos(be, partialTick, poseStack, bufferSource);
            renderParticleResult(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        }
        if (be.exo() != null && !be.exo().piece().getItem(0).isEmpty()) renderExoPiece(be, rot, anchor.subtract(pos), poseStack, bufferSource, light, packedOverlay);
        if (m == BigMachine.EXO_STATION && !com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass()) renderGlass(be, partialTick, poseStack, bufferSource, light, packedOverlay, pos, anchor);
        if (m == BigMachine.CONTROL_DESK && com.sqc.nuclearsq.NuclearClientConfig.cameraDistSq(pos) <= com.sqc.nuclearsq.client.PanelCull.MAX_DIST * com.sqc.nuclearsq.client.PanelCull.MAX_DIST) com.sqc.nuclearsq.data.client.DeskScreens.render(be, rot, partialTick, poseStack, bufferSource);
        if (be.substation() != null) com.sqc.nuclearsq.transport.client.GridWireRenderer.renderLinks(be.getLevel(), be.substation(), pos, poseStack, bufferSource);
        for (RodShaft s : be.shafts()) {
            renderRod(be, s, partialTick, poseStack, bufferSource, light, packedOverlay);
            renderClamp(be, s, partialTick, poseStack, bufferSource, light, packedOverlay);
        }
    }

    private void renderModel(BigMachineBlockEntity be, int rot, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                             int light, int packedOverlay, BlockPos pos, BlockPos anchor) {
        BakedMachines.Dyn dyn = null;
        if (BakedMachines.eligible(be) && BakedMachines.active()) {
            var geo = getGeoModel();
            dyn = BakedMachines.dyn(be.machine(), geo.getBakedModel(geo.getModelResource(be, this)), geo.getAnimationResource(be));
        }
        final BakedMachines.Dyn d = dyn;
        int gf = d == null ? -1 : BigMachineGlowLayer.glowFrame(be);
        RenderType glow = gf < 0 || !com.sqc.nuclearsq.NuclearClientConfig.inGlowRange(pos) ? null : BigMachineGlowLayer.type(be.machine(), gf);
        BakedMachines.Painter painter = d == null ? null : (pose, buffers) -> {
            pose.translate(anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
            d.hideDynamic();
            try {
                super.render(be, partialTick, pose, buffers, light, packedOverlay);
            } finally {
                d.reset();
            }
        };
        boolean inChunk = d != null && ChunkMachines.update(be, getGeoModel().getTextureResource(be, this), d.any(), painter);
        if (inChunk) {
            if (com.sqc.nuclearsq.client.PerfProbe.on) com.sqc.nuclearsq.client.PerfProbe.chunked++;
            if (com.sqc.nuclearsq.NuclearClientConfig.cameraDistSq(pos) > DYNAMIC_MAX_DIST * DYNAMIC_MAX_DIST) return;
            if (glow != null) BakedMachines.draw(be.getLevel(), pos, rot, be.machine(), poseStack, bufferSource, light, glow, painter, true);
            renderDynamic(d, be, partialTick, poseStack, bufferSource, light, packedOverlay, pos, anchor, glow);
            return;
        }
        if (d != null && BakedMachines.draw(be.getLevel(), pos, rot, be.machine(), poseStack, bufferSource, light, glow, painter)) {
            renderDynamic(d, be, partialTick, poseStack, bufferSource, light, packedOverlay, pos, anchor, glow);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        poseStack.popPose();
    }

    private void renderDynamic(BakedMachines.Dyn d, BigMachineBlockEntity be, float partialTick, PoseStack poseStack,
                               MultiBufferSource bufferSource, int light, int packedOverlay, BlockPos pos, BlockPos anchor, RenderType glow) {
        if (!d.any()) return;
        boolean idleCrafter = be.machine().isAutocrafter() && !be.isRunning() && be.getLevel() != null;
        if (idleCrafter || (be.rack() != null && be.rack().codes() != null)) {
            long var = idleCrafter ? 2L : rackVariant(be);
            BakedMachines.Painter dynPainter = (pose, buffers) -> {
                pose.translate(anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
                d.showOnlyDynamic();
                try {
                    if (getGeoModel() instanceof BigMachineGeoModel code) code.applyCode(be, 0f);
                    super.render(be, 0f, pose, buffers, light, packedOverlay);
                } finally {
                    d.reset();
                }
            };
            long t0 = com.sqc.nuclearsq.client.PerfProbe.start();
            boolean drawn = BakedMachines.draw(be.getLevel(), pos, be.formedRot(), be.machine(), poseStack, bufferSource, light, glow, dynPainter, false, var);
            if (drawn) {
                if (com.sqc.nuclearsq.client.PerfProbe.on) com.sqc.nuclearsq.client.PerfProbe.dynamic(be.machine(), System.nanoTime() - t0);
                return;
            }
        }
        poseStack.pushPose();
        poseStack.translate(anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
        d.showOnlyDynamic();
        long t0 = com.sqc.nuclearsq.client.PerfProbe.start();
        try {
            if (getGeoModel() instanceof BigMachineGeoModel code) code.applyCode(be, partialTick);
            super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        } finally {
            if (com.sqc.nuclearsq.client.PerfProbe.on) com.sqc.nuclearsq.client.PerfProbe.dynamic(be.machine(), System.nanoTime() - t0);
            d.reset();
            poseStack.popPose();
        }
    }

    private static long rackVariant(BigMachineBlockEntity be) {
        int[] codes = be.rack().codes();
        boolean on = be.isRunning();
        long t = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        long h = on ? 0x9E3779B97F4A7C15L : 0x1234567L;
        for (int k = 0; k < codes.length; k++) {
            h = (h ^ codes[k]) * 0x100000001B3L;
            if (on && com.sqc.nuclearsq.storage.ServerRack.busy(codes[k])) h = (h ^ (((t + k) & 2) + 1)) * 0x100000001B3L;
        }
        return h == 0 ? 1 : h;
    }

    private static final RenderType HALO = RenderType.entityTranslucentEmissive(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "textures/block/particle_assembler_halo.png"));

    private void renderParticleHalos(BigMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        var p = com.sqc.nuclearsq.machine.big.ParticleAssemblerFx.pose(be, partialTick);
        if (p.flash() < 0.02f) return;
        var mid = com.sqc.nuclearsq.machine.big.ParticleAssemblerFx.center(be);
        BlockPos pos = be.getBlockPos();
        VertexConsumer vc = buffers.getBuffer(HALO);
        halo(pose, vc, mid.x - pos.getX(), mid.y - pos.getY(), mid.z - pos.getZ(), 0.55f * p.flash(), 0x9C8AF8, 1f);
    }

    private static void halo(PoseStack pose, VertexConsumer vc, double x, double y, double z, float r, int rgb, float alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose last = pose.last();
        int cr = rgb >> 16 & 255, cg = rgb >> 8 & 255, cb = rgb & 255, ca = Math.round(alpha * 255);
        float[][] corners = {{-r, -r, 0, 1}, {r, -r, 1, 1}, {r, r, 1, 0}, {-r, r, 0, 0}};
        for (float[] k : corners) {
            vc.addVertex(last, k[0], k[1], 0f).setColor(cr, cg, cb, ca).setUv(k[2], k[3])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f);
        }
        pose.popPose();
    }

    private static final RenderType EXO_GLASS = RenderType.create("nuclearsq_exo_glass", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "textures/block/" + BigMachine.EXO_STATION.id + ".png"), false, false))
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                    .setLightmapState(net.minecraft.client.renderer.RenderStateShard.LIGHTMAP)
                    .setOverlayState(net.minecraft.client.renderer.RenderStateShard.OVERLAY)
                    .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final RenderType EXO_GLASS_SHADER = RenderType.entityTranslucent(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "textures/block/" + BigMachine.EXO_STATION.id + ".png"));

    private boolean glassPass;

    private void renderGlass(BigMachineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                             int light, int packedOverlay, BlockPos pos, BlockPos anchor) {
        poseStack.pushPose();
        poseStack.translate(anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
        glassPass = true;
        try {
            super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        } finally {
            glassPass = false;
            poseStack.popPose();
        }
    }

    @Override
    public void renderRecursively(PoseStack poseStack, BigMachineBlockEntity be, software.bernie.geckolib.cache.object.GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {
        if (be.machine() == BigMachine.EXO_STATION && bone.getParent() != null && glassPass != "glass".equals(bone.getName())) return;
        super.renderRecursively(poseStack, be, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private void renderExoPiece(BigMachineBlockEntity be, int rot, BlockPos off, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(off.getX() + 0.5, off.getY(), off.getZ() + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-90f * rot));
        pose.translate(0.5, 1.05, 0.5);
        pose.scale(0.85f, 0.85f, 0.85f);
        Minecraft.getInstance().getItemRenderer().renderStatic(be.exo().piece().getItem(0), net.minecraft.world.item.ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }

    private void renderParticleResult(BigMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var fx = be.particleFx();
        var c = be.crafter();
        if (fx == null || c == null || fx.shown() <= 0 || c.lastResult().isEmpty()) return;
        float age = com.sqc.nuclearsq.machine.big.ParticleAssemblerFx.SHOW_TICKS - fx.shown() + partialTick;
        var mid = com.sqc.nuclearsq.machine.big.ParticleAssemblerFx.center(be);
        BlockPos pos = be.getBlockPos();
        float grow = Math.min(1f, age / 6f), fade = Math.min(1f, fx.shown() / 8f);
        pose.pushPose();
        pose.translate(mid.x - pos.getX(), mid.y - pos.getY() - 0.15f + age * 0.006f, mid.z - pos.getZ());
        pose.mulPose(Axis.YP.rotationDegrees(age * 9f));
        float sc = 0.9f * grow * fade;
        pose.scale(sc, sc, sc);
        Minecraft.getInstance().getItemRenderer().renderStatic(c.lastResult(), net.minecraft.world.item.ItemDisplayContext.GROUND,
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }

    private void renderClamp(BigMachineBlockEntity be, RodShaft s, float partialTick, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel frame = mc.getModelManager().getModel(BigMachineClient.CLAMP_FRAME);
        BakedModel jaw = mc.getModelManager().getModel(BigMachineClient.CLAMP_JAW_RACK);
        ModelBlockRenderer mr = mc.getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        BlockPos base = be.rodBase(s), pos = be.getBlockPos();
        pose.pushPose();
        pose.translate(base.getX() - pos.getX(), base.getY() - pos.getY() + be.inletY() - 0.25f + 0.004f, base.getZ() - pos.getZ());
        mr.renderModel(pose.last(), vc, null, frame, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
        float out = s.open(partialTick) * 0.5f / 16f;
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.translate(0.5f, 0f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(90f * i));
            pose.translate(-0.5f, 0f, -0.5f - out);
            mr.renderModel(pose.last(), vc, null, jaw, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
        }
        pose.popPose();
    }

    private void renderRod(BigMachineBlockEntity be, RodShaft s, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
        int headY = s.headY();
        if (headY < 0 || !s.isDriven(be.isClient())) return;
        BlockPos base = be.rodBase(s), pos = be.getBlockPos();
        BlockState st = be.getLevel().getBlockState(base.above(headY));
        if (!st.hasProperty(ReactorProps.DRIVEN)) return;
        st = st.setValue(ReactorProps.DRIVEN, false);
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(st);
        float shown = s.shown(partialTick);
        boolean rack = be.machine() == BigMachine.SPENT_FUEL_RACK;
        pose.pushPose();
        pose.translate(base.getX() - pos.getX(), base.getY() - pos.getY() + headY - shown, base.getZ() - pos.getZ());
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        if (rack) {
            float top = be.inletY() - 0.25f - (headY - shown);
            ClippedModel.render(pose.last(), vc, st, model, top, light, overlay);
        } else {
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, st, model,
                    1f, 1f, 1f, light, overlay, ModelData.EMPTY, null);
        }
        float clip = rack ? RACK_GLOW_CLIP : 0f;
        com.sqc.nuclearsq.reactor.client.RodGlow.rod(pose, buffers, be.getLevel(), base.above(headY), shown, be.cherenkov(), clip);
        pose.popPose();
    }

    @Override
    public RenderType getRenderType(BigMachineBlockEntity be, net.minecraft.resources.ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return glassPass ? (com.sqc.nuclearsq.compat.iris.IrisCompat.shaderPackInUse() ? EXO_GLASS_SHADER : EXO_GLASS) : super.getRenderType(be, texture, buffers, partialTick);
    }

    @Override
    protected Direction getFacing(BigMachineBlockEntity be) {
        return FACING[Math.max(be.formedRot(), 0)];
    }

    @Override
    public AABB getRenderBoundingBox(BigMachineBlockEntity be) {
        int rot = Math.max(be.formedRot(), 0);
        BigMachine m = be.machine();
        boolean fixed = m != BigMachine.CONTROL_ROD_DRIVE && m != BigMachine.SPENT_FUEL_RACK;
        if (fixed && be.renderBoxCache != null && be.renderBoxRot == rot) return be.renderBoxCache;
        AABB made = computeRenderBox(be, rot, m);
        if (fixed) {
            be.renderBoxCache = made;
            be.renderBoxRot = rot;
        }
        return made;
    }

    private AABB computeRenderBox(BigMachineBlockEntity be, int rot, BigMachine m) {
        MultiblockStructure s = m.structure;
        AABB box = AABB.encapsulatingFullBlocks(s.world(be.getBlockPos(), rot, 0, 0, 0),
                s.world(be.getBlockPos(), rot, m.w - 1, m.h - 1, m.d - 1));
        return m == BigMachine.CONTROL_ROD_DRIVE || m == BigMachine.SPENT_FUEL_RACK
                ? box.setMaxY(Math.max(box.maxY, box.minY + m.ay + be.maxHeadY() + 1)) : box;
    }

    @Override
    public boolean shouldRenderOffScreen(BigMachineBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.sqc.nuclearsq.NuclearClientConfig.viewDistance();
    }
}
