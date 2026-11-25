package com.sqc.nuclearsq.machine.big.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class BakedMachines {
    private BakedMachines() {}

    public interface Painter {
        void paint(PoseStack pose, MultiBufferSource buffers);
    }

    private record Part(RenderType type, VertexBuffer buffer, Rec rec, boolean glow) {}

    private static final class Entry {
        final List<Part> parts = new ArrayList<>();
        boolean failed, replay;
        int rot, light, vertices;
        Object kind;
        long tick, used;
        Key key;

        void close() {
            for (Part p : parts) if (p.buffer() != null) p.buffer().close();
            parts.clear();
        }
    }

    private static final int MAX_ENTRIES = 1024, MAX_VERTICES = 2_500_000;
    private record Key(Object kind, int rot, boolean replay, int light, long variant) {}

    private static final java.util.HashMap<Key, Entry> CACHE = new java.util.HashMap<>();
    private static Level cacheLevel;
    private static long lastBakeTick = -1;
    private static final Matrix4f MV = new Matrix4f();
    private static net.minecraft.client.renderer.culling.Frustum viewFrustum;
    private static double camX, camY, camZ;

    public static boolean active() {
        return NuclearClientConfig.bakedMachines();
    }

    static boolean eligible(BigMachineBlockEntity be) {
        return be.machine() != BigMachine.CONTAINMENT && be.machine() != BigMachine.CONTROL_ROD_DRIVE;
    }

    private static Dyn activeDyn;
    private static int activeMode;

    public static void reapply() {
        if (activeDyn == null) return;
        if (activeMode == 1) activeDyn.hideDynamic();
        else if (activeMode == 2) activeDyn.hideStatic();
    }

    public static final class Dyn {
        final BakedGeoModel model;
        final List<GeoBone> all = new ArrayList<>();
        final java.util.Set<GeoBone> hasDyn = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        final List<GeoBone> roots = new ArrayList<>();
        final java.util.Set<GeoBone> dyn = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        Dyn(BakedGeoModel model, java.util.Set<String> animated) {
            this.model = model;
            for (GeoBone b : model.topLevelBones()) walk(b, animated, false);
            for (GeoBone b : model.topLevelBones()) mark(b);
        }

        private boolean mark(GeoBone b) {
            boolean any = dyn.contains(b);
            for (GeoBone c : b.getChildBones()) any |= mark(c);
            if (any) hasDyn.add(b);
            return any;
        }

        private void walk(GeoBone b, java.util.Set<String> animated, boolean inDyn) {
            all.add(b);
            boolean d = inDyn || animated.contains(b.getName()) || codeDriven(b.getName());
            if (d) dyn.add(b);
            if (d && !inDyn) roots.add(b);
            for (GeoBone c : b.getChildBones()) walk(c, animated, d);
        }

        public boolean any() {
            return !roots.isEmpty();
        }

        public void hideDynamic() {
            activeDyn = this;
            activeMode = 1;
            for (GeoBone b : roots) b.setHidden(true);
        }

        void hideStatic() {
            for (GeoBone b : all) if (!dyn.contains(b)) { b.setHidden(true); b.setChildrenHidden(!hasDyn.contains(b)); }
        }

        public void showOnlyDynamic() {
            activeDyn = this;
            activeMode = 2;
            for (GeoBone b : all) {
                if (dyn.contains(b)) b.setHidden(false);
                else { b.setHidden(true); b.setChildrenHidden(!hasDyn.contains(b)); }
            }
        }

        public void reset() {
            activeDyn = null;
            activeMode = 0;
            for (GeoBone b : all) { b.setHidden(false); b.setChildrenHidden(false); }
        }

        private static boolean codeDriven(String n) {
            return n.startsWith("lamp_") || n.startsWith("arm_") || n.startsWith("grip_") || n.startsWith("ball_")
                    || n.startsWith("lift_") || n.startsWith("part_") || n.startsWith("belt_roller_")
                    || n.startsWith("disk_") || n.startsWith("tier_") || n.startsWith("led_") || n.startsWith("act_")
                    || n.startsWith("tracker_") || n.equals("flash") || n.equals("ram") || n.equals("press_flash") || n.equals("product");
        }
    }

    private static final Map<Object, Dyn> DYN = new java.util.HashMap<>();

    public static Dyn dyn(Object m, BakedGeoModel model, ResourceLocation animations) {
        Dyn d = DYN.get(m);
        if (d != null && d.model == model) return d;
        java.util.Set<String> animated = new java.util.HashSet<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(animations);
            if (res.isPresent()) {
                try (var r = res.get().openAsReader()) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    if (root.has("animations")) {
                        for (var a : root.getAsJsonObject("animations").entrySet()) {
                            JsonObject bones = a.getValue().getAsJsonObject().has("bones")
                                    ? a.getValue().getAsJsonObject().getAsJsonObject("bones") : null;
                            if (bones != null) animated.addAll(bones.keySet());
                        }
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            NuclearSQ.LOGGER.warn("Animationen von {} nicht lesbar, Maschine wird nicht gebacken", m, e);
            return null;
        }
        d = new Dyn(model, animated);
        DYN.put(m, d);
        return d;
    }

    public static boolean draw(Level level, BlockPos pos, int rot, Object kind, PoseStack pose, MultiBufferSource buffers, int light,
                        RenderType glow, Painter painter) {
        return draw(level, pos, rot, kind, pose, buffers, light, glow, painter, false);
    }

    public static boolean draw(Level level, BlockPos pos, int rot, Object kind, PoseStack pose, MultiBufferSource buffers, int light,
                        RenderType glow, Painter painter, boolean glowOnly) {
        return draw(level, pos, rot, kind, pose, buffers, light, glow, painter, glowOnly, 0L);
    }

    public static boolean draw(Level level, BlockPos pos, int rot, Object kind, PoseStack pose, MultiBufferSource buffers, int light,
                        RenderType glow, Painter painter, boolean glowOnly, long variant) {
        if (level == null || !active()) return false;
        if (level != cacheLevel) {
            clear();
            cacheLevel = level;
        }
        long now = level.getGameTime();
        boolean replay = com.sqc.nuclearsq.compat.iris.IrisCompat.shaderPackInUse() || RtxBatchBufferSource.isRtxBuffer(buffers);
        Key key = new Key(kind, rot, replay, replay ? 0 : light, variant);
        Entry e = CACHE.get(key);
        if (e == null) {
            if (lastBakeTick == now) return false;
            lastBakeTick = now;
            e = bake(painter, rot, light, kind, now, replay);
            if (com.sqc.nuclearsq.client.PerfProbe.on) com.sqc.nuclearsq.client.PerfProbe.bakes++;
            e.key = key;
            trim(e.vertices);
            CACHE.put(key, e);
            com.sqc.nuclearsq.client.PerfProbe.cacheSize(CACHE.size());
        }
        e.used = now;
        if (e.failed || e.parts.isEmpty()) return false;
        boolean shadow = com.sqc.nuclearsq.compat.iris.IrisCompat.shadowPass();
        if (!shadow) {
            var mc = Minecraft.getInstance();
            try {
                viewFrustum = mc.levelRenderer.getFrustum();
            } catch (RuntimeException ex) {
                viewFrustum = null;
            }
            var cam = mc.gameRenderer.getMainCamera().getPosition();
            camX = cam.x;
            camY = cam.y;
            camZ = cam.z;
        }
        if (shadow) {
            double d = NuclearClientConfig.shadowDistance();
            if (NuclearClientConfig.cameraDistSq(pos) > d * d) return true;
            glow = null;
        }
        for (Part p : e.parts) {
            RenderType type = p.type();
            if (glowOnly && !p.glow()) continue;
            if (p.glow()) {
                if (glow == null) continue;
                type = glow;
            }
            if (com.sqc.nuclearsq.client.PerfProbe.on) {
                if (p.rec() != null) com.sqc.nuclearsq.client.PerfProbe.replayVertices += p.rec().count;
                else com.sqc.nuclearsq.client.PerfProbe.bufferDraws++;
            }
            if (p.rec() != null) {
                float push = glowOnly && p.glow() ? Math.min(0.03f, 0.003f + 0.0002f * (float) Math.sqrt(NuclearClientConfig.cameraDistSq(pos))) : 0f;
                int sent = p.rec().replay(buffers.getBuffer(type), pose.last(), !shadow, e.light, light, push);
                if (com.sqc.nuclearsq.client.PerfProbe.on) {
                    com.sqc.nuclearsq.client.PerfProbe.sentVertices += sent;
                    com.sqc.nuclearsq.client.PerfProbe.kind(kind, sent);
                }
            } else drawPart(type, p.buffer(), pose);
        }
        return true;
    }

    private static Entry bake(Painter painter, int rot, int light, Object machine, long now, boolean replay) {
        Entry e = new Entry();
        e.rot = rot;
        e.light = light;
        e.kind = machine;
        e.tick = now;
        e.replay = replay;
        if (replay) {
            bakeReplay(e, painter, machine);
            return e;
        }
        Capture cap = new Capture();
        BigMachineGlowLayer.capture = true;
        try {
            painter.paint(new PoseStack(), cap);
            for (Map.Entry<RenderType, BufferBuilder> en : cap.builders.entrySet()) {
                MeshData md = en.getValue().build();
                if (md == null) continue;
                e.vertices += md.drawState().vertexCount();
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(md);
                VertexBuffer.unbind();
                e.parts.add(new Part(en.getKey(), vb, null, isGlow(en.getKey())));
            }
        } catch (RuntimeException | LinkageError ex) {
            NuclearSQ.LOGGER.warn("Maschine {} nicht gebacken, zeichne wie bisher", machine, ex);
            e.close();
            e.failed = true;
        } finally {
            BigMachineGlowLayer.capture = false;
            cap.close();
        }
        return e;
    }

    private static void trim(int incoming) {
        long total = incoming;
        for (Entry x : CACHE.values()) total += x.vertices;
        if (CACHE.size() < MAX_ENTRIES && total <= MAX_VERTICES) return;
        List<Key> keys = new ArrayList<>(CACHE.keySet());
        keys.sort((a, b) -> Long.compare(CACHE.get(a).used, CACHE.get(b).used));
        for (Key k : keys) {
            if (CACHE.size() < MAX_ENTRIES * 0.9 && total <= MAX_VERTICES * 0.9) break;
            Entry x = CACHE.remove(k);
            total -= x.vertices;
            x.close();
        }
    }

    static boolean isGlow(RenderType t) {
        return BigMachineGlowLayer.isGlow(t) || com.sqc.nuclearsq.turbine.client.TurbineGlowLayer.isGlow(t);
    }

    private static void bakeReplay(Entry e, Painter painter, Object machine) {
        try {
            for (Map.Entry<RenderType, Rec> en : record(painter).entrySet()) {
                e.vertices += en.getValue().count;
                e.parts.add(new Part(en.getKey(), null, en.getValue(), isGlow(en.getKey())));
            }
        } catch (RuntimeException ex) {
            NuclearSQ.LOGGER.warn("Maschine {} nicht aufgezeichnet, zeichne wie bisher", machine, ex);
            e.close();
            e.failed = true;
        }
    }

    static Map<RenderType, Rec> record(Painter painter) {
        Map<RenderType, Rec> recs = new IdentityHashMap<>();
        BigMachineGlowLayer.capture = true;
        try {
            painter.paint(new PoseStack(), type -> recs.computeIfAbsent(type, t -> new Rec()));
            for (Map.Entry<RenderType, Rec> en : recs.entrySet()) {
                if (en.getValue().count > 0 && !en.getKey().sortOnUpload() && !isGlow(en.getKey())) {
                    en.getValue().dropInternal();
                    en.getValue().snapNearPlanes();
                    en.getValue().dropOverlaps();
                }
                if (en.getValue().count > 0) en.getValue().prepare();
            }
        } finally {
            BigMachineGlowLayer.capture = false;
        }
        recs.values().removeIf(r -> r.count == 0);
        return recs;
    }

    static final class Rec implements com.mojang.blaze3d.vertex.VertexConsumer {
        static final int N = 13;
        float[] data = new float[N * 1024];
        int[] col = new int[1024];
        int count;
        private int base = -N;

        private void need() {
            if ((count + 1) * N > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            if (count + 1 > col.length) col = java.util.Arrays.copyOf(col, col.length * 2);
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            need();
            base = count * N;
            count++;
            data[base] = x;
            data[base + 1] = y;
            data[base + 2] = z;
            col[count - 1] = -1;
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
            col[count - 1] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
            data[base + 4] = u;
            data[base + 5] = v;
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
            data[base + 6] = u;
            data[base + 7] = v;
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
            data[base + 8] = u;
            data[base + 9] = v;
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
            data[base + 10] = x;
            data[base + 11] = y;
            data[base + 12] = z;
            return this;
        }

        void dropInternal() {
            if (count % 4 != 0) return;
            int quads = count / 4;
            int[] axis = new int[quads], sign = new int[quads];
            long[] plane = new long[quads];
            float[][] rect = new float[quads][];
            java.util.HashMap<Long, java.util.ArrayList<Integer>> byPlane = new java.util.HashMap<>();
            for (int q = 0; q < quads; q++) {
                int o = q * 4 * N;
                float nx = data[o + 10], ny = data[o + 11], nz = data[o + 12];
                int a = Math.abs(nx) > 0.99f ? 0 : Math.abs(ny) > 0.99f ? 1 : Math.abs(nz) > 0.99f ? 2 : -1;
                axis[q] = a;
                if (a < 0) continue;
                float nv = a == 0 ? nx : a == 1 ? ny : nz;
                sign[q] = nv > 0 ? 1 : -1;
                float d = data[o + a];
                boolean flat = true;
                for (int k = 1; k < 4; k++) if (Math.abs(data[o + k * N + a] - d) > 1e-3f) flat = false;
                if (!flat) { axis[q] = -1; continue; }
                int u = (a + 1) % 3, v = (a + 2) % 3;
                float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = u0, v1 = u1;
                for (int k = 0; k < 4; k++) {
                    float uu = data[o + k * N + u], vv = data[o + k * N + v];
                    u0 = Math.min(u0, uu); u1 = Math.max(u1, uu); v0 = Math.min(v0, vv); v1 = Math.max(v1, vv);
                }
                rect[q] = new float[] {u0, u1, v0, v1};
                plane[q] = Math.round(d * 1024f);
                byPlane.computeIfAbsent(((plane[q] * 3 + a) << 1) | (sign[q] > 0 ? 1 : 0), x -> new java.util.ArrayList<>()).add(q);
            }
            boolean[] drop = new boolean[quads];
            int dropped = 0;
            for (int q = 0; q < quads; q++) {
                if (axis[q] < 0) continue;
                var cover = byPlane.get(((plane[q] * 3 + axis[q]) << 1) | (sign[q] > 0 ? 0 : 1));
                if (cover == null) continue;
                float[] r = rect[q];
                for (int c : cover) {
                    float[] b = rect[c];
                    if (b[0] <= r[0] + 1e-3f && b[1] >= r[1] - 1e-3f && b[2] <= r[2] + 1e-3f && b[3] >= r[3] - 1e-3f) {
                        drop[q] = true;
                        dropped++;
                        break;
                    }
                }
            }
            if (dropped == 0) return;
            int w = 0;
            for (int q = 0; q < quads; q++) {
                if (drop[q]) continue;
                if (w != q) {
                    System.arraycopy(data, q * 4 * N, data, w * 4 * N, 4 * N);
                    System.arraycopy(col, q * 4, col, w * 4, 4);
                }
                w++;
            }
            count = w * 4;
        }

        private final class Planes {
            final int quads = count / 4;
            final float[][] nrm = new float[quads][];
            final float[] off = new float[quads];
            final float[][] basis = new float[quads][];
            final float[][] rect = new float[quads][];
            final List<List<Integer>> groups = new ArrayList<>();
            static final float EPS = 2e-3f;

            Planes(boolean byPlane) {
                java.util.HashMap<Long, List<Integer>> map = new java.util.HashMap<>();
                for (int q = 0; q < quads; q++) {
                    int o = q * 4 * N;
                    float nx = data[o + 10], ny = data[o + 11], nz = data[o + 12];
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 0.5f) continue;
                    nx /= len; ny /= len; nz /= len;
                    float d = nx * data[o] + ny * data[o + 1] + nz * data[o + 2];
                    boolean flat = true;
                    for (int k = 1; k < 4; k++) {
                        int w = o + k * N;
                        if (Math.abs(nx * data[w] + ny * data[w + 1] + nz * data[w + 2] - d) > EPS) flat = false;
                    }
                    if (!flat) continue;
                    nrm[q] = new float[] {nx, ny, nz};
                    off[q] = d;
                    long key = ((long) Math.round(nx * 500f) & 0x3FFL) | ((long) Math.round(ny * 500f) & 0x3FFL) << 10
                            | ((long) Math.round(nz * 500f) & 0x3FFL) << 20;
                    map.computeIfAbsent(key, x -> new ArrayList<>()).add(q);
                }
                List<List<Integer>> sets = new ArrayList<>();
                for (List<Integer> g : map.values()) {
                    if (!byPlane) { sets.add(g); continue; }
                    List<Integer> sorted = new ArrayList<>(g);
                    sorted.sort((x, y) -> Float.compare(off[x], off[y]));
                    List<Integer> cur = new ArrayList<>();
                    for (int q : sorted) {
                        if (!cur.isEmpty() && off[q] - off[cur.get(cur.size() - 1)] > 1e-4f) { sets.add(cur); cur = new ArrayList<>(); }
                        cur.add(q);
                    }
                    sets.add(cur);
                }
                for (List<Integer> g : sets) {
                    if (g.size() < 2) continue;
                    int f = g.get(0), fo = f * 4 * N;
                    float[] n = nrm[f];
                    float ex = data[fo + N] - data[fo], ey = data[fo + N + 1] - data[fo + 1], ez = data[fo + N + 2] - data[fo + 2];
                    float dn = ex * n[0] + ey * n[1] + ez * n[2];
                    ex -= dn * n[0]; ey -= dn * n[1]; ez -= dn * n[2];
                    float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                    if (el < 1e-4f) continue;
                    ex /= el; ey /= el; ez /= el;
                    float[] e1 = {ex, ey, ez}, e2 = {n[1] * ez - n[2] * ey, n[2] * ex - n[0] * ez, n[0] * ey - n[1] * ex};
                    List<Integer> use = new ArrayList<>();
                    for (int q : g) {
                        int o = q * 4 * N;
                        float ax = data[o + N] - data[o], ay = data[o + N + 1] - data[o + 1], az = data[o + N + 2] - data[o + 2];
                        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                        if (al < 1e-4f) continue;
                        float dot = Math.abs(ax * e1[0] + ay * e1[1] + az * e1[2]) / al;
                        if (dot < 0.9995f && dot > 0.0005f) continue;
                        float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = u0, v1 = u1;
                        for (int k = 0; k < 4; k++) {
                            int w = o + k * N;
                            float uu = data[w] * e1[0] + data[w + 1] * e1[1] + data[w + 2] * e1[2];
                            float vv = data[w] * e2[0] + data[w + 1] * e2[1] + data[w + 2] * e2[2];
                            u0 = Math.min(u0, uu); u1 = Math.max(u1, uu); v0 = Math.min(v0, vv); v1 = Math.max(v1, vv);
                        }
                        rect[q] = new float[] {u0, u1, v0, v1};
                        basis[q] = new float[] {e1[0], e1[1], e1[2], e2[0], e2[1], e2[2]};
                        use.add(q);
                    }
                    if (use.size() >= 2) groups.add(use);
                }
            }
        }

        private float[] prio;

        void snapNearPlanes() {
            if (count % 4 != 0) return;
            Planes pl = new Planes(false);
            prio = pl.off.clone();
            final float tol = 0.012f, eps = Planes.EPS;
            for (List<Integer> g : pl.groups) {
                List<Integer> order = new ArrayList<>(g);
                order.sort((x, y) -> Float.compare(pl.off[x], pl.off[y]));
                for (int i = order.size() - 1; i >= 0; i--) {
                    int lo = order.get(i);
                    float target = pl.off[lo];
                    for (int j = order.size() - 1; j > i; j--) {
                        int hi = order.get(j);
                        float dd = pl.off[hi] - pl.off[lo];
                        if (dd <= 1e-5f || dd > tol) continue;
                        float[] r = pl.rect[lo], l = pl.rect[hi];
                        if (Math.min(r[1], l[1]) - Math.max(r[0], l[0]) > eps && Math.min(r[3], l[3]) - Math.max(r[2], l[2]) > eps) {
                            target = pl.off[hi];
                            break;
                        }
                    }
                    if (target > pl.off[lo]) {
                        float sh = target - pl.off[lo];
                        float[] n = pl.nrm[lo];
                        int o = lo * 4 * N;
                        for (int k = 0; k < 4; k++) {
                            data[o + k * N] += n[0] * sh;
                            data[o + k * N + 1] += n[1] * sh;
                            data[o + k * N + 2] += n[2] * sh;
                        }
                        pl.off[lo] = target;
                    }
                }
            }
        }

        void dropOverlaps() {
            if (count % 4 != 0) return;
            Planes pl = new Planes(true);
            final int quads = pl.quads;
            final float eps = Planes.EPS;
            boolean[] drop = new boolean[quads];
            List<List<float[]>> pieces = new ArrayList<>();
            for (int q = 0; q < quads; q++) pieces.add(null);
            boolean changed = false;
            for (List<Integer> g : pl.groups) {
                List<Integer> use = new ArrayList<>(g);
                if (prio != null && prio.length == quads) {
                    use.sort((x, y) -> {
                        int c = Integer.compare(Math.round(prio[x] * 256f), Math.round(prio[y] * 256f));
                        return c != 0 ? c : Integer.compare(x, y);
                    });
                }
                for (int i = 0; i < use.size(); i++) {
                    int e = use.get(i);
                    List<float[]> cur = new ArrayList<>();
                    cur.add(pl.rect[e].clone());
                    boolean cut = false;
                    for (int j = i + 1; j < use.size() && !cur.isEmpty(); j++) {
                        float[] l = pl.rect[use.get(j)];
                        List<float[]> next = new ArrayList<>();
                        for (float[] r : cur) {
                            float iu0 = Math.max(r[0], l[0]), iu1 = Math.min(r[1], l[1]), iv0 = Math.max(r[2], l[2]), iv1 = Math.min(r[3], l[3]);
                            if (iu1 - iu0 <= eps || iv1 - iv0 <= eps) { next.add(r); continue; }
                            cut = true;
                            if (iu0 - r[0] > eps) next.add(new float[] {r[0], iu0, r[2], r[3]});
                            if (r[1] - iu1 > eps) next.add(new float[] {iu1, r[1], r[2], r[3]});
                            if (iv0 - r[2] > eps) next.add(new float[] {iu0, iu1, r[2], iv0});
                            if (r[3] - iv1 > eps) next.add(new float[] {iu0, iu1, iv1, r[3]});
                        }
                        cur = next;
                        if (cur.size() > 24) { cut = false; cur = null; break; }
                    }
                    if (cur == null || !cut) continue;
                    if (cur.isEmpty()) { drop[e] = true; changed = true; }
                    else { pieces.set(e, cur); changed = true; }
                }
            }
            if (!changed) return;
            for (int q = 0; q < quads; q++) {
                List<float[]> ps = pieces.get(q);
                if (ps == null || drop[q]) continue;
                int start = count;
                boolean ok = true;
                for (float[] p : ps) {
                    int nq = count / 4;
                    if ((nq + 1) * 4 > col.length) col = java.util.Arrays.copyOf(col, col.length * 2);
                    if ((nq + 1) * 4 * N > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
                    System.arraycopy(data, q * 4 * N, data, nq * 4 * N, 4 * N);
                    System.arraycopy(col, q * 4, col, nq * 4, 4);
                    count += 4;
                    if (!trim(nq, pl.basis[q], pl.rect[q], p)) { ok = false; break; }
                }
                if (ok) drop[q] = true;
                else count = start;
            }
            int total = count / 4, w = 0;
            for (int q = 0; q < total; q++) {
                if (q < quads && drop[q]) continue;
                if (w != q) {
                    System.arraycopy(data, q * 4 * N, data, w * 4 * N, 4 * N);
                    System.arraycopy(col, q * 4, col, w * 4, 4);
                }
                w++;
            }
            count = w * 4;
        }

        private boolean trim(int q, float[] b, float[] r0, float[] r1) {
            int o = q * 4 * N;
            float du = r0[1] - r0[0], dv = r0[3] - r0[2];
            if (du < 1e-4f || dv < 1e-4f) return false;
            int[] cs = new int[4], ct = new int[4];
            float[] pu = new float[4], pv = new float[4];
            boolean[] seen = new boolean[4];
            for (int k = 0; k < 4; k++) {
                int w = o + k * N;
                pu[k] = data[w] * b[0] + data[w + 1] * b[1] + data[w + 2] * b[2];
                pv[k] = data[w] * b[3] + data[w + 1] * b[4] + data[w + 2] * b[5];
                float s = (pu[k] - r0[0]) / du, t = (pv[k] - r0[2]) / dv;
                cs[k] = s > 0.5f ? 1 : 0;
                ct[k] = t > 0.5f ? 1 : 0;
                if (Math.abs(s - cs[k]) > 2e-3f || Math.abs(t - ct[k]) > 2e-3f) return false;
                if (seen[cs[k] * 2 + ct[k]]) return false;
                seen[cs[k] * 2 + ct[k]] = true;
            }
            float[][] uv = new float[4][];
            for (int k = 0; k < 4; k++) uv[cs[k] * 2 + ct[k]] = new float[] {data[o + k * N + 4], data[o + k * N + 5]};
            for (int k = 0; k < 4; k++) {
                float nu = cs[k] == 1 ? r1[1] : r1[0], nv = ct[k] == 1 ? r1[3] : r1[2];
                float s = (nu - r0[0]) / du, t = (nv - r0[2]) / dv;
                int w = o + k * N;
                float mu = nu - pu[k], mv = nv - pv[k];
                data[w] += b[0] * mu + b[3] * mv;
                data[w + 1] += b[1] * mu + b[4] * mv;
                data[w + 2] += b[2] * mu + b[5] * mv;
                data[w + 4] = (1 - s) * (1 - t) * uv[0][0] + (1 - s) * t * uv[1][0] + s * (1 - t) * uv[2][0] + s * t * uv[3][0];
                data[w + 5] = (1 - s) * (1 - t) * uv[0][1] + (1 - s) * t * uv[1][1] + s * (1 - t) * uv[2][1] + s * t * uv[3][1];
            }
            return true;
        }

        float[] plane;
        float[] boxes;
        static final int CLUSTER = 32;

        void prepare() {
            int quads = count / 4;
            plane = new float[quads * 4];
            if (count % 4 == 0) {
                int n = (quads + CLUSTER - 1) / CLUSTER;
                boxes = new float[n * 6];
                for (int c = 0; c < n; c++) {
                    float x0 = Float.MAX_VALUE, y0 = x0, z0 = x0, x1 = -Float.MAX_VALUE, y1 = x1, z1 = x1;
                    for (int v = c * CLUSTER * 4, e = Math.min(count, v + CLUSTER * 4); v < e; v++) {
                        float x = data[v * N], y = data[v * N + 1], z = data[v * N + 2];
                        x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
                        x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
                    }
                    boxes[c * 6] = x0; boxes[c * 6 + 1] = y0; boxes[c * 6 + 2] = z0;
                    boxes[c * 6 + 3] = x1; boxes[c * 6 + 4] = y1; boxes[c * 6 + 5] = z1;
                }
            }
            for (int q = 0; q < quads; q++) {
                int o = q * 4 * N;
                float cx = 0, cy = 0, cz = 0;
                for (int k = 0; k < 4; k++) {
                    cx += data[o + k * N];
                    cy += data[o + k * N + 1];
                    cz += data[o + k * N + 2];
                }
                cx *= 0.25f;
                cy *= 0.25f;
                cz *= 0.25f;
                float nx = data[o + 10], ny = data[o + 11], nz = data[o + 12];
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 0.5f) {
                    plane[q * 4 + 3] = Float.NaN;
                    continue;
                }
                plane[q * 4] = nx / len;
                plane[q * 4 + 1] = ny / len;
                plane[q * 4 + 2] = nz / len;
                plane[q * 4 + 3] = (nx * cx + ny * cy + nz * cz) / len;
            }
        }

        private static int lu(float u, int bu, int nu, float v, int bv) {
            return (int) u == bu && (int) v == bv ? nu : (int) u;
        }

        private static int lv(float u, int bu, float v, int bv, int nv) {
            return (int) u == bu && (int) v == bv ? nv : (int) v;
        }

        int replay(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, boolean cull, int baseLight, int light, float push) {
            int emitted = 0;
            final int bu = baseLight & 0xFFFF, bv = baseLight >>> 16, nu = light & 0xFFFF, nv = light >>> 16;
            Matrix4f m = pose.pose();
            if (m.m00() == 1f && m.m11() == 1f && m.m22() == 1f && m.m01() == 0f && m.m02() == 0f && m.m10() == 0f
                    && m.m12() == 0f && m.m20() == 0f && m.m21() == 0f) {
                float tx = m.m30(), ty = m.m31(), tz = m.m32();
                float camx = -tx, camy = -ty, camz = -tz;
                boolean skip = cull && plane != null && count % 4 == 0;
                net.minecraft.client.renderer.culling.Frustum fr = skip && boxes != null ? viewFrustum : null;
                double wx = camX + tx, wy = camY + ty, wz = camZ + tz;
                final int span = CLUSTER * 4;
                for (int i = 0, o = 0; i < count; i++, o += N) {
                    if (fr != null && i % span == 0) {
                        int b = i / span * 6;
                        if (!fr.isVisible(new net.minecraft.world.phys.AABB(wx + boxes[b], wy + boxes[b + 1], wz + boxes[b + 2], wx + boxes[b + 3], wy + boxes[b + 4], wz + boxes[b + 5]))) {
                            int n = Math.min(span, count - i);
                            i += n - 1;
                            o += (n - 1) * N;
                            if (com.sqc.nuclearsq.client.PerfProbe.on) com.sqc.nuclearsq.client.PerfProbe.clusterCulled++;
                            continue;
                        }
                    }
                    if (skip && (i & 3) == 0) {
                        int q = (i >> 2) * 4;
                        float d = plane[q + 3];
                        if (d == d && plane[q] * camx + plane[q + 1] * camy + plane[q + 2] * camz - d < -0.01f) {
                            i += 3;
                            o += 3 * N;
                            continue;
                        }
                    }
                    int c = col[i];
                    vc.addVertex(data[o] + tx + data[o + 10] * push, data[o + 1] + ty + data[o + 11] * push, data[o + 2] + tz + data[o + 12] * push)
                            .setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24)
                            .setUv(data[o + 4], data[o + 5])
                            .setUv1((int) data[o + 6], (int) data[o + 7])
                            .setUv2(lu(data[o + 8], bu, nu, data[o + 9], bv), lv(data[o + 8], bu, data[o + 9], bv, nv))
                            .setNormal(data[o + 10], data[o + 11], data[o + 12]);
                    emitted++;
                }
                return emitted;
            }
            for (int i = 0, o = 0; i < count; i++, o += N) {
                int c = col[i];
                vc.addVertex(pose, data[o] + data[o + 10] * push, data[o + 1] + data[o + 11] * push, data[o + 2] + data[o + 12] * push)
                        .setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24)
                        .setUv(data[o + 4], data[o + 5])
                        .setUv1((int) data[o + 6], (int) data[o + 7])
                        .setUv2(lu(data[o + 8], bu, nu, data[o + 9], bv), lv(data[o + 8], bu, data[o + 9], bv, nv))
                        .setNormal(pose, data[o + 10], data[o + 11], data[o + 12]);
                emitted++;
            }
            return emitted;
        }
    }

    private static void drawPart(RenderType type, VertexBuffer buffer, PoseStack pose) {
        type.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                MV.set(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
                buffer.bind();
                buffer.drawWithShader(MV, RenderSystem.getProjectionMatrix(), shader);
                VertexBuffer.unbind();
            }
        } finally {
            type.clearRenderState();
        }
    }

    public static void clear() {
        for (Entry e : CACHE.values()) e.close();
        CACHE.clear();
        cacheLevel = null;
    }

    public static void clearAll() {
        clear();
        DYN.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    private static final class Capture implements MultiBufferSource {
        final Map<RenderType, BufferBuilder> builders = new IdentityHashMap<>();
        private final List<ByteBufferBuilder> memory = new ArrayList<>();

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type) {
            return builders.computeIfAbsent(type, t -> {
                ByteBufferBuilder mem = new ByteBufferBuilder(1 << 18);
                memory.add(mem);
                return new BufferBuilder(mem, t.mode(), t.format());
            });
        }

        void close() {
            for (ByteBufferBuilder m : memory) m.close();
            memory.clear();
            builders.clear();
        }
    }
}
