package com.sqc.nuclearsq.progression.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import com.sqc.nuclearsq.progression.BuildData;
import com.sqc.nuclearsq.progression.BuildData.Block;
import com.sqc.nuclearsq.progression.BuildData.Machine;
import com.sqc.nuclearsq.progression.BuildData.Pipe;
import com.sqc.nuclearsq.progression.BuildData.Step;

final class BuildScene {
    private static final int STRIP_HEIGHT = 70;
    private static final java.util.Map<String, Integer> PORT_KIND_COLORS = java.util.Map.ofEntries(java.util.Map.entry("energy", 0xFFE05050), java.util.Map.entry("item", 0xFFE0C060), java.util.Map.entry("terminal", 0xFF5ADCE8), java.util.Map.entry("signal", 0xFFF2DC5A), java.util.Map.entry("high_voltage", 0xFFB8892A), java.util.Map.entry("water", 0xFF4FA3D9), java.util.Map.entry("big_water", 0xFF4FA3D9), java.util.Map.entry("feedwater", 0xFF4B7FC0), java.util.Map.entry("primary", 0xFFA85BD8), java.util.Map.entry("steam", 0xFFE6EDF2), java.util.Map.entry("wet_steam", 0xFFC9D3DC), java.util.Map.entry("coolant", 0xFF6FD0E0), java.util.Map.entry("acid", 0xFF8AD04A), java.util.Map.entry("gas", 0xFFE8803A));

    private static final class Q {
        final float group, d;
        final float[] xy;
        final float[] uv;
        final int c;
        final ResourceLocation tex;
        float[] z;

        Q(float group, float d, float[] xy, float[] uv, int c, ResourceLocation tex) {
            this.group = group;
            this.d = d;
            this.xy = xy;
            this.uv = uv;
            this.c = c;
            this.tex = tex;
        }
    }

    private final String prefix;
    private final BuildData data;
    private final boolean single;
    private final int stripHeight;
    private int hoverPort = -1;
    private List<Object[]> markerSpecs;
    private final List<Object[]> portMarkers = new ArrayList<>();
    private String[] textCache;
    private final List<Machine> machines;
    private final List<Pipe> pipes;
    private final List<Step> steps;
    private final int[] stepOfMachine, stepOfPipe;
    private final List<double[]> boundsPoints;
    private final int groundWidth, groundDepth;
    private final double centerX, centerY, centerZ;
    private final List<String[]> captions;
    private final List<Block> blocks;
    private final ModelMesh[] blockMeshes;
    private final int[] blockMeshTries;
    private final ModelMesh[] meshes;
    private final int[] meshTries;

    private int step;
    private float t, yaw = 0.35f, pitch = 0.615f, zoom = 1f, panX, panY, fit = 1f;
    private boolean playing = true;
    private long lastMs;
    private int fitW = -1, fitH = -1;
    private int drag = -1;
    private int ax, ay, aw, ah, vh;
    private int visibleTop, visibleHeight;
    private final List<int[]> buttons = new ArrayList<>();

    private final List<float[]> hitSegs = new ArrayList<>();
    private int hoverPipe = -1, hoverMachine = -1;
    private boolean dragMoved;
    private String pressedPage;

    private float[][] mBox;

    private List<Q> cachedQuads, cachedGhost;
    private float[][] cachedBox;
    private int sceneSig;
    private static JsonObject cooling;
    private final Map<String, JsonObject> mbCache = new HashMap<>();

    static BuildScene create(String pageId) {
        BuildData d = BuildData.load(pageId);
        return d == null ? null : new BuildScene(pageId, d, false);
    }

    static BuildScene createForMachine(String machineId, String pageId) {
        try {
            return new BuildScene(pageId, BuildData.ofMachine(machineId, pageId), true);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("No 3D view for machine {}", machineId, e);
            return null;
        }
    }

    private BuildScene(String pageId, BuildData d, boolean single) {
        prefix = "compendium." + pageId;
        data = d;
        this.single = single;
        stripHeight = single ? 0 : STRIP_HEIGHT;
        textCache = new String[d.steps.size()];
        machines = d.machines;
        pipes = d.pipes;
        steps = d.steps;
        stepOfMachine = d.stepOfMachine;
        stepOfPipe = d.stepOfPipe;
        boundsPoints = d.boundsPoints;
        groundWidth = d.groundWidth;
        groundDepth = d.groundDepth;
        centerX = d.centerX;
        centerY = d.centerY;
        centerZ = d.centerZ;
        captions = d.captions;
        blocks = d.blocks;
        blockMeshes = new ModelMesh[blocks.size()];
        blockMeshTries = new int[blocks.size()];
        meshes = new ModelMesh[machines.size()];
        meshTries = new int[machines.size()];
    }

    private ModelMesh mesh(int i) {
        if (meshes[i] == null && meshTries[i] < 20) {
            meshTries[i]++;
            try {
                meshes[i] = ModelMesh.of(machines.get(i));
            } catch (Throwable e) {
                meshTries[i] = 20;
                com.mojang.logging.LogUtils.getLogger().warn("Build scene: model of {} not usable, drawing a box", machines.get(i).id(), e);
            }
        }
        return meshes[i];
    }

    private static float ease(float v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
    }

    private void advance(float dt) {
        if (!playing) return;
        t += dt;
        float dur = steps.get(step).totalSeconds();
        if (t >= dur) {
            if (step < steps.size() - 1) { step++; t = 0; }
            else { t = dur; playing = false; }
        }
    }

    private float machineProgress(int i) {
        int s = stepOfMachine[i];
        if (s < 0 || step > s) return 1;
        if (step < s) return 0;
        return ease(t / BuildData.BUILD_SECONDS);
    }

    private float pipeProgress(int i) {
        int s = stepOfPipe[i];
        if (s < 0 || step > s) return 1;
        if (step < s) return 0;
        return ease((t - steps.get(s).buildSeconds()) / BuildData.PIPE_SECONDS);
    }

    private void finish() {
        step = steps.size() - 1;
        t = steps.get(step).totalSeconds();
        playing = false;
    }

    private void jump(int s) {
        step = Math.max(0, Math.min(steps.size() - 1, s));
        t = 0;
        playing = true;
    }

    private double cosA, sinA, cosF, sinF, scale, baseX, baseY;
    private static final double C0 = 1.2247449;
    private static final float DEPTH_SCALE = 8f;

    private void setCamera() {
        double a = yaw + Math.PI / 4;
        cosA = Math.cos(a);
        sinA = Math.sin(a);
        cosF = Math.cos(pitch);
        sinF = Math.sin(pitch);
    }

    private float[] proj(double x, double y, double z) {
        double dx = x - centerX, dz = z - centerZ, u = dx * cosA - dz * sinA, d = dx * sinA + dz * cosA;
        return new float[] {(float) (baseX + u * C0 * scale), (float) (baseY + (d * sinF - (y - centerY) * cosF) * C0 * scale)};
    }

    private float depth(double x, double y, double z) {
        double dx = x - centerX, dz = z - centerZ, d = dx * sinA + dz * cosA;
        return (float) (d * cosF + (y - centerY) * sinF);
    }

    private void computeFit() {
        double best = 1e9;
        double[] pitches = {0.15, 0.5, 0.615, 0.9, 1.2, 1.5};
        for (int a = 0; a < 24; a++) {
            double ang = a * Math.PI / 12 + Math.PI / 4, c = Math.cos(ang), s = Math.sin(ang);
            for (double pf : pitches) {
                double sf = Math.sin(pf), cf = Math.cos(pf), mx = 0.01, my = 0.01;
                for (double[] p : boundsPoints) {
                    double dx = p[0] - centerX, dz = p[2] - centerZ, u = dx * c - dz * s, d = dx * s + dz * c;
                    mx = Math.max(mx, Math.abs(u * C0));
                    my = Math.max(my, Math.abs((d * sf - (p[1] - centerY) * cf) * C0));
                }
                best = Math.min(best, Math.min((aw - 50) / (2 * mx), (vh - 40) / (2 * my)));
            }
        }
        fit = (float) Math.max(1.5, best);
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void flat(List<Q> out, float group, float d, float[] a, float[] b, float[] c, float[] e, int col) {
        out.add(new Q(group, d, new float[] {a[0], a[1], b[0], b[1], c[0], c[1], e[0], e[1]}, null, col, null));
    }

    private void line(List<Q> out, float group, float d, float[] a, float[] b, float w, int col) {
        float dx = b[0] - a[0], dy = b[1] - a[1], len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        out.add(new Q(group, d, new float[] {a[0] + nx, a[1] + ny, b[0] + nx, b[1] + ny, b[0] - nx, b[1] - ny, a[0] - nx, a[1] - ny}, null, col, null));
    }

    private void drawMesh(List<Q> out, Machine machine, ModelMesh mesh, float grown) {
        float group = depth(machine.modelX(), machine.modelY() + (mesh.maxY - mesh.minY) / 2, machine.modelZ());
        drawFaces(out, mesh, grown, group, false);
    }

    private void drawFaces(List<Q> out, ModelMesh mesh, float grown, float group, boolean brightened) {
        float cutHeight = mesh.minY + (mesh.maxY - mesh.minY) * grown;
        double towardsViewerX = sinA * cosF, towardsViewerY = sinF, towardsViewerZ = cosA * cosF;
        float boost = brightened ? 1.6f : 1f;
        for (int face = 0; face < mesh.pos.size(); face++) {
            float[] normal = mesh.nrm.get(face), corners = mesh.pos.get(face), uv = mesh.uv.get(face);
            if (normal[0] * towardsViewerX + normal[1] * towardsViewerY + normal[2] * towardsViewerZ <= 0.02) continue;
            double normalRight = normal[0] * cosA - normal[2] * sinA, normalDepth = normal[0] * sinA + normal[2] * cosA;
            float lighting = (float) Math.max(0, normalRight * 0.29 + normal[1] * 0.72 + normalDepth * 0.587);
            float brightness = (0.5f + 0.5f * lighting) * boost;
            float lowestY = Math.min(Math.min(corners[1], corners[4]), Math.min(corners[7], corners[10]));
            float highestY = Math.max(Math.max(corners[1], corners[4]), Math.max(corners[7], corners[10]));
            if (grown < 1f && lowestY >= cutHeight) continue;

            List<float[]> polygon = new ArrayList<>(5);
            for (int corner = 0; corner < 4; corner++) polygon.add(new float[] {corners[corner * 3], corners[corner * 3 + 1], corners[corner * 3 + 2], uv[corner * 2], uv[corner * 2 + 1]});
            if (grown < 1f && highestY > cutHeight) polygon = clip(polygon, cutHeight);
            if (polygon.size() < 3) continue;
            int tint = mesh.tint.get(face);
            float red = brightness * 255 * ((tint >> 16) & 255) / 255f, green = brightness * 255 * ((tint >> 8) & 255) / 255f, blue = brightness * 255 * (tint & 255) / 255f;
            boolean beingBuilt = grown < 1f && highestY > cutHeight - 1.2f;
            int color;
            if (beingBuilt) color = 0xFF000000 | (Math.min(255, (int) (red * 0.55f)) << 16) | (Math.min(255, (int) (green * 0.96f)) << 8) | Math.min(255, (int) blue + 30);
            else color = 0xFF000000 | (Math.min(255, (int) red) << 16) | (Math.min(255, (int) green) << 8) | Math.min(255, (int) blue);
            float centerX = 0, centerY = 0, centerZ = 0;
            for (float[] corner : polygon) { centerX += corner[0]; centerY += corner[1]; centerZ += corner[2]; }
            float faceDepth = depth(centerX / polygon.size(), centerY / polygon.size(), centerZ / polygon.size());

            for (int k = 1; k + 1 < polygon.size(); k++) {
                float[] first = polygon.get(0), second = polygon.get(k), third = polygon.get(k + 1);
                float[] screenFirst = proj(first[0], first[1], first[2]), screenSecond = proj(second[0], second[1], second[2]), screenThird = proj(third[0], third[1], third[2]);
                Q quad = new Q(group, faceDepth, new float[] {screenFirst[0], screenFirst[1], screenSecond[0], screenSecond[1], screenThird[0], screenThird[1], screenThird[0], screenThird[1]},
                        new float[] {first[3], first[4], second[3], second[4], third[3], third[4], third[3], third[4]}, color, mesh.tex);
                float zFirst = DEPTH_SCALE * depth(first[0], first[1], first[2]), zSecond = DEPTH_SCALE * depth(second[0], second[1], second[2]), zThird = DEPTH_SCALE * depth(third[0], third[1], third[2]);
                quad.z = new float[] {zFirst, zSecond, zThird, zThird};
                out.add(quad);
            }
        }
    }

    private static List<float[]> clip(List<float[]> in, float cut) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            float[] a = in.get(i), b = in.get((i + 1) % in.size());
            boolean ain = a[1] <= cut, bin = b[1] <= cut;
            if (ain) out.add(a);
            if (ain != bin) {
                float f = (cut - a[1]) / (b[1] - a[1]);
                float[] v = new float[5];
                for (int k = 0; k < 5; k++) v[k] = a[k] + (b[k] - a[k]) * f;
                out.add(v);
            }
        }
        return out;
    }

    private void box(List<Q> out, Machine m, float q) {
        double hh = m.height() * q, x0 = m.cornerX(), x1 = x0 + m.width(), z0 = m.cornerZ(), z1 = z0 + m.depth(), y0 = m.cornerY(), y1 = y0 + hh;
        float group = depth((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
        double[][] sides = {{-1, 0, x0, z0, x0, z1}, {1, 0, x1, z0, x1, z1}, {0, -1, x0, z0, x1, z0}, {0, 1, x0, z1, x1, z1}};
        for (double[] s : sides) {
            double nu = s[0] * cosA - s[1] * sinA;
            if (s[0] * sinA * cosF + s[1] * cosA * cosF <= 0.001 || hh <= 0) continue;
            float br = 0.52f + 0.38f * (float) ((1.414 * nu + 1.42) / 2.84);
            flat(out, group, group, proj(s[2], y0, s[3]), proj(s[4], y0, s[5]), proj(s[4], y1, s[5]), proj(s[2], y1, s[3]), shade(m.color(), br));
        }
        if (hh > 0) flat(out, group, group + 1, proj(x0, y1, z0), proj(x1, y1, z0), proj(x1, y1, z1), proj(x0, y1, z1), shade(m.color(), 1.18f));
    }

    private void ghost(List<Q> over, Machine m) {
        double x0 = m.cornerX(), x1 = x0 + m.width(), z0 = m.cornerZ(), z1 = z0 + m.depth(), y0 = m.cornerY(), yt = m.cornerY() + m.height();
        double[][] g = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++) {
            double[] a = g[i], b = g[(i + 1) % 4];
            line(over, 0, 0, proj(a[0], y0, a[1]), proj(b[0], y0, b[1]), 1f, withAlpha(0x6FD0FF, 150));
            line(over, 0, 0, proj(a[0], yt, a[1]), proj(b[0], yt, b[1]), 1f, withAlpha(0x6FD0FF, 110));
            line(over, 0, 0, proj(a[0], y0, a[1]), proj(a[0], yt, a[1]), 1f, withAlpha(0x6FD0FF, 110));
        }
    }

    private void footprint(List<Q> out, Machine m) {
        double x0 = m.cornerX(), x1 = x0 + m.width(), z0 = m.cornerZ(), z1 = z0 + m.depth();
        double[][] g = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        flat(out, -1e8f + 2, -1e8f + 2, proj(x0, 0, z0), proj(x1, 0, z0), proj(x1, 0, z1), proj(x0, 0, z1), withAlpha(0x6FD0FF, 26));
        for (int i = 0; i < 4; i++) line(out, -1e8f + 3, -1e8f + 3, proj(g[i][0], 0, g[i][1]), proj(g[(i + 1) % 4][0], 0, g[(i + 1) % 4][1]), 1f, withAlpha(0x6FD0FF, 90));
    }

    private ModelMesh blockMesh(int blockIndex) {
        if (blockMeshes[blockIndex] == null && blockMeshTries[blockIndex] < 20) {
            blockMeshTries[blockIndex]++;
            Block block = blocks.get(blockIndex);
            try {
                blockMeshes[blockIndex] = ModelMesh.conduit(block.kind, block.connections, block.x, block.y, block.z);
            } catch (Throwable e) {
                blockMeshTries[blockIndex] = 20;
                com.mojang.logging.LogUtils.getLogger().warn("Build scene: model of a {} block not usable", block.kind, e);
            }
        }
        return blockMeshes[blockIndex];
    }

    private void drawPipeBlocks(List<Q> out) {
        float circleRadius = Math.max(3f, (float) (0.5 * C0 * scale));
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            Block block = blocks.get(blockIndex);
            Pipe pipe = pipes.get(block.pipeIndex);
            if (pipeProgress(block.pipeIndex) * pipe.blockCount() <= block.indexInPipe) continue;
            double centerX = block.x + 0.5, centerY = block.y + 0.5, centerZ = block.z + 0.5;
            float[] screen = proj(centerX, centerY, centerZ);
            hitSegs.add(new float[] {screen[0], screen[1], screen[0], screen[1], circleRadius, block.pipeIndex});
            ModelMesh mesh = blockMesh(blockIndex);
            if (mesh != null) drawFaces(out, mesh, 1f, depth(centerX, centerY, centerZ), block.pipeIndex == hoverPipe);
        }
    }

    void render(GuiGraphics g, Font font, int x, int areaTop, int w, int h, int mx, int my, int scroll) {
        int y = areaTop - scroll;
        visibleTop = areaTop;
        visibleHeight = h;
        ax = x; ay = y; aw = w; ah = h;
        vh = h - stripHeight;
        g.enableScissor(x, areaTop, x + w, areaTop + h);
        long now = System.currentTimeMillis();
        float dt = lastMs == 0 ? 0 : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        advance(dt);
        if (aw != fitW || vh != fitH) { fitW = aw; fitH = vh; computeFit(); }
        setCamera();
        scale = fit * zoom;
        baseX = x + w / 2.0 + panX;
        baseY = y + vh / 2.0 + panY;

        g.fillGradient(x, y, x + w, y + vh, 0xFF0F1218, 0xFF1B212B);
        g.fill(x, y, x + w, y + 1, 0xFF38383C);
        g.fill(x, y, x + 1, y + vh, 0xFF38383C);
        g.fill(x + w - 1, y, x + w, y + vh, 0xFF38383C);

        if (mBox == null || mBox.length != machines.size()) mBox = new float[machines.size()][];
        float[] prog = new float[machines.size()];
        for (int i = 0; i < prog.length; i++) prog[i] = machineProgress(i);

        int sig = java.util.Objects.hash(yaw, pitch, scale, baseX, baseY, aw, vh, java.util.Arrays.hashCode(prog), java.util.Arrays.hashCode(meshes));
        if (cachedQuads == null || sig != sceneSig) {
            List<Q> qs = new ArrayList<>(), ghosts = new ArrayList<>();
            java.util.Arrays.fill(mBox, null);
            flat(qs, -1e9f, -1e9f, proj(0, 0, 0), proj(groundWidth, 0, 0), proj(groundWidth, 0, groundDepth), proj(0, 0, groundDepth), 0xFF232A33);
            for (int i = 0; i <= groundWidth; i += 2) line(qs, -1e9f + 1, -1e9f + 1, proj(i, 0, 0), proj(i, 0, groundDepth), 1f, withAlpha(0xFFFFFF, i % 8 == 0 ? 34 : 14));
            for (int i = 0; i <= groundDepth; i += 2) line(qs, -1e9f + 1, -1e9f + 1, proj(0, 0, i), proj(groundWidth, 0, i), 1f, withAlpha(0xFFFFFF, i % 8 == 0 ? 34 : 14));
            for (int i = 0; i < machines.size(); i++) {
                float q = prog[i];
                Machine m = machines.get(i);
                if (q <= 0) { footprint(qs, m); continue; }
                ModelMesh me = mesh(i);
                if (me != null) drawMesh(qs, m, me, q);
                else box(qs, m, q);
                if (q < 1f) ghost(ghosts, m);
                mBox[i] = bbox(m, me != null ? me.maxY : m.cornerY() + m.height());
            }
            qs.sort((p, q) -> p.group != q.group ? Float.compare(p.group, q.group) : Float.compare(p.d, q.d));
            cachedQuads = qs;
            cachedGhost = ghosts;
            cachedBox = mBox.clone();
            sceneSig = sig;
        }
        List<Q> quads = cachedQuads, over = new ArrayList<>(cachedGhost);
        mBox = cachedBox;

        List<Q> pipeQuads = new ArrayList<>();
        hoverPipe = drag < 0 && inView(mx, my) ? pipeAt(mx, my) : -1;
        hoverMachine = !single && hoverPipe < 0 && drag < 0 && inView(mx, my) ? machineAt(mx, my) : -1;
        if (hoverMachine >= 0) highlight(over, machines.get(hoverMachine));
        hitSegs.clear();
        drawPipeBlocks(pipeQuads);
        pipeQuads.sort((p, q) -> p.group != q.group ? Float.compare(p.group, q.group) : Float.compare(p.d, q.d));
        g.enableScissor(x + 1, y + 1, x + w - 1, y + vh - 1);
        g.flush();
        List<Q> flatQuads = new ArrayList<>(), solidQuads = new ArrayList<>();
        for (Q quad : quads) (quad.z == null ? flatQuads : solidQuads).add(quad);
        solidQuads.addAll(pipeQuads);
        solidQuads.sort(java.util.Comparator.comparing(quad -> quad.tex == null ? "" : quad.tex.toString()));
        RenderSystem.clear(256, Minecraft.ON_OSX);
        flushQuads(g, flatQuads, false);
        flushQuads(g, solidQuads, true);
        RenderSystem.clear(256, Minecraft.ON_OSX);
        flushQuads(g, over, false);

        for (int i = 0; i < machines.size(); i++) {
            if (single || mBox[i] == null || stepOfMachine[i] < 0) continue;
            Machine m = machines.get(i);
            float bestD = -1e9f;
            float[] bp = null;
            for (int k = 0; k < 4; k++) {
                double cx = k % 2 == 0 ? m.cornerX() : m.cornerX() + m.width(), cz = k / 2 == 0 ? m.cornerZ() : m.cornerZ() + m.depth();
                float d = depth(cx, 0, cz);
                if (d > bestD) { bestD = d; bp = proj(cx, 0, cz); }
            }
            String num = Integer.toString(stepOfMachine[i] + 1);
            boolean cur = stepOfMachine[i] == step, hov = i == hoverMachine;
            int bw = Math.max(11, font.width(num) + 6), bx = (int) bp[0] - bw / 2, by = (int) bp[1] + 3;
            int edge = cur || hov ? 0xFFE8A33D : 0xFF4A4D56;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 11, edge);
            g.fill(bx, by, bx + bw, by + 10, 0xE0101216);
            g.drawString(font, num, bx + (bw - font.width(num)) / 2, by + 1, cur || hov ? 0xFFE8A33D : 0xFFC8CCD4, false);
            String tg = tag(m);
            if (!tg.isEmpty()) {
                int tx = bx + bw + 4, tw = font.width(tg);
                g.fill(tx - 3, by - 1, tx + tw + 3, by + 11, 0xE0101216);
                g.fill(tx - 3, by - 1, tx - 2, by + 11, 0xFF7ED6E0);
                g.drawString(font, tg, tx, by + 1, 0xFF7ED6E0, false);
            }
        }
        for (int pipeIndex = 0; pipeIndex < pipes.size(); pipeIndex++) {
            String item = data.pipeItems.get(pipeIndex);
            if (item == null || pipeProgress(pipeIndex) < 1f) continue;
            List<Block> pipeBlocks = data.blocksOfPipe.get(pipeIndex);
            Block middle = pipeBlocks.get(pipeBlocks.size() / 2);
            float[] screen = proj(middle.x + 0.5, middle.y + 1.2, middle.z + 0.5);
            MachineThumbs.drawStack(g, stackOf(item), (int) screen[0] - 8, (int) screen[1] - 8);
        }
        for (int i = 0; i < machines.size(); i++) {
            String makes = data.machineMakes.get(i);
            if (makes == null || mBox[i] == null || stepOfMachine[i] < 0 || stepOfMachine[i] > step) continue;
            Machine machine = machines.get(i);
            float[] top = proj(machine.cornerX() + machine.width() / 2.0, machine.cornerY() + machine.height() + 0.6, machine.cornerZ() + machine.depth() / 2.0);
            g.fill((int) top[0] - 10, (int) top[1] - 10, (int) top[0] + 10, (int) top[1] + 10, 0xE0101216);
            MachineThumbs.drawStack(g, stackOf(makes), (int) top[0] - 8, (int) top[1] - 8);
        }
        hoverPort = -1;
        if (single) drawPortMarkers(g, mx, my);
        g.disableScissor();
        if (single) g.drawString(font, tr("compendium.build.portshint"), x + 6, y + vh - 24, 0xFF6C727C, false);
        g.drawString(font, tr("compendium.build.hint"), x + 6, y + vh - 12, 0xFF6C727C, false);
        if (!single) drawStrip(g, font, x, y + vh, w, mx, my);
        g.disableScissor();
        if (hoverPipe >= 0) tooltip(g, font, mx, my);
        else if (hoverPort >= 0) portTooltip(g, font, mx, my);
        else if (hoverMachine >= 0) machineTooltip(g, font, mx, my);
    }

    private float[] bbox(Machine m, double top) {
        float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
        for (int k = 0; k < 8; k++) {
            float[] p = proj(k % 2 == 0 ? m.cornerX() : m.cornerX() + m.width(), (k / 2) % 2 == 0 ? m.cornerY() : top, k / 4 == 0 ? m.cornerZ() : m.cornerZ() + m.depth());
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        return new float[] {minX, minY, maxX, maxY, depth(m.cornerX() + m.width() / 2.0, (m.cornerY() + top) / 2, m.cornerZ() + m.depth() / 2.0)};
    }

    private int machineAt(int mx, int my) {
        int best = -1;
        for (int i = 0; i < mBox.length; i++) {
            float[] b = mBox[i];
            if (b == null || mx < b[0] || mx > b[2] || my < b[1] || my > b[3]) continue;
            if (best < 0 || b[4] > mBox[best][4]) best = i;
        }
        return best;
    }

    private void highlight(List<Q> over, Machine m) {
        double x0 = m.cornerX(), x1 = x0 + m.width(), z0 = m.cornerZ(), z1 = z0 + m.depth();
        double[][] g = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++)
            line(over, 0, 0, proj(g[i][0], 0, g[i][1]), proj(g[(i + 1) % 4][0], 0, g[(i + 1) % 4][1]), 2f, withAlpha(0xE8A33D, 230));
    }

    private void drawPortMarkers(GuiGraphics g, int mx, int my) {
        if (machineProgress(0) < 1f) return;
        Machine machine = machines.get(0);
        double towardsViewerX = sinA * cosF, towardsViewerY = sinF, towardsViewerZ = cosA * cosF;
        portMarkers.clear();
        for (Object[] spec : markerSpecs()) {
            String port = (String) spec[0];
            double[] portCenter = BuildData.portPoints(machine, port)[0], outward = BuildData.outwardOf((int) spec[1]);
            double outwardX = outward[0], outwardY = outward[1], outwardZ = outward[2];
            if (outwardX * towardsViewerX + outwardY * towardsViewerY + outwardZ * towardsViewerZ <= 0.05) continue;
            double[] marker = snapToModel(mesh(0), new double[] {portCenter[0] + outwardX * 0.5, portCenter[1] + outwardY * 0.5, portCenter[2] + outwardZ * 0.5}, outward);
            float[] screen = proj(marker[0], marker[1], marker[2]);
            int plateColor = spec.length > 2 ? (int) spec[2] : 0;
            portMarkers.add(new Object[] {port, screen[0], screen[1], plateColor != 0 ? plateColor : PORT_KIND_COLORS.getOrDefault(portKind(machine, port), 0xFF9AA0A8)});
        }
        final float hoverRadius = 8f;
        float nearest = hoverRadius * hoverRadius;
        if (drag < 0 && inView(mx, my)) {
            for (int k = 0; k < portMarkers.size(); k++) {
                float dx = (float) portMarkers.get(k)[1] - mx, dy = (float) portMarkers.get(k)[2] - my;
                if (dx * dx + dy * dy <= nearest) { nearest = dx * dx + dy * dy; hoverPort = k; }
            }
        }
        for (int k = 0; k < portMarkers.size(); k++) {
            int centerX = Math.round((float) portMarkers.get(k)[1]), centerY = Math.round((float) portMarkers.get(k)[2]);
            int color = (int) portMarkers.get(k)[3];
            int half = k == hoverPort ? 5 : 4;
            g.fill(centerX - half - 1, centerY - half - 1, centerX + half + 1, centerY + half + 1, k == hoverPort ? 0xFFE8A33D : 0xFF101216);
            g.fill(centerX - half, centerY - half, centerX + half, centerY + half, color);
        }
    }

    private List<Object[]> markerSpecs() {
        if (markerSpecs != null) return markerSpecs;
        ModelMesh mesh = mesh(0);
        if (mesh == null) return List.of();
        List<Object[]> specs = new ArrayList<>();
        Machine machine = machines.get(0);
        JsonObject multiblock = mb(machine.id());
        if (!multiblock.has("ports")) return specs;
        int[] size = intsOf(multiblock.getAsJsonArray("size")), controller = intsOf(multiblock.getAsJsonArray("controller"));
        for (JsonElement portElement : multiblock.getAsJsonArray("ports")) {
            JsonObject portJson = portElement.getAsJsonObject();
            String port = portJson.get("name").getAsString();
            if (!machine.portWalls().containsKey(port)) continue;
            List<Integer> plateWalls = com.sqc.nuclearsq.progression.PortFaces.platesOnWalls(machine.id(), size, controller, intsOf(portJson.getAsJsonArray("cell")));
            if (plateWalls.isEmpty()) {
                specs.add(markerSpec(mesh, machine, port, machine.portWalls().get(port)));
                continue;
            }
            double[] portCenter = BuildData.portPoints(machine, port)[0];
            int bestWall = -1;
            double bestChroma = -1;
            List<Integer> coloredWalls = new ArrayList<>();
            for (int wall : plateWalls) {
                int turnedWall = com.sqc.nuclearsq.progression.PortFaces.turnWall(wall, machine.quarterTurns());
                double[] outward = BuildData.outwardOf(turnedWall);
                double chroma = plateChroma(mesh, new double[] {portCenter[0] + outward[0] * 0.5, portCenter[1] + outward[1] * 0.5, portCenter[2] + outward[2] * 0.5}, outward);
                if (chroma >= MINIMUM_PLATE_CHROMA) coloredWalls.add(turnedWall);
                if (chroma > bestChroma) { bestChroma = chroma; bestWall = turnedWall; }
            }
            if (coloredWalls.isEmpty()) coloredWalls.add(bestWall);
            for (int turnedWall : coloredWalls) specs.add(markerSpec(mesh, machine, port, turnedWall));
        }
        markerSpecs = specs;
        return markerSpecs;
    }

    private static final double MINIMUM_PLATE_CHROMA = 55;

    private static Object[] markerSpec(ModelMesh mesh, Machine machine, String port, int wall) {
        double[] portCenter = BuildData.portPoints(machine, port)[0], outward = BuildData.outwardOf(wall);
        int color = plateColor(mesh, new double[] {portCenter[0] + outward[0] * 0.5, portCenter[1] + outward[1] * 0.5, portCenter[2] + outward[2] * 0.5}, outward);
        return new Object[] {port, wall, color};
    }

    /** Colour of the coloured port plate on this wall, brightened so the marker stays visible; 0 if the plate is grey or white. */
    private static int plateColor(ModelMesh mesh, double[] wallPoint, double[] outward) {
        int bestFace = -1;
        double bestChroma = 0;
        for (int face = 0; face < mesh.pos.size(); face++) {
            float[] normal = mesh.nrm.get(face), corners = mesh.pos.get(face);
            if (normal[0] * outward[0] + normal[1] * outward[1] + normal[2] * outward[2] < 0.6) continue;
            double distanceSquared = 0;
            for (int axis = 0; axis < 3; axis++) {
                double low = Math.min(Math.min(corners[axis], corners[3 + axis]), Math.min(corners[6 + axis], corners[9 + axis]));
                double high = Math.max(Math.max(corners[axis], corners[3 + axis]), Math.max(corners[6 + axis], corners[9 + axis]));
                double clamped = Math.max(low, Math.min(high, wallPoint[axis]));
                distanceSquared += (clamped - wallPoint[axis]) * (clamped - wallPoint[axis]);
            }
            if (distanceSquared > 0.45 * 0.45) continue;
            double chroma = chromaOf(mesh, face);
            if (chroma > bestChroma) { bestChroma = chroma; bestFace = face; }
        }
        if (bestFace < 0 || bestChroma < MINIMUM_PLATE_CHROMA) return 0;
        var image = imageOf(mesh.tex);
        if (image == null) return 0;
        float[] uv = mesh.uv.get(bestFace);
        float lowU = Math.min(Math.min(uv[0], uv[2]), Math.min(uv[4], uv[6])), highU = Math.max(Math.max(uv[0], uv[2]), Math.max(uv[4], uv[6]));
        float lowV = Math.min(Math.min(uv[1], uv[3]), Math.min(uv[5], uv[7])), highV = Math.max(Math.max(uv[1], uv[3]), Math.max(uv[5], uv[7]));
        int fromX = Math.max(0, (int) Math.floor(lowU * image.getWidth())), toX = Math.min(image.getWidth(), Math.max(fromX + 1, (int) Math.ceil(highU * image.getWidth())));
        int fromY = Math.max(0, (int) Math.floor(lowV * image.getHeight())), toY = Math.min(image.getHeight(), Math.max(fromY + 1, (int) Math.ceil(highV * image.getHeight())));
        long sumRed = 0, sumGreen = 0, sumBlue = 0;
        int count = 0;
        for (int pixelX = fromX; pixelX < toX; pixelX++) {
            for (int pixelY = fromY; pixelY < toY; pixelY++) {
                int abgr = image.getPixelRGBA(pixelX, pixelY);
                if ((abgr >>> 24) < 128) continue;
                int red = abgr & 255, green = (abgr >> 8) & 255, blue = (abgr >> 16) & 255;
                if (Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) < MINIMUM_PLATE_CHROMA) continue;
                sumRed += red; sumGreen += green; sumBlue += blue;
                count++;
            }
        }
        if (count == 0) return 0;
        double red = (double) sumRed / count, green = (double) sumGreen / count, blue = (double) sumBlue / count;
        double lift = 235.0 / Math.max(1, Math.max(red, Math.max(green, blue)));
        if (lift > 1) { red *= lift; green *= lift; blue *= lift; }
        return 0xFF000000 | ((int) Math.min(255, red) << 16) | ((int) Math.min(255, green) << 8) | (int) Math.min(255, blue);
    }
    private static final Map<ResourceLocation, com.mojang.blaze3d.platform.NativeImage> TEXTURE_IMAGES = new HashMap<>();

    private static com.mojang.blaze3d.platform.NativeImage imageOf(ResourceLocation texture) {
        if (texture == null) return null;
        com.mojang.blaze3d.platform.NativeImage cached = TEXTURE_IMAGES.get(texture);
        if (cached != null) return cached;
        try (var stream = Minecraft.getInstance().getResourceManager().open(texture)) {
            com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(stream);
            TEXTURE_IMAGES.put(texture, image);
            return image;
        } catch (Exception e) {
            return null;
        }
    }

    private static double chromaOf(ModelMesh mesh, int face) {
        var image = imageOf(mesh.tex);
        if (image == null) return 255;
        float[] uv = mesh.uv.get(face);
        float lowU = Math.min(Math.min(uv[0], uv[2]), Math.min(uv[4], uv[6])), highU = Math.max(Math.max(uv[0], uv[2]), Math.max(uv[4], uv[6]));
        float lowV = Math.min(Math.min(uv[1], uv[3]), Math.min(uv[5], uv[7])), highV = Math.max(Math.max(uv[1], uv[3]), Math.max(uv[5], uv[7]));
        int fromX = Math.max(0, (int) Math.floor(lowU * image.getWidth())), toX = Math.min(image.getWidth(), Math.max(fromX + 1, (int) Math.ceil(highU * image.getWidth())));
        int fromY = Math.max(0, (int) Math.floor(lowV * image.getHeight())), toY = Math.min(image.getHeight(), Math.max(fromY + 1, (int) Math.ceil(highV * image.getHeight())));
        double total = 0;
        int count = 0;
        for (int pixelX = fromX; pixelX < toX; pixelX++) {
            for (int pixelY = fromY; pixelY < toY; pixelY++) {
                int abgr = image.getPixelRGBA(pixelX, pixelY);
                if ((abgr >>> 24) < 128) continue;
                int red = abgr & 255, green = (abgr >> 8) & 255, blue = (abgr >> 16) & 255;
                total += Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private static double plateChroma(ModelMesh mesh, double[] wallPoint, double[] outward) {
        double best = 0;
        for (int face = 0; face < mesh.pos.size(); face++) {
            float[] normal = mesh.nrm.get(face), corners = mesh.pos.get(face);
            if (normal[0] * outward[0] + normal[1] * outward[1] + normal[2] * outward[2] < 0.6) continue;
            double distanceSquared = 0;
            for (int axis = 0; axis < 3; axis++) {
                double low = Math.min(Math.min(corners[axis], corners[3 + axis]), Math.min(corners[6 + axis], corners[9 + axis]));
                double high = Math.max(Math.max(corners[axis], corners[3 + axis]), Math.max(corners[6 + axis], corners[9 + axis]));
                double clamped = Math.max(low, Math.min(high, wallPoint[axis]));
                distanceSquared += (clamped - wallPoint[axis]) * (clamped - wallPoint[axis]);
            }
            if (distanceSquared <= 0.45 * 0.45) best = Math.max(best, chromaOf(mesh, face));
        }
        return best;
    }

    private static int[] intsOf(com.google.gson.JsonArray array) {
        int[] values = new int[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsInt();
        return values;
    }

    private static double[] snapToModel(ModelMesh mesh, double[] wallPoint, double[] outward) {
        if (mesh == null) return wallPoint;
        double nearest = 1.0;
        double[] snapped = wallPoint;
        for (int face = 0; face < mesh.pos.size(); face++) {
            float[] normal = mesh.nrm.get(face), corners = mesh.pos.get(face);
            if (normal[0] * outward[0] + normal[1] * outward[1] + normal[2] * outward[2] < 0.6) continue;
            double[] clamped = new double[3];
            double distanceSquared = 0;
            for (int axis = 0; axis < 3; axis++) {
                double low = Math.min(Math.min(corners[axis], corners[3 + axis]), Math.min(corners[6 + axis], corners[9 + axis]));
                double high = Math.max(Math.max(corners[axis], corners[3 + axis]), Math.max(corners[6 + axis], corners[9 + axis]));
                clamped[axis] = Math.max(low, Math.min(high, wallPoint[axis]));
                distanceSquared += (clamped[axis] - wallPoint[axis]) * (clamped[axis] - wallPoint[axis]);
            }
            if (distanceSquared < nearest * nearest) {
                nearest = Math.sqrt(distanceSquared);
                snapped = clamped;
            }
        }
        return snapped;
    }

    private String kindNameOf(String kind) {
        Language lang = Language.getInstance();
        return lang.has("gui.nuclearsq.term.kind." + kind) ? tr("gui.nuclearsq.term.kind." + kind) : tr("block.nuclearsq.machine_port_" + kind + "_sqc");
    }

    private void portTooltip(GuiGraphics g, Font font, int mx, int my) {
        Machine machine = machines.get(0);
        String port = (String) portMarkers.get(hoverPort)[0];
        String kind = portKind(machine, port);
        Language lang = Language.getInstance();
        String labelKey = "port.nuclearsq." + machine.id() + "." + port;
        String label = lang.has(labelKey) ? tr(labelKey) : port;
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(label).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), 230));
        lines.addAll(font.split(Component.literal(kindNameOf(kind)).withStyle(style -> style.withColor((int) portMarkers.get(hoverPort)[3] & 0xFFFFFF)), 230));
        Object[] rate = portRate(machine.id(), port);
        if (rate != null) lines.addAll(font.split(Component.literal(tr("compendium.build.tip.rate").formatted(String.format(java.util.Locale.ROOT, "%,d", (int) rate[0]) + " " + rate[1])), 230));
        String hintKey = "gui.nuclearsq.term.kindhint." + kind;
        lines.addAll(font.split(Component.literal(tr(lang.has(hintKey) ? hintKey : "compendium.build.tip.pipe")), 230));
        drawTip(g, font, mx, my, lines);
    }

    private void machineTooltip(GuiGraphics g, Font font, int mx, int my) {
        Machine m = machines.get(hoverMachine);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(machineName(m)).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), 230));
        String note = note(m.key());
        if (!note.isEmpty()) lines.addAll(font.split(Component.literal(note), 230));
        else if (m.page() != null) lines.addAll(font.split(Component.literal(blurb(Language.getInstance().getOrDefault("compendium." + m.page() + ".text"), 190)), 230));
        drawTip(g, font, mx, my, lines);
    }

    private int pipeAt(int mx, int my) {
        for (int k = hitSegs.size() - 1; k >= 0; k--) {
            float[] s = hitSegs.get(k);
            float dx = s[2] - s[0], dy = s[3] - s[1], len2 = dx * dx + dy * dy;
            float f = len2 < 1e-6f ? 0 : Math.max(0, Math.min(1, ((mx - s[0]) * dx + (my - s[1]) * dy) / len2));
            float px = s[0] + dx * f - mx, py = s[1] + dy * f - my;
            if (px * px + py * py <= s[4] * s[4]) return (int) s[5];
        }
        return -1;
    }

    private JsonObject mb(String id) {
        return mbCache.computeIfAbsent(id, k -> {
            try { return BuildData.multiblock(k); } catch (Exception e) { return new JsonObject(); }
        });
    }

    private String portKind(Machine m, String port) {
        JsonObject o = mb(m.id());
        if (o.has("ports")) for (JsonElement pe : o.getAsJsonArray("ports")) {
            JsonObject po = pe.getAsJsonObject();
            if (po.get("name").getAsString().equals(port)) return po.get("type").getAsString();
        }
        return "";
    }

    private static Object[] portRate(String machine, String port) {
        try {
            if (cooling == null) {
                try (var in = BuildScene.class.getResourceAsStream("/data/nuclearsq/cooling_values.json")) {
                    cooling = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            JsonObject p = cooling.getAsJsonObject("machines").getAsJsonObject(machine).getAsJsonObject("ports").getAsJsonObject(port);
            String unit = p.get("unit").getAsString();
            int rate = p.get("rate").getAsInt();
            return rate > 0 && (unit.equals("mB/t") || unit.startsWith("FE")) ? new Object[] {rate, unit} : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String machineName(Machine m) {
        return m.page() != null ? tr("compendium." + m.page() + ".title") : tr(prefix + ".m." + m.key());
    }

    private static String portLabel(Machine m, String port) {
        if (m.id().startsWith("block:") || (port.equals("data") && !Language.getInstance().has("port.nuclearsq." + m.id() + "." + port))) return tr("compendium.build.port.data");
        return tr("port.nuclearsq." + m.id() + "." + port);
    }

    private static net.minecraft.world.item.ItemStack iconOf(Machine machine) {
        String itemId = machine.id().startsWith("block:") ? machine.id().substring(6) : machine.id() + "_controller_sqc";
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("nuclearsq", itemId)));
    }

    private void tooltip(GuiGraphics g, Font font, int mx, int my) {
        Pipe pipe = pipes.get(hoverPipe);
        Machine from = machines.get(pipe.fromMachine()), to = machines.get(pipe.toMachine());
        String kind = pipe.fromPort().equals("data") ? "data" : portKind(from, pipe.fromPort());
        Language lang = Language.getInstance();
        String kindName = kind.equals("data") ? tr("compendium.build.kind.data")
                : lang.has("gui.nuclearsq.term.kind." + kind) ? tr("gui.nuclearsq.term.kind." + kind) : tr("block.nuclearsq.machine_port_" + kind + "_sqc");
        String hint = kind.equals("data") ? tr("compendium.build.hint.data")
                : lang.has("gui.nuclearsq.term.kindhint." + kind) ? tr("gui.nuclearsq.term.kindhint." + kind) : tr("compendium.build.tip.pipe");
        String fromName = machineName(from), toName = machineName(to);
        String fromPort = portLabel(from, pipe.fromPort()), toPort = portLabel(to, pipe.toPort());
        Object[] fromRate = portRate(from.id(), pipe.fromPort()), toRate = portRate(to.id(), pipe.toPort());
        Object[] rate = fromRate == null ? toRate : toRate == null ? fromRate : (int) fromRate[0] <= (int) toRate[0] ? fromRate : toRate;
        String rateText = rate == null ? "" : tr("compendium.build.tip.rate").formatted(String.format(java.util.Locale.ROOT, "%,d", (int) rate[0]) + " " + rate[1]);

        String carried = data.pipeItems.get(hoverPipe);
        String carriedText = carried == null ? "" : tr("compendium.build.carries").formatted(stackOf(carried).getHoverName().getString());
        final int padding = 6, rowHeight = 18, arrowGap = 9, iconColumn = 20;
        int nameWidth = Math.max(font.width(fromName), font.width(toName)), portWidth = Math.max(font.width(fromPort), font.width(toPort));
        int innerWidth = Math.max(Math.max(font.width(kindName), iconColumn + nameWidth + 14 + portWidth), 150);
        List<FormattedCharSequence> hintLines = font.split(Component.literal(hint), innerWidth);
        int height = padding + 12 + rowHeight + arrowGap + rowHeight + (rateText.isEmpty() ? 0 : 12) + (carried == null ? 0 : rowHeight) + 9 + hintLines.size() * 10 + padding - 2;
        int width = innerWidth + 2 * padding;
        int[] corner = tipCorner(mx, my, width, height);
        int left = corner[0], top = corner[1];

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(left - 1, top - 1, left + width + 1, top + height + 1, 0xFF5B5B66);
        g.fill(left, top, left + width, top + height, 0xF2141418);
        g.drawString(font, Component.literal(kindName).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), left + padding, top + padding, 0xFFE8A33D, false);
        int rowTop = top + padding + 12;
        String[] names = {fromName, toName}, ports = {fromPort, toPort};
        Machine[] ends = {from, to};
        for (int end = 0; end < 2; end++) {
            MachineThumbs.drawStack(g, iconOf(ends[end]), left + padding, rowTop);
            g.drawString(font, names[end], left + padding + iconColumn, rowTop + 5, 0xFFFFFFFF, false);
            g.drawString(font, ports[end], left + width - padding - font.width(ports[end]), rowTop + 5, 0xFF9AA0A8, false);
            rowTop += rowHeight;
            if (end == 0) {
                g.drawString(font, "\u2193", left + padding + 5, rowTop - 1, 0xFF7ED6E0, false);
                rowTop += arrowGap;
            }
        }
        if (!rateText.isEmpty()) {
            g.drawString(font, rateText, left + padding, rowTop + 1, 0xFF7ED6E0, false);
            rowTop += 12;
        }
        if (carried != null) {
            MachineThumbs.drawStack(g, stackOf(carried), left + padding, rowTop);
            g.drawString(font, carriedText, left + padding + iconColumn, rowTop + 5, 0xFFE0C060, false);
            rowTop += rowHeight;
        }
        g.fill(left + padding, rowTop + 2, left + width - padding, rowTop + 3, 0xFF3A3D46);
        for (int line = 0; line < hintLines.size(); line++) g.drawString(font, hintLines.get(line), left + padding, rowTop + 7 + line * 10, 0xFF8E8E96, false);
        g.pose().popPose();
    }

    private static int[] tipCorner(int mx, int my, int width, int height) {
        var window = Minecraft.getInstance().getWindow();
        int left = mx + 12, top = my + 12;
        if (left + width > window.getGuiScaledWidth() - 4) left = mx - 12 - width;
        if (top + height > window.getGuiScaledHeight() - 4) top = my - height - 8;
        return new int[] {left, top};
    }

    private static void drawTip(GuiGraphics g, Font font, int mx, int my, List<FormattedCharSequence> lines) {
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        w += 10;
        int h = lines.size() * 10 + 8;
        int[] corner = tipCorner(mx, my, w, h);
        int x = corner[0], y = corner[1];
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF5B5B66);
        g.fill(x, y, x + w, y + h, 0xF2141418);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 5, y + 5 + i * 10, 0xFFDDDDE0, false);
        g.pose().popPose();
    }

    private static void flushQuads(GuiGraphics g, List<Q> list, boolean depthTested) {
        if (list.isEmpty()) return;
        Matrix4f m = g.pose().last().pose();
        if (depthTested) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int i = 0;
        while (i < list.size()) {
            ResourceLocation tex = list.get(i).tex;
            int j = i;
            while (j < list.size() && list.get(j).tex == tex) j++;
            AbstractTexture at = null;
            BufferBuilder b;
            if (tex == null) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int k = i; k < j; k++) {
                    Q q = list.get(k);
                    for (int v = 0; v < 4; v++) b.addVertex(m, q.xy[v * 2], q.xy[v * 2 + 1], q.z == null ? 0f : q.z[v]).setColor(q.c);
                }
            } else {
                if (!tex.equals(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)) {
                    at = Minecraft.getInstance().getTextureManager().getTexture(tex);
                    at.setFilter(true, false);
                }
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, tex);
                b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (int k = i; k < j; k++) {
                    Q q = list.get(k);
                    for (int v = 0; v < 4; v++) b.addVertex(m, q.xy[v * 2], q.xy[v * 2 + 1], q.z == null ? 0f : q.z[v]).setUv(q.uv[v * 2], q.uv[v * 2 + 1]).setColor(q.c);
                }
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
            if (at != null) at.setFilter(false, false);
            i = j;
        }
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static net.minecraft.world.item.ItemStack stackOf(String itemId) {
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
    }

    private String flowText(int stepIndex) {
        int machineIndex = steps.get(stepIndex).machines().get(0);
        StringBuilder text = new StringBuilder();
        String makes = data.machineMakes.get(machineIndex);
        if (makes != null) {
            String destination = data.machineMakesTo.get(machineIndex);
            String product = stackOf(makes).getHoverName().getString();
            text.append(" ").append(destination == null ? tr("compendium.build.makes").formatted(product) : tr("compendium.build.crafts").formatted(product, machineNameOfKey(destination)));
        }
        for (int pipeIndex = 0; pipeIndex < pipes.size(); pipeIndex++) {
            String item = data.pipeItems.get(pipeIndex);
            Pipe pipe = pipes.get(pipeIndex);
            if (item == null || pipe.fromMachine() != machineIndex) continue;
            text.append(" ").append(tr("compendium.build.sends").formatted(stackOf(item).getHoverName().getString(), machineName(machines.get(pipe.toMachine()))));
        }
        return text.toString();
    }

    private String machineNameOfKey(String nodeKey) {
        for (Machine machine : machines) if (machine.key().equals(nodeKey)) return machineName(machine);
        return nodeKey;
    }

    private String titleOf(int stepIndex) {
        return tr("compendium." + captions.get(stepIndex)[0] + ".title");
    }

    private String textOf(int stepIndex) {
        if (textCache[stepIndex] == null) textCache[stepIndex] = buildTextOf(stepIndex);
        return textCache[stepIndex];
    }

    private String buildTextOf(int stepIndex) {
        String[] caption = captions.get(stepIndex);
        String note = note(caption[2]);
        String text = !note.isEmpty() ? note : blurb(Language.getInstance().getOrDefault("compendium." + caption[0] + ".text"), 190);
        text += flowText(stepIndex);
        if (caption[1] != null) text = tr("compendium.build.link").formatted(tr("compendium." + caption[1] + ".title")) + " " + text;
        return text;
    }

    private String note(String key) {
        String k = prefix + ".note." + key;
        return Language.getInstance().has(k) ? tr(k) : "";
    }

    private String tag(Machine m) {
        String k = prefix + ".tag." + m.key();
        return Language.getInstance().has(k) ? tr(k) : "";
    }

    static String blurb(String raw, int limit) {
        for (String line : raw.split("\n")) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("{")) continue;
            t = t.replaceAll("\\{[^}]*\\}", "").replace("**", "").strip();
            int cut = t.indexOf(". ");
            if (cut > 20) t = t.substring(0, cut + 1);
            return t.length() > limit ? t.substring(0, limit - 3) + "..." : t;
        }
        return "";
    }

    private static String tr(String key) {
        return Language.getInstance().getOrDefault(key).replace("**", "");
    }

    private void drawStrip(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        buttons.clear();
        g.fill(x, y, x + w, y + stripHeight, 0xFF16181C);
        g.fill(x, y, x + w, y + 1, 0xFF38383C);
        int bx = x + 6, by = y + 5, bs = 14;
        button(g, bx, by, bs, 0, mx, my, false);
        button(g, bx + 17, by, bs, 1, mx, my, playing);
        button(g, bx + 34, by, bs, 2, mx, my, false);
        button(g, bx + 51, by, bs, 3, mx, my, false);
        int sx = bx + 74;
        for (int k = 0; k < steps.size(); k++) {
            int x0 = sx + k * 16;
            boolean act = k == step, done = k < step;
            boolean hov = mx >= x0 && mx < x0 + 14 && my >= by && my < by + bs;
            g.fill(x0, by, x0 + 14, by + bs, act ? 0xFFE8A33D : hov ? 0xFF3A3D46 : done ? 0xFF2D4A38 : 0xFF2A2A2D);
            String n = Integer.toString(k + 1);
            g.drawString(font, n, x0 + 7 - font.width(n) / 2, by + 3, act ? 0xFF101216 : 0xFFDDDDE0, false);
            buttons.add(new int[] {x0, by, x0 + 14, by + bs, 10 + k});
        }
        String head = tr("compendium.build.step").formatted(step + 1, steps.size()) + " " + titleOf(step);
        g.drawString(font, Component.literal(font.plainSubstrByWidth(head, w - 12)).withStyle(net.minecraft.ChatFormatting.BOLD), x + 6, y + 24, 0xFFFFFFFF, false);
        int ly = y + 35;
        List<FormattedCharSequence> lines = font.split(Component.literal(textOf(step)), w - 12);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            if (i == 2 && lines.size() > 3) {
                StringBuilder sb = new StringBuilder();
                lines.get(2).accept((k, st, cp) -> { sb.appendCodePoint(cp); return true; });
                g.drawString(font, font.plainSubstrByWidth(sb.toString().stripTrailing(), w - 12 - font.width("...")) + "...", x + 6, ly + i * 10, 0xFFB4B8C0, false);
            } else g.drawString(font, lines.get(i), x + 6, ly + i * 10, 0xFFB4B8C0, false);
        }
        float total = 0, doneT = 0;
        for (int k = 0; k < steps.size(); k++) { total += steps.get(k).totalSeconds(); if (k < step) doneT += steps.get(k).totalSeconds(); }
        doneT += Math.min(t, steps.get(step).totalSeconds());
        g.fill(x, y + stripHeight - 2, x + w, y + stripHeight, 0xFF101216);
        g.fill(x, y + stripHeight - 2, x + (int) (w * doneT / total), y + stripHeight, 0xFFE8A33D);
    }

    int totalHeight() {
        return visibleHeight;
    }

    private void button(GuiGraphics g, int x, int y, int s, int id, int mx, int my, boolean pause) {
        boolean hov = mx >= x && mx < x + s && my >= y && my < y + s;
        g.fill(x, y, x + s, y + s, hov ? 0xFF3A3D46 : 0xFF2A2A2D);
        int c = 0xFFDDDDE0, cx = x + s / 2, cy = y + s / 2;
        switch (id) {
            case 1 -> {
                if (pause) { g.fill(cx - 3, cy - 4, cx - 1, cy + 4, c); g.fill(cx + 1, cy - 4, cx + 3, cy + 4, c); }
                else drawTri(g, cx + 3, cy, 1, c);
            }
            case 0 -> { g.fill(cx - 4, cy - 4, cx - 3, cy + 5, c); drawTri(g, cx - 2, cy, -1, c); }
            case 3 -> { drawTri(g, cx - 1, cy, 1, c); drawTri(g, cx + 3, cy, 1, c); g.fill(cx + 4, cy - 4, cx + 5, cy + 5, c); }
            default -> { g.fill(cx + 3, cy - 4, cx + 4, cy + 5, c); drawTri(g, cx + 2, cy, 1, c); }
        }
        buttons.add(new int[] {x, y, x + s, y + s, id});
    }

    private static void drawTri(GuiGraphics g, int x, int y, int dir, int c) {
        for (int i = 0; i < 5; i++) {
            int xx = x - dir * i;
            g.fill(xx, y - i, xx + 1, y + i + 1, c);
        }
    }

    private boolean inView(double mx, double my) {
        return mx >= ax && mx < ax + aw && my >= Math.max(ay, visibleTop) && my < Math.min(ay + vh, visibleTop + visibleHeight);
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (inView(mx, my)) {
            drag = button;
            dragMoved = false;
            pressedPage = !single && hoverMachine >= 0 ? machines.get(hoverMachine).page() : null;
            return true;
        }
        if (button == 0 && mx >= ax && mx < ax + aw && my >= Math.max(ay + vh, visibleTop) && my < Math.min(ay + vh + stripHeight, visibleTop + visibleHeight)) {
            for (int[] b : buttons) {
                if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                    int id = b[4];
                    if (id == 0) jump(step - 1);
                    else if (id == 2) jump(step + 1);
                    else if (id == 3) finish();
                    else if (id == 1) {
                        if (!playing && step >= steps.size() - 1 && t >= steps.get(step).totalSeconds() - 1e-3f) { step = 0; t = 0; playing = true; }
                        else playing = !playing;
                    } else jump(id - 10);
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    boolean dragging() {
        return drag >= 0;
    }

    void mouseDragged(double dx, double dy) {
        if (Math.abs(dx) + Math.abs(dy) > 0.5) dragMoved = true;
        if (drag == 0) {
            yaw += (float) dx * 0.012f;
            pitch = Math.max(0.1f, Math.min(1.5f, pitch + (float) dy * 0.008f));
        }
        else if (drag == 1) { panX += (float) dx; panY += (float) dy; }
    }

    String mouseReleased() {
        String target = drag != 0 || dragMoved ? null : pressedPage;
        drag = -1;
        return target;
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (!inView(mx, my)) return false;
        zoom = Math.max(0.5f, Math.min(5f, zoom * (sy > 0 ? 1.12f : 1 / 1.12f)));
        return true;
    }
}
