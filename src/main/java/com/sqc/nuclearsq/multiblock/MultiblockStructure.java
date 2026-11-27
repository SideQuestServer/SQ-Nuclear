package com.sqc.nuclearsq.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class MultiblockStructure {
    public record Cell(BlockPos pos, Block block, char c, Block alt) {
        public boolean ok(net.minecraft.world.level.block.state.BlockState s) {
            return s.is(block) || (alt != Blocks.AIR && s.is(alt));
        }
    }

    public final String id;
    private volatile Layout layout;
    public volatile int size;

    private record Layout(char[][][] g, Map<Character, String> legend, Map<Character, Character> alts, int ax, int ay, int az, int size,
                          Map<Character, Block> blockCache) {
        static Layout of(char[][][] g, Map<Character, String> legend, Map<Character, Character> alts) {
            int fx = 0, fy = 0, fz = 0, n = 0;
            for (int y = 0; y < g.length; y++)
                for (int z = 0; z < g[y].length; z++)
                    for (int x = 0; x < g[y][z].length; x++) {
                        if (g[y][z][x] == '@') { fx = x; fy = y; fz = z; }
                        if (g[y][z][x] != ' ') n++;
                    }
            return new Layout(g, Map.copyOf(legend), Map.copyOf(alts), fx, fy, fz, n, new java.util.concurrent.ConcurrentHashMap<>());
        }
    }

    private MultiblockStructure(String id, char[][][] g, Map<Character, String> legend, Map<Character, Character> alts) {
        this.id = id;
        set(Layout.of(g, legend, alts));
    }

    private void set(Layout l) {
        layout = l;
        size = l.size();
    }

    public int width() { return layout.g()[0][0].length; }
    public int height() { return layout.g().length; }
    public int depth() { return layout.g()[0].length; }
    public int anchorX() { return layout.ax(); }
    public int anchorY() { return layout.ay(); }
    public int anchorZ() { return layout.az(); }

    public static int rotFor(Direction look) {
        return switch (look) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            default -> 3;
        };
    }

    public static int[] rotate(int dx, int dz, int r) {
        int x = dx;
        int z = dz;
        for (int i = 0; i < ((r % 4) + 4) % 4; i++) {
            int nx = -z;
            int nz = x;
            x = nx;
            z = nz;
        }
        return new int[] {x, z};
    }

    public BlockPos world(BlockPos anchor, int rot, int x, int y, int z) {
        Layout l = layout;
        int[] r = rotate(x - l.ax(), z - l.az(), rot);
        return anchor.offset(r[0], y - l.ay(), r[1]);
    }

    public Block block(char c) {
        return block(layout, c);
    }

    private static Block block(Layout l, char c) {
        return l.blockCache().computeIfAbsent(c, k -> {
            String name = l.legend().get(k);
            if (name == null) return Blocks.AIR;
            return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name));
        });
    }

    public char charAt(int x, int y, int z) {
        char[][][] g = layout.g();
        if (y < 0 || y >= g.length || z < 0 || z >= g[y].length || x < 0 || x >= g[y][z].length) return ' ';
        return g[y][z][x];
    }

    public java.util.Collection<String> blockIds() {
        return layout.legend().values();
    }

    public List<Cell> cells(BlockPos anchor, int rot) {
        Layout l = layout;
        char[][][] g = l.g();
        List<Cell> out = new ArrayList<>(l.size());
        for (int y = 0; y < g.length; y++)
            for (int z = 0; z < g[y].length; z++)
                for (int x = 0; x < g[y][z].length; x++) {
                    char c = g[y][z][x];
                    if (c == ' ') continue;
                    Block b = block(l, c);
                    if (b == Blocks.AIR) continue;
                    Character a = l.alts().get(c);
                    int[] r = rotate(x - l.ax(), z - l.az(), rot);
                    out.add(new Cell(anchor.offset(r[0], y - l.ay(), r[1]), b, c, a == null ? Blocks.AIR : block(l, a)));
                }
        return out;
    }

    public int matched(Level level, BlockPos anchor, int rot) {
        Layout l = layout;
        char[][][] g = l.g();
        int n = 0, r = ((rot % 4) + 4) % 4;
        char last = ' ';
        Block b = Blocks.AIR, alt = Blocks.AIR;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 0; y < g.length; y++)
            for (int z = 0; z < g[y].length; z++)
                for (int x = 0; x < g[y][z].length; x++) {
                    char c = g[y][z][x];
                    if (c == ' ') continue;
                    if (c != last) {
                        last = c;
                        b = block(l, c);
                        Character a = l.alts().get(c);
                        alt = a == null ? Blocks.AIR : block(l, a);
                    }
                    if (b == Blocks.AIR) continue;
                    int dx = x - l.ax(), dz = z - l.az();
                    var s = level.getBlockState(p.set(anchor.getX() + (r == 0 ? dx : r == 1 ? -dz : r == 2 ? -dx : dz), anchor.getY() + y - l.ay(),
                            anchor.getZ() + (r == 0 ? dz : r == 1 ? dx : r == 2 ? -dz : -dx)));
                    if (s.is(b) || (alt != Blocks.AIR && s.is(alt))) n++;
                }
        return n;
    }

    public int bestRot(Level level, BlockPos anchor) {
        int best = 0, bestN = -1;
        for (int r = 0; r < 4; r++) {
            int n = matched(level, anchor, r);
            if (n > bestN) { bestN = n; best = r; }
        }
        return best;
    }

    public static final Map<String, MultiblockStructure> ALL = new HashMap<>();

    private static final Map<Character, String> LEGEND = Map.of(
            'C', "nuclearsq:pressure_chamber_sqc", 'B', "nuclearsq:turbine_basic_sqc",
            'R', "nuclearsq:turbine_brass_rotor_sqc", 'S', "nuclearsq:turbine_steel_blades_sqc",
            'G', "nuclearsq:turbine_glowstone_generator_sqc", 'P', "nuclearsq:pressure_chamber_reinforced_sqc",
            '@', "nuclearsq:turbine_controller_sqc", 'E', "nuclearsq:turbine_port_energy_sqc",
            'V', "nuclearsq:turbine_port_steam_sqc", 'W', "nuclearsq:turbine_port_water_sqc");

    private static final Map<Character, String> LEGEND_DATA = with(LEGEND, 'T', "nuclearsq:machine_port_terminal_sqc");

    private static Map<Character, String> with(Map<Character, String> base, char c, String id) {
        Map<Character, String> m = new HashMap<>(base);
        m.put(c, id);
        return Map.copyOf(m);
    }

    public static final MultiblockStructure TURBINE = build("turbine", 19, 9, 42, LEGEND_DATA,
            Map.of('E', 'B', 'V', 'P', 'W', 'C', 'T', 'B'),
            (g, w, h, d) -> {
                for (int z = 0; z <= 27; z++)
                    for (int x = 3; x <= 15; x++) g[0][z][x] = 'B';
                for (int z = 28; z <= 41; z++)
                    for (int x : new int[] {3, 4, 5, 13, 14, 15}) g[0][z][x] = 'C';
                for (int z = 2; z <= 25; z++) g[4][z][9] = 'R';
                for (int z : new int[] {2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23})
                    for (int y = 4; y <= 6; y++)
                        for (int x = 8; x <= 10; x++) if (x != 9 || y != 4) g[y][z][x] = 'S';
                for (int z : new int[] {5, 9, 13, 17, 21, 25})
                    for (int y = 4; y <= 5; y++)
                        for (int x = 8; x <= 10; x++) if (x != 9 || y != 4) g[y][z][x] = 'G';
                g[1][0][9] = '@';
                g[com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.DATA_Y][com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.DATA_Z][com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity.DATA_X] = 'T';
                for (com.sqc.nuclearsq.turbine.TurbinePortType t : com.sqc.nuclearsq.turbine.TurbinePortType.values())
                    g[t.y][t.z][t.x] = t.c;
            });

    public static final MultiblockStructure TURBINE_LEGACY = build("turbine_legacy", 7, 7, 16, LEGEND,
            Map.of('E', 'B', 'V', 'P', 'W', 'C'),
            (g, w, h, d) -> {
                for (int z = 0; z < 10; z++)
                    for (int x = 1; x <= 5; x++) g[0][z][x] = 'B';
                for (int z = 0; z <= 2; z++) { ring(g, z, 2, 'B'); ring(g, z, 1, 'G'); g[3][z][3] = 'R'; }
                ring(g, 3, 2, 'C'); g[3][3][3] = 'R';
                for (int z = 4; z <= 8; z++) {
                    ring(g, z, 2, 'C');
                    g[3][z][3] = 'R';
                    if (z == 5 || z == 7) ring(g, z, 1, 'S');
                }
                ring(g, 9, 3, 'B'); ring(g, 9, 2, 'C'); g[3][9][3] = 'R';
                for (int z = 10; z <= 14; z++) {
                    ring(g, z, 3, 'C');
                    g[3][z][3] = 'R';
                    if (z == 11 || z == 13) { ring(g, z, 1, 'S'); ring(g, z, 2, 'S'); }
                }
                ring(g, 15, 3, 'B'); g[3][15][3] = 'R';
                for (int z = 5; z <= 7; z++) g[6][z][3] = 'P';
                g[1][0][3] = '@';
                g[1][0][4] = 'E';
                g[6][6][3] = 'V';
                g[0][12][1] = 'W';
            });

    private static void ring(char[][][] g, int z, int r, char c) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                if (r >= 2 && Math.abs(dx) == r && Math.abs(dy) == r) continue;
                g[3 + dy][z][3 + dx] = c;
            }
    }

    public static MultiblockStructure load(String id) {
        String res = "/data/nuclearsq/multiblocks/" + id + ".json";
        try (java.io.InputStream in = MultiblockStructure.class.getResourceAsStream(res)) {
            if (in == null) return null;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Data data = Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, o).getOrThrow();
            return build(id, data.w(), data.h(), data.d(), data.legendMap(), Map.of(), data::fill);
        } catch (java.io.IOException | RuntimeException e) {
            throw new IllegalStateException("Blockmuster " + res + " fehlerhaft: " + e.getMessage(), e);
        }
    }

    public record Data(List<Integer> size, List<Integer> anchor, Map<String, String> legend, List<List<String>> layers) {
        public static final com.mojang.serialization.Codec<Data> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                        com.mojang.serialization.Codec.intRange(1, 256).listOf(3, 3).fieldOf("size").forGetter(Data::size),
                        com.mojang.serialization.Codec.intRange(0, 255).listOf(3, 3).optionalFieldOf("anchor", List.of()).forGetter(Data::anchor),
                        com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.string(1, 1), com.mojang.serialization.Codec.STRING)
                                .fieldOf("legend").forGetter(Data::legend),
                        com.mojang.serialization.Codec.STRING.listOf().listOf().fieldOf("layers").forGetter(Data::layers)
                ).apply(i, Data::new));

        int w() { return size.get(0); }
        int h() { return size.get(1); }
        int d() { return size.get(2); }

        Map<Character, String> legendMap() {
            Map<Character, String> m = new HashMap<>();
            for (var e : legend.entrySet()) m.put(e.getKey().charAt(0), e.getValue());
            return m;
        }

        void fill(char[][][] g, int ww, int hh, int dd) {
            for (int y = 0; y < Math.min(hh, layers.size()); y++) {
                List<String> rows = layers.get(y);
                for (int z = 0; z < Math.min(dd, rows.size()); z++) {
                    String row = rows.get(z);
                    for (int x = 0; x < Math.min(ww, row.length()); x++) g[y][z][x] = row.charAt(x);
                }
            }
        }
    }

    public List<String> override(Data data, boolean apply) {
        List<String> errors = new ArrayList<>();
        Layout base = builtIn;
        if (data.w() != base.g()[0][0].length || data.h() != base.g().length || data.d() != base.g()[0].length) {
            errors.add("size " + data.size() + " passt nicht zum eingebauten Muster [" + base.g()[0][0].length + ", "
                    + base.g().length + ", " + base.g()[0].length + "]");
            return errors;
        }
        Map<Character, String> legend = data.legendMap();
        for (var e : legend.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getValue());
            if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
                errors.add("legend '" + e.getKey() + "': unbekannter Block " + e.getValue());
            }
        }
        if (!java.util.Objects.equals(legend.get('@'), base.legend().get('@'))) {
            errors.add("legend '@' muss " + base.legend().get('@') + " sein");
        }
        char[][][] g = new char[data.h()][data.d()][data.w()];
        for (char[][] layer : g) for (char[] row : layer) java.util.Arrays.fill(row, ' ');
        data.fill(g, data.w(), data.h(), data.d());
        java.util.Set<Character> unknown = new java.util.TreeSet<>();
        for (char[][] layer : g) for (char[] row : layer) for (char c : row) if (c != ' ' && !legend.containsKey(c)) unknown.add(c);
        if (!unknown.isEmpty()) errors.add("Zeichen ohne Legende: " + unknown);
        Layout l = Layout.of(g, legend, base.alts());
        if (l.ax() != base.ax() || l.ay() != base.ay() || l.az() != base.az()) {
            errors.add("Controller '@' an [" + l.ax() + ", " + l.ay() + ", " + l.az() + "], erwartet [" + base.ax() + ", " + base.ay()
                    + ", " + base.az() + "]");
        }
        if (errors.isEmpty() && apply) set(l);
        return errors;
    }

    public void reset() {
        set(builtIn);
    }

    private Layout builtIn;

    private interface Filler { void fill(char[][][] g, int w, int h, int d); }

    private static MultiblockStructure build(String id, int w, int h, int d, Map<Character, String> legend,
                                             Map<Character, Character> alts, Filler f) {
        char[][][] g = new char[h][d][w];
        for (char[][] layer : g) for (char[] row : layer) java.util.Arrays.fill(row, ' ');
        f.fill(g, w, h, d);
        MultiblockStructure s = new MultiblockStructure(id, g, legend, alts);
        s.builtIn = s.layout;
        ALL.put(id, s);
        return s;
    }
}
