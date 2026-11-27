package com.sqc.nuclearsq.progression.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sqc.nuclearsq.progression.CompendiumSearch;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.progression.ResourceCalc;
import com.sqc.nuclearsq.progression.ResourceCalc.Res;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class CalcPanel {
    private static final int EDGE = 0xFF38383C, ACCENT = 0xFFE8A33D, CYAN = 0xFFB4C2FF, TEXT = 0xFFDDDDE0, MUTED = 0xFF8E8E96;
    private static final int PANEL_BG = 0xFF161618, CARD = 0xFF2A2A2D, HOVER_BG = 0xFF28282B, ACTIVE_BG = 0xFF30323C;
    private static final int GREEN = 0xFF46C46A, RED = 0xFFFF6060, BOX_BG = 0xFF1A1B20;
    private static final int CELL = 22, FIELD_H = 13, BOX_H = 66, ROW = 18, ENTRY = 17;

    private enum Act { NONE, SELECT, FILTER, MINUS, PLUS, PLUS8, PLUS64, ADD, CLEAR, LINE_MINUS, LINE_PLUS, LINE_DEL, LINE_RECIPE, RECIPE, PICK, SEARCH, AMOUNT }

    private record Hit(int x0, int y0, int x1, int y1, Act act, int a, Object o) {}

    private final List<Map.Entry<RecipeSources.Where, List<ItemStack>>> groups;
    private final List<Hit> hits = new ArrayList<>();
    private ResourceCalc calc;
    private ResourceCalc.Result result;
    private boolean dirty = true;
    private String search = "", amount = "1";
    private boolean searchFocus, amountFocus;
    private int filter, scrollL, scrollR, totalL, totalR, viewL, viewR;
    private int gridX, gridY, gridW, gridH, rightX, rightY, rightW, rightH;
    private ItemStack selected = ItemStack.EMPTY;
    private List<Component> tip;

    CalcPanel(List<Map.Entry<RecipeSources.Where, List<ItemStack>>> groups) {
        this.groups = groups;
        CalcStore.load();
    }

    private static String tr(String key) { return Language.getInstance().getOrDefault(key); }

    private static void rrect(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0 + 1, y0, x1 - 1, y1, c);
        g.fill(x0, y0 + 1, x1, y1 - 1, c);
    }

    private static boolean in(int mx, int my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    List<FormattedCharSequence> tooltip(Font font) {
        if (tip == null) return null;
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : tip) out.addAll(font.split(c, 220));
        return out;
    }

    private ResourceCalc.Result result() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (calc == null) calc = ResourceCalc.of(level.getRecipeManager(), level.registryAccess());
        if (dirty || result == null) {
            result = calc.compute(CalcStore.LINES, CalcStore.CHOICE, CalcStore.PICKS);
            dirty = false;
        }
        return result;
    }

    private static int have(Item it) {
        var pl = Minecraft.getInstance().player;
        return pl == null ? 0 : pl.getInventory().countItem(it);
    }

    private static boolean machine(ItemStack st) {
        String p = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return p.endsWith("_controller_sqc") || p.endsWith("_kit_sqc");
    }

    private boolean shows(ItemStack st) {
        if (filter == 1 && !machine(st)) return false;
        if (filter == 2 && machine(st)) return false;
        if (search.isBlank()) return true;
        String q = CompendiumSearch.fold(search.trim());
        for (String t : q.split("\\s+")) {
            if (t.isEmpty()) continue;
            if (!CompendiumSearch.fold(st.getHoverName().getString()).contains(t)
                    && !CompendiumSearch.fold(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()).contains(t)) return false;
        }
        return true;
    }

    private long amount() {
        try {
            return Math.max(1, Math.min(CalcStore.MAX_COUNT, Long.parseLong(amount.isEmpty() ? "1" : amount)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Component whereName(ResourceCalc.Src s) {
        if (s.where() == null || s.where() == RecipeSources.Where.OTHER) return s.machine().isEmpty() ? Component.literal(s.type().toString()) : s.machine().getHoverName();
        return Component.translatable(s.where().langKey());
    }

    private static String num(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', '.');
    }

    private static String amountText(Res r, long n) {
        return r.isFluid() ? num(n) + " mB" : num(n) + "x";
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        tip = null;
        var res = result();
        g.drawString(font, font.plainSubstrByWidth(tr("compendium.calc.hint"), w), x, y, MUTED, false);
        y += 14;
        h -= 14;
        int lw = Math.min(206, Math.max(150, w * 2 / 5));
        drawLeft(g, font, x, y, lw, h, mx, my, res);
        int rx = x + lw + 12;
        g.fill(rx - 7, y, rx - 6, y + h, EDGE);
        drawRight(g, font, rx, y, x + w - rx, h, mx, my, res);
    }

    private int button(GuiGraphics g, Font font, int x, int y, int w, String label, boolean on, int mx, int my, Act act, int a, int col) {
        boolean over = in(mx, my, x, y, x + w, y + FIELD_H);
        rrect(g, x, y, x + w, y + FIELD_H, on ? ACCENT : over ? ACTIVE_BG : CARD);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, on ? 0xFF000000 : col, false);
        hits.add(new Hit(x, y, x + w, y + FIELD_H, act, a, null));
        return x + w;
    }

    private void field(GuiGraphics g, Font font, int x, int y, int w, String value, String hint, boolean focus, boolean right, Act act) {
        g.fill(x, y, x + w, y + FIELD_H, focus ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD_H - 1, 0xFF000000);
        String s = value.isEmpty() && !focus ? hint : value + (focus && (System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
        int col = value.isEmpty() && !focus ? 0xFF707070 : 0xFFFFFFFF;
        String shown = font.plainSubstrByWidth(s, w - 8);
        g.drawString(font, shown, right ? x + w - 4 - font.width(shown) : x + 4, y + 3, col, false);
        hits.add(new Hit(x, y, x + w, y + FIELD_H, act, 0, null));
    }

    private void drawLeft(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, ResourceCalc.Result res) {
        field(g, font, x, y, w, search, tr("compendium.calc.search"), searchFocus, false, Act.SEARCH);
        int fy = y + FIELD_H + 4, fx = x;
        String[] names = {"compendium.calc.filter.all", "compendium.calc.filter.machines", "compendium.calc.filter.parts"};
        for (int i = 0; i < names.length; i++) {
            String s = tr(names[i]);
            fx = button(g, font, fx, fy, font.width(s) + 8, s, filter == i, mx, my, Act.FILTER, i, TEXT) + 3;
        }
        int top = fy + FIELD_H + 5;
        int machinesN = res == null ? 0 : res.machines().size();
        int machH = machinesN == 0 ? 0 : 12 + Math.min(machinesN, 8) * 11 + 4;
        int gh = Math.max(CELL * 2 + 14, h - (top - y) - 6 - BOX_H - 6 - machH);
        gridX = x;
        gridY = top;
        gridW = w;
        gridH = gh;
        drawGrid(g, font, x, top, w, gh, mx, my);
        int by = top + gh + 6;
        drawBox(g, font, x, by, w, mx, my);
        if (machinesN > 0) drawMachines(g, font, x, by + BOX_H + 6, w, y + h - (by + BOX_H + 6), mx, my, res);
    }

    private void drawGrid(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        int cols = Math.max(1, (w - 8) / CELL);
        viewL = h;
        scrollL = Math.max(0, Math.min(scrollL, Math.max(0, totalL - h)));
        g.enableScissor(x, y, x + w, y + h);
        int cy = y + 3 - scrollL;
        boolean any = false;
        for (var grp : groups) {
            List<ItemStack> list = grp.getValue().stream().filter(this::shows).toList();
            if (list.isEmpty()) continue;
            any = true;
            ItemStack icon = grp.getKey() == null ? new ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK) : grp.getKey().iconStack();
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + 3, cy, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                MachineThumbs.drawStack(g, icon, 0, 0);
                g.pose().popPose();
            }
            g.drawString(font, font.plainSubstrByWidth(grp.getKey() == null ? "Minecraft" : tr(grp.getKey().langKey()), w - 24), x + 18, cy + 2, 0xFFFFFFFF, false);
            cy += 14;
            for (int k = 0; k < list.size(); k++) {
                int cx = x + 3 + (k % cols) * CELL, ry = cy + (k / cols) * CELL;
                ItemStack st = list.get(k);
                boolean sel = !selected.isEmpty() && st.getItem() == selected.getItem();
                boolean over = in(mx, my, cx, Math.max(ry, y), cx + CELL - 2, Math.min(ry + CELL - 2, y + h));
                if (ry + CELL > y && ry < y + h) {
                    rrect(g, cx, ry, cx + CELL - 2, ry + CELL - 2, sel ? ACCENT : over ? TEXT : EDGE);
                    rrect(g, cx + 1, ry + 1, cx + CELL - 3, ry + CELL - 3, sel ? ACTIVE_BG : over ? HOVER_BG : CARD);
                    MachineThumbs.drawStack(g, st, cx + 2, ry + 2);
                }
                if (over) {
                    hits.add(new Hit(cx, Math.max(ry, y), cx + CELL - 2, Math.min(ry + CELL - 2, y + h), Act.SELECT, 0, st));
                    tip = List.of(st.getHoverName().copy().withStyle(ChatFormatting.YELLOW), Component.literal(tr("compendium.calc.tip.add")).withStyle(ChatFormatting.GRAY));
                }
            }
            cy += ((list.size() + cols - 1) / cols) * CELL + 4;
        }
        if (!any) g.drawString(font, tr("compendium.search.none"), x + 4, cy + 2, MUTED, false);
        totalL = cy + scrollL - y;
        g.disableScissor();
        if (totalL > h) {
            int th = Math.max(14, h * h / totalL), ty = y + (h - th) * scrollL / Math.max(1, totalL - h);
            g.fill(x + w - 3, y, x + w, y + h, 0xFF1E222B);
            g.fill(x + w - 3, ty, x + w, ty + th, 0xFF5A6478);
        }
    }

    private void drawBox(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        rrect(g, x, y, x + w, y + BOX_H, selected.isEmpty() ? EDGE : ACCENT);
        rrect(g, x + 1, y + 1, x + w - 1, y + BOX_H - 1, BOX_BG);
        if (selected.isEmpty()) {
            List<FormattedCharSequence> l = font.split(Component.literal(tr("compendium.calc.pick")), w - 12);
            for (int i = 0; i < l.size() && i < 2; i++) g.drawString(font, l.get(i), x + 6, y + 8 + i * 10, MUTED, false);
        } else {
            MachineThumbs.drawStack(g, selected, x + 6, y + 6);
            g.drawString(font, font.plainSubstrByWidth(selected.getHoverName().getString(), w - 32), x + 26, y + 6, 0xFFFFFF55, false);
            var c = calc == null ? List.<ResourceCalc.Src>of() : calc.candidates(Res.of(selected.getItem()), true);
            if (!c.isEmpty()) g.drawString(font, font.plainSubstrByWidth(whereName(c.get(0)).getString(), w - 32), x + 26, y + 15, CYAN, false);
        }
        int ay = y + 28;
        String lab = tr("compendium.calc.amount");
        g.drawString(font, lab, x + 6, ay + 3, MUTED, false);
        int bx = x + 6 + font.width(lab) + 6;
        bx = button(g, font, bx, ay, 13, "-", false, mx, my, Act.MINUS, 0, TEXT) + 2;
        field(g, font, bx, ay, 40, amount, "1", amountFocus, true, Act.AMOUNT);
        bx += 42;
        bx = button(g, font, bx, ay, 13, "+", false, mx, my, Act.PLUS, 0, TEXT) + 4;
        bx = button(g, font, bx, ay, font.width("+8") + 8, "+8", false, mx, my, Act.PLUS8, 0, TEXT) + 3;
        if (bx + font.width("+64") + 8 <= x + w - 4) button(g, font, bx, ay, font.width("+64") + 8, "+64", false, mx, my, Act.PLUS64, 0, TEXT);
        int ab = y + 46;
        boolean ok = !selected.isEmpty() && CalcStore.LINES.size() < CalcStore.MAX_LINES;
        boolean over = ok && in(mx, my, x + 6, ab, x + w - 6, ab + 15);
        rrect(g, x + 6, ab, x + w - 6, ab + 15, ok ? (over ? 0xFFF5B85A : ACCENT) : CARD);
        String add = CalcStore.LINES.size() >= CalcStore.MAX_LINES ? tr("compendium.calc.full").formatted(CalcStore.MAX_LINES) : tr("compendium.calc.add");
        g.drawString(font, add, x + (w - font.width(add)) / 2, ab + 4, ok ? 0xFF000000 : MUTED, false);
        if (ok) hits.add(new Hit(x + 6, ab, x + w - 6, ab + 15, Act.ADD, 0, null));
    }

    private void drawMachines(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, ResourceCalc.Result res) {
        g.drawString(font, tr("compendium.calc.machines").formatted(res.machines().size()), x, y, ACCENT, false);
        int ry = y + 12, n = 0, fit = Math.max(1, (h - 12) / 11);
        for (var e : res.machines().entrySet()) {
            if (n == fit - 1 && res.machines().size() > fit) {
                g.drawString(font, tr("compendium.calc.more").formatted(res.machines().size() - n), x + 16, ry + 1, MUTED, false);
                break;
            }
            ItemStack st = new ItemStack(e.getKey());
            g.pose().pushPose();
            g.pose().translate(x + 1, ry - 1, 0);
            g.pose().scale(0.6875f, 0.6875f, 1f);
            MachineThumbs.drawStack(g, st, 0, 0);
            g.pose().popPose();
            String runs = e.getValue() == 1 ? tr("compendium.calc.run") : tr("compendium.calc.runs").formatted(num(e.getValue()));
            g.drawString(font, font.plainSubstrByWidth(st.getHoverName().getString(), w - 20 - font.width(runs) - 4), x + 16, ry + 1, TEXT, false);
            g.drawString(font, runs, x + w - font.width(runs), ry + 1, MUTED, false);
            if (in(mx, my, x, ry, x + w, ry + 11)) tip = List.of(st.getHoverName().copy().withStyle(ChatFormatting.YELLOW), Component.literal(runs).withStyle(ChatFormatting.GRAY));
            ry += 11;
            n++;
        }
    }

    private void drawRight(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, ResourceCalc.Result res) {
        rightX = x;
        rightY = y;
        rightW = w;
        rightH = h;
        viewR = h - 15;
        scrollR = Math.max(0, Math.min(scrollR, Math.max(0, totalR - viewR)));
        String head = tr("compendium.calc.list").formatted(CalcStore.LINES.size());
        g.drawString(font, head, x, y + 2, ACCENT, false);
        String clr = tr("compendium.calc.clear");
        if (!CalcStore.LINES.isEmpty()) button(g, font, x + w - font.width(clr) - 10, y - 1, font.width(clr) + 10, clr, false, mx, my, Act.CLEAR, 0, TEXT);
        int top = y + 15, bottom = y + h;
        g.enableScissor(x, top, x + w, bottom);
        int cy = top - scrollR;
        int sw = w - 4;
        if (CalcStore.LINES.isEmpty()) {
            for (FormattedCharSequence l : font.split(Component.literal(tr("compendium.calc.empty")), sw)) {
                g.drawString(font, l, x, cy + 2, MUTED, false);
                cy += 10;
            }
        }
        for (int i = 0; i < CalcStore.LINES.size(); i++) {
            var line = CalcStore.LINES.get(i);
            boolean vis = cy + ROW > top && cy < bottom;
            if (i % 2 == 0) g.fill(x, cy - 1, x + sw, cy + ROW - 1, 0x18FFFFFF);
            ItemStack st = new ItemStack(line.item());
            Res r = Res.of(line.item());
            int opts = res == null ? 0 : res.options().getOrDefault(r, 0);
            int bx = x + sw - 44;
            String rec = null;
            if (opts > 1) {
                rec = tr("compendium.calc.recipe").formatted(Math.floorMod(CalcStore.CHOICE.getOrDefault(r, 0), opts) + 1, opts);
                bx -= font.width(rec) + 6;
            }
            if (vis) {
                MachineThumbs.drawStack(g, st, x + 2, cy);
                g.drawString(font, font.plainSubstrByWidth(num(line.count()) + "x " + st.getHoverName().getString(), bx - x - 26), x + 22, cy + 4, TEXT, false);
                int b = x + sw - 44;
                if (rec != null) {
                    int rw = font.width(rec) + 4;
                    rrect(g, b - rw - 2, cy + 2, b - 2, cy + 15, ACTIVE_BG);
                    g.drawString(font, rec, b - rw, cy + 5, ACCENT, false);
                    addClipped(b - rw - 2, cy + 2, b - 2, cy + 15, top, bottom, Act.LINE_RECIPE, i, r);
                }
                smallButton(g, font, b, cy + 2, "-", TEXT, mx, my, top, bottom, Act.LINE_MINUS, i);
                smallButton(g, font, b + 14, cy + 2, "+", TEXT, mx, my, top, bottom, Act.LINE_PLUS, i);
                smallButton(g, font, b + 30, cy + 2, "x", RED, mx, my, top, bottom, Act.LINE_DEL, i);
            }
            cy += ROW;
        }
        if (res != null && !CalcStore.LINES.isEmpty()) {
            cy += 4;
            g.fill(x, cy, x + sw, cy + 1, EDGE);
            cy += 6;
            List<Res> raw = res.rawItems();
            g.drawString(font, tr("compendium.calc.raw"), x, cy, ACCENT, false);
            int miss = 0;
            for (Res r : raw) if (have(r.item()) < res.demand().getOrDefault(r, 0L)) miss++;
            if (miss > 0) {
                String m = tr("compendium.calc.missing").formatted(miss);
                g.drawString(font, m, x + sw - font.width(m), cy, RED, false);
            }
            cy += 13;
            cy = entries(g, font, x, cy, sw, raw, res, mx, my, top, bottom, true);
            List<Res> fl = res.rawFluids();
            if (!fl.isEmpty()) {
                cy += 3;
                g.drawString(font, tr("compendium.calc.fluids"), x, cy, ACCENT, false);
                cy += 13;
                cy = entries(g, font, x, cy, sw, fl, res, mx, my, top, bottom, true);
            }
            List<Res> mid = res.intermediates();
            if (!mid.isEmpty()) {
                cy += 3;
                g.drawString(font, tr("compendium.calc.mid").formatted(mid.size()), x, cy, ACCENT, false);
                cy += 13;
                cy = entries(g, font, x, cy, sw, mid, res, mx, my, top, bottom, false);
            }
        }
        totalR = cy + scrollR - top + 4;
        g.disableScissor();
        int vh = bottom - top;
        if (totalR > vh) {
            int th = Math.max(14, vh * vh / totalR), ty = top + (vh - th) * scrollR / Math.max(1, totalR - vh);
            g.fill(x + w - 2, top, x + w, bottom, 0xFF1E222B);
            g.fill(x + w - 2, ty, x + w, ty + th, 0xFF5A6478);
        }
    }

    private void smallButton(GuiGraphics g, Font font, int x, int y, String s, int col, int mx, int my, int top, int bottom, Act act, int a) {
        boolean over = in(mx, my, x, Math.max(y, top), x + 12, Math.min(y + 12, bottom));
        rrect(g, x, y, x + 12, y + 12, over ? ACTIVE_BG : CARD);
        g.drawString(font, s, x + (12 - font.width(s)) / 2 + 1, y + 2, col, false);
        addClipped(x, y, x + 12, y + 12, top, bottom, act, a, null);
    }

    private void addClipped(int x0, int y0, int x1, int y1, int top, int bottom, Act act, int a, Object o) {
        int a0 = Math.max(y0, top), a1 = Math.min(y1, bottom);
        if (a1 > a0) hits.add(new Hit(x0, a0, x1, a1, act, a, o));
    }

    private int entries(GuiGraphics g, Font font, int x, int cy, int w, List<Res> list, ResourceCalc.Result res, int mx, int my, int top, int bottom, boolean raw) {
        int colw = w / 2;
        for (int i = 0; i < list.size(); i++) {
            Res r = list.get(i);
            int ex = x + (i % 2) * colw, ey = cy + (i / 2) * ENTRY;
            long n = res.demand().getOrDefault(r, 0L);
            int opts = res.options().getOrDefault(r, 0);
            Item first = res.pickOf().get(r);
            int pickN = first == null ? 0 : pickOptions(res, r);
            boolean canRecipe = !raw && opts > 1;
            boolean canPick = pickN > 1;
            long left = res.left().getOrDefault(r, 0L);
            String extra = canRecipe ? (Math.floorMod(CalcStore.CHOICE.getOrDefault(r, 0), opts) + 1) + "/" + opts : canPick ? "⇄" : left > 0 ? "+" + num(left) : "";
            boolean over = in(mx, my, ex, Math.max(ey, top), ex + colw - 2, Math.min(ey + ENTRY - 1, bottom));
            if (ey + ENTRY > top && ey < bottom) {
                if (over && (canRecipe || canPick)) g.fill(ex, ey - 1, ex + colw - 2, ey + ENTRY - 1, HOVER_BG);
                MachineThumbs.drawStack(g, r.icon(), ex, ey);
                int col = r.isFluid() ? CYAN : !raw ? TEXT : have(r.item()) >= n ? GREEN : RED;
                int ew = extra.isEmpty() ? 0 : font.width(extra) + 4;
                g.drawString(font, font.plainSubstrByWidth(amountText(r, n) + " " + r.name().getString(), colw - 22 - ew), ex + 19, ey + 4, col, false);
                if (!extra.isEmpty()) g.drawString(font, extra, ex + colw - 4 - font.width(extra), ey + 4, canRecipe || canPick ? ACCENT : MUTED, false);
            }
            if (over) {
                List<Component> t = new ArrayList<>();
                t.add(r.name().copy().withStyle(ChatFormatting.YELLOW));
                t.add(Component.literal(amountText(r, n)).withStyle(ChatFormatting.GRAY));
                var src = res.chosen().get(r);
                if (src != null) t.add(Component.translatable("compendium.made_in").append(" ").append(whereName(src)).withStyle(ChatFormatting.AQUA));
                if (left > 0) t.add(Component.literal(tr("compendium.calc.left").formatted(amountText(r, left))).withStyle(ChatFormatting.GRAY));
                if (canRecipe) t.add(Component.literal(tr("compendium.calc.tip.recipe").formatted(Math.floorMod(CalcStore.CHOICE.getOrDefault(r, 0), opts) + 1, opts)).withStyle(ChatFormatting.GOLD));
                if (canPick) t.add(Component.literal(tr("compendium.calc.tip.pick").formatted(Math.floorMod(CalcStore.PICKS.getOrDefault(first, 0), pickN) + 1, pickN)).withStyle(ChatFormatting.GOLD));
                tip = t;
                if (canRecipe) addClipped(ex, ey - 1, ex + colw - 2, ey + ENTRY - 1, top, bottom, Act.RECIPE, opts, r);
                else if (canPick) addClipped(ex, ey - 1, ex + colw - 2, ey + ENTRY - 1, top, bottom, Act.PICK, pickN, first);
            }
        }
        return cy + ((list.size() + 1) / 2) * ENTRY;
    }

    private int pickOptions(ResourceCalc.Result res, Res r) {
        Item first = res.pickOf().get(r);
        if (first == null) return 0;
        for (var s : res.chosen().values())
            for (var n : s.needs()) {
                ItemStack[] items = n.ingredient().getItems();
                if (items.length > 1 && items[0].getItem() == first) return items.length;
            }
        return 0;
    }

    boolean mouseClicked(double mx, double my, int button) {
        boolean wasAmount = amountFocus;
        searchFocus = false;
        amountFocus = false;
        if (button != 0) return false;
        for (Hit h : hits) {
            if (!(mx >= h.x0() && mx < h.x1() && my >= h.y0() && my < h.y1())) continue;
            switch (h.act()) {
                case SEARCH -> searchFocus = true;
                case AMOUNT -> {
                    amountFocus = true;
                    if (!wasAmount) amount = "";
                }
                case SELECT -> {
                    selected = ((ItemStack) h.o()).copyWithCount(1);
                    if (Screen.hasShiftDown()) add();
                }
                case FILTER -> {
                    filter = h.a();
                    scrollL = 0;
                }
                case MINUS -> amount = String.valueOf(Math.max(1, amount() - 1));
                case PLUS -> amount = String.valueOf(Math.min(CalcStore.MAX_COUNT, amount() + 1));
                case PLUS8 -> amount = String.valueOf(Math.min(CalcStore.MAX_COUNT, (amount.isEmpty() ? 0 : amount()) + 8));
                case PLUS64 -> amount = String.valueOf(Math.min(CalcStore.MAX_COUNT, (amount.isEmpty() ? 0 : amount()) + 64));
                case ADD -> add();
                case CLEAR -> {
                    CalcStore.clear();
                    dirty = true;
                }
                case LINE_MINUS -> { CalcStore.change(h.a(), Screen.hasShiftDown() ? -10 : -1); dirty = true; }
                case LINE_PLUS -> { CalcStore.change(h.a(), Screen.hasShiftDown() ? 10 : 1); dirty = true; }
                case LINE_DEL -> { CalcStore.remove(h.a()); dirty = true; }
                case LINE_RECIPE, RECIPE -> {
                    CalcStore.CHOICE.merge((Res) h.o(), Screen.hasShiftDown() ? -1 : 1, Integer::sum);
                    CalcStore.save();
                    dirty = true;
                }
                case PICK -> {
                    CalcStore.PICKS.merge((Item) h.o(), Screen.hasShiftDown() ? -1 : 1, Integer::sum);
                    CalcStore.save();
                    dirty = true;
                }
                default -> {}
            }
            if (wasAmount && !amountFocus && amount.isEmpty()) amount = "1";
            return true;
        }
        if (wasAmount && amount.isEmpty()) amount = "1";
        return false;
    }

    private void add() {
        if (selected.isEmpty()) return;
        CalcStore.add(selected.getItem(), amount());
        dirty = true;
    }

    boolean focused() { return searchFocus || amountFocus; }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (in((int) mx, (int) my, gridX, gridY, gridX + gridW, gridY + gridH)) {
            scrollL = Math.max(0, Math.min(Math.max(0, totalL - viewL), scrollL - (int) (sy * 14)));
            return true;
        }
        if (in((int) mx, (int) my, rightX, rightY, rightX + rightW, rightY + rightH)) {
            scrollR = Math.max(0, Math.min(Math.max(0, totalR - viewR), scrollR - (int) (sy * 14)));
            return true;
        }
        return false;
    }

    boolean keyPressed(int key) {
        if (!focused()) return false;
        if (key == 256) {
            searchFocus = false;
            if (amountFocus && amount.isEmpty()) amount = "1";
            amountFocus = false;
            return true;
        }
        if (key == 259) {
            if (searchFocus && !search.isEmpty()) { search = search.substring(0, search.length() - 1); scrollL = 0; }
            if (amountFocus && !amount.isEmpty()) amount = amount.substring(0, amount.length() - 1);
            return true;
        }
        if (key == 257 || key == 335) {
            if (amountFocus) {
                if (amount.isEmpty()) amount = "1";
                add();
            }
            searchFocus = false;
            amountFocus = false;
            return true;
        }
        return true;
    }

    boolean charTyped(char c) {
        if (searchFocus) {
            if (c >= ' ' && search.length() < 40) {
                search += c;
                scrollL = 0;
            }
            return true;
        }
        if (amountFocus) {
            if (c >= '0' && c <= '9' && amount.length() < 6) amount = (amount.equals("0") ? "" : amount) + c;
            return true;
        }
        return false;
    }
}
