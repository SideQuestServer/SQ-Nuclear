package com.sqc.nuclearsq.progression;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Direction;

public final class BuildData {
    public static final float BUILD_SECONDS = 1.3f, PIPE_SECONDS = 1.2f, HOLD_SECONDS = 0.7f;

    public static final List<String> LINK_KINDS = List.of("pipe", "item", "power", "data");

    public record Machine(String key, String id, int color, double cornerX, double cornerY, double cornerZ, int width, int height, int depth,
                          int quarterTurns, double modelX, double modelY, double modelZ, Map<String, int[]> portCells, Map<String, Integer> portWalls, String page) {}

    public record Pipe(String key, String kind, int color, List<double[]> pathPoints, int fromMachine, int toMachine, String fromPort, String toPort,
                       int blockCount) {}

    public static final class Block {
        public final int x, y, z;
        public final int pipeIndex, indexInPipe;
        public final String kind;
        public int connections;

        Block(int x, int y, int z, String kind, int pipeIndex, int indexInPipe) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.pipeIndex = pipeIndex;
            this.indexInPipe = indexInPipe;
        }
    }

    public record Step(List<Integer> machines, List<Integer> pipes) {
        public float buildSeconds() { return machines.isEmpty() ? 0 : BUILD_SECONDS; }
        public float pipeSeconds() { return pipes.isEmpty() ? 0 : PIPE_SECONDS; }
        public float totalSeconds() { return buildSeconds() + pipeSeconds() + HOLD_SECONDS; }
    }

    public final List<Machine> machines = new ArrayList<>();
    public final List<Pipe> pipes = new ArrayList<>();
    public final List<String> pipeItems = new ArrayList<>();
    public final List<String> machineMakes = new ArrayList<>(), machineMakesTo = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();

    public final List<String[]> captions = new ArrayList<>();
    public final List<Block> blocks = new ArrayList<>();

    public final List<List<Block>> blocksOfPipe = new ArrayList<>();
    public final int[] stepOfMachine, stepOfPipe;

    public final List<double[]> boundsPoints = new ArrayList<>();
    public final double centerX, centerY, centerZ;
    public int groundWidth, groundDepth;

    private final Map<Long, Block> blockAtPosition = new HashMap<>();

    private final Map<Long, String> reservedForKind = new HashMap<>();
    private final java.util.Set<Long> corridorCells = new java.util.HashSet<>();

    private BuildData(JsonObject root) throws Exception {
        layout(root);
        stepOfMachine = new int[machines.size()];
        stepOfPipe = new int[pipes.size()];
        java.util.Arrays.fill(stepOfMachine, -1);
        java.util.Arrays.fill(stepOfPipe, -1);
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            for (int machine : steps.get(stepIndex).machines()) stepOfMachine[machine] = stepIndex;
            for (int pipe : steps.get(stepIndex).pipes()) stepOfPipe[pipe] = stepIndex;
        }
        for (Machine machine : machines) {
            for (int corner = 0; corner < 8; corner++) {
                boundsPoints.add(new double[] {
                        corner % 2 == 0 ? machine.cornerX() : machine.cornerX() + machine.width(),
                        (corner / 2) % 2 == 0 ? machine.cornerY() : machine.cornerY() + machine.height(),
                        corner / 4 == 0 ? machine.cornerZ() : machine.cornerZ() + machine.depth()});
            }
        }
        for (Pipe pipe : pipes) boundsPoints.addAll(pipe.pathPoints());
        double minX = 1e9, minY = 0, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        for (double[] point : boundsPoints) {
            minX = Math.min(minX, point[0]); maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]); maxY = Math.max(maxY, point[1]);
            minZ = Math.min(minZ, point[2]); maxZ = Math.max(maxZ, point[2]);
        }
        centerX = (minX + maxX) / 2;
        centerY = (minY + maxY) / 2;
        centerZ = (minZ + maxZ) / 2;
    }

    private static JsonObject readJson(String path) throws Exception {
        try (var in = BuildData.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    public static BuildData parse(String pageId) throws Exception {
        return new BuildData(readJson("/data/nuclearsq/compendium/builds/" + pageId + ".json"));
    }

    public static BuildData load(String pageId) {
        try {
            return parse(pageId);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Build scene {} unreadable", pageId, e);
            return null;
        }
    }

    public static List<String[]> nodes(String pageId) {
        List<String[]> pagesAndItems = new ArrayList<>();
        try {
            for (JsonElement element : readJson("/data/nuclearsq/compendium/builds/" + pageId + ".json").getAsJsonArray("nodes")) {
                JsonObject node = element.getAsJsonObject();
                String itemId = node.has("block") ? node.get("block").getAsString() : node.get("machine").getAsString() + "_controller_sqc";
                pagesAndItems.add(new String[] {node.get("page").getAsString(), itemId});
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Build scene {} has no node list", pageId, ex);
        }
        return pagesAndItems;
    }

    public static List<String> linkKinds(String pageId) {
        List<String> kinds = new ArrayList<>();
        try {
            for (JsonElement element : readJson("/data/nuclearsq/compendium/builds/" + pageId + ".json").getAsJsonArray("links")) {
                JsonObject link = element.getAsJsonObject();
                kinds.add(link.has("kind") ? link.get("kind").getAsString() : "pipe");
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Build scene {} has no link list", pageId, ex);
        }
        return kinds;
    }

    private static int[] toInts(JsonArray array) {
        int[] values = new int[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsInt();
        return values;
    }

    public static JsonObject multiblock(String machineId) throws Exception {
        if (machineId.startsWith("block:"))
            return JsonParser.parseString("{\"size\":[1,1,1],\"controller\":[0,0,0],\"ports\":[{\"name\":\"data\",\"type\":\"data\",\"dir\":\"both\",\"cell\":[0,0,0]}]}").getAsJsonObject();
        return readJson("/data/nuclearsq/multiblocks/" + machineId + ".json");
    }

    public static int[] turnCell(int[] cell, int quarterTurns, int width, int depth) {
        int x = cell[0], z = cell[2];
        return switch (quarterTurns) {
            case 1 -> new int[] {depth - 1 - z, cell[1], x};
            case 2 -> new int[] {width - 1 - x, cell[1], depth - 1 - z};
            case 3 -> new int[] {z, cell[1], width - 1 - x};
            default -> new int[] {x, cell[1], z};
        };
    }

    private static int wallOfPort(String machineId, JsonObject multiblock, int[] size, int quarterTurns, String portName) {
        int[] controller = toInts(multiblock.getAsJsonArray("controller"));
        for (JsonElement element : multiblock.getAsJsonArray("ports")) {
            JsonObject port = element.getAsJsonObject();
            if (port.get("name").getAsString().equals(portName))
                return PortFaces.turnWall(PortFaces.wallOfPort(machineId, size, controller, toInts(port.getAsJsonArray("cell"))), quarterTurns);
        }
        throw new IllegalArgumentException("unknown port " + portName);
    }

    private void layout(JsonObject root) throws Exception {
        JsonArray nodeArray = root.getAsJsonArray("nodes"), linkArray = root.getAsJsonArray("links");
        int nodeCount = nodeArray.size(), linkCount = linkArray.size();
        String[] nodeKey = new String[nodeCount], machineId = new String[nodeCount], nodePage = new String[nodeCount];
        int[] column = new int[nodeCount], row = new int[nodeCount], quarterTurns = new int[nodeCount], nodeColor = new int[nodeCount];
        JsonObject[] multiblockOf = new JsonObject[nodeCount];
        int[][] footprintSize = new int[nodeCount][];
        Map<String, Integer> nodeIndexByKey = new HashMap<>();
        for (int node = 0; node < nodeCount; node++) {
            JsonObject json = nodeArray.get(node).getAsJsonObject();
            nodeKey[node] = json.get("key").getAsString();
            machineId[node] = json.has("block") ? "block:" + json.get("block").getAsString() : json.get("machine").getAsString();
            nodePage[node] = json.get("page").getAsString();
            machineMakes.add(json.has("makes") ? json.get("makes").getAsString() : null);
            machineMakesTo.add(json.has("makesTo") ? json.get("makesTo").getAsString() : null);
            column[node] = json.get("col").getAsInt();
            row[node] = json.has("row") ? json.get("row").getAsInt() : 0;
            nodeColor[node] = Integer.parseInt(json.has("color") ? json.get("color").getAsString() : "8A94A0", 16);
            multiblockOf[node] = multiblock(machineId[node]);
            footprintSize[node] = toInts(multiblockOf[node].getAsJsonArray("size"));
            if (nodeIndexByKey.put(nodeKey[node], node) != null) throw new IllegalArgumentException("duplicate node " + nodeKey[node]);
        }
        int[] linkFromNode = new int[linkCount], linkToNode = new int[linkCount], linkColor = new int[linkCount];
        String[] linkFromPort = new String[linkCount], linkToPort = new String[linkCount], linkKey = new String[linkCount], linkKind = new String[linkCount];
        for (int link = 0; link < linkCount; link++) {
            JsonObject json = linkArray.get(link).getAsJsonObject();
            String[] from = json.get("from").getAsString().split("\\."), to = json.get("to").getAsString().split("\\.");
            Integer fromNode = nodeIndexByKey.get(from[0]), toNode = nodeIndexByKey.get(to[0]);
            if (fromNode == null || toNode == null) throw new IllegalArgumentException("link to unknown node: " + json);
            linkFromNode[link] = fromNode;
            linkFromPort[link] = from[1];
            linkToNode[link] = toNode;
            linkToPort[link] = to[1];
            linkColor[link] = Integer.parseInt(json.get("color").getAsString(), 16);
            linkKind[link] = json.has("kind") ? json.get("kind").getAsString() : "pipe";
            if (!LINK_KINDS.contains(linkKind[link])) throw new IllegalArgumentException("unknown link kind " + linkKind[link]);
            linkKey[link] = "l" + link;
            pipeItems.add(json.has("item") ? json.get("item").getAsString() : null);
        }

        for (int node = 0; node < nodeCount; node++) {
            int bestScore = -1;
            for (int turns = 0; turns < 4; turns++) {
                int score = 0;
                for (int link = 0; link < linkCount; link++) {
                    int otherNode = linkFromNode[link] == node ? linkToNode[link] : linkToNode[link] == node ? linkFromNode[link] : -1;
                    if (otherNode < 0) continue;
                    int wantedWall = column[otherNode] > column[node] ? 1 : column[otherNode] < column[node] ? 0 : row[otherNode] > row[node] ? 3 : 2;
                    String port = linkFromNode[link] == node ? linkFromPort[link] : linkToPort[link];
                    if (wallOfPort(machineId[node], multiblockOf[node], footprintSize[node], turns, port) == wantedWall) score++;
                }
                if (score > bestScore) { bestScore = score; quarterTurns[node] = turns; }
            }
        }
        int columnCount = 0, rowCount = 0;
        for (int node = 0; node < nodeCount; node++) { columnCount = Math.max(columnCount, column[node] + 1); rowCount = Math.max(rowCount, row[node] + 1); }
        int[] turnedWidth = new int[nodeCount], turnedDepth = new int[nodeCount], columnWidth = new int[columnCount], rowDepth = new int[rowCount];
        for (int node = 0; node < nodeCount; node++) {
            turnedWidth[node] = quarterTurns[node] % 2 == 0 ? footprintSize[node][0] : footprintSize[node][2];
            turnedDepth[node] = quarterTurns[node] % 2 == 0 ? footprintSize[node][2] : footprintSize[node][0];
            columnWidth[column[node]] = Math.max(columnWidth[column[node]], turnedWidth[node]);
            rowDepth[row[node]] = Math.max(rowDepth[row[node]], turnedDepth[node]);
        }
        double gapBetweenColumns = 7, gapBetweenRows = 6, margin = 3;
        double[] columnStartX = new double[columnCount], rowStartZ = new double[rowCount];
        double nextX = margin, nextZ = margin;
        for (int c = 0; c < columnCount; c++) { columnStartX[c] = nextX; nextX += columnWidth[c] + gapBetweenColumns; }
        for (int r = 0; r < rowCount; r++) { rowStartZ[r] = nextZ; nextZ += rowDepth[r] + gapBetweenRows; }
        groundWidth = (int) Math.ceil(nextX - gapBetweenColumns + margin);
        groundDepth = (int) Math.ceil(nextZ - gapBetweenRows + margin);
        for (int node = 0; node < nodeCount; node++) {
            int width = footprintSize[node][0], height = footprintSize[node][1], depth = footprintSize[node][2];
            Map<String, int[]> portCells = new HashMap<>();
            Map<String, Integer> portWalls = new HashMap<>();
            int[] unturnedController = toInts(multiblockOf[node].getAsJsonArray("controller"));
            for (JsonElement element : multiblockOf[node].getAsJsonArray("ports")) {
                JsonObject port = element.getAsJsonObject();
                int[] unturnedCell = toInts(port.getAsJsonArray("cell"));
                portCells.put(port.get("name").getAsString(), turnCell(unturnedCell, quarterTurns[node], width, depth));
                portWalls.put(port.get("name").getAsString(), PortFaces.turnWall(PortFaces.wallOfPort(machineId[node], footprintSize[node], unturnedController, unturnedCell), quarterTurns[node]));
            }
            int[] controllerCell = turnCell(toInts(multiblockOf[node].getAsJsonArray("controller")), quarterTurns[node], width, depth);
            double cornerX = columnStartX[column[node]] + (columnWidth[column[node]] - turnedWidth[node]) / 2;
            double cornerZ = rowStartZ[row[node]] + (rowDepth[row[node]] - turnedDepth[node]) / 2;
            machines.add(new Machine(nodeKey[node], machineId[node], nodeColor[node], cornerX, 0, cornerZ, turnedWidth[node], height, turnedDepth[node],
                    quarterTurns[node], cornerX + controllerCell[0] + 0.5, controllerCell[1], cornerZ + controllerCell[2] + 0.5, portCells, portWalls, nodePage[node]));
        }
        for (int link = 0; link < linkCount; link++) {
            for (int end = 0; end < 2; end++) {
                Machine machine = machines.get(end == 0 ? linkFromNode[link] : linkToNode[link]);
                String portName = end == 0 ? linkFromPort[link] : linkToPort[link];
                double[] outward = outwardOf(machine.portWalls().get(portName));
                double[] center = portPoints(machine, portName)[1];
                while (true) {
                    int[] block = blockOf(center);
                    long position = positionKey(block[0], block[1], block[2]);
                    String previous = reservedForKind.put(position, linkKind[link]);
                    if (previous != null && !previous.equals(linkKind[link]))
                        throw new IllegalArgumentException("two lines of different kinds end in the same block next to " + machine.key());
                    if (!isInsideMachine(center)) break;
                    corridorCells.add(position);
                    center = new double[] {center[0] + outward[0], center[1] + outward[1], center[2] + outward[2]};
                }
            }
        }
        for (int link = 0; link < linkCount; link++)
            layPipe(link, linkKey[link], linkKind[link], linkColor[link], linkFromNode[link], linkToNode[link], linkFromPort[link], linkToPort[link]);
        boolean[] nodeBuilt = new boolean[nodeCount], linkLaid = new boolean[linkCount];
        for (int node = 0; node < nodeCount; node++) {
            nodeBuilt[node] = true;
            List<Integer> linksOfStep = new ArrayList<>();
            String connectedToPage = null;
            for (int link = 0; link < linkCount; link++) {
                if (linkLaid[link] || !nodeBuilt[linkFromNode[link]] || !nodeBuilt[linkToNode[link]]) continue;
                linkLaid[link] = true;
                linksOfStep.add(link);
                if (connectedToPage == null) connectedToPage = nodePage[linkFromNode[link] == node ? linkToNode[link] : linkFromNode[link]];
            }
            steps.add(new Step(List.of(node), linksOfStep));
            captions.add(new String[] {nodePage[node], connectedToPage, nodeKey[node]});
        }
    }

    private static long positionKey(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
    }

    private void layPipe(int pipeIndex, String key, String kind, int color, int fromMachine, int toMachine, String fromPort, String toPort) {
        double[][] startPort = portPoints(machines.get(fromMachine), fromPort), endPort = portPoints(machines.get(toMachine), toPort);
        double[] firstBlockCenter = startPort[1], lastBlockCenter = endPort[1];
        List<double[]> pathPoints = routeBetween(firstBlockCenter, lastBlockCenter, kind);
        List<int[]> blockPositions = new ArrayList<>();
        walkBlocks(pathPoints, (position) -> blockPositions.add(position));
        List<Block> pipeBlocks = new ArrayList<>();
        for (int indexInPipe = 0; indexInPipe < blockPositions.size(); indexInPipe++) {
            int[] position = blockPositions.get(indexInPipe);
            long key64 = positionKey(position[0], position[1], position[2]);
            Block block = blockAtPosition.get(key64);
            if (block == null) {
                block = new Block(position[0], position[1], position[2], kind, pipeIndex, indexInPipe);
                blockAtPosition.put(key64, block);
                blocks.add(block);
            }
            pipeBlocks.add(block);
        }
        for (int i = 0; i + 1 < pipeBlocks.size(); i++) {
            int[] here = blockPositions.get(i), next = blockPositions.get(i + 1);
            Direction towardsNext = Direction.fromDelta(next[0] - here[0], next[1] - here[1], next[2] - here[2]);
            if (towardsNext == null) continue;
            pipeBlocks.get(i).connections |= 1 << (2 * towardsNext.ordinal());
            pipeBlocks.get(i + 1).connections |= 1 << (2 * towardsNext.getOpposite().ordinal());
        }

        connectToPort(pipeBlocks.get(0), startPort);
        connectToPort(pipeBlocks.get(pipeBlocks.size() - 1), endPort);
        blocksOfPipe.add(pipeBlocks);
        pipes.add(new Pipe(key, kind, color, pathPoints, fromMachine, toMachine, fromPort, toPort, blockPositions.size()));
    }

    private static List<double[]> withoutRepeatedPoints(List<double[]> path) {
        List<double[]> cleaned = new ArrayList<>();
        for (double[] point : path) if (cleaned.isEmpty() || manhattan(cleaned.get(cleaned.size() - 1), point) > 1e-6) cleaned.add(point);
        return cleaned;
    }

    private static void walkBlocks(List<double[]> pathPoints, java.util.function.Consumer<int[]> visitor) {
        for (int i = 0; i < pathPoints.size(); i++) {
            double[] point = pathPoints.get(i);
            if (i == 0) {
                visitor.accept(blockOf(point));
                continue;
            }
            double[] previous = pathPoints.get(i - 1);
            int unitSteps = (int) Math.round(manhattan(point, previous));
            for (int step = 1; step <= unitSteps; step++) {
                double fraction = (double) step / unitSteps;
                visitor.accept(blockOf(new double[] {
                        previous[0] + (point[0] - previous[0]) * fraction,
                        previous[1] + (point[1] - previous[1]) * fraction,
                        previous[2] + (point[2] - previous[2]) * fraction}));
            }
        }
    }

    private static int[] blockOf(double[] center) {
        return new int[] {(int) Math.floor(center[0]), (int) Math.floor(center[1]), (int) Math.floor(center[2])};
    }

    private static void connectToPort(Block block, double[][] portPoints) {
        double[] portCenter = portPoints[0], blockCenter = portPoints[1];
        Direction towardsPort = Direction.fromDelta((int) Math.round(portCenter[0] - blockCenter[0]), (int) Math.round(portCenter[1] - blockCenter[1]),
                (int) Math.round(portCenter[2] - blockCenter[2]));
        if (towardsPort != null) block.connections |= 3 << (2 * towardsPort.ordinal());
    }

    public boolean isPortCorridor(int x, int y, int z) {
        return corridorCells.contains(positionKey(x, y, z));
    }

    public boolean isInsideMachine(double[] point) {
        for (Machine machine : machines) {
            if (point[0] > machine.cornerX() && point[0] < machine.cornerX() + machine.width()
                    && point[1] > machine.cornerY() && point[1] < machine.cornerY() + machine.height()
                    && point[2] > machine.cornerZ() && point[2] < machine.cornerZ() + machine.depth()) return true;
        }
        return false;
    }

    private boolean isFreeFor(int x, int y, int z, String kind) {
        boolean inMachine = isInsideMachine(new double[] {x + 0.5, y + 0.5, z + 0.5}) && !corridorCells.contains(positionKey(x, y, z));
        if (y < 0 || inMachine) return false;
        String reservedKind = reservedForKind.get(positionKey(x, y, z));
        if (reservedKind != null && !reservedKind.equals(kind)) return false;
        Block taken = blockAtPosition.get(positionKey(x, y, z));
        return taken == null || taken.kind.equals(kind);
    }

    private boolean touchesOtherKind(int x, int y, int z, String kind) {
        for (Direction side : Direction.values()) {
            Block next = blockAtPosition.get(positionKey(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()));
            if (next != null && !next.kind.equals(kind)) return true;
        }
        return false;
    }

    private List<double[]> routeBetween(double[] startCenter, double[] endCenter, String kind) {
        int[] start = blockOf(startCenter), end = blockOf(endCenter);
        for (int margin : new int[] {12, 40, 100000}) {
            List<double[]> route = searchRoute(start, end, kind, margin);
            if (route != null) return route;
        }
        com.mojang.logging.LogUtils.getLogger().warn("Build scene: no free route for a {} line from {} to {}", kind, java.util.Arrays.toString(start), java.util.Arrays.toString(end));
        List<double[]> fallback = new ArrayList<>();
        fallback.add(startCenter);
        fallback.add(new double[] {endCenter[0], startCenter[1], startCenter[2]});
        fallback.add(new double[] {endCenter[0], startCenter[1], endCenter[2]});
        fallback.add(endCenter);
        return fallback;
    }

    private List<double[]> searchRoute(int[] start, int[] end, String kind, int margin) {
        int minX = Math.max(-3, Math.min(start[0], end[0]) - margin), maxX = Math.min(groundWidth + 3, Math.max(start[0], end[0]) + margin);
        int minZ = Math.max(-3, Math.min(start[2], end[2]) - margin), maxZ = Math.min(groundDepth + 3, Math.max(start[2], end[2]) + margin);
        int maxY = 8;
        for (Machine machine : machines) maxY = Math.max(maxY, (int) Math.ceil(machine.cornerY() + machine.height()) + 6);
        int sizeX = maxX - minX + 1, sizeY = maxY + 1, sizeZ = maxZ - minZ + 1;
        final int noDirection = 6;
        int stateCount = sizeX * sizeY * sizeZ * (noDirection + 1);
        double[] bestCost = new double[stateCount];
        int[] cameFrom = new int[stateCount];
        java.util.Arrays.fill(bestCost, Double.MAX_VALUE);
        java.util.Arrays.fill(cameFrom, -1);
        java.util.PriorityQueue<double[]> queue = new java.util.PriorityQueue<>(java.util.Comparator.comparingDouble(entry -> entry[0]));
        if (isFreeFor(start[0], start[1], start[2], kind)) {
            int startState = (((start[0] - minX) * sizeY + start[1]) * sizeZ + (start[2] - minZ)) * (noDirection + 1) + noDirection;
            bestCost[startState] = 0;
            queue.add(new double[] {0.5 * (Math.abs(start[0] - end[0]) + Math.abs(start[1] - end[1]) + Math.abs(start[2] - end[2])), startState, 0});
        }
        Direction[] directions = Direction.values();
        while (!queue.isEmpty()) {
            double[] entry = queue.poll();
            int state = (int) entry[1];
            if (entry[2] > bestCost[state]) continue;
            int direction = state % (noDirection + 1), cellIndex = state / (noDirection + 1);
            int z = cellIndex % sizeZ + minZ, y = (cellIndex / sizeZ) % sizeY, x = cellIndex / (sizeZ * sizeY) + minX;
            if (x == end[0] && y == end[1] && z == end[2]) {
                List<int[]> cellsOfPath = new ArrayList<>();
                for (int at = state; at != -1; at = cameFrom[at]) {
                    int index = at / (noDirection + 1);
                    cellsOfPath.add(0, new int[] {index / (sizeZ * sizeY) + minX, (index / sizeZ) % sizeY, index % sizeZ + minZ});
                }
                return cornersOf(cellsOfPath);
            }
            for (int nextDirection = 0; nextDirection < 6; nextDirection++) {
                int nextX = x + directions[nextDirection].getStepX(), nextY = y + directions[nextDirection].getStepY(), nextZ = z + directions[nextDirection].getStepZ();
                if (nextX < minX || nextX > maxX || nextY < 0 || nextY > maxY || nextZ < minZ || nextZ > maxZ) continue;
                if (!isFreeFor(nextX, nextY, nextZ, kind)) continue;
                double stepCost = 1 + 0.08 * nextY;
                if (direction != noDirection && direction != nextDirection) stepCost += 1.5;
                if (blockAtPosition.containsKey(positionKey(nextX, nextY, nextZ))) stepCost -= 0.5;
                if (touchesOtherKind(nextX, nextY, nextZ, kind)) stepCost += 1;
                int nextState = (((nextX - minX) * sizeY + nextY) * sizeZ + (nextZ - minZ)) * (noDirection + 1) + nextDirection;
                if (entry[2] + stepCost < bestCost[nextState]) {
                    bestCost[nextState] = entry[2] + stepCost;
                    cameFrom[nextState] = state;
                    double remaining = 0.5 * (Math.abs(nextX - end[0]) + Math.abs(nextY - end[1]) + Math.abs(nextZ - end[2]));
                    queue.add(new double[] {bestCost[nextState] + remaining, nextState, bestCost[nextState]});
                }
            }
        }
        return null;
    }

    private static List<double[]> cornersOf(List<int[]> cellsOfPath) {
        List<double[]> corners = new ArrayList<>();
        for (int i = 0; i < cellsOfPath.size(); i++) {
            int[] cell = cellsOfPath.get(i);
            boolean endOfPath = i == 0 || i == cellsOfPath.size() - 1;
            boolean turns = !endOfPath && (cellsOfPath.get(i + 1)[0] - cell[0] != cell[0] - cellsOfPath.get(i - 1)[0]
                    || cellsOfPath.get(i + 1)[1] - cell[1] != cell[1] - cellsOfPath.get(i - 1)[1]
                    || cellsOfPath.get(i + 1)[2] - cell[2] != cell[2] - cellsOfPath.get(i - 1)[2]);
            if (endOfPath || turns) corners.add(new double[] {cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5});
        }
        return corners;
    }

    private static double manhattan(double[] from, double[] to) {
        return Math.abs(from[0] - to[0]) + Math.abs(from[1] - to[1]) + Math.abs(from[2] - to[2]);
    }

    public static double[][] portPoints(Machine machine, String portName) {
        int[] cell = machine.portCells().get(portName);
        if (cell == null) throw new IllegalArgumentException("unknown port " + portName + " of " + machine.key());
        double[] outward = outwardOf(machine.portWalls().get(portName));
        double[] portCenter = {machine.cornerX() + cell[0] + 0.5, machine.cornerY() + cell[1] + 0.5, machine.cornerZ() + cell[2] + 0.5};
        return new double[][] {portCenter, {portCenter[0] + outward[0], portCenter[1] + outward[1], portCenter[2] + outward[2]}};
    }

    public static double[] outwardOf(int wall) {
        return switch (wall) {
            case PortFaces.MINUS_X -> new double[] {-1, 0, 0};
            case PortFaces.PLUS_X -> new double[] {1, 0, 0};
            case PortFaces.MINUS_Z -> new double[] {0, 0, -1};
            case PortFaces.PLUS_Z -> new double[] {0, 0, 1};
            default -> new double[] {0, 1, 0};
        };
    }
}
