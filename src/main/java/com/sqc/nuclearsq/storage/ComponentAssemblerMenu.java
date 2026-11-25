package com.sqc.nuclearsq.storage;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ComponentAssemblerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 196, HEIGHT = 280, DATA = 18;
    public static final int GRID_X = 10, GRID_Y = 32, PREVIEW_X = 78, PREVIEW_Y = 50, BLANK_X = 112, BLANK_Y = 32,
            CARD_X = 112, CARD_Y = 68, CARDS_Y = 106, JOB_X = 10, JOB_Y = 134, INV_Y = 198, UPG_X = 134, UPG_Y = 50;
    public static final int PREVIEW = 9, JOB = 10, CARDS = 11, BLANK = CARDS + ComponentAssembler.CARDS, WRITTEN = BLANK + 1,
            UPG = WRITTEN + 1, INV = UPG + 1;
    public static final int BTN_WRITE = 0, BTN_CLEAR = 1, BTN_CANCEL = 2, BTN_CANCEL_ALL = 3;

    public final BlockPos pos;
    private final ComponentAssembler assembler;
    private final ContainerData data;
    private final Player player;

    public ComponentAssemblerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ComponentAssembler(BigMachine.COMPONENT_ASSEMBLER, new MachineEnergy(1, 0, 0),
                new ItemStackHandler(1), () -> {}), new SimpleContainerData(DATA));
    }

    public ComponentAssemblerMenu(int id, Inventory inv, BlockPos pos, ComponentAssembler assembler, ContainerData data) {
        super(StorageRegistry.ASSEMBLER_MENU.get(), id);
        this.pos = pos;
        this.assembler = assembler;
        this.data = data;
        this.player = inv.player;
        for (int i = 0; i < ComponentAssembler.GRID; i++) {
            addSlot(new Ghost(assembler.template(), i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        addSlot(new Ghost(assembler.display(), 0, PREVIEW_X, PREVIEW_Y));
        addSlot(new Ghost(assembler.display(), 1, JOB_X, JOB_Y));
        int cx = (WIDTH - ComponentAssembler.CARDS * 18) / 2 + 1;
        for (int i = 0; i < ComponentAssembler.CARDS; i++) addSlot(new SlotItemHandler(assembler.cards(), i, cx + i * 18, CARDS_Y));
        addSlot(new SlotItemHandler(assembler.encode(), 0, BLANK_X, BLANK_Y));
        addSlot(new SlotItemHandler(assembler.encode(), 1, CARD_X, CARD_Y));
        addSlot(new SlotItemHandler(assembler.upgrades(), 0, UPG_X, UPG_Y));
        int px = (WIDTH - 162) / 2 + 1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    private static final class Ghost extends SlotItemHandler {
        Ghost(IItemHandler h, int index, int x, int y) {
            super(h, index, x, y);
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player p) { return false; }
    }

    public int status() { return data.get(0); }
    public int progress() { return data.get(1); }
    public int jobs() { return data.get(2); }
    public int energyPercent() { return data.get(3); }
    public int powerUse() { return data.get(4); }
    public int leftInStep() { return data.get(5); }
    public int previewKind() { return data.get(6); }
    public int jobAmount() { return data.get(7); }
    public int step() { return data.get(8); }
    public int steps() { return data.get(9); }
    public boolean formed() { return data.get(10) != 0; }
    public int lanesAllowed() { return data.get(11); }
    public int lanesBusy() { return data.get(12); }
    public int tempo() { return data.get(13); }
    public int sizeUsed() { return data.get(14); }
    public int sizeAllowed() { return data.get(15); }
    public int stalledMinutes() { return data.get(16); }
    public int peers() { return data.get(17); }

    public static ContainerData data(BigMachineBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                ComponentAssembler a = be.assembler();
                if (a == null) return 0;
                ComponentAssembler.Job j = a.jobs().isEmpty() ? null : a.jobs().get(0);
                MachineEnergy e = be.energy();
                return switch (i) {
                    case 0 -> a.status();
                    case 1 -> a.progressPercent();
                    case 2 -> a.jobs().size();
                    case 3 -> e == null || e.getMaxEnergyStored() <= 0 ? 0 : (int) (e.getEnergyStored() * 100L / e.getMaxEnergyStored());
                    case 4 -> a.powerUse();
                    case 5 -> j == null ? 0 : Math.min(Short.MAX_VALUE, j.leftInStep());
                    case 6 -> a.previewKind();
                    case 7 -> j == null ? 0 : Math.min(Short.MAX_VALUE, j.amount);
                    case 8 -> j == null ? 0 : j.step() + 1;
                    case 9 -> j == null ? 0 : j.steps();
                    case 10 -> be.isFormed() ? 1 : 0;
                    case 11 -> a.laneCap();
                    case 12 -> a.busyLanes();
                    case 13 -> a.tempoLevel();
                    case 14 -> (int) Math.min(Short.MAX_VALUE, a.sizeUsed());
                    case 15 -> (int) Math.min(Short.MAX_VALUE, a.sizeCap());
                    case 16 -> be.getLevel() == null ? 0 : Math.min(Short.MAX_VALUE, a.stalledMinutes(be.getLevel().getGameTime()));
                    case 17 -> a.peers();
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) { }

            @Override
            public int getCount() { return DATA; }
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId >= 0 && slotId < ComponentAssembler.GRID) {
            ghostClick(slotId, button, type);
            return;
        }
        if (slotId == PREVIEW || slotId == JOB) return;
        super.clicked(slotId, button, type, p);
    }

    private void ghostClick(int i, int button, ClickType type) {
        ItemStackHandler t = assembler.template();
        ItemStack cur = t.getStackInSlot(i), held = getCarried();
        ItemStack next = cur;
        if (type == ClickType.QUICK_MOVE || type == ClickType.THROW) next = ItemStack.EMPTY;
        else if (type == ClickType.PICKUP) {
            if (held.isEmpty()) next = button == 0 || cur.getCount() <= 1 ? ItemStack.EMPTY : cur.copyWithCount(cur.getCount() - 1);
            else if (button == 0) next = held.copyWithCount(Math.min(held.getCount(), 64));
            else next = ItemStack.isSameItemSameComponents(cur, held) ? cur.copyWithCount(Math.min(cur.getCount() + 1, 64)) : held.copyWithCount(1);
        } else return;
        t.setStackInSlot(i, next);
        if (!player.level().isClientSide) assembler.updatePreview(player.level());
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p.level().getBlockEntity(pos) instanceof BigMachineBlockEntity be) || be.assembler() != assembler) return false;
        switch (id) {
            case BTN_WRITE -> {
                if (!assembler.writeCard(p.level())) return false;
            }
            case BTN_CLEAR -> {
                for (int i = 0; i < ComponentAssembler.GRID; i++) assembler.template().setStackInSlot(i, ItemStack.EMPTY);
                assembler.updatePreview(p.level());
            }
            case BTN_CANCEL -> assembler.cancel(false);
            case BTN_CANCEL_ALL -> assembler.cancel(true);
            default -> { return false; }
        }
        be.setChanged();
        return true;
    }

    public void templateRecipe(net.minecraft.resources.ResourceLocation id) {
        if (!(player.level().getBlockEntity(pos) instanceof BigMachineBlockEntity be) || be.assembler() != assembler) return;
        var r = player.level().getRecipeManager().byKey(id).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
        if (r == null || !com.sqc.nuclearsq.progression.MachineRecipes.makes(be.machine(), r)) return;
        ItemStack[] grid = com.sqc.nuclearsq.progression.MachineRecipes.template(r, ComponentAssembler.GRID, player.level().registryAccess());
        if (grid == null) return;
        for (int i = 0; i < ComponentAssembler.GRID; i++) assembler.template().setStackInSlot(i, grid[i]);
        assembler.updatePreview(player.level());
        be.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < CARDS) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < INV) {
            if (!moveItemStackTo(stack, INV, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(StorageRegistry.ASSEMBLER_TEMPO.get())) {
            if (!moveItemStackTo(stack, UPG, UPG + 1, false)) return ItemStack.EMPTY;
        } else if (PatternCardItem.pattern(stack) != null) {
            if (!moveItemStackTo(stack, CARDS, BLANK, false)) return ItemStack.EMPTY;
        } else if (PatternCardItem.isBlank(stack)) {
            if (!moveItemStackTo(stack, BLANK, WRITTEN, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.level().getBlockEntity(pos) instanceof BigMachineBlockEntity be && be.assembler() != null
                && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 24 * 24;
    }
}
