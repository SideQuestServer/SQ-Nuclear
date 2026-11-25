package com.sqc.nuclearsq.door;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.DeviceMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AirlockBlockEntity extends BlockEntity {
    public static final int RANGE = 12, PURGE = 40;
    public static final int DATA = 3, FLAG_LINK1 = 1, FLAG_OPEN1 = 2, FLAG_LINK2 = 4, FLAG_OPEN2 = 8;

    private final BlockPos[] doors = new BlockPos[2];
    private int want = -1, purge;
    private boolean powered;

    public AirlockBlockEntity(BlockPos pos, BlockState state) {
        super(DoorRegistry.AIRLOCK_BE.get(), pos, state);
    }

    @Nullable
    public DoorBlockEntity door(int i) {
        return doors[i] != null && level != null && level.isLoaded(doors[i]) && level.getBlockEntity(doors[i]) instanceof DoorBlockEntity d
                && worldPosition.equals(d.airlock()) ? d : null;
    }

    public boolean mayUse(Player player) {
        for (int i = 0; i < 2; i++) {
            DoorBlockEntity d = door(i);
            if (d != null && !d.mayUse(player)) return false;
        }
        return true;
    }

    public boolean busy() { return want >= 0; }
    public int openDoor() {
        for (int i = 0; i < 2; i++) {
            DoorBlockEntity d = door(i);
            if (d != null && !d.closed()) return i + 1;
        }
        return 0;
    }

    public void link() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < 2; i++) if (door(i) == null) doors[i] = null;
        if (doors[0] != null && doors[1] != null) return;
        List<DoorBlockEntity> free = new ArrayList<>();
        BlockPos p = worldPosition;
        for (int cx = p.getX() - RANGE >> 4; cx <= p.getX() + RANGE >> 4; cx++) {
            for (int cz = p.getZ() - RANGE >> 4; cz <= p.getZ() + RANGE >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos q = be.getBlockPos();
                    if (be instanceof DoorBlockEntity d && Math.abs(q.getX() - p.getX()) <= RANGE && Math.abs(q.getY() - p.getY()) <= RANGE
                            && Math.abs(q.getZ() - p.getZ()) <= RANGE
                            && (d.airlock() == null || !(level.getBlockEntity(d.airlock()) instanceof AirlockBlockEntity))) free.add(d);
                }
            }
        }
        free.sort(Comparator.comparingDouble(d -> d.getBlockPos().distSqr(p)));
        for (DoorBlockEntity d : free) {
            int slot = doors[0] == null ? 0 : doors[1] == null ? 1 : -1;
            if (slot < 0) break;
            doors[slot] = d.getBlockPos();
            d.setAirlock(p);
        }
        setChanged();
    }

    public boolean mayOpen(DoorBlockEntity d) {
        for (int i = 0; i < 2; i++) {
            DoorBlockEntity o = door(i);
            if (o != null && o != d && !o.closed()) return false;
        }
        return true;
    }

    public void request(int i) {
        if (level == null || level.isClientSide) return;
        link();
        want = Math.min(i, 1);
        purge = 0;
        if (i < 0) {
            for (int k = 0; k < 2; k++) if (door(k) != null) door(k).setOpen(false);
        }
        setChanged();
    }

    public void cycle() {
        request(openDoor() == 1 ? 1 : 0);
    }

    public void redstone(boolean signal) {
        if (signal && !powered) cycle();
        powered = signal;
    }

    public void serverTick() {
        if (level.getGameTime() % 40 == 0) link();
        if (want < 0) return;
        DoorBlockEntity mine = door(want), other = door(1 - want);
        if (mine == null) {
            want = -1;
        } else if (other != null && !other.closed()) {
            if (other.open()) other.setOpen(false);
            purge = PURGE;
        } else if (purge > 0) {
            if (purge-- == PURGE && level instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.6f);
                sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, 12, 0.4, 0.2, 0.4, 0.01);
            }
        } else {
            mine.setOpen(true);
            want = -1;
            setChanged();
        }
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                if (i == 1) return purge;
                if (i == 2) return want + 1;
                DoorBlockEntity a = door(0), b = door(1);
                return (a != null ? FLAG_LINK1 : 0) | (a != null && !a.closed() ? FLAG_OPEN1 : 0)
                        | (b != null ? FLAG_LINK2 : 0) | (b != null && !b.closed() ? FLAG_OPEN2 : 0);
            }

            @Override public void set(int i, int v) { }
            @Override public int getCount() { return DATA; }
        };
    }

    public void openTerminal(ServerPlayer sp) {
        link();
        DeviceMenu.open(sp, worldPosition, DeviceMenu.AIRLOCK, menuData(), Component.translatable("block.nuclearsq.airlock_control_sqc"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        for (int i = 0; i < 2; i++) if (doors[i] != null) tag.putLong("Door" + i, doors[i].asLong());
        tag.putInt("Want", want);
        tag.putInt("Purge", purge);
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        for (int i = 0; i < 2; i++) doors[i] = tag.contains("Door" + i) ? BlockPos.of(tag.getLong("Door" + i)) : null;
        want = tag.contains("Want") ? Math.max(-1, Math.min(1, tag.getInt("Want"))) : -1;
        purge = Math.max(0, Math.min(PURGE, tag.getInt("Purge")));
        powered = tag.getBoolean("Powered");
    }
}
