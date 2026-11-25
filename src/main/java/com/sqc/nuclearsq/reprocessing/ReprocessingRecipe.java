package com.sqc.nuclearsq.reprocessing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.registry.ReprocessingRegistry;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public record ReprocessingRecipe(Ingredient ingredient, int count, int time, int energyPerTick, List<Result> results, Kind kind,
                                 @Nullable Ingredient aux, int auxCount, List<FluidCost> fluids, List<FluidOut> fluidResults, int priority)
        implements Recipe<SingleRecipeInput> {

    public static final int AUX_SLOT = 5;

    public ReprocessingRecipe(Ingredient ingredient, int count, int time, int energyPerTick, List<Result> results, Kind kind) {
        this(ingredient, count, time, energyPerTick, results, kind, null, 0, List.of(), List.of(), 0);
    }

    public enum Kind {
        REPROCESSING("reprocessing"),
        ORE_MILLING("ore_milling"),
        ENRICHMENT("enrichment"),
        WEAPONS_PROCESSING("weapons_processing"),
        LEACHING("leaching"),
        UF6_CONVERSION("uf6_conversion"),
        PELLETING("pelleting"),
        FOUNDRY("foundry"),
        SLAG_CRUSHING("slag_crushing"),
        ACID_PLANT("acid_plant"),
        BORIC_ACID("boric_acid"),
        ION_EXCHANGE("ion_exchange"),
        HEAVY_WATER("heavy_water"),
        POLYMER("polymer"),
        VITRIFICATION("vitrification"),
        CHEMISTRY("chemistry"),
        PLATE_MILLING("plate_milling"),
        DUST_MILLING("dust_milling"),
        KILN_FIRING("kiln_firing"),
        BLAST_SMELTING("blast_smelting");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public RecipeType<ReprocessingRecipe> type() {
            return ReprocessingRegistry.TYPES.get(this).get();
        }
    }

    public record Result(ItemStack stack, float chance) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("item").forGetter(Result::stack),
                Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(Result::chance)
        ).apply(i, Result::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Result::stack, ByteBufCodecs.FLOAT, Result::chance, Result::new);
    }

    public record FluidCost(String port, int amount) {
        public static final Codec<FluidCost> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("port").forGetter(FluidCost::port),
                Codec.intRange(1, 64000).fieldOf("amount").forGetter(FluidCost::amount)
        ).apply(i, FluidCost::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidCost> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FluidCost::port, ByteBufCodecs.VAR_INT, FluidCost::amount, FluidCost::new);
    }

    public record FluidOut(String port, net.minecraft.resources.ResourceLocation fluid, int amount) {
        public static final Codec<FluidOut> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("port").forGetter(FluidOut::port),
                net.minecraft.resources.ResourceLocation.CODEC.fieldOf("fluid").forGetter(FluidOut::fluid),
                Codec.intRange(1, 64000).fieldOf("amount").forGetter(FluidOut::amount)
        ).apply(i, FluidOut::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidOut> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FluidOut::port, net.minecraft.resources.ResourceLocation.STREAM_CODEC, FluidOut::fluid,
                ByteBufCodecs.VAR_INT, FluidOut::amount, FluidOut::new);
    }

    public static MapCodec<ReprocessingRecipe> codec(Kind kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ReprocessingRecipe::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(ReprocessingRecipe::count),
                Codec.intRange(1, 32767).fieldOf("time").forGetter(ReprocessingRecipe::time),
                Codec.intRange(0, 100000).fieldOf("energy_per_tick").forGetter(ReprocessingRecipe::energyPerTick),
                Result.CODEC.listOf(0, 4).optionalFieldOf("results", List.of()).forGetter(ReprocessingRecipe::results),
                Ingredient.CODEC_NONEMPTY.optionalFieldOf("aux").forGetter(r -> Optional.ofNullable(r.aux())),
                Codec.intRange(1, 64).optionalFieldOf("aux_count", 1).forGetter(ReprocessingRecipe::auxCount),
                FluidCost.CODEC.listOf().optionalFieldOf("fluids", List.of()).forGetter(ReprocessingRecipe::fluids),
                FluidOut.CODEC.listOf().optionalFieldOf("fluid_results", List.of()).forGetter(ReprocessingRecipe::fluidResults),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(ReprocessingRecipe::priority)
        ).apply(i, (ing, count, time, fe, results, aux, auxCount, fluids, fluidOut, priority) ->
                new ReprocessingRecipe(ing, count, time, fe, results, kind, aux.orElse(null), aux.isPresent() ? auxCount : 0, fluids, fluidOut, priority)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ReprocessingRecipe> streamCodec(Kind kind) {
        return new StreamCodec<>() {
            @Override
            public ReprocessingRecipe decode(RegistryFriendlyByteBuf buf) {
                Ingredient ing = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                int count = buf.readVarInt(), time = buf.readVarInt(), fe = buf.readVarInt();
                List<Result> results = Result.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                Ingredient aux = buf.readBoolean() ? Ingredient.CONTENTS_STREAM_CODEC.decode(buf) : null;
                int auxCount = buf.readVarInt();
                List<FluidCost> fluids = FluidCost.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                List<FluidOut> fluidOut = FluidOut.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                int priority = buf.readVarInt();
                return new ReprocessingRecipe(ing, count, time, fe, results, kind, aux, auxCount, fluids, fluidOut, priority);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ReprocessingRecipe r) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.ingredient());
                buf.writeVarInt(r.count());
                buf.writeVarInt(r.time());
                buf.writeVarInt(r.energyPerTick());
                Result.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.results());
                buf.writeBoolean(r.aux() != null);
                if (r.aux() != null) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.aux());
                buf.writeVarInt(r.auxCount());
                FluidCost.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.fluids());
                FluidOut.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.fluidResults());
                buf.writeVarInt(r.priority());
            }
        };
    }

    public boolean hasAux() {
        return aux != null && auxCount > 0;
    }

    public boolean acceptsAux(ItemStack stack) {
        return hasAux() && !stack.isEmpty() && aux.test(stack);
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && ingredient.test(stack);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return accepts(input.item()) && input.item().getCount() >= count;
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.create();
        out.add(ingredient);
        if (aux != null) out.add(aux);
        return out;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack().copy();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ReprocessingRegistry.SERIALIZERS.get(kind).get();
    }

    @Override
    public RecipeType<?> getType() {
        return kind.type();
    }

    public static class Serializer implements RecipeSerializer<ReprocessingRecipe> {
        private final MapCodec<ReprocessingRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, ReprocessingRecipe> streamCodec;

        public Serializer(Kind kind) {
            this.codec = ReprocessingRecipe.codec(kind);
            this.streamCodec = ReprocessingRecipe.streamCodec(kind);
        }

        @Override
        public MapCodec<ReprocessingRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ReprocessingRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
