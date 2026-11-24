package com.sqc.nuclearsq.progression.client;

import com.sqc.nuclearsq.client.GuiKit;
import com.sqc.nuclearsq.progression.CompendiumSearch;
import com.sqc.nuclearsq.progression.Guide;
import com.sqc.nuclearsq.progression.GuideProgress;
import com.sqc.nuclearsq.progression.RecipeSources;
import com.sqc.nuclearsq.progression.TextMarks;
import com.sqc.nuclearsq.registry.AssemblyRegistry;
import com.sqc.nuclearsq.registry.CatalogRegistry;
import com.sqc.nuclearsq.registry.ModFluids;
import com.sqc.nuclearsq.registry.MultiblockRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqc.nuclearsq.registry.ReactorRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class ProgressionScreen extends Screen {
    private enum Anim { NONE, WELCOME, ROD, ENERGY, HEAT, BOOM, MACHINE, TREE, CATALOG, ROADMAP, PANEL, LOOP, PRIMARY, PRIMARY_EXT, PRIMARY_FULL, FLUIDS, PLANT, ASSEMBLY, ACHIEVE, WEAPONS, GUIDE, LINE, LINES, SCOPE, BUILD, EXAMPLES }

    private static boolean isLoop(Anim a) { return a == Anim.LOOP || a == Anim.PRIMARY || a == Anim.PRIMARY_EXT || a == Anim.PRIMARY_FULL; }
    private static boolean isNet(Anim a) { return a == Anim.PLANT || a == Anim.ASSEMBLY || a == Anim.WEAPONS || a == Anim.LINE; }
    private static boolean zoomable(Anim a) { return isNet(a) || isLoop(a) || a == Anim.TREE || a == Anim.SCOPE; }
    private boolean zoomNow() { return !recipeOf.isEmpty() || zoomable(current().anim()); }

    private static int animH(Anim a) {
        return switch (a) {
            case MACHINE -> 44;
            case HEAT -> 56;
            case ENERGY, ROADMAP -> 76;
            case BOOM -> 80;
            default -> ANIM_H;
        };
    }

    private record Page(String id, Anim anim) {}

    private record Chapter(String id, Supplier<ItemStack> icon, List<Page> pages, boolean flat) {}

    private static Page pg(String id, Anim a) { return new Page(id, a); }

    private record Section(String id, Supplier<ItemStack> icon, List<Chapter> chapters) {}

    private static ItemStack modItem(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, id)));
    }

    private static final java.util.Map<String, Supplier<ItemStack>> ICONS = new java.util.HashMap<>();

    static {
        ICONS.put("grp_start", () -> new ItemStack(Items.BOOK));
        ICONS.put("grp_reactor", () -> new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()));
        ICONS.put("grp_chem", () -> modItem("chemical_lab_controller_sqc"));
        ICONS.put("grp_energy", () -> new ItemStack(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get()));
        ICONS.put("grp_manufacturing", () -> modItem("small_parts_assembler_controller_sqc"));
        ICONS.put("grp_robotic", () -> modItem("robot_arm_sqc"));
        ICONS.put("grp_computing", () -> new ItemStack(com.sqc.nuclearsq.data.DataRegistry.TERMINAL.get()));
        ICONS.put("examples", () -> new ItemStack(Items.WRITABLE_BOOK));
        ICONS.put("examples_overview", () -> new ItemStack(Items.BOOK));
        ICONS.put("examples_manufacturing", () -> modItem("small_parts_assembler_controller_sqc"));
        ICONS.put("examples_chemistry", () -> modItem("chemical_lab_controller_sqc"));
        ICONS.put("examples_robotics", () -> modItem("robot_arm_sqc"));
        ICONS.put("examples_computing", () -> new ItemStack(com.sqc.nuclearsq.data.DataRegistry.TERMINAL.get()));
        ICONS.put("examples_energy", () -> new ItemStack(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get()));
        ICONS.put("examples_complete_base", () -> modItem("substation_controller_sqc"));
        ICONS.put("examples_reactor", () -> new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()));
        ICONS.put("intro", () -> new ItemStack(Items.BOOK));
        ICONS.put("guide", () -> new ItemStack(Items.WRITABLE_BOOK));
        ICONS.put("assembly", () -> modItem("small_parts_assembler_controller_sqc"));
        ICONS.put("modules", () -> modItem("overclock_module_sqc"));
        ICONS.put("rod_basic", () -> new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()));
        ICONS.put("fuel_end", () -> new ItemStack(com.sqc.nuclearsq.registry.SpentFuelRegistry.LARGE_CASTOR_ITEM.get()));
        ICONS.put("reactor", () -> new ItemStack(com.sqc.nuclearsq.registry.BigMachineRegistry.CONTROLLER_ITEMS.get(com.sqc.nuclearsq.machine.big.BigMachine.CONTROL_ROD_DRIVE).get()));
        ICONS.put("safety", () -> new ItemStack(AssemblyRegistry.DETONATOR_ITEM.get()));
        ICONS.put("cooling", () -> new ItemStack(ModFluids.COOLANT.bucket.get()));
        ICONS.put("cooling_plant", () -> modItem("condenser_controller_sqc"));
        ICONS.put("cooling_big", () -> modItem("cooling_tower_large_controller_sqc"));
        ICONS.put("fusion_core", () -> modItem("fusion_reactor_controller_sqc"));
        ICONS.put("chem_uran", () -> modItem("leaching_plant_controller_sqc"));
        ICONS.put("chem_reactor", () -> modItem("boric_acid_plant_controller_sqc"));
        ICONS.put("chem_waste", () -> modItem("reprocessing_plant_controller_sqc"));
        ICONS.put("chem_oil", () -> modItem("oil_derrick_controller_sqc"));
        ICONS.put("renewable", () -> modItem("solar_panel_sqc"));
        ICONS.put("metal", () -> modItem("ore_mill_controller_sqc"));
        ICONS.put("energy", () -> new ItemStack(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get()));
        ICONS.put("efficiency", () -> new ItemStack(com.sqc.nuclearsq.registry.BigMachineRegistry.CONTROLLER_ITEMS.get(com.sqc.nuclearsq.machine.big.BigMachine.REHEATER).get()));
        ICONS.put("transport", () -> new ItemStack(com.sqc.nuclearsq.transport.TransportRegistry.PIPES.get(com.sqc.nuclearsq.transport.TransportTier.REINFORCED).get()));
        ICONS.put("robots", () -> modItem("robot_arm_sqc"));
        ICONS.put("chips", () -> modItem("micro_assembler_controller_sqc"));
        ICONS.put("data", () -> new ItemStack(com.sqc.nuclearsq.data.DataRegistry.TERMINAL.get()));
        ICONS.put("machines", () -> new ItemStack(AssemblyRegistry.ASSEMBLER_ITEM.get()));
        ICONS.put("storage", () -> new ItemStack(com.sqc.nuclearsq.storage.StorageRegistry.TERMINAL.get()));
        ICONS.put("bank", () -> new ItemStack(com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get()));
        ICONS.put("protection", () -> new ItemStack(com.sqc.nuclearsq.radiation.ProtectionRegistry.SUIT.get(0).get(0).get()));
        ICONS.put("achievements", () -> new ItemStack(Items.NETHER_STAR));
        ICONS.put("weapons", () -> modItem("nuclear_bomb_sqc"));
        ICONS.put("the_mod", () -> modItem("uranium_ore_sqc"));
    }

    private static Supplier<ItemStack> icon(String id) {
        Supplier<ItemStack> sup = ICONS.get(id);
        return () -> {
            ItemStack st = sup == null ? ItemStack.EMPTY : sup.get();
            return st.isEmpty() ? new ItemStack(Items.BOOK) : st;
        };
    }

    private static final List<Section> SECTIONS = loadSections();

    private static List<Section> loadSections() {
        try (var in = ProgressionScreen.class.getResourceAsStream("/data/nuclearsq/compendium/kompendium_struktur.json")) {
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            List<Section> out = new ArrayList<>();
            for (var sec : root.getAsJsonArray("sections")) {
                var so = sec.getAsJsonObject();
                List<Chapter> chs = new ArrayList<>();
                for (var ch : so.getAsJsonArray("chapters")) {
                    var co = ch.getAsJsonObject();
                    List<Page> pages = new ArrayList<>();
                    for (var pe : co.getAsJsonArray("pages")) {
                        var po = pe.getAsJsonObject();
                        pages.add(pg(po.get("id").getAsString(), Anim.valueOf(po.get("anim").getAsString())));
                    }
                    String cid = co.get("id").getAsString();
                    chs.add(new Chapter(cid, icon(cid), List.copyOf(pages), co.has("flat") && co.get("flat").getAsBoolean()));
                }
                String sid = so.get("id").getAsString();
                out.add(new Section(sid, icon(sid), List.copyOf(chs)));
            }
            return List.copyOf(out);
        } catch (Exception e) {
            throw new IllegalStateException("kompendium_struktur.json nicht lesbar: " + e, e);
        }
    }

    private static final List<Chapter> CHAPTERS = SECTIONS.stream().flatMap(s -> s.chapters().stream()).toList();

    private static int sectionOf(int c) {
        int n = 0;
        for (int s = 0; s < SECTIONS.size(); s++) {
            n += SECTIONS.get(s).chapters().size();
            if (c < n) return s;
        }
        return SECTIONS.size() - 1;
    }

    private static int firstChapter(int s) {
        int n = 0;
        for (int k = 0; k < s; k++) n += SECTIONS.get(k).chapters().size();
        return n;
    }

    private static final int BG = 0xFF202022, SIDE_BG = 0xFF19191B, EDGE = 0xFF38383C, ACCENT = 0xFFE8A33D, CYAN = 0xFFB4C2FF;
    private static final int TEXT = 0xFFDDDDE0, MUTED = 0xFF8E8E96, PANEL_BG = 0xFF161618, CARD = 0xFF2A2A2D, HOVER_BG = 0xFF28282B, ACTIVE_BG = 0xFF30323C;
    private static final int GREEN = 0xFF46C46A, NEVER = 0xFF5A5A62;

    private static final net.minecraft.resources.ResourceLocation LOGO = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.sqc.nuclearsq.NuclearSQ.MODID, "textures/gui/compendium/logo.png");
    private static final net.minecraft.resources.ResourceLocation LOGO_SMALL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.sqc.nuclearsq.NuclearSQ.MODID, "textures/gui/compendium/logo_small.png");
    private static final int LOGO_W = 512, LOGO_H = 288, LOGO_SMALL_W = 256, LOGO_SMALL_H = 144;
    private static final int SIDE_HEAD = 76;
    public static final int FULL_W = 760, FULL_H = 440;

    private static final int GBAR = 76, SIDEBAR = 136, ANIM_H = 100, CELL = 24, COL_W = 52, ROW_H = 30, LABEL_W = 100, LINE_H = 11;

    private static final java.util.Map<String, List<String>> RECIPE_ITEMS = com.sqc.nuclearsq.progression.CompendiumItems.RECIPE_ITEMS;

    private record Row(String key, List<Supplier<ItemLike>> nodes) {}

    private record Ln(FormattedCharSequence seq, int indent, String mark, int kind) {}

    private record Hit(int x0, int y0, int x1, int y1, int type, int a, int b) {}

    private static final Pattern NUM = Pattern.compile("^(\\d+)\\.\\s+");

    private final List<Row> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int chapter = 0, page = 0, scroll = 0, sideScroll = 0, textTotal = 0, textVisible = 0, headOff = 0;
    private final java.util.ArrayDeque<int[]> history = new java.util.ArrayDeque<>();
    private static final int HISTORY_MAX = 64;
    private final List<com.sqc.nuclearsq.progression.PlantOverview.Node> links = new ArrayList<>();
    private final List<String> linkNames = new ArrayList<>();
    private int linkHover = -1, mouseX, mouseY;
    private boolean sideFollow = true;
    private List<Ln> lines = new ArrayList<>();
    private List<int[]> cards = new ArrayList<>();
    private List<FormattedCharSequence> tipLines = new ArrayList<>();
    private record Ico(int y, int x, ItemStack st, int size) {}
    private List<Ico> icons = new ArrayList<>();
    private final java.util.Set<String> openDrops = new java.util.HashSet<>();
    private ItemStack hover = ItemStack.EMPTY;
    private List<FormattedCharSequence> loopTip = null;
    private long plantStart = 0;
    private float plantZoom = 0, plantPanX, plantPanY, plantFit = 1;
    private boolean plantDrag, plantMoved;
    private final com.sqc.nuclearsq.client.ScrollbarDrag bar = new com.sqc.nuclearsq.client.ScrollbarDrag();
    private double plantDragX, plantDragY;
    private int plantHover = -1;
    private int plantAX, plantAY, plantAW, plantAH;
    private int px, py, pw, ph;
    private net.minecraft.client.gui.components.EditBox search;
    private String query = "";
    private CompendiumSearch searchIdx;
    private final java.util.Map<String, ItemStack> searchStacks = new java.util.HashMap<>();
    private String hitsQuery;
    private final java.util.EnumMap<CompendiumSearch.Filter, List<CompendiumSearch.Found>> hitsCache = new java.util.EnumMap<>(CompendiumSearch.Filter.class);
    private final java.util.Map<String, String> snipCache = new java.util.HashMap<>();
    private CompendiumSearch.Filter searchFilter = CompendiumSearch.Filter.ALL;
    private static final int SEARCH_H = 14, FILTER_H = 28, ITEMS_IN_ALL = 8;

    @SafeVarargs
    private void row(String key, Supplier<ItemLike>... nodes) {
        rows.add(new Row(key, List.of(nodes)));
    }

    private static Supplier<ItemLike> bi(String id) { return () -> CatalogRegistry.BLOCK_ITEM_MAP.get(id).get(); }
    private static Supplier<ItemLike> ii(String id) { return () -> CatalogRegistry.ITEM_MAP.get(id).get(); }
    private static Supplier<ItemLike> reg(String id) {
        return () -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, id));
    }

    private static ItemStack b(String id) {
        var h = CatalogRegistry.BLOCK_ITEM_MAP.get(id);
        return h == null ? modItem(id) : new ItemStack(h.get());
    }

    private static ItemStack i(String id) {
        var h = CatalogRegistry.ITEM_MAP.get(id);
        return h == null ? modItem(id) : new ItemStack(h.get());
    }

    private static String tr(String key) { return Language.getInstance().getOrDefault(key); }

    private static boolean has(String key) { return Language.getInstance().has(key); }

    private String startQuery;

    private int[] lastPage = java.util.Objects.requireNonNullElse(findPage(com.sqc.nuclearsq.NuclearClientConfig.compendiumLast()), findPage("intro_welcome"));

    @Override
    public void removed() {
        com.sqc.nuclearsq.NuclearClientConfig.setCompendiumLast(current().id());
    }

    public ProgressionScreen(String startQuery) {
        this();
        this.startQuery = startQuery;
    }

    public ProgressionScreen() {
        super(Component.translatable("gui.nuclearsq.tree.title"));
        row("materials", ii("zirconium_dust_sqc"), ii("zirconium_ingot_sqc"), ii("zircaloy_sheet_sqc"), ii("reactor_plate_sqc"));
        row("hull", () -> ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get(), () -> ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get(),
                () -> ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get(), () -> ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get());
        row("docker", () -> ReactorRegistry.FUEL_ROD_HEAD_ITEM.get(), () -> ReactorRegistry.FUEL_ROD_HEAD_REINFORCED_ITEM.get(),
                () -> ReactorRegistry.FUEL_ROD_HEAD_HARDENED_ITEM.get(), () -> ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL_ITEM.get());
        row("cooling", bi("vent_grille_sqc"), bi("heat_exchanger_sqc"), bi("active_cooler_sqc"), bi("cryo_cooling_core_sqc"));
        row("translator", bi("translator_basic_sqc"), bi("translator_brass_sqc"), bi("translator_composite_sqc"), bi("translator_glowstone_sqc"));
        row("moderator", bi("moderator_coal_sqc"), bi("moderator_carbon_sqc"), bi("moderator_obsidian_sqc"), bi("moderator_fluorite_sqc"), reg("moderator_heavy_water_sqc"));
        row("pump", bi("pump_basic_sqc"), bi("pump_electric_sqc"), bi("pump_high_pressure_sqc"), bi("pump_sodium_sqc"));
        row("battery", reg("battery_small_kit_sqc"), reg("battery_medium_kit_sqc"), reg("battery_large_kit_sqc"));
        row("turbine", bi("turbine_basic_sqc"), bi("turbine_brass_rotor_sqc"), bi("turbine_steel_blades_sqc"), bi("turbine_glowstone_generator_sqc"));
        row("pressure", bi("pressure_chamber_sqc"), bi("pressure_chamber_reinforced_sqc"), bi("pressure_chamber_hardened_sqc"), bi("pressure_chamber_experimental_sqc"));
        row("tower", bi("cooling_tower_segment_sqc"), bi("cooling_tower_plated_sqc"), bi("cooling_tower_hardened_sqc"), bi("cooling_tower_closed_sqc"));
        row("board", ii("control_board_1_sqc"), ii("control_board_2_sqc"), ii("control_board_3_sqc"));
        row("chips", reg("chip_1_sqc"), reg("chip_2_sqc"), reg("chip_3_sqc"));
        row("cpu", reg("cpu_module_1_sqc"), reg("cpu_module_2_sqc"), reg("cpu_module_sqc"), reg("cpu_module_8_sqc"), reg("cpu_module_16_sqc"),
                reg("cpu_module_32_sqc"), reg("cpu_module_64_sqc"), reg("cpu_module_128_sqc"));
        row("ram", reg("ram_module_256mb_sqc"), reg("ram_module_512mb_sqc"), reg("ram_module_1gb_sqc"), reg("ram_module_2gb_sqc"),
                reg("ram_module_4gb_sqc"), reg("ram_module_8gb_sqc"), reg("ram_module_sqc"), reg("ram_module_32gb_sqc"));
        row("cable", reg("cable_basic_sqc"), reg("cable_reinforced_sqc"), reg("cable_hardened_sqc"), reg("cable_superconductor_sqc"));
        row("pipe", reg("pipe_basic_sqc"), reg("pipe_reinforced_sqc"), reg("pipe_hardened_sqc"));
        row("suit", reg("hazmat_1_chestplate_sqc"), reg("hazmat_2_chestplate_sqc"), reg("hazmat_3_chestplate_sqc"));
        row("drive", reg("hard_drive_1_sqc"), reg("hard_drive_2_sqc"), reg("hard_drive_3_sqc"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 8, FULL_W);
        ph = Math.min(height - 8, FULL_H);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        buildLinks();
        buildSearch();
        String old = search != null ? search.getValue() : "";
        search = new net.minecraft.client.gui.components.EditBox(font, contentX() + contentW() - searchW(), py + 5, searchW(), SEARCH_H, Component.translatable("compendium.search.hint"));
        search.setMaxLength(40);
        search.setHint(Component.translatable("compendium.search.hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(old);
        search.setResponder(v -> {
            query = v.trim().toLowerCase(java.util.Locale.ROOT);
            sideScroll = 0;
            if (current().anim() == Anim.CATALOG || current().anim() == Anim.ACHIEVE) scroll = 0;
        });
        addRenderableWidget(search);
        if (startQuery != null) {
            search.setValue(startQuery);
            startQuery = null;
        }
        if (lastPage != null) {
            show(lastPage[0], lastPage[1]);
            lastPage = null;
        } else layoutText();
    }

    static String fold(String s) { return CompendiumSearch.fold(s); }

    private List<String> terms() {
        if (query.isEmpty()) return List.of();
        return java.util.Arrays.stream(fold(query).split("\\s+")).filter(t -> !t.isEmpty()).toList();
    }

    private static boolean hasAll(String folded, List<String> terms) {
        for (String t : terms) if (!folded.contains(t)) return false;
        return true;
    }

    private static String langOrNull(String key) { return has(key) ? tr(key) : null; }

    private void buildSearch() {
        searchIdx = null;
        hitsQuery = null;
    }

    private CompendiumSearch searchIndex() {
        if (searchIdx != null) return searchIdx;
        List<CompendiumSearch.Entry> all = new ArrayList<>();
        for (Section sec : SECTIONS) for (Chapter ch : sec.chapters()) for (Page p : ch.pages())
            all.add(CompendiumSearch.page(p.id(), sec.id(), ch.id(), ProgressionScreen::langOrNull));
        searchStacks.clear();
        for (var nd : com.sqc.nuclearsq.progression.AchievementTree.NODES) {
            Item it = TextMarks.item(nd.item());
            searchStacks.put("m:" + nd.key(), it == null ? new ItemStack(Items.BOOK) : new ItemStack(it));
        }
        all.addAll(CompendiumSearch.machines(ProgressionScreen::langOrNull));
        if (catalogGroups == null) buildCatalog();
        for (Group grp : catalogGroups) for (ItemStack st : grp.items()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (searchStacks.putIfAbsent("i:" + path, st) == null) all.add(CompendiumSearch.item(path, st.getHoverName().getString()));
        }
        searchIdx = new CompendiumSearch(all);
        return searchIdx;
    }

    private List<CompendiumSearch.Found> searchHits(CompendiumSearch.Filter f) {
        if (!query.equals(hitsQuery)) {
            hitsCache.clear();
            snipCache.clear();
            hitsQuery = query;
        }
        return hitsCache.computeIfAbsent(f, k -> searchIndex().find(query, k));
    }

    private String searchSnippet(CompendiumSearch.Entry e) {
        return snipCache.computeIfAbsent(e.kind() + ":" + e.id(), k -> CompendiumSearch.snippet(e.text(), terms(), 6, 48));
    }

    private boolean matches(ItemStack st) {
        if (query.isEmpty()) return true;
        List<String> terms = terms();
        if (hasAll(fold(st.getHoverName().getString()), terms)) return true;
        return hasAll(fold(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()), terms);
    }

    private boolean matches(com.sqc.nuclearsq.progression.PlantOverview.Node nd) {
        if (query.isEmpty()) return true;
        String base = "compendium.plant." + nd.key();
        return hasAll(fold(tr(base + ".name") + " " + tr(base + ".short")), terms());
    }

    private int machineMatches() {
        int n = 0;
        for (var nd : com.sqc.nuclearsq.progression.AchievementTree.NODES) if (matches(nd)) n++;
        return n;
    }

    private int catalogMatches() {
        if (catalogGroups == null) buildCatalog();
        int n = 0;
        for (Group grp : catalogGroups) for (ItemStack st : grp.items()) if (matches(st)) n++;
        return n;
    }

    private static int[] machinesPage() { return findPage("ach_overview"); }

    private static int[] catalogPage() { return findPage("catalog_all"); }

    private Page current() { return CHAPTERS.get(chapter).pages().get(page); }

    private int searchW() { return Math.min(150, contentW() / 3); }

    private void drawPath(GuiGraphics g, int mx, int my, int x, int w) {
        int y = py + 5;
        boolean hb = mx >= x && mx < x + 16 && my >= y && my < y + 14;
        rrect(g, x, y, x + 16, y + 14, hb ? ACTIVE_BG : CARD);
        g.drawCenteredString(font, "‹", x + 8, y + 3, hb ? 0xFFFFFFFF : TEXT);
        hits.add(new Hit(x, y, x + 16, y + 14, 1, 0, 0));
        int s = sectionOf(chapter), cx = x + 24, end = x + w;
        List<String> names = new ArrayList<>();
        List<int[]> targets = new ArrayList<>();
        if (SECTIONS.get(s).chapters().size() > 1) { names.add(tr("compendium.section." + SECTIONS.get(s).id())); targets.add(new int[] {firstChapter(s), 0}); }
        names.add(tr("compendium." + CHAPTERS.get(chapter).id() + ".chapter"));
        targets.add(new int[] {chapter, 0});
        for (int i = 0; i < names.size() && cx < end; i++) {
            if (i > 0) {
                g.drawString(font, "›", cx, y + 3, MUTED, false);
                cx += font.width("›") + 5;
            }
            String n = font.plainSubstrByWidth(names.get(i), Math.max(0, end - cx));
            int nw = font.width(n);
            boolean link = targets.get(i) != null, hv = link && mx >= cx && mx < cx + nw && my >= y && my < y + 14;
            g.drawString(font, n, cx, y + 3, !link ? 0xFFFFFFFF : hv ? ACCENT : MUTED, false);
            if (link) hits.add(new Hit(cx, y, cx + nw, y + 14, 0, targets.get(i)[0], targets.get(i)[1]));
            cx += nw + 5;
        }
    }

    private void go(int c, int p) {
        if (c != chapter || p != page) {
            history.push(new int[] {chapter, page});
            while (history.size() > HISTORY_MAX) history.removeLast();
        }
        show(c, p);
    }

    private void back() {
        if (history.isEmpty()) {
            int c = chapter, p = page - 1;
            if (p < 0) { c = (c + CHAPTERS.size() - 1) % CHAPTERS.size(); p = CHAPTERS.get(c).pages().size() - 1; }
            show(c, p);
        } else {
            int[] last = history.pop();
            show(last[0], last[1]);
        }
    }

    private void show(int c, int p) {
        recipeOf = ItemStack.EMPTY;
        pick = null;
        tab = 0;
        show3d = false;
        chapter = c;
        page = p;
        scroll = 0;
        sideFollow = true;
        achFollow = current().anim() == Anim.ACHIEVE;
        guideFollow = current().anim() == Anim.GUIDE;
        if (zoomable(current().anim())) {
            plantStart = System.currentTimeMillis();
            plantZoom = 0;
            scopeReplay = false;
            scopePin = -1;
        }
        layoutText();
    }

    private void turn(int d) {
        int c = chapter, p = page + d;
        if (p < 0) { c = (c + CHAPTERS.size() - 1) % CHAPTERS.size(); p = CHAPTERS.get(c).pages().size() - 1; }
        else if (p >= CHAPTERS.get(c).pages().size()) { c = (c + 1) % CHAPTERS.size(); p = 0; }
        go(c, p);
    }

    private int contentX() { return px + GBAR + SIDEBAR + 14; }
    private int contentW() { return pw - GBAR - SIDEBAR - 28; }

    private MutableComponent rich(String s) { return rich(s, false); }

    private MutableComponent rich(String s, boolean linked) { return TextMarks.parse(s, linked ? this::linkFor : null, LINK); }

    private static final String LINK = "nsq:";

    private void buildLinks() {
        links.clear();
        linkNames.clear();
        List<com.sqc.nuclearsq.progression.PlantOverview.Node> all = new ArrayList<>(com.sqc.nuclearsq.progression.PlantOverview.NODES);
        all.addAll(com.sqc.nuclearsq.progression.AssemblyTree.NODES);
        all.addAll(com.sqc.nuclearsq.progression.AchievementTree.NODES);
        all.addAll(com.sqc.nuclearsq.progression.SecretWeaponsTree.NODES);
        all.add(new com.sqc.nuclearsq.progression.PlantOverview.Node("robot_arm_dock", "robot_arm_sqc", "robot_arm_dock", com.sqc.nuclearsq.progression.PlantOverview.Group.ASSEMBLY, 0, 0));
        for (var nd : all) {
            if (findPage(nd.page()) == null) continue;
            String base = "compendium.plant." + nd.key();
            addLink(tr(base + ".name"), nd);
            if (has(base + ".alias")) for (String a : tr(base + ".alias").split(",")) addLink(a, nd);
        }
    }

    private void addLink(String text, com.sqc.nuclearsq.progression.PlantOverview.Node nd) {
        String name = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (name.length() < 4 || linkNames.contains(name)) return;
        int k = 0;
        while (k < linkNames.size() && linkNames.get(k).length() >= name.length()) k++;
        links.add(k, nd);
        linkNames.add(k, name);
    }

    private int linkFor(String part) {
        String t = part.trim().toLowerCase(java.util.Locale.ROOT);
        String here = current().id();
        for (int k = 0; k < links.size(); k++) {
            String n = linkNames.get(k);
            if (!t.startsWith(n) || t.length() - n.length() > 3) continue;
            if (!t.substring(n.length()).chars().allMatch(Character::isLetter)) continue;
            return links.get(k).page().equals(here) ? -1 : k;
        }
        return -1;
    }

    private static int lineH(Ln l) { return l.kind() == 2 ? l.indent() : l.kind() == 1 ? LINE_H + 1 : l.kind() == 4 ? LINE_H + 4 : l.kind() == 5 ? 20 : LINE_H; }

    private int lineY() {
        int t = 0;
        for (Ln l : lines) t += lineH(l);
        return t;
    }

    private boolean inList;

    private void addPara(String para, int w) {
        if (para.isBlank()) return;
        if (para.startsWith("## ")) {
            lines.add(new Ln(null, lines.isEmpty() ? 0 : 6, null, 2));
            for (FormattedCharSequence s : font.split(rich(para.substring(3)).withStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(true)), w - 4)) {
                scanIcons(s, lineY(), 4);
                lines.add(new Ln(s, 0, null, 1));
            }
            lines.add(new Ln(null, 5, "-", 2));
            inList = true;
            return;
        }
        String mark = null, body = para.startsWith("- ") ? para.substring(2) : para;
        int indent = 0, kind = 0;
        Matcher m = NUM.matcher(body);
        if (m.find()) { mark = m.group(1); body = body.substring(m.end()); }
        else if (body.startsWith("Hinweis:") || body.startsWith("Note:") || body.startsWith("Примечание:")) kind = 3;
        else if (inList) { mark = "•"; }
        int y0 = lineY();
        if (kind == 3) lines.add(new Ln(null, 4, null, 2));
        boolean first = true;
        for (FormattedCharSequence s : font.split(rich(body, true), w - 2 - indent)) {
            scanIcons(s, lineY(), 4 + indent);
            lines.add(new Ln(s, indent, first ? mark : null, kind));
            first = false;
        }
        if (kind == 3) {
            lines.add(new Ln(null, 4, null, 2));
            cards.add(new int[] {y0, lineY() - y0, 1});
            lines.add(new Ln(null, 3, null, 2));
        } else lines.add(new Ln(null, mark != null || para.startsWith("- ") ? 4 : 7, null, 2));
    }

    private void scanIcons(FormattedCharSequence s, int y, int x0) {
        int[] x = {x0};
        String[] last = {null};
        s.accept((i, st, cp) -> {
            String ins = st.getInsertion();
            if (ins != null && ins.startsWith(TextMarks.ITEM) && !ins.equals(last[0])) {
                Item it = TextMarks.item(ins.substring(TextMarks.ITEM.length()));
                if (it != null) icons.add(new Ico(y, x[0], new ItemStack(it), 12));
            }
            last[0] = ins;
            x[0] += font.width(FormattedCharSequence.codepoint(cp, st));
            return true;
        });
    }

    private void addBlocks(String[] paras, String id, int w) {
        int drops = 0, skip = 0, infoAt = -1, infoY = 0, rgb = 0;
        for (String para : paras) {
            Matcher m = TextMarks.BLOCK.matcher(para.trim());
            if (!m.matches()) {
                if (skip == 0) addPara(para, w);
                continue;
            }
            String tag = m.group(1), arg = m.group(2) == null ? "" : m.group(2);
            int d = tag.equals("drop") ? drops++ : -1;
            if (skip > 0) {
                skip += d >= 0 ? 1 : tag.equals("/drop") ? -1 : 0;
                continue;
            }
            switch (tag) {
                case "drop" -> {
                    boolean open = openDrops.contains(id + "#" + d);
                    List<FormattedCharSequence> t = font.split(rich(arg), w - 20);
                    FormattedCharSequence s = t.isEmpty() ? FormattedCharSequence.EMPTY : t.get(0);
                    int y0 = lineY();
                    scanIcons(s, y0 + 2, 16);
                    lines.add(new Ln(s, d, open ? "-" : "+", 4));
                    cards.add(new int[] {y0, LINE_H + 4, open ? 3 : 0});
                    lines.add(new Ln(null, 3, null, 2));
                    if (!open) skip = 1;
                }
                case "info" -> {
                    Integer c = TextMarks.color(arg);
                    infoAt = cards.size();
                    infoY = lineY();
                    rgb = c == null ? CYAN & 0xFFFFFF : c;
                }
                case "/info" -> {
                    if (infoAt >= 0 && lineY() - infoY > 3) {
                        cards.subList(infoAt, cards.size()).clear();
                        cards.add(new int[] {infoY, lineY() - infoY - 3, 2, rgb});
                    }
                    infoAt = -1;
                }
                case "recipe" -> {
                    Item it = TextMarks.item(arg);
                    if (it == null) addPara(para, w);
                    else {
                        int y0 = lineY();
                        icons.add(new Ico(y0 + 2, 6, new ItemStack(it), 16));
                        lines.add(new Ln(it.getDescription().getVisualOrderText(), 22, null, 5));
                        cards.add(new int[] {y0, 20, 0});
                        lines.add(new Ln(null, 3, null, 2));
                    }
                }
                default -> {}
            }
        }
    }

    private void toggleDrop(int d) {
        String k = current().id() + "#" + d;
        if (!openDrops.remove(k)) openDrops.add(k);
        layoutText();
    }

    private void hoverTip(FormattedCharSequence s, int lx) {
        Style st = font.getSplitter().componentStyleAtWidth(s, lx);
        if (st != null && st.getHoverEvent() != null && st.getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT) instanceof Component c) loopTip = font.split(c, 220);
    }

    private void layoutText() {
        lines = new ArrayList<>();
        cards = new ArrayList<>();
        icons = new ArrayList<>();
        tipLines = new ArrayList<>();
        inList = false;
        textTotal = 0;
        if (font == null) return;
        Page p = current();
        String key = "compendium." + p.id();
        int tw = contentW() - 12 - (machineOf(p) != null && sideBrief() ? SB_W + 10 : 0);
        tabs = List.of();
        if (p.anim() != Anim.TREE && p.anim() != Anim.CATALOG && p.anim() != Anim.ACHIEVE && p.anim() != Anim.GUIDE && !isLoop(p.anim()) && p.anim() != Anim.FLUIDS && !isNet(p.anim()) && p.anim() != Anim.SCOPE && p.anim() != Anim.BUILD && p.anim() != Anim.EXAMPLES && p.anim() != Anim.WELCOME && has(key + ".text")) {
            String[] paras = tr(key + ".text").split("\n");
            var secs = sections(paras);
            if (secs.size() > 1 && secs.size() <= 4) {
                List<String> all = new ArrayList<>();
                for (var sec : secs) {
                    if (!sec.get(0).isEmpty()) all.add("## " + sec.get(0));
                    all.addAll(sec.subList(1, sec.size()));
                }
                addBlocks(all.toArray(new String[0]), p.id(), tw);
                if (fitH >= 0 && lineY() > fitH) {
                    lines.clear();
                    cards.clear();
                    icons.clear();
                    inList = false;
                    tabs = secs;
                    tab = Math.min(tab, secs.size() - 1);
                    addBlocks(secs.get(tab).subList(1, secs.get(tab).size()).toArray(new String[0]), p.id(), tw);
                }
            } else addBlocks(paras, p.id(), tw);
        }
        textTotal = lineY();
        if (showTips() && has(key + ".tip")) {
            for (String para : tr(key + ".tip").split("\n")) tipLines.addAll(font.split(rich(para), tw - 10));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (search != null && !search.isMouseOver(mx, my) && search.isFocused()) {
            search.setFocused(false);
            setFocused(null);
        }
        if (button == 3) { back(); return true; }
        if ((current().anim() == Anim.BUILD || show3d) && build != null && build.mouseClicked(mx, my, button)) return true;
        if (button == 1 && plantHover >= 0 && current().anim() == Anim.SCOPE && scope != null && scope.nodes.get(plantHover).page() != null) {
            int[] target = findPage(scope.nodes.get(plantHover).page());
            if (target != null) go(target[0], target[1]);
            return true;
        }
        if (button == 0) {
            int bs = bar.press(mx, my, scroll);
            if (bs >= 0) {
                scroll = bs;
                return true;
            }
            for (Hit h : hits) {
                if (mx >= h.x0() && mx < h.x1() && my >= h.y0() && my < h.y1()) {
                    switch (h.type()) {
                        case 0 -> go(h.a(), h.b());
                        case 1 -> back();
                        case 3 -> { plantStart = System.currentTimeMillis(); scopeReplay = true; scopeFollow = true; }
                        case 4 -> plantZoom = 0;
                        case 9 -> toggleDrop(h.a());
                        case 10 -> { go(h.a(), h.b()); jumpToFind(); }
                        case 11 -> { searchFilter = CompendiumSearch.Filter.values()[h.a()]; sideScroll = 0; }
                        case 12 -> { String k = recipeRows.get(h.a()).path(); if (!recipeOpen.remove(k)) recipeOpen.add(k); }
                        case 13 -> { var it = recipeRows.get(h.a()).part().stack().getItem(); recipeChoice.merge(it, 1, Integer::sum); buildRecipe(); }
                        case 14 -> { recipeOf = ItemStack.EMPTY; plantZoom = 0; }
                        case 15 -> { tab = h.a(); show3d = false; scroll = 0; layoutText(); }
                        case 22 -> { show3d = true; scroll = 0; }
                        case 17 -> scopeLinks = !scopeLinks;
                        case 18 -> scopeNames = !scopeNames;
                        case 16 -> { if (!com.sqc.nuclearsq.client.RecipeLink.showRecipe(briefStack)) openRecipe(briefStack); }
                        case 20 -> { fluidSel = h.a(); scroll = 0; }
                        case 21 -> { var o = com.sqc.nuclearsq.progression.AchievementTree.NODES.get(h.a()); int[] t = findPage(o.page()); go(t[0], t[1]); pick = o.key(); }
                        case 19 -> { ItemStack st = proofStack(com.sqc.nuclearsq.progression.AchievementTree.NODES.get(h.a())); if (!com.sqc.nuclearsq.client.RecipeLink.showRecipe(st)) openRecipe(st); }
                        default -> turn(1);
                    }
                    return true;
                }
            }
            if (linkHover >= 0) {
                int[] target = findPage(links.get(linkHover).page());
                if (target != null) go(target[0], target[1]);
                return true;
            }
            if (!hover.isEmpty() && !sources(hover.getItem()).isEmpty()) {
                openRecipe(hover);
                return true;
            }
            if (zoomNow() && inPlant(mx, my)) {
                plantDrag = true;
                plantMoved = false;
                plantDragX = mx;
                plantDragY = my;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private BuildScene build;
    private String buildFor = "";

    private static final String FEATURED_EXAMPLE = "example_industrial_base";
    private static final java.util.Map<String, Integer> LINK_KIND_COLORS = java.util.Map.of("pipe", 0xFF4FA3D9, "item", 0xFFE0C060, "power", 0xFFE05050, "data", 0xFF5ADCE8);

    private void drawExamples(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        Section sec = null;
        for (Section s : SECTIONS) if (s.id().equals("examples")) sec = s;
        if (sec == null) return;
        final int cw = w - 8, cardH = 52;
        int cols = cw >= 330 ? 2 : 1, tw = (cw - (cols - 1) * 6) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, guideTotal - h)));
        g.enableScissor(x, y, x + w, y + h);
        int cy = y - scroll;
        String tip = null;
        Page featured = null;
        for (Chapter ch : sec.chapters())
            for (Page pg : ch.pages()) if (pg.anim() == Anim.BUILD && pg.id().equals(FEATURED_EXAMPLE)) featured = pg;
        if (featured != null) {
            g.drawString(font, tr("compendium.build.featured").toUpperCase(java.util.Locale.ROOT), x + 2, cy + 4, ACCENT, false);
            cy += 20;
            tip = drawExampleCard(g, mx, my, y, h, featured, x, cy, cw, cardH, true);
            cy += cardH + 14;
        }
        for (Chapter ch : sec.chapters()) {
            List<Page> pages = ch.pages().stream().filter(pg -> pg.anim() == Anim.BUILD && !pg.id().equals(FEATURED_EXAMPLE)).toList();
            if (pages.isEmpty()) continue;
            MachineThumbs.drawStack(g, ch.icon().get(), x + 2, cy);
            g.drawString(font, tr("compendium." + ch.id() + ".chapter").toUpperCase(java.util.Locale.ROOT), x + 24, cy + 4, ACCENT, false);
            cy += 20;
            int col = 0;
            for (Page pg : pages) {
                String cardTip = drawExampleCard(g, mx, my, y, h, pg, x + col * (tw + 6), cy, tw, cardH, false);
                if (cardTip != null) tip = cardTip;
                if (++col == cols) {
                    cy += cardH + 4;
                    col = 0;
                }
            }
            if (col > 0) cy += cardH + 4;
            cy += 8;
        }
        g.disableScissor();
        guideTotal = cy + scroll - y;
        scrollBar(g, x, y, w, h, guideTotal);
        if (tip != null) g.renderTooltip(font, font.split(Component.literal(tip), 220), mx, my);
    }

    private String drawExampleCard(GuiGraphics g, int mx, int my, int viewTop, int viewHeight, Page pg, int left, int top, int width, int height, boolean featured) {
        List<String[]> nodes = com.sqc.nuclearsq.progression.BuildData.nodes(pg.id());
        List<String> kinds = com.sqc.nuclearsq.progression.BuildData.linkKinds(pg.id());
        boolean over = mx >= left && mx < left + width && my >= Math.max(top, viewTop) && my < Math.min(top + height, viewTop + viewHeight);
        rrect(g, left, top, left + width, top + height, over || featured ? ACCENT : EDGE);
        rrect(g, left + 1, top + 1, left + width - 1, top + height - 1, over ? HOVER_BG : CARD);

        StringBuilder chain = new StringBuilder();
        for (String[] node : nodes) chain.append(chain.length() > 0 ? " \u2192 " : "").append(tr("compendium." + node[0] + ".title"));

        Component title = Component.literal(tr("compendium." + pg.id() + ".title")).withStyle(ChatFormatting.BOLD);
        String titleText = title.getString();
        int titleLimit = width - 12;
        while (titleText.length() > 1 && font.width(Component.literal(titleText).withStyle(ChatFormatting.BOLD)) > titleLimit) titleText = titleText.substring(0, titleText.length() - 1);
        if (titleText.length() < title.getString().length()) titleText = titleText.stripTrailing() + "\u2026";
        g.drawString(font, Component.literal(titleText).withStyle(ChatFormatting.BOLD), left + 6, top + 5, 0xFFFFFFFF, false);

        String chainLine = chain.toString();
        int chainWidth = width - 12;
        if (font.width(chainLine) > chainWidth) chainLine = font.plainSubstrByWidth(chainLine, chainWidth - font.width("\u2026")) + "\u2026";
        g.drawString(font, chainLine, left + 6, top + 18, MUTED, false);

        String meta = tr("compendium.build.meta").formatted(nodes.size(), kinds.size());
        int metaX = left + width - 6 - font.width(meta);
        g.drawString(font, meta, metaX, top + 36, MUTED, false);
        int dotsX = metaX - 6;
        for (String kind : LINK_KIND_COLORS.keySet().stream().sorted().toList()) {
            if (!kinds.contains(kind)) continue;
            dotsX -= 7;
            g.fill(dotsX, top + 37, dotsX + 5, top + 42, LINK_KIND_COLORS.get(kind));
        }

        final int iconStride = 24;
        int fit = Math.max(1, (dotsX - 6 - left - 6) / iconStride), shown = nodes.size() > fit ? fit - 1 : nodes.size();
        for (int k = 0; k < shown; k++) {
            MachineThumbs.drawStack(g, modItem(nodes.get(k)[1]), left + 6 + k * iconStride, top + 31);
            if (k + 1 < nodes.size()) g.drawString(font, "\u203A", left + 6 + k * iconStride + 17, top + 35, MUTED, false);
        }
        if (shown < nodes.size()) g.drawString(font, "+" + (nodes.size() - shown), left + 8 + shown * iconStride, top + 35, MUTED, false);

        int[] target = findPage(pg.id());
        if (target != null && over) hits.add(new Hit(left, Math.max(top, viewTop), left + width, Math.min(top + height, viewTop + viewHeight), 0, target[0], target[1]));
        if (!over) return null;
        return chain.toString();
    }

    private String machine3dId() {
        Page p = current();
        var node = machineOf(p);
        for (var o : com.sqc.nuclearsq.progression.AchievementTree.NODES) if (node != null && o.key().equals(pick) && o.page().equals(p.id())) node = o;
        final String suffix = "_controller_sqc";
        if (node == null || !node.item().endsWith(suffix)) return null;
        String machineId = node.item().substring(0, node.item().length() - suffix.length());
        return com.sqc.nuclearsq.progression.BuildData.hasMultiblockModel(machineId) ? machineId : null;
    }

    private void draw3d(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String machineId) {
        String sceneKey = "3d:" + current().id() + ":" + machineId;
        if (!sceneKey.equals(buildFor)) {
            buildFor = sceneKey;
            build = BuildScene.createForMachine(machineId, current().id());
        }
        if (build == null) g.drawString(font, "3D view unavailable: " + machineId, x, y, 0xFFFF5555, false);
        else build.render(g, font, x, y, w - 6, h - 4, mx, my, 0);
    }

    private void drawBuild(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        if (!p.id().equals(buildFor)) {
            buildFor = p.id();
            build = BuildScene.create(p.id());
        }
        if (build == null) g.drawString(font, "Build data missing: " + p.id(), x, y, 0xFFFF5555, false);
        else {
            scroll = Math.max(0, Math.min(scroll, Math.max(0, build.totalHeight() - h)));
            build.render(g, font, x, y, w, h, mx, my, scroll);
            scrollBar(g, x, y, w, h, build.totalHeight());
        }
    }

    private boolean inPlant(double mx, double my) {
        return mx >= plantAX && mx < plantAX + plantAW && my >= plantAY && my < plantAY + plantAH;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int bs = bar.drag(my);
        if (bs >= 0) {
            scroll = bs;
            return true;
        }
        if (build != null && build.dragging()) {
            build.mouseDragged(dx, dy);
            return true;
        }
        if (plantDrag) {
            plantPanX += (float) dx;
            plantPanY += (float) dy;
            scopeFollow = false;
            if (Math.abs(mx - plantDragX) + Math.abs(my - plantDragY) > 3) plantMoved = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        bar.release();
        if (build != null && build.dragging()) {
            String target = build.mouseReleased();
            int[] page = target == null ? null : findPage(target);
            if (page != null) go(page[0], page[1]);
            return true;
        }
        if (plantDrag && button == 0) {
            plantDrag = false;
            if (!plantMoved && plantHover >= 0 && isNet(current().anim())) {
                int[] target = findPage(netNodes().get(plantHover).page());
                if (target != null) go(target[0], target[1]);
            }
            if (!plantMoved && current().anim() == Anim.SCOPE) scopePin = plantHover == scopePin ? -1 : plantHover;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if ((current().anim() == Anim.BUILD || show3d) && build != null && build.mouseScrolled(mx, my, sy)) return true;
        if (zoomNow() && inPlant(mx, my) && plantZoom > 0) {
            float z = Math.max(Math.min(PLANT_ZOOM_MIN, plantFit), Math.min(PLANT_ZOOM_MAX, plantZoom * (sy > 0 ? 1.15f : 1 / 1.15f)));
            float wx = (float) (mx - plantAX - plantPanX) / plantZoom, wy = (float) (my - plantAY - plantPanY) / plantZoom;
            plantPanX = (float) (mx - plantAX) - wx * z;
            plantPanY = (float) (my - plantAY) - wy * z;
            plantZoom = z;
            scopeFollow = false;
            return true;
        }
        if (mx >= px + GBAR && mx < px + GBAR + SIDEBAR) sideScroll = Math.max(0, sideScroll - (int) (sy * 16));
        else scroll = Math.max(0, scroll - (int) (sy * 14));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && search != null && !search.getValue().isEmpty()) { search.setValue(""); return true; }
        if (key == 256 && !recipeOf.isEmpty()) { recipeOf = ItemStack.EMPTY; plantZoom = 0; return true; }
        if (key == 70 && (mods & 2) != 0 && search != null) { setFocused(search); search.setFocused(true); return true; }
        if (search != null && search.isFocused() && key != 256) return super.keyPressed(key, scan, mods);
        if (key == 263) { turn(-1); return true; }
        if (key == 262) { turn(1); return true; }
        if (key == 265) { scroll = Math.max(0, scroll - 20); return true; }
        if (key == 264) { scroll += 20; return true; }
        if (key == 268) { scroll = 0; return true; }
        if (key == 269) { scroll = Integer.MAX_VALUE / 2; return true; }
        if (key == 266) { scroll = Math.max(0, scroll - Math.max(20, textVisible - 20)); return true; }
        if (key == 267) { scroll += Math.max(20, textVisible - 20); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static void rrect(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0 + 1, y0, x1 - 1, y1, c);
        g.fill(x0, y0 + 1, x1, y1 - 1, c);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        hover = ItemStack.EMPTY;
        loopTip = null;
        linkHover = -1;
        mouseX = mx;
        mouseY = my;
        plantHover = -1;
        hits.clear();
        bar.clear();
        float t = (System.currentTimeMillis() % 1000000L) / 1000f;

        rrect(g, px - 2, py - 2, px + pw + 2, py + ph + 2, EDGE);
        rrect(g, px, py, px + pw, py + ph, BG);
        g.fill(px + 1, py, px + GBAR + SIDEBAR, py + ph, SIDE_BG);
        g.fill(px + 1, py, px + GBAR, py + ph, PANEL_BG);
        g.fill(px + GBAR - 1, py, px + GBAR, py + ph, EDGE);
        g.fill(px + GBAR + SIDEBAR, py, px + GBAR + SIDEBAR + 1, py + ph, EDGE);
        drawSidebar(g, mx, my);

        Page p = current();
        int x = contentX(), w = contentW();
        drawPath(g, mx, my, x, w - searchW() - 8);
        g.fill(x, py + 22, x + w, py + 23, EDGE);
        int y = py + 29;
        FormattedCharSequence title = rich(tr("compendium." + p.id() + ".title")).withStyle(ChatFormatting.BOLD).getVisualOrderText();
        g.drawString(font, title, x, y, 0xFFFFFFFF, false);
        if (my >= y && my < y + 10) hoverTip(title, mx - x);
        y += 12;
        g.fill(x, y, x + 28, y + 2, ACCENT);
        y += 8;

        int bottom = py + ph - 32;
        if (recipeOf.isEmpty() && CHAPTERS.get(chapter).id().equals("achievements")) y += drawPageTabs(g, mx, my, x, y, w);
        if (!recipeOf.isEmpty()) {
            drawRecipeTree(g, mx, my, x, y, w, bottom - y);
        } else if (p.anim() == Anim.TREE) {
            drawTree(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.CATALOG) {
            drawCatalog(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.ACHIEVE) {
            drawMachineList(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.GUIDE) {
            drawGuide(g, mx, my, x, y, w, bottom - y, p);
        } else if (isLoop(p.anim())) {
            drawLoop(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.FLUIDS) {
            drawFluids(g, mx, my, x, y, w, bottom - y, p);
        } else if (isNet(p.anim())) {
            drawPlant(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.LINES) {
            drawLineTiles(g, mx, my, x, y, w, bottom - y);
        } else if (p.anim() == Anim.SCOPE) {
            drawScope(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.BUILD) {
            drawBuild(g, mx, my, x, y, w, bottom - y, p);
        } else if (p.anim() == Anim.EXAMPLES) {
            drawExamples(g, mx, my, x, y, w, bottom - y);
        } else {
            headOff = 0;
            int tx = x, tw = w;
            var nd = machineOf(p);
            for (var o : com.sqc.nuclearsq.progression.AchievementTree.NODES) if (nd != null && o.key().equals(pick) && o.page().equals(p.id())) nd = o;
            if (nd != null) {
                if (sideBrief()) {
                    drawBrief(g, mx, my, nd, x, y, SB_W, true);
                    tx += SB_W + 10;
                    tw -= SB_W + 10;
                } else y += drawBrief(g, mx, my, nd, x, y, w, false) + 6;
                y += drawMachineHead(g, mx, my, nd, tx, y, tw);
            } else y += drawTextTabs(g, mx, my, tx, y, tw);
            if (p.anim() == Anim.WELCOME) {
                g.enableScissor(x, y, x + w, bottom);
                headOff = drawWelcome(g, mx, my, x, y - scroll, w - 8, y, bottom);
                g.disableScissor();
            } else {
                if (!(show3d && machine3dId() != null)) y += drawHeadRow(g, p, tx, y, tw, mx, my, t);
            }
            if (show3d && machine3dId() != null) draw3d(g, mx, my, tx, y, tw, bottom - y, machine3dId());
            else drawText(g, tx, y, tw, bottom - y);
        }
        drawFooter(g, mx, my, x, w);
    }

    private record SideRow(int kind, int section, int chapter, int page, int y, int h) {}

    private List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < 3) {
            if (font.width(rest) <= w) { out.add(rest); rest = ""; break; }
            int cut = 0;
            for (int i = 1; i < rest.length(); i++) {
                char c = rest.charAt(i - 1);
                if ((c == ' ' || c == '-') && font.width(rest.substring(0, i).stripTrailing()) <= w) cut = i;
            }
            if (cut == 0) cut = Math.max(1, font.plainSubstrByWidth(rest, w).length());
            out.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, font.plainSubstrByWidth(out.get(last), w - font.width("...")) + "...");
        }
        return out;
    }

    private void drawSidebar(GuiGraphics g, int mx, int my) {
        drawGroups(g, mx, my);
        int lw = SIDEBAR - 16, lh = lw * LOGO_H / LOGO_W, lx = px + GBAR + 8, ly = py + (SIDE_HEAD - lh) / 2;
        rrect(g, lx - 1, ly - 1, lx + lw + 1, ly + lh + 1, EDGE);
        drawLogo(g, lx, ly, lw);
        g.fill(px + GBAR + 8, py + SIDE_HEAD, px + GBAR + SIDEBAR - 8, py + SIDE_HEAD + 1, EDGE);
        if (!query.isEmpty()) {
            drawSearchResults(g, mx, my);
            return;
        }

        int activeSec = sectionOf(chapter);
        List<SideRow> side = new ArrayList<>();
        int vy = 0, curY = 0, curH = 14;
        List<Chapter> chs = SECTIONS.get(activeSec).chapters();
        int ind = chs.size() > 1 ? 8 : 0;
        for (int k = 0; k < chs.size(); k++) {
            int c = firstChapter(activeSec) + k;
            if (chs.size() > 1) {
                int h = 5 + 9 * wrap(tr("compendium." + CHAPTERS.get(c).id() + ".chapter"), SIDEBAR - 36).size();
                side.add(new SideRow(1, activeSec, c, 0, vy, h));
                vy += h + 1;
            }
            if (c != chapter || (chs.get(k).flat() && chs.size() > 1)) continue;
            for (int q = 0; q < chs.get(k).pages().size(); q++) {
                String pid = chs.get(k).pages().get(q).id();
                int no = guideNo(pid);
                int h = 4 + 9 * wrap((no > 0 ? no + " " : "") + tr("compendium." + pid + ".title"), SIDEBAR - 40 - ind - (no > 0 ? 6 : 0)).size();
                if (q == page) { curY = vy; curH = h + 1; }
                side.add(new SideRow(2, activeSec, c, q, vy, h));
                vy += h + 1;
            }
        }
        vy += 4;

        int top = sideTop(), bottom = py + ph - 8, visible = bottom - top;
        if (sideFollow) {
            if (curY < sideScroll) sideScroll = curY;
            if (curY + curH > sideScroll + visible) sideScroll = curY + curH - visible;
            sideFollow = false;
        }
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, vy - visible)));
        g.enableScissor(px + GBAR + 1, top, px + GBAR + SIDEBAR, bottom);
        int left = px + GBAR + 5, right = px + GBAR + SIDEBAR - 5, gNext = guideNext();
        for (SideRow r : side) {
            int y = top + r.y() - sideScroll;
            if (y + r.h() < top || y > bottom) continue;
            boolean hov = mx >= left && mx < right && my >= Math.max(y, top) && my < Math.min(y + r.h(), bottom);
            switch (r.kind()) {
                case 1 -> {
                    boolean cur = r.chapter() == chapter;
                    if (hov && !cur) rrect(g, left + 6, y, right, y + r.h(), HOVER_BG);
                    g.fill(left + 10, y + 4, left + 15, y + 9, cur ? ACCENT : EDGE);
                    List<String> lines = wrap(tr("compendium." + CHAPTERS.get(r.chapter()).id() + ".chapter"), SIDEBAR - 36);
                    for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), left + 20, y + 3 + 9 * i, cur ? 0xFFFFFFFF : hov ? TEXT : MUTED, false);
                    hits.add(new Hit(left, Math.max(y, top), right, Math.min(y + r.h(), bottom), 0, r.chapter(), 0));
                }
                default -> {
                    boolean cur = r.page() == page;
                    if (hov && !cur) rrect(g, left + 6 + ind, y, right, y + r.h(), HOVER_BG);
                    String pid = CHAPTERS.get(r.chapter()).pages().get(r.page()).id();
                    int no = guideNo(pid), sh = no > 0 ? 6 : 0;
                    if (no > 0) {
                        Guide.Chapter gc = Guide.CHAPTERS.get(no - 1);
                        stateBox(g, left + 12 + ind, y + 2, Guide.count(gc, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone) == gc.steps().size() ? 1 : no - 1 == gNext ? 2 : 0);
                    } else g.fill(left + 12 + ind, y + 5, left + 15 + ind, y + 8, cur ? ACCENT : EDGE);
                    List<String> lines = wrap((no > 0 ? no + " " : "") + tr("compendium." + pid + ".title"), SIDEBAR - 40 - ind - sh);
                    for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), left + 21 + ind + sh, y + 3 + 9 * i, cur ? 0xFFFFFFFF : hov ? TEXT : MUTED, false);
                    hits.add(new Hit(left, Math.max(y, top), right, Math.min(y + r.h(), bottom), 0, r.chapter(), r.page()));
                }
            }
        }
        g.disableScissor();
    }

    private static final java.util.Set<String> LOWER_SECTIONS = java.util.Set.of("protection", "achievements", "weapons");

    private void drawGroups(GuiGraphics g, int mx, int my) {
        int n = SECTIONS.size(), low = 0, up = 0, lo = 0, active = sectionOf(chapter);
        for (Section sec : SECTIONS) if (LOWER_SECTIONS.contains(sec.id())) low++;
        int h = Math.max(28, Math.min(38, (ph - 14) / n)), bottom = py + ph - 4;
        g.fill(px + 6, bottom - low * h - 3, px + GBAR - 6, bottom - low * h - 2, EDGE);
        for (int k = 0; k < n; k++) {
            Section sec = SECTIONS.get(k);
            int y = LOWER_SECTIONS.contains(sec.id()) ? bottom - (low - lo++) * h : py + 4 + up++ * h;
            boolean act = k == active, hov = mx >= px + 1 && mx < px + GBAR - 1 && my >= y && my < y + h;
            if (act) { rrect(g, px + 3, y, px + GBAR - 3, y + h - 1, ACTIVE_BG); g.fill(px + 3, y + 3, px + 5, y + h - 4, ACCENT); }
            else if (hov) rrect(g, px + 3, y, px + GBAR - 3, y + h - 1, HOVER_BG);
            MachineThumbs.drawStack(g, sec.icon().get(), px + GBAR / 2 - 8, y + 2);
            String name = tr("compendium.section." + sec.id());
            int sp = name.indexOf(' '), col = act ? ACCENT : hov ? TEXT : MUTED;
            if (font.width(name) <= GBAR - 2) g.drawCenteredString(font, name, px + GBAR / 2, y + 20, col);
            else if (sp > 0 && h >= 38 && font.width(name.substring(0, sp)) <= GBAR - 2 && font.width(name.substring(sp + 1)) <= GBAR - 2) {
                g.drawCenteredString(font, name.substring(0, sp), px + GBAR / 2, y + 19, col);
                g.drawCenteredString(font, name.substring(sp + 1), px + GBAR / 2, y + 28, col);
            } else {
                float sc = (GBAR - 2) / (float) font.width(name);
                g.pose().pushPose();
                g.pose().translate(px + GBAR / 2f, y + 21, 0);
                g.pose().scale(sc, sc, 1f);
                g.drawString(font, name, -font.width(name) / 2, 0, col, false);
                g.pose().popPose();
            }
            hits.add(new Hit(px + 1, y, px + GBAR - 1, y + h, 0, act ? chapter : firstChapter(k), act ? page : 0));
        }
    }

    private int sideTop() { return py + SIDE_HEAD + 6; }

    private void drawSearchResults(GuiGraphics g, int mx, int my) {
        drawSearchFilters(g, mx, my);
        int top = sideTop() + FILTER_H, bottom = py + ph - 8, visible = bottom - top;
        int left = px + GBAR + 5, right = px + GBAR + SIDEBAR - 5;
        boolean all = searchFilter == CompendiumSearch.Filter.ALL;
        List<CompendiumSearch.Found> found = new ArrayList<>();
        int shownItems = 0;
        for (var f : searchHits(searchFilter))
            if (!all || f.entry().kind() != CompendiumSearch.Kind.ITEM || shownItems++ < ITEMS_IN_ALL) found.add(f);
        int parts = all ? catalogMatches() : 0, machines = all ? machineMatches() : 0;
        int total = (parts > 0 ? 22 : 0) + (machines > 0 ? 22 : 0) + found.size() * 23 + (found.isEmpty() && parts == 0 && machines == 0 ? 14 : 0);
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, total - visible)));
        g.enableScissor(px + GBAR + 1, top, px + GBAR + SIDEBAR, bottom);
        int y = top - sideScroll;
        if (machines > 0) y = listRow(g, mx, my, y, machinesPage(), modItem(com.sqc.nuclearsq.progression.PlantOverview.ASSEMBLER_ITEM), "compendium.search.machines", machines);
        if (parts > 0) y = listRow(g, mx, my, y, catalogPage(), new ItemStack(Items.CHEST), "compendium.search.parts", parts);
        if (found.isEmpty() && parts == 0 && machines == 0) g.drawString(font, font.plainSubstrByWidth(tr("compendium.search.none"), SIDEBAR - 20), left + 6, y + 3, MUTED, false);
        for (var f : found) {
            if (y + 22 >= top && y <= bottom) searchRow(g, mx, my, y, f.entry(), top, bottom);
            y += 23;
        }
        g.disableScissor();
    }

    private void drawSearchFilters(GuiGraphics g, int mx, int my) {
        CompendiumSearch.Filter[] fs = CompendiumSearch.Filter.values();
        int x0 = px + GBAR + 8, bw = (SIDEBAR - 18) / 2, h = 12;
        for (int k = 0; k < fs.length; k++) {
            int x = x0 + (k % 2) * (bw + 2), y = sideTop() - 2 + (k / 2) * (h + 2);
            String label = font.plainSubstrByWidth(tr("compendium.search.filter." + fs[k].name().toLowerCase(java.util.Locale.ROOT)), bw - 4);
            boolean act = fs[k] == searchFilter, hov = mx >= x && mx < x + bw && my >= y && my < y + h;
            rrect(g, x, y, x + bw, y + h, act ? ACTIVE_BG : hov ? HOVER_BG : CARD);
            if (act) g.fill(x + 2, y + h - 1, x + bw - 2, y + h, ACCENT);
            g.drawCenteredString(font, label, x + bw / 2, y + 2, act ? ACCENT : hov ? TEXT : MUTED);
            hits.add(new Hit(x, y, x + bw, y + h, 11, k, 0));
        }
    }

    private void searchRow(GuiGraphics g, int mx, int my, int y, CompendiumSearch.Entry e, int top, int bottom) {
        int left = px + GBAR + 5, right = px + GBAR + SIDEBAR - 5;
        int[] target = e.page() == null ? null : findPage(e.page());
        boolean isPage = e.kind() == CompendiumSearch.Kind.PAGE;
        boolean cur = isPage && target != null && target[0] == chapter && target[1] == page;
        boolean hov = mx >= left && mx < right && my >= Math.max(y, top) && my < Math.min(y + 22, bottom);
        if (cur) rrect(g, left, y, right, y + 22, ACTIVE_BG);
        else if (hov && target != null) rrect(g, left, y, right, y + 22, HOVER_BG);
        int tx = left + 14;
        if (isPage) g.fill(left + 6, y + 6, left + 9, y + 9, cur ? ACCENT : EDGE);
        else {
            MachineThumbs.drawStack(g, searchStacks.getOrDefault((e.kind() == CompendiumSearch.Kind.ITEM ? "i:" : "m:") + e.id(), ItemStack.EMPTY), left + 3, y + 3);
            tx = left + 22;
        }
        int tw = right - tx - 2;
        drawMarked(g, e.title(), tx, y + 3, tw, cur || hov ? 0xFFFFFFFF : TEXT);
        String sub = searchSnippet(e);
        if (sub.isEmpty() && target != null)
            sub = isPage ? tr("compendium." + CHAPTERS.get(target[0]).id() + ".chapter") : tr("compendium." + e.page() + ".title");
        drawMarked(g, sub, tx, y + 12, tw, MUTED);
        if (target != null) hits.add(new Hit(left, Math.max(y, top), right, Math.min(y + 22, bottom), 10, target[0], target[1]));
    }

    private void drawMarked(GuiGraphics g, String s, int x, int y, int w, int color) {
        String t = font.plainSubstrByWidth(s, w);
        int pos = 0, cx = x;
        for (int[] r : CompendiumSearch.spans(t, terms())) {
            String a = t.substring(pos, r[0]), b = t.substring(r[0], r[1]);
            g.drawString(font, a, cx, y, color, false);
            cx += font.width(a);
            g.drawString(font, b, cx, y, ACCENT, false);
            cx += font.width(b);
            pos = r[1];
        }
        g.drawString(font, t.substring(pos), cx, y, color, false);
    }

    private String plainOf(FormattedCharSequence s, @javax.annotation.Nullable List<Integer> xs) {
        StringBuilder sb = new StringBuilder();
        int[] cx = {0};
        s.accept((i, st, cp) -> {
            if (xs != null) for (int k = 0; k < Character.charCount(cp); k++) xs.add(cx[0]);
            sb.appendCodePoint(cp);
            cx[0] += font.width(FormattedCharSequence.codepoint(cp, st));
            return true;
        });
        if (xs != null) xs.add(cx[0]);
        return sb.toString();
    }

    private void markFinds(GuiGraphics g, FormattedCharSequence s, int x, int y) {
        if (query.isEmpty() || s == null) return;
        List<Integer> xs = new ArrayList<>();
        String t = plainOf(s, xs);
        for (int[] r : CompendiumSearch.spans(t, terms())) g.fill(x + xs.get(r[0]) - 1, y - 1, x + xs.get(r[1]) + 1, y + 9, 0x66E8A33D);
    }

    private int findLine() {
        List<String> terms = terms();
        int y = 0;
        for (Ln l : lines) {
            if (l.kind() != 2 && l.seq() != null && !CompendiumSearch.spans(plainOf(l.seq(), null), terms).isEmpty()) return y;
            y += lineH(l);
        }
        return -1;
    }

    private void jumpToFind() {
        if (query.isEmpty()) return;
        List<String> fterms = terms();
        for (int i = 0; i < tabs.size(); i++)
            if (tabs.get(i).stream().anyMatch(para -> !CompendiumSearch.spans(CompendiumSearch.plain(para, ProgressionScreen::langOrNull), fterms).isEmpty())) {
                if (i != tab || show3d) {
                    tab = i;
                    show3d = false;
                    layoutText();
                }
                break;
            }
        int at = findLine();
        String key = "compendium." + current().id() + ".text";
        if (at < 0 && has(key)) {
            List<String> terms = terms();
            int drops = 0, open = -1;
            boolean changed = false;
            for (String para : tr(key).split("\n")) {
                Matcher m = TextMarks.BLOCK.matcher(para.trim());
                if (m.matches()) {
                    if (m.group(1).equals("drop")) open = drops++;
                    else if (m.group(1).equals("/drop")) open = -1;
                    continue;
                }
                if (open >= 0 && !CompendiumSearch.spans(CompendiumSearch.plain(para, ProgressionScreen::langOrNull), terms).isEmpty())
                    changed |= openDrops.add(current().id() + "#" + open);
            }
            if (changed) {
                layoutText();
                at = findLine();
            }
        }
        if (at >= 0) scroll = Math.max(0, at - 2 * LINE_H);
    }

    private int listRow(GuiGraphics g, int mx, int my, int y, int[] target, ItemStack icon, String key, int n) {
        if (target == null) return y;
        int top = sideTop() + FILTER_H, bottom = py + ph - 8, left = px + GBAR + 5, right = px + GBAR + SIDEBAR - 5;
        boolean cur = chapter == target[0] && page == target[1];
        boolean hov = mx >= left && mx < right && my >= Math.max(y, top) && my < Math.min(y + 20, bottom);
        if (cur) { rrect(g, left, y, right, y + 20, ACTIVE_BG); g.fill(left, y + 3, left + 2, y + 17, ACCENT); }
        else if (hov) rrect(g, left, y, right, y + 20, HOVER_BG);
        MachineThumbs.drawStack(g, icon, left + 6, y + 2);
        String name = tr(key).replace("%s", String.valueOf(n));
        g.drawString(font, font.plainSubstrByWidth(name, SIDEBAR - 36), left + 26, y + 6, cur ? 0xFFFFFFFF : hov ? TEXT : ACCENT, false);
        hits.add(new Hit(left, Math.max(y, top), right, Math.min(y + 20, bottom), 0, target[0], target[1]));
        return y + 22;
    }

    private void drawFooter(GuiGraphics g, int mx, int my, int x, int w) {
        int fy = py + ph - 24;
        g.fill(x, fy - 6, x + w, fy - 5, EDGE);
        int[] pv = step(-1), nx = step(1);
        int n = CHAPTERS.get(chapter).pages().size(), half = w / 2 - 30;
        String prev = "‹ " + font.plainSubstrByWidth(rich(tr("compendium." + CHAPTERS.get(pv[0]).pages().get(pv[1]).id() + ".title")).getString(), half - 10);
        String next = font.plainSubstrByWidth(rich(tr("compendium." + CHAPTERS.get(nx[0]).pages().get(nx[1]).id() + ".title")).getString(), half - 10) + " ›";
        int pw0 = font.width(prev), nw = font.width(next);
        boolean hp = mx >= x && mx < x + pw0 && my >= fy && my < fy + 16;
        boolean hn = mx >= x + w - nw && mx < x + w && my >= fy && my < fy + 16;
        g.drawString(font, prev, x, fy + 4, hp ? 0xFFFFFFFF : MUTED, false);
        g.drawString(font, next, x + w - nw, fy + 4, hn ? 0xFFFFFFFF : MUTED, false);
        hits.add(new Hit(x, fy, x + pw0, fy + 16, 0, pv[0], pv[1]));
        hits.add(new Hit(x + w - nw, fy, x + w, fy + 16, 0, nx[0], nx[1]));
        g.drawCenteredString(font, (page + 1) + " / " + n, x + w / 2, fy + 4, MUTED);
    }

    private int[] step(int d) {
        int c = chapter, p = page + d;
        if (p < 0) { c = (c + CHAPTERS.size() - 1) % CHAPTERS.size(); p = CHAPTERS.get(c).pages().size() - 1; }
        else if (p >= CHAPTERS.get(c).pages().size()) { c = (c + 1) % CHAPTERS.size(); p = 0; }
        return new int[] {c, p};
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (loopTip != null) {
            g.renderTooltip(font, loopTip, mx, my);
            return;
        }
        if (hover.isEmpty()) return;
        var src = sources(hover.getItem());
        if (src.isEmpty()) g.renderTooltip(font, hover, mx, my);
        else drawRecipePanel(g, hover, src, mx, my);
    }

    private List<com.sqc.nuclearsq.progression.PlantOverview.Node> pageMachines(Page p) {
        List<com.sqc.nuclearsq.progression.PlantOverview.Node> shown = new ArrayList<>();
        List<com.sqc.nuclearsq.progression.PlantOverview.Node> all = new ArrayList<>(com.sqc.nuclearsq.progression.PlantOverview.NODES);
        all.addAll(com.sqc.nuclearsq.progression.AssemblyTree.NODES);
        all.addAll(com.sqc.nuclearsq.progression.AchievementTree.NODES);
        all.addAll(com.sqc.nuclearsq.progression.SecretWeaponsTree.NODES);
        for (var nd : all) {
            if (nd.page().equals(p.id()) && shown.stream().noneMatch(o -> o.item().equals(nd.item()))) shown.add(nd);
        }
        return shown;
    }

    private int cardW(com.sqc.nuclearsq.progression.PlantOverview.Node nd) {
        return Math.max(STRIP_CARD, font.width(tr("compendium.plant." + nd.key() + ".name")) + 4);
    }

    private int machineStripW(List<com.sqc.nuclearsq.progression.PlantOverview.Node> shown) {
        int sum = 0;
        for (var nd : shown) sum += cardW(nd) + 6;
        return sum - 6;
    }

    private List<ItemStack> pageRecipeItems(Page p) {
        List<String> ids = RECIPE_ITEMS.get(p.id());
        List<ItemStack> stacks = new ArrayList<>();
        if (ids == null) return stacks;
        List<String> fams = new ArrayList<>();
        for (String id : ids) if (!fams.contains(family(id))) fams.add(family(id));
        ids = new ArrayList<>(ids);
        ids.sort(java.util.Comparator.comparingInt((String id) -> fams.indexOf(family(id))).thenComparingLong(ProgressionScreen::tier));
        for (String id : ids) {
            var rl = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nuclearsq", id);
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (it != Items.AIR && !sources(it).isEmpty()) stacks.add(new ItemStack(it));
        }
        return stacks;
    }

    private static String family(String id) { return id.replaceAll("\\d+(mb|gb)?", ""); }

    private static long tier(String id) {
        Matcher m = Pattern.compile("(\\d+)(mb|gb)?").matcher(id);
        return m.find() ? Long.parseLong(m.group(1)) * ("gb".equals(m.group(2)) ? 1024 : 1) : 0;
    }

    private int recipeStripW(List<ItemStack> stacks) {
        return stacks.size() * 24 + font.width(tr("compendium.recipes")) + 20;
    }

    private static final int SB_W = 128;
    private List<List<String>> tabs = List.of();
    private int tab = 0;
    private boolean show3d;
    private String pick;
    private int fitH = -1;
    private ItemStack briefStack = ItemStack.EMPTY;

    @javax.annotation.Nullable
    private static com.sqc.nuclearsq.progression.PlantOverview.Node machineOf(Page p) {
        if (p.anim() == Anim.WELCOME) return null;
        for (var nd : com.sqc.nuclearsq.progression.AchievementTree.NODES) if (nd.page().equals(p.id())) return nd;
        return null;
    }

    private boolean sideBrief() { return contentW() >= 400; }

    private static List<List<String>> sections(String[] paras) {
        List<List<String>> out = new ArrayList<>();
        out.add(new ArrayList<>(List.of("")));
        int depth = 0;
        for (String para : paras) {
            String s = para.trim();
            Matcher m = TextMarks.BLOCK.matcher(s);
            boolean block = m.matches(), open = block && m.group(1).equals("drop"), close = block && m.group(1).equals("/drop");
            if (depth == 0 && (s.startsWith("## ") || open)) {
                out.add(new ArrayList<>(List.of(open ? java.util.Objects.requireNonNullElse(m.group(2), "") : s.substring(3))));
                if (open) depth = 1;
                continue;
            }
            if (open && depth > 0) depth++;
            if (close && depth > 0 && --depth == 0) continue;
            out.get(out.size() - 1).add(para);
        }
        if (out.get(0).stream().skip(1).allMatch(String::isBlank)) out.remove(0);
        return out.size() > 1 ? out : List.of();
    }

    @javax.annotation.Nullable
    private static com.sqc.nuclearsq.machine.big.BigMachine bigOf(String item) {
        for (var m : com.sqc.nuclearsq.machine.big.BigMachine.values()) if (m.controllerId().equals(item)) return m;
        return null;
    }

    @javax.annotation.Nullable
    private static com.sqc.nuclearsq.progression.LineTrees.Line lineOf(String key) {
        for (var l : com.sqc.nuclearsq.progression.LineTrees.LINES)
            for (var b : l.blocks()) if (b.keys().contains(key)) return l;
        return null;
    }

    private int drawBrief(GuiGraphics g, int mx, int my, com.sqc.nuclearsq.progression.PlantOverview.Node nd, int x, int y, int w, boolean side) {
        List<String[]> rows = new ArrayList<>();
        var m = bigOf(nd.item());
        if (m != null) {
            rows.add(new String[] {tr("compendium.brief.size"), m.w + " × " + m.h + " × " + m.d});
            var e = m.energy;
            if (e != null && e.use() > 0) rows.add(new String[] {tr("compendium.brief.power"), String.format("%,d FE/t", e.use())});
            int makes = m.extra("fe_per_tick"), peak = m.extra("fe_per_tick_peak");
            if (e != null && e.maxExtract() > 0 && e.use() == 0 && makes > 0) rows.add(new String[] {tr("compendium.brief.makes"), String.format("%,d FE/t", makes)});
            else if (e != null && e.maxExtract() > 0 && peak > 0) rows.add(new String[] {tr("compendium.brief.makes"), tr("compendium.brief.upto").formatted(String.format("%,d FE/t", peak))});
            else if (e != null && e.maxExtract() > 0) rows.add(new String[] {tr("compendium.brief.output"), String.format("%,d FE/t", e.maxExtract())});
            if (e != null && e.capacity() > 0) rows.add(new String[] {tr("compendium.brief.storage"), String.format("%,d FE", e.capacity())});
            if (!m.ports.isEmpty()) rows.add(new String[] {tr("compendium.brief.ports"), String.valueOf(m.ports.size())});
        }
        briefStack = proofStack(nd);
        var src = sources(briefStack.getItem());
        if (!src.isEmpty()) rows.add(new String[] {tr("compendium.brief.made"), whereName(src.get(0)).getString()});
        var line = lineOf(nd.key());
        if (line != null) rows.add(new String[] {tr("compendium.brief.line"), tr("compendium." + line.page() + ".title"), line.page()});
        if (side) {
            int in = w - 12, h = 94 + 26;
            for (String[] r : rows) h += font.width(r[0]) + 6 + font.width(r[1]) > in ? 24 : 14;
            rrect(g, x, y, x + w, y + h, EDGE);
            rrect(g, x + 1, y + 1, x + w - 1, y + h - 1, CARD);
            g.fill(x + 6, y + 6, x + w - 6, y + 76, PANEL_BG);
            MachineThumbs.draw(g, nd.item(), x + (w - 64) / 2, y + 9, 64, 1f);
            g.drawString(font, tr("compendium.brief.title"), x + 6, y + 82, ACCENT, false);
            int ly = y + 94;
            for (String[] r : rows) {
                boolean two = font.width(r[0]) + 6 + font.width(r[1]) > in;
                g.drawString(font, r[0], x + 6, ly + 2, MUTED, false);
                briefValue(g, mx, my, r, x + 6, x + w - 6, ly + (two ? 12 : 2));
                ly += two ? 24 : 14;
                g.fill(x + 6, ly - 1, x + w - 6, ly, EDGE);
            }
            briefButton(g, mx, my, x + 6, ly + 4, in);
            return h;
        }
        int img = 48, lx = x + img + 12, ly = y, labW = 62;
        rrect(g, x, y, x + img + 4, y + img + 4, EDGE);
        rrect(g, x + 1, y + 1, x + img + 3, y + img + 3, PANEL_BG);
        MachineThumbs.draw(g, nd.item(), x + 2, y + 2, img, 1f);
        for (String[] r : rows) {
            g.drawString(font, font.plainSubstrByWidth(r[0], labW - 2), lx, ly, MUTED, false);
            briefValue(g, mx, my, r, lx + labW, lx + labW, ly);
            ly += LINE_H;
        }
        briefButton(g, mx, my, lx, ly + 2, Math.min(120, font.width(tr("compendium.brief.recipe")) + 12));
        return Math.max(img + 4, ly + 18 - y);
    }

    private ItemStack proofStack(com.sqc.nuclearsq.progression.PlantOverview.Node nd) {
        ItemStack st = ItemStack.EMPTY;
        for (String id : com.sqc.nuclearsq.progression.AchievementTree.proofItems(nd).reversed()) {
            st = modItem(id);
            if (!sources(st.getItem()).isEmpty()) break;
        }
        return st;
    }

    private void briefValue(GuiGraphics g, int mx, int my, String[] r, int x0, int x1, int y) {
        String v = x1 > x0 ? font.plainSubstrByWidth(r[1], x1 - x0) : r[1];
        int vx = x1 > x0 ? x1 - font.width(v) : x0;
        boolean link = r.length > 2, hv = link && mx >= vx && mx < vx + font.width(v) && my >= y - 1 && my < y + 9;
        g.drawString(font, v, vx, y, link ? (hv ? 0xFFFFFFFF : CYAN) : TEXT, false);
        int[] tgt = link ? findPage(r[2]) : null;
        if (tgt != null) hits.add(new Hit(vx, y - 1, vx + font.width(v), y + 9, 0, tgt[0], tgt[1]));
    }

    private void briefButton(GuiGraphics g, int mx, int my, int bx, int by, int bw) {
        boolean hb = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        rrect(g, bx, by, bx + bw, by + 16, hb ? 0xFFFFC060 : ACCENT);
        String bt = font.plainSubstrByWidth(tr("compendium.brief.recipe"), bw - 4);
        g.drawString(font, bt, bx + (bw - font.width(bt)) / 2, by + 4, 0xFF14171D, false);
        hits.add(new Hit(bx, by, bx + bw, by + 16, 16, 0, 0));
    }

    private int drawMachineHead(GuiGraphics g, int mx, int my, com.sqc.nuclearsq.progression.PlantOverview.Node nd, int x, int y, int w) {
        int y0 = y;
        var shortLines = font.split(Component.literal(tr("compendium.plant." + nd.key() + ".short")), w);
        for (int i = 0; i < Math.min(2, shortLines.size()); i++) {
            g.drawString(font, shortLines.get(i), x, y, MUTED, false);
            y += 10;
        }
        y += 4;
        var line = lineOf(nd.key());
        if (line != null) {
            var nodes = com.sqc.nuclearsq.progression.LineTrees.nodes(line);
            int i = 0;
            while (i < nodes.size() && !nodes.get(i).key().equals(nd.key())) i++;
            rrect(g, x, y, x + w - 6, y + 22, CARD);
            int cx = x + 5;
            String tree = tr("compendium.brief.tree");
            int treeX = x + w - 12 - font.width(tree);
            for (int k = Math.max(0, i - 1); k <= Math.min(nodes.size() - 1, i + 1); k++) {
                var o = nodes.get(k);
                String n = tr("compendium.plant." + o.key() + ".name");
                int nw = Math.min(font.width(n), Math.max(20, (treeX - x - 30) / 3 - 26));
                if (cx + 20 + nw > treeX - 4) break;
                if (k != Math.max(0, i - 1)) {
                    g.drawString(font, "›", cx, y + 7, MUTED, false);
                    cx += 9;
                }
                boolean cur = k == i, hv = !cur && mx >= cx && mx < cx + 20 + nw && my >= y && my < y + 22;
                if (cur) rrect(g, cx - 2, y + 2, cx + 20 + nw + 2, y + 20, ACCENT);
                if (cur) rrect(g, cx - 1, y + 3, cx + 20 + nw + 1, y + 19, CARD);
                MachineThumbs.draw(g, o.item(), cx, y + 3, 16, 1f);
                g.drawString(font, font.plainSubstrByWidth(n, nw), cx + 19, y + 7, cur ? 0xFFFFFFFF : hv ? ACCENT : TEXT, false);
                int[] tgt = cur ? null : findPage(o.page());
                if (tgt != null) hits.add(new Hit(cx, y, cx + 20 + nw, y + 22, 21, com.sqc.nuclearsq.progression.AchievementTree.index(o.key()), 0));
                else if (!cur) hits.add(new Hit(cx, y, cx + 20 + nw, y + 22, 19, com.sqc.nuclearsq.progression.AchievementTree.index(o.key()), 0));
                cx += 20 + nw + 6;
            }
            boolean ht = mx >= treeX && mx < treeX + font.width(tree) && my >= y && my < y + 22;
            g.drawString(font, tree, treeX, y + 7, ht ? 0xFFFFFFFF : CYAN, false);
            int[] tgt = findPage(line.page());
            if (tgt != null) hits.add(new Hit(treeX, y, treeX + font.width(tree), y + 22, 0, tgt[0], tgt[1]));
            y += 28;
        }
        return y - y0 + drawTextTabs(g, mx, my, x, y, w);
    }

    private int drawTextTabs(GuiGraphics g, int mx, int my, int x, int y, int w) {
        int y0 = y;
        boolean has3d = machine3dId() != null;
        if (tabs.size() > 1 || has3d) {
            int textTabs = Math.max(1, tabs.size()), n = textTabs + (has3d ? 1 : 0), each = (w - 6) / n, tx = x;
            for (int k = 0; k < n; k++) {
                boolean threeD = k == textTabs;
                String t = threeD ? tr("compendium.tab.3d") : tabs.size() < 2 || tabs.get(k).get(0).isEmpty() ? tr("compendium.brief.overview") : rich(tabs.get(k).get(0)).getString();
                int tw = Math.min(font.width(t) + 12, each);
                boolean cur = threeD ? show3d : k == tab && !show3d, hv = mx >= tx && mx < tx + tw && my >= y && my < y + 15;
                if (cur || hv) g.fill(tx, y, tx + tw, y + 15, cur ? ACTIVE_BG : HOVER_BG);
                if (cur) g.fill(tx, y + 13, tx + tw, y + 15, ACCENT);
                g.drawString(font, font.plainSubstrByWidth(t, tw - 8), tx + 6, y + 4, cur ? 0xFFFFFFFF : MUTED, false);
                hits.add(new Hit(tx, y, tx + tw, y + 15, threeD ? 22 : 15, k, 0));
                tx += tw + 2;
            }
            g.fill(x, y + 15, x + w - 6, y + 16, EDGE);
            y += 20;
        }
        return y - y0;
    }

    private int drawHeadRow(GuiGraphics g, Page p, int x, int y, int w, int mx, int my, float t) {
        var machines = showItems() ? pageMachines(p) : new ArrayList<com.sqc.nuclearsq.progression.PlantOverview.Node>();
        var stacks = showItems() ? pageRecipeItems(p) : List.<ItemStack>of();
        var own = machineOf(p);
        if (own != null) machines.removeIf(o -> o.item().equals(own.item()));
        boolean hasAnim = p.anim() != Anim.NONE && !(own != null && p.anim() == Anim.MACHINE);
        int mw = machines.isEmpty() ? 0 : machineStripW(machines);
        int rw = stacks.isEmpty() ? 0 : recipeStripW(stacks);
        int stripW = mw + rw + (mw > 0 && rw > 0 ? 8 : 0);
        int ah = hasAnim ? animH(p.anim()) : 0;
        int total = 0;
        boolean sameRow = hasAnim && stripW > 0 && w - stripW - 8 >= MIN_HEAD_ANIM_W;
        int aw = hasAnim ? (sameRow ? w - stripW - 8 : w) : 0;
        if (hasAnim) {
            g.fillGradient(x, y, x + aw, y + ah, 0xFF161618, 0xFF1C1C20);
            g.fill(x, y, x + aw, y + 1, EDGE);
            g.fill(x, y + ah - 1, x + aw, y + ah, EDGE);
            g.fill(x, y, x + 1, y + ah, EDGE);
            g.fill(x + aw - 1, y, x + aw, y + ah, EDGE);
            g.enableScissor(x + 1, y + 1, x + aw - 1, y + ah - 1);
            drawAnim(g, p.anim(), x, y, aw, ah, t, mx, my);
            g.disableScissor();
            total = ah + 8;
        }
        int sx = sameRow ? x + aw + 8 : x;
        int sy = sameRow ? y : y + total;
        int rowH = 0;
        if (mw > 0) {
            rowH = drawMachineStrip(g, machines, sx, sy, mx, my);
            sx += mw + 8;
        }
        if (rw > 0) {
            if (mw > 0 && x + w - 6 - sx < recipeStripW(stacks.subList(0, Math.min(3, stacks.size())))) {
                sx = x;
                sy += rowH;
                total += rowH;
                rowH = 0;
            }
            rowH = Math.max(rowH, drawRecipeStrip(g, stacks, sx, sy, x + w - 6 - sx, mx, my, t));
        }
        if (sameRow) return Math.max(ah, rowH) + 8;
        return total + rowH;
    }

    private int drawMachineStrip(GuiGraphics g, List<com.sqc.nuclearsq.progression.PlantOverview.Node> shown, int x, int y, int mx, int my) {
        int sx = x;
        for (var nd : shown) {
            int cw = cardW(nd);
            String base = "compendium.plant." + nd.key();
            boolean over = mx >= sx && mx < sx + cw && my >= y && my < y + STRIP_CARD;
            int ix = sx + (cw - STRIP_CARD) / 2;
            rrect(g, ix, y, ix + STRIP_CARD, y + STRIP_CARD, over ? ACCENT : EDGE);
            rrect(g, ix + 1, y + 1, ix + STRIP_CARD - 1, y + STRIP_CARD - 1, CARD);
            MachineThumbs.draw(g, nd.item(), ix + 2, y + 2, STRIP_CARD - 4, 1f);
            g.drawCenteredString(font, tr(base + ".name"), sx + cw / 2, y + STRIP_CARD + 2, over ? 0xFFFFFFFF : MUTED);
            if (over) {
                List<FormattedCharSequence> tip = new ArrayList<>();
                tip.add(Component.literal(tr(base + ".name")).withStyle(ChatFormatting.BOLD).getVisualOrderText());
                tip.addAll(font.split(Component.literal(tr(base + ".short")), 200));
                loopTip = tip;
            }
            sx += cw + 6;
        }
        return STRIP_CARD + 14;
    }

    private int drawRecipeStrip(GuiGraphics g, List<ItemStack> stacks, int x, int y, int maxW, int mx, int my, float t) {
        int lw = font.width(tr("compendium.recipes")) + 16;
        int per = Math.max(1, Math.min(stacks.size(), (maxW - lw - 4) / 24)), rows = (stacks.size() + per - 1) / per;
        rrect(g, x, y - 2, x + lw + per * 24 + 4, y + rows * 24 - 2, CARD);
        g.drawString(font, tr("compendium.recipes"), x + 8, y + 7, ACCENT, false);
        for (int i = 0; i < stacks.size(); i++) {
            int sx = x + lw + i % per * 24, sy = y + i / per * 24;
            g.fill(sx - 2, sy - 1, sx + 18, sy + 19, EDGE);
            g.fill(sx - 1, sy, sx + 17, sy + 18, 0xFF14171D);
            item(g, stacks.get(i), sx, sy + 1, mx, my);
        }
        return 28 + (rows - 1) * 24;
    }

    private final java.util.Map<net.minecraft.world.item.Item, List<RecipeSources.Source>> sourceCache = new java.util.HashMap<>();

    private List<RecipeSources.Source> sources(net.minecraft.world.item.Item item) {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return sourceCache.computeIfAbsent(item, it -> RecipeSources.forItem(level.getRecipeManager(), level.registryAccess(), it));
    }

    private Component whereName(RecipeSources.Source s) {
        if (s.where() != RecipeSources.Where.OTHER) return Component.translatable(s.where().langKey());
        ItemStack m = RecipeSources.otherMachine(s.type());
        return m.isEmpty() ? Component.literal(s.type().toString()) : m.getHoverName();
    }

    private static final int PLACE_H = 26;

    private static void drawWhereIcon(GuiGraphics g, RecipeSources.Source s, int x, int y, int size) {
        if (s.where() != RecipeSources.Where.OTHER && s.where().icon.getNamespace().equals(com.sqc.nuclearsq.NuclearSQ.MODID)) {
            MachineThumbs.draw(g, s.where().icon.getPath(), x, y, size, 1f);
            return;
        }
        ItemStack icon = s.where() == RecipeSources.Where.OTHER ? RecipeSources.otherMachine(s.type()) : s.where().iconStack();
        if (!icon.isEmpty()) MachineThumbs.drawStack(g, icon, x + (size - 16) / 2, y + (size - 16) / 2);
    }

    private void drawRecipePanel(GuiGraphics g, ItemStack st, List<RecipeSources.Source> sources, int mx, int my) {
        RecipeSources.Source rec = sources.get(0);
        List<RecipeSources.Source> places = new ArrayList<>();
        for (var s : sources) {
            if (places.stream().noneMatch(o -> o.where() == s.where() && o.type().equals(s.type()))) places.add(s);
        }
        var needs = rec.needs();
        String kindKey = rec.where().shaped ? "compendium.recipe.shaped" : rec.where().assembler() ? "compendium.recipe.assembler" : null;
        String infoKey = "compendium.item." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        List<FormattedCharSequence> info = has(infoKey) ? font.split(Component.literal(tr(infoKey)), 190) : List.of();
        var shaped = rec.grid();
        int[] cnt = new int[9];
        if (shaped == null && rec.where().assembler() && !needs.isEmpty()) {
            List<net.minecraft.world.item.crafting.Ingredient> cells = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                cells.add(i < needs.size() ? needs.get(i).ingredient() : net.minecraft.world.item.crafting.Ingredient.EMPTY);
                if (i < needs.size()) cnt[i] = needs.get(i).count();
            }
            shaped = new RecipeSources.Grid(3, 3, cells);
        }
        int listH = 12 + places.size() * PLACE_H + (kindKey == null ? 4 : 14) + (shaped == null ? needs.size() * 18 : shaped.height() * PATTERN_CELL + 4) + 26;
        int w = 210, h = 26 + listH + (info.isEmpty() ? 0 : info.size() * 10 + 6);
        int x = Math.min(mx + 12, width - w - 4), y = Math.max(4, Math.min(my - 8, height - h - 4));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ACCENT);
        g.fill(x, y, x + w, y + h, 0xF00D0F14);
        g.drawString(font, st.getHoverName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), x + 6, y + 5, 0xFFFFFF, false);
        g.drawString(font, Component.translatable("compendium.made_in"), x + 6, y + 17, 0x999999, false);
        int ry = y + 28;
        for (var p : places) {
            g.fill(x + 6, ry, x + w - 6, ry + PLACE_H - 1, 0x40FFB020);
            drawWhereIcon(g, p, x + 7, ry + 1, PLACE_H - 3);
            g.drawString(font, whereName(p), x + PLACE_H + 10, ry + (PLACE_H - 9) / 2, ACCENT, false);
            ry += PLACE_H;
        }
        ry += 2;
        if (kindKey != null) {
            g.drawString(font, Component.translatable(kindKey), x + 6, ry, 0x999999, false);
            ry += 12;
        }
        ry += 2;
        int needsTop = ry;
        long ms = System.currentTimeMillis();
        if (shaped == null) for (var n : needs) {
            ItemStack d = n.display(ms);
            MachineThumbs.drawStack(g, d, x + 6, ry - 2);
            g.drawString(font, font.plainSubstrByWidth(n.count() + "x " + d.getHoverName().getString(), 176), x + 28, ry + 2, 0xDDDDDD, false);
            ry += 18;
        } else {
            for (int r = 0; r < shaped.height(); r++) {
                for (int c = 0; c < shaped.width(); c++) {
                    int cx = x + 6 + c * PATTERN_CELL, cy = ry - 2 + r * PATTERN_CELL;
                    g.fill(cx, cy, cx + PATTERN_CELL - 1, cy + PATTERN_CELL - 1, CARD);
                    ItemStack[] all = shaped.cell(r, c).getItems();
                    if (all.length > 0) MachineThumbs.drawStack(g, all[(int) ((ms / 1000) % all.length)], cx, cy);
                    int n = r * shaped.width() + c < cnt.length ? cnt[r * shaped.width() + c] : 0;
                    if (n > 1) {
                        g.pose().translate(0, 0, 200);
                        g.drawString(font, "" + n, cx + PATTERN_CELL - 1 - font.width("" + n), cy + PATTERN_CELL - 9, 0xFFFFFF, true);
                        g.pose().translate(0, 0, -200);
                    }
                }
            }
            ry += shaped.height() * PATTERN_CELL + 4;
        }
        g.drawString(font, "↓", x + 12, ry - 2, 0xFFC040, false);
        ItemStack out = rec.result();
        MachineThumbs.drawStack(g, out, x + 28, ry - 4);
        g.renderItemDecorations(font, out, x + 28, ry - 4);
        String chance = rec.chance() < 1f ? " (" + Math.round(rec.chance() * 100) + " %)" : "";
        g.drawString(font, out.getCount() + "x " + out.getHoverName().getString() + chance, x + 50, ry + 1, 0x7CFC7C, false);
        int iy = ry + 20;
        if (!info.isEmpty()) {
            for (FormattedCharSequence l : info) { g.drawString(font, l, x + 6, iy, 0xBBBBBB, false); iy += 10; }
            iy += 2;
        }
        g.drawString(font, tr("compendium.recipe_tree.hint"), x + 6, iy, ACCENT, false);
        g.pose().popPose();
    }

    private ItemStack recipeOf = ItemStack.EMPTY;
    private com.sqc.nuclearsq.progression.RecipeTree.Part recipeRoot;
    private final java.util.Set<String> recipeOpen = new java.util.HashSet<>();
    private final java.util.Map<net.minecraft.world.item.Item, Integer> recipeChoice = new java.util.HashMap<>();
    private record RecipeRow(com.sqc.nuclearsq.progression.RecipeTree.Part part, String path, int depth) {}
    private final List<RecipeRow> recipeRows = new ArrayList<>();
    private static final int RT_ROW = 20, RT_INDENT = 16, RT_W = 300, RT_SUM_W = 150;

    private void openRecipe(ItemStack st) {
        recipeOf = st.copyWithCount(1);
        recipeOpen.clear();
        recipeChoice.clear();
        plantZoom = 0;
        buildRecipe();
    }

    private void buildRecipe() {
        var level = Minecraft.getInstance().level;
        if (level == null) { recipeOf = ItemStack.EMPTY; return; }
        recipeChoice.replaceAll((it, k) -> k % Math.max(1, sources(it).size()));
        recipeRoot = com.sqc.nuclearsq.progression.RecipeTree.build(level.getRecipeManager(), level.registryAccess(), recipeOf, 1, recipeChoice);
    }

    private void collectRows(com.sqc.nuclearsq.progression.RecipeTree.Part p, String path, int depth) {
        recipeRows.add(new RecipeRow(p, path, depth));
        if (depth == 0 || recipeOpen.contains(path))
            for (int i = 0; i < p.children().size(); i++) collectRows(p.children().get(i), path + "." + i, depth + 1);
    }

    private static int have(net.minecraft.world.item.Item it) {
        var pl = Minecraft.getInstance().player;
        return pl == null ? 0 : pl.getInventory().countItem(it);
    }

    private void drawRecipeTree(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int bx = plantButton(g, mx, my, x + w, y, tr("compendium.recipe_tree.close"), 14);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("compendium.recipe_tree.title", recipeOf.getHoverName()).getString(), bx - x - 4), x, y + 3, ACCENT, false);
        y += 17;
        h -= 17;
        if (recipeRoot == null) return;
        recipeRows.clear();
        collectRows(recipeRoot, "0", 0);
        var totals = com.sqc.nuclearsq.progression.RecipeTree.rawTotals(recipeRoot);
        int worldW = RT_W + RT_SUM_W + 8, worldH = Math.max(recipeRows.size(), totals.size() + 1) * RT_ROW + 8;
        plantAX = x;
        plantAY = y;
        plantAW = w;
        plantAH = h;
        plantFit = Math.min(1.2f, Math.min(w / (float) worldW, h / (float) worldH));
        if (plantZoom <= 0) {
            plantZoom = Math.min(1.2f, w / (float) worldW);
            plantPanX = Math.max(0, (w - worldW * plantZoom) / 2);
            plantPanY = 0;
        }
        float z = plantZoom;
        float wmx = (mx - x - plantPanX) / z, wmy = (my - y - plantPanY) / z;
        boolean in = inPlant(mx, my);
        g.fillGradient(x, y, x + w, y + h, 0xFF161618, 0xFF1C1C20);
        g.enableScissor(x, y, x + w, y + h);
        g.pose().pushPose();
        g.pose().translate(x + plantPanX, y + plantPanY, 0);
        g.pose().scale(z, z, 1f);
        for (int r = 0; r < recipeRows.size(); r++) {
            var row = recipeRows.get(r);
            var part = row.part();
            int ry = 4 + r * RT_ROW, rx = 4 + row.depth() * RT_INDENT;
            if (r % 2 == 0) g.fill(0, ry - 2, RT_W, ry + RT_ROW - 2, 0x18FFFFFF);
            boolean open = row.depth() == 0 || recipeOpen.contains(row.path());
            if (!part.raw() && row.depth() > 0) {
                g.drawString(font, open ? "▼" : "▶", rx, ry + 4, ACCENT, false);
                hitWorld(x, y, z, rx - 2, ry, rx + 10, ry + 16, 12, r);
            }
            MachineThumbs.drawStack(g, part.stack(), rx + 12, ry);
            if (in && wmx >= rx + 12 && wmx < rx + 28 && wmy >= ry && wmy < ry + 16) hover = part.stack();
            int col = part.raw() ? (have(part.stack().getItem()) >= part.count() ? GREEN : 0xFFFF6060) : TEXT;
            String name = part.count() + "x " + part.stack().getHoverName().getString();
            g.drawString(font, font.plainSubstrByWidth(name, 150 - row.depth() * RT_INDENT), rx + 32, ry + 4, col, false);
            int wx = 190;
            if (part.raw() || part.source() == null) {
                g.drawString(font, tr("compendium.recipe_tree.raw"), wx, ry + 4, MUTED, false);
            } else {
                drawWhereIcon(g, part.source(), wx, ry - 1, 18);
                g.drawString(font, font.plainSubstrByWidth(whereName(part.source()).getString(), RT_W - wx - 24), wx + 20, ry + 4, CYAN, false);
                if (!part.alternatives().isEmpty()) {
                    int k = recipeChoice.getOrDefault(part.stack().getItem(), 0) + 1, n = part.alternatives().size() + 1;
                    String alt = tr("compendium.recipe_tree.recipe").formatted(k, n);
                    int ax = RT_W - font.width(alt) - 4;
                    g.fill(ax - 2, ry + 1, RT_W - 2, ry + 14, ACTIVE_BG);
                    g.drawString(font, alt, ax, ry + 4, ACCENT, false);
                    hitWorld(x, y, z, ax - 2, ry + 1, RT_W - 2, ry + 14, 13, r);
                }
            }
        }
        int sx = RT_W + 8;
        g.drawString(font, tr("compendium.recipe_tree.total"), sx, 8, ACCENT, false);
        int k = 0;
        for (var e : totals.entrySet()) {
            int ry = 4 + (k + 1) * RT_ROW;
            ItemStack st = new ItemStack(e.getKey());
            MachineThumbs.drawStack(g, st, sx, ry);
            if (in && wmx >= sx && wmx < sx + 16 && wmy >= ry && wmy < ry + 16) hover = st;
            int got = have(e.getKey());
            g.drawString(font, font.plainSubstrByWidth(e.getValue() + "x " + st.getHoverName().getString(), RT_SUM_W - 20), sx + 20, ry + 4,
                    got >= e.getValue() ? GREEN : 0xFFFF6060, false);
            k++;
        }
        g.pose().popPose();
        g.disableScissor();
    }

    private void hitWorld(int x, int y, float z, int wx0, int wy0, int wx1, int wy1, int type, int a) {
        int x0 = (int) (x + plantPanX + wx0 * z), y0 = (int) (y + plantPanY + wy0 * z), x1 = (int) (x + plantPanX + wx1 * z), y1 = (int) (y + plantPanY + wy1 * z);
        x0 = Math.max(x0, plantAX); y0 = Math.max(y0, plantAY); x1 = Math.min(x1, plantAX + plantAW); y1 = Math.min(y1, plantAY + plantAH);
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1, y1, type, a, 0));
    }

    private static int nextGoal() {
        var nodes = com.sqc.nuclearsq.progression.AchievementTree.NODES;
        for (int k = 0; k < nodes.size(); k++) if (!builtNode(nodes.get(k))) return k;
        return -1;
    }

    private static boolean builtNode(com.sqc.nuclearsq.progression.PlantOverview.Node n) {
        for (String id : com.sqc.nuclearsq.progression.AchievementTree.proofItems(n)) if (com.sqc.nuclearsq.progression.Discovered.known(id)) return true;
        return false;
    }

    private void drawLineTiles(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        var all = com.sqc.nuclearsq.progression.AchievementTree.NODES;
        int goal = nextGoal(), built = 0, items = 0, known = 0;
        for (var nd : all) if (builtNode(nd)) built++;
        var level = Minecraft.getInstance().level;
        if (level != null)
            for (var e : com.sqc.nuclearsq.progression.ItemOverview.get(level.getRecipeManager(), level.registryAccess())) {
                items++;
                if (com.sqc.nuclearsq.progression.Discovered.known(e.item())) known++;
            }
        g.drawString(font, font.plainSubstrByWidth(tr("compendium.lines.count").formatted(built, all.size(), known, items), w), x, y, MUTED, false);
        y += 14;
        h -= 14;
        var goalLine = goal < 0 ? null : lineOf(all.get(goal).key());
        String toTree = tr("compendium.lines.to_tree");
        int ttx = x + w - 8 - font.width(toTree);
        if (goalLine != null) {
            int[] tl = findPage(goalLine.page());
            if (tl != null) hits.add(new Hit(ttx, y, x + w, y + 24, 0, tl[0], tl[1]));
        }
        String head = goal < 0 ? tr("compendium.lines.complete") : tr("compendium.lines.goal_head").formatted(tr("compendium.plant." + all.get(goal).key() + ".name"));
        boolean hg = goal >= 0 && mx >= x && mx < x + w && my >= y && my < y + 24;
        rrect(g, x, y, x + w, y + 24, hg ? ACCENT : EDGE);
        rrect(g, x + 1, y + 1, x + w - 1, y + 23, hg ? ACTIVE_BG : CARD);
        if (goal >= 0) {
            MachineThumbs.draw(g, all.get(goal).item(), x + 4, y + 4, 16, 1f);
            int[] tgt = findPage(all.get(goal).page());
            if (tgt != null) hits.add(new Hit(x, y, x + w, y + 24, 0, tgt[0], tgt[1]));
        }
        g.drawString(font, font.plainSubstrByWidth(head, ttx - x - 30), x + 24, y + 8, ACCENT, false);
        if (goalLine != null) {
            boolean ht = mx >= ttx && mx < x + w && my >= y && my < y + 24;
            g.drawString(font, toTree, ttx, y + 8, ht ? 0xFFFFFFFF : CYAN, false);
        }
        y += 30;
        h -= 30;
        var lines = com.sqc.nuclearsq.progression.LineTrees.LINES;
        int cols = Math.max(1, Math.min(3, w / 160)), rows = (lines.size() + cols - 1) / cols;
        int cw = (w - (cols - 1) * 6) / cols, ch = Math.max(30, Math.min(44, h / rows - 4));
        for (int i = 0; i < lines.size(); i++) {
            var l = lines.get(i);
            var nodes = com.sqc.nuclearsq.progression.LineTrees.nodes(l);
            int done = 0;
            com.sqc.nuclearsq.progression.PlantOverview.Node next = null;
            for (var nd : nodes) {
                if (builtNode(nd)) done++;
                else if (next == null) next = nd;
            }
            int tx = x + (i % cols) * (cw + 6), ty = y + (i / cols) * (ch + 4);
            if (ty + ch > y + h) break;
            boolean hv = mx >= tx && mx < tx + cw && my >= ty && my < ty + ch;
            rrect(g, tx, ty, tx + cw, ty + ch, hv ? ACCENT : done == nodes.size() ? GREEN : EDGE);
            rrect(g, tx + 1, ty + 1, tx + cw - 1, ty + ch - 1, hv ? ACTIVE_BG : CARD);
            g.drawString(font, font.plainSubstrByWidth(tr("compendium." + l.page() + ".title"), cw - 50), tx + 5, ty + 4, 0xFFFFFFFF, false);
            String d = done + "/" + nodes.size();
            g.drawString(font, d, tx + cw - 5 - font.width(d), ty + 4, MUTED, false);
            g.fill(tx + 5, ty + 14, tx + cw - 5, ty + 17, PANEL_BG);
            g.fill(tx + 5, ty + 14, tx + 5 + (cw - 10) * done / Math.max(1, nodes.size()), ty + 17, ACCENT);
            if (ch >= 36 || next == null) {
                String ns = next == null ? tr("compendium.lines.complete") : tr("compendium.lines.next_step").formatted(tr("compendium.plant." + next.key() + ".name"));
                if (next != null) MachineThumbs.draw(g, next.item(), tx + 5, ty + ch - 14, 10, 1f);
                g.drawString(font, font.plainSubstrByWidth(ns, cw - 22), tx + (next != null ? 18 : 5), ty + ch - 12, MUTED, false);
            }
            int[] tgt = findPage(l.page());
            if (tgt != null) hits.add(new Hit(tx, ty, tx + cw, ty + ch, 0, tgt[0], tgt[1]));
        }
    }

    private int drawPageTabs(GuiGraphics g, int mx, int my, int x, int y, int w) {
        var pages = CHAPTERS.get(chapter).pages();
        int n = pages.size(), each = w / Math.max(1, n), tx = x;
        for (int k = 0; k < n; k++) {
            String t = rich(tr("compendium." + pages.get(k).id() + ".title")).getString();
            int tw = Math.min(font.width(t) + 12, each - 2);
            boolean cur = k == page, hv = mx >= tx && mx < tx + tw && my >= y && my < y + 15;
            if (cur || hv) g.fill(tx, y, tx + tw, y + 15, cur ? ACTIVE_BG : HOVER_BG);
            if (cur) g.fill(tx, y + 13, tx + tw, y + 15, ACCENT);
            g.drawString(font, font.plainSubstrByWidth(t, tw - 8), tx + 6, y + 4, cur ? 0xFFFFFFFF : MUTED, false);
            hits.add(new Hit(tx, y, tx + tw, y + 15, 0, chapter, k));
            tx += tw + 2;
        }
        g.fill(x, y + 15, x + w, y + 16, EDGE);
        return 20;
    }

    private static final int PATTERN_CELL = 17;

    private void drawText(GuiGraphics g, int x, int y, int w, int h) {
        int tipH = tipLines.isEmpty() ? 0 : tipLines.size() * LINE_H + 22;
        int textH = Math.max(LINE_H, h - tipH - (tipH > 0 ? 6 : 0));
        textVisible = textH;
        int fit = textH + (tabs.size() > 1 && machine3dId() == null ? 20 : 0);
        if (fit != fitH) {
            fitH = fit;
            layoutText();
        }
        scroll = Math.min(scroll, Math.max(0, textTotal + headOff - textH));
        int tw = w - 10;
        g.enableScissor(x, y, x + w, y + textH);
        int ty = y - scroll + headOff, ty0 = ty;
        for (int[] c : cards) {
            int cy = ty + c[0];
            if (cy + c[1] < y || cy > y + textH) continue;
            rrect(g, x, cy, x + w - 6, cy + c[1], EDGE);
            rrect(g, x + 1, cy + 1, x + w - 7, cy + c[1] - 1, c[2] == 3 ? ACTIVE_BG : CARD);
            if (c[2] == 1 || c[2] == 2) g.fill(x, cy + 1, x + 2, cy + c[1] - 1, c[2] == 1 ? CYAN : 0xFF000000 | c[3]);
        }
        for (Ln l : lines) {
            if (l.kind() == 2) {
                if ("-".equals(l.mark()) && ty >= y && ty <= y + textH) g.fill(x + 4, ty + 1, x + w - 10, ty + 2, EDGE);
                ty += l.indent();
                continue;
            }
            int lh = lineH(l);
            if (ty + lh >= y && ty <= y + textH) {
                int lx = l.kind() == 4 ? x + 16 : x + 4 + l.indent();
                int dy = l.kind() == 4 ? 2 : l.kind() == 5 ? 6 : 0;
                if (l.kind() == 4) {
                    g.drawString(font, l.mark(), x + 6, ty + dy, ACCENT, false);
                    int y0 = Math.max(ty, y), y1 = Math.min(ty + lh, y + textH);
                    if (y1 > y0) hits.add(new Hit(x, y0, x + w - 6, y1, 9, l.indent(), 0));
                }
                markFinds(g, l.seq(), lx, ty + dy);
                g.drawString(font, l.seq(), lx, ty + dy, TEXT, false);
                if (mouseY >= Math.max(ty, y) && mouseY < Math.min(ty + lh, y + textH) && mouseX >= lx) {
                    hoverTip(l.seq(), mouseX - lx);
                    Style st = font.getSplitter().componentStyleAtWidth(l.seq(), mouseX - lx);
                    if (st != null && st.getInsertion() != null && st.getInsertion().startsWith(LINK)) linkHover = Integer.parseInt(st.getInsertion().substring(LINK.length()));
                }
            }
            ty += lh;
        }
        for (Ico c : icons) {
            int ix = x + c.x(), iy = ty0 + c.y();
            if (iy + c.size() < y || iy > y + textH) continue;
            g.pose().pushPose();
            g.pose().translate(ix, iy, 0);
            g.pose().scale(c.size() / 16f, c.size() / 16f, 1f);
            MachineThumbs.drawStack(g, c.st(), 0, 0);
            g.pose().popPose();
            if (mouseX >= ix && mouseX < ix + c.size() && mouseY >= Math.max(iy, y) && mouseY < Math.min(iy + c.size(), y + textH)) hover = c.st();
        }
        g.disableScissor();
        if (linkHover >= 0) loopTip = linkTip(links.get(linkHover));
        int total = textTotal + headOff;
        if (total > textH) {
            int trackH = textH;
            int thumbH = Math.max(14, trackH * textH / total);
            int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, total - textH);
            bar.track(x + w - 5, y, 5, trackH, thumbH, total - textH);
            g.fill(x + w - 5, y, x + w, y + trackH, 0xFF1E222B);
            g.fill(x + w - 5, thumbY, x + w, thumbY + thumbH, 0xFF5A6478);
            if (scroll < total - textH - 2) g.drawString(font, "\u25BC", x + w - 16, y + textH - 11, ACCENT, false);
        }
        if (!tipLines.isEmpty()) {
            int by = y + h - tipH;
            rrect(g, x, by, x + w, by + tipH, CARD);
            g.fill(x, by + 2, x + 3, by + tipH - 2, ACCENT);
            g.drawString(font, Component.translatable("compendium.tip").withStyle(ChatFormatting.BOLD), x + 10, by + 5, ACCENT, false);
            int ly = by + 17;
            for (FormattedCharSequence l : tipLines) {
                markFinds(g, l, x + 10, ly);
                g.drawString(font, l, x + 10, ly, TEXT, false);
                if (mouseY >= ly && mouseY < ly + LINE_H) hoverTip(l, mouseX - x - 10);
                ly += LINE_H;
            }
        }
    }

    private List<FormattedCharSequence> linkTip(com.sqc.nuclearsq.progression.PlantOverview.Node nd) {
        String base = "compendium.plant." + nd.key();
        List<FormattedCharSequence> tip = new ArrayList<>();
        tip.add(Component.literal(tr(base + ".name")).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD).getVisualOrderText());
        tip.addAll(font.split(Component.literal(tr(base + ".short")), 220));
        tip.add(Component.literal(tr("compendium.plant.open")).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        return tip;
    }

    private void drawTree(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        int hintLines = 0;
        if (has("compendium." + p.id() + ".text")) {
            g.drawString(font, tr("compendium." + p.id() + ".text").split("\n")[0], x, y, MUTED, false);
            hintLines = 14;
        }
        y += hintLines;
        h -= hintLines;
        int cols = 0;
        for (Row row : rows) cols = Math.max(cols, row.nodes().size());
        int worldW = LABEL_W + cols * COL_W + 4, worldH = rows.size() * ROW_H + 4;
        plantAX = x;
        plantAY = y;
        plantAW = w;
        plantAH = h;
        plantFit = Math.min(1.2f, Math.min(w / (float) worldW, h / (float) worldH));
        if (plantZoom <= 0) {
            plantZoom = plantFit;
            plantPanX = Math.max(0, (w - worldW * plantZoom) / 2);
            plantPanY = 0;
        }
        float z = plantZoom;
        float wmx = (mx - x - plantPanX) / z, wmy = (my - y - plantPanY) / z;
        boolean in = inPlant(mx, my);
        g.enableScissor(x, y, x + w, y + h);
        g.pose().pushPose();
        g.pose().translate(x + plantPanX, y + plantPanY, 0);
        g.pose().scale(z, z, 1f);
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int ry = 2 + r * ROW_H;
            g.drawString(font, Component.translatable("gui.nuclearsq.tree.row." + row.key()), 0, ry + 8, TEXT, false);
            for (int c = 0; c < row.nodes().size(); c++) {
                int cx = LABEL_W + c * COL_W;
                ItemLike it = row.nodes().get(c).get();
                boolean has = com.sqc.nuclearsq.progression.Discovered.known(it.asItem());
                if (c > 0) g.fill(cx - (COL_W - CELL), ry + CELL / 2, cx, ry + CELL / 2 + 1, EDGE);
                rrect(g, cx, ry, cx + CELL, ry + CELL, has ? GREEN : NEVER);
                rrect(g, cx + 1, ry + 1, cx + CELL - 1, ry + CELL - 1, CARD);
                ItemStack st = new ItemStack(it);
                MachineThumbs.drawStack(g, st, cx + 4, ry + 4);
                if (in && wmx >= cx && wmx < cx + CELL && wmy >= ry && wmy < ry + CELL) hover = st;
            }
        }
        g.pose().popPose();
        g.disableScissor();
    }

    private static final float LOOP_STEP = 0.4f;
    private static final int PLANT_CARD = 36, STRIP_CARD = 44, MIN_HEAD_ANIM_W = 150;
    private static final int LOOP_RING = 0xFF3A8FB0, LOOP_BRANCH = 0xFF9A7020;

    private static int[] findPage(String id) {
        for (int c = 0; c < CHAPTERS.size(); c++) {
            List<Page> ps = CHAPTERS.get(c).pages();
            for (int k = 0; k < ps.size(); k++) if (ps.get(k).id().equals(id)) return new int[] {c, k};
        }
        return null;
    }

    private static void segment(GuiGraphics g, int ax, int ay, int bx, int by, float f, int argb) {
        if (f <= 0) return;
        double len = Math.hypot(bx - ax, by - ay) * f;
        double ux = (bx - ax) / Math.max(1e-6, Math.hypot(bx - ax, by - ay)), uy = (by - ay) / Math.max(1e-6, Math.hypot(bx - ax, by - ay));
        for (double d = 0; d <= len; d += 1) {
            int x = (int) Math.round(ax + ux * d), y = (int) Math.round(ay + uy * d);
            g.fill(x - 1, y - 1, x + 1, y + 1, argb);
        }
    }

    private void drawLoop(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        if (plantStart == 0) plantStart = System.currentTimeMillis();
        drawPrimary(g, mx, my, x, y, w, h, p, (System.currentTimeMillis() - plantStart) / 1000f,
                switch (p.anim()) {
                    case PRIMARY -> com.sqc.nuclearsq.progression.PrimaryLoop.DIAGRAM;
                    case PRIMARY_EXT -> com.sqc.nuclearsq.progression.PlantLoops.EXTENDED;
                    case PRIMARY_FULL -> com.sqc.nuclearsq.progression.PlantLoops.FULL;
                    default -> com.sqc.nuclearsq.progression.ReactorLoop.DIAGRAM;
                });
    }

    private static final int PRIMARY_HOT = 0xFFD86A3A, PRIMARY_FILL = 0xFF8AA8D0, PRIMARY_WATER = 0xFF5A9FE0,
            PRIMARY_FEED = 0xFF6FC8B8, PRIMARY_STEAM = 0xFFC8C8CC, PRIMARY_HULL = 0xFF6A6A70;

    private static int flowColor(String kind) {
        return switch (kind) {
            case "hot", "relief" -> PRIMARY_HOT;
            case "cold" -> LOOP_RING;
            case "fill" -> PRIMARY_FILL;
            case "water", "bigpipe", "hydraulic" -> PRIMARY_WATER;
            case "feedwater" -> PRIMARY_FEED;
            case "coolant" -> 0xFF8FD6E8;
            case "power" -> ACCENT;
            case "shaft" -> 0xFFB8A48C;
            case "rods" -> 0xFFC89A40;
            default -> PRIMARY_STEAM;
        };
    }

    private static double pathLength(int[] pts) {
        double l = 0;
        for (int i = 2; i < pts.length; i += 2) l += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
        return l;
    }

    private static int[] pathPoint(int[] pts, double d) {
        for (int i = 2; i < pts.length; i += 2) {
            double s = Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
            if (d <= s || i == pts.length - 2) {
                double f = s <= 0 ? 0 : Math.min(1, d / s);
                return new int[] {(int) Math.round(pts[i - 2] + (pts[i] - pts[i - 2]) * f), (int) Math.round(pts[i - 1] + (pts[i + 1] - pts[i - 1]) * f)};
            }
            d -= s;
        }
        return new int[] {pts[0], pts[1]};
    }

    private static void thickPath(GuiGraphics g, int[] pts, float f, int r, int argb) {
        double len = pathLength(pts) * f;
        for (double d = 0; d <= len; d += 1) {
            int[] q = pathPoint(pts, d);
            g.fill(q[0] - r, q[1] - r, q[0] + r + (r == 0 ? 1 : 0), q[1] + r + (r == 0 ? 1 : 0), argb);
        }
    }

    private static void flowArrow(GuiGraphics g, int[] pts, int argb) { flowArrow(g, pts, argb, false); }

    private static void flowArrow(GuiGraphics g, int[] pts, int argb, boolean atEnd) {
        int best = pts.length - 2;
        double bl = Math.hypot(pts[best] - pts[best - 2], pts[best + 1] - pts[best - 1]);
        if (!atEnd) {
            bl = -1;
            for (int i = 2; i < pts.length; i += 2) {
                double s = Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
                if (s > bl) { bl = s; best = i; }
            }
        }
        double ux = (pts[best] - pts[best - 2]) / Math.max(1e-6, bl), uy = (pts[best + 1] - pts[best - 1]) / Math.max(1e-6, bl);
        double cx = atEnd ? pts[best] - ux * (PLANT_CARD / 2 + 6) : (pts[best] + pts[best - 2]) / 2.0, cy = atEnd ? pts[best + 1] - uy * (PLANT_CARD / 2 + 6) : (pts[best + 1] + pts[best - 1]) / 2.0;
        for (int t = 0; t <= 4; t++) {
            int half = 4 - t;
            for (int s = -half; s <= half; s++) {
                int px = (int) Math.round(cx + ux * (t - 2) - uy * s), py = (int) Math.round(cy + uy * (t - 2) + ux * s);
                g.fill(px, py, px + 1, py + 1, argb);
            }
        }
    }

    private void drawPrimary(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p, float el, com.sqc.nuclearsq.progression.PrimaryLoop.Diagram d) {
        var nodes = d.nodes();
        var flows = d.flows();
        int[] order = d.order();
        int n = nodes.size();
        int bx = plantButton(g, mx, my, x + w, y, tr("compendium.plant.reset"), 4);
        bx = plantButton(g, mx, my, bx, y, tr("compendium.plant.replay"), 3);
        if (has("compendium." + p.id() + ".text"))
            g.drawString(font, font.plainSubstrByWidth(tr("compendium." + p.id() + ".text").split("\n")[0], bx - x - 4), x, y + 3, MUTED, false);
        y += 17;
        h -= 17;
        g.fillGradient(x, y, x + w, y + h, 0xFF161618, 0xFF1C1C20);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);

        plantAX = x;
        plantAY = y;
        plantAW = w;
        plantAH = h;
        plantFit = Math.min(1f, Math.min((w - 8) / (float) d.width(), (h - 8) / (float) d.height()));
        if (plantZoom <= 0) {
            plantZoom = plantFit;
            plantPanX = (w - d.width() * plantZoom) / 2;
            plantPanY = (h - d.height() * plantZoom) / 2;
        }
        float z = plantZoom, ox = x + plantPanX, oy = y + plantPanY;
        float wmx = (mx - ox) / z, wmy = (my - oy) / z;
        boolean in = inPlant(mx, my);
        int[] step = new int[n];
        for (int s = 0; s < order.length; s++) step[order[s]] = s;
        float[] shown = new float[n];
        for (int k = 0; k < n; k++) shown[k] = ease((el - step[k] * LOOP_STEP - 0.12f) / 0.28f);
        float done = order.length * LOOP_STEP + 0.4f;

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(z, z, 1f);

        int hull = d.hull();
        if (hull >= 0) hullFrame(g, d.hullRect(), (int) (255 * shown[hull]));

        for (var fl : flows) {
            int[] q = fl.path();
            float t0 = Math.max(step[fl.from()], step[fl.to()]) * LOOP_STEP;
            float grow = ease((el - t0) / 0.35f);
            if (grow <= 0) continue;
            int col = flowColor(fl.kind());
            if (fl.kind().equals("bigpipe")) {
                thickPath(g, q, grow, 3, 0xFF55585E);
                thickPath(g, q, grow, 2, 0xFF7A7E86);
                thickPath(g, q, grow, 0, 0xFF303236);
            } else {
                thickPath(g, q, grow, 1, col);
            }
            if (grow >= 1f) flowArrow(g, q, col);
            int la = (int) (255 * ease((el - t0 - 0.2f) / 0.4f));
            if (la > 8) {
                var rows = font.split(Component.literal(tr("compendium.loop.pwr_flow." + fl.kind())), fl.lw());
                int lc = rows.size();
                for (int r = 0; r < lc; r++) {
                    int lw = font.width(rows.get(r));
                    int left = fl.align() < 0 ? fl.lx() : fl.align() > 0 ? fl.lx() - lw : fl.lx() - lw / 2;
                    int ly = fl.align() == 0 ? fl.ly() - 4 - (lc - 1 - r) * 10 : fl.ly() - 4 - (lc - 1) * 5 + r * 10;
                    g.drawString(font, rows.get(r), left, ly, alpha(la, fl.kind().equals("bigpipe") ? TEXT : col), false);
                    if (in && fl.kind().equals("hydraulic") && wmx >= left - 1 && wmx < left + lw + 1 && wmy >= ly - 1 && wmy < ly + 9) loopTip = hydraulicTip();
                }
            }
        }
        if (el > done) {
            float u = ((el - done) * 0.25f) % 1f;
            for (var fl : flows) {
                int[] pt = pathPoint(fl.path(), pathLength(fl.path()) * u);
                circle(g, pt[0], pt[1], 2, flowColor(fl.kind()) | 0xFF000000);
            }
        }

        for (int k = 0; k < n; k++) {
            if (shown[k] <= 0.02f) continue;
            var nd = nodes.get(k);
            boolean isHull = k == hull;
            int size = isHull ? 22 : PLANT_CARD;
            int cx = nd.x(), cy = nd.y();
            int a = (int) (255 * shown[k]);
            int x0 = cx - size / 2, y0 = cy - size / 2;
            boolean over = in && shown[k] > 0.6f && wmx >= x0 && wmx < x0 + size && wmy >= y0 && wmy < y0 + size;
            int border = over ? ACCENT : isHull ? PRIMARY_HULL : k < d.ring() ? LOOP_RING : LOOP_BRANCH;
            rrect(g, x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, alpha(a, border));
            rrect(g, x0, y0, x0 + size, y0 + size, alpha(a, over ? ACTIVE_BG : CARD));
            if (shown[k] > 0.45f) MachineThumbs.draw(g, nd.item(), x0 + 1, y0 + 1, size - 2, z);
            String base = "compendium.loop." + nd.key();
            if (a > 8) {
                String num = String.valueOf(step[k] + 1);
                g.pose().pushPose();
                g.pose().translate(x0 - 3, y0 - 4, 200);
                g.fill(-2, -1, font.width(num) + 1, 9, alpha(a, border));
                g.drawString(font, num, 0, 0, alpha(a, 0x14171D), false);
                g.pose().popPose();
                if (isHull) {
                    g.drawString(font, tr(base + ".name"), x0 + size + 4, cy - 4, alpha(a, over ? 0xFFFFFF : MUTED), false);
                } else {
                    int bg = alpha(a * 9 / 10, BG), ny = y0 + size + 2;
                    for (var row : font.split(Component.literal(tr(base + ".name")), nd.nameW())) {
                        int hw = font.width(row) / 2 + 2;
                        g.fill(cx - hw, ny, cx + hw, ny + 10, bg);
                        g.drawString(font, row, cx - font.width(row) / 2, ny + 1, alpha(a, over ? 0xFFFFFF : TEXT), false);
                        ny += 10;
                    }
                }
            }
            if (over) {
                List<FormattedCharSequence> tip = new ArrayList<>();
                tip.add(Component.literal(tr(base + ".name")).withStyle(ChatFormatting.BOLD).getVisualOrderText());
                tip.addAll(font.split(Component.literal(tr(base + ".short")).withStyle(ChatFormatting.GRAY), 200));
                tip.addAll(font.split(Component.literal(tr(base + ".hint")), 200));
                int[] target = findPage(nd.page());
                if (target != null) {
                    tip.add(Component.literal(tr("compendium.loop.open")).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                    hits.add(new Hit(Math.max(x, (int) (ox + x0 * z)), Math.max(y, (int) (oy + y0 * z)), Math.min(x + w, (int) Math.ceil(ox + (x0 + size) * z)), Math.min(y + h, (int) Math.ceil(oy + (y0 + size) * z)), 0, target[0], target[1]));
                }
                loopTip = tip;
            }
        }
        g.pose().popPose();
        if (el > done) {
            int ca = (int) (255 * ease((el - done) / 0.6f));
            String closed = tr("compendium.plant.closed");
            if (ca > 8) g.drawString(font, closed, x + w - font.width(closed) - 6, y + h - 12, alpha(ca, ACCENT), false);
        }
        g.disableScissor();
    }

    private static void hullFrame(GuiGraphics g, int[] r, int ha) {
        if (ha <= 8) return;
        int x0 = r[0], y0 = r[1], x1 = r[2], y1 = r[3], c = alpha(ha, PRIMARY_HULL);
        g.fill(x0 + 3, y0, x1 - 3, y0 + 1, c);
        g.fill(x0 + 3, y1 - 1, x1 - 3, y1, c);
        g.fill(x0, y0 + 3, x0 + 1, y1 - 3, c);
        g.fill(x1 - 1, y0 + 3, x1, y1 - 3, c);
        g.fill(x0 + 1, y0 + 1, x0 + 3, y0 + 3, c);
        g.fill(x1 - 3, y0 + 1, x1 - 1, y0 + 3, c);
        g.fill(x0 + 1, y1 - 3, x0 + 3, y1 - 1, c);
        g.fill(x1 - 3, y1 - 3, x1 - 1, y1 - 1, c);
    }

    private List<FormattedCharSequence> hydraulicTip() {
        var drive = com.sqc.nuclearsq.machine.big.BigMachine.CONTROL_ROD_DRIVE;
        var port = drive.port("hydraulic_in");
        List<FormattedCharSequence> tip = new ArrayList<>();
        tip.add(Component.literal(tr("compendium.loop.pwr_flow.hydraulic")).withStyle(ChatFormatting.BOLD).getVisualOrderText());
        tip.addAll(font.split(Component.literal(tr("compendium.loop.pwr_flow.hydraulic.hint").formatted(
                port.rate() * com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.ROD_STEP_TICKS, port.tank())), 220));
        tip.addAll(font.split(Component.literal(tr("compendium.fluids.accepts").formatted(acceptedFluids(drive, port))).withStyle(ChatFormatting.GRAY), 220));
        return tip;
    }

    private static String acceptedFluids(com.sqc.nuclearsq.machine.big.BigMachine machine, com.sqc.nuclearsq.machine.big.BigMachine.Port port) {
        var ok = com.sqc.nuclearsq.machine.big.BigMachineBlockEntity.accepts(machine, port);
        List<String> names = new ArrayList<>();
        for (var f : com.sqc.nuclearsq.progression.ReactorFluids.FLUIDS) {
            var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(f.fluid()));
            if (fluid != net.minecraft.world.level.material.Fluids.EMPTY && ok.test(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1))) names.add(tr(f.nameKey()));
        }
        return String.join(", ", names);
    }

    private int fluidSel = 0;
    private static final int FLUID_ROW = 22, FLUID_GAP = 8, FLUID_THUMB = 18;

    private void drawFluids(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        var fluids = com.sqc.nuclearsq.progression.ReactorFluids.FLUIDS;
        fluidSel = Math.max(0, Math.min(fluidSel, fluids.size() - 1));
        int bottom = y + h;
        if (has("compendium." + p.id() + ".text")) {
            for (FormattedCharSequence ln : font.split(rich(tr("compendium." + p.id() + ".text")), w)) {
                g.drawString(font, ln, x, y, MUTED, false);
                y += 10;
            }
            y += 4;
        }
        int lw = Math.min(118, w / 4), ly = y;
        for (int i = 0; i < fluids.size(); i++) {
            var f = fluids.get(i);
            boolean sel = i == fluidSel, over = mx >= x && mx < x + lw && my >= ly && my < ly + 13;
            if (sel || over) rrect(g, x, ly, x + lw, ly + 13, sel ? ACTIVE_BG : HOVER_BG);
            g.fill(x + 3, ly + 3, x + 10, ly + 10, f.color() | 0xFF000000);
            String name = font.plainSubstrByWidth(tr(f.nameKey()), lw - 16);
            g.drawString(font, name, x + 14, ly + 3, sel ? ACCENT : over ? 0xFFFFFFFF : TEXT, false);
            hits.add(new Hit(x, ly, x + lw, ly + 13, 20, i, 0));
            ly += 14;
        }

        var f = fluids.get(fluidSel);
        int rx = x + lw + 10, rw = w - lw - 10, col = f.color() | 0xFF000000;
        g.fill(rx - 6, y, rx - 5, bottom, EDGE);
        g.fill(rx, y + 1, rx + 8, y + 9, col);
        g.drawString(font, Component.literal(tr(f.nameKey())).withStyle(ChatFormatting.BOLD), rx + 12, y + 1, 0xFFFFFFFF, false);
        y += 13;
        String dk = "compendium.fluids." + f.key() + ".text";
        if (has(dk)) {
            for (FormattedCharSequence ln : font.split(rich(tr(dk)), rw - 6)) {
                g.drawString(font, ln, rx, y, TEXT, false);
                y += 10;
            }
        }
        y += 4;
        int cw = (rw - 46) / 2, tx = rx + cw + 40;
        g.drawString(font, tr("compendium.fluids.from"), rx, y, MUTED, false);
        g.drawString(font, tr("compendium.fluids.to"), tx, y, MUTED, false);
        y += 12;
        int top = y, vh = bottom - top, total = 0;
        for (var link : f.links()) total += Math.max(1, link.to().size()) * FLUID_ROW + FLUID_GAP;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - vh)));
        g.enableScissor(rx, top, rx + rw, bottom);
        int cy = top - scroll;
        for (var link : f.links()) {
            int n = Math.max(1, link.to().size()), gh = n * FLUID_ROW;
            int sy = cy + (gh - FLUID_ROW) / 2;
            fluidCard(g, mx, my, link.from(), rx, sy, cw, top, bottom, f);
            int sx1 = rx + cw, bx = sx1 + 14, mid = sy + FLUID_ROW / 2 - 1;
            g.fill(sx1, mid, bx, mid + 2, col);
            int first = cy + FLUID_ROW / 2 - 1, last = cy + (n - 1) * FLUID_ROW + FLUID_ROW / 2 - 1;
            if (n > 1) g.fill(bx - 1, first, bx + 1, last + 2, col);
            for (int k = 0; k < link.to().size(); k++) {
                int ty = cy + k * FLUID_ROW, ay = ty + FLUID_ROW / 2 - 1;
                g.fill(bx - 1, ay, tx - 5, ay + 2, col);
                for (int t = 0; t < 4; t++) g.fill(tx - 5 + t, ay - 3 + t, tx - 4 + t, ay + 5 - t, col);
                fluidCard(g, mx, my, link.to().get(k), tx, ty, cw, top, bottom, f);
            }
            cy += gh + FLUID_GAP;
            if (cy - FLUID_GAP / 2 < bottom) g.fill(rx, cy - FLUID_GAP / 2 - 1, rx + rw - 6, cy - FLUID_GAP / 2, 0xFF2C2C30);
        }
        g.disableScissor();
        scrollBar(g, rx, top, rw, vh, total);
    }

    private void fluidCard(GuiGraphics g, int mx, int my, com.sqc.nuclearsq.progression.ReactorFluids.End e, int x, int y, int w, int top, int bottom,
            com.sqc.nuclearsq.progression.ReactorFluids.Fluid f) {
        if (y + FLUID_ROW <= top || y >= bottom) return;
        boolean over = my >= Math.max(y, top) && my < Math.min(y + FLUID_ROW - 2, bottom) && mx >= x && mx < x + w;
        rrect(g, x, y, x + w, y + FLUID_ROW - 2, over ? ACCENT : EDGE);
        rrect(g, x + 1, y + 1, x + w - 1, y + FLUID_ROW - 3, over ? HOVER_BG : CARD);
        MachineThumbs.draw(g, com.sqc.nuclearsq.progression.ReactorFluids.controllerItem(e.machine()), x + 1, y + 1, FLUID_THUMB, 1f);
        String mk = "compendium.fluids.m." + e.machine();
        String port = tr(com.sqc.nuclearsq.progression.ReactorFluids.portKey(e.machine(), e.port()));
        if (e.ports().size() > 1) port += " +" + (e.ports().size() - 1);
        int tw = w - FLUID_THUMB - 6;
        g.drawString(font, font.plainSubstrByWidth(tr(mk), tw), x + FLUID_THUMB + 4, y + 2, over ? 0xFFFFFFFF : TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(port, tw), x + FLUID_THUMB + 4, y + 11, MUTED, false);
        if (!over) return;
        List<FormattedCharSequence> tip = new ArrayList<>();
        tip.add(Component.literal(tr(mk)).withStyle(ChatFormatting.BOLD).getVisualOrderText());
        for (String pn : e.ports())
            tip.addAll(font.split(Component.literal(tr(com.sqc.nuclearsq.progression.ReactorFluids.portKey(e.machine(), pn))).withStyle(ChatFormatting.GRAY), 220));
        if (!com.sqc.nuclearsq.progression.ReactorFluids.TURBINE.equals(e.machine())) {
            try {
                var bm = com.sqc.nuclearsq.machine.big.BigMachine.valueOf(e.machine().toUpperCase(java.util.Locale.ROOT));
                var bp = bm.port(e.port());
                if (bp != null) {
                    String acc = acceptedFluids(bm, bp);
                    if (!acc.isEmpty()) tip.addAll(font.split(Component.literal(tr("compendium.fluids.accepts").formatted(acc)).withStyle(ChatFormatting.GRAY), 220));
                }
            } catch (IllegalArgumentException ex) {
            }
        }
        int rate = fluidRate(e);
        if (rate > 0) tip.add(Component.literal(tr("compendium.fluids.rate").formatted(String.valueOf(rate))).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        int[] target = findPage(com.sqc.nuclearsq.progression.ReactorFluids.PAGES.getOrDefault(e.machine(), ""));
        if (target != null) {
            tip.add(Component.literal(tr("compendium.loop.open")).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            hits.add(0, new Hit(x, Math.max(y, top), x + w, Math.min(y + FLUID_ROW - 2, bottom), 0, target[0], target[1]));
        }
        loopTip = tip;
    }

    private static int fluidRate(com.sqc.nuclearsq.progression.ReactorFluids.End e) {
        if (com.sqc.nuclearsq.progression.ReactorFluids.TURBINE.equals(e.machine())) return 0;
        try {
            var port = com.sqc.nuclearsq.machine.big.BigMachine.valueOf(e.machine().toUpperCase(java.util.Locale.ROOT)).port(e.port());
            return port == null ? 0 : port.rate();
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    private static final int PLANT_CW = 104, PLANT_RH = 78, PLANT_PAD = 40;
    private static final int LINE_CW = 128;
    private int plantCw = PLANT_CW, plantPad = PLANT_PAD;
    private static final float PLANT_STEP = 0.22f, PLANT_ZOOM_MIN = 0.35f, PLANT_ZOOM_MAX = 2.5f;
    private static final int PLANT_RING = 0xFF3A8FB0;
    private static final int[] PLANT_COLORS = {0xFF8A93A3, 0xFF9CCB3A, 0xFFE07A2E, 0xFFD24A4A, 0xFF3A8FB0, 0xFF46C46A, 0xFFA070D8, 0xFFE0B040, 0xFF4FC3F7, 0xFFE05A5A};

    private List<com.sqc.nuclearsq.progression.PlantOverview.Node> netNodes() {
        return switch (current().anim()) {
            case ASSEMBLY -> com.sqc.nuclearsq.progression.AssemblyTree.NODES;
            case WEAPONS -> com.sqc.nuclearsq.progression.SecretWeaponsTree.NODES;
            case LINE -> com.sqc.nuclearsq.progression.LineTrees.nodes(com.sqc.nuclearsq.progression.LineTrees.byPage(current().id()));
            default -> com.sqc.nuclearsq.progression.PlantOverview.NODES;
        };
    }

    private List<com.sqc.nuclearsq.progression.PlantOverview.Edge> netEdges() {
        return switch (current().anim()) {
            case ASSEMBLY -> com.sqc.nuclearsq.progression.AssemblyTree.EDGES;
            case WEAPONS -> com.sqc.nuclearsq.progression.SecretWeaponsTree.EDGES;
            case LINE -> com.sqc.nuclearsq.progression.LineTrees.edges(com.sqc.nuclearsq.progression.LineTrees.byPage(current().id()));
            default -> com.sqc.nuclearsq.progression.PlantOverview.EDGES;
        };
    }

    private List<String> plantNameLines(String name) {
        if (font.width(name) <= plantCw - 8) return List.of(name);
        int best = -1;
        for (int i = 1; i < name.length() - 1; i++) {
            char c = name.charAt(i);
            if (c != ' ' && c != '-') continue;
            if (best < 0 || Math.abs(i - name.length() / 2) < Math.abs(best - name.length() / 2)) best = i;
        }
        if (best < 0) return List.of(name);
        return List.of(name.substring(0, name.charAt(best) == '-' ? best + 1 : best), name.substring(best + 1));
    }

    private int plantX(com.sqc.nuclearsq.progression.PlantOverview.Node n) { return plantPad + n.col() * plantCw; }
    private static int plantY(com.sqc.nuclearsq.progression.PlantOverview.Node n) { return PLANT_PAD / 2 + n.row() * PLANT_RH; }

    private int[] lineRoute(com.sqc.nuclearsq.progression.PlantOverview.Node a, com.sqc.nuclearsq.progression.PlantOverview.Node b) {
        int ax = plantX(a), ay = plantY(a), bx = plantX(b), by = plantY(b), gx = bx - LINE_CW / 2 + 3;
        if (ay == by) return new int[] {ax, ay, bx, by};
        if (bx > ax) return new int[] {ax, ay, gx, ay, gx, by, bx, by};
        int ex = ax + LINE_CW / 2 - 3, cy = by - PLANT_CARD / 2 - (by - ay == PLANT_RH ? 10 : 44);
        return new int[] {ax, ay, ex, ay, ex, cy, gx, cy, gx, by, bx, by};
    }

    private static boolean showTips() { return com.sqc.nuclearsq.NuclearClientConfig.compendiumTips(); }

    private static boolean showItems() { return com.sqc.nuclearsq.NuclearClientConfig.compendiumItems(); }

    private int plantButton(GuiGraphics g, int mx, int my, int bx, int y, String label, int type) {
        int bw = font.width(label) + 10;
        boolean over = mx >= bx - bw && mx < bx && my >= y && my < y + 13;
        rrect(g, bx - bw, y, bx, y + 13, over ? ACTIVE_BG : CARD);
        g.drawString(font, label, bx - bw + 5, y + 3, over ? 0xFFFFFFFF : TEXT, false);
        hits.add(new Hit(bx - bw, y, bx, y + 13, type, 0, 0));
        return bx - bw - 4;
    }

    private void drawPlant(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        var nodes = netNodes();
        var edges = netEdges();
        int n = nodes.size();
        int bx = plantButton(g, mx, my, x + w, y, tr("compendium.plant.reset"), 4);
        bx = plantButton(g, mx, my, bx, y, tr("compendium.plant.replay"), 3);
        var line = p.anim() == Anim.LINE ? com.sqc.nuclearsq.progression.LineTrees.byPage(p.id()) : p.anim() == Anim.ASSEMBLY ? com.sqc.nuclearsq.progression.AssemblyTree.LINE : null;
        int goal = line != null ? nextGoal() : -1;
        if (line != null) {
            int done = 0;
            for (var nd : nodes) if (builtNode(nd)) done++;
            String prog = tr("compendium.lines.done").formatted(done, n);
            bx -= 64;
            g.fill(bx, y + 4, bx + 60, y + 9, PANEL_BG);
            g.fill(bx, y + 4, bx + 60 * done / Math.max(1, n), y + 9, ACCENT);
            bx -= font.width(prog) + 6;
            g.drawString(font, prog, bx, y + 3, MUTED, false);
        }
        if (has("compendium." + p.id() + ".text")) {
            String hint = font.plainSubstrByWidth(tr("compendium." + p.id() + ".text").split("\n")[0], bx - x - 4);
            g.drawString(font, hint, x, y + 3, MUTED, false);
        }
        y += 17;
        h -= 17;
        plantAX = x;
        plantAY = y;
        plantAW = w;
        plantAH = h;
        g.fillGradient(x, y, x + w, y + h, 0xFF161618, 0xFF1C1C20);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);

        int maxCol = 0, maxRow = 0;
        for (var nd : nodes) {
            maxCol = Math.max(maxCol, nd.col());
            maxRow = Math.max(maxRow, nd.row());
        }
        plantCw = line != null ? LINE_CW : PLANT_CW;
        plantPad = line != null ? LINE_CW / 2 : PLANT_PAD;
        int top = line != null ? 36 : 0, prodY = PLANT_PAD + maxRow * PLANT_RH + 50;
        int worldW = 2 * plantPad + maxCol * plantCw, worldH = PLANT_PAD + maxRow * PLANT_RH + 44 + top + (line != null && !line.products().isEmpty() ? 40 : 0);
        plantFit = line != null ? Math.min(1f, (w - 8) / (float) worldW) : Math.min(1f, Math.min((w - 8) / (float) worldW, (h - 8) / (float) worldH));
        if (plantZoom <= 0) {
            plantZoom = plantFit;
            plantPanX = (w - worldW * plantZoom) / 2;
            plantPanY = Math.max(4, (h - worldH * plantZoom) / 2) + top * plantZoom;
        }
        if (plantStart == 0) plantStart = System.currentTimeMillis();
        float el = (System.currentTimeMillis() - plantStart) / 1000f;
        float[] shown = new float[n];
        for (int k = 0; k < n; k++) shown[k] = ease((el - k * PLANT_STEP - 0.1f) / 0.25f);

        float z = plantZoom;
        float wmx = (mx - x - plantPanX) / z, wmy = (my - y - plantPanY) / z;
        if (inPlant(mx, my)) {
            for (int k = 0; k < n; k++) {
                var nd = nodes.get(k);
                if (shown[k] > 0.6f && Math.abs(wmx - plantX(nd)) <= PLANT_CARD / 2f && Math.abs(wmy - plantY(nd)) <= PLANT_CARD / 2f) plantHover = k;
            }
        }
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        g.pose().pushPose();
        g.pose().translate(x + plantPanX, y + plantPanY, 0);
        g.pose().scale(z, z, 1f);

        int lastRing = -1;
        for (var e : edges) if (e.ring()) lastRing = Math.max(lastRing, Math.max(e.from(), e.to()));
        for (var e : edges) {
            int a = Math.min(e.from(), e.to()), b = Math.max(e.from(), e.to());
            float grow = ease((el - b * PLANT_STEP) / 0.3f);
            var na = nodes.get(a);
            var nb = nodes.get(b);
            boolean lit = plantHover == e.from() || plantHover == e.to();
            int col = lit ? ACCENT : e.ring() ? PLANT_RING : alpha(150, PLANT_COLORS[nodes.get(e.from()).group().ordinal()]);
            if (line == null) segment(g, plantX(na), plantY(na), plantX(nb), plantY(nb), grow, col);
            else {
                int[] q = lineRoute(nodes.get(e.from()), nodes.get(e.to()));
                thickPath(g, q, grow, 1, col | 0xFF000000);
                if (grow >= 1f) flowArrow(g, q, col | 0xFF000000, true);
            }
        }
        if (line != null) {
            var mids = lineMids(line, nodes, edges);
            float tDone = n * PLANT_STEP + 0.4f;
            for (int i = 0; i < edges.size(); i++) {
                var e = edges.get(i);
                int[] q = lineRoute(nodes.get(e.from()), nodes.get(e.to()));
                if (el > tDone) {
                    int[] pt = pathPoint(q, pathLength(q) * (((el - tDone) * 0.4f) % 1f));
                    circle(g, pt[0], pt[1], 2, ACCENT);
                }
                ItemStack st = mids.get(i);
                if (st.isEmpty() || ease((el - Math.max(e.from(), e.to()) * PLANT_STEP) / 0.3f) < 0.99f) continue;
                int[] mid = pathPoint(q, pathLength(q) / 2);
                int ix = mid[0] - 8, iy = mid[1] - 8;
                rrect(g, ix - 2, iy - 2, ix + 18, iy + 18, EDGE);
                rrect(g, ix - 1, iy - 1, ix + 17, iy + 17, PANEL_BG);
                MachineThumbs.drawStack(g, st, ix, iy);
                if (inPlant(mx, my) && wmx >= ix && wmx < ix + 16 && wmy >= iy && wmy < iy + 16) hover = st;
            }
        }
        float tClose = lastRing * PLANT_STEP + 0.4f;
        if (lastRing >= 0 && el > tClose) {
            float u = ((el - tClose) * 0.6f) % 1f;
            for (var e : edges) {
                if (!e.ring()) continue;
                var na = nodes.get(e.from());
                var nb = nodes.get(e.to());
                circle(g, plantX(na) + Math.round((plantX(nb) - plantX(na)) * u), plantY(na) + Math.round((plantY(nb) - plantY(na)) * u), 2, ACCENT);
            }
        }

        for (int k = 0; k < n; k++) {
            if (shown[k] <= 0.02f) continue;
            var nd = nodes.get(k);
            int a = (int) (255 * shown[k]);
            int cx = plantX(nd), cy = plantY(nd), x0 = cx - PLANT_CARD / 2, y0 = cy - PLANT_CARD / 2;
            boolean over = k == plantHover;
            int border = line != null ? (over ? 0xFFFFFFFF : builtNode(nd) ? GREEN : com.sqc.nuclearsq.progression.AchievementTree.index(nd.key()) == goal ? ACCENT : NEVER)
                    : over ? ACCENT : PLANT_COLORS[nd.group().ordinal()];
            rrect(g, x0 - 1, y0 - 1, x0 + PLANT_CARD + 1, y0 + PLANT_CARD + 1, alpha(a, border));
            rrect(g, x0, y0, x0 + PLANT_CARD, y0 + PLANT_CARD, alpha(a, over ? ACTIVE_BG : CARD));
            if (shown[k] > 0.45f) MachineThumbs.draw(g, nd.item(), x0 + 1, y0 + 1, PLANT_CARD - 2, z);
            if (a <= 8) continue;
            String num = String.valueOf(k + 1);
            g.pose().pushPose();
            g.pose().translate(x0 - 3, y0 - 4, 200);
            g.fill(-2, -1, font.width(num) + 1, 9, alpha(a, border));
            g.drawString(font, num, 0, 0, alpha(a, 0x14171D), false);
            g.pose().popPose();
            String base = "compendium.plant." + nd.key();
            int bg = alpha(a * 9 / 10, BG);
            List<String> nameLines = z < 0.55f ? List.of() : plantNameLines(tr(base + ".name"));
            int ny = y0 + PLANT_CARD + 2;
            for (String ln : nameLines) {
                int hw = font.width(ln) / 2 + 2;
                g.fill(cx - hw, ny, cx + hw, ny + 10, bg);
                g.drawString(font, ln, cx - font.width(ln) / 2, ny + 1, alpha(a, over ? 0xFFFFFF : TEXT), false);
                ny += 10;
            }
        }
        if (line != null) {
            int[][] bp = com.sqc.nuclearsq.progression.LineTrees.blockPos(line);
            for (int i = 0, first = 0; i < bp.length; first += line.blocks().get(i).keys().size(), i++) {
                var blk = line.blocks().get(i);
                int fx0 = bp[i][0] * LINE_CW + 6, fx1 = (bp[i][0] + bp[i][2]) * LINE_CW - 6;
                int fy0 = PLANT_PAD / 2 + bp[i][1] * PLANT_RH - PLANT_CARD / 2 - 30, fy1 = PLANT_PAD / 2 + (bp[i][1] + bp[i][3] - 1) * PLANT_RH + PLANT_CARD / 2 + 26;
                int fa = (int) (255 * shown[first]);
                String lab = blk.made() != null ? tr("compendium.made_in") + " " + tr("compendium.made_in." + blk.made())
                        : tr("compendium.lines.stage").formatted(blk.stage()) + "  " + tr("compendium.ach.stage." + com.sqc.nuclearsq.progression.AchievementTree.STAGES.get(blk.stage() - 1).key());
                if (blk.made() != null) fx1 = Math.max(fx1, fx0 + font.width(lab) + 10);
                hullFrame(g, new int[] {fx0, fy0, fx1, fy1}, fa);
                if (fa > 8) g.drawString(font, font.plainSubstrByWidth(lab, fx1 - fx0 - 10), fx0 + 5, fy0 + 4, alpha(fa, MUTED), false);
            }
            if (!line.products().isEmpty()) {
                g.drawString(font, tr("compendium.lines.products"), PLANT_PAD - PLANT_CARD / 2, prodY, 0xFFFFFFFF, false);
                int ix = PLANT_PAD - PLANT_CARD / 2;
                for (String id : line.products()) {
                    ItemStack st = modItem(id);
                    g.fill(ix - 1, prodY + 11, ix + 17, prodY + 29, CARD);
                    MachineThumbs.drawStack(g, st, ix, prodY + 12);
                    if (inPlant(mx, my) && wmx >= ix && wmx < ix + 16 && wmy >= prodY + 12 && wmy < prodY + 28) hover = st;
                    ix += 22;
                }
            }
        }
        g.pose().popPose();

        if (line != null) {
            drawLineLegend(g, mx, my, x, y, w, h, line);
            g.disableScissor();
            plantTip(nodes, edges);
            return;
        }
        List<com.sqc.nuclearsq.progression.PlantOverview.Group> groups = new ArrayList<>();
        for (var gr : com.sqc.nuclearsq.progression.PlantOverview.Group.values())
            if (nodes.stream().anyMatch(nd -> nd.group() == gr)) groups.add(gr);
        int lw = 0;
        for (var gr : groups) lw = Math.max(lw, font.width(tr("compendium.plant.group." + gr.name().toLowerCase(java.util.Locale.ROOT))));
        int lx = x + 6, ly = y + h - 5 - groups.size() * 10;
        g.fill(lx - 3, ly - 3, lx + 13 + lw, y + h - 3, 0xD00B0D12);
        for (var gr : groups) {
            g.fill(lx, ly + 1, lx + 6, ly + 7, PLANT_COLORS[gr.ordinal()]);
            g.drawString(font, tr("compendium.plant.group." + gr.name().toLowerCase(java.util.Locale.ROOT)), lx + 10, ly, TEXT, false);
            ly += 10;
        }
        if (lastRing >= 0 && el > tClose) {
            int ca = (int) (255 * ease((el - tClose) / 0.6f));
            String closed = tr("compendium.plant.closed");
            if (ca > 8) g.drawString(font, closed, x + w - font.width(closed) - 6, y + h - 12, alpha(ca, ACCENT), false);
        }
        g.disableScissor();
        plantTip(nodes, edges);
    }

    private com.sqc.nuclearsq.progression.ModScope scope;
    private List<ItemStack> scopeStacks = List.of();
    private boolean scopeLinks = false, scopeNames = true, scopeFollow = true;
    private long scopeFrame = 0;
    private boolean scopeReplay;
    private int scopePin = -1;

    private int scopeToggle(GuiGraphics g, int mx, int my, int bx, int y, String label, int type, boolean on) {
        int bw = font.width(label) + 10;
        boolean over = mx >= bx - bw && mx < bx && my >= y && my < y + 13;
        rrect(g, bx - bw, y, bx, y + 13, on ? ACCENT : over ? ACTIVE_BG : CARD);
        g.drawString(font, label, bx - bw + 5, y + 3, on ? 0xFF19191B : over ? 0xFFFFFFFF : TEXT, false);
        hits.add(new Hit(bx - bw, y, bx, y + 13, type, 0, 0));
        return bx - bw - 4;
    }

    private static void scopeLine(GuiGraphics g, float ax, float ay, float bx, float by, float hw, float cut, int argb) {
        float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float f = cut / Math.max(Math.abs(dx), Math.abs(dy));
        if (f >= 0.5f) return;
        ax += dx * f;
        ay += dy * f;
        bx -= dx * f;
        by -= dy * f;
        float nx = -dy / len * hw, ny = dx / len * hw;
        var m = g.pose().last().pose();
        var vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        vc.addVertex(m, ax - nx, ay - ny, 0).setColor(argb);
        vc.addVertex(m, ax + nx, ay + ny, 0).setColor(argb);
        vc.addVertex(m, bx + nx, by + ny, 0).setColor(argb);
        vc.addVertex(m, bx - nx, by - ny, 0).setColor(argb);
    }

    private void drawScope(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        var level = Minecraft.getInstance().level;
        if (scope == null && level != null) {
            scope = com.sqc.nuclearsq.progression.ModScope.build(RecipeSources.all(level.getRecipeManager(), level.registryAccess()));
            scopeStacks = scope.nodes.stream().map(nd -> new ItemStack(nd.item())).toList();
        }
        if (scope == null || scope.nodes.isEmpty()) return;
        var sc = scope;
        int n = sc.nodes.size(), S = com.sqc.nuclearsq.progression.ModScope.SLOT;
        if (plantStart == 0) plantStart = System.currentTimeMillis();
        boolean played = com.sqc.nuclearsq.NuclearClientConfig.compendiumScopePlayed();
        float el = played && !scopeReplay ? sc.duration + 10 : (System.currentTimeMillis() - plantStart) / 1000f;
        if (!played && el >= sc.duration) com.sqc.nuclearsq.NuclearClientConfig.setCompendiumScopePlayed();
        int shown = 0;
        while (shown < n && sc.time[sc.order[shown]] <= el) shown++;
        int lit = plantHover >= 0 ? plantHover : scopePin < n && scopePin >= 0 && sc.time[scopePin] <= el ? scopePin : -1;
        int bx = plantButton(g, mx, my, x + w, y, tr("compendium.plant.reset"), 4);
        bx = plantButton(g, mx, my, bx, y, tr("compendium.plant.replay"), 3);
        bx = scopeToggle(g, mx, my, bx, y, tr("compendium.modscope.names"), 18, scopeNames);
        bx = scopeToggle(g, mx, my, bx, y, tr("compendium.modscope.links"), 17, scopeLinks);
        g.drawString(font, font.plainSubstrByWidth(tr("compendium.modscope.count").formatted(shown, n), bx - x - 4), x, y + 3, MUTED, false);
        y += 17;
        h -= 17;
        plantAX = x;
        plantAY = y;
        plantAW = w;
        plantAH = h;
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);

        plantFit = Math.min((w - 8) / (float) sc.width, (h - 8) / (float) sc.height);
        boolean snap = plantZoom <= 0;
        if (snap) scopeFollow = true;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - scopeFrame) / 1000f);
        scopeFrame = now;
        if (scopeFollow) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (int k = 0; k < n; k++) {
                var nd = sc.nodes.get(sc.order[k]);
                if (k > 0 && sc.time[sc.order[k]] > el + 2f) break;
                x0 = Math.min(x0, nd.x()); y0 = Math.min(y0, nd.y()); x1 = Math.max(x1, nd.x() + S); y1 = Math.max(y1, nd.y() + S);
            }
            float tz = Math.max(plantFit, Math.min(2f, Math.min((w - 8) / (x1 - x0 + 90f), (h - 8) / (y1 - y0 + 90f))));
            float tpx = w / 2f - (x0 + x1) / 2f * tz, tpy = h / 2f - (y0 + y1) / 2f * tz;
            float k = snap ? 1 : 1 - (float) Math.exp(-dt * 2.4f);
            if (snap) plantZoom = tz;
            float cx = (w / 2f - plantPanX) / plantZoom, cy = (h / 2f - plantPanY) / plantZoom;
            float ncx = cx + ((x0 + x1) / 2f - cx) * k, ncy = cy + ((y0 + y1) / 2f - cy) * k;
            plantZoom = snap ? tz : plantZoom * (float) Math.pow(tz / plantZoom, k);
            plantPanX = snap ? tpx : w / 2f - ncx * plantZoom;
            plantPanY = snap ? tpy : h / 2f - ncy * plantZoom;
        }
        float z = plantZoom;
        float wmx = (mx - x - plantPanX) / z, wmy = (my - y - plantPanY) / z;
        float vx0 = -plantPanX / z, vy0 = -plantPanY / z, vx1 = (w - plantPanX) / z, vy1 = (h - plantPanY) / z;
        if (inPlant(mx, my)) {
            for (int k = 0; k < shown; k++) {
                var nd = sc.nodes.get(sc.order[k]);
                if (wmx >= nd.x() && wmx < nd.x() + S && wmy >= nd.y() && wmy < nd.y() + S) plantHover = sc.order[k];
            }
        }
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        g.pose().pushPose();
        g.pose().translate(x + plantPanX, y + plantPanY, 0);
        g.pose().scale(z, z, 1f);
        boolean[] chapOn = new boolean[sc.chapters.size()], secOn = new boolean[sc.sections.size()];
        for (int k = 0; k < shown; k++) {
            int b = sc.nodes.get(sc.order[k]).box();
            chapOn[b] = true;
            secOn[sc.chapters.get(b).parent()] = true;
        }
        for (int i = 0; i < secOn.length; i++) {
            var b = sc.sections.get(i);
            if (!secOn[i]) continue;
            g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y() + b.h() + 1, EDGE);
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), PANEL_BG);
        }
        for (int i = 0; i < chapOn.length; i++) {
            var b = sc.chapters.get(i);
            if (chapOn[i]) g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), CARD);
        }
        boolean icons = z * 16 * Minecraft.getInstance().getWindow().getGuiScale() >= 8;
        for (int k = 0; k < shown; k++) {
            int i = sc.order[k];
            var nd = sc.nodes.get(i);
            if (nd.x() + S < vx0 || nd.x() > vx1 || nd.y() + S < vy0 || nd.y() > vy1) continue;
            float age = el - sc.time[i];
            g.fill(nd.x(), nd.y(), nd.x() + S, nd.y() + S, i == plantHover || i == scopePin ? 0xFFFFFFFF : nd.machine() ? ACCENT : EDGE);
            g.fill(nd.x() + 1, nd.y() + 1, nd.x() + S - 1, nd.y() + S - 1, SIDE_BG);
            if (icons) MachineThumbs.drawStack(g, scopeStacks.get(i), nd.x() + 1, nd.y() + 1);
            if (age < 0.8f) {
                int r = (int) (age * 14), c = alpha((int) (255 * (1 - age / 0.8f)), ACCENT);
                g.fill(nd.x() - r, nd.y() - r, nd.x() + S + r, nd.y() - r + 1, c);
                g.fill(nd.x() - r, nd.y() + S + r - 1, nd.x() + S + r, nd.y() + S + r, c);
                g.fill(nd.x() - r, nd.y() - r, nd.x() - r + 1, nd.y() + S + r, c);
                g.fill(nd.x() + S + r - 1, nd.y() - r, nd.x() + S + r, nd.y() + S + r, c);
            }
        }
        float hw = 0.5f / z, mid = S / 2f;
        if (scopeLinks) {
            for (var e : sc.edges) {
                if (sc.time[e.from()] > el || sc.time[e.to()] > el) continue;
                var a = sc.nodes.get(e.from());
                var b = sc.nodes.get(e.to());
                if (Math.max(a.x(), b.x()) + S < vx0 || Math.min(a.x(), b.x()) > vx1 || Math.max(a.y(), b.y()) + S < vy0 || Math.min(a.y(), b.y()) > vy1) continue;
                int al = (e.machine() ? 22 : 44) / (lit >= 0 ? 3 : 1);
                scopeLine(g, a.x() + mid, a.y() + mid, b.x() + mid, b.y() + mid, hw, mid, alpha(al, e.machine() ? ACCENT : CYAN));
            }
        }
        if (lit >= 0) {
            for (int ei : sc.adj.get(lit)) {
                var e = sc.edges.get(ei);
                if (sc.time[e.from()] > el || sc.time[e.to()] > el) continue;
                var a = sc.nodes.get(e.from());
                var b = sc.nodes.get(e.to());
                scopeLine(g, a.x() + mid, a.y() + mid, b.x() + mid, b.y() + mid, hw * 1.6f, mid, alpha(230, e.machine() ? ACCENT : CYAN));
            }
        }
        g.flush();
        g.pose().popPose();
        if (scopeNames) {
            for (int i = 0; i < secOn.length; i++) {
                var b = sc.sections.get(i);
                int sx = x + Math.round(plantPanX + b.x() * z), sy = y + Math.round(plantPanY + b.y() * z), sw = Math.round(b.w() * z);
                if (!secOn[i] || sw < 24 || com.sqc.nuclearsq.progression.ModScope.SEC_HEAD * z < 9) continue;
                int ty = sy + Math.round((com.sqc.nuclearsq.progression.ModScope.SEC_HEAD * z - 8) / 2);
                g.fill(sx + 4, ty - 1, sx + 6, ty + 9, ACCENT);
                g.drawString(font, font.plainSubstrByWidth(tr(b.key()).toUpperCase(java.util.Locale.ROOT), sw - 12), sx + 9, ty, ACCENT, false);
            }
            for (int i = 0; i < chapOn.length; i++) {
                var b = sc.chapters.get(i);
                int sx = x + Math.round(plantPanX + b.x() * z), sy = y + Math.round(plantPanY + b.y() * z), sw = Math.round(b.w() * z);
                if (!chapOn[i] || sw < 24 || com.sqc.nuclearsq.progression.ModScope.HEAD * z < 10 || b.key().equals(sc.sections.get(b.parent()).key())) continue;
                if (tr(b.key()).equals(tr(sc.sections.get(b.parent()).key()))) continue;
                g.drawString(font, font.plainSubstrByWidth(tr(b.key()), sw - 8), sx + Math.round(com.sqc.nuclearsq.progression.ModScope.PAD * z), sy + Math.round((com.sqc.nuclearsq.progression.ModScope.HEAD * z - 8) / 2), TEXT, false);
            }
            if (shown > 0 && shown < n) {
                String name = "+ " + scopeStacks.get(sc.order[shown - 1]).getHoverName().getString();
                int nw = font.width(name);
                g.fill(x + w / 2 - nw / 2 - 4, y + h - 16, x + w / 2 + nw / 2 + 4, y + h - 4, 0xD0161618);
                g.drawString(font, name, x + w / 2 - nw / 2, y + h - 14, 0xFFFFFFFF, false);
            }
        }
        g.disableScissor();
        if (plantHover >= 0) {
            var nd = sc.nodes.get(plantHover);
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(scopeStacks.get(plantHover).getHoverName().getVisualOrderText());
            tip.add(Component.literal(tr(sc.chapters.get(nd.box()).key())).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            loopTip = tip;
        }
    }

    private final java.util.Map<String, List<ItemStack>> lineMidCache = new java.util.HashMap<>();

    private List<ItemStack> lineMids(com.sqc.nuclearsq.progression.LineTrees.Line line, List<com.sqc.nuclearsq.progression.PlantOverview.Node> nodes,
            List<com.sqc.nuclearsq.progression.PlantOverview.Edge> edges) {
        var level = Minecraft.getInstance().level;
        if (level == null) return java.util.Collections.nCopies(edges.size(), ItemStack.EMPTY);
        return lineMidCache.computeIfAbsent(line.key(), k -> {
            var all = RecipeSources.all(level.getRecipeManager(), level.registryAccess());
            List<ItemStack> out = new ArrayList<>();
            for (var e : edges) {
                String a = nodes.get(e.from()).item(), b = nodes.get(e.to()).item();
                ItemStack found = ItemStack.EMPTY;
                search:
                for (var s : all) {
                    if (!s.where().icon.getPath().equals(a) || s.result().isEmpty()) continue;
                    for (var t : all)
                        if (t.where().icon.getPath().equals(b))
                            for (var n : t.needs())
                                if (n.ingredient().test(s.result())) {
                                    found = s.result().copyWithCount(1);
                                    break search;
                                }
                }
                out.add(found);
            }
            return out;
        });
    }

    private void drawLineLegend(GuiGraphics g, int mx, int my, int x, int y, int w, int h, com.sqc.nuclearsq.progression.LineTrees.Line line) {
        int ly = y + h - 13, lx = x + 6;
        g.fill(x + 1, ly - 3, x + w - 1, y + h - 1, 0xD00B0D12);
        int[] cols = {GREEN, ACCENT, NEVER};
        String[] keys = {"built", "goal", "never"};
        for (int i = 0; i < 3; i++) {
            rrect(g, lx, ly, lx + 8, ly + 8, cols[i]);
            g.fill(lx + 2, ly + 2, lx + 6, ly + 6, CARD);
            String s = tr("compendium.lines." + keys[i]);
            g.drawString(font, s, lx + 11, ly, TEXT, false);
            lx += font.width(s) + 22;
        }
        var all = com.sqc.nuclearsq.progression.LineTrees.LINES;
        int i = all.indexOf(line);
        if (i < 0 || i + 1 >= all.size()) return;
        var next = all.get(i + 1);
        String s = tr("compendium.lines.next").formatted(tr("compendium." + next.page() + ".title"));
        int sw = font.width(s), sx = x + w - sw - 6;
        if (sx < lx) return;
        boolean hv = mx >= sx && mx < sx + sw && my >= ly - 2 && my < ly + 10;
        g.drawString(font, s, sx, ly, hv ? 0xFFFFFFFF : CYAN, false);
        int[] t = findPage(next.page());
        if (t != null) hits.add(new Hit(sx, ly - 2, sx + sw, ly + 10, 0, t[0], t[1]));
    }

    private void plantTip(List<com.sqc.nuclearsq.progression.PlantOverview.Node> nodes, List<com.sqc.nuclearsq.progression.PlantOverview.Edge> edges) {
        if (plantHover >= 0 && !plantDrag) {
            var nd = nodes.get(plantHover);
            String base = "compendium.plant." + nd.key();
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(Component.literal((plantHover + 1) + ". " + tr(base + ".name")).withStyle(ChatFormatting.BOLD).getVisualOrderText());
            tip.addAll(font.split(Component.literal(tr(base + ".short")), 220));
            StringBuilder from = new StringBuilder(), to = new StringBuilder();
            for (var e : edges) {
                if (e.to() == plantHover) from.append(from.isEmpty() ? "" : ", ").append(tr("compendium.plant." + nodes.get(e.from()).key() + ".name"));
                if (e.from() == plantHover) to.append(to.isEmpty() ? "" : ", ").append(tr("compendium.plant." + nodes.get(e.to()).key() + ".name"));
            }
            if (!from.isEmpty()) tip.addAll(font.split(Component.literal(tr("compendium.plant.from") + from).withStyle(ChatFormatting.AQUA), 220));
            if (!to.isEmpty()) tip.addAll(font.split(Component.literal(tr("compendium.plant.to") + to).withStyle(ChatFormatting.GOLD), 220));
            if (findPage(nd.page()) != null) tip.add(Component.literal(tr("compendium.plant.open")).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            loopTip = tip;
        }
    }


    private record Sub(@javax.annotation.Nullable String title, List<ItemStack> items) {}

    private record Group(RecipeSources.Where where, List<Sub> subs) {
        List<ItemStack> items() { return subs.stream().flatMap(sb -> sb.items().stream()).toList(); }
    }

    private List<Group> catalogGroups;
    private int catalogTotal = 0;
    private static final int CATALOG_SPLIT = 18;

    private static final List<RecipeSources.Where> STAGES = RecipeSources.CATALOG_STAGES;

    private final java.util.Map<net.minecraft.world.item.Item, int[]> tabOrder = new java.util.HashMap<>();
    private final List<String> tabSections = new ArrayList<>();

    private void buildTabOrder() {
        tabOrder.clear();
        tabSections.clear();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var tab = com.sqc.nuclearsq.registry.ModCreativeTabs.TAB.get();
        if (tab.getDisplayItems().isEmpty()) {
            try {
                tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
            } catch (RuntimeException ex) {
                return;
            }
        }
        int sec = -1, pos = 0;
        for (ItemStack st : tab.getDisplayItems()) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
            if (id.getPath().startsWith("header_")) {
                tabSections.add(st.getItem().getDescriptionId());
                sec = tabSections.size() - 1;
                continue;
            }
            if (st.is(com.sqc.nuclearsq.registry.ModCreativeTabs.SPACER.get())) continue;
            tabOrder.putIfAbsent(st.getItem(), new int[] {Math.max(0, sec), pos++});
        }
    }

    private void buildCatalog() {
        buildTabOrder();
        java.util.Map<RecipeSources.Where, List<ItemStack>> map = new java.util.LinkedHashMap<>();
        for (var st : STAGES) map.put(st, new ArrayList<>());
        var level = Minecraft.getInstance().level;
        if (level != null) for (var it : RecipeSources.craftable(level.getRecipeManager(), level.registryAccess())) {
            RecipeSources.Where first = RecipeSources.Where.OTHER;
            for (var src : sources(it)) if (STAGES.indexOf(src.where()) < STAGES.indexOf(first)) first = src.where();
            map.computeIfAbsent(first, w -> new ArrayList<>()).add(new ItemStack(it));
        }
        final int[] none = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        catalogGroups = new ArrayList<>();
        for (var e : map.entrySet()) {
            List<ItemStack> items = new ArrayList<>(e.getValue());
            if (items.isEmpty()) continue;
            items.sort(java.util.Comparator.comparingInt((ItemStack st) -> tabOrder.getOrDefault(st.getItem(), none)[0])
                    .thenComparingInt(st -> tabOrder.getOrDefault(st.getItem(), none)[1]));
            List<Sub> subs = new ArrayList<>();
            if (items.size() < CATALOG_SPLIT || tabSections.isEmpty()) {
                subs.add(new Sub(null, items));
            } else {
                int cur = Integer.MIN_VALUE;
                for (ItemStack st : items) {
                    int sec = tabOrder.getOrDefault(st.getItem(), none)[0];
                    if (sec != cur) {
                        subs.add(new Sub(sec == Integer.MAX_VALUE ? "compendium.catalog.misc" : tabSections.get(sec), new ArrayList<>()));
                        cur = sec;
                    }
                    subs.get(subs.size() - 1).items().add(st);
                }
            }
            catalogGroups.add(new Group(e.getKey(), subs));
        }
    }

    @javax.annotation.Nullable
    private static int[] pageFor(net.minecraft.world.item.Item item) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        for (var nd : com.sqc.nuclearsq.progression.AchievementTree.NODES) {
            if (com.sqc.nuclearsq.progression.AchievementTree.proofItems(nd).contains(path)) {
                int[] t = findPage(nd.page());
                if (t != null) return t;
            }
        }
        for (boolean fam : new boolean[] {false, true})
            for (int c = 0; c < CHAPTERS.size(); c++) {
                List<Page> ps = CHAPTERS.get(c).pages();
                for (int k = 0; k < ps.size(); k++)
                    if (com.sqc.nuclearsq.progression.CompendiumItems.lists(ps.get(k).id(), path, fam)) return new int[] {c, k};
            }
        return null;
    }

    private static void drawStage(GuiGraphics g, RecipeSources.Where w, int x, int y) {
        if (w.icon.getNamespace().equals(com.sqc.nuclearsq.NuclearSQ.MODID)) MachineThumbs.draw(g, w.icon.getPath(), x - 1, y - 1, 18, 1f);
        else if (!w.iconStack().isEmpty()) g.renderItem(w.iconStack(), x, y);
    }

    private int progressBar(GuiGraphics g, int x, int y, int w, String key, int done, int total) {
        String label = Component.translatable(key, done, total).getString();
        g.drawString(font, label, x, y + 2, ACCENT, false);
        int bx = x + font.width(label) + 8, bw = x + w - 4 - bx;
        if (bw > 20) {
            g.fill(bx, y + 3, bx + bw, y + 9, 0xFF101216);
            g.fill(bx, y + 3, bx + (int) (bw * (total == 0 ? 0 : done / (float) total)), y + 9, GREEN);
        }
        return 14;
    }

    private void drawCatalog(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        if (catalogGroups == null) buildCatalog();
        if (has("compendium." + p.id() + ".text")) {
            g.drawString(font, font.plainSubstrByWidth(tr("compendium." + p.id() + ".text").split("\n")[0], w), x, y, MUTED, false);
            y += 12;
            h -= 12;
        }
        int known = 0, total = 0;
        for (Group grp : catalogGroups) for (ItemStack st : grp.items()) {
            total++;
            if (com.sqc.nuclearsq.progression.Discovered.known(st.getItem())) known++;
        }
        int ph = progressBar(g, x, y, w, "compendium.ach.items_progress", known, total);
        y += ph;
        h -= ph;
        final int cell = 22, head = 20;
        int cols = Math.max(1, (w - 6) / cell);
        scroll = Math.min(scroll, Math.max(0, catalogTotal - h));
        g.enableScissor(x, y, x + w, y + h);
        int cy = y - scroll;
        boolean any = false;
        for (Group grp : catalogGroups) {
            List<ItemStack> shown = query.isEmpty() ? grp.items() : grp.items().stream().filter(this::matches).toList();
            if (shown.isEmpty()) continue;
            any = true;
            drawStage(g, grp.where(), x, cy + 1);
            String name = tr(grp.where().langKey());
            g.drawString(font, name, x + 20, cy + 5, 0xFFFFFFFF, false);
            int have = 0;
            for (ItemStack st : shown) if (com.sqc.nuclearsq.progression.Discovered.known(st.getItem())) have++;
            String cnt = have + " / " + shown.size();
            g.drawString(font, cnt, x + 20 + font.width(name) + 6, cy + 5, MUTED, false);
            g.fill(x + 20 + font.width(name) + 6 + font.width(cnt) + 6, cy + 9, x + w - 6, cy + 10, EDGE);
            cy += head;
            for (Sub sub : grp.subs()) {
                List<ItemStack> list = query.isEmpty() ? sub.items() : sub.items().stream().filter(this::matches).toList();
                if (list.isEmpty()) continue;
                if (sub.title() != null) {
                    g.drawString(font, tr(sub.title()), x + 4, cy + 1, MUTED, false);
                    cy += 11;
                }
                int rowsN = (list.size() + cols - 1) / cols;
                for (int k = 0; k < list.size(); k++) {
                    int cx = x + (k % cols) * cell, ry = cy + (k / cols) * cell;
                    ItemStack st = list.get(k);
                    boolean got = com.sqc.nuclearsq.progression.Discovered.known(st.getItem());
                    rrect(g, cx, ry, cx + cell - 2, ry + cell - 2, got ? GREEN : NEVER);
                    rrect(g, cx + 1, ry + 1, cx + cell - 3, ry + cell - 3, CARD);
                    MachineThumbs.drawStack(g, st, cx + 2, ry + 2);
                    if (mx >= cx && mx < cx + cell - 2 && my >= Math.max(ry, y) && my < Math.min(ry + cell - 2, y + h)) {
                        hover = st;
                        int[] target = pageFor(st.getItem());
                        if (target != null) hits.add(new Hit(cx, Math.max(ry, y), cx + cell - 2, Math.min(ry + cell - 2, y + h), 0, target[0], target[1]));
                    }
                }
                cy += rowsN * cell + 4;
            }
            cy += 2;
        }
        if (!any) g.drawString(font, tr("compendium.search.none"), x, cy + 4, MUTED, false);
        catalogTotal = cy + scroll - y;
        g.disableScissor();
        if (catalogTotal > h) {
            int thumbH = Math.max(14, h * h / catalogTotal);
            int thumbY = y + (h - thumbH) * scroll / Math.max(1, catalogTotal - h);
            bar.track(x + w - 3, y, 3, h, thumbH, catalogTotal - h);
            g.fill(x + w - 3, y, x + w, y + h, 0xFF1E222B);
            g.fill(x + w - 3, thumbY, x + w, thumbY + thumbH, 0xFF5A6478);
        }
    }

    private boolean achFollow = true;

    private void drawMachineList(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        var nodes = com.sqc.nuclearsq.progression.AchievementTree.NODES;
        var stages = com.sqc.nuclearsq.progression.AchievementTree.STAGES;
        boolean[] built = new boolean[nodes.size()];
        int done = 0, next = -1;
        for (int k = 0; k < nodes.size(); k++) {
            for (String id : com.sqc.nuclearsq.progression.AchievementTree.proofItems(nodes.get(k)))
                built[k] |= com.sqc.nuclearsq.progression.Discovered.known(id);
            if (built[k]) done++;
            else if (next < 0) next = k;
        }
        if (has("compendium." + p.id() + ".text")) {
            g.drawString(font, font.plainSubstrByWidth(tr("compendium." + p.id() + ".text").split("\n")[0], w), x, y, MUTED, false);
            y += 12;
            h -= 12;
        }
        int ph = progressBar(g, x, y, w, "compendium.ach.progress", done, nodes.size());
        y += ph;
        h -= ph;
        List<List<Integer>> groups = new ArrayList<>();
        for (int s = 0; s < stages.size(); s++) groups.add(new ArrayList<>());
        int cur = 0;
        for (int k = 0; k < nodes.size(); k++) {
            var st = com.sqc.nuclearsq.progression.AchievementTree.stageAt(k);
            if (st != null) cur = stages.indexOf(st);
            if (matches(nodes.get(k))) groups.get(cur).add(k);
        }
        final int cell = 22, head = 20;
        int cols = Math.max(1, (w - 6) / cell);
        int total = 0, nextY = -1;
        for (List<Integer> grp : groups) {
            if (grp.isEmpty()) continue;
            if (grp.contains(next)) nextY = total;
            total += head + (grp.size() + cols - 1) / cols * cell + 6;
        }
        if (achFollow) {
            if (nextY > 0) scroll = nextY;
            achFollow = false;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - h)));
        long ms = System.currentTimeMillis();
        g.enableScissor(x, y, x + w, y + h);
        int cy = y - scroll;
        boolean any = false;
        for (int s = 0; s < stages.size(); s++) {
            List<Integer> grp = groups.get(s);
            if (grp.isEmpty()) continue;
            any = true;
            MachineThumbs.draw(g, nodes.get(grp.get(0)).item(), x - 1, cy, 18, 1f);
            String name = tr("compendium.ach.stage." + stages.get(s).key());
            g.drawString(font, name, x + 20, cy + 5, 0xFFFFFFFF, false);
            int have = 0;
            for (int k : grp) if (built[k]) have++;
            String cnt = have + " / " + grp.size() + " (" + have * 100 / grp.size() + " %)" + (grp.contains(next) ? "  " + Component.translatable("compendium.ach.remaining", grp.size() - have).getString() : "");
            g.drawString(font, cnt, x + 20 + font.width(name) + 6, cy + 5, MUTED, false);
            g.fill(x + 20 + font.width(name) + 6 + font.width(cnt) + 6, cy + 9, x + w - 6, cy + 10, EDGE);
            cy += head;
            for (int i = 0; i < grp.size(); i++) {
                int k = grp.get(i);
                int cx = x + (i % cols) * cell, ry = cy + (i / cols) * cell;
                if (ry + cell < y || ry > y + h) continue;
                var nd = nodes.get(k);
                boolean goal = k == next;
                int frame = goal ? alpha((int) (150 + 80 * Math.sin(ms / 300.0)), ACCENT) : built[k] ? GREEN : NEVER;
                rrect(g, cx, ry, cx + cell - 2, ry + cell - 2, frame);
                rrect(g, cx + 1, ry + 1, cx + cell - 3, ry + cell - 3, CARD);
                MachineThumbs.draw(g, nd.item(), cx + 1, ry + 1, cell - 4, 1f);
                if (mx >= cx && mx < cx + cell - 2 && my >= Math.max(ry, y) && my < Math.min(ry + cell - 2, y + h)) {
                    List<FormattedCharSequence> tip = new ArrayList<>(linkTip(nd));
                    String status = tr(built[k] ? "compendium.ach.built" : goal ? "compendium.ach.next" : "compendium.ach.missing");
                    tip.add(1, Component.literal((k + 1) + ". " + status)
                            .withStyle(built[k] ? ChatFormatting.GREEN : goal ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY).getVisualOrderText());
                    loopTip = tip;
                    int[] target = findPage(nd.page());
                    if (target != null) hits.add(new Hit(cx, Math.max(ry, y), cx + cell - 2, Math.min(ry + cell - 2, y + h), 0, target[0], target[1]));
                }
            }
            cy += (grp.size() + cols - 1) / cols * cell + 6;
        }
        if (!any) g.drawString(font, tr("compendium.search.none"), x, cy + 4, MUTED, false);
        g.disableScissor();
        if (total > h) {
            int thumbH = Math.max(14, h * h / total);
            int thumbY = y + (h - thumbH) * scroll / Math.max(1, total - h);
            bar.track(x + w - 3, y, 3, h, thumbH, total - h);
            g.fill(x + w - 3, y, x + w, y + h, 0xFF1E222B);
            g.fill(x + w - 3, thumbY, x + w, thumbY + thumbH, 0xFF5A6478);
        }
    }

    private static boolean gotItem(String path) { return com.sqc.nuclearsq.progression.Discovered.known(path); }

    private static boolean gotMilestone(String key) { return GuideProgress.known(key); }

    private static boolean stepDone(Guide.Step s) { return Guide.done(s, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone); }

    private boolean guideFollow = true;
    private int guideTotal = 0;

    private static ItemStack modStack(String path) {
        var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, path));
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static String needLabel(String need) {
        if (need.startsWith("i:")) {
            ItemStack st = modStack(need.substring(2));
            return st.isEmpty() ? need.substring(2) : st.getHoverName().getString();
        }
        String m = need.substring(2);
        if (m.startsWith("formed:")) return tr("compendium.guide.need.formed");
        if (m.startsWith("power:")) return tr("compendium.guide.need.power");
        return tr("compendium.guide.ms." + m);
    }

    private List<FormattedCharSequence> wrapParas(String text, int w) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            if (para.isBlank()) continue;
            out.addAll(font.split(rich(para), w));
        }
        return out;
    }

    private void drawGuide(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        if (p.id().equals(Guide.OVERVIEW)) drawGuideOverview(g, mx, my, x, y, w, h);
        else drawGuideChapter(g, mx, my, x, y, w, h, p);
    }

    private void scrollBar(GuiGraphics g, int x, int y, int w, int h, int total) {
        if (total <= h) return;
        int thumbH = Math.max(14, h * h / total);
        int thumbY = y + (h - thumbH) * scroll / Math.max(1, total - h);
        bar.track(x + w - 3, y, 3, h, thumbH, total - h);
        g.fill(x + w - 3, y, x + w, y + h, 0xFF1E222B);
        g.fill(x + w - 3, thumbY, x + w, thumbY + thumbH, 0xFF5A6478);
    }

    private void drawGuideChapter(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Page p) {
        Guide.Chapter ch = Guide.chapter(p.id().substring("guide_".length()));
        if (ch == null) return;
        int top = y;
        String key = "compendium." + p.id();
        if (has(key + ".text")) {
            for (FormattedCharSequence s : wrapParas(tr(key + ".text"), w - 6)) {
                g.drawString(font, s, x, y, MUTED, false);
                y += LINE_H;
            }
            y += 3;
        }
        List<com.sqc.nuclearsq.progression.LineTrees.Line> chLines = new ArrayList<>();
        for (Guide.Step st : ch.steps())
            for (var nd : com.sqc.nuclearsq.progression.AchievementTree.NODES)
                if (nd.page().equals(st.page())) {
                    var l = lineOf(nd.key());
                    if (l != null && !chLines.contains(l)) chLines.add(l);
                }
        if (!chLines.isEmpty()) {
            String lab = tr("compendium.brief.line") + ":";
            g.drawString(font, lab, x, y, MUTED, false);
            int lx = x + font.width(lab) + 6;
            for (var l : chLines) {
                String t = tr("compendium." + l.page() + ".title");
                int tw = font.width(t);
                if (lx + tw > x + w) break;
                boolean hv = mx >= lx && mx < lx + tw && my >= y - 1 && my < y + 9;
                g.drawString(font, t, lx, y, hv ? 0xFFFFFFFF : CYAN, false);
                int[] tgt = findPage(l.page());
                if (tgt != null) hits.add(new Hit(lx, y - 1, lx + tw, y + 9, 0, tgt[0], tgt[1]));
                lx += tw + 10;
            }
            y += LINE_H + 3;
        }
        int done = Guide.count(ch, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone);
        y += progressBar(g, x, y, w, "compendium.guide.progress", done, ch.steps().size()) + 2;
        h -= y - top;
        final int cw = w - 6, gutter = 18;
        int goal = Guide.next(ch, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone);
        int n = ch.steps().size();
        int[] heights = new int[n];
        int[] rightW = new int[n];
        List<List<FormattedCharSequence>> texts = new ArrayList<>();
        int total = 0, goalY = 0;
        for (int i = 0; i < n; i++) {
            Guide.Step s = ch.steps().get(i);
            rightW[i] = Math.max(78, Math.min(s.show().size(), 4) * 18 + 6);
            texts.add(wrapParas(tr("compendium.guide.step." + s.key() + ".text"), cw - gutter - rightW[i] - 14));
            heights[i] = stepDone(s) ? 21 : Math.max(52, 6 + 13 + texts.get(i).size() * LINE_H + 4 + s.need().size() * 11 + 7);
            if (i == goal) goalY = total;
            total += heights[i] + 6;
        }
        guideTotal = total;
        if (guideFollow) {
            scroll = Math.max(0, goalY - 4);
            guideFollow = false;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - h)));
        long ms = System.currentTimeMillis();
        int pulse = (int) (150 + 80 * Math.sin(ms / 300.0));
        int clipY1 = y + h;
        g.enableScissor(x, y, x + w, clipY1);
        int cy = y - scroll;
        for (int i = 0; i < n; i++) {
            int hh = heights[i];
            if (cy + hh >= y && cy <= clipY1) drawGuideCard(g, mx, my, x, cy, cw, hh, i, ch.steps().get(i), texts.get(i), i == goal, rightW[i], gutter, pulse, y, clipY1);
            cy += hh + 6;
        }
        g.disableScissor();
        scrollBar(g, x, y, w, h, total);
    }

    private void drawGuideCard(GuiGraphics g, int mx, int my, int x, int cy, int cw, int hh, int index, Guide.Step s, List<FormattedCharSequence> text,
            boolean goal, int rightW, int gutter, int pulse, int clipY0, int clipY1) {
        boolean done = stepDone(s);
        String base = "compendium.guide.step." + s.key();
        rrect(g, x, cy, x + cw, cy + hh, goal ? alpha(pulse, ACCENT) : done ? GREEN : EDGE);
        rrect(g, x + 1, cy + 1, x + cw - 1, cy + hh - 1, CARD);
        stateBox(g, x + 7, cy + 6, done ? 1 : goal ? 2 : 0);
        int tx = x + gutter + 4, tw = cw - gutter - rightW - 14;
        int[] target = findPage(s.page());
        if (done) {
            g.drawString(font, font.plainSubstrByWidth(tr(base + ".title"), cw - gutter - 32), tx, cy + 6, MUTED, false);
            boolean vis = cy + 18 > clipY0 && cy + 2 < clipY1;
            for (String id : s.show()) {
                ItemStack st = modStack(id);
                if (st.isEmpty()) continue;
                item(g, st, x + cw - 22, cy + 2, vis ? mx : -1000, vis ? my : -1000);
                break;
            }
            if (target != null) hits.add(new Hit(x, Math.max(cy, clipY0), x + cw, Math.min(cy + hh, clipY1), 0, target[0], target[1]));
            return;
        }
        String next = tr("compendium.guide.next");
        net.minecraft.network.chat.FormattedText title = font.substrByWidth(Component.literal(tr(base + ".title")).withStyle(ChatFormatting.BOLD), tw - 8 - (goal ? font.width(next) + 12 : 0));
        g.drawString(font, Language.getInstance().getVisualOrder(title), tx, cy + 6, goal ? 0xFFFFFFFF : TEXT, false);
        if (goal) g.drawString(font, next, tx + font.width(title) + 12, cy + 6, ACCENT, false);
        int ty = cy + 19;
        for (FormattedCharSequence line : text) {
            g.drawString(font, line, tx, ty, TEXT, false);
            ty += LINE_H;
        }
        ty += 4;
        for (String need : s.need()) {
            boolean ok = Guide.met(need, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone);
            stateBox(g, tx, ty, ok ? 1 : 0);
            String label = needLabel(need);
            g.drawString(font, font.plainSubstrByWidth(label, tw - 14), tx + 13, ty + 1, ok ? MUTED : 0xFFFFFFFF, false);
            if (need.startsWith("i:") && mx >= tx && mx < tx + 13 + font.width(label) && my >= Math.max(ty, clipY0) && my < Math.min(ty + 10, clipY1)) {
                ItemStack st = modStack(need.substring(2));
                if (!st.isEmpty()) hover = st;
            }
            ty += 11;
        }
        int rx = x + cw - rightW - 6, iy = cy + 6;
        int shown = 0;
        for (String id : s.show()) {
            if (shown >= 4) break;
            ItemStack st = modStack(id);
            if (st.isEmpty()) continue;
            boolean vis = iy + 16 > clipY0 && iy < clipY1;
            item(g, st, rx + shown * 18, iy, vis ? mx : -1000, vis ? my : -1000);
            shown++;
        }
        button(g, mx, my, rx, cy + hh - 21, rightW, tr("compendium.guide.open"), target, goal, clipY0, clipY1);
    }

    private static int guideNext() {
        for (int c = 0; c < Guide.CHAPTERS.size(); c++)
            if (Guide.next(Guide.CHAPTERS.get(c), ProgressionScreen::gotItem, ProgressionScreen::gotMilestone) >= 0) return c;
        return -1;
    }

    private static int guideNo(String pageId) {
        for (int c = 0; c < Guide.CHAPTERS.size(); c++) if (Guide.CHAPTERS.get(c).page().equals(pageId)) return c + 1;
        return 0;
    }

    private void drawGuideOverview(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        String key = "compendium." + Guide.OVERVIEW;
        final int cw = w - 6;
        int all = 0, done = 0, nextC = guideNext();
        for (Guide.Chapter ch : Guide.CHAPTERS) {
            all += ch.steps().size();
            done += Guide.count(ch, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone);
        }
        guideFollow = false;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, guideTotal - h)));
        g.enableScissor(x, y, x + w, y + h);
        int cy = y - scroll;
        if (has(key + ".text")) {
            for (FormattedCharSequence s : wrapParas(tr(key + ".text"), cw)) {
                g.drawString(font, s, x, cy, MUTED, false);
                cy += LINE_H;
            }
            cy += 4;
        }
        cy += progressBar(g, x, cy, cw, "compendium.guide.progress_all", done, all) + 4;
        cy += drawGoalCard(g, mx, my, x, cy, cw, y, y + h) + 6;
        int cols = cw >= 330 ? 2 : 1, tw = (cw - (cols - 1) * 6) / cols, col = 0;
        for (int c = 0; c < Guide.CHAPTERS.size(); c++) {
            if (c == 0 || c == Guide.MAIN) {
                if (col > 0) cy += 32;
                col = 0;
                g.drawString(font, tr(c == 0 ? "compendium.guide.main" : "compendium.guide.optional").toUpperCase(java.util.Locale.ROOT), x, cy + 4, ACCENT, false);
                cy += 16;
            }
            Guide.Chapter ch = Guide.CHAPTERS.get(c);
            int cnt = Guide.count(ch, ProgressionScreen::gotItem, ProgressionScreen::gotMilestone), tot = ch.steps().size(), tx = x + col * (tw + 6);
            boolean over = mx >= tx && mx < tx + tw && my >= Math.max(cy, y) && my < Math.min(cy + 28, y + h);
            rrect(g, tx, cy, tx + tw, cy + 28, over || c == nextC ? ACCENT : cnt == tot ? GREEN : EDGE);
            rrect(g, tx + 1, cy + 1, tx + tw - 1, cy + 27, over ? HOVER_BG : CARD);
            stateBox(g, tx + 6, cy + 5, cnt == tot ? 1 : c == nextC ? 2 : 0);
            String cnts = cnt + "/" + tot;
            g.drawString(font, cnts, tx + tw - 6 - font.width(cnts), cy + 6, cnt == tot ? GREEN : MUTED, false);
            g.drawString(font, font.plainSubstrByWidth((c + 1) + " " + tr("compendium.guide_" + ch.id() + ".title"), tw - 32 - font.width(cnts)), tx + 19, cy + 6, c == nextC ? 0xFFFFFFFF : TEXT, false);
            g.fill(tx + 6, cy + 20, tx + tw - 6, cy + 23, 0xFF101216);
            g.fill(tx + 6, cy + 20, tx + 6 + (tw - 12) * cnt / Math.max(1, tot), cy + 23, GREEN);
            int[] target = findPage(ch.page());
            if (target != null && over) hits.add(new Hit(tx, Math.max(cy, y), tx + tw, Math.min(cy + 28, y + h), 0, target[0], target[1]));
            if (++col == cols) {
                cy += 32;
                col = 0;
            }
        }
        if (col > 0) cy += 32;
        g.disableScissor();
        guideTotal = cy + scroll - y;
        scrollBar(g, x, y, w, h, guideTotal);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int argb) {
        int steps = Math.max(24, r * 6);
        for (int s = 0; s < steps; s++) {
            double a = s * Math.PI * 2 / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, argb);
        }
    }

    private static int alpha(int a, int rgb) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }

    private static float ease(float x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x); }

    private void label(GuiGraphics g, String key, int x, int y, int color) {
        int ly = y;
        for (String ln : tr(key).split("\n")) {
            g.drawString(font, ln, x, ly, color, false);
            ly += 10;
        }
    }

    private void labelR(GuiGraphics g, String key, int xr, int y, int color) {
        int ly = y;
        for (String ln : tr(key).split("\n")) {
            g.drawString(font, ln, xr - font.width(ln), ly, color, false);
            ly += 10;
        }
    }

    private static void flame(GuiGraphics g, int cx, int base, float t) {
        float f = 1f + 0.12f * (float) Math.sin(t * 9) + 0.06f * (float) Math.sin(t * 17);
        int hh = (int) (22 * f);
        for (int row = 0; row < hh; row++) {
            float u = row / (float) hh;
            float half = (float) Math.sin(Math.pow(u, 0.7) * Math.PI * 0.9) * 8 * (0.6f + 0.4f * u);
            int sway = (int) (Math.sin(t * 6 + u * 4) * (1 - u) * 2);
            int y = base - hh + row;
            int hw = (int) half;
            g.fill(cx - hw + sway, y, cx + hw + 1 + sway, y + 1, 0xFFE8542A);
            int iw = (int) (half * 0.62f);
            if (u > 0.25f) g.fill(cx - iw + sway, y, cx + iw + 1 + sway, y + 1, 0xFFFFA630);
            int cw = (int) (half * 0.3f);
            if (u > 0.5f) g.fill(cx - cw + sway, y, cx + cw + 1 + sway, y + 1, 0xFFFFE680);
        }
    }

    private static void drawLogo(GuiGraphics g, int x, int y, int w) {
        int h = w * LOGO_H / LOGO_W;
        boolean small = w * Minecraft.getInstance().getWindow().getGuiScale() <= (LOGO_W + LOGO_SMALL_W) / 2.0;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        if (small) g.blit(LOGO_SMALL, x, y, w, h, 0, 0, LOGO_SMALL_W, LOGO_SMALL_H, LOGO_SMALL_W, LOGO_SMALL_H);
        else g.blit(LOGO, x, y, w, h, 0, 0, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void stateBox(GuiGraphics g, int x, int y, int state) {
        rrect(g, x, y, x + 9, y + 9, state == 1 ? GREEN : state == 2 ? ACCENT : NEVER);
        if (state != 1) rrect(g, x + 1, y + 1, x + 8, y + 8, CARD);
        else for (int[] d : new int[][] {{2, 4}, {3, 5}, {4, 4}, {5, 3}, {6, 2}}) g.fill(x + d[0], y + d[1], x + d[0] + 1, y + d[1] + 2, 0xFFFFFFFF);
    }

    private void button(GuiGraphics g, int mx, int my, int bx, int by, int bw, String label, int[] target, boolean primary, int top, int bottom) {
        int y0 = Math.max(by, top), y1 = Math.min(by + 15, bottom);
        boolean over = mx >= bx && mx < bx + bw && my >= y0 && my < y1;
        rrect(g, bx, by, bx + bw, by + 15, primary ? (over ? 0xFFFFC060 : ACCENT) : EDGE);
        if (!primary) rrect(g, bx + 1, by + 1, bx + bw - 1, by + 14, over ? ACTIVE_BG : CARD);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 4, primary ? 0xFF14171D : over ? 0xFFFFFFFF : TEXT, false);
        if (target != null && y1 > y0) hits.add(new Hit(bx, y0, bx + bw, y1, 0, target[0], target[1]));
    }

    private int drawGoalCard(GuiGraphics g, int mx, int my, int x, int y, int w, int top, int bottom) {
        int nextC = -1, nextS = -1;
        for (int c = 0; c < Guide.CHAPTERS.size() && nextC < 0; c++) {
            int nx = Guide.next(Guide.CHAPTERS.get(c), ProgressionScreen::gotItem, ProgressionScreen::gotMilestone);
            if (nx >= 0) { nextC = c; nextS = nx; }
        }
        int hh = 46, bw = Math.max(font.width(tr("compendium.welcome.continue")), font.width(tr("compendium.welcome.all_steps"))) + 16, bx = x + w - bw - 6;
        rrect(g, x, y, x + w, y + hh, nextC < 0 ? GREEN : alpha((int) (150 + 80 * Math.sin(System.currentTimeMillis() / 300.0)), ACCENT));
        rrect(g, x + 1, y + 1, x + w - 1, y + hh - 1, CARD);
        if (nextC < 0) g.drawString(font, font.plainSubstrByWidth(tr("compendium.guide.alldone"), bx - x - 16), x + 8, y + 19, GREEN, false);
        else {
            Guide.Chapter ch = Guide.CHAPTERS.get(nextC);
            Guide.Step s = ch.steps().get(nextS);
            g.drawString(font, tr("compendium.guide.goal").toUpperCase(java.util.Locale.ROOT), x + 8, y + 6, ACCENT, false);
            int tx = x + 8;
            if (!s.show().isEmpty()) {
                MachineThumbs.drawStack(g, modStack(s.show().get(0)), tx, y + 22);
                tx += 22;
            }
            g.drawString(font, Component.literal(font.plainSubstrByWidth(tr("compendium.guide.step." + s.key() + ".title"), bx - tx - 16)).withStyle(ChatFormatting.BOLD), tx, y + 20, 0xFFFFFFFF, false);
            String where = tr("compendium.welcome.goal.where").formatted(tr("compendium." + ch.page() + ".title"), nextS + 1, ch.steps().size());
            g.drawString(font, font.plainSubstrByWidth(where, bx - tx - 8), tx, y + 31, MUTED, false);
            button(g, mx, my, bx, y + 6, bw, tr("compendium.welcome.continue"), findPage(ch.page()), true, top, bottom);
        }
        button(g, mx, my, bx, y + (nextC < 0 ? 15 : 25), bw, tr("compendium.welcome.all_steps"), findPage(Guide.OVERVIEW), false, top, bottom);
        return hh;
    }

    private int drawWelcome(GuiGraphics g, int mx, int my, int x, int y, int w, int top, int bottom) {
        int y0 = y;
        for (FormattedCharSequence s : font.split(Component.literal(tr("compendium.intro_welcome.text")), w)) {
            g.drawString(font, s, x, y, MUTED, false);
            y += LINE_H;
        }
        y += 4;
        y += drawGoalCard(g, mx, my, x, y, w, top, bottom) + 8;
        g.drawString(font, tr("compendium.welcome.what").toUpperCase(java.util.Locale.ROOT), x, y, ACCENT, false);
        y += 12;
        List<Integer> secs = new ArrayList<>();
        for (int k = 0; k < SECTIONS.size(); k++) if (has("compendium.welcome.tile." + SECTIONS.get(k).id())) secs.add(k);
        for (int k = 0; k < secs.size(); k++)
            if (SECTIONS.get(secs.get(k)).id().equals("examples")) { secs.add(secs.remove(k)); break; }
        boolean wide = w >= 440;
        int cols = wide ? 4 : 2, tw = (w - (cols - 1) * 6) / cols, th = 58;
        for (int i = 0; i < secs.size(); i++) {
            Section sec = SECTIONS.get(secs.get(i));
            int tx = x + (i % cols) * (tw + 6), ty = y + (i / cols) * (th + 6), ty0 = Math.max(ty, top), ty1 = Math.min(ty + th, bottom);
            boolean hv = mx >= tx && mx < tx + tw && my >= ty0 && my < ty1;
            rrect(g, tx, ty, tx + tw, ty + th, hv ? ACCENT : EDGE);
            rrect(g, tx + 1, ty + 1, tx + tw - 1, ty + th - 1, hv ? ACTIVE_BG : CARD);
            MachineThumbs.drawStack(g, sec.icon().get(), tx + 5, ty + 5);
            g.drawString(font, Component.literal(font.plainSubstrByWidth(tr("compendium.section." + sec.id()), tw - 46)).withStyle(ChatFormatting.BOLD), tx + 26, ty + 9, 0xFFFFFFFF, false);
            List<FormattedCharSequence> ls = font.split(Component.literal(tr("compendium.welcome.tile." + sec.id())), tw - 12);
            for (int k = 0; k < Math.min(3, ls.size()); k++) g.drawString(font, ls.get(k), tx + 6, ty + 25 + k * 10, MUTED, false);
            if (ty1 > ty0) hits.add(new Hit(tx, ty0, tx + tw, ty1, 0, firstChapter(secs.get(i)), 0));
        }
        y += (secs.size() + cols - 1) / cols * (th + 6) + 6;
        g.drawString(font, tr("compendium.welcome.read").toUpperCase(java.util.Locale.ROOT), x, y, ACCENT, false);
        y += 12;
        String[] keys = {"click", "link", "frames"}, st = {"built", "goal", "never"};
        int fw = 6;
        for (String s : st) fw += font.width(tr("compendium.lines." + s)) + 20;
        for (int i = 0, cx = x; i < 3; i++) {
            int cw = !wide ? w : i < 2 ? (w - 12 - fw) / 2 : x + w - cx;
            rrect(g, cx, y, cx + cw, y + 30, EDGE);
            rrect(g, cx + 1, y + 1, cx + cw - 1, y + 29, CARD);
            g.drawString(font, Component.literal(tr("compendium.welcome.help." + keys[i] + ".title")).withStyle(ChatFormatting.BOLD), cx + 6, y + 5, 0xFFFFFFFF, false);
            if (i < 2) g.drawString(font, font.plainSubstrByWidth(tr("compendium.welcome.help." + keys[i] + ".text"), cw - 12), cx + 6, y + 17, MUTED, false);
            else {
                for (int k = 0, sx = cx + 6; k < 3; k++) {
                    stateBox(g, sx, y + 16, k == 0 ? 1 : k == 1 ? 2 : 0);
                    String s = tr("compendium.lines." + st[k]);
                    g.drawString(font, s, sx + 12, y + 17, MUTED, false);
                    sx += font.width(s) + 20;
                }
            }
            if (wide) cx += cw + 6;
            else y += 34;
        }
        if (wide) y += 34;
        return y - y0;
    }

    private void item(GuiGraphics g, ItemStack st, int x, int y, int mx, int my) {
        MachineThumbs.drawStack(g, st, x, y);
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) hover = st;
    }

    private void drawChain(GuiGraphics g, int x, int y, int w, int h, float t, int mx, int my, ItemStack[] items, String[] labelKeys) {
        int n = items.length;
        int span = w - 56;
        int cy = y + h / 2 - 4;
        float pulse = (t * 0.35f) % 1f;
        for (int k = 0; k < n; k++) {
            int cx = x + 28 + (n == 1 ? span / 2 : k * span / (n - 1));
            if (k < n - 1) {
                int nx = x + 28 + (k + 1) * span / (n - 1);
                g.fill(cx + 16, cy, nx - 16, cy + 2, EDGE);
            }
            float pos = pulse * (n - 1);
            float near = 1f - Math.min(1f, Math.abs(pos - k));
            int border = near > 0.35f ? ACCENT : EDGE;
            rrect(g, cx - 15, cy - 15, cx + 15, cy + 17, border);
            rrect(g, cx - 14, cy - 14, cx + 14, cy + 16, CARD);
            item(g, items[k], cx - 8, cy - 8, mx, my);
            g.drawCenteredString(font, Component.translatable(labelKeys[k]), cx, cy + 22, near > 0.35f ? 0xFFFFFFFF : TEXT);
            g.drawCenteredString(font, String.valueOf(k + 1), cx, cy - 28, near > 0.35f ? ACCENT : MUTED);
        }
        int dotX = x + 28 + (int) (pulse * span);
        circle(g, dotX, cy + 1, 3, ACCENT);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h, float t) {
        String id = current().id();
        float s1 = 0.5f + 0.5f * (float) Math.sin(t * 0.9f), s2 = 0.5f + 0.5f * (float) Math.sin(t * 0.7f + 1f), s3 = 0.5f + 0.5f * (float) Math.sin(t * 0.5f + 2f);
        int r = 25, cy = y + 33;
        int[] gx = {x + w / 6, x + w / 2, x + w * 5 / 6};
        switch (id) {
            case "reactor_terminal" -> {
                int max = com.sqc.nuclearsq.machine.big.DriveCore.MAX_POWER;
                GuiKit.gauge(g, font, gx[0], cy, r, 0.3f + 0.6f * s2, 0.8f, String.valueOf((int) (30 + 60 * s2)), "%", Component.translatable("gui.nuclearsq.device.rod.k.heat"));
                GuiKit.gauge(g, font, gx[1], cy, r, 0.4f + 0.4f * s1, 2f, String.format("%.2f", 0.3 + 0.4 * s1), "/t", Component.translatable("gui.nuclearsq.device.rod.k.cooling"));
                GuiKit.gauge(g, font, gx[2], cy, r, 0.5f + 0.4f * s3, 2f, String.valueOf((int) (max * (0.5f + 0.4f * s3))), "", Component.translatable("gui.nuclearsq.device.rod.k.power"));
                float depth = 0.5f + 0.5f * (float) Math.sin(t * 0.6f);
                GuiKit.slider(g, font, x + 20, y + h - 18, w - 40, 11, depth, Component.translatable("compendium.anim.target", (int) (depth * 15), 15), 0xFF3F74A8);
            }
            case "energy_turbine" -> {
                GuiKit.gauge(g, font, gx[0], cy, r, s1, 0.85f, String.valueOf((int) (3000 * s1)), "rpm", Component.translatable("compendium.anim.rpm"));
                GuiKit.gauge(g, font, gx[1], cy, r, s1, 0.9f, (int) (819.2f * s1) + "k", "SU", Component.translatable("gui.nuclearsq.turbine.f.load"));
                GuiKit.gauge(g, font, gx[2], cy, r, 0.3f + 0.5f * s1, 0.8f, String.valueOf((int) (60 + 200 * s1)), "°C", Component.translatable("compendium.anim.temp"));
                GuiKit.slider(g, font, x + 20, y + h - 18, w - 40, 11, s2, Component.translatable("gui.nuclearsq.turbine.throttle", (int) (s2 * 100)), 0xFF4E7A3A);
            }
            default -> {
                GuiKit.gauge(g, font, gx[0], cy, r, 0.4f + 0.5f * s1, 2f, String.valueOf((int) (40 + 50 * s1)), "%", Component.translatable("gui.nuclearsq.device.energy"));
                GuiKit.gauge(g, font, gx[1], cy, r, 0.5f + 0.4f * s2, 2f, String.valueOf((int) (50 + 40 * s2)), "%", Component.translatable("gui.nuclearsq.device.coolant"));
                GuiKit.gauge(g, font, gx[2], cy, r, 0.3f + 0.6f * s3, 2f, String.format("%.2f", 0.4 + 0.8 * s3), "/t", Component.translatable("gui.nuclearsq.device.cooling"));
                g.drawCenteredString(font, tr("compendium.anim.menus"), x + w / 2, y + h - 16, MUTED);
            }
        }
    }

    private void drawAnim(GuiGraphics g, Anim a, int x, int y, int w, int h, float t, int mx, int my) {
        int cx = x + w / 2, cy = y + h / 2;
        switch (a) {
            case ROD -> {
                boolean tier2 = current().id().equals("rod_reinforced");
                boolean advanced = current().id().equals("rod_advanced");
                boolean tier4 = advanced && ((int) (t / 9f)) % 2 == 1;
                float ph = t % 9f;
                int baseY = y + h - 20, sx = cx - 8;
                ItemStack blk = new ItemStack(tier4 ? ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get()
                        : advanced ? ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get()
                        : tier2 ? ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get() : ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get());
                ItemStack head = new ItemStack(tier4 ? ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL_ITEM.get()
                        : advanced ? ReactorRegistry.FUEL_ROD_HEAD_HARDENED_ITEM.get() : ReactorRegistry.FUEL_ROD_HEAD_ITEM.get());
                int tint = tier4 ? 0x56C2F0 : advanced ? 0xE07050 : tier2 ? 0x4FA0FF : 0xFFE050;
                String hulls = tier4 ? "compendium.anim.hulls_experimental" : advanced ? "compendium.anim.hulls_hardened"
                        : tier2 ? "compendium.anim.hulls_reinforced" : "compendium.anim.hulls";
                for (int k = 0; k < 5; k++) {
                    float start = k * 1.3f;
                    if (ph < start) continue;
                    float pr = ease((ph - start) / 0.8f);
                    int fy = baseY - k * 15;
                    int cyk = (int) (y - 16 + (fy - (y - 16)) * pr);
                    item(g, k < 4 ? blk : head, sx, cyk, mx, my);
                }
                if (ph > 6.8f) circle(g, sx + 8, baseY - 28, 30, alpha((int) (40 * Math.sin((ph - 6.8f) * 3.5f)), tint));
                label(g, "compendium.anim.docker", sx + 26, baseY - 60 + 4, 0xFFB060);
                label(g, hulls, sx + 26, baseY - 22 + 4, tier2 ? 0x7FB8FF : advanced ? tint : 0xFFB060);
                labelR(g, "compendium.anim.stack", sx - 12, baseY - 40, 0xDDDDDD);
            }
            case ENERGY -> {
                drawChain(g, x, y, w, h, t, mx, my,
                        new ItemStack[] {b("fuel_rod_block_sqc").isEmpty() ? new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()) : b("fuel_rod_block_sqc"),
                                new ItemStack(com.sqc.nuclearsq.registry.BigMachineRegistry.CONTROLLER_ITEMS.get(com.sqc.nuclearsq.machine.big.BigMachine.CONTROL_ROD_DRIVE).get()), i("machine_port_steam_sqc"),
                                new ItemStack(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get()), i("copper_coil_sqc")},
                        new String[] {"compendium.anim.step.rod", "compendium.anim.step.drive", "compendium.anim.step.steam", "compendium.anim.step.turbine", "compendium.anim.step.power"});
                String su = "+" + (int) (640 + 160 * Math.sin(t * 1.3)) + "k SU";
                g.drawString(font, su, x + w - 8 - font.width(su), y + 6, 0xFF55FF55, false);
            }
            case HEAT -> {
                float v = 0.5f + 0.45f * (float) Math.sin(t * 1.2);
                int bw = w - 60, bx = x + 40, by = cy - 5;
                g.fill(bx - 1, by - 1, bx + bw + 1, by + 13, 0xFF555A66);
                g.fill(bx, by, bx + bw, by + 12, 0xFF101216);
                int fill = (int) (bw * v);
                for (int k = 0; k < fill; k++) {
                    float f = k / (float) bw;
                    int r = (int) Math.min(255, 80 + f * 400), gr = (int) Math.max(0, 255 - Math.max(0, f - 0.4f) * 450);
                    g.fill(bx + k, by, bx + k + 1, by + 12, 0xFF000000 | (r << 16) | (gr << 8) | 0x30);
                }
                String state = v < 0.45f ? "compendium.anim.cold" : v < 0.8f ? "compendium.anim.ok" : "compendium.anim.hot";
                label(g, state, bx, by - 14, v < 0.8f ? 0x66FF66 : 0xFF5555);
                label(g, "compendium.anim.cooling", bx, by + 18, 0x6FB0FF);
                flame(g, x + 10, by + 12, t);
                if (v > 0.8f) g.fill(bx, by - 2, bx + bw, by - 1, alpha((int) (160 + 90 * Math.sin(t * 12)), 0xFF3030));
            }
            case BOOM -> {
                float tt = t % 8f;
                for (int k = 0; k < 3; k++) {
                    float f = ((tt * 0.4f + k / 3f) % 1f);
                    ring(g, x + 40, cy, (int) (4 + f * 32), alpha((int) (220 * (1 - f)), 0xFF6030));
                }
                item(g, new ItemStack(AssemblyRegistry.DETONATOR_ITEM.get()), x + 32, cy - 8, mx, my);
                int bx = x + 90, bw = w - 100, by = cy + 6;
                for (int k = 0; k < 8; k++) {
                    float f = (tt * 0.25f + k / 8f) % 1f;
                    int px = bx + bw - 24 + (int) (Math.sin((k + tt) * 1.7) * 5 * f), py = by + 10 - (int) (f * 44), s = (int) (3 + f * 9);
                    g.fill(px - s, py - s / 2, px + s, py + s / 2, alpha((int) (200 * (1 - f)), 0xDDE4EA));
                }
                label(g, "compendium.anim.flash", bx, by - 16, 0xDDDDDD);
                label(g, "compendium.anim.radius", x + 8, y + h - 12, 0xFF8060);
            }
            case ROADMAP -> drawChain(g, x, y, w, h, t, mx, my,
                    new ItemStack[] {new ItemStack(AssemblyRegistry.ASSEMBLER_ITEM.get()), new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()),
                            new ItemStack(com.sqc.nuclearsq.registry.BigMachineRegistry.CONTROLLER_ITEMS.get(com.sqc.nuclearsq.machine.big.BigMachine.CONTROL_ROD_DRIVE).get()), b("pump_basic_sqc"),
                            i("machine_port_steam_sqc"), new ItemStack(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get())},
                    new String[] {"compendium.anim.step.assembler", "compendium.anim.step.rod", "compendium.anim.step.drive",
                            "compendium.anim.step.cooling", "compendium.anim.step.steam", "compendium.anim.step.turbine"});
            case PANEL -> drawPanel(g, x, y, w, h, t);
            case MACHINE -> {
                float ph = t % 5f;
                List<FormattedCharSequence> fe = new ArrayList<>();
                for (String ln : tr("compendium.anim.fe").split("\n")) fe.addAll(font.split(Component.literal(ln), Math.max(30, w - 150)));
                int lw = 0;
                for (FormattedCharSequence ln : fe) lw = Math.max(lw, font.width(ln));
                int gw = 16 + 36 + 28 + 36 + 16 + 14 + lw, gx = cx - gw / 2;
                int ax = gx + 52, ox = ax + 28 + 36;
                ItemStack[] in = {i("zirconium_ingot_sqc"), i("steel_sheet_sqc"), new ItemStack(net.minecraft.world.item.Items.REDSTONE)};
                for (int k = 0; k < 3; k++) {
                    float f = ease((ph - k * 0.5f) / 1.2f);
                    if (ph < k * 0.5f || f >= 1f) continue;
                    int sy = cy - 8 + (k - 1) * 12;
                    item(g, in[k], (int) (gx + (ax + 6 - gx) * f), (int) (sy + (cy - 12 - sy) * f), mx, my);
                }
                rrect(g, ax, cy - 16, ax + 28, cy + 8, EDGE);
                rrect(g, ax + 1, cy - 15, ax + 27, cy + 7, CARD);
                item(g, new ItemStack(AssemblyRegistry.ASSEMBLER_ITEM.get()), ax + 6, cy - 12, mx, my);
                float prog = Math.max(0, Math.min(1, (ph - 1.8f) / 2.2f));
                g.fill(ax - 4, cy + 11, ax + 32, cy + 15, 0xFF101216);
                g.fill(ax - 4, cy + 11, ax - 4 + (int) (36 * prog), cy + 15, GREEN);
                if (ph > 4f) item(g, i("reactor_plate_sqc"), (int) (ax + 30 + (ox - ax - 30) * ease((ph - 4f) / 0.6f)), cy - 12, mx, my);
                int ly = cy - fe.size() * 5;
                for (FormattedCharSequence ln : fe) {
                    g.drawString(font, ln, ox + 30, ly, 0x55FF55, false);
                    ly += 10;
                }
            }
            default -> { }
        }
    }
}
