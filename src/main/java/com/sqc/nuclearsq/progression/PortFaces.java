package com.sqc.nuclearsq.progression;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class PortFaces {
    public static final int MINUS_X = 0, PLUS_X = 1, MINUS_Z = 2, PLUS_Z = 3, TOP = 4;

    private record Box(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {}

    private static final Map<String, List<Box>> MODEL_BOXES = new HashMap<>();

    private PortFaces() {}

    private static List<Box> modelBoxes(String machineId) {
        return MODEL_BOXES.computeIfAbsent(machineId, id -> {
            List<Box> boxes = new ArrayList<>();
            try (var in = PortFaces.class.getResourceAsStream("/assets/nuclearsq/geo/" + id + ".geo.json")) {
                if (in == null) return boxes;
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement geometry : root.getAsJsonArray("minecraft:geometry")) {
                    for (JsonElement boneElement : geometry.getAsJsonObject().getAsJsonArray("bones")) {
                        JsonObject bone = boneElement.getAsJsonObject();
                        if (!bone.has("cubes") || bone.has("rotation")) continue;
                        for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                            JsonObject cube = cubeElement.getAsJsonObject();
                            if (cube.has("rotation")) continue;
                            var origin = cube.getAsJsonArray("origin");
                            var size = cube.getAsJsonArray("size");
                            double originX = origin.get(0).getAsDouble(), originY = origin.get(1).getAsDouble(), originZ = origin.get(2).getAsDouble();
                            double sizeX = size.get(0).getAsDouble(), sizeY = size.get(1).getAsDouble(), sizeZ = size.get(2).getAsDouble();
                            boxes.add(new Box(-(originX + sizeX) / 16, -originX / 16, originY / 16, (originY + sizeY) / 16, originZ / 16, (originZ + sizeZ) / 16));
                        }
                    }
                }
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().warn("Model of {} not readable for its ports", id, ex);
            }
            return boxes;
        });
    }

    public static int nearestWall(int[] size, int[] cell) {
        int[] distanceToWall = {cell[0], size[0] - 1 - cell[0], cell[2], size[2] - 1 - cell[2]};
        int nearest = 0;
        for (int wall = 1; wall < 4; wall++) if (distanceToWall[wall] < distanceToWall[nearest]) nearest = wall;
        return cell[1] == size[1] - 1 && distanceToWall[nearest] > 0 ? TOP : nearest;
    }

    private static double[] wallPoint(int[] controller, int[] cell, int wall) {
        double x = cell[0] - controller[0], y = cell[1] - controller[1] + 0.5, z = cell[2] - controller[2];
        switch (wall) {
            case MINUS_X -> x -= 0.5;
            case PLUS_X -> x += 0.5;
            case MINUS_Z -> z -= 0.5;
            case PLUS_Z -> z += 0.5;
            default -> y += 0.5;
        }
        return new double[] {x, y, z};
    }

    public static double distanceToModel(String machineId, int[] size, int[] controller, int[] cell, int wall) {
        double[] point = wallPoint(controller, cell, wall);
        double nearest = Double.MAX_VALUE;
        for (Box box : modelBoxes(machineId)) {
            double dx = Math.max(Math.max(box.minX() - point[0], 0), point[0] - box.maxX());
            double dy = Math.max(Math.max(box.minY() - point[1], 0), point[1] - box.maxY());
            double dz = Math.max(Math.max(box.minZ() - point[2], 0), point[2] - box.maxZ());
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return nearest;
    }

    public static boolean hasModel(String machineId) {
        return !modelBoxes(machineId).isEmpty();
    }

    public static List<Integer> platesOnWalls(String machineId, int[] size, int[] controller, int[] cell) {
        List<Integer> walls = new ArrayList<>();
        for (int candidate = MINUS_X; candidate <= TOP; candidate++) {
            if (candidate == TOP && cell[1] != size[1] - 1) continue;
            if (allowedWall(size, cell, candidate) && plateAt(machineId, controller, cell, candidate)) walls.add(candidate);
        }
        return walls;
    }

    public static boolean plateAt(String machineId, int[] controller, int[] cell, int wall) {
        return plateTouches(machineId, wallPoint(controller, cell, wall), wall);
    }

    private static boolean plateTouches(String machineId, double[] point, int wall) {
        for (Box box : modelBoxes(machineId)) {
            double dx = Math.max(Math.max(box.minX() - point[0], 0), point[0] - box.maxX());
            double dy = Math.max(Math.max(box.minY() - point[1], 0), point[1] - box.maxY());
            double dz = Math.max(Math.max(box.minZ() - point[2], 0), point[2] - box.maxZ());
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 0.15) continue;
            double sizeX = box.maxX() - box.minX(), sizeY = box.maxY() - box.minY(), sizeZ = box.maxZ() - box.minZ();
            boolean alongX = wall == MINUS_X || wall == PLUS_X, alongZ = wall == MINUS_Z || wall == PLUS_Z;
            double thickness = alongX ? sizeX : alongZ ? sizeZ : sizeY;
            double tangentA = alongX ? sizeY : alongZ ? sizeX : sizeX, tangentB = alongX ? sizeZ : alongZ ? sizeY : sizeZ;
            if (thickness <= 0.35 && tangentA >= 0.3 && tangentA <= 1.1 && tangentB >= 0.3 && tangentB <= 1.1) return true;
        }
        return false;
    }

    private static boolean onBoundary(int[] size, int[] cell, int wall) {
        return switch (wall) {
            case MINUS_X -> cell[0] == 0;
            case PLUS_X -> cell[0] == size[0] - 1;
            case MINUS_Z -> cell[2] == 0;
            case PLUS_Z -> cell[2] == size[2] - 1;
            default -> cell[1] == size[1] - 1;
        };
    }

    private static boolean allowedWall(int[] size, int[] cell, int wall) {
        boolean cellOnBoundary = false;
        for (int candidate = MINUS_X; candidate <= TOP; candidate++) cellOnBoundary |= onBoundary(size, cell, candidate);
        return !cellOnBoundary || onBoundary(size, cell, wall);
    }

    public static int wallOfPort(String machineId, int[] size, int[] controller, int[] cell) {
        int wall = nearestWall(size, cell);
        if (!hasModel(machineId)) return wall;
        int plateWall = -1;
        double plateDistance = Double.MAX_VALUE;
        for (int candidate = MINUS_X; candidate <= TOP; candidate++) {
            if (candidate == TOP && cell[1] != size[1] - 1) continue;
            if (!allowedWall(size, cell, candidate)) continue;
            if (!plateTouches(machineId, wallPoint(controller, cell, candidate), candidate)) continue;
            double distance = distanceToModel(machineId, size, controller, cell, candidate);
            if (distance < plateDistance) {
                plateDistance = distance;
                plateWall = candidate;
            }
        }
        if (plateWall >= 0) return plateWall;
        double wallDistance = distanceToModel(machineId, size, controller, cell, wall);
        for (int candidate = MINUS_X; candidate <= TOP; candidate++) {
            if (candidate == TOP && cell[1] != size[1] - 1) continue;
            if (!allowedWall(size, cell, candidate)) continue;
            double distance = distanceToModel(machineId, size, controller, cell, candidate);
            if (distance + 0.25 < wallDistance) {
                wall = candidate;
                wallDistance = distance;
            }
        }
        return wall;
    }

    public static int turnWall(int wall, int quarterTurns) {
        if (wall == TOP) return TOP;
        int dx = wall == MINUS_X ? -1 : wall == PLUS_X ? 1 : 0, dz = wall == MINUS_Z ? -1 : wall == PLUS_Z ? 1 : 0;
        for (int turn = 0; turn < ((quarterTurns % 4) + 4) % 4; turn++) {
            int turnedX = -dz, turnedZ = dx;
            dx = turnedX;
            dz = turnedZ;
        }
        return dx < 0 ? MINUS_X : dx > 0 ? PLUS_X : dz < 0 ? MINUS_Z : PLUS_Z;
    }
}
