package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.registry.PowerRegistry;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SolarCollectorBlockEntity extends SolarPanelBlockEntity {
    public static final int OUT = 16384;
    public static final int NO_ROUTE = Integer.MAX_VALUE;
    public static final int MAX_GROUP = 1024;

    private int dist = NO_ROUTE;
    private long stamp = -1000;
    private int outThisTick;
    private long outTick = -1;

    public SolarCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerRegistry.SOLAR_COLLECTOR_BE.get(), pos, state, producing(state) ? peakPerTick() : 0);
    }

    public static int peakPerTick() {
        return 312;
    }

    private static boolean producing(BlockState state) {
        if (!state.hasProperty(SolarCollectorBlock.PART)) return true;
        int p = state.getValue(SolarCollectorBlock.PART);
        return p == 0 || p == SolarCollectorBlock.SINGLE;
    }

    public int distance() { return dist; }

    @Override
    protected boolean outputs(Direction d) {
        BlockState s = getBlockState();
        return !s.hasProperty(SolarCollectorBlock.PART) || SolarCollectorBlock.outputs(s, d);
    }

    @Override
    protected boolean tracks() { return true; }

    @Override
    protected void onSecond() {
        updateGroup();
    }

    @Nullable
    private SolarCollectorBlockEntity neighbour(Direction d) {
        BlockPos n = worldPosition.relative(d);
        return level.isLoaded(n) && level.getBlockEntity(n) instanceof SolarCollectorBlockEntity c && !c.isRemoved() ? c : null;
    }

    void updateGroup() {
        long now = level.getGameTime();
        if (now - stamp < 20) return;
        Map<BlockPos, SolarCollectorBlockEntity> group = new HashMap<>();
        ArrayDeque<SolarCollectorBlockEntity> open = new ArrayDeque<>();
        group.put(worldPosition, this);
        open.add(this);
        while (!open.isEmpty() && group.size() < MAX_GROUP) {
            SolarCollectorBlockEntity c = open.poll();
            for (Direction d : SIDES) {
                SolarCollectorBlockEntity n = c.neighbour(d);
                if (n != null && group.putIfAbsent(n.worldPosition, n) == null) open.add(n);
            }
        }
        open.clear();
        for (SolarCollectorBlockEntity c : group.values()) {
            c.stamp = now;
            c.dist = c.hasSink() ? 0 : NO_ROUTE;
            if (c.dist == 0) open.add(c);
        }
        while (!open.isEmpty()) {
            SolarCollectorBlockEntity c = open.poll();
            for (Direction d : SIDES) {
                SolarCollectorBlockEntity n = group.get(c.worldPosition.relative(d));
                if (n != null && n.dist > c.dist + 1) {
                    n.dist = c.dist + 1;
                    open.add(n);
                }
            }
        }
    }

    @Override
    public int deliver(int amount) {
        SolarCollectorBlockEntity at = this;
        while (at.dist > 0) {
            if (at.dist == NO_ROUTE) return 0;
            SolarCollectorBlockEntity next = null;
            for (Direction d : SIDES) {
                SolarCollectorBlockEntity c = at.neighbour(d);
                if (c != null && c.dist < at.dist && (next == null || c.dist < next.dist)) next = c;
            }
            if (next == null) return 0;
            at = next;
        }
        return at.emit(amount);
    }

    private int emit(int amount) {
        long now = level.getGameTime();
        if (outTick != now) {
            outTick = now;
            outThisTick = 0;
        }
        int given = pushToSinks(amount, OUT - outThisTick);
        outThisTick += given;
        return given;
    }
}
