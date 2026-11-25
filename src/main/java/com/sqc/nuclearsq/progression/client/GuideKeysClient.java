package com.sqc.nuclearsq.progression.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class GuideKeysClient {
    private GuideKeysClient() {}

    public static final KeyMapping OPEN_GUIDE = new KeyMapping("key.nuclearsq.open_guide", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.nuclearsq");
    public static final KeyMapping OPEN_EXAMPLE = new KeyMapping("key.nuclearsq.open_example", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.nuclearsq");

    private static final int HOLD_TICKS = 20, BAR = 10;

    private static Item hovered, holding;
    private static KeyMapping holdKey;
    private static int seenTick, ticks, clientTick;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUIDE);
        event.register(OPEN_EXAMPLE);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        if (e.getEntity() == null || Minecraft.getInstance().screen instanceof ProgressionScreen) return;
        Item item = e.getItemStack().getItem();
        boolean guide = ProgressionScreen.guidePage(item) != null, example = ProgressionScreen.examplePage(item) != null;
        if (!guide && !example) return;
        hovered = item;
        seenTick = clientTick;
        List<Component> tip = e.getToolTip();
        if (guide) tip.add(hint(OPEN_GUIDE, "tooltip.nuclearsq.open_guide", item));
        if (example) tip.add(hint(OPEN_EXAMPLE, "tooltip.nuclearsq.open_example", item));
    }

    private static Component hint(KeyMapping key, String text, Item item) {
        var line = Component.translatable(text, key.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY);
        if (holdKey != key || holding != item || ticks == 0) return line;
        int done = ticks * BAR / HOLD_TICKS;
        return line.append(" ").append(Component.literal("|".repeat(done)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("|".repeat(BAR - done)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        clientTick++;
        Minecraft mc = Minecraft.getInstance();
        KeyMapping key = isHeld(mc, OPEN_GUIDE) ? OPEN_GUIDE : isHeld(mc, OPEN_EXAMPLE) ? OPEN_EXAMPLE : null;
        boolean active = key != null && mc.player != null && mc.screen != null && !(mc.screen instanceof ProgressionScreen)
                && !(mc.screen.getFocused() instanceof EditBox) && clientTick - seenTick <= 2;
        if (!active || key != holdKey || hovered != holding) {
            holdKey = active ? key : null;
            holding = active ? hovered : null;
            ticks = 0;
            if (!active) return;
        }
        if (++ticks < HOLD_TICKS) return;
        int[] page = key == OPEN_GUIDE ? ProgressionScreen.guidePage(holding) : ProgressionScreen.examplePage(holding);
        holdKey = null;
        holding = null;
        ticks = 0;
        if (page == null) return;
        mc.player.closeContainer();
        mc.setScreen(new ProgressionScreen(page));
    }

    private static boolean isHeld(Minecraft mc, KeyMapping key) {
        if (key.isUnbound()) return false;
        InputConstants.Key k = key.getKey();
        long window = mc.getWindow().getWindow();
        return k.getType() == InputConstants.Type.MOUSE ? GLFW.glfwGetMouseButton(window, k.getValue()) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(window, k.getValue());
    }
}
