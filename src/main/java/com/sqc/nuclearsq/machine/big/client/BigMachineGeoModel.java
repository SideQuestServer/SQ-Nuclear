package com.sqc.nuclearsq.machine.big.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import javax.annotation.Nullable;

public class BigMachineGeoModel extends GeoModel<BigMachineBlockEntity> {
    private static ResourceLocation res(String path) {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(BigMachineBlockEntity be, @Nullable GeoRenderer<BigMachineBlockEntity> renderer) {
        return res("geo/" + be.machine().id + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BigMachineBlockEntity be, @Nullable GeoRenderer<BigMachineBlockEntity> renderer) {
        return res("textures/block/" + be.machine().id + ".png");
    }

    @Deprecated
    @Override
    public ResourceLocation getModelResource(BigMachineBlockEntity be) { return getModelResource(be, null); }

    @Deprecated
    @Override
    public ResourceLocation getTextureResource(BigMachineBlockEntity be) { return getTextureResource(be, null); }

    @Override
    public ResourceLocation getAnimationResource(BigMachineBlockEntity be) { return res("animations/" + be.machine().id + ".animation.json"); }

    private static final String[] LAMPS = {"lamp_green", "lamp_yellow", "lamp_red"};

    @Override
    public void setCustomAnimations(BigMachineBlockEntity be, long instanceId, AnimationState<BigMachineBlockEntity> state) {
        applyCode(be, state.getPartialTick());
    }

    public void applyCode(BigMachineBlockEntity be, double partialTick) {
        customAnimations(be, partialTick);
        BakedMachines.reapply();
    }

    private void customAnimations(BigMachineBlockEntity be, double partialTick) {
        if (be.machine() == BigMachine.PARTICLE_ASSEMBLER) {
            particleBones(be, partialTick);
            return;
        }
        if (be.rack() != null) {
            rackBones(be);
            return;
        }
        if (be.machine().isAutocrafter()) {
            componentBones(be, partialTick);
            return;
        }
        if (be.machine() == BigMachine.SOLAR_FIELD) {
            trackerBones(be, partialTick);
            return;
        }
        if (be.machine() != BigMachine.CONTROL_ROD_DRIVE) return;
        int on = be.statusLamp();
        for (int i = 0; i < LAMPS.length; i++) {
            var bone = getAnimationProcessor().getBone(LAMPS[i]);
            if (bone != null) bone.setHidden(i != on);
        }
    }

    private void trackerBones(BigMachineBlockEntity be, double partialTick) {
        int rot = be.formedRot();
        float a = be.getLevel() == null ? 0f : com.sqc.nuclearsq.power.SolarMath.track(be.getLevel(), (float) partialTick) * (rot >= 2 ? -1f : 1f);
        for (int k = 0; k < 4; k++) {
            var bone = getAnimationProcessor().getBone("tracker_" + k);
            if (bone == null) continue;
            bone.setRotZ((rot & 1) == 0 ? a : 0f);
            bone.setRotX((rot & 1) == 0 ? 0f : a);
        }
    }

    private void particleBones(BigMachineBlockEntity be, double partialTick) {
        var p = com.sqc.nuclearsq.machine.big.ParticleAssemblerFx.pose(be, (float) partialTick);
        boolean active = p != com.sqc.nuclearsq.machine.big.ParticleAssemblerFx.REST;
        float spin = active && be.getLevel() != null ? (be.getLevel().getGameTime() + (float) partialTick) * 0.35f : 0f;
        String[] sides = {"l", "r"};
        for (int i = 0; i < 2; i++) {
            float sign = i == 0 ? 1f : -1f;
            var arm = getAnimationProcessor().getBone("arm_" + sides[i]);
            var grip = getAnimationProcessor().getBone("grip_" + sides[i]);
            var ball = getAnimationProcessor().getBone("ball_" + sides[i]);
            if (arm != null) arm.setPosX(sign * p.travel());
            if (grip != null) {
                grip.setPosY(p.drop());
                grip.setRotZ(-sign * p.tilt());
            }
            if (ball != null) {
                ball.setHidden(!p.balls());
                ball.setScaleX(p.scale());
                ball.setScaleY(p.scale());
                ball.setScaleZ(p.scale());
            }
        }
        var fl = getAnimationProcessor().getBone("flash");
        if (fl != null) {
            fl.setHidden(p.flash() < 0.02f);
            float sc = Math.max(p.flash(), 0.001f);
            fl.setScaleX(sc);
            fl.setScaleY(sc);
            fl.setScaleZ(sc);
            fl.setRotY(spin * 2f);
        }
    }

    private void rackBones(BigMachineBlockEntity be) {
        int[] codes = be.rack().codes();
        boolean on = be.isRunning();
        long t = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        for (int k = 0; k < codes.length; k++) {
            int c = codes[k], tier = com.sqc.nuclearsq.storage.ServerRack.tier(c);
            boolean full = com.sqc.nuclearsq.storage.ServerRack.full(c);
            hide("disk_" + k, tier == 0);
            if (tier == 0) continue;
            for (int i = 1; i <= 5; i++) hide("tier_" + k + "_" + i, i != tier);
            hide("led_" + k + "_ok", !on || full);
            hide("led_" + k + "_full", !on || !full);
            hide("act_" + k, !on || !com.sqc.nuclearsq.storage.ServerRack.busy(c) || ((t + k) & 2) == 0);
        }
    }

    private static float ease(float t, float a, float b) {
        float x = Math.max(0f, Math.min(1f, (t - a) / (b - a)));
        return x * x * (3f - 2f * x);
    }

    private void componentBones(BigMachineBlockEntity be, double partialTick) {
        boolean run = be.isRunning() && be.getLevel() != null;
        float time = run ? be.getLevel().getGameTime() + (float) partialTick : 0f;
        int cycle = Math.max(1, be.machine().extra("cycle_ticks"));
        float t = run ? (time % cycle) / cycle : 0f;
        float travel = run ? 19f * (ease(t, 0.20f, 0.38f) - ease(t, 0.54f, 0.70f)) : 0f;
        float lift = run ? -7f * (ease(t, 0f, 0.10f) - ease(t, 0.12f, 0.20f) + ease(t, 0.38f, 0.46f) - ease(t, 0.46f, 0.54f)) : 0f;
        boolean carrying = run && t < 0.46f;
        String[] sides = {"l", "r"};
        for (int i = 0; i < 2; i++) {
            float sign = i == 0 ? 1f : -1f;
            var arm = getAnimationProcessor().getBone("arm_" + sides[i]);
            var lf = getAnimationProcessor().getBone("lift_" + sides[i]);
            var grip = getAnimationProcessor().getBone("grip_" + sides[i]);
            if (arm != null) arm.setPosX(sign * travel);
            if (lf != null) lf.setPosY(lift);
            if (grip != null) grip.setRotY(run ? (float) Math.PI / 2f * ease(t, 0.20f, 0.38f) * (1f - ease(t, 0.54f, 0.70f)) : 0f);
            hide("part_" + sides[i], !carrying);
        }
        var ram = getAnimationProcessor().getBone("ram");
        if (ram != null) ram.setPosY(run ? -11f * (ease(t, 0.50f, 0.60f) - ease(t, 0.62f, 0.70f)) : 0f);
        var fl = getAnimationProcessor().getBone("press_flash");
        float flash = run ? ease(t, 0.58f, 0.61f) - ease(t, 0.61f, 0.66f) : 0f;
        if (fl != null) {
            fl.setHidden(flash < 0.02f);
            float sc = Math.max(flash, 0.001f);
            fl.setScaleX(sc);
            fl.setScaleY(sc);
            fl.setScaleZ(sc);
        }
        var pr = getAnimationProcessor().getBone("product");
        if (pr != null) {
            pr.setHidden(!run || t < 0.46f);
            float chute = ease(t, 0.70f, 0.78f);
            pr.setPosZ(-16f * chute);
            pr.setPosY(-5f * chute);
            pr.setPosX(-32f * ease(t, 0.78f, 1f));
        }
        float roll = run ? time * 0.3f : 0f;
        for (int k = 0; k < 5; k++) {
            var roller = getAnimationProcessor().getBone("belt_roller_" + k);
            if (roller != null) roller.setRotZ(-roll);
        }
    }

    private void hide(String name, boolean hidden) {
        var bone = getAnimationProcessor().getBone(name);
        if (bone != null) bone.setHidden(hidden);
    }
}
