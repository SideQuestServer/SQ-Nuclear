package com.sqc.nuclearsq.machine.big.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sqc.nuclearsq.NuclearClientConfig;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigMachineControllerBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class ChunkMachines {
    private ChunkMachines() {}

    public record Payload(BigMachine machine, int rot, long pos) {}

    public static final ModelProperty<Payload> PROP = new ModelProperty<>();

    private record Key(BigMachine machine, int rot) {}

    private record Info(boolean glow, boolean dynamic) {}

    private static final Map<Key, List<BakedQuad>> QUADS = new ConcurrentHashMap<>();
    private static final Map<Key, Info> INFO = new ConcurrentHashMap<>();
    private static final java.util.Set<Key> FAILED = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Long> DELIVERED = new ConcurrentHashMap<>();
    private static final Map<Long, Key> REQUESTED = new ConcurrentHashMap<>();
    private static final java.util.Set<Long> HIDDEN = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Long> HIDE_SINCE = new ConcurrentHashMap<>();
    private static final long HIDE_TICKS = 20;
    private static final Map<Long, Long> RETURNING = new ConcurrentHashMap<>();
    private static final long RETURN_NS = 1_500_000_000L;
    private static final long OVERLAP_NS = 700_000_000L;
    private record Placement(long pos, Key key, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int sections,
                             Map<Long, List<BakedQuad>> bySection, long t0, int light, boolean faceLight) {
        java.util.Set<Long> need() {
            return bySection.keySet();
        }

        boolean touches(BlockPos o) {
            return o.getX() <= maxX && o.getX() + 15 >= minX && o.getY() <= maxY && o.getY() + 15 >= minY
                    && o.getZ() <= maxZ && o.getZ() + 15 >= minZ;
        }
    }

    private static final Map<Long, Placement> PLACEMENTS = new ConcurrentHashMap<>();
    private static final Map<Long, java.util.Set<Long>> SEEN = new ConcurrentHashMap<>();
    private static final Map<Key, List<BakedQuad>> SLICED = new ConcurrentHashMap<>();
    private static final float PIECE = 4f;
    private static final float SMALL_PIECE = 2f;

    private static final int MIN = -7, MAX = 23;

    public static volatile int holeMode;

    private static boolean shaderSeen;

    private static boolean shaderOn() {
        return com.sqc.nuclearsq.compat.iris.IrisCompat.shaderPackInUse();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        boolean now = shaderOn();
        if (now == shaderSeen) return;
        shaderSeen = now;
        reset();
        Minecraft.getInstance().levelRenderer.allChanged();
    }

    static boolean chunkable(BigMachineBlockEntity be) {
        return be.machine().chunkBaked() && NuclearClientConfig.chunkBaking() && BakedMachines.active()
                && !shaderOn()
                && !be.isRemoved() && be.formedRot() >= 0 && be.getLevel() != null;
    }

    public static boolean eligible(BigMachineBlockEntity be) {
        return chunkable(be) && fits(be);
    }

    private static final boolean SODIUM = net.neoforged.fml.ModList.get().isLoaded("sodium");

    private static boolean fits(BigMachineBlockEntity be) {
        BigMachine m = be.machine();
        if (!SODIUM || m == BigMachine.CONTAINMENT) return false;
        int rot = be.formedRot();
        BlockPos pos = be.getBlockPos();
        BlockPos a = m.structure.world(pos, rot, 0, 0, 0), b = m.structure.world(pos, rot, m.w - 1, m.h - 1, m.d - 1);
        int lx = pos.getX() & 15, ly = pos.getY() & 15, lz = pos.getZ() & 15;
        return Math.min(a.getX(), b.getX()) - pos.getX() + lx >= MIN && Math.max(a.getX(), b.getX()) + 1 - pos.getX() + lx <= MAX
                && Math.min(a.getY(), b.getY()) - pos.getY() + ly >= MIN && Math.max(a.getY(), b.getY()) + 1 - pos.getY() + ly <= MAX
                && Math.min(a.getZ(), b.getZ()) - pos.getZ() + lz >= MIN && Math.max(a.getZ(), b.getZ()) + 1 - pos.getZ() + lz <= MAX;
    }

    public static ModelData modelData(BigMachineBlockEntity be) {
        if (!eligible(be)) return ModelData.EMPTY;
        return ModelData.builder().with(PROP, new Payload(be.machine(), be.formedRot(), be.getBlockPos().asLong())).build();
    }

    public static void forget(BlockPos pos) {
        DELIVERED.remove(pos.asLong());
        REQUESTED.remove(pos.asLong());
        SEEN.remove(pos.asLong());
        HIDDEN.remove(pos.asLong());
        HIDE_SINCE.remove(pos.asLong());
        RETURNING.remove(pos.asLong());
        Placement pl = PLACEMENTS.remove(pos.asLong());
        if (pl != null) dirty(pl);
    }

    private static void dirty(Placement p) {
        try {
            Minecraft.getInstance().levelRenderer.setBlocksDirty(p.minX(), p.minY(), p.minZ(), p.maxX(), p.maxY(), p.maxZ());
        } catch (RuntimeException e) {
        }
    }

    static void occluded(BigMachineBlockEntity be, boolean occluded) {
        reports++;
        if (!chunkable(be)) {
            notChunkable++;
            return;
        }
        if (occluded) reportsOccluded++;
        long pos = be.getBlockPos().asLong(), now = be.getLevel().getGameTime();
        if (!occluded) {
            HIDE_SINCE.remove(pos);
            if (HIDDEN.remove(pos)) {
                RETURNING.put(pos, System.nanoTime());
                remesh(be, pos);
            }
            return;
        }
        long since = HIDE_SINCE.computeIfAbsent(pos, k -> now);
        if (now < since) HIDE_SINCE.put(pos, now);
        else if (now - since < HIDE_TICKS) waiting++;
        else if (!delivered(pos)) notDelivered++;
        else if (HIDDEN.add(pos)) remesh(be, pos);
    }

    private static void remesh(BigMachineBlockEntity be, long pos) {
        Placement pl = PLACEMENTS.get(pos);
        if (pl != null) {
            dirty(pl);
            return;
        }
        BlockPos p = be.getBlockPos();
        try {
            Minecraft.getInstance().levelRenderer.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
        } catch (RuntimeException e) {
        }
    }

    private static boolean returning(long pos) {
        Long t = RETURNING.get(pos);
        if (t == null) return false;
        if (System.nanoTime() - t < RETURN_NS) return true;
        RETURNING.remove(pos);
        return false;
    }

    private static boolean delivered(long pos) {
        Long t = DELIVERED.get(pos);
        return t != null && System.nanoTime() - t > OVERLAP_NS;
    }

    static boolean update(BigMachineBlockEntity be, ResourceLocation texture, boolean dynamic, BakedMachines.Painter painter) {
        if (!chunkable(be)) return false;
        Key key = new Key(be.machine(), be.formedRot());
        if (FAILED.contains(key)) return false;
        if (!QUADS.containsKey(key)) {
            record(key, texture, dynamic, painter);
            if (!QUADS.containsKey(key)) return false;
        }
        long pos = be.getBlockPos().asLong();
        if (returning(pos)) return false;
        if (!fits(be)) return updateSections(be, key, pos);
        if (!key.equals(REQUESTED.get(pos))) {
            REQUESTED.put(pos, key);
            DELIVERED.remove(pos);
            be.requestModelDataUpdate();
            BlockPos p = be.getBlockPos();
            Minecraft.getInstance().levelRenderer.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
        }
        return delivered(pos);
    }

    private static boolean updateSections(BigMachineBlockEntity be, Key key, long pos) {
        Placement pl = PLACEMENTS.get(pos);
        if (pl == null || !pl.key().equals(key)) {
            BigMachine m = be.machine();
            BlockPos p = be.getBlockPos(), a = m.structure.world(p, key.rot(), 0, 0, 0), b = m.structure.world(p, key.rot(), m.w - 1, m.h - 1, m.d - 1);
            int x0 = Math.min(a.getX(), b.getX()) - 1, x1 = Math.max(a.getX(), b.getX()) + 1;
            int y0 = Math.min(a.getY(), b.getY()) - 1, y1 = Math.max(a.getY(), b.getY()) + 1;
            int z0 = Math.min(a.getZ(), b.getZ()) - 1, z1 = Math.max(a.getZ(), b.getZ()) + 1;
            Map<Long, List<BakedQuad>> bySection = assign(be.getLevel(), key, p);
            for (long s : bySection.keySet()) {
                x0 = Math.min(x0, BlockPos.getX(s) << 4); x1 = Math.max(x1, (BlockPos.getX(s) << 4) + 15);
                y0 = Math.min(y0, BlockPos.getY(s) << 4); y1 = Math.max(y1, (BlockPos.getY(s) << 4) + 15);
                z0 = Math.min(z0, BlockPos.getZ(s) << 4); z1 = Math.max(z1, (BlockPos.getZ(s) << 4) + 15);
            }
            int n = ((x1 >> 4) - (x0 >> 4) + 1) * ((y1 >> 4) - (y0 >> 4) + 1) * ((z1 >> 4) - (z0 >> 4) + 1);
            pl = new Placement(pos, key, x0, y0, z0, x1, y1, z1, n, bySection, System.nanoTime(), machineLight(be),
                    m == BigMachine.CONTAINMENT);
            SEEN.put(pos, ConcurrentHashMap.newKeySet());
            DELIVERED.remove(pos);
            PLACEMENTS.put(pos, pl);
            dirty(pl);
        }
        java.util.Set<Long> seen = SEEN.get(pos);
        if (seen != null && !DELIVERED.containsKey(pos)
                && (seen.containsAll(pl.need()) || !seen.isEmpty() && System.nanoTime() - pl.t0() > 3_000_000_000L)) {
            DELIVERED.put(pos, System.nanoTime());
        }
        return delivered(pos);
    }

    static String why(BigMachineBlockEntity be) {
        if (!chunkable(be)) return "nicht backbar";
        long pos = be.getBlockPos().asLong();
        if (returning(pos)) return "kommt zurueck";
        Info i = INFO.get(new Key(be.machine(), be.formedRot()));
        if (i == null) return "Mesh noch nicht aufgenommen";
        if (!delivered(pos)) return "Mesh noch nicht geliefert";
        if (i.dynamic()) return "bewegte Teile";
        if (i.glow()) return "Leuchtbild";
        return "sonst";
    }

    static boolean idle(BigMachineBlockEntity be) {
        BigMachine m = be.machine();
        if (!m.chunkBaked() || be.formedRot() < 0) return false;
        Info i = INFO.get(new Key(m, be.formedRot()));
        return i != null && !i.dynamic() && (!i.glow() || BigMachineGlowLayer.glowFrame(be) < 0
                || !NuclearClientConfig.inGlowRange(be.getBlockPos()))
                && chunkable(be) && delivered(be.getBlockPos().asLong()) && !returning(be.getBlockPos().asLong());
    }

    private static void record(Key key, ResourceLocation texture, boolean dynamic, BakedMachines.Painter painter) {
        try {
            Map<RenderType, BakedMachines.Rec> recs = BakedMachines.record(painter);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),
                    texture.getPath().replaceFirst("^textures/", "").replaceFirst("\\.png$", ""));
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(id);
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                NuclearSQ.LOGGER.warn("Chunk-Backen {}: Textur {} nicht im Block-Atlas, zeichne wie bisher", key, id);
                FAILED.add(key);
                return;
            }
            List<BakedQuad> out = new ArrayList<>();
            boolean glow = false;
            for (Map.Entry<RenderType, BakedMachines.Rec> en : recs.entrySet()) {
                if (BakedMachines.isGlow(en.getKey())) {
                    glow = true;
                    continue;
                }
                if (en.getKey().sortOnUpload()) {
                    FAILED.add(key);
                    return;
                }
                if (!quads(en.getValue(), sprite, out)) {
                    FAILED.add(key);
                    return;
                }
            }
            INFO.put(key, new Info(glow, dynamic));
            QUADS.put(key, List.copyOf(out));
        } catch (RuntimeException ex) {
            NuclearSQ.LOGGER.warn("Chunk-Backen {} fehlgeschlagen, zeichne wie bisher", key, ex);
            FAILED.add(key);
        }
    }

    private static boolean quads(BakedMachines.Rec r, TextureAtlasSprite sprite, List<BakedQuad> out) {
        if (r.count % 4 != 0) return false;
        final int n = BakedMachines.Rec.N;
        for (int q = 0; q < r.count / 4; q++) {
            float[][] pt = new float[4][5];
            int[] col = new int[4];
            for (int k = 0; k < 4; k++) {
                int o = (q * 4 + k) * n;
                pt[k] = new float[] {r.data[o], r.data[o + 1], r.data[o + 2], r.data[o + 4], r.data[o + 5]};
                col[k] = r.col[q * 4 + k];
            }
            int o = q * 4 * n;
            float[] nrm = {r.data[o + 10], r.data[o + 11], r.data[o + 12]};
            Direction dir = Direction.getNearest(nrm[0], nrm[1], nrm[2]);
            boolean inside = true;
            for (float[] p : pt) if (p[3] < -1e-3f || p[3] > 1.001f || p[4] < -1e-3f || p[4] > 1.001f) inside = false;
            if (inside) addQuad(out, sprite, pt, col, nrm, dir);
            else for (float[][] piece : wrapPieces(pt)) addQuad(out, sprite, piece, col, nrm, dir);
        }
        return true;
    }

    private static void addQuad(List<BakedQuad> out, TextureAtlasSprite sprite, float[][] pt, int[] col, float[] nrm, Direction dir) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        int[] v = new int[32];
        for (int k = 0; k < 4; k++) {
            int b = k * 8;
            v[b] = Float.floatToRawIntBits(pt[k][0]);
            v[b + 1] = Float.floatToRawIntBits(pt[k][1]);
            v[b + 2] = Float.floatToRawIntBits(pt[k][2]);
            int c = col[k];
            v[b + 3] = c & 0xFF00FF00 | c >> 16 & 255 | (c & 255) << 16;
            v[b + 4] = Float.floatToRawIntBits(u0 + (u1 - u0) * Math.max(0f, Math.min(1f, pt[k][3])));
            v[b + 5] = Float.floatToRawIntBits(v0 + (v1 - v0) * Math.max(0f, Math.min(1f, pt[k][4])));
            v[b + 6] = 0;
            v[b + 7] = (byte) Math.round(nrm[0] * 127f) & 255 | ((byte) Math.round(nrm[1] * 127f) & 255) << 8
                    | ((byte) Math.round(nrm[2] * 127f) & 255) << 16;
        }
        out.add(new BakedQuad(v, -1, dir, sprite, true));
    }

    private static List<float[][]> wrapPieces(float[][] p) {
        float[] ss = cuts(p[0], p[1], p[3], p[2]), ts = cuts(p[0], p[3], p[1], p[2]);
        List<float[][]> res = new ArrayList<>();
        for (int i = 0; i + 1 < ss.length; i++) {
            for (int j = 0; j + 1 < ts.length; j++) {
                float[][] c = new float[4][];
                float[][] st = {{ss[i], ts[j]}, {ss[i + 1], ts[j]}, {ss[i + 1], ts[j + 1]}, {ss[i], ts[j + 1]}};
                for (int k = 0; k < 4; k++) c[k] = bilerp(p, st[k][0], st[k][1]);
                float cu = (c[0][3] + c[1][3] + c[2][3] + c[3][3]) * 0.25f, cv = (c[0][4] + c[1][4] + c[2][4] + c[3][4]) * 0.25f;
                float su = (float) Math.floor(cu), sv = (float) Math.floor(cv);
                for (float[] k : c) {
                    k[3] -= su;
                    k[4] -= sv;
                }
                res.add(c);
            }
        }
        return res;
    }

    private static float[] cuts(float[] a, float[] b, float[] c, float[] d) {
        java.util.TreeSet<Float> set = new java.util.TreeSet<>();
        set.add(0f);
        set.add(1f);
        for (float[][] e : new float[][][] {{a, b}, {c, d}}) {
            for (int comp = 3; comp <= 4; comp++) {
                float x0 = e[0][comp], x1 = e[1][comp], dx = x1 - x0;
                if (Math.abs(dx) < 1e-6f) continue;
                int lo = (int) Math.floor(Math.min(x0, x1)) + 1, hi = (int) Math.ceil(Math.max(x0, x1)) - 1;
                for (int k = lo; k <= hi; k++) {
                    float t = (k - x0) / dx;
                    if (t > 1e-4f && t < 1f - 1e-4f) set.add(t);
                }
            }
        }
        List<Float> l = new ArrayList<>();
        for (float t : set) if (l.isEmpty() || t - l.get(l.size() - 1) > 1e-4f) l.add(t);
        float[] out = new float[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    private static float[] bilerp(float[][] p, float s, float t) {
        float[] r = new float[5];
        for (int a = 0; a < 5; a++) r[a] = (1 - s) * (1 - t) * p[0][a] + s * (1 - t) * p[1][a] + s * t * p[2][a] + (1 - s) * t * p[3][a];
        return r;
    }

    @SubscribeEvent
    public static void onSection(AddSectionGeometryEvent event) {
        if (PLACEMENTS.isEmpty()) return;
        BlockPos o = event.getSectionOrigin();
        List<Placement> hit = null;
        for (Placement p : PLACEMENTS.values()) {
            if (!p.touches(o)) continue;
            if (hit == null) hit = new ArrayList<>();
            hit.add(p);
        }
        if (hit == null) return;
        final List<Placement> hits = hit;
        event.addRenderer(ctx -> renderSection(ctx, o, hits));
    }

    private static void renderSection(AddSectionGeometryEvent.SectionRenderingContext ctx, BlockPos o, List<Placement> hits) {
        VertexConsumer vc = ctx.getOrCreateChunkBuffer(RenderType.cutout());
        final long sec = BlockPos.asLong(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4);
        for (Placement p : hits) {
            BlockPos at = BlockPos.of(p.pos());
            boolean hidden = HIDDEN.contains(p.pos());
            for (BakedQuad q : hidden ? List.<BakedQuad>of() : p.bySection().getOrDefault(sec, List.of())) {
                int[] v = q.getVertices();
                float nx = (byte) v[7] / 127f, ny = (byte) (v[7] >> 8) / 127f, nz = (byte) (v[7] >> 16) / 127f;
                int light = p.faceLight() ? faceLight(ctx.getRegion(), at, o, v, nx, ny, nz, p.light()) : p.light();
                int sh = shade(nx, ny, nz, q.isShade());
                float tx = nx + 0.1f, ty = ny + 0.1f, tz = nz + 0.1f, tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                tx /= tl;
                ty /= tl;
                tz /= tl;
                int mode = holeMode;
                for (int pass = 0; pass < (mode == 2 ? 2 : 1); pass++) {
                    boolean rev = mode == 3 || pass == 1;
                    for (int i = 0; i < 4; i++) {
                        int b = (rev ? 3 - i : i) * 8;
                        float sx = mode == 1 ? 0f : rev ? -tx : tx, sy = mode == 1 ? 0f : rev ? -ty : ty, sz = mode == 1 ? 0f : rev ? -tz : tz;
                        vc.addVertex(at.getX() + Float.intBitsToFloat(v[b]) - o.getX(), at.getY() + Float.intBitsToFloat(v[b + 1]) - o.getY(),
                                        at.getZ() + Float.intBitsToFloat(v[b + 2]) - o.getZ())
                                .setColor(sh, sh, sh, 255)
                                .setUv(Float.intBitsToFloat(v[b + 4]), Float.intBitsToFloat(v[b + 5]))
                                .setLight(light)
                                .setNormal(sx, sy, sz);
                    }
                }
            }
            java.util.Set<Long> seen = SEEN.get(p.pos());
            if (seen != null) seen.add(sec);
        }
    }

    private static int faceLight(net.minecraft.world.level.BlockAndTintGetter region, BlockPos at, BlockPos o, int[] v,
                                 float nx, float ny, float nz, int fallback) {
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < 4; i++) {
            cx += Float.intBitsToFloat(v[i * 8]);
            cy += Float.intBitsToFloat(v[i * 8 + 1]);
            cz += Float.intBitsToFloat(v[i * 8 + 2]);
        }
        cx = at.getX() + cx / 4;
        cy = at.getY() + cy / 4;
        cz = at.getZ() + cz / 4;
        int r = 16 - com.sqc.nuclearsq.machine.big.FaceLight.STEPS - 1;
        if (cx < o.getX() - r || cx > o.getX() + 16 + r || cy < o.getY() - r || cy > o.getY() + 16 + r
                || cz < o.getZ() - r || cz > o.getZ() + 16 + r) return fallback;
        try {
            BlockPos s = com.sqc.nuclearsq.machine.big.FaceLight.sample(region, cx, cy, cz, nx, ny, nz);
            return s == null ? fallback : LevelRenderer.getLightColor(region, s);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int machineLight(BigMachineBlockEntity be) {
        BlockPos c = be.getBlockPos();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int block = 0, sky = 0;
        try {
            BigMachine m = be.machine();
            int rot = be.formedRot();
            BlockPos a = m.structure.world(c, rot, 0, 0, 0), b = m.structure.world(c, rot, m.w - 1, m.h - 1, m.d - 1);
            int x0 = Math.min(a.getX(), b.getX()) - 1, x1 = Math.max(a.getX(), b.getX()) + 1;
            int y0 = Math.min(a.getY(), b.getY()) - 1, y1 = Math.max(a.getY(), b.getY()) + 1;
            int z0 = Math.min(a.getZ(), b.getZ()) - 1, z1 = Math.max(a.getZ(), b.getZ()) + 1;
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) {
                        if (x > x0 && x < x1 && y > y0 && y < y1 && z > z0 && z < z1) continue;
                        mp.set(x, y, z);
                        int l = LevelRenderer.getLightColor(be.getLevel(), mp);
                        block = Math.max(block, l >> 4 & 15);
                        sky = Math.max(sky, l >> 20 & 15);
                    }
            return block << 4 | sky << 20;
        } catch (RuntimeException e) {
            block = 0;
            sky = 0;
        }
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    mp.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    int l = LevelRenderer.getLightColor(be.getLevel(), mp);
                    block = Math.max(block, l >> 4 & 15);
                    sky = Math.max(sky, l >> 20 & 15);
                }
        return block << 4 | sky << 20;
    }

    private static int shade(float nx, float ny, float nz, boolean shade) {
        if (!shade) return 255;
        float ax = nx * nx, ay = ny * ny, az = nz * nz, t = ax + ay + az;
        if (t < 1e-4f) return 255;
        float v = (ax * 0.6f + az * 0.8f + ay * (ny > 0 ? 1.0f : 0.5f)) / t;
        return Math.round(255f * v);
    }

    private static Map<Long, List<BakedQuad>> assign(net.minecraft.world.level.Level level, Key key, BlockPos at) {
        Map<Long, List<BakedQuad>> out = new java.util.HashMap<>();
        Map<Long, Boolean> air = new java.util.HashMap<>();
        for (BakedQuad q : SLICED.computeIfAbsent(key, k -> subdivide(QUADS.getOrDefault(k, List.of()), PIECE))) {
            long s = section(q, at);
            if (!onlyAir(level, s, air)) {
                out.computeIfAbsent(s, x -> new ArrayList<>()).add(q);
                continue;
            }
            for (BakedQuad piece : subdivide(List.of(q), SMALL_PIECE)) {
                long h = host(level, piece, at, air);
                out.computeIfAbsent(h, x -> new ArrayList<>()).add(piece);
            }
        }
        out.replaceAll((s, l) -> List.copyOf(l));
        return out;
    }

    private static long section(BakedQuad q, BlockPos at) {
        int[] v = q.getVertices();
        float cx = 0, cy = 0, cz = 0;
        for (int k = 0; k < 4; k++) {
            cx += Float.intBitsToFloat(v[k * 8]);
            cy += Float.intBitsToFloat(v[k * 8 + 1]);
            cz += Float.intBitsToFloat(v[k * 8 + 2]);
        }
        return BlockPos.asLong((int) Math.floor(at.getX() + cx * 0.25) >> 4, (int) Math.floor(at.getY() + cy * 0.25) >> 4,
                (int) Math.floor(at.getZ() + cz * 0.25) >> 4);
    }

    private static long host(net.minecraft.world.level.Level level, BakedQuad q, BlockPos at, Map<Long, Boolean> air) {
        long s = section(q, at);
        if (!onlyAir(level, s, air)) return s;
        int[] v = q.getVertices();
        int sx = BlockPos.getX(s), sy = BlockPos.getY(s), sz = BlockPos.getZ(s);
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    long nb = BlockPos.asLong(sx + dx, sy + dy, sz + dz);
                    if ((dx | dy | dz) == 0 || onlyAir(level, nb, air)) continue;
                    boolean in = true;
                    for (int k = 0; k < 4 && in; k++) {
                        float lx = at.getX() + Float.intBitsToFloat(v[k * 8]) - ((sx + dx) << 4);
                        float ly = at.getY() + Float.intBitsToFloat(v[k * 8 + 1]) - ((sy + dy) << 4);
                        float lz = at.getZ() + Float.intBitsToFloat(v[k * 8 + 2]) - ((sz + dz) << 4);
                        in = lx >= MIN && lx <= MAX && ly >= MIN && ly <= MAX && lz >= MIN && lz <= MAX;
                    }
                    if (in) return nb;
                }
        return s;
    }

    private static boolean onlyAir(net.minecraft.world.level.Level level, long s, Map<Long, Boolean> cache) {
        return cache.computeIfAbsent(s, k -> {
            int idx = level.getSectionIndexFromSectionY(BlockPos.getY(k));
            if (idx < 0 || idx >= level.getSectionsCount()) return true;
            var section = level.getChunk(BlockPos.getX(k), BlockPos.getZ(k)).getSection(idx);
            return section == null || section.hasOnlyAir();
        });
    }

    private static List<BakedQuad> subdivide(List<BakedQuad> in, float piece) {
        List<BakedQuad> out = new ArrayList<>(in.size());
        for (BakedQuad q : in) {
            int[] v = q.getVertices();
            float[][] P = new float[4][5];
            for (int k = 0; k < 4; k++) {
                P[k][0] = Float.intBitsToFloat(v[k * 8]);
                P[k][1] = Float.intBitsToFloat(v[k * 8 + 1]);
                P[k][2] = Float.intBitsToFloat(v[k * 8 + 2]);
                P[k][3] = Float.intBitsToFloat(v[k * 8 + 4]);
                P[k][4] = Float.intBitsToFloat(v[k * 8 + 5]);
            }
            int n = (int) Math.ceil(Math.max(dist(P[0], P[1]), dist(P[3], P[2])) / piece);
            int m = (int) Math.ceil(Math.max(dist(P[0], P[3]), dist(P[1], P[2])) / piece);
            if (n <= 1 && m <= 1) {
                out.add(q);
                continue;
            }
            n = Math.max(n, 1);
            m = Math.max(m, 1);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    float s0 = (float) i / n, s1 = (float) (i + 1) / n, t0 = (float) j / m, t1 = (float) (j + 1) / m;
                    float[][] c = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
                    int[] nv = v.clone();
                    for (int k = 0; k < 4; k++) {
                        float s = c[k][0], t = c[k][1];
                        for (int a = 0; a < 5; a++) {
                            float val = (1 - s) * (1 - t) * P[0][a] + s * (1 - t) * P[1][a] + s * t * P[2][a] + (1 - s) * t * P[3][a];
                            nv[k * 8 + (a < 3 ? a : a + 1)] = Float.floatToRawIntBits(val);
                        }
                    }
                    out.add(new BakedQuad(nv, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade()));
                }
            }
        }
        return out;
    }

    private static float dist(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public static void reset() {
        QUADS.clear();
        INFO.clear();
        FAILED.clear();
        DELIVERED.clear();
        REQUESTED.clear();
        PLACEMENTS.clear();
        SEEN.clear();
        SLICED.clear();
        HIDDEN.clear();
        HIDE_SINCE.clear();
        RETURNING.clear();
    }

    public static int machinesInChunks() {
        return (int) DELIVERED.values().stream().filter(t -> System.nanoTime() - t > OVERLAP_NS).count();
    }

    private static int reports, reportsOccluded, waiting, notDelivered, notChunkable;
    private static String snap = "";

    public static void snapshotCounters() {
        snap = reports + " Meldungen, verdeckt " + reportsOccluded + ", wartet " + waiting + ", nicht geliefert " + notDelivered
                + ", nicht backbar " + notChunkable;
        reports = reportsOccluded = waiting = notDelivered = notChunkable = 0;
    }

    public static String counters() {
        return snap;
    }

    public static int hiddenMeshes() {
        return HIDDEN.size();
    }

    public static int quadCount() {
        return QUADS.values().stream().mapToInt(List::size).sum();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.ModifyBakingResult event) {
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof BigMachineControllerBlock c) || !c.machine().chunkBaked()) continue;
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                if (!st.getValue(ReactorProps.FORMED)) continue;
                ModelResourceLocation loc = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(st);
                BakedModel orig = event.getModels().get(loc);
                if (orig != null) event.getModels().put(loc, new ChunkMachineModel(orig));
            }
        }
    }

    private static final class ChunkMachineModel extends BakedModelWrapper<BakedModel> {
        ChunkMachineModel(BakedModel original) {
            super(original);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
            if (side != null || (renderType != null && renderType != RenderType.cutout())) return List.of();
            Payload p = data.get(PROP);
            if (p == null) return List.of();
            List<BakedQuad> q = QUADS.get(new Key(p.machine(), p.rot()));
            if (q == null) return List.of();
            DELIVERED.putIfAbsent(p.pos(), System.nanoTime());
            return HIDDEN.contains(p.pos()) ? List.of() : q;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.cutout());
        }
    }
}
