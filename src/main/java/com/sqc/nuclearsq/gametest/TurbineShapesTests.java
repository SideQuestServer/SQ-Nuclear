package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.turbine.TurbineShapes;
import com.sqc.nuclearsq.turbine.TurbineShellBlock;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class TurbineShapesTests {
    private TurbineShapesTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "turbine_shapes", timeoutTicks = 20)
    public static void turbineShapesMatchPattern(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        int n = TurbineShapes.COUNT;
        if (TurbineShapes.BOXES.length != n) bad.add("BOXES " + TurbineShapes.BOXES.length + " statt " + n);
        if (TurbineShapes.LADDER.length != n) bad.add("LADDER " + TurbineShapes.LADDER.length + " statt " + n);
        if (TurbineShellBlock.SHAPE.getPossibleValues().size() != n) {
            bad.add("Blockzustand shape hat " + TurbineShellBlock.SHAPE.getPossibleValues().size() + " Werte statt " + n);
        }
        for (int i = 0; i < TurbineShapes.BOXES.length; i++) {
            for (double[] b : TurbineShapes.BOXES[i]) {
                boolean ok = b.length == 6;
                for (int k = 0; ok && k < 3; k++) ok = b[k] >= 0 && b[k + 3] <= 16 && b[k] < b[k + 3];
                if (!ok) bad.add("Form " + i + ": Quader " + java.util.Arrays.toString(b) + " liegt nicht im Blockfeld");
            }
        }
        MultiblockStructure s = MultiblockStructure.TURBINE;
        Set<Integer> used = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (int[] c : TurbineShapes.CELLS) {
            String at = c[0] + "," + c[1] + "," + c[2];
            if (c[0] < 0 || c[0] >= s.width() || c[1] < 0 || c[1] >= s.height() || c[2] < 0 || c[2] >= s.depth()) {
                bad.add("Feld " + at + " ausserhalb des Musters");
            }
            if (c[3] < 0 || c[3] >= n) bad.add("Feld " + at + ": Form " + c[3] + " gibt es nicht");
            if (!seen.add(at)) bad.add("Feld " + at + " doppelt");
            used.add(c[3]);
        }
        if (used.size() != n) bad.add((n - used.size()) + " Formen werden von keinem Feld benutzt");
        if (!bad.isEmpty()) throw new GameTestAssertException("TurbineShapes: " + String.join("; ", bad));
        h.succeed();
    }
}
