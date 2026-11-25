package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.progression.BuildData;
import com.sqc.nuclearsq.progression.PortFaces;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class BuildSceneTests {
    private BuildSceneTests() {}

    private static final String FLOOR = "floor";
    private static final String[] LANGUAGE_CODES = {"en_us", "de_de", "uk_ua", "ru_ru", "zh_cn"};

    private static List<String> buildPageIds() {
        List<String> pageIds = new ArrayList<>();
        try (var in = BuildSceneTests.class.getResourceAsStream("/data/" + NuclearSQ.MODID + "/compendium/kompendium_struktur.json")) {
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var section : root.getAsJsonArray("sections"))
                for (var chapter : section.getAsJsonObject().getAsJsonArray("chapters"))
                    for (var page : chapter.getAsJsonObject().getAsJsonArray("pages")) {
                        JsonObject pageJson = page.getAsJsonObject();
                        if (pageJson.get("anim").getAsString().equals("BUILD")) pageIds.add(pageJson.get("id").getAsString());
                    }
        } catch (Exception ex) {
            throw new GameTestAssertException("kompendium_struktur.json nicht lesbar: " + ex);
        }
        if (pageIds.isEmpty()) throw new GameTestAssertException("keine 3D-Beispiele in der Struktur");
        return pageIds;
    }

    private static void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) throw new GameTestAssertException(problems.size() + " Probleme: " + String.join("; ", problems));
    }

    private static final Map<String, BuildData> PARSED_SCENES = new HashMap<>();

    private static BuildData parseOrNote(String pageId, List<String> problems) {
        BuildData known = PARSED_SCENES.get(pageId);
        if (known != null) return known;
        try {
            BuildData parsed = BuildData.parse(pageId);
            PARSED_SCENES.put(pageId, parsed);
            return parsed;
        } catch (Exception ex) {
            problems.add(pageId + ": " + ex);
            return null;
        }
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void everyBuildPageHasAValidScene(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null) continue;
            Set<String> nodeKeys = new TreeSet<>();
            for (var machine : scene.machines) nodeKeys.add(machine.key());
            for (String languageCode : LANGUAGE_CODES) {
                Map<String, String> lang = CompendiumSearchTests.lang(languageCode);
                if (!lang.containsKey("compendium." + pageId + ".title") || !lang.containsKey("compendium." + pageId + ".text"))
                    problems.add(pageId + ": Titel oder Text fehlt in " + languageCode);
                for (var machine : scene.machines)
                    if (!lang.containsKey("compendium." + machine.page() + ".title") || !lang.containsKey("compendium." + machine.page() + ".text"))
                        problems.add(pageId + ": Seite " + machine.page() + " fehlt in " + languageCode);
                String prefix = "compendium." + pageId + ".";
                for (String key : lang.keySet()) {
                    if (!key.startsWith(prefix)) continue;
                    String rest = key.substring(prefix.length());
                    boolean noteOrTag = rest.startsWith("note.") || rest.startsWith("tag.");
                    if (noteOrTag && !nodeKeys.contains(rest.substring(rest.indexOf('.') + 1))) problems.add(key + ": kein Knoten dazu in " + languageCode);
                }
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void turnedMachinesFollowTheStructureRotation(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Set<String> machineIds = new TreeSet<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null) continue;
            for (var machine : scene.machines) if (!machine.id().startsWith("block:")) machineIds.add(machine.id());
        }
        for (String machineId : machineIds) {
            try {
                JsonObject multiblock = BuildData.multiblock(machineId);
                var size = multiblock.getAsJsonArray("size");
                int width = size.get(0).getAsInt(), depth = size.get(2).getAsInt();
                var controllerJson = multiblock.getAsJsonArray("controller");
                int[] controllerCell = {controllerJson.get(0).getAsInt(), controllerJson.get(1).getAsInt(), controllerJson.get(2).getAsInt()};
                for (var portElement : multiblock.getAsJsonArray("ports")) {
                    JsonObject port = portElement.getAsJsonObject();
                    var cellJson = port.getAsJsonArray("cell");
                    int[] portCell = {cellJson.get(0).getAsInt(), cellJson.get(1).getAsInt(), cellJson.get(2).getAsInt()};
                    for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
                        int[] turnedPort = BuildData.turnCell(portCell, quarterTurns, width, depth);
                        int[] turnedController = BuildData.turnCell(controllerCell, quarterTurns, width, depth);
                        int[] expectedOffset = MultiblockStructure.rotate(portCell[0] - controllerCell[0], portCell[2] - controllerCell[2], quarterTurns);
                        boolean sameTurn = turnedPort[0] - turnedController[0] == expectedOffset[0] && turnedPort[2] - turnedController[2] == expectedOffset[1]
                                && turnedPort[1] == portCell[1];
                        if (!sameTurn) problems.add(machineId + " " + port.get("name").getAsString() + " bei Drehung " + quarterTurns);
                    }
                }
            } catch (Exception ex) {
                problems.add(machineId + ": " + ex);
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void machinesStayOnTheGroundAndApart(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null) continue;
            for (int first = 0; first < scene.machines.size(); first++) {
                var machine = scene.machines.get(first);
                boolean onGround = machine.cornerX() >= 0 && machine.cornerZ() >= 0
                        && machine.cornerX() + machine.width() <= scene.groundWidth && machine.cornerZ() + machine.depth() <= scene.groundDepth;
                if (!onGround) problems.add(pageId + ": " + machine.key() + " liegt ausserhalb des Bodens");
                for (int second = first + 1; second < scene.machines.size(); second++) {
                    var other = scene.machines.get(second);
                    boolean overlap = machine.cornerX() < other.cornerX() + other.width() && other.cornerX() < machine.cornerX() + machine.width()
                            && machine.cornerZ() < other.cornerZ() + other.depth() && other.cornerZ() < machine.cornerZ() + machine.depth();
                    if (overlap && !scene.machineOpen.get(first) && !scene.machineOpen.get(second)) problems.add(pageId + ": " + machine.key() + " und " + other.key() + " ueberlappen sich");
                }
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void pipesLeaveAndEnterExactlyAtThePorts(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null) continue;
            for (int pipeIndex = 0; pipeIndex < scene.pipes.size(); pipeIndex++) {
                var pipe = scene.pipes.get(pipeIndex);
                List<BuildData.Block> blocks = scene.blocksOfPipe.get(pipeIndex);
                String name = pageId + ": Rohr " + pipe.key() + " (" + pipe.fromPort() + " -> " + pipe.toPort() + ")";
                checkEnd(name + " Anfang", scene.machines.get(pipe.fromMachine()), pipe.fromPort(), blocks.get(0), problems);
                checkEnd(name + " Ende", scene.machines.get(pipe.toMachine()), pipe.toPort(), blocks.get(blocks.size() - 1), problems);
                for (int i = 1; i < blocks.size(); i++) {
                    var before = blocks.get(i - 1);
                    var after = blocks.get(i);
                    int distance = Math.abs(after.x - before.x) + Math.abs(after.y - before.y) + Math.abs(after.z - before.z);
                    if (distance != 1) problems.add(name + ": Bloecke " + (i - 1) + " und " + i + " haengen nicht zusammen (Abstand " + distance + ")");
                }
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    private static void checkEnd(String name, BuildData.Machine machine, String portName, BuildData.Block block, List<String> problems) {
        int[] portCell = machine.portCells().get(portName);
        int wall = machine.portWalls().get(portName);
        double[] outward = BuildData.outwardOf(wall);
        double[] blockCenter = {block.x + 0.5, block.y + 0.5, block.z + 0.5};
        double[] expectedCenter = {machine.cornerX() + portCell[0] + 0.5 + outward[0], machine.cornerY() + portCell[1] + 0.5 + outward[1], machine.cornerZ() + portCell[2] + 0.5 + outward[2]};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(blockCenter[axis] - expectedCenter[axis]) > 1e-6)
                problems.add(name + ": Block bei " + java.util.Arrays.toString(blockCenter) + " statt direkt neben dem Port bei " + java.util.Arrays.toString(expectedCenter));
        }
        net.minecraft.core.Direction towardsPort = switch (wall) {
            case PortFaces.MINUS_X -> net.minecraft.core.Direction.EAST;
            case PortFaces.PLUS_X -> net.minecraft.core.Direction.WEST;
            case PortFaces.MINUS_Z -> net.minecraft.core.Direction.SOUTH;
            case PortFaces.PLUS_Z -> net.minecraft.core.Direction.NORTH;
            default -> net.minecraft.core.Direction.DOWN;
        };
        if (((block.connections >> (2 * towardsPort.ordinal())) & 3) != 3) problems.add(name + ": Block ist nicht mit dem Port verbunden");
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void everyUsedPortSitsOnTheModel(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null || scene.fixedLayout) continue;
            for (var pipe : scene.pipes) {
                for (int end = 0; end < 2; end++) {
                    var machine = scene.machines.get(end == 0 ? pipe.fromMachine() : pipe.toMachine());
                    String portName = end == 0 ? pipe.fromPort() : pipe.toPort();
                    if (machine.id().startsWith("block:") || !PortFaces.hasModel(machine.id())) continue;
                    try {
                        JsonObject multiblock = BuildData.multiblock(machine.id());
                        int[] size = intsOf(multiblock.getAsJsonArray("size")), controller = BuildData.modelCell(multiblock);
                        int[] cell = null;
                        for (var portElement : multiblock.getAsJsonArray("ports"))
                            if (portElement.getAsJsonObject().get("name").getAsString().equals(portName)) cell = intsOf(portElement.getAsJsonObject().getAsJsonArray("cell"));
                        int wall = PortFaces.wallOfPort(machine.id(), size, controller, cell);
                        double distance = PortFaces.distanceToModel(machine.id(), size, controller, cell, wall);
                        if (distance > 0.5) problems.add(pageId + ": " + machine.id() + "." + portName + " liegt " + String.format("%.2f", distance) + " Bloecke vom Modell entfernt");
                        int turnedWall = PortFaces.turnWall(wall, machine.quarterTurns());
                        if (machine.portWalls().get(portName) != turnedWall) problems.add(pageId + ": " + machine.id() + "." + portName + " Wand stimmt nicht mit der Drehung ueberein");
                    } catch (Exception ex) {
                        problems.add(pageId + ": " + machine.id() + "." + portName + ": " + ex);
                    }
                }
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    private static int[] intsOf(com.google.gson.JsonArray array) {
        int[] values = new int[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsInt();
        return values;
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void pipeBlocksStandInTheOpenAndOnlyOnce(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null) continue;
            Map<String, BuildData.Block> blockAtPosition = new HashMap<>();
            for (var block : scene.blocks) {
                String position = block.x + "," + block.y + "," + block.z;
                if (blockAtPosition.put(position, block) != null) problems.add(pageId + ": zwei Rohrbloecke bei " + position);
                if (block.y < 0) problems.add(pageId + ": Rohrblock unter dem Boden bei " + position);
                if (!scene.fixedLayout && scene.isInsideMachine(new double[] {block.x + 0.5, block.y + 0.5, block.z + 0.5}) && !scene.isPortCorridor(block.x, block.y, block.z)) problems.add(pageId + ": Rohrblock in einer Maschine bei " + position);
            }

            for (int pipeIndex = 0; pipeIndex < scene.pipes.size(); pipeIndex++) {
                var pipe = scene.pipes.get(pipeIndex);
                for (var block : scene.blocksOfPipe.get(pipeIndex))
                    if (!block.kind.equals(pipe.kind()))
                        problems.add(pageId + ": Rohr " + pipe.key() + " (" + pipe.kind() + ") geht durch einen Block der Art " + block.kind + " bei " + block.x + "," + block.y + "," + block.z);
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void shownItemsExistAndHaveRealDestinations(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String pageId : buildPageIds()) {
            BuildData scene = parseOrNote(pageId, problems);
            if (scene == null) continue;
            List<String> itemIds = new ArrayList<>();
            for (String itemId : scene.pipeItems) if (itemId != null) itemIds.add(itemId);
            for (String itemId : scene.machineMakes) if (itemId != null) itemIds.add(itemId);
            for (String itemId : itemIds) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
                if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) problems.add(pageId + ": unbekannter Gegenstand " + itemId);
            }
            for (int machineIndex = 0; machineIndex < scene.machines.size(); machineIndex++) {
                String destination = scene.machineMakesTo.get(machineIndex);
                if (destination == null) continue;
                var machine = scene.machines.get(machineIndex);
                boolean destinationExists = false, linked = false;
                for (int otherIndex = 0; otherIndex < scene.machines.size(); otherIndex++) {
                    if (otherIndex == machineIndex || !scene.machines.get(otherIndex).key().equals(destination)) continue;
                    destinationExists = true;
                    for (var pipe : scene.pipes)
                        if ((pipe.fromMachine() == machineIndex && pipe.toMachine() == otherIndex) || (pipe.fromMachine() == otherIndex && pipe.toMachine() == machineIndex)) linked = true;
                }
                if (!destinationExists) problems.add(pageId + ": " + machine.key() + " liefert an unbekanntes Ziel " + destination);
                else if (!linked) problems.add(pageId + ": " + machine.key() + " liefert an " + destination + " ohne Leitung");
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void loopExamplesFollowTheTestBuilds(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String[][] pairs = {{"example_primary_loop", "primary"}, {"example_extended_loop", "extended"}, {"example_full_loop", "full"}};
        for (String[] pair : pairs) {
            BuildData scene = parseOrNote(pair[0], problems);
            if (scene == null) continue;
            if (!scene.fixedLayout) problems.add(pair[0] + ": keine festen Positionen");
            try (var in = BuildSceneTests.class.getResourceAsStream("/data/" + NuclearSQ.MODID + "/testbuilds/" + pair[1] + ".json")) {
                JsonObject plan = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var planned = plan.getAsJsonArray("machines");
                if (planned.size() != scene.machines.size()) problems.add(pair[0] + ": " + scene.machines.size() + " Anlagen statt " + planned.size());
                for (int index = 0; index < Math.min(planned.size(), scene.machines.size()); index++) {
                    JsonObject machineJson = planned.get(index).getAsJsonObject();
                    var machine = scene.machines.get(index);
                    if (!machine.id().equals(machineJson.get("id").getAsString()) || machine.quarterTurns() != machineJson.get("rot").getAsInt())
                        problems.add(pair[0] + ": Anlage " + index + " ist " + machine.id() + " mit Drehung " + machine.quarterTurns());
                    int[] anchor = intsOf(machineJson.getAsJsonArray("anchor")), first = intsOf(planned.get(0).getAsJsonObject().getAsJsonArray("anchor"));
                    var hull = scene.machines.get(0);
                    boolean samePlace = Math.abs(machine.modelX() - hull.modelX() - (anchor[0] - first[0])) < 1e-6 && Math.abs(machine.modelY() - hull.modelY() - (anchor[1] - first[1])) < 1e-6
                            && Math.abs(machine.modelZ() - hull.modelZ() - (anchor[2] - first[2])) < 1e-6;
                    if (!samePlace) problems.add(pair[0] + ": " + machine.key() + " steht nicht wie im Testbau");
                }
                Set<String> pipeCells = new TreeSet<>();
                for (String kind : new String[] {"pipes", "cables", "bigpipes"})
                    for (var cell : plan.getAsJsonArray(kind)) {
                        int[] position = intsOf(cell.getAsJsonArray());
                        pipeCells.add((position[0] - first(planned)[0]) + "," + (position[1] - first(planned)[1]) + "," + (position[2] - first(planned)[2]));
                    }
                var hull = scene.machines.get(0);
                Set<String> sceneCells = new TreeSet<>();
                for (var block : scene.blocks)
                    sceneCells.add((int) Math.round(block.x + 0.5 - hull.modelX()) + "," + (int) Math.round(block.y - hull.modelY()) + "," + (int) Math.round(block.z + 0.5 - hull.modelZ()));
                if (!pipeCells.equals(sceneCells)) problems.add(pair[0] + ": " + sceneCells.size() + " Leitungsbloecke statt " + pipeCells.size() + " wie im Testbau");
            } catch (Exception ex) {
                problems.add(pair[0] + ": " + ex);
            }
            if (!scene.machineOpen.get(0)) problems.add(pair[0] + ": Huelle ist nicht aufgeschnitten");
        }
        failIfAny(problems);
        helper.succeed();
    }

    private static int[] first(com.google.gson.JsonArray planned) {
        return intsOf(planned.get(0).getAsJsonObject().getAsJsonArray("anchor"));
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void everyMachinePageHasAWorkingSingleMachineScene(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        final String suffix = "_controller_sqc";
        for (var node : com.sqc.nuclearsq.progression.AchievementTree.NODES) {
            if (!node.item().endsWith(suffix)) continue;
            String machineId = node.item().substring(0, node.item().length() - suffix.length());
            if (!BuildData.hasMultiblockModel(machineId)) continue;
            try {
                BuildData scene = BuildData.ofMachine(machineId, node.page());
                if (scene.machines.size() != 1) problems.add(machineId + ": " + scene.machines.size() + " Maschinen in der Einzelszene");
                var machine = scene.machines.get(0);
                for (String port : machine.portCells().keySet()) {
                    double[][] points = BuildData.portPoints(machine, port);
                    if (points == null) problems.add(machineId + ": kein Punkt fuer Anschluss " + port);
                }
            } catch (Exception ex) {
                problems.add(machineId + ": " + ex);
            }
        }
        failIfAny(problems);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void thePortPlateDecidesTheWallAtACorner(GameTestHelper helper) {
        int[] size = {4, 5, 3}, controller = {0, 0, 0};
        int energyWall = PortFaces.wallOfPort("pattern_rack", size, controller, new int[] {0, 0, 2});
        int dataWall = PortFaces.wallOfPort("pattern_rack", size, controller, new int[] {3, 0, 2});
        if (energyWall != PortFaces.PLUS_Z || dataWall != PortFaces.PLUS_Z) throw new GameTestAssertException("pattern_rack ports sit on the back wall but got " + energyWall + " and " + dataWall);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "build_scenes", timeoutTicks = 20)
    public static void everyMachinePortHasATooltipLabelAndSitsNearItsModel(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var lang = net.minecraft.locale.Language.getInstance();
        final String suffix = "_controller_sqc";
        var seen = new java.util.HashSet<String>();
        for (var node : com.sqc.nuclearsq.progression.AchievementTree.NODES) {
            if (!node.item().endsWith(suffix)) continue;
            String machineId = node.item().substring(0, node.item().length() - suffix.length());
            if (!seen.add(machineId) || !BuildData.hasMultiblockModel(machineId)) continue;
            try {
                JsonObject multiblock = BuildData.multiblock(machineId);
                int[] size = intsOf(multiblock.getAsJsonArray("size")), controller = BuildData.modelCell(multiblock);
                for (var portElement : multiblock.getAsJsonArray("ports")) {
                    JsonObject port = portElement.getAsJsonObject();
                    String portName = port.get("name").getAsString(), kind = port.get("type").getAsString();
                    int[] cell = intsOf(port.getAsJsonArray("cell"));
                    int wall = PortFaces.wallOfPort(machineId, size, controller, cell);
                    double distance = PortFaces.distanceToModel(machineId, size, controller, cell, wall);
                    String where = machineId + "." + portName + " kind=" + kind + " cell=" + java.util.Arrays.toString(cell) + " wall=" + wall;
                    if (distance > 0.75) problems.add("FAR " + where + " dist=" + String.format("%.2f", distance));
                    if (!lang.has("port.nuclearsq." + machineId + "." + portName)) problems.add("NOLABEL " + where);

                }
            } catch (Exception ex) {
                problems.add(machineId + ": " + ex);
            }
        }
        failIfAny(problems);
        helper.succeed();
    }
}
