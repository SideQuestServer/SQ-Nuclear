package com.sqc.nuclearsq.client;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.client.TermKit;
import com.sqc.nuclearsq.progression.MachineRecipes;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class MachineRecipesScreen extends Screen {
    private static final int BAR = 5, ICON = 18, SIDE = 74;

    private final AbstractContainerScreen<?> parent;
    private final ItemStack icon;
    private final List<MachineRecipes.Row> rows;
    private final int[] top;
    private final int total;
    private final List<net.minecraft.resources.ResourceLocation> ids;
    private final boolean pick, template;
    private int x, y, w, h, lx, ly, lw, lh;
    private double scroll;
    private boolean drag;

    public MachineRecipesScreen(AbstractContainerScreen<?> parent, BigMachine machine, ItemStack icon) {
        super(icon.getHoverName());
        this.parent = parent;
        this.icon = icon;
        Minecraft mc = Minecraft.getInstance();
        rows = mc.level == null ? List.of() : MachineRecipes.rows(machine, mc.level.getRecipeManager(), mc.level.registryAccess());
        top = new int[rows.size()];
        int yy = 0;
        for (int i = 0; i < rows.size(); i++) {
            top[i] = yy;
            yy += rowHeight(rows.get(i));
        }
        total = yy;
        template = parent.getMenu() instanceof com.sqc.nuclearsq.storage.ComponentAssemblerMenu;
        pick = mc.level != null && (template || parent.getMenu() instanceof com.sqc.nuclearsq.machine.big.BigCrafterMenu cm && cm.lockable());
        ids = pick ? MachineRecipes.ids(machine, mc.level.getRecipeManager()) : List.of();
    }

    private int lockOf(MachineRecipes.Row r) {
        return r.id() == null ? 0 : ids.indexOf(r.id()) + 1;
    }

    private int locked() {
        return parent.getMenu() instanceof com.sqc.nuclearsq.machine.big.BigCrafterMenu cm ? cm.lock() : 0;
    }

    private int rowAt(double mx, double my) {
        if (!pick || mx < lx || mx >= lx + lw || my < ly || my >= ly + lh) return -1;
        double yy = my - ly + (int) scroll;
        for (int i = 0; i < rows.size(); i++) if (yy >= top[i] && yy < top[i] + rowHeight(rows.get(i))) return lockOf(rows.get(i)) > 0 ? i : -1;
        return -1;
    }

    private static int rowHeight(MachineRecipes.Row r) {
        return 22 + r.notes().size() * 9;
    }

    private int maxScroll() {
        return Math.max(0, total - lh);
    }

    @Override
    protected void init() {
        w = Math.max(160, Math.min(width - 40, Math.max(parent.getXSize(), 300)));
        h = Math.max(80, Math.min(height - 24, Math.max(parent.getYSize(), 200)));
        x = Mth.clamp(parent.getGuiLeft() + parent.getXSize() / 2 - w / 2, 8, Math.max(8, width - w - 32));
        y = Mth.clamp(parent.getGuiTop() + parent.getYSize() / 2 - h / 2, 16, Math.max(16, height - h - 8));
        lx = x + 8;
        ly = y + 26;
        lw = w - 16 - BAR - 4;
        lh = h - 26 - 6;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void resize(Minecraft mc, int width, int height) {
        parent.resize(mc, width, height);
        super.resize(mc, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        parent.render(g, -1, -1, pt);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, width, height, 0x90000000);
        TermKit.recipes(icon);
        TermKit.monitor(g, x - 4, y - 4, w + 8, h + 8);
        TermKit.header(g, font, title, Component.translatable("gui.nuclearsq.term.recipes.count", rows.size()), x + 8, y + 8, w - 16, TermKit.MID);
        TermKit.rule(g, x + 8, y + 20, w - 16, TermKit.DIM);
        if (rows.isEmpty()) TermKit.center(g, font, Component.translatable("gui.nuclearsq.term.recipes.none"), x + w / 2, ly + lh / 2 - 4, TermKit.MID);

        ItemStack hover = ItemStack.EMPTY;
        float chance = 1f;
        boolean inList = mx >= lx && mx < lx + lw && my >= ly && my < ly + lh;
        long ms = Util.getMillis();
        int off = (int) scroll, room = (lw - SIDE - 14) / ICON;
        int hoverRow = rowAt(mx, my), lock = locked();
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            MachineRecipes.Row r = rows.get(i);
            int ry = ly + top[i] - off, rh = rowHeight(r);
            if (ry + rh <= ly) continue;
            if (ry >= ly + lh) break;
            boolean mine = lock > 0 && lockOf(r) == lock;
            if (i == hoverRow || mine) TermKit.fill(g, lx, ry, lx + lw, ry + rh - 1, TermKit.FAINT);
            if (mine) TermKit.fill(g, lx, ry, lx + 1, ry + rh - 1, TermKit.GREEN);
            int iy = ry + 2, ix = lx + 1, ins = r.needs().size(), outs = r.results().size();
            int shown = ins + outs <= room ? ins : Math.max(0, room - outs - 1);
            for (int k = 0; k < shown; k++) {
                RecipeSources.Need n = r.needs().get(k);
                ItemStack st = n.display(ms).copyWithCount(Math.max(1, n.count()));
                item(g, st, ix, iy);
                if (inList && hit(mx, my, ix, iy)) hover = st;
                ix += ICON;
            }
            if (shown < ins) {
                TermKit.text(g, font, Component.literal("+" + (ins - shown)), ix + 1, iy + 5, TermKit.MID);
                ix += ICON;
            }
            if (ins > 0) {
                arrow(g, ix + 2, iy + 8);
                ix += 14;
            }
            for (ReprocessingRecipe.Result res : r.results()) {
                item(g, res.stack(), ix, iy);
                if (res.chance() < 1f) TermKit.fill(g, ix, iy, ix + 3, iy + 3, TermKit.AMBER);
                if (inList && hit(mx, my, ix, iy)) {
                    hover = res.stack();
                    chance = res.chance();
                }
                ix += ICON;
            }
            if (r.time() > 0)
                TermKit.right(g, font, Component.translatable("jei.nuclearsq.time", String.format("%.1f", r.time() / 20.0)), lx + lw - 2, iy, TermKit.MID);
            if (r.energyPerTick() > 0)
                TermKit.right(g, font, Component.translatable("jei.nuclearsq.energy", r.energyPerTick()), lx + lw - 2, iy + 9, TermKit.DIM);
            for (int k = 0; k < r.notes().size(); k++) {
                int ny = ry + 21 + k * 9;
                if (ny >= ly && ny + 8 <= ly + lh) TermKit.fit(g, font, r.notes().get(k), lx + 1, ny, lw - 2, TermKit.MID);
            }
            TermKit.rule(g, lx, ry + rh - 1, lw, TermKit.FAINT);
        }
        g.disableScissor();

        if (total > lh) {
            int bx = x + w - 8 - BAR, th = thumb(), ty = ly + (int) ((lh - th) * (scroll / Math.max(1, maxScroll())));
            TermKit.fill(g, bx, ly, bx + BAR, ly + lh, TermKit.FAINT);
            TermKit.fill(g, bx, ty, bx + BAR, ty + th, TermKit.MID);
        }

        if (hoverRow >= 0 && hover.isEmpty()) {
            boolean mine = lock > 0 && lockOf(rows.get(hoverRow)) == lock;
            TermKit.tip(lx, ly + top[hoverRow] - off, lw, rowHeight(rows.get(hoverRow)), Component.translatable("gui.nuclearsq.term.recipes.pick"),
                    Component.translatable(template ? "gui.nuclearsq.term.recipes.pick.template" : mine ? "gui.nuclearsq.term.recipes.pick.clear" : "gui.nuclearsq.term.recipes.pick.tip"));
        }
        if (!TermKit.renderTip(g, font, mx, my) && !hover.isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hover));
            if (chance < 1f) lines.add(Component.translatable("jei.nuclearsq.chance", Math.round(chance * 1000) / 10.0).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
        }
        g.pose().popPose();
    }

    private void item(GuiGraphics g, ItemStack st, int ix, int iy) {
        g.renderItem(st, ix + 1, iy + 1);
        g.renderItemDecorations(font, st, ix + 1, iy + 1);
    }

    private static boolean hit(int mx, int my, int ix, int iy) {
        return mx >= ix && mx < ix + ICON && my >= iy && my < iy + ICON;
    }

    private static void arrow(GuiGraphics g, int ax, int ay) {
        TermKit.fill(g, ax, ay, ax + 8, ay + 1, TermKit.MID);
        for (int i = 0; i < 4; i++) TermKit.fill(g, ax + 7 - i, ay - i, ax + 8 - i, ay + 1 + i, TermKit.MID);
    }

    private int thumb() {
        return Math.max(12, lh * lh / Math.max(1, total));
    }

    private void dragTo(double my) {
        int th = thumb();
        scroll = Mth.clamp((my - ly - th / 2.0) / Math.max(1, lh - th) * maxScroll(), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - sy * 22, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        if (button == 0 && total > lh && mx >= x + w - 8 - BAR && mx < x + w - 8 && my >= ly && my < ly + lh) {
            drag = true;
            dragTo(my);
            return true;
        }
        int row = button == 0 ? rowAt(mx, my) : -1;
        if (row >= 0 && template) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.sqc.nuclearsq.storage.CraftingPayloads.Fill(
                    parent.getMenu().containerId, rows.get(row).id()));
            onClose();
            return true;
        }
        if (row >= 0) {
            int want = lockOf(rows.get(row));
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.sqc.nuclearsq.network.DeviceControlPayload(
                    com.sqc.nuclearsq.network.DeviceControlPayload.RECIPE_LOCK, want == locked() ? 0 : want));
            onClose();
            return true;
        }
        if (mx < x - 4 || mx >= x + w + 4 || my < y - 4 || my >= y + h + 4) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == parent.getMenu()) minecraft.setScreen(parent);
        else super.onClose();
    }
}
