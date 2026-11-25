package com.sqc.nuclearsq.storage.client;

import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.storage.NetPipeAdapterBlockEntity;
import com.sqc.nuclearsq.storage.NetPipeAdapterMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class NetPipeAdapterScreen extends AbstractContainerScreen<NetPipeAdapterMenu> {
    private static final int ROW_Y = 22, BTN_H = 12;
    private static final int[][] BUTTONS = {{8, 76}, {88, 38}};
    private static final String[] MODES = {"demand", "store", "export"};

    public NetPipeAdapterScreen(NetPipeAdapterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = NetPipeAdapterMenu.WIDTH;
        imageHeight = NetPipeAdapterMenu.HEIGHT;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.net_pipe_adapter." + key, args);
    }

    private String modeKey() {
        int m = menu.mode();
        return m >= 0 && m < MODES.length ? MODES[m] : MODES[0];
    }

    private boolean over(int i, double mx, double my) {
        int x = leftPos + BUTTONS[i][0], y = topPos + ROW_Y;
        return mx >= x && mx < x + BUTTONS[i][1] && my >= y && my < y + BTN_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        for (int i = 0; i < BUTTONS.length; i++) {
            if (button == 0 && over(i, mx, my) && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private Component state() {
        if (!menu.enabled()) return t("state.off");
        String p = menu.problem();
        if (p.isEmpty()) return t("state.ok", menu.moved());
        return t("problem." + p);
    }

    private int stateColor() {
        if (!menu.enabled()) return TermKit.MID;
        String p = menu.problem();
        return p.isEmpty() ? TermKit.GREEN : p.equals("no_storage") ? TermKit.RED : p.equals("idle") ? TermKit.MID : TermKit.AMBER;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8, 20);
        TermKit.header(g, font, title, Component.empty(), x + 8, y + 7, w - 16, TermKit.MID);
        Component[] labels = {t("mode." + modeKey()), t(menu.enabled() ? "on" : "off")};
        Component[] tips = {t("mode.tip"), t("enable.tip")};
        for (int i = 0; i < BUTTONS.length; i++) {
            boolean active = i == 0 || menu.enabled();
            TermKit.button(g, font, x + BUTTONS[i][0], y + ROW_Y, BUTTONS[i][1], BTN_H, labels[i], TermKit.CYAN, true, over(i, mx, my), active);
            TermKit.tip(x + BUTTONS[i][0], y + ROW_Y, BUTTONS[i][1], BTN_H, labels[i], tips[i]);
        }
        boolean demand = menu.mode() == NetPipeAdapterBlockEntity.DEMAND;
        Component info = menu.exporting() ? t(menu.hasFilter() ? "info.export" : "info.no_filter")
                : !demand ? t("info.store") : menu.targets() > 0 ? t("info.targets", menu.targets()) : t("info.none");
        TermKit.fit(g, font, info, x + 8, y + NetPipeAdapterMenu.SHOW_Y - 10, w - 16, TermKit.MID);
        if (menu.exporting()) {
            for (int i = NetPipeAdapterMenu.SHOWN; i < NetPipeAdapterMenu.FIRST_INV; i++) {
                Slot s = menu.slots.get(i);
                TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.CYAN);
                TermKit.tip(x + s.x - 1, y + s.y - 1, 18, 18, t("slot.filter"), t("slot.filter.tip"));
            }
        }
        if (demand) {
            for (int i = 0; i < NetPipeAdapterMenu.SHOWN; i++) {
                Slot s = menu.slots.get(i);
                TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.CYAN);
                TermKit.tip(x + s.x - 1, y + s.y - 1, 18, 18, t(i == 0 ? "slot.machine" : "slot.pattern"), t(i == 0 ? "slot.machine.tip" : "slot.pattern.tip"));
            }
            TermKit.center(g, font, Component.literal(">"), x + (NetPipeAdapterMenu.MACHINE_X + NetPipeAdapterMenu.RESULT_X + 16) / 2, y + NetPipeAdapterMenu.SHOW_Y + 4, TermKit.MID);
            TermKit.fit(g, font, t("pattern"), x + NetPipeAdapterMenu.RESULT_X + 24, y + NetPipeAdapterMenu.SHOW_Y + 4, w - NetPipeAdapterMenu.RESULT_X - 32, TermKit.MID);
        }
        TermKit.fit(g, font, state(), x + 8, y + NetPipeAdapterMenu.SHOW_Y + 26, w - 16, stateColor());
        TermKit.rule(g, x + 8, y + NetPipeAdapterMenu.INV_Y - 6, w - 16, TermKit.FAINT);
        for (int i = NetPipeAdapterMenu.FIRST_INV; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, TermKit.FAINT);
        }
        TermKit.scanlines(g, x - 4, y - 4, w + 8, h + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
