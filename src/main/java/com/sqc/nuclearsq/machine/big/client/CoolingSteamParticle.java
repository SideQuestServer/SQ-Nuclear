package com.sqc.nuclearsq.machine.big.client;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.explosion.client.NukeDustParticle;
import com.sqc.nuclearsq.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public class CoolingSteamParticle extends TextureSheetParticle {
    private static final int MAX_LIVE = 360;
    private static int live = 0;

    private final float baseSize;
    private final float maxAlpha;
    private final float spin;

    protected CoolingSteamParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        double rise = Math.max(0.05, vy);
        this.xd = vx;
        this.yd = rise;
        this.zd = vz;
        this.baseSize = (float) (rise * 26.0) * (0.8f + random.nextFloat() * 0.4f);
        this.quadSize = baseSize;
        this.lifetime = 160 + random.nextInt(80);
        float g = 0.86f + random.nextFloat() * 0.12f;
        this.rCol = g;
        this.gCol = g;
        this.bCol = Math.min(1f, g + 0.02f);
        this.maxAlpha = 0.36f + random.nextFloat() * 0.16f;
        this.alpha = 0f;
        this.friction = 0.985f;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.012f;
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
        xd *= friction;
        yd = yd * friction + 0.0012;
        zd *= friction;
        float p = age / (float) lifetime;
        quadSize = baseSize * (1f + 2.2f * p);
        alpha = maxAlpha * Math.min(1f, p / 0.12f) * (p < 0.35f ? 1f : (1f - p) / 0.65f);
        roll += spin;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return NukeDustParticle.SOFT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            if (live >= MAX_LIVE) return null;
            ParticleStatus s = Minecraft.getInstance().options.particles().get();
            if (s == ParticleStatus.MINIMAL && level.random.nextInt(3) != 0) return null;
            if (s == ParticleStatus.DECREASED && level.random.nextInt(3) == 0) return null;
            return new CoolingSteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    @EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegister(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.COOLING_STEAM.get(), Provider::new);
        }
    }
}
