package com.sqc.nuclearsq.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class OreDepositFeature extends Feature<OreDepositFeature.Config> {

    public record Config(List<OreConfiguration.TargetBlockState> core, List<OreConfiguration.TargetBlockState> poor, int size,
                         float discard, float chunkChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                OreConfiguration.TargetBlockState.CODEC.listOf().fieldOf("core").forGetter(Config::core),
                OreConfiguration.TargetBlockState.CODEC.listOf().fieldOf("poor").forGetter(Config::poor),
                Codec.intRange(0, 64).fieldOf("size").forGetter(Config::size),
                Codec.floatRange(0f, 1f).optionalFieldOf("discard_chance_on_air_exposure", 0f).forGetter(Config::discard),
                Codec.floatRange(0f, 1f).optionalFieldOf("chunk_chance", 1f).forGetter(Config::chunkChance)).apply(i, Config::new));
    }

    public OreDepositFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos o = ctx.origin();
        Config cfg = ctx.config();
        if (cfg.chunkChance() < 1f) {
            long salt = BuiltInRegistries.BLOCK.getKey(cfg.core().get(0).state.getBlock()).hashCode();
            long seed = level.getSeed() ^ new ChunkPos(o).toLong() * 341873128712L ^ salt * 132897987541L;
            if (RandomSource.create(seed).nextFloat() >= cfg.chunkChance()) return false;
        }
        OreConfiguration ore = new OreConfiguration(cfg.core(), cfg.size(), cfg.discard());
        if (!Feature.ORE.place(new FeaturePlaceContext<>(ctx.topFeature(), level, ctx.chunkGenerator(), rnd, o, ore))) return false;
        Set<BlockState> states = new HashSet<>();
        for (OreConfiguration.TargetBlockState t : cfg.core()) states.add(t.state);
        int r = Mth.ceil(cfg.size() / 8f) + 2;
        List<BlockPos> edge = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r - 2; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.setWithOffset(o, dx, dy, dz);
                    if (!level.ensureCanWrite(p) || !states.contains(level.getBlockState(p))) continue;
                    for (Direction d : Direction.values()) {
                        BlockPos n = p.immutable().relative(d);
                        if (seen.add(n) && level.ensureCanWrite(n) && !states.contains(level.getBlockState(n))) edge.add(n);
                    }
                }
            }
        }
        int want = 2 + rnd.nextInt(3);
        for (int k = 0; k < edge.size() && want > 0; k++) {
            int j = k + rnd.nextInt(edge.size() - k);
            BlockPos n = edge.set(j, edge.get(k));
            BlockState here = level.getBlockState(n);
            for (OreConfiguration.TargetBlockState t : cfg.poor()) {
                if (t.target.test(here, rnd)) {
                    level.setBlock(n, t.state, 2);
                    want--;
                    break;
                }
            }
        }
        return true;
    }
}
