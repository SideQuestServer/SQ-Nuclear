package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.progression.AchievementTree;
import com.sqc.nuclearsq.progression.LineTrees;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class LineTreeTests {
    private LineTreeTests() {}

    private static JsonObject json(String path) {
        try (var in = LineTreeTests.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new GameTestAssertException(path + " nicht lesbar: " + ex);
        }
    }

    private static int stageOf(String key) {
        int stage = 0;
        for (int k = 0; k <= AchievementTree.index(key); k++) if (AchievementTree.stageAt(k) != null) stage++;
        return stage;
    }

    @GameTest(template = "floor", batch = "compendium_lines", timeoutTicks = 20)
    public static void everyMachineIsInALine(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> twice = Set.of("kiln", "blast_furnace");
        var root = json("/data/" + NuclearSQ.MODID + "/compendium/kompendium_struktur.json");
        java.util.Map<String, String> firstPage = new java.util.HashMap<>();
        for (var s : root.getAsJsonArray("sections"))
            for (var c : s.getAsJsonObject().getAsJsonArray("chapters")) {
                var co = c.getAsJsonObject();
                for (var p : co.getAsJsonArray("pages")) {
                    String id = p.getAsJsonObject().get("id").getAsString();
                    if (id.startsWith("line_")) firstPage.put(id, co.get("id").getAsString());
                }
            }
        for (var l : LineTrees.LINES) {
            if (!l.chapter().equals(firstPage.get(l.page()))) problems.add(l.page() + " fehlt im Kapitel " + l.chapter());
            var nodes = LineTrees.nodes(l);
            Set<String> cells = new HashSet<>();
            for (var n : nodes) {
                if (!seen.add(n.key()) && !twice.contains(n.key())) problems.add(n.key() + " steht in zwei Linien");
                if (!cells.add(n.col() + "," + n.row())) problems.add(l.key() + ": Feld von " + n.key() + " doppelt");
                for (var o : nodes)
                    if (o.row() == n.row() && o.col() > n.col() && AchievementTree.index(o.key()) < AchievementTree.index(n.key()))
                        problems.add(l.key() + ": " + o.key() + " steht rechts von " + n.key() + ", kommt aber frueher");
            }
            for (var b : l.blocks())
                for (String k : b.keys()) if (stageOf(k) != b.stage()) problems.add(l.key() + ": " + k + " gehoert in Etappe " + stageOf(k) + ", nicht " + b.stage());
            for (var e : LineTrees.edges(l))
                if (e.from() < 0 || e.to() >= nodes.size() || e.from() == e.to()) problems.add(l.key() + ": Linie " + e.from() + " -> " + e.to());
            for (String id : l.products())
                if (BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id)) == Items.AIR) problems.add(l.key() + ": Erzeugnis " + id + " nicht registriert");
        }
        for (var n : AchievementTree.NODES) if (!seen.contains(n.key())) problems.add(n.key() + " steht in keiner Linie");
        for (String id : com.sqc.nuclearsq.kit.KitRegistry.IDS)
            if (AchievementTree.NODES.stream().noneMatch(n -> n.item().equals(id + "_controller_sqc"))) problems.add(id + ": Controller in keiner Linie");
        for (String lang : List.of("de_de", "en_us")) {
            var l = json("/assets/" + NuclearSQ.MODID + "/lang/" + lang + ".json");
            for (String k : List.of("compendium.lines.stage", "compendium.lines.products")) if (!l.has(k)) problems.add(lang + ": " + k);
            for (var line : LineTrees.LINES)
                for (String part : List.of(".title", ".text")) if (!l.has("compendium." + line.page() + part)) problems.add(lang + ": compendium." + line.page() + part);
            for (var n : AchievementTree.NODES)
                for (String part : List.of(".name", ".short")) if (!l.has("compendium.plant." + n.key() + part)) problems.add(lang + ": compendium.plant." + n.key() + part);
        }
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
        h.succeed();
    }
}
