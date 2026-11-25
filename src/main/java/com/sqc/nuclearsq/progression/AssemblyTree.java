package com.sqc.nuclearsq.progression;

import java.util.List;

import com.sqc.nuclearsq.progression.PlantOverview.Edge;
import com.sqc.nuclearsq.progression.PlantOverview.Node;

public final class AssemblyTree {
    public static final LineTrees.Line LINE = LineTrees.line("assembly_tree|assembly|workbench:3 coal kiln small_parts;small_parts_assembler:4 micro assembler* plate_mill cnc_mill / +hammer_mill solder_machine"
            + ";reactor_assembler:4 solar_panel solar_collector heavy equipment / +rtg rod_gripper pipe_adapter;heavy_assembler:4@heavy solar clean_room component particle / +compute_asm decon_dock quantum blast_furnace"
            + ";equipment_assembler:1@equipment exo_station|");

    public static final List<Node> NODES = LineTrees.nodes(LINE);

    public static final List<Edge> EDGES = LineTrees.edges(LINE);

    private AssemblyTree() {}
}
