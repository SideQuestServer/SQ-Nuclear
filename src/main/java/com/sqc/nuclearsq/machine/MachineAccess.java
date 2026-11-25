package com.sqc.nuclearsq.machine;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.compat.ftbteams.FtbTeamsCompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class MachineAccess {
    public static final int USE = 1, ADMIN = 7;

    public interface Holder {
        MachineAccess access();

        @Nullable
        UUID owner();

        void setChanged();
    }

    private final Map<String, Integer> grants = new TreeMap<>();

    public Map<String, Integer> grants() {
        return grants;
    }

    public void set(String key, int level) {
        grants.put(key, level);
    }

    public boolean remove(String key) {
        return grants.remove(key) != null;
    }

    public int rights(ServerPlayer sp, @Nullable UUID owner) {
        if (owner == null || owner.equals(sp.getUUID()) || sp.hasPermissions(2)) return ADMIN;
        int r = grants.getOrDefault("p:" + sp.getGameProfile().getName().toLowerCase(java.util.Locale.ROOT), 0);
        TeamData teams = TeamData.get(sp.server);
        for (Map.Entry<String, Integer> e : grants.entrySet())
            if (e.getKey().startsWith("g:") && teams.inTeam(e.getKey().substring(2), sp.getUUID())) r |= e.getValue();
        if ((r & USE) == 0 && FtbTeamsCompat.sameTeam(owner, sp.getUUID())) r |= USE;
        return r;
    }

    public boolean can(ServerPlayer sp, @Nullable UUID owner, int level) {
        return (rights(sp, owner) & level) == level;
    }

    public boolean canBuild(ServerPlayer sp, @Nullable UUID owner) {
        return can(sp, owner, ADMIN) || owner != null && (FtbTeamsCompat.sameTeam(owner, sp.getUUID())
                || grants.keySet().stream().anyMatch(k -> k.startsWith("g:") && TeamData.get(sp.server).inTeam(k.substring(2), sp.getUUID())));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        grants.forEach(t::putInt);
        return t;
    }

    public void load(CompoundTag t) {
        grants.clear();
        for (String k : t.getAllKeys()) grants.put(k, t.getInt(k) >= ADMIN ? ADMIN : USE);
    }
}
