package com.sqc.nuclearsq.door.client;

import com.sqc.nuclearsq.door.DoorBlockEntity;
import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.client.AccessScreen;
import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.network.DeviceControlPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class DoorScreen extends AbstractContainerScreen<DeviceMenu> {
    private static final int W = 220, H = 120, BTN_X = 10, BTN_Y = 82, BTN_W = 96, BTN_H = 14;
    private static final String[] KINDS = {"kind.hangar", "kind.airlock", "kind.vault", "kind.sectional", "kind.grille", "kind.folding",
            "kind.lab", "kind.glass", "kind.shield", "kind.cold", "kind.cage", "kind.slide", "kind.industrial", "kind.perforated", "kind.speed", "kind.lift"};

    public DoorScreen(DeviceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelY = -100;
        this.inventoryLabelY = -100;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.door." + key, args);
    }

    private boolean flag(int f) {
        return (menu.u(0) & f) != 0;
    }

    private boolean overButton(double mx, double my) {
        return mx >= leftPos + BTN_X && mx < leftPos + BTN_X + BTN_W && my >= topPos + BTN_Y && my < topPos + BTN_Y + BTN_H;
    }

    private boolean overAccess(double mx, double my) {
        return mx >= leftPos + W - 52 && mx < leftPos + W - 8 && my >= topPos + BTN_Y + 1 && my < topPos + BTN_Y + 12;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0 && overAccess(mx, my)) {
            AccessScreen.request(menu.pos);
            return true;
        }
        if (button == 0 && overButton(mx, my)) {
            PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.DOOR_OPEN, flag(DoorBlockEntity.FLAG_OPEN) ? 0 : 1));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        boolean open = flag(DoorBlockEntity.FLAG_OPEN), moving = flag(DoorBlockEntity.FLAG_MOVING), found = flag(DoorBlockEntity.FLAG_FOUND);
        TermKit.monitor(g, x - 4, y - 4, W + 8, H + 8);
        TermKit.text(g, font, title, x + 8, y + 8, TermKit.GREEN);
        TermKit.rule(g, x + 8, y + 20, W - 16, TermKit.DIM);
        TermKit.text(g, font, t(moving ? open ? "opening" : "closing" : open ? "open" : "closed"), x + 10, y + 28,
                moving ? TermKit.AMBER : open ? TermKit.CYAN : TermKit.GREEN);
        TermKit.text(g, font, t(found ? KINDS[Math.min(KINDS.length - 1, Math.max(0, menu.u(5) - 1))] : flag(DoorBlockEntity.FLAG_RAGGED) ? "ragged" : "none"), x + 10, y + 42, found ? TermKit.MID : TermKit.RED);
        if (found) TermKit.text(g, font, t("size", menu.u(1), menu.u(2), menu.u(3)), x + 10, y + 54, TermKit.MID);
        TermKit.bar(g, x + 10, y + 68, W - 20, 6, menu.u(4) / 100f, moving ? TermKit.AMBER : TermKit.CYAN);
        TermKit.button(g, font, x + BTN_X, y + BTN_Y, BTN_W, BTN_H, t(open ? "close" : "do_open"), open ? TermKit.AMBER : TermKit.GREEN,
                true, overButton(mx, my), false);
        TermKit.button(g, font, x + W - 52, y + BTN_Y + 1, 44, 11, Component.translatable("gui.nuclearsq.access.button"), TermKit.CYAN, true, overAccess(mx, my), false);
        boolean locked = flag(DoorBlockEntity.FLAG_LOCKED);
        TermKit.prompt(g, font, x + 8, y + 104, t(locked ? "locked" : "hint"), locked ? TermKit.AMBER : TermKit.DIM);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, H + 8);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (!TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
