package com.sqc.nuclearsq.progression.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.sqc.nuclearsq.progression.BuildData.Machine;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

final class ModelMesh {
    final List<float[]> pos = new ArrayList<>(), uv = new ArrayList<>(), nrm = new ArrayList<>();

    final List<Integer> tint = new ArrayList<>();
    float texW = 256, texH = 256;
    ResourceLocation tex;
    float maxY, minY = 1e9f;

    private ModelMesh() {}

    static ModelMesh of(Machine m) {
        return m.id().startsWith("block:") ? block(m) : geo(m);
    }

    private static boolean hidden(String machine, String bone) {
        if (machine.startsWith("server_rack") || machine.equals("pattern_rack"))
            return bone.matches("tier_\\d+_[234]") || bone.matches("led_\\d+_full") || bone.matches("act_\\d+");
        return switch (machine) {
            case "component_assembler" -> bone.equals("product") || bone.equals("press_flash") || bone.startsWith("part_");
            case "control_rod_drive" -> bone.equals("lamp_yellow") || bone.equals("lamp_red");
            default -> false;
        };
    }

    private static ModelMesh geo(Machine m) {
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(ResourceLocation.fromNamespaceAndPath("nuclearsq", "geo/" + m.id() + ".geo.json"));
        if (model == null) return null;
        ModelMesh out = new ModelMesh();
        out.tex = ResourceLocation.fromNamespaceAndPath("nuclearsq", "textures/block/" + m.id() + ".png");
        out.texW = (float) model.properties().textureWidth();
        out.texH = (float) model.properties().textureHeight();
        for (GeoBone b : model.topLevelBones()) bone(out, m, b, new Matrix4f());
        return out.pos.isEmpty() ? null : out;
    }

    private static void bone(ModelMesh out, Machine m, GeoBone b, Matrix4f parent) {
        if (hidden(m.id(), b.getName())) return;
        Matrix4f mb = new Matrix4f(parent);
        var snap = b.getInitialSnapshot();
        float px = snap != null ? snap.getOffsetX() : b.getPosX(), py = snap != null ? snap.getOffsetY() : b.getPosY(), pz = snap != null ? snap.getOffsetZ() : b.getPosZ();
        float rx = snap != null ? snap.getRotX() : b.getRotX(), ry = snap != null ? snap.getRotY() : b.getRotY(), rz = snap != null ? snap.getRotZ() : b.getRotZ();
        float sx = snap != null ? snap.getScaleX() : b.getScaleX(), sy = snap != null ? snap.getScaleY() : b.getScaleY(), sz = snap != null ? snap.getScaleZ() : b.getScaleZ();
        mb.translate(-px / 16f, py / 16f, pz / 16f);
        mb.translate(b.getPivotX() / 16f, b.getPivotY() / 16f, b.getPivotZ() / 16f);
        if (rz != 0) mb.rotateZ(rz);
        if (ry != 0) mb.rotateY(ry);
        if (rx != 0) mb.rotateX(rx);
        mb.scale(sx, sy, sz);
        mb.translate(-b.getPivotX() / 16f, -b.getPivotY() / 16f, -b.getPivotZ() / 16f);
        if (!b.isHidden() && !Boolean.TRUE.equals(b.shouldNeverRender())) for (GeoCube c : b.getCubes()) cube(out, m, c, mb);
        if (!b.isHidingChildren()) for (GeoBone ch : b.getChildBones()) bone(out, m, ch, mb);
    }

    private static void cube(ModelMesh out, Machine m, GeoCube c, Matrix4f bone) {
        Matrix4f mc = new Matrix4f(bone);
        var pv = c.pivot();
        mc.translate((float) pv.x / 16f, (float) pv.y / 16f, (float) pv.z / 16f);
        var r = c.rotation();
        if (r.z != 0) mc.rotateZ((float) r.z);
        if (r.y != 0) mc.rotateY((float) r.y);
        if (r.x != 0) mc.rotateX((float) r.x);
        mc.translate(-(float) pv.x / 16f, -(float) pv.y / 16f, -(float) pv.z / 16f);
        List<float[]> verts = new ArrayList<>(), uvs = new ArrayList<>();
        float cx = 0, cy = 0, cz = 0;
        int n = 0;
        for (GeoQuad q : c.quads()) {
            if (q == null) continue;
            float[] p = new float[12], uv = new float[8];
            for (int i = 0; i < 4; i++) {
                GeoVertex v = q.vertices()[i];
                Vector4f w = mc.transform(new Vector4f(v.position().x, v.position().y, v.position().z, 1f));
                p[i * 3] = (float) m.modelX() + rotX(m.quarterTurns(), w.x, w.z);
                p[i * 3 + 1] = (float) m.modelY() + w.y;
                p[i * 3 + 2] = (float) m.modelZ() + rotZ(m.quarterTurns(), w.x, w.z);
                uv[i * 2] = v.texU();
                uv[i * 2 + 1] = v.texV();
                cx += p[i * 3]; cy += p[i * 3 + 1]; cz += p[i * 3 + 2];
                n++;
            }
            for (int axis = 0; axis < 2; axis++) {
                float low = Math.min(Math.min(uv[axis], uv[2 + axis]), Math.min(uv[4 + axis], uv[6 + axis]));
                float high = Math.max(Math.max(uv[axis], uv[2 + axis]), Math.max(uv[4 + axis], uv[6 + axis]));
                if ((high - low) * (axis == 0 ? out.texW : out.texH) <= 1.001f) for (int i = 0; i < 4; i++) uv[i * 2 + axis] = (low + high) / 2;
            }
            verts.add(p);
            uvs.add(uv);
        }
        if (n == 0) return;
        cx /= n; cy /= n; cz /= n;
        for (int k = 0; k < verts.size(); k++) {
            float[] p = verts.get(k);
            Vector3f e1 = new Vector3f(p[3] - p[0], p[4] - p[1], p[5] - p[2]), e2 = new Vector3f(p[6] - p[0], p[7] - p[1], p[8] - p[2]);
            Vector3f nr = e1.cross(e2, new Vector3f());
            if (nr.lengthSquared() < 1e-12f) continue;
            nr.normalize();

            float fx = (p[0] + p[3] + p[6] + p[9]) / 4 - cx, fy = (p[1] + p[4] + p[7] + p[10]) / 4 - cy, fz = (p[2] + p[5] + p[8] + p[11]) / 4 - cz;
            if (nr.x * fx + nr.y * fy + nr.z * fz < 0) nr.negate();
            out.pos.add(p);
            out.uv.add(uvs.get(k));
            out.nrm.add(new float[] {nr.x, nr.y, nr.z});
            out.tint.add(0xFFFFFF);
            for (int i = 0; i < 4; i++) { out.maxY = Math.max(out.maxY, p[i * 3 + 1]); out.minY = Math.min(out.minY, p[i * 3 + 1]); }
        }
    }

    private static ModelMesh block(Machine m) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("nuclearsq", m.id().substring(6)));
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(HorizontalDirectionalBlock.FACING)) st = st.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        BakedModel bm = Minecraft.getInstance().getBlockRenderer().getBlockModel(st);
        RandomSource rs = RandomSource.create(42);
        List<BakedQuad> quads = new ArrayList<>(bm.getQuads(st, null, rs, ModelData.EMPTY, null));
        for (Direction d : Direction.values()) quads.addAll(bm.getQuads(st, d, rs, ModelData.EMPTY, null));
        if (quads.isEmpty()) return null;
        ModelMesh out = new ModelMesh();
        out.tex = TextureAtlas.LOCATION_BLOCKS;
        for (BakedQuad q : quads) {
            int[] v = q.getVertices();
            float[] p = new float[12], uv = new float[8];
            for (int i = 0; i < 4; i++) {
                float dx = Float.intBitsToFloat(v[i * 8]) - 0.5f, y = Float.intBitsToFloat(v[i * 8 + 1]), dz = Float.intBitsToFloat(v[i * 8 + 2]) - 0.5f;
                p[i * 3] = (float) m.modelX() + rotX(m.quarterTurns(), dx, dz);
                p[i * 3 + 1] = (float) m.modelY() + y;
                p[i * 3 + 2] = (float) m.modelZ() + rotZ(m.quarterTurns(), dx, dz);
                uv[i * 2] = Float.intBitsToFloat(v[i * 8 + 4]);
                uv[i * 2 + 1] = Float.intBitsToFloat(v[i * 8 + 5]);
                out.maxY = Math.max(out.maxY, p[i * 3 + 1]);
                out.minY = Math.min(out.minY, p[i * 3 + 1]);
            }
            Direction dir = q.getDirection();
            out.pos.add(p);
            out.uv.add(uv);
            out.nrm.add(new float[] {rotX(m.quarterTurns(), dir.getStepX(), dir.getStepZ()), dir.getStepY(), rotZ(m.quarterTurns(), dir.getStepX(), dir.getStepZ())});
            out.tint.add(0xFFFFFF);
        }
        return out;
    }

    static ModelMesh conduit(String kind, int connections, int cellX, int cellY, int cellZ) {
        String blockId = switch (kind) {
            case "item" -> "item_pipe_basic_sqc";
            case "power" -> "cable_basic_sqc";
            case "data" -> "data_cable_sqc";
            default -> "pipe_basic_sqc";
        };
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("nuclearsq", blockId));
        BlockState state = block.defaultBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        ModelData data = ModelData.builder().with(ConduitBlockEntity.CONN, connections).build();
        RandomSource random = RandomSource.create(42);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, random, data, null));
        for (Direction side : Direction.values()) quads.addAll(model.getQuads(state, side, random, data, null));
        if (quads.isEmpty()) return null;

        int tintOfTintedFaces = kind.equals("power") ? 0x707070 : 0xFFFFFF;
        ModelMesh out = new ModelMesh();
        out.tex = TextureAtlas.LOCATION_BLOCKS;
        for (BakedQuad quad : quads) {
            int[] vertexData = quad.getVertices();
            float[] corners = new float[12], uv = new float[8];
            for (int corner = 0; corner < 4; corner++) {
                corners[corner * 3] = cellX + Float.intBitsToFloat(vertexData[corner * 8]);
                corners[corner * 3 + 1] = cellY + Float.intBitsToFloat(vertexData[corner * 8 + 1]);
                corners[corner * 3 + 2] = cellZ + Float.intBitsToFloat(vertexData[corner * 8 + 2]);
                uv[corner * 2] = Float.intBitsToFloat(vertexData[corner * 8 + 4]);
                uv[corner * 2 + 1] = Float.intBitsToFloat(vertexData[corner * 8 + 5]);
                out.maxY = Math.max(out.maxY, corners[corner * 3 + 1]);
                out.minY = Math.min(out.minY, corners[corner * 3 + 1]);
            }
            Direction facing = quad.getDirection();
            out.pos.add(corners);
            out.uv.add(uv);
            out.nrm.add(new float[] {facing.getStepX(), facing.getStepY(), facing.getStepZ()});
            out.tint.add(quad.getTintIndex() >= 0 ? tintOfTintedFaces : 0xFFFFFF);
        }
        return out;
    }

    private static float rotX(int rot, float dx, float dz) {
        return switch (rot) { case 1 -> -dz; case 2 -> -dx; case 3 -> dz; default -> dx; };
    }

    private static float rotZ(int rot, float dx, float dz) {
        return switch (rot) { case 1 -> dx; case 2 -> -dz; case 3 -> -dx; default -> dz; };
    }
}
