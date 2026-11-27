package com.sqc.nuclearsq.compat.jei;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.registry.AssemblyRegistry;
import com.sqc.nuclearsq.registry.BigMachineRegistry;
import com.sqc.nuclearsq.registry.HeavyAssemblyRegistry;
import com.sqc.nuclearsq.registry.EquipmentRegistry;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;
import com.sqc.nuclearsq.storage.CraftingPayloads;
import com.sqc.nuclearsq.storage.CraftingTerminalMenu;
import com.sqc.nuclearsq.storage.StorageRegistry;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

@JeiPlugin
public class SqcJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "jei");
    }

    private static final RecipeType<SqcJeiCategories.Needs> ASSEMBLY = RecipeType.create(NuclearSQ.MODID, "assembly", SqcJeiCategories.Needs.class);
    private static final RecipeType<SqcJeiCategories.Needs> SMALL_PARTS =
            RecipeType.create(NuclearSQ.MODID, "small_parts_assembly", SqcJeiCategories.Needs.class);
    private static final RecipeType<SqcJeiCategories.Needs> EQUIPMENT =
            RecipeType.create(NuclearSQ.MODID, "equipment_assembler", SqcJeiCategories.Needs.class);
    private static final RecipeType<SqcJeiCategories.Needs> MICRO =
            RecipeType.create(NuclearSQ.MODID, "micro_assembler", SqcJeiCategories.Needs.class);
    private static final RecipeType<SqcJeiCategories.Needs> CNC =
            RecipeType.create(NuclearSQ.MODID, "cnc_mill", SqcJeiCategories.Needs.class);
    private static final RecipeType<SqcJeiCategories.Needs> SOLDER =
            RecipeType.create(NuclearSQ.MODID, "solder_machine", SqcJeiCategories.Needs.class);
    private static final RecipeType<HeavyAssemblyRecipe> HEAVY = RecipeType.create(NuclearSQ.MODID, "heavy_assembly", HeavyAssemblyRecipe.class);
    private static final RecipeType<SqcJeiCategories.Drop> PARTICLE = RecipeType.create(NuclearSQ.MODID, "particle_assembler", SqcJeiCategories.Drop.class);
    private final Map<ReprocessingRecipe.Kind, RecipeType<RecipeHolder<ReprocessingRecipe>>> machineTypes = new EnumMap<>(ReprocessingRecipe.Kind.class);

    private static ItemStack controller(BigMachine m) {
        return new ItemStack(BigMachineRegistry.CONTROLLER_ITEMS.get(m).get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        var gui = reg.getJeiHelpers().getGuiHelper();
        reg.addRecipeCategories(
                new SqcJeiCategories.Formless(gui, ASSEMBLY, "reactor_assembler", controller(BigMachine.REACTOR_ASSEMBLER)),
                new SqcJeiCategories.Formless(gui, SMALL_PARTS, "small_parts_assembler", controller(BigMachine.SMALL_PARTS_ASSEMBLER)),
                new SqcJeiCategories.Formless(gui, MICRO, "micro_assembler", controller(BigMachine.MICRO_ASSEMBLER)),
                new SqcJeiCategories.Formless(gui, CNC, "cnc_mill", controller(BigMachine.CNC_MILL)),
                new SqcJeiCategories.Formless(gui, SOLDER, "solder_machine", controller(BigMachine.SOLDER_MACHINE)),
                new SqcJeiCategories.Formless(gui, EQUIPMENT, "equipment_assembler", controller(BigMachine.EQUIPMENT_ASSEMBLER)),
                new SqcJeiCategories.Heavy(gui, HEAVY, controller(BigMachine.HEAVY_ASSEMBLER)),
                new SqcJeiCategories.Particle(gui, PARTICLE, controller(BigMachine.PARTICLE_ASSEMBLER)));
        for (BigMachine m : BigMachine.values()) {
            ReprocessingRecipe.Kind kind = m.recipeKind();
            if (kind == null) continue;
            RecipeType<RecipeHolder<ReprocessingRecipe>> type = RecipeType.createFromVanilla(kind.type());
            machineTypes.put(kind, type);
            reg.addRecipeCategories(new SqcJeiCategories.Machine(gui, type, m, controller(m)));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var manager = level.getRecipeManager();
        var tab = com.sqc.nuclearsq.registry.ModCreativeTabs.TAB.get();
        if (tab.getDisplayItems().isEmpty()) tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        Map<net.minecraft.world.item.Item, Integer> pos = new java.util.HashMap<>();
        tab.getDisplayItems().forEach(st -> pos.putIfAbsent(st.getItem(), pos.size()));
        java.util.Comparator<ItemStack> byTab = java.util.Comparator.comparingInt(st -> pos.getOrDefault(st.getItem(), Integer.MAX_VALUE));
        var needs = java.util.Comparator.comparing(SqcJeiCategories.Needs::result, byTab);
        reg.addRecipes(ASSEMBLY, manager.getAllRecipesFor(AssemblyRegistry.TYPE.get()).stream().map(h -> SqcJeiCategories.needs(h.value())).sorted(needs).toList());
        var parts = manager.getAllRecipesFor(SmallPartsRegistry.TYPE.get());
        reg.addRecipes(SMALL_PARTS, parts.stream().filter(h -> !h.value().result().is(BigCrafter.MICRO_PRODUCTS) && !h.value().result().is(BigCrafter.CNC_PRODUCTS) && !h.value().result().is(BigCrafter.SOLDER_PRODUCTS)).map(h -> SqcJeiCategories.needs(h.value())).sorted(needs).toList());
        reg.addRecipes(MICRO, parts.stream().filter(h -> h.value().result().is(BigCrafter.MICRO_PRODUCTS)).map(h -> SqcJeiCategories.needs(h.value())).sorted(needs).toList());
        reg.addRecipes(CNC, parts.stream().filter(h -> h.value().result().is(BigCrafter.CNC_PRODUCTS) || h.value().result().is(BigCrafter.CNC_SHARED)).map(h -> SqcJeiCategories.needs(h.value())).sorted(needs).toList());
        reg.addRecipes(SOLDER, parts.stream().filter(h -> h.value().result().is(BigCrafter.SOLDER_PRODUCTS)).map(h -> SqcJeiCategories.needs(h.value())).sorted(needs).toList());
        reg.addRecipes(EQUIPMENT, manager.getAllRecipesFor(EquipmentRegistry.TYPE.get()).stream().map(h -> SqcJeiCategories.needs(h.value().base())).sorted(needs).toList());
        reg.addRecipes(HEAVY, manager.getAllRecipesFor(HeavyAssemblyRegistry.TYPE.get()).stream().map(h -> h.value()).sorted(java.util.Comparator.comparing(HeavyAssemblyRecipe::result, byTab)).toList());
        reg.addRecipes(PARTICLE, SqcJeiCategories.particleDrops());
        machineTypes.forEach((kind, type) -> reg.addRecipes(type, manager.getAllRecipesFor(kind.type())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalysts(ASSEMBLY, controller(BigMachine.REACTOR_ASSEMBLER), controller(BigMachine.COMPUTE_ASSEMBLER),
                controller(BigMachine.COMPONENT_ASSEMBLER), controller(BigMachine.QUANTUM_COMPUTER));
        reg.addRecipeCatalysts(SMALL_PARTS, controller(BigMachine.SMALL_PARTS_ASSEMBLER), controller(BigMachine.COMPONENT_ASSEMBLER),
                controller(BigMachine.QUANTUM_COMPUTER));
        reg.addRecipeCatalysts(MICRO, controller(BigMachine.MICRO_ASSEMBLER), controller(BigMachine.COMPONENT_ASSEMBLER),
                controller(BigMachine.QUANTUM_COMPUTER));
        reg.addRecipeCatalysts(CNC, controller(BigMachine.CNC_MILL), controller(BigMachine.COMPONENT_ASSEMBLER),
                controller(BigMachine.QUANTUM_COMPUTER));
        reg.addRecipeCatalysts(SOLDER, controller(BigMachine.SOLDER_MACHINE), controller(BigMachine.COMPONENT_ASSEMBLER),
                controller(BigMachine.QUANTUM_COMPUTER));
        reg.addRecipeCatalysts(EQUIPMENT, controller(BigMachine.EQUIPMENT_ASSEMBLER));
        reg.addRecipeCatalysts(HEAVY, controller(BigMachine.HEAVY_ASSEMBLER));
        reg.addRecipeCatalysts(PARTICLE, controller(BigMachine.PARTICLE_ASSEMBLER));
        reg.addRecipeCatalysts(RecipeTypes.CRAFTING, controller(BigMachine.COMPONENT_ASSEMBLER), controller(BigMachine.QUANTUM_COMPUTER));
        reg.addRecipeCatalysts(RecipeTypes.SMELTING, controller(BigMachine.KILN));
        reg.addRecipeCatalysts(RecipeTypes.BLASTING, controller(BigMachine.BLAST_FURNACE));
        for (BigMachine m : BigMachine.values()) {
            ReprocessingRecipe.Kind kind = m.recipeKind();
            if (kind != null) reg.addRecipeCatalysts(machineTypes.get(kind), controller(m));
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiBridge.set(runtime);
        var manager = runtime.getRecipeManager();
        manager.createRecipeCategoryLookup().includeHidden().get()
                .map(c -> c.getRecipeType())
                .filter(t -> t.getUid().getPath().startsWith("tag_recipes/"))
                .toList()
                .forEach(manager::hideRecipeCategory);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBridge.set(null);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new Handler(), RecipeTypes.CRAFTING);
    }

    private static final class Handler implements IRecipeTransferHandler<CraftingTerminalMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends CraftingTerminalMenu> getContainerClass() {
            return CraftingTerminalMenu.class;
        }

        @Override
        public Optional<MenuType<CraftingTerminalMenu>> getMenuType() {
            return Optional.of(StorageRegistry.CRAFTING_MENU.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(CraftingTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                Player player, boolean maxTransfer, boolean doTransfer) {
            if (doTransfer) PacketDistributor.sendToServer(new CraftingPayloads.Fill(menu.containerId, recipe.id()));
            return null;
        }
    }
}
