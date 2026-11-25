package com.sqc.nuclearsq.door;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.door.DoorPanelBlock.Phase;
import com.sqc.nuclearsq.machine.DeviceMenu;
import com.sqc.nuclearsq.machine.MachineAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class DoorBlockEntity extends BlockEntity implements MachineAccess.Holder {
    public static final int MAX_SIDE = 15, MIN_TRAVEL = 50;
    public static final int DATA = 6, FLAG_OPEN = 1, FLAG_MOVING = 2, FLAG_RAGGED = 4, FLAG_FOUND = 8, FLAG_LOCKED = 16;

    private final List<BlockPos> panels = new ArrayList<>();
    private Direction.Axis axis = Direction.Axis.Z;
    private DoorPanelBlock.Kind kind = DoorPanelBlock.Kind.HANGAR;
    private boolean open, powered, ragged, flip, flat = true;
    private int progress, travel = 1, clientPrev;
    private BlockPos min = BlockPos.ZERO, max = BlockPos.ZERO;
    @Nullable
    private BlockPos airlock;
    @Nullable
    private UUID owner;
    private final MachineAccess access = new MachineAccess();

    public DoorBlockEntity(BlockPos pos, BlockState state) {
        super(DoorRegistry.CONTROLLER_BE.get(), pos, state);
    }

    public List<BlockPos> panels() { return panels; }
    public Direction.Axis axis() { return axis; }
    public boolean flip() { return flip; }
    public boolean flat() { return flat; }
    public DoorPanelBlock.Kind kind() { return kind; }
    public boolean open() { return open; }
    public boolean moving() { return open ? progress < travel : progress > 0; }
    public boolean closed() { return !open && progress == 0; }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    @Nullable
    public BlockPos airlock() { return airlock; }

    @Nullable
    @Override
    public UUID owner() { return owner; }
    @Override
    public MachineAccess access() { return access; }

    public void claim(Player player) {
        if (owner != null || !(player instanceof ServerPlayer)) return;
        owner = player.getUUID();
        setChanged();
    }

    public boolean mayUse(Player player) {
        if (!(player instanceof ServerPlayer sp) || access.can(sp, owner, MachineAccess.USE)) return true;
        sp.displayClientMessage(Component.translatable("message.nuclearsq.access.denied"), true);
        return false;
    }

    public void setAirlock(@Nullable BlockPos pos) {
        airlock = pos;
        setChanged();
    }

    public boolean locked() {
        return airlock != null && level != null && level.getBlockEntity(airlock) instanceof AirlockBlockEntity a && !a.mayOpen(this);
    }

    public Direction.Axis wide() { return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X; }
    public Direction.Axis tall() { return axis == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y; }
    public int width() { return max.get(wide()) - min.get(wide()) + 1; }
    public int height() { return max.get(tall()) - min.get(tall()) + 1; }
    public int way() { return kind.up() ? height() : width(); }

    public float fraction(float partial) {
        return (clientPrev + (progress - clientPrev) * partial) / travel;
    }

    static List<BlockPos> connected(Level level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        List<BlockPos> out = new ArrayList<>();
        if (!(first.getBlock() instanceof DoorPanelBlock)) return out;
        Direction.Axis normal = first.getValue(DoorPanelBlock.AXIS);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start.immutable());
        seen.add(start.immutable());
        int[] lo = {start.getX(), start.getY(), start.getZ()}, hi = lo.clone();
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (d.getAxis() == normal || seen.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (!s.is(first.getBlock()) || s.getValue(DoorPanelBlock.AXIS) != normal) continue;
                int[] c = {n.getX(), n.getY(), n.getZ()};
                boolean fits = true;
                for (int i = 0; i < 3; i++) fits &= Math.max(hi[i], c[i]) - Math.min(lo[i], c[i]) < MAX_SIDE;
                if (!fits) continue;
                for (int i = 0; i < 3; i++) {
                    lo[i] = Math.min(lo[i], c[i]);
                    hi[i] = Math.max(hi[i], c[i]);
                }
                seen.add(n);
                todo.add(n);
            }
        }
        return out;
    }

    public boolean master() { return getBlockState().getBlock() instanceof DoorPanelBlock; }
    public boolean retired() { return master() && !getBlockState().getValue(DoorPanelBlock.MASTER); }

    @Nullable
    public static DoorBlockEntity controllerOf(Level level, BlockPos pos) {
        DoorBlockEntity master = null;
        for (BlockPos p : connected(level, pos)) {
            if (master == null && level.getBlockEntity(p) instanceof DoorBlockEntity be) master = be;
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(p.relative(d)) instanceof DoorBlockEntity be && !be.master()) return be;
            }
        }
        return master;
    }

    @Nullable
    public static DoorBlockEntity of(Level level, BlockPos pos) {
        DoorBlockEntity be = controllerOf(level, pos);
        if (be != null || level.isClientSide) return be;
        level.setBlock(pos, level.getBlockState(pos).setValue(DoorPanelBlock.MASTER, true), Block.UPDATE_ALL);
        return level.getBlockEntity(pos) instanceof DoorBlockEntity made ? made : null;
    }

    public void scan() {
        if (level == null || !closed()) return;
        panels.clear();
        ragged = false;
        for (BlockPos n : master() ? List.of(worldPosition) : Direction.stream().map(worldPosition::relative).toList()) {
            BlockState s = level.getBlockState(n);
            if (s.getBlock() instanceof DoorPanelBlock b && (s.getValue(DoorPanelBlock.PHASE) == Phase.RAW || s.getValue(DoorPanelBlock.PHASE) == Phase.CLOSED)) {
                panels.addAll(connected(level, n));
                axis = s.getValue(DoorPanelBlock.AXIS);
                flip = s.getValue(DoorPanelBlock.FLIP);
                kind = b.kind;
                bounds();
                travel = Math.max(MIN_TRAVEL, b.ticksPerBlock * way());
                ragged = panels.size() != width() * height();
                if (ragged) panels.clear();
                return;
            }
        }
    }

    private void bounds() {
        if (panels.isEmpty()) return;
        BlockPos a = panels.get(0), b = a;
        for (BlockPos p : panels) {
            a = new BlockPos(Math.min(a.getX(), p.getX()), Math.min(a.getY(), p.getY()), Math.min(a.getZ(), p.getZ()));
            b = new BlockPos(Math.max(b.getX(), p.getX()), Math.max(b.getY(), p.getY()), Math.max(b.getZ(), p.getZ()));
        }
        min = a;
        max = b;
    }

    public void adopt() {
        if (level == null || level.isClientSide || !closed()) return;
        List<BlockPos> before = new ArrayList<>(panels);
        scan();
        for (BlockPos p : before) {
            BlockState s = level.getBlockState(p);
            if (!panels.contains(p) && s.getBlock() instanceof DoorPanelBlock) level.setBlock(p, s.setValue(DoorPanelBlock.PHASE, Phase.RAW), Block.UPDATE_ALL);
        }
        phase(Phase.CLOSED);
        if (!before.equals(panels)) sync();
    }

    private void phase(Phase phase) {
        for (BlockPos p : panels) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DoorPanelBlock) level.setBlock(p, s.setValue(DoorPanelBlock.PHASE, phase), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public Component setOpen(boolean want) {
        if (level == null || level.isClientSide || want == open) return null;
        if (want && closed()) {
            if (locked()) return Component.translatable("message.nuclearsq.airlock.locked");
            adopt();
            if (panels.isEmpty()) return Component.translatable(ragged ? "message.nuclearsq.door.ragged" : "message.nuclearsq.door.no_panels");
            phase(Phase.MOVING);
        } else if (!want && progress >= half()) {
            if (occupied()) {
                return Component.translatable("message.nuclearsq.door.blocked");
            }
            phase(Phase.MOVING);
        }
        open = want;
        level.playSound(null, worldPosition, kind.up() ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.8f);
        sync();
        return null;
    }

    public Component toggleFlat() {
        if (level == null || !closed()) return Component.translatable("message.nuclearsq.door.flat_moving");
        flat = !flat;
        sync();
        return Component.translatable(flat ? "message.nuclearsq.door.flat_on" : "message.nuclearsq.door.flat_off");
    }

    @Nullable
    public Component toggle() {
        return setOpen(!open);
    }

    private boolean occupied() {
        return !level.getEntitiesOfClass(LivingEntity.class, AABB.encapsulatingFullBlocks(min, max)).isEmpty();
    }

    private int half() {
        return (travel + 1) / 2;
    }

    public void redstone() {
        boolean signal = level.hasNeighborSignal(worldPosition) || panels.stream().anyMatch(level::hasNeighborSignal);
        if (signal == powered) return;
        powered = signal;
        setOpen(signal);
        setChanged();
    }

    public void tick() {
        clientPrev = progress;
        if (closed() && !level.isClientSide && level.getGameTime() % 20 == 0) {
            if (master() && controllerOf(level, worldPosition) != this) {
                release();
                level.setBlock(worldPosition, getBlockState().setValue(DoorPanelBlock.MASTER, false).setValue(DoorPanelBlock.PHASE, Phase.RAW), Block.UPDATE_ALL);
                level.removeBlockEntity(worldPosition);
                return;
            }
            adopt();
        }
        if (open ? progress >= travel : progress <= 0) return;
        if (!open && progress == 1 && occupied()) return;
        progress += open ? 1 : -1;
        if (!level.isClientSide && open && progress == half() && moving()) phase(Phase.OPEN);
        if (level.isClientSide || moving()) return;
        phase(open ? Phase.OPEN : Phase.CLOSED);
        level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.4f, 0.6f);
        sync();
    }

    public static void lost(Level level, BlockPos pos) {
        Set<DoorBlockEntity> doors = new HashSet<>();
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockEntity(n) instanceof DoorBlockEntity be) doors.add(be);
            DoorBlockEntity c = level.getBlockState(n).getBlock() instanceof DoorPanelBlock ? controllerOf(level, n) : null;
            if (c != null) doors.add(c);
        }
        for (DoorBlockEntity be : doors) {
            if (!be.panels.contains(pos)) continue;
            if (be.closed()) be.adopt();
            else {
                be.release();
                be.sync();
            }
        }
    }

    public void release() {
        if (level == null || level.isClientSide) return;
        phase(Phase.RAW);
        panels.clear();
        open = false;
        progress = 0;
    }

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> (open ? FLAG_OPEN : 0) | (moving() ? FLAG_MOVING : 0) | (ragged ? FLAG_RAGGED : 0) | (panels.isEmpty() ? 0 : FLAG_FOUND) | (locked() ? FLAG_LOCKED : 0);
                    case 1 -> panels.isEmpty() ? 0 : width();
                    case 2 -> panels.isEmpty() ? 0 : height();
                    case 3 -> panels.size();
                    case 5 -> panels.isEmpty() ? 0 : kind.ordinal() + 1;
                    default -> 100 * progress / travel;
                };
            }

            @Override public void set(int i, int v) { }
            @Override public int getCount() { return DATA; }
        };
    }

    public void openTerminal(ServerPlayer sp) {
        adopt();
        DeviceMenu.open(sp, worldPosition, DeviceMenu.DOOR, menuData(), Component.translatable("block.nuclearsq.door_control_sqc"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putLongArray("Panels", panels.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Axis", axis.ordinal());
        tag.putBoolean("Flip", flip);
        tag.putBoolean("Flat", flat);
        tag.putBoolean("Open", open);
        tag.putInt("Kind", kind.ordinal());
        tag.putBoolean("Powered", powered);
        tag.putInt("Progress", progress);
        tag.putInt("Travel", travel);
        if (airlock != null) tag.putLong("Airlock", airlock.asLong());
        if (owner != null) tag.putUUID("owner", owner);
        if (!access.grants().isEmpty()) tag.put("access", access.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        panels.clear();
        for (long l : tag.getLongArray("Panels")) panels.add(BlockPos.of(l));
        axis = Direction.Axis.values()[Math.floorMod(tag.getInt("Axis"), 3)];
        flip = tag.getBoolean("Flip");
        flat = !tag.contains("Flat") || tag.getBoolean("Flat");
        open = tag.getBoolean("Open");
        kind = DoorPanelBlock.Kind.values()[Math.floorMod(tag.getInt("Kind"), DoorPanelBlock.Kind.values().length)];
        powered = tag.getBoolean("Powered");
        travel = Math.max(1, tag.getInt("Travel"));
        progress = Math.max(0, Math.min(travel, tag.getInt("Progress")));
        clientPrev = progress;
        airlock = tag.contains("Airlock") ? BlockPos.of(tag.getLong("Airlock")) : null;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        access.load(tag.getCompound("access"));
        bounds();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        return saveWithoutMetadata(reg);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
