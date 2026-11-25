package com.sqc.nuclearsq.door.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.door.DoorBlockEntity;
import com.sqc.nuclearsq.door.DoorRegistry;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public class DoorRenderer implements BlockEntityRenderer<DoorBlockEntity> {
    private static final float P = 1 / 16f, GAP = 0.02f, E = P / 4, M = E / 4;
    private static final ResourceLocation SLATS = tex("slats"), HAZARD = tex("hazard"), FRAME = tex("frame"),
            HULL = tex("hull"), VAULT = tex("vault"), WHEEL_RED = tex("wheel_red"),
            SECTIONS = tex("sections"), WINDOW = tex("window"), GRILLE = tex("grille"), FOLD = tex("fold"),
            LOCK_PLATE = tex("lock_plate"), LOCK_WEAR = tex("lock_wear"), LOCK_RIM = tex("lock_rim"), LOCK_FRAME = tex("lock_frame"),
            LOCK_WHEEL = tex("lock_wheel"), LOCK_GLASS = tex("lock_glass"), LOCK_LAMP = tex("lock_lamp"),
            LAB_PLATE = tex("lab_plate"), INOX = tex("inox"), PANE = tex("pane"), ALU = tex("alu"), LEAD = tex("lead"), TREFOIL = tex("trefoil"),
            COLD = tex("cold"), MESH = tex("mesh"), GALV = tex("galv"), GALV_BAND = tex("galv_band"), PERF = tex("perf"), PVC = tex("pvc"),
            LIFT = tex("lift"), SCREEN = tex("screen"), RUBBER = tex("rubber");

    private interface Leaf {
        void draw(float f, float sw, float hinge, float d, float len);
    }

    private PoseStack stack;
    private PoseStack.Pose pose;
    private MultiBufferSource buffers;
    private VertexConsumer vc;
    private DoorBlockEntity be;
    private final int[] uDir = new int[3], vDir = new int[3], nDir = new int[3];
    private final float[] base = new float[3];
    private int w, h;
    private boolean swap, glow;
    private int[] lights = new int[0];

    public DoorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/entity/door/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DoorRegistry.CONTROLLER_BE.get(), DoorRenderer::new);
    }

    @Override
    public AABB getRenderBoundingBox(DoorBlockEntity be) {
        AABB own = new AABB(be.getBlockPos());
        return be.panels().isEmpty() ? own : own.minmax(AABB.encapsulatingFullBlocks(be.min(), be.max()).inflate(be.way()));
    }

    private static void unit(Direction.Axis a, int[] out) {
        out[0] = a == Direction.Axis.X ? 1 : 0;
        out[1] = a == Direction.Axis.Y ? 1 : 0;
        out[2] = a == Direction.Axis.Z ? 1 : 0;
    }

    @Override
    public void render(DoorBlockEntity door, float partial, PoseStack stack, MultiBufferSource source, int light, int overlay) {
        if (door.getLevel() == null || door.panels().isEmpty() || door.retired()) return;
        be = door;
        this.stack = stack;
        pose = stack.last();
        buffers = source;
        unit(door.wide(), uDir);
        unit(door.tall(), vDir);
        unit(door.axis(), nDir);
        BlockPos o = door.getBlockPos(), m = door.min();
        base[0] = m.getX() - o.getX();
        base[1] = m.getY() - o.getY();
        base[2] = m.getZ() - o.getZ();
        if (door.flip()) for (int i = 0; i < 3; i++) {
            nDir[i] = -nDir[i];
            base[i] -= nDir[i];
        }
        w = door.width();
        h = door.height();
        if (lights.length < w * h) lights = new int[w * h];
        java.util.Arrays.fill(lights, 0, w * h, -1);
        float f = Mth.clamp(door.fraction(partial), 0, 1);
        switch (door.kind()) {
            case HANGAR -> hangar(f);
            case AIRLOCK -> airlock(f);
            case VAULT -> vault(f);
            case SECTIONAL -> sectional(f);
            case GRILLE -> grille(f);
            case FOLDING -> folding(f);
            case LAB -> lab(f);
            case GLASS -> glassDoor(f);
            case SHIELD -> shield(f);
            case COLD -> cold(f);
            case CAGE -> cage(f);
            case SLIDE -> slide(f);
            case INDUSTRIAL -> industrial(f);
            case PERFORATED -> perforated(f);
            case SPEED -> speed(f);
            case LIFT -> lift(f);
        }
    }

    private void hangar(float f) {
        float top = h - 6 * P, bottom = f * (top - 3 * P);
        use(FRAME);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 2 * P, w, h, 14 * P, 0, 0);
        use(SLATS);
        box(2 * P, bottom + 3 * P, 7 * P, w - 2 * P, top, 9 * P, 0, bottom);
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 3 * P, 10 * P, 0, bottom);
    }

    private void airlock(float f) {
        float t = Mth.clamp(f / 0.25f, 0, 1), sl = Mth.clamp((f - 0.25f) / 0.75f, 0, 1), swing = sl * sl * (3 - 2 * sl);
        float u0 = 2 * P, u1 = w - 2 * P, top = h - 4 * P, mid = w / 2f, vt = top + P / 2;
        float rim = w == 1 ? 2 * P : 3 * P, a = u0 + P + rim, b = u1 - P - rim;
        boolean tall = h >= 2;
        float rw = P * Math.min(w == 1 ? 3.5f : w == 2 ? 5.5f : 6.5f, h == 1 ? 3.5f : h == 2 ? 4 : 6.5f);
        int g = Math.min(w == 1 ? 2 : w == 2 ? 3 : 4, h == 2 ? 3 : 4);
        float rg = g * P, cw = P * (h == 1 ? 7 : h == 2 ? 11 : 14), cp = P * (h == 2 ? 20 : 27);
        use(HAZARD);
        box(0, 0, 2 * P, w, P, 14 * P, 0, 0);
        use(LOCK_FRAME);
        box(0, P, 2 * P, 3 * P, top, 14 * P, 0, 0);
        box(w - 3 * P, P, 2 * P, w, top, 14 * P, w - 3 * P, 0);
        swap = true;
        box(0, top, 2 * P, w, h, 14 * P, 0, h);
        swap = false;
        box(w / 2f - 2 * P, h - 3.5f * P, P, w / 2f + 2 * P, h - 0.5f * P, 15 * P, w / 2f - 6 * P, 0);
        use(LOCK_LAMP);
        glow = true;
        box(w / 2f - P, h - 2.5f * P, P - 0.01f, w / 2f + P, h - 1.5f * P, 15 * P + 0.01f, 0, h - 2.5f * P + (f <= 0 ? 2 : f >= 1 ? 12 : 7) * P);
        glow = false;
        use(LOCK_FRAME);
        boolean flat = be.flat();
        float[] hinges = tall ? new float[] {4 * P, top - 7 * P} : new float[] {2 * P, top - 5 * P};
        for (float y : hinges) {
            if (flat) {
                box(0.5f * P, y - P, 0.5f * P, 1.5f * P, y, 2 * P, 0, 0);
                box(0.5f * P, y + 3 * P, 0.5f * P, 1.5f * P, y + 4 * P, 2 * P, 0, 0);
            } else {
                box(P, y, P, 3 * P, y + 3 * P, 2 * P, 0, 0);
            }
        }
        if (flat) {
            turn((be.axis() == Direction.Axis.Z ? 180 : -180) * swing, vDir, P, 0, P);
            for (float y : hinges) {
                box(0.5f * P, y, 0.5f * P, 1.5f * P, y + 3 * P, 2.2f * P, 0, 0);
                box(1.2f * P, y, 2.1f * P, 2.2f * P, y + 3 * P, 6.2f * P, 0, 0);
            }
        } else {
            turn((be.axis() == Direction.Axis.Z ? 90 : -90) * swing, vDir, 3 * P, 0, 5 * P);
        }
        use(LOCK_WEAR);
        box(u0, P, 6 * P, u1, tall ? P + 1 : vt, 10 * P, u0, P + 1);
        if (tall) {
            use(LOCK_PLATE);
            box(u0, P + 1, 6 * P, u1, cp - rg, 10 * P, u0, 0);
            box(u0, cp + rg, 6 * P, u1, vt, 10 * P, u0, 0);
            for (int j = -g; j < g; j++) {
                float hw = P * Math.round(Math.sqrt(g * g - (j + 0.5f) * (j + 0.5f)));
                box(u0, cp + j * P, 6 * P, mid - hw, cp + (j + 1) * P, 10 * P, u0, 0);
                box(mid + hw, cp + j * P, 6 * P, u1, cp + (j + 1) * P, 10 * P, u0, 0);
            }
            use(LOCK_GLASS);
            box(mid - rg - P / 2, cp - rg - P / 2, 7.75f * P, mid + rg + P / 2, cp + rg + P / 2, 8.25f * P, mid - rg, cp + rg);
        }
        use(LOCK_RIM);
        box(u0 + P, P, 5 * P, a, vt + M, 11 * P, u0 + P, 0);
        box(b, P, 5 * P, u1 - P, vt + M, 11 * P, b, 0);
        for (int k = 1; k < 8 && w >= 4; k++) {
            for (int d = -1; d <= 1; d += 2) {
                float x = mid + d * k - P;
                if (x > a + 4 * P && x + 2 * P < b - 4 * P) box(x, P, 5 * P, x + 2 * P, top, 11 * P, x, 0);
            }
        }
        swap = true;
        box(a, top - rim, 5 * P, b, vt + M, 11 * P, 0, top);
        if (!tall) box(a, P, 5 * P, b, P + rim, 11 * P, 0, P + rim);
        for (float y = 36 * P; y + 2 * P <= top - rim - 2 * P; y++) box(a, y, 5 * P, b, y + 2 * P, 11 * P, 0, y + 2 * P);
        swap = false;
        if (tall) {
            use(HAZARD);
            box(a, P, 5 * P - M, b, 5 * P, 11 * P + M, u0, 0);
        }
        use(FRAME);
        box(a, cw - P, 5 * P - M, b, cw + P, 11 * P + M, 0, 0);
        if (tall) ring(mid, cp, rg - P / 2, rg + 1.5f * P, 5 * P, 6 * P, 0);
        if (w >= 2 && tall) {
            float pull = 2.5f * P * t;
            for (float x : new float[] {a + P, b - 2 * P}) {
                for (float n : new float[] {4.5f * P, 10.5f * P}) {
                    box(x, cw + P, n, x + P, vt - pull, n + P, 0, 0);
                    box(x, P + pull, n, x + P, cw - P, n + P, 0, 0);
                }
                for (float v : new float[] {(cw + top) / 2, (cw + 5 * P) / 2 + P}) box(x - P / 2, v, 4 * P, x + 1.5f * P, v + P, 12 * P, 0, 0);
            }
        }
        float spin = 270 * t * t * (3 - 2 * t);
        box(mid - P, cw - P, 4 * P, mid + P, cw + P, 12 * P, 0, 0);
        use(LOCK_WHEEL);
        ring(mid, cw, rw - P, rw, 3 * P, 4 * P, spin);
        for (int i = 0; i < 2; i++) {
            turn(spin + 90 * i, nDir, mid, cw, 0);
            box(mid - rw + P / 2, cw - P / 2, 3.2f * P, mid + rw - P / 2, cw + P / 2, 3.8f * P, 0, 0);
            box(mid - rw + P / 2, cw - P / 2, 12.2f * P, mid + rw - P / 2, cw + P / 2, 12.8f * P, 0, 0);
            back();
        }
        use(LOCK_FRAME);
        turn(spin, nDir, mid, cw, 0);
        box(mid - 1.5f * P, cw - 1.5f * P, 2.6f * P, mid + 1.5f * P, cw + 1.5f * P, 4.2f * P, mid - 8 * P, 0);
        box(mid - 1.5f * P, cw - 1.5f * P, 11.8f * P, mid + 1.5f * P, cw + 1.5f * P, 13.4f * P, mid - 8 * P, 0);
        back();
        back();
    }

    private void ring(float cu, float cv, float ri, float ro, float n0, float n1, float spin) {
        float seg = ro * 0.19891237f + 0.001f;
        for (int i = 0; i < 16; i++) {
            turn(spin + 22.5f * i, nDir, cu, cv, 0);
            box(cu - seg, cv + ri, n0, cu + seg, cv + ro, n1, cu, cv);
            box(cu - seg, cv + ri, 1 - n1, cu + seg, cv + ro, 1 - n0, cu, cv);
            back();
        }
    }

    private void sectional(float f) {
        float top = h - 4 * P, bottom = f * (top - 2 * P), band = bottom + Mth.floor(top * 0.62f * 2) / 2f;
        use(FRAME);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 3 * P, w, h, 13 * P, 0, 0);
        use(SECTIONS);
        box(2 * P, bottom + 2 * P, 7 * P, w - 2 * P, top, 9 * P, 0, bottom);
        if (h >= 2) {
            use(WINDOW);
            box(2 * P, band + P, 6.8f * P, w - 2 * P, Math.min(band + 7 * P, top), 9.2f * P, 0, band);
        }
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 2 * P, 10 * P, 0, bottom);
    }

    private void grille(float f) {
        float top = h - 6 * P, bottom = f * (top - 3 * P);
        use(FRAME);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 2 * P, w, h, 14 * P, 0, 0);
        use(GRILLE);
        box(2 * P, bottom + 3 * P, 7.5f * P, w - 2 * P, top, 8.5f * P, 0, bottom);
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 3 * P, 10 * P, 0, bottom);
    }

    private void folding(float f) {
        float top = h - 3 * P, half = (w - 4 * P) / 2;
        int n = Math.max(2, Math.round(half * 2) / 2 * 2);
        float pw = half / n, angle = f * 80, c = Mth.cos(angle * Mth.DEG_TO_RAD);
        use(FRAME);
        box(0, 0, 3 * P, 2 * P, top, 13 * P, 0, 0);
        box(w - 2 * P, 0, 3 * P, w, top, 13 * P, 0, 0);
        box(0, top, 3 * P, w, h, 13 * P, 0, 0);
        use(FOLD);
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < n; i++) {
                boolean even = i % 2 == 0;
                float x = 2 * P + P * Mth.sin(angle * Mth.DEG_TO_RAD) + (even ? i : i + 1) * pw * c, pivot = side == 0 ? x : w - x;
                turn((even ? angle : -angle) * (side == 0 ? 1 : -1), vDir, pivot, 0, 8 * P);
                span(pivot, (side == 0) == even ? 1 : -1, 0, pw, 0, top, 7 * P, 9 * P);
                back();
            }
        }
    }

    private void vault(float f) {
        float t = 2 * P;
        use(FRAME);
        box(0, 0, 3 * P, t, h - t, 13 * P, 0, 0);
        box(w - t, 0, 3 * P, w, h - t, 13 * P, 0, 0);
        box(t, 0, 3 * P, w - t, P, 13 * P, 0, 0);
        use(HAZARD);
        box(0, h - t, 2.5f * P, w, h, 13.5f * P, 0, 0);
        if (w >= 4) {
            leaf(f, t, w / 2f);
            leaf(f, w - t, w / 2f);
        } else {
            leaf(f, t, w - t);
        }
    }

    private void leaf(float f, float hinge, float edge) {
        float dir = Math.signum(edge - hinge), len = Math.abs(edge - hinge), top = h - 2 * P, cx = len / 2, cv = (P + top) / 2;
        float r = Math.min(1.2f, Math.min(len, h) * 0.3f), in = Mth.clamp((f - 0.15f) / 0.2f, 0, 1) * 3 * P;
        float swing = Mth.clamp((f - 0.35f) / 0.65f, 0, 1);
        for (float v : new float[] {P + (top - P) * 0.18f, P + (top - P) * 0.82f - 4 * P}) {
            use(FRAME);
            span(hinge, -dir, 0, 2 * P, v - P, v + 5 * P, 2 * P, 3 * P);
        }
        turn((be.axis() == Direction.Axis.Z ? 90 : -90) * dir * swing * swing * (3 - 2 * swing), vDir, hinge + dir * (P / 2 + E), 0, 5.6f * P);
        use(VAULT);
        span(hinge, dir, E, len - E, P + E, top - E, 6.2f * P, 9.8f * P);
        use(FRAME);
        span(hinge, dir, 0, len, P, 4 * P, 5.6f * P, 10.4f * P);
        span(hinge, dir, 0, len, top - 3 * P, top, 5.6f * P, 10.4f * P);
        span(hinge, dir, 0, 3 * P, 4 * P, top - 3 * P, 5.6f * P, 10.4f * P);
        span(hinge, dir, len - 3 * P, len, 4 * P, top - 3 * P, 5.6f * P, 10.4f * P);
        float cut = r * 0.3f;
        span(hinge, dir, cx - r, cx + r, cv - r + cut, cv + r - cut, 5.6f * P, 10.4f * P);
        span(hinge, dir, cx - r + cut, cx + r - cut, cv + r - cut, cv + r, 5.6f * P, 10.4f * P);
        span(hinge, dir, cx - r + cut, cx + r - cut, cv - r, cv - r + cut, 5.6f * P, 10.4f * P);
        if (h >= 2) {
            for (float v : new float[] {P + (top - P) * 0.18f, P + (top - P) * 0.82f - 4 * P}) {
                span(hinge, dir, E, Math.min(len * 0.45f, cx - 2 * P), v, v + 4 * P, 5.3f * P, 10.7f * P);
            }
        }
        use(HULL);
        span(hinge, dir, E + in, cx - r + E, cv - P, cv + P, 5.3f * P, 10.7f * P);
        span(hinge, dir, cx + r - E, len - E - in, cv - P, cv + P, 5.3f * P, 10.7f * P);
        span(hinge, dir, cx - P, cx + P, cv + r - E, top - E - in, 5.3f * P, 10.7f * P);
        span(hinge, dir, cx - P, cx + P, P + E + in, cv - r + E, 5.3f * P, 10.7f * P);
        if (h >= 2) {
            use(HAZARD);
            span(hinge, dir, 3 * P, len - 3 * P, 4 * P, 7 * P, 5.9f * P, 10.1f * P);
        }
        turn(360 * Mth.clamp(f / 0.3f, 0, 1), nDir, hinge + dir * cx, cv, 0);
        use(WHEEL_RED);
        decal(hinge + dir * cx, cv, r * 0.9f, 5.6f * P);
        back();
        back();
    }

    private float sgn() {
        return be.axis() == Direction.Axis.Z ? 1 : -1;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private void zarge(float t, float n0, float n1, ResourceLocation texture) {
        use(texture);
        box(0, 0, n0, t, h - t, n1, 0, 0);
        box(w - t, 0, n0, w, h - t, n1, 0, 0);
        box(0, h - t, n0, w, h, n1, 0, 0);
    }

    private void leaves(float f, float t, float delay, Leaf leaf) {
        float sw = smooth((f - delay) / (1 - delay));
        if (w >= 2) {
            leaf.draw(f, sw, t, 1, w / 2f - t + E);
            leaf.draw(f, sw, w - t, -1, w / 2f - t + E);
        } else {
            leaf.draw(f, sw, t, 1, w - 2 * t);
        }
    }

    private void handle(float hinge, float d, float x, float v, float n0, float n1, float f) {
        use(INOX);
        span(hinge, d, x - P, x + P, v - 1.5f * P, v + 1.5f * P, n0 - 0.5f * P, n1 + 0.5f * P);
        float press = f < 0.2f ? 20 * smooth(f / 0.1f) : f < 0.3f ? 20 * smooth((0.3f - f) / 0.1f) : 0;
        for (int side = 0; side < 2; side++) {
            float a = side == 0 ? n0 - 2 * P : n1 + 1.2f * P, b = side == 0 ? n0 - 1.2f * P : n1 + 2 * P;
            turn(-press * d * sgn(), nDir, hinge + d * x, v, 0);
            span(hinge, d, x - 0.5f * P, x + P, v - 0.5f * P, v + 0.5f * P, side == 0 ? n0 - 2 * P : n1 + 0.5f * P, side == 0 ? n0 - 0.5f * P : n1 + 2 * P);
            span(hinge, d, x - 5 * P, x + P, v - 0.5f * P, v + 0.5f * P, a, b);
            back();
        }
    }

    private void lab(float f) {
        float t = 1.5f * P, n0 = 7 * P, n1 = 9 * P;
        zarge(t, 5 * P, 11 * P, HULL);
        leaves(f, t, 0.1f, (ff, sw, hinge, d, len) -> {
            float top = h - t - 0.5f * P, wx0 = len / 2 - 2.5f * P, wx1 = len / 2 + 2.5f * P, wv0 = h * 0.42f, wv1 = top - 4 * P;
            turn(sgn() * 90 * d * sw, vDir, hinge + d * (0.3f * P + E), 0, n0);
            use(LAB_PLATE);
            if (h >= 2) {
                span(hinge, d, E, wx0, P / 2, top, n0, n1);
                span(hinge, d, wx1, len - E, P / 2, top, n0, n1);
                span(hinge, d, wx0, wx1, P / 2, wv0, n0, n1);
                span(hinge, d, wx0, wx1, wv1, top, n0, n1);
                use(INOX);
                span(hinge, d, wx0 - P, wx0 + M, wv0 - P, wv1 + P, n0 - 0.3f * P, n1 + 0.3f * P);
                span(hinge, d, wx1 - M, wx1 + P, wv0 - P, wv1 + P, n0 - 0.3f * P, n1 + 0.3f * P);
                span(hinge, d, wx0, wx1, wv0 - P, wv0 + M, n0 - 0.3f * P, n1 + 0.3f * P);
                span(hinge, d, wx0, wx1, wv1 - M, wv1 + P, n0 - 0.3f * P, n1 + 0.3f * P);
                glass(PANE);
                span(hinge, d, wx0, wx1, wv0, wv1, 7.75f * P, 8.25f * P);
            } else {
                span(hinge, d, E, len - E, P / 2, top, n0, n1);
            }
            use(INOX);
            span(hinge, d, E - M, len - E + M, P / 2 - M, Math.min(5 * P, h * 0.2f), n0 - 0.3f * P, n1 + 0.3f * P);
            handle(hinge, d, len - 3 * P, Math.min(1, h * 0.48f), n0, n1, ff);
            back();
            use(FRAME);
            span(hinge, d, P, 6 * P, h - t - P, h - t, n0 - 1.5f * P, n0 - 0.5f * P);
        });
    }

    private void glassDoor(float f) {
        float t = 1.5f * P, n0 = 7 * P, n1 = 9 * P;
        zarge(t, 5 * P, 11 * P, ALU);
        leaves(f, t, 0.1f, (ff, sw, hinge, d, len) -> {
            float top = h - t - 0.5f * P, rim = 2.5f * P, bot = h >= 2 ? 4 * P : 2.5f * P;
            turn(sgn() * 90 * d * sw, vDir, hinge, 0, n0);
            use(ALU);
            span(hinge, d, E, rim, P / 2, top, n0, n1);
            span(hinge, d, len - rim, len - E, P / 2, top, n0, n1);
            span(hinge, d, rim, len - rim, P / 2, bot, n0, n1);
            span(hinge, d, rim, len - rim, top - rim, top, n0, n1);
            glass(PANE);
            span(hinge, d, rim, len - rim, bot, top - rim, 7.75f * P, 8.25f * P);
            if (h >= 2) {
                use(INOX);
                float v = Math.min(1, h * 0.45f);
                for (int side = 0; side < 2; side++) {
                    float a = side == 0 ? n0 - 2.5f * P : n1 + 1.5f * P, c = side == 0 ? n0 - 1.5f * P : n1;
                    span(hinge, d, rim, len - rim, v - P / 2, v + P / 2, a, a + P);
                    for (float x : new float[] {rim + P, len - rim - 2 * P}) span(hinge, d, x, x + P, v - P / 2, v + P / 2, c, c + 1.5f * P);
                }
            }
            back();
        });
    }

    private void shield(float f) {
        float t = 3 * P, n0 = 6 * P, n1 = 10 * P;
        zarge(t, 2 * P, 14 * P, FRAME);
        use(HAZARD);
        box(-E / 2, h - t - E / 2, 1.5f * P, w + E / 2, h - t + 2 * P, 14.5f * P, 0, 0);
        use(LOCK_LAMP);
        glow = true;
        box(w / 2f - 1.5f * P, h - 1.5f * P, 1.4f * P, w / 2f + 1.5f * P, h - 0.2f * P, 14.6f * P, 0, h - 1.5f * P + (f <= 0 ? 2 : f >= 1 ? 12 : 7) * P);
        glow = false;
        leaves(f, t, 0.15f, (ff, sw, hinge, d, len) -> {
            float top = h - t - 0.25f * P;
            use(FRAME);
            for (float v : h >= 2 ? new float[] {3 * P, top * 0.5f - 1.5f * P, top - 6 * P} : new float[] {2 * P, top - 5 * P}) {
                span(hinge, d, -P, 2 * P, v, v + 3 * P, n0 - 1.5f * P, n0);
            }
            turn(sgn() * 90 * d * sw, vDir, hinge + d * (P / 2 + E), 0, n0);
            use(LEAD);
            span(hinge, d, E, len - E, P / 2, top, n0, n1);
            use(FRAME);
            span(hinge, d, E - M, len - E + M, top - 1.5f * P, top + M, n0 - 0.5f * P, n1 + 0.5f * P);
            span(hinge, d, E - M, 1.5f * P, P / 2 - M, top + M, n0 - 0.5f * P, n1 + 0.5f * P);
            span(hinge, d, len - 1.5f * P, len - E + M, P / 2 - M, top + M, n0 - 0.5f * P, n1 + 0.5f * P);
            if (h >= 2) {
                use(HAZARD);
                span(hinge, d, 1.5f * P, len - 1.5f * P, P / 2 - M, 4 * P, n0 - 0.5f * P, n1 + 0.5f * P);
                use(TREFOIL);
                decal(hinge + d * len / 2, Math.min(h * 0.62f, top - 0.6f), Math.min(len * 0.32f, 0.45f), n0 - 0.4f * P);
            }
            use(INOX);
            float x = len - 3 * P, v = Math.min(1, h * 0.48f);
            for (int side = 0; side < 2; side++) {
                float a = side == 0 ? n0 - 3 * P : n1 + 0.5f * P, b = side == 0 ? n0 - 0.5f * P : n1 + 3 * P;
                span(hinge, d, x - P, x + P, v - 5 * P, v + 5 * P, side == 0 ? a : n1 + 2.5f * P, side == 0 ? n0 - 2.5f * P : b);
                span(hinge, d, x - P, x + P, v - 5 * P, v - 4 * P, a, b);
                span(hinge, d, x - P, x + P, v + 4 * P, v + 5 * P, a, b);
            }
            back();
        });
    }

    private void cold(float f) {
        float t = 2.5f * P, n0 = 6 * P, n1 = 10 * P;
        zarge(t, 2.5f * P, 13.5f * P, HULL);
        leaves(f, t, 0.15f, (ff, sw, hinge, d, len) -> {
            float top = h - t;
            use(INOX);
            for (float v : h >= 2 ? new float[] {4 * P, top - 7 * P} : new float[] {2 * P, top - 5 * P}) {
                span(hinge, d, -P, 3 * P, v, v + 3 * P, n0 - 1.5f * P, n0);
            }
            turn(sgn() * 90 * d * sw, vDir, hinge + d * (0.3f * P + E), 0, n0);
            use(COLD);
            span(hinge, d, E, len - E, P / 2, top - P / 2, n0, n1);
            use(RUBBER);
            span(hinge, d, len - P, len - E + M, P / 2 - M, top - P / 2 + M, n0 - 0.2f * P, n1 + 0.2f * P);
            if (h >= 2) {
                use(INOX);
                span(hinge, d, P, len - P, P, 4 * P, n0 - 0.3f * P, n1 + 0.3f * P);
                use(SCREEN);
                glow = true;
                span(hinge, d, len / 2 - 2 * P, len / 2 + 2 * P, h * 0.66f, h * 0.66f + 3 * P, n0 - 0.6f * P, n0);
                glow = false;
            }
            float x = len - 4 * P, v = Math.min(1, h * 0.48f);
            use(INOX);
            span(hinge, d, x - 1.5f * P, x + 1.5f * P, v - 3 * P, v + 3 * P, n0 - P, n0);
            turn(-30 * (smooth(ff / 0.12f) - smooth((ff - 0.2f) / 0.12f)) * d * sgn(), nDir, hinge + d * x, v, 0);
            span(hinge, d, Math.max(x - 7 * P, 2 * P), x + 0.5f * P, v - P, v + P, n0 - 2.5f * P, n0 - P);
            back();
            span(hinge, d, x - 1.5f * P, x + 1.5f * P, v - 2 * P, v + 2 * P, n1, n1 + P);
            back();
        });
    }

    private void cage(float f) {
        float t = 1.5f * P, n0 = 7 * P, n1 = 9 * P;
        zarge(t, 5 * P, 11 * P, FRAME);
        leaves(f, t, 0.1f, (ff, sw, hinge, d, len) -> {
            float top = h - t - 0.5f * P, a = n0 - 0.5f * P, b = n1 + 0.5f * P, mid = h * 0.48f;
            turn(sgn() * 90 * d * sw, vDir, hinge + d * (P / 2 + E), 0, n0);
            use(FRAME);
            span(hinge, d, E, 1.5f * P, P, top, a, b);
            span(hinge, d, len - 1.5f * P, len - E, P, top, a, b);
            span(hinge, d, 1.5f * P, len - 1.5f * P, P, 2.5f * P, a, b);
            span(hinge, d, 1.5f * P, len - 1.5f * P, top - 1.5f * P, top, a, b);
            if (h >= 2) span(hinge, d, 1.5f * P, len - 1.5f * P, mid - 0.75f * P, mid + 0.75f * P, n0 - 0.25f * P, n1 + 0.25f * P);
            use(MESH);
            span(hinge, d, 1.5f * P, len - 1.5f * P, 2.5f * P, top - 1.5f * P, 7.9f * P, 8.1f * P);
            use(HAZARD);
            span(hinge, d, len - 3 * P, len - 1.5f * P, mid - 2 * P, mid + 2 * P, n0 - 1.5f * P, n0 - 0.5f * P);
            back();
        });
    }

    private void slide(float f) {
        float head = 4 * P, top = h - head, rim = 1.5f * P, s = smooth(f);
        use(ALU);
        box(0, top, 3 * P, w, h, 13 * P, 0, 0);
        use(SCREEN);
        glow = true;
        box(w / 2f - 1.5f * P, h - 3 * P, 2.6f * P, w / 2f + 1.5f * P, h - 1.5f * P, 3 * P, 0, 0);
        glow = false;
        use(FRAME);
        box(0, 0, 7 * P, w, 0.5f * P, 9 * P, 0, 0);
        int halves = w >= 2 ? 2 : 1;
        for (int k = 0; k < halves; k++) {
            float a = halves == 2 ? k * w / 2f : 0, b = halves == 2 ? (k + 1) * w / 2f : w, off = (k == 0 ? -1 : 1) * s * (b - a - rim);
            float a2 = a + off, b2 = b + off;
            use(ALU);
            box(a2, P / 2, 7 * P, a2 + rim, top, 9 * P, 0, 0);
            box(b2 - rim, P / 2, 7 * P, b2, top, 9 * P, 0, 0);
            box(a2 + rim, P / 2, 7 * P, b2 - rim, 2.5f * P, 9 * P, 0, 0);
            box(a2 + rim, top - rim, 7 * P, b2 - rim, top, 9 * P, 0, 0);
            glass(PANE);
            box(a2 + rim, 2.5f * P, 7.75f * P, b2 - rim, top - rim, 8.25f * P, 0, 0);
        }
    }

    private float rollFrame(float boxH, ResourceLocation texture) {
        float top = h - boxH;
        use(texture);
        box(0, 0, 4 * P, 2 * P, top, 12 * P, 0, 0);
        box(w - 2 * P, 0, 4 * P, w, top, 12 * P, 0, 0);
        box(0, top, 2 * P, w, h, 14 * P, 0, 0);
        return top;
    }

    private void industrial(float f) {
        float top = rollFrame(6 * P, GALV), bottom = f * (top - 3 * P), band = bottom + 3 * P + Math.max(1, Math.min(2, h * 0.55f - 0.5f));
        use(FRAME);
        box(w - 4 * P, top + 0.5f * P, 14 * P, w - 0.5f * P, h - 0.5f * P, 15.5f * P, 0, 0);
        use(GALV);
        box(2 * P, bottom + 3 * P, 7 * P, w - 2 * P, Math.min(band, top), 9 * P, 0, bottom);
        if (band < top) {
            float bt = Math.min(band + 8 * P, top);
            use(GALV_BAND);
            box(2 * P, band, 7 * P, w - 2 * P, bt, 9 * P, 2 * P, band + 8 * P);
            use(GALV);
            box(2 * P, bt, 7 * P, w - 2 * P, top, 9 * P, 0, bottom);
            glass(PANE);
            box(2 * P, band, 7.9f * P, w - 2 * P, bt, 8.1f * P, 0, 0);
        }
        use(FRAME);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 3 * P, 10 * P, 0, bottom);
    }

    private void perforated(float f) {
        float top = rollFrame(6 * P, FRAME), bottom = f * (top - 3 * P);
        use(PERF);
        box(2 * P, bottom + 3 * P, 7.5f * P, w - 2 * P, top, 8.5f * P, 0, bottom);
        use(HAZARD);
        box(2 * P, bottom, 6 * P, w - 2 * P, bottom + 3 * P, 10 * P, 0, bottom);
    }

    private void curtain(float a, float b, float bottom) {
        if (b <= a) return;
        use(PVC);
        box(3 * P, a, 7.5f * P, w - 3 * P, b, 8.5f * P, 0, bottom);
    }

    private void speed(float f) {
        float top = h - 7 * P, bottom = f * (top - 2 * P), win0 = h >= 2 ? bottom + 2 * P + 0.9f : h + 1;
        use(RUBBER);
        box(0, 0, 3 * P, 3 * P, top, 13 * P, 0, 0);
        box(w - 3 * P, 0, 3 * P, w, top, 13 * P, 0, 0);
        use(HAZARD);
        box(0, 0, 2.9f * P, 3 * P, top * 0.25f, 3 * P, 0, 0);
        box(w - 3 * P, 0, 2.9f * P, w, top * 0.25f, 3 * P, 0, 0);
        use(ALU);
        box(0, top, 3 * P, w, h, 13 * P, 0, 0);
        if (win0 < top) {
            float win1 = Math.min(win0 + 6 * P, top);
            curtain(bottom + 2 * P, win0, bottom);
            curtain(win1, top, bottom);
            glass(PANE);
            box(3 * P, win0, 7.6f * P, w - 3 * P, win1, 8.4f * P, 0, 0);
        } else {
            curtain(bottom + 2 * P, top, bottom);
        }
        use(ALU);
        for (int k = 1; bottom + k * 0.75f < top - P; k++) {
            float y = bottom + k * 0.75f;
            box(3 * P, y, 6.5f * P, w - 3 * P, y + P, 9.5f * P, 0, 0);
        }
        use(RUBBER);
        box(3 * P, bottom, 6.5f * P, w - 3 * P, bottom + 2 * P, 9.5f * P, 0, 0);
    }

    private void lift(float f) {
        float b = smooth(f) * h, beam = 2 * h;
        use(FRAME);
        box(0, 0, 3 * P, 2 * P, beam, 13 * P, 0, 0);
        box(w - 2 * P, 0, 3 * P, w, beam, 13 * P, 0, 0);
        use(HAZARD);
        box(0, beam, 2.5f * P, w, beam + 3 * P, 13.5f * P, 0, 0);
        use(FRAME);
        for (float x : new float[] {w * 0.25f, w * 0.75f}) box(x - P / 2, Math.max(h, b + h - P), 7.5f * P, x + P / 2, beam, 8.5f * P, 0, 0);
        use(LIFT);
        box(2 * P, b, 5 * P, w - 2 * P, b + h - P, 11 * P, 2 * P, b);
        use(FRAME);
        box(2 * P - M, b + 3 * P, 4 * P, 3 * P, b + h - 2 * P, 12 * P, 0, 0);
        box(w - 3 * P, b + 3 * P, 4 * P, w - 2 * P + M, b + h - 2 * P, 12 * P, 0, 0);
        for (int i = 1; i < w; i++) box(i - P / 2, b + 3 * P, 4 * P, i + P / 2, b + h - 2 * P, 12 * P, 0, 0);
        box(2 * P - M, b + h - 2 * P, 4 * P, w - 2 * P + M, b + h - P + M, 12 * P, 0, 0);
        use(HAZARD);
        box(2 * P - M, b - M, 4 * P, w - 2 * P + M, b + 3 * P, 12 * P, 0, b);
    }

    private void span(float origin, float dir, float x0, float x1, float v0, float v1, float n0, float n1) {
        box(Math.min(origin + dir * x0, origin + dir * x1), v0, n0, Math.max(origin + dir * x0, origin + dir * x1), v1, n1, 0, 0);
    }

    private void turn(float degrees, int[] axis, float u, float v, float n) {
        stack.pushPose();
        stack.rotateAround(new org.joml.Quaternionf().rotationAxis((be.flip() ? -degrees : degrees) * Mth.DEG_TO_RAD, axis[0], axis[1], axis[2]),
                base[0] + u * uDir[0] + v * vDir[0] + n * nDir[0], base[1] + u * uDir[1] + v * vDir[1] + n * nDir[1],
                base[2] + u * uDir[2] + v * vDir[2] + n * nDir[2]);
        pose = stack.last();
    }

    private void back() {
        stack.popPose();
        pose = stack.last();
    }

    private void use(ResourceLocation texture) {
        vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
    }

    private void glass(ResourceLocation texture) {
        vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
    }

    private int light(float u, float v) {
        int cu = Mth.clamp(Mth.floor(u), 0, w - 1), cv = Mth.clamp(Mth.floor(v), 0, h - 1), i = cu + cv * w;
        if (lights[i] < 0) {
            BlockPos m = be.min();
            lights[i] = LevelRenderer.getLightColor(be.getLevel(),
                    m.offset(cu * uDir[0] + cv * vDir[0], cu * uDir[1] + cv * vDir[1], cu * uDir[2] + cv * vDir[2]));
        }
        return lights[i];
    }

    private void vertex(float u, float v, float n, float tu, float tv, int[] normal, int sign) {
        vc.addVertex(pose, base[0] + u * uDir[0] + v * vDir[0] + n * nDir[0], base[1] + u * uDir[1] + v * vDir[1] + n * nDir[1],
                        base[2] + u * uDir[2] + v * vDir[2] + n * nDir[2])
                .setColor(255, 255, 255, 255).setUv(swap ? tv : tu, swap ? tu : tv).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(glow ? LightTexture.FULL_BRIGHT : light(u, v))
                .setNormal(pose, sign * normal[0], sign * normal[1], sign * normal[2]);
    }

    private void box(float u0, float v0, float n0, float u1, float v1, float n1, float au, float av) {
        if (u1 <= u0 || v1 <= v0) return;
        for (int k = 0; k < 2; k++) {
            float n = k == 0 ? n0 : n1;
            vertex(u0, v0, n, u0 - au, av - v0, nDir, k * 2 - 1);
            vertex(u1, v0, n, u1 - au, av - v0, nDir, k * 2 - 1);
            vertex(u1, v1, n, u1 - au, av - v1, nDir, k * 2 - 1);
            vertex(u0, v1, n, u0 - au, av - v1, nDir, k * 2 - 1);
            float u = k == 0 ? u0 : u1;
            vertex(u, v0, n0, n0, av - v0, uDir, k * 2 - 1);
            vertex(u, v0, n1, n1, av - v0, uDir, k * 2 - 1);
            vertex(u, v1, n1, n1, av - v1, uDir, k * 2 - 1);
            vertex(u, v1, n0, n0, av - v1, uDir, k * 2 - 1);
            float v = k == 0 ? v0 : v1;
            vertex(u0, v, n0, u0 - au, n0, vDir, k * 2 - 1);
            vertex(u1, v, n0, u1 - au, n0, vDir, k * 2 - 1);
            vertex(u1, v, n1, u1 - au, n1, vDir, k * 2 - 1);
            vertex(u0, v, n1, u0 - au, n1, vDir, k * 2 - 1);
        }
    }

    private void decal(float cu, float cv, float r, float n) {
        for (int k = 0; k < 2; k++) {
            float at = k == 0 ? n - GAP : 1 - n + GAP;
            vertex(cu - r, cv - r, at, 0, 1, nDir, k * 2 - 1);
            vertex(cu + r, cv - r, at, 1, 1, nDir, k * 2 - 1);
            vertex(cu + r, cv + r, at, 1, 0, nDir, k * 2 - 1);
            vertex(cu - r, cv + r, at, 0, 0, nDir, k * 2 - 1);
        }
    }
}
