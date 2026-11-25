package com.sqc.nuclearsq.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class NetPipeAdapterMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, SHOW_Y = 44, INV_Y = 90, HEIGHT = INV_Y + 82, DATA = 5;
    public static final int MACHINE_X = 30, RESULT_X = 64, SHOWN = 2, FIRST_INV = SHOWN + NetPipeAdapterBlockEntity.FILTER_SLOTS;
    public static final int BTN_MODE = 0, BTN_ENABLE = 1;
    public final BlockPos pos;
    private final Container filters;
    private final ContainerData data;

    public NetPipeAdapterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new SimpleContainer(SHOWN), new SimpleContainer(NetPipeAdapterBlockEntity.FILTER_SLOTS), new SimpleContainerData(DATA));
    }

    public NetPipeAdapterMenu(int id, Inventory inv, BlockPos pos, Container shown, Container filters, ContainerData data) {
        super(StorageRegistry.NET_PIPE_ADAPTER_MENU.get(), id);
        this.pos = pos;
        this.filters = filters;
        this.data = data;
        int[] xs = {MACHINE_X, RESULT_X};
        for (int i = 0; i < SHOWN; i++) addSlot(new Shown(shown, i, xs[i], SHOW_Y, false));
        int x0 = (WIDTH - NetPipeAdapterBlockEntity.FILTER_SLOTS * 18) / 2 + 1;
        for (int i = 0; i < NetPipeAdapterBlockEntity.FILTER_SLOTS; i++) addSlot(new Shown(filters, i, x0 + i * 18, SHOW_Y, true));
        int px = (WIDTH - 162) / 2 + 1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, px + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, px + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public static void open(ServerPlayer sp, NetPipeAdapterBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new NetPipeAdapterMenu(id, inv, pos, be.shown(), be.filters(), be.menuData()),
                Component.translatable("block.nuclearsq.net_pipe_adapter_sqc")), buf -> buf.writeBlockPos(pos));
    }

    private final class Shown extends Slot {
        private final boolean filter;

        Shown(Container c, int i, int x, int y, boolean filter) {
            super(c, i, x, y);
            this.filter = filter;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return filter == exporting();
        }
    }

    public boolean exporting() { return data.get(0) == NetPipeAdapterBlockEntity.EXPORT; }

    public boolean hasFilter() {
        for (int i = 0; i < NetPipeAdapterBlockEntity.FILTER_SLOTS; i++) if (!filters.getItem(i).isEmpty()) return true;
        return false;
    }

    private void setFilter(int slot, ItemStack stack) {
        if (!stack.isEmpty()) {
            for (int i = 0; i < NetPipeAdapterBlockEntity.FILTER_SLOTS; i++) {
                if (i != slot && ItemStack.isSameItemSameComponents(filters.getItem(i), stack)) return;
            }
        }
        filters.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public int mode() { return data.get(0); }
    public boolean enabled() { return data.get(1) != 0; }
    public int moved() { return data.get(2); }
    public int targets() { return data.get(4); }

    public String problem() {
        int i = data.get(3);
        return i >= 0 && i < NetPipeAdapterBlockEntity.PROBLEMS.length ? NetPipeAdapterBlockEntity.PROBLEMS[i] : "";
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < FIRST_INV) {
            if (slotId >= SHOWN && exporting() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE))
                setFilter(slotId - SHOWN, type == ClickType.QUICK_MOVE ? ItemStack.EMPTY : getCarried());
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= FIRST_INV && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < FIRST_INV || !exporting()) return ItemStack.EMPTY;
        ItemStack st = slots.get(index).getItem();
        if (st.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < NetPipeAdapterBlockEntity.FILTER_SLOTS; i++) if (ItemStack.isSameItemSameComponents(filters.getItem(i), st)) return ItemStack.EMPTY;
        for (int i = 0; i < NetPipeAdapterBlockEntity.FILTER_SLOTS; i++) {
            if (filters.getItem(i).isEmpty()) {
                setFilter(i, st);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof NetPipeAdapterBlockEntity be)) return false;
        switch (id) {
            case BTN_MODE -> be.cycleMode();
            case BTN_ENABLE -> be.setEnabled(!be.enabled());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof NetPipeAdapterBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
