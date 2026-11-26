package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, NuclearSQ.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NUKE_DUST =
            PARTICLES.register("nuke_dust", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NUKE_SURGE =
            PARTICLES.register("nuke_surge", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COOLING_STEAM =
            PARTICLES.register("cooling_steam", () -> new SimpleParticleType(true));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
