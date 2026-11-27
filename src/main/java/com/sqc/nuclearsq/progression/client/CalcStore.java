package com.sqc.nuclearsq.progression.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.progression.ResourceCalc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

public final class CalcStore {
    private CalcStore() {}

    public static final int MAX_LINES = 64;
    public static final long MAX_COUNT = 999_999;

    public static final List<ResourceCalc.Line> LINES = new ArrayList<>();
    public static final Map<ResourceCalc.Res, Integer> CHOICE = new HashMap<>();
    public static final Map<Item, Integer> PICKS = new HashMap<>();
    private static boolean loaded;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("nuclearsq-calculator.json");
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("list")) for (var e : o.getAsJsonArray("list")) {
                JsonObject l = e.getAsJsonObject();
                Item it = item(l.get("item").getAsString());
                long n = Math.max(1, Math.min(MAX_COUNT, l.get("count").getAsLong()));
                if (it != Items.AIR && LINES.size() < MAX_LINES) LINES.add(new ResourceCalc.Line(it, n));
            }
            if (o.has("recipes")) for (var e : o.getAsJsonObject("recipes").entrySet()) {
                ResourceCalc.Res res = ResourceCalc.Res.parse(e.getKey());
                if (res != null) CHOICE.put(res, e.getValue().getAsInt());
            }
            if (o.has("picks")) for (var e : o.getAsJsonObject("picks").entrySet()) {
                Item it = item(e.getKey());
                if (it != Items.AIR) PICKS.put(it, e.getValue().getAsInt());
            }
        } catch (Exception e) {
            LINES.clear();
            CHOICE.clear();
            PICKS.clear();
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        JsonArray list = new JsonArray();
        for (var l : LINES) {
            JsonObject e = new JsonObject();
            e.addProperty("item", BuiltInRegistries.ITEM.getKey(l.item()).toString());
            e.addProperty("count", l.count());
            list.add(e);
        }
        o.add("list", list);
        JsonObject rec = new JsonObject();
        for (var e : CHOICE.entrySet()) if (e.getValue() != 0) rec.addProperty(e.getKey().key(), e.getValue());
        o.add("recipes", rec);
        JsonObject pk = new JsonObject();
        for (var e : PICKS.entrySet()) if (e.getValue() != 0) pk.addProperty(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue());
        o.add("picks", pk);
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (Exception e) {
        }
    }

    public static void add(Item item, long count) {
        if (item == Items.AIR || count <= 0) return;
        for (int i = 0; i < LINES.size(); i++) {
            if (LINES.get(i).item() == item) {
                LINES.set(i, new ResourceCalc.Line(item, Math.min(MAX_COUNT, LINES.get(i).count() + count)));
                save();
                return;
            }
        }
        if (LINES.size() >= MAX_LINES) return;
        LINES.add(new ResourceCalc.Line(item, Math.min(MAX_COUNT, count)));
        save();
    }

    public static void change(int index, long delta) {
        if (index < 0 || index >= LINES.size()) return;
        var l = LINES.get(index);
        long n = l.count() + delta;
        if (n <= 0) LINES.remove(index);
        else LINES.set(index, new ResourceCalc.Line(l.item(), Math.min(MAX_COUNT, n)));
        save();
    }

    public static void remove(int index) {
        if (index < 0 || index >= LINES.size()) return;
        LINES.remove(index);
        save();
    }

    public static void clear() {
        LINES.clear();
        save();
    }
}
