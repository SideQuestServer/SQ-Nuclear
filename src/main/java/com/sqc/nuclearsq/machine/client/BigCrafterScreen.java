package com.sqc.nuclearsq.machine.client;

import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigCrafterMenu;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.network.DeviceControlPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class BigCrafterScreen extends AbstractContainerScreen<BigCrafterMenu> {
    private static final int W = BigCrafterMenu.W;
    private static final int GREEN = TermKit.GREEN, MID = TermKit.MID, DIM = TermKit.DIM, AMBER = TermKit.AMBER, RED = TermKit.RED;
    private static final String[] STATES = {"ok", "incomplete", "noinput", "needmore", "outfull", "nopower", "off", "nowater", "nofeed", "shallow", "noacid", "noaux", "noarm"};
    private static final int SEG_H = 14;

    private int segW() { return asm() ? 62 : 100; }

    public BigCrafterScreen(BigCrafterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = menu.inventoryY() + 84;
    }

    private boolean asm() {
        return BigCrafterMenu.isAssembler(menu.machine);
    }

    private boolean small() {
        return menu.machine.smallGrid();
    }

    private boolean rack() {
        return menu.machine == BigMachine.SPENT_FUEL_RACK;
    }

    private boolean gen() {
        return menu.machine == BigMachine.COAL_GENERATOR;
    }

    private boolean particle() {
        return menu.machine == BigMachine.PARTICLE_ASSEMBLER;
    }

    private static final int PART_LINES_Y = 84, PART_PX = 116, PART_PW = 36;

    private static final java.util.Set<String> GEN_TIPS = java.util.Set.of("ok", "noinput", "outfull");

    private String stateLabel(String st) {
        return gen() && st.equals("outfull") ? "state.gen.outfull" : "state." + st;
    }

    private String stateTip(String st) {
        return gen() && GEN_TIPS.contains(st) ? "tip.state.gen." + st : "tip.state." + st;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.nuclearsq.big_crafter." + key, args);
    }

    private int segX() { return asm() || particle() ? 120 : 8; }
    private int segY() { return asm() ? 102 : particle() ? 114 : rack() ? 54 : 84; }

    private int clearX() { return asm() ? 112 : 120; }
    private int clearY() { return asm() ? 89 : 36; }
    private static final int CLEAR_W = 74, CLEAR_H = 11, FILL_X = 196, FILL_W = 36;

    private int ghostFor = -1;
    @javax.annotation.Nullable
    private net.minecraft.world.item.crafting.Recipe<?> ghost;

    @javax.annotation.Nullable
    private net.minecraft.world.item.crafting.Recipe<?> ghost() {
        int lock = menu.lockable() ? menu.lock() : 0;
        if (lock != ghostFor && minecraft != null && minecraft.level != null) {
            ghostFor = lock;
            var ids = com.sqc.nuclearsq.progression.MachineRecipes.ids(menu.machine, minecraft.level.getRecipeManager());
            ghost = lock > 0 && lock <= ids.size() ? minecraft.level.getRecipeManager().byKey(ids.get(lock - 1))
                    .map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null) : null;
        }
        return ghost;
    }

    private void ghosts(GuiGraphics g, int x, int y, int mx, int my) {
        var r = ghost();
        if (r == null || minecraft == null || minecraft.level == null) return;
        int own = menu.slots.size() - 36;
        net.minecraft.world.item.ItemStack[] st = com.sqc.nuclearsq.progression.MachineRecipes.ghost(r, BigCrafter.GRID + 1,
                minecraft.level.registryAccess(), net.minecraft.Util.getMillis());
        for (int i = 0; i < own; i++) {
            Slot s = menu.slots.get(i);
            int k = s instanceof net.neoforged.neoforge.items.SlotItemHandler h ? h.getSlotIndex() : -1;
            if (s.hasItem() || k < 0 || k >= st.length || st[k].isEmpty()) continue;
            g.renderFakeItem(st[k], x + s.x, y + s.y);
            g.renderItemDecorations(font, st[k], x + s.x, y + s.y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, (TermKit.col(TermKit.SCREEN) & 0xFFFFFF) | 0xA0000000);
            g.pose().popPose();
            tip(s.x - 1, s.y - 1, 18, 18, st[k].getHoverName(), t("tip.ghost"));
        }
        boolean hover = in(mx, my, clearX(), clearY(), CLEAR_W, CLEAR_H);
        TermKit.button(g, font, x + clearX(), y + clearY(), CLEAR_W, CLEAR_H, t("ghost.clear"), AMBER, true, hover, false);
        tip(clearX(), clearY(), CLEAR_W, CLEAR_H, t("ghost.clear"), t("tip.ghost.clear"));
        boolean fh = in(mx, my, FILL_X, clearY(), FILL_W, CLEAR_H);
        TermKit.button(g, font, x + FILL_X, y + clearY(), FILL_W, CLEAR_H, t("ghost.fill"), GREEN, true, fh, false);
        tip(FILL_X, clearY(), FILL_W, CLEAR_H, t("ghost.fill"), t("tip.ghost.fill"), t("tip.ghost.fill.all"));
    }

    private int rightsX() { int rx = segX() + segW() + 6; return rx + 44 > W - 8 ? W - 52 : rx; }
    private int rightsY() { return segX() + segW() + 6 + 44 > W - 8 ? segY() + SEG_H + 4 : segY() + 1; }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= leftPos + rx && mx < leftPos + rx + rw && my >= topPos + ry && my < topPos + ry + rh;
    }

    private void tip(int rx, int ry, int rw, int rh, Component... lines) {
        TermKit.tip(leftPos + rx, topPos + ry, rw, rh, lines);
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, Math.min(16, height - imageHeight - 4));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && TermKit.styleClick(mx, my)) return true;
        int shaft = rack() && button == 0 ? shaftAt(mx, my) : -1;
        if (shaft >= 0 && placeState(shaft) != BigCrafter.RackView.EMPTY) {
            PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.RACK_TOGGLE, shaft));
            return true;
        }
        if (button == 0 && ghost() != null && in(mx, my, clearX(), clearY(), CLEAR_W, CLEAR_H)) {
            PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.RECIPE_LOCK, 0));
            return true;
        }
        if (button == 0 && ghost() != null && in(mx, my, FILL_X, clearY(), FILL_W, CLEAR_H)) {
            PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.RECIPE_FILL, hasShiftDown() ? 1 : 0));
            return true;
        }
        if (button == 0 && in(mx, my, rightsX(), rightsY(), 44, 12)) {
            com.sqc.nuclearsq.machine.client.AccessScreen.request(menu.pos);
            return true;
        }
        if (button == 0 && in(mx, my, segX(), segY(), segW(), SEG_H)) {
            int v = Mth.clamp((int) (mx - leftPos - segX()) * 2 / segW(), 0, 1);
            PacketDistributor.sendToServer(new DeviceControlPayload(DeviceControlPayload.BIG_ENABLE, v));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static int stateColor(int st) {
        return switch (st) {
            case BigCrafter.OK -> GREEN;
            case BigCrafter.NO_INPUT, BigCrafter.NEED_MORE, BigCrafter.NO_ARM -> AMBER;
            case BigCrafter.OFF -> DIM;
            case BigCrafter.NO_WATER, BigCrafter.NO_FEED, BigCrafter.SHALLOW -> RED;
            default -> RED;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos, h = imageHeight;
        int st = Mth.clamp(menu.status(), 0, STATES.length - 1);
        TermKit.recipes(com.sqc.nuclearsq.client.RecipeLink.machine(menu.machine));
        TermKit.monitor(g, x - 4, y - 4, W + 8, h + 8);
        Component state = t(stateLabel(STATES[st]), menu.needed());
        if (asm()) {
            TermKit.headBadge(g, font, title, state, x + 8, y + 8, W - 16, stateColor(st));
            tip(W - 8 - TermKit.badgeWidth(font, state), 4, TermKit.badgeWidth(font, state), 14, state, t(stateTip(STATES[st])));
        } else {
            TermKit.title(g, font, title, x + 8, y + 8, W - 16);
            TermKit.field(g, font, x + 8, y + 26, W - 16, t("state"), state, stateColor(st));
            tip(8, 25, W - 16, 10, state, t(stateTip(STATES[st])));
        }
        TermKit.rule(g, x + 8, y + 20, W - 16, DIM);

        for (Slot s : menu.slots) {
            boolean own = s.index < menu.slots.size() - 36;
            TermKit.frame(g, font, x + s.x - 1, y + s.y - 1, 18, 18, null, own ? MID : TermKit.FAINT);
        }

        ghosts(g, x, y, mx, my);
        int cap = Math.max(1, menu.capacity());
        float fe = menu.energy() / (float) cap;
        boolean powerOk = st != BigCrafter.NO_POWER;
        int cx = asm() ? 112 : 8, cw = asm() ? W - 8 - 112 : particle() ? 70 : 100, cy = asm() ? 30 : 40;
        if (!rack() && !gen()) {
            TermKit.field(g, font, x + cx, y + cy, cw, t("power"), Component.literal(Math.round(fe * 100) + "%"), powerOk ? AMBER : RED);
            TermKit.bar(g, x + cx, y + cy + 11, cw, 5, fe, powerOk ? AMBER : RED);
            tip(cx, cy - 1, cw, 18, t("power"), t("tip.power", menu.energy(), menu.capacity()), t("tip.power2"));
        }
        if (rack()) {
            int fw = (W - 24) / 2, colX = 16 + fw;
            boolean feed = menu.water() >= Math.max(1, menu.flow());
            Component fv = feed ? t("feed.ok", menu.water()) : t("feed.missing");
            TermKit.field(g, font, x + 8, y + cy, fw, t("feed"), fv, feed ? TermKit.CYAN : RED);
            tip(8, cy - 1, fw, 10, t("feed"), t("tip.feed", menu.flow(), menu.water(), menu.waterCapacity()));
            boolean deep = menu.column() >= menu.columnNeeded();
            TermKit.field(g, font, x + colX, y + cy, fw, t("column"), Component.literal(menu.column() + "/" + menu.columnNeeded()),
                    deep ? TermKit.CYAN : RED);
            tip(colX, cy - 1, fw, 10, t("column"), t("tip.column", menu.columnNeeded()));
            int hover = shaftAt(mx, my);
            for (int i = 0; i < BigCrafter.SHAFTS; i++) shaftField(g, x, y, i, i == hover);
        }
        if (asm()) {
            TermKit.field(g, font, x + cx, y + cy + 20, cw, t("use"), Component.literal(menu.energyPerTick() + " FE/t"), MID);
            tip(cx, cy + 19, cw, 10, t("use"), t("tip.use", menu.energyPerTick()));
        }
        if (particle()) particleFields(g, x, y, mx, my);
        if (menu.machine.hasAuxSlot() || menu.machine.hasFluidRows()) auxFields(g, x, y);
        if (gen()) {
            int gx = 120, gw = W - 8 - 120;
            Component made = Component.literal((st == BigCrafter.OK ? menu.energyPerTick() : 0) + " FE/t");
            TermKit.field(g, font, x + gx, y + cy, gw, t("gen"), made, st == BigCrafter.OK ? GREEN : DIM);
            tip(gx, cy - 1, gw, 10, t("gen"), t("tip.gen", menu.energyPerTick()));
            TermKit.field(g, font, x + 8, y + cy, 100, t("coal_stock"), Component.literal(String.valueOf(menu.needed())), menu.needed() > 0 ? MID : DIM);
            tip(8, cy - 1, 100, 10, t("coal_stock"), t("tip.coal_stock"));
            for (int k = 0; k < BigCrafter.COAL_CARDS.size(); k++) {
                String key = BigCrafter.COAL_CARDS.get(k);
                int sx = BigCrafterMenu.GEN_CARD_X + k * 20;
                if (menu.slots.get(1 + k).hasItem()) continue;
                TermKit.text(g, font, Component.literal(key.substring(0, 1).toUpperCase()), x + sx + 5, y + BigCrafterMenu.IN_Y + 4, TermKit.FAINT);
                tip(sx - 1, BigCrafterMenu.IN_Y - 1, 18, 18, t("coal_card." + key), t("tip.coal_card." + key));
            }
        }
        int px, py, pw;
        if (particle()) {
            px = x + PART_PX;
            py = y + BigCrafterMenu.PART_UY + 4;
            pw = PART_PW;
        } else if (asm()) {
            px = x + 112;
            py = y + BigCrafterMenu.ASM_OY + 5;
            pw = BigCrafterMenu.ASM_OX - 112 - 6;
        } else if (rack()) {
            px = x + 116;
            py = y + 57;
            pw = W - 8 - 116 - 24;
        } else {
            px = x + BigCrafterMenu.IN_X + 22;
            py = y + BigCrafterMenu.IN_Y + 5;
            pw = BigCrafterMenu.OUT_X - BigCrafterMenu.IN_X - 30;
        }
        TermKit.bar(g, px, py, pw, 7, menu.progress(), GREEN);
        int pct = Math.round(menu.progress() * 100);
        if (rack()) TermKit.text(g, font, Component.literal(pct + "%"), px + pw + 4, py, pct > 0 ? GREEN : DIM);
        else TermKit.text(g, font, Component.literal(pct + "%"), px, py + 10, pct > 0 ? GREEN : DIM);
        tip(px - x, py - y - 1, rack() ? pw + 24 : pw, rack() ? 9 : 20, t("tip.progress", pct),
                 t(particle() ? "tip.progress.particle" : small() ? "tip.progress.small" : asm() ? "tip.progress.asm" : rack() ? "tip.progress.pool" : gen() ? "tip.progress.gen" : "tip.progress.rep"));

        int hv = in(mx, my, segX(), segY(), segW(), SEG_H) ? Mth.clamp((mx - x - segX()) * 2 / segW(), 0, 1) : -1;
        TermKit.segments(g, font, x + segX(), y + segY(), segW(), SEG_H, new Component[] {t("off"), t("on")},
                menu.enabled() ? 1 : 0, menu.enabled() ? GREEN : AMBER, hv);
        tip(segX(), segY(), segW() / 2, SEG_H, t("off"), t("tip.off"));
        tip(segX() + segW() / 2, segY(), segW() / 2, SEG_H, t("on"), t("tip.on"));

        TermKit.button(g, font, x + rightsX(), y + rightsY(), 44, 12, Component.translatable("gui.nuclearsq.access.button"), TermKit.CYAN, true,
                in(mx, my, rightsX(), rightsY(), 44, 12), false);
        tip(rightsX(), rightsY(), 44, 12, Component.translatable("gui.nuclearsq.access.button"), Component.translatable("gui.nuclearsq.access.button.tip"));
        int iy = y + menu.inventoryY();
        TermKit.rule(g, x + 8, iy - 8, W - 16, TermKit.FAINT);
        TermKit.scanlines(g, x - 4, y - 4, W + 8, h + 8);
    }

    private void particleFields(GuiGraphics g, int x, int y, int mx, int my) {
        int ux = 84, uw = 152 - ux;
        TermKit.field(g, font, x + ux, y + 40, uw, t("use"), Component.literal(menu.energyPerTick() + " FE/t"), MID);
        tip(ux, 39, uw, 10, t("use"), t("tip.use", menu.energyPerTick()), t("tip.use.particle"));
        var classes = com.sqc.nuclearsq.particle.ParticleClasses.get();
        for (var u : com.sqc.nuclearsq.particle.ParticleClasses.Upgrade.values()) {
            int sx = 9 + u.ordinal() * 20;
            Slot s = menu.slots.get(u.slot());
            if (!s.hasItem()) TermKit.text(g, font, Component.literal(u.key.substring(0, 1).toUpperCase()), x + sx + 5, y + BigCrafterMenu.PART_UY + 4, TermKit.FAINT);
            if (!s.hasItem()) tip(sx - 1, BigCrafterMenu.PART_UY - 1, 18, 18, t("upgrade." + u.key),
                    t("tip.upgrade." + u.key), t("tip.upgrade.max", classes.max(u)));
        }
        int cx = BigCrafterMenu.PART_CARD_X;
        int tier = com.sqc.nuclearsq.particle.ParticleClassCardItem.tierOf(menu.slots.get(com.sqc.nuclearsq.particle.ParticleClasses.CARD).getItem());
        if (tier == 0) {
            TermKit.text(g, font, Component.literal("K"), x + cx + 5, y + BigCrafterMenu.PART_UY + 4, TermKit.FAINT);
            tip(cx - 1, BigCrafterMenu.PART_UY - 1, 18, 18, t("card"), t("tip.card"));
        }
        int last = menu.lastClass();
        for (int k = 0; k < Math.min(BigCrafterMenu.PART_CLASSES, classes.classes.size()); k++) {
            String id = classes.classes.get(k).id();
            int ly = PART_LINES_Y + k * 9, permille = menu.classChance(k);
            boolean locked = k > tier;
            Component v = locked ? t("class.locked") : Component.literal(permille / 10 + "." + permille % 10 + "%");
            int color = locked ? TermKit.FAINT : k == last ? GREEN : permille > 0 ? MID : DIM;
            TermKit.field(g, font, x + 8, y + ly, 104, t("class." + id), v, color);
            if (k == last && !locked) TermKit.text(g, font, Component.literal(">"), x + 2, y + ly, GREEN);
            if (locked) tip(8, ly - 1, 104, 9, t("class." + id), t("tip.class." + id), t("tip.class.locked", t("card." + id)));
            else tip(8, ly - 1, 104, 9, t("class." + id), t("tip.class." + id), t("tip.class", v));
        }
        int by = PART_LINES_Y + BigCrafterMenu.PART_CLASSES * 9;
        TermKit.field(g, font, x + 8, y + by, 104, t("bonus"), Component.literal(menu.bonus() + "%"), menu.bonus() > 0 ? GREEN : DIM);
        tip(8, by - 1, 104, 9, t("bonus"), t("tip.bonus", menu.bonus()));
    }

    private void auxFields(GuiGraphics g, int x, int y) {
        if (menu.machine.hasAuxSlot()) {
            TermKit.text(g, font, Component.literal("+"), x + BigCrafterMenu.AUX_X + 22, y + BigCrafterMenu.AUX_Y + 5, MID);
            tip(BigCrafterMenu.AUX_X - 1, BigCrafterMenu.AUX_Y - 1, 18, 18, t("aux"), t("tip.aux"));
        }
        var ports = menu.fluidPorts();
        int n = Math.max(1, Math.min(ports.size(), 3));
        int fw = (W - 16 - (n - 1) * 8) / n;
        for (int i = 0; i < ports.size(); i++) {
            int fx = 8 + i * (fw + 8), fy = BigCrafterMenu.FLUID_Y;
            int have = menu.fluid(i), cap = Math.max(1, menu.fluidCapacity(i));
            int st = menu.status();
            boolean low = ports.get(i).in() && (ports.get(i).name().contains("water") ? st == BigCrafter.NO_WATER : st == BigCrafter.NO_ACID);
            Component name = Component.translatable("port.nuclearsq." + menu.machine.id + "." + ports.get(i).name());
            TermKit.field(g, font, x + fx, y + fy, fw, name, Component.literal(have + " mB"), low ? RED : TermKit.CYAN);
            TermKit.bar(g, x + fx, y + fy + 11, fw, 5, have / (float) cap, low ? RED : TermKit.CYAN);
            tip(fx, fy - 1, fw, 18, name, t("tip.fluid", have, cap));
        }
    }

    private static int shaftX(int i) { return 8 + (i % 3) * (BigCrafterMenu.SHAFT_W + 4); }
    private static int shaftY(int i) { return BigCrafterMenu.SHAFT_Y + (i / 3) * BigCrafterMenu.SHAFT_DY; }

    private int shaftAt(double mx, double my) {
        for (int i = 0; i < BigCrafter.SHAFTS; i++) {
            if (in(mx, my, shaftX(i), shaftY(i), BigCrafterMenu.SHAFT_W, BigCrafterMenu.SHAFT_H)) return i;
        }
        return -1;
    }

    private int placeState(int i) { return (menu.rackPlace(i) >> BigCrafterMenu.PLACE_STATE_SHIFT) & 3; }

    private void shaftField(GuiGraphics g, int x, int y, int i, boolean hover) {
        int v = menu.rackPlace(i), pct = v & 127, state = placeState(i);
        boolean locked = (v & BigCrafterMenu.PLACE_LOCKED) != 0, inPlace = (v & BigCrafterMenu.PLACE_IN) != 0;
        boolean released = (v & BigCrafterMenu.PLACE_RELEASED) != 0, moving = (v & BigCrafterMenu.PLACE_MOVING) != 0;
        int fx = shaftX(i), fy = shaftY(i), w = BigCrafterMenu.SHAFT_W, h = BigCrafterMenu.SHAFT_H;
        String key;
        int color;
        if (state == BigCrafter.RackView.EMPTY) {
            key = "empty";
            color = DIM;
        } else if (!locked) {
            key = moving ? "out" : "free";
            color = AMBER;
        } else if (!inPlace) {
            key = "in";
            color = AMBER;
        } else if (state == BigCrafter.RackView.HOT) {
            key = "cooling";
            color = pct < 50 ? RED : AMBER;
        } else if (state == BigCrafter.RackView.COOLED) {
            key = "cooled";
            color = GREEN;
        } else {
            key = "fresh";
            color = RED;
        }
        TermKit.frame(g, font, x + fx, y + fy, w, h, null, hover && state != BigCrafter.RackView.EMPTY ? GREEN : state == BigCrafter.RackView.EMPTY ? TermKit.FAINT : MID);
        TermKit.text(g, font, t("place", i + 1), x + fx + 4, y + fy + 3, MID);
        Component label = t("place." + key, pct);
        TermKit.text(g, font, label, x + fx + 4, y + fy + 13, color);
        if (state == BigCrafter.RackView.HOT || state == BigCrafter.RackView.COOLED) {
            int heat = state == BigCrafter.RackView.COOLED ? 0 : 100 - pct;
            TermKit.bar(g, x + fx + 4, y + fy + h - 4, w - 8, 2, heat / 100f, heat > 50 ? RED : heat > 15 ? AMBER : GREEN);
        }
        Component action = state == BigCrafter.RackView.EMPTY ? t("tip.place.empty") : t(released || !locked ? "tip.place.lower" : "tip.place.raise");
        tip(fx, fy, w, h, t("place", i + 1), label, t("tip.place." + key, pct, Math.round(25 + 2.75f * (100 - pct))), action);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if ((hoveredSlot != null && hoveredSlot.hasItem()) || !TermKit.renderTip(g, font, mx, my)) renderTooltip(g, mx, my);
    }
}
