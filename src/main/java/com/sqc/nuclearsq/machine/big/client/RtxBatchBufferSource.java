package com.sqc.nuclearsq.machine.big.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.function.IntFunction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class RtxBatchBufferSource implements MultiBufferSource {

    private static final int SOFT_VERTS_PER_BATCH = 3840;
    private static final int MAX_VERTS_PER_BATCH = 4000;
    private static final int MAX_PARTS_PER_BATCH = 960;

    private static final WeakHashMap<RenderType, RenderType[]> BATCH_TYPES = new WeakHashMap<>();
    private static volatile Field compositeStateField;
    private static volatile boolean compositeStateSearched;

    private final MultiBufferSource delegate;
    private final HashMap<RenderType, BatchConsumer> active = new HashMap<>(4);

    private RtxBatchBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    public static boolean isRtxBuffer(MultiBufferSource source) {
        if (source == null) {
            return false;
        }
        if (source instanceof RtxBatchBufferSource) {
            return true;
        }
        return source.getClass().getName().endsWith("RemixVertexConsumerProvider");
    }

    public static MultiBufferSource wrap(MultiBufferSource source) {
        if (source == null || source instanceof RtxBatchBufferSource || !isRtxBuffer(source)) {
            return source;
        }
        return new RtxBatchBufferSource(source);
    }

    private static RenderType.CompositeState extractCompositeState(RenderType rt) {
        Field f = compositeStateField;
        if (!compositeStateSearched) {
            for (Field cand : rt.getClass().getDeclaredFields()) {
                if (cand.getType() == RenderType.CompositeState.class) {
                    cand.setAccessible(true);
                    f = cand;
                    compositeStateField = cand;
                    break;
                }
            }
            if (f != null) {
                compositeStateSearched = true;
            }
        }
        if (f == null || !f.getDeclaringClass().isInstance(rt)) {
            return null;
        }
        try {
            return (RenderType.CompositeState) f.get(rt);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (renderType.mode() != VertexFormat.Mode.QUADS
                || extractCompositeState(renderType) == null) {
            return delegate.getBuffer(renderType);
        }
        BatchConsumer consumer = active.get(renderType);
        if (consumer == null) {
            consumer = new BatchConsumer(idx -> delegate.getBuffer(batchRenderType(renderType, idx)));
            active.put(renderType, consumer);
        } else {
            consumer.refresh();
        }
        return consumer;
    }

    static RenderType batchRenderType(RenderType base, int index) {
        if (index == 0) {
            return base;
        }
        synchronized (BATCH_TYPES) {
            RenderType[] arr = BATCH_TYPES.get(base);
            if (arr == null || index >= arr.length) {
                int newLen = Math.max(index + 1, arr == null ? 8 : arr.length * 2);
                arr = arr == null ? new RenderType[newLen] : Arrays.copyOf(arr, newLen);
                BATCH_TYPES.put(base, arr);
            }
            RenderType rt = arr[index];
            if (rt == null) {
                RenderType.CompositeState state = extractCompositeState(base);
                if (state == null) {
                    return base;
                }
                rt = RenderType.create(
                        "nuclearsq_rtxbatch_" + index,
                        base.format(),
                        base.mode(),
                        base.bufferSize(),
                        base.affectsCrumbling(),
                        base.sortOnUpload(),
                        state);
                arr[index] = rt;
            }
            return rt;
        }
    }

    private static final class BatchConsumer implements VertexConsumer {
        private final IntFunction<VertexConsumer> batchSupplier;
        private int batchIndex;
        private VertexConsumer current;
        private int vertsInBatch;
        private int partsInBatch;
        private int quadVertIndex;
        private final Matrix4f lastPose = new Matrix4f();
        private boolean hasLastPose;
        private boolean lastPoseWasNull;

        BatchConsumer(IntFunction<VertexConsumer> batchSupplier) {
            this.batchSupplier = batchSupplier;
            this.current = batchSupplier.apply(0);
        }

        void refresh() {
            current = batchSupplier.apply(batchIndex);
        }

        private void beforeQuadStart(Matrix4f pose, int vertDelta) {
            boolean poseChanged;
            if (pose == null) {
                poseChanged = !hasLastPose || !lastPoseWasNull;
            } else {
                poseChanged = !hasLastPose || lastPoseWasNull || !lastPose.equals(pose);
            }
            int nextParts = partsInBatch + (poseChanged ? 1 : 0);
            if (vertsInBatch + vertDelta > MAX_VERTS_PER_BATCH
                    || (poseChanged && vertsInBatch >= SOFT_VERTS_PER_BATCH)
                    || nextParts >= MAX_PARTS_PER_BATCH) {
                batchIndex++;
                current = batchSupplier.apply(batchIndex);
                vertsInBatch = 0;
                partsInBatch = 0;
                hasLastPose = false;
                lastPoseWasNull = false;
                poseChanged = true;
            }
            if (poseChanged) {
                partsInBatch++;
                hasLastPose = true;
                if (pose != null) {
                    lastPose.set(pose);
                    lastPoseWasNull = false;
                } else {
                    lastPoseWasNull = true;
                }
            }
        }

        private void advanceVertex(Matrix4f pose) {
            if (quadVertIndex == 0) {
                beforeQuadStart(pose, 4);
            }
            vertsInBatch++;
            quadVertIndex = (quadVertIndex + 1) & 3;
        }

        private void advanceBulkQuad(Matrix4f pose) {
            quadVertIndex = 0;
            beforeQuadStart(pose, 4);
            vertsInBatch += 4;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            advanceVertex(null);
            current.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(
                float x, float y, float z,
                int color, float u, float v,
                int overlay, int light,
                float nx, float ny, float nz) {
            advanceVertex(null);
            current.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(Vector3f pos) {
            advanceVertex(null);
            current.addVertex(pos);
            return this;
        }

        @Override
        public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
            advanceVertex(pose.pose());
            current.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(PoseStack.Pose pose, Vector3f pos) {
            advanceVertex(pose.pose());
            current.addVertex(pose, pos);
            return this;
        }

        @Override
        public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
            advanceVertex(pose);
            current.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            current.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(float red, float green, float blue, float alpha) {
            current.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            current.setColor(argb);
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int alpha) {
            current.setWhiteAlpha(alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int overlay) {
            current.setOverlay(overlay);
            return this;
        }

        @Override
        public VertexConsumer setLight(int light) {
            current.setLight(light);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            current.setNormal(nx, ny, nz);
            return this;
        }

        @Override
        public VertexConsumer setNormal(PoseStack.Pose pose, float nx, float ny, float nz) {
            current.setNormal(pose, nx, ny, nz);
            return this;
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose, BakedQuad quad,
                float red, float green, float blue, float alpha,
                int light, int overlay) {
            advanceBulkQuad(pose.pose());
            current.putBulkData(pose, quad, red, green, blue, alpha, light, overlay);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose, BakedQuad quad,
                float red, float green, float blue, float alpha,
                int light, int overlay, boolean readExistingColor) {
            advanceBulkQuad(pose.pose());
            current.putBulkData(pose, quad, red, green, blue, alpha, light, overlay, readExistingColor);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                float red, float green, float blue, float alpha,
                int[] lights, int overlay, boolean colorize) {
            advanceBulkQuad(pose.pose());
            current.putBulkData(pose, quad, brightness, red, green, blue, alpha, lights, overlay, colorize);
        }
    }
}
