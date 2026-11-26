package com.sqc.nuclearsq.explosion.client;

import com.sqc.nuclearsq.registry.ModParticles;

import com.sqc.nuclearsq.NuclearSQ;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public class NukeDustParticle extends TextureSheetParticle {
    private static final ResourceLocation PARTICLE_ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
    private static int live = 0;
    public static int live() { return live; }
    public static void resetLive() { live = 0; }

    static float nextGlow = 0f;

    private final float baseSize;
    private final float spin;
    private final float maxAlpha;
    private final float glow;
    private final float dr, dg, db;

    protected NukeDustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean surge) {
        super(level, x, y, z);
        this.xd = vx + (random.nextDouble() - 0.5) * 0.01;
        this.yd = vy + (random.nextDouble() - 0.5) * 0.01;
        this.zd = vz + (random.nextDouble() - 0.5) * 0.01;
        this.lifetime = surge ? 90 + random.nextInt(70) : 120 + random.nextInt(110);
        this.baseSize = surge ? 17f + random.nextFloat() * 10f : 7.5f + random.nextFloat() * 4.5f;
        this.quadSize = baseSize;
        float g = surge ? 0.28f + random.nextFloat() * 0.10f : 0.09f + random.nextFloat() * 0.10f;
        this.dr = g + (surge ? 0.07f : 0.02f);
        this.dg = g + (surge ? 0.03f : 0f);
        this.db = g - (surge ? 0.03f : 0.01f);
        this.glow = surge ? 0f : nextGlow * (0.6f + 0.4f * random.nextFloat());
        this.maxAlpha = surge ? 0.82f : 0.88f;
        tint();
        this.alpha = 0f;
        this.friction = 0.97f;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.roll = random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.03f;
        pickSprite(sprites);
        live++;
    }

    @Override
    public void remove() {
        if (isAlive() && live > 0) live--;
        super.remove();
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;
        oRoll = roll;
        if (age++ >= lifetime) { remove(); return; }
        move(xd, yd, zd);
        xd *= friction; yd *= friction; zd *= friction;
        float p = age / (float) lifetime;
        quadSize = baseSize * (1f + 0.9f * p);
        alpha = Math.min(1f, age / 10f) * maxAlpha * (p < 0.65f ? 1f : (1f - p) / 0.35f);
        roll += spin;
        tint();
    }

    private float heat() {
        return glow * Math.max(0f, 1f - age / 40f);
    }

    private void tint() {
        float h = heat();
        rCol = dr + (1.0f - dr) * h;
        gCol = dg + (0.45f - dg) * h * h;
        bCol = db + (0.12f - db) * h * h;
    }

    public static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, PARTICLE_ATLAS);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "NUCLEARSQ_SOFT_SMOKE";
        }
    };

    @Override
    public ParticleRenderType getRenderType() {
        return SOFT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        int base = super.getLightColor(partialTick);
        if (heat() > 0.2f) return 0xF000F0;
        return (base & 0xFF0000) | Math.max(base & 0xFF, 0xA0);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean surge;

        public Provider(SpriteSet sprites, boolean surge) { this.sprites = sprites; this.surge = surge; }

        @Override
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new NukeDustParticle(level, x, y, z, vx, vy, vz, sprites, surge);
        }
    }

    @EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegister(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.NUKE_DUST.get(), s -> new Provider(s, false));
            event.registerSpriteSet(ModParticles.NUKE_SURGE.get(), s -> new Provider(s, true));
        }
    }
}
