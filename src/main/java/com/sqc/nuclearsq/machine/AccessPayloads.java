package com.sqc.nuclearsq.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AccessPayloads {
    private AccessPayloads() {}

    public static final int OPEN = 0, SET = 1, REMOVE = 2;

    public record Action(BlockPos pos, int op, int kind, String name, int level) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "access_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos, ByteBufCodecs.VAR_INT, Action::op, ByteBufCodecs.VAR_INT, Action::kind,
                ByteBufCodecs.STRING_UTF8, Action::name, ByteBufCodecs.VAR_INT, Action::level, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(BlockPos pos, String owner, boolean admin, List<String> keys, List<Integer> levels) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "access_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, State::pos, ByteBufCodecs.STRING_UTF8, State::owner, ByteBufCodecs.BOOL, State::admin,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), State::keys,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), State::levels, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handle(Action a, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !sp.level().isLoaded(a.pos())
                    || sp.distanceToSqr(a.pos().getX() + 0.5, a.pos().getY() + 0.5, a.pos().getZ() + 0.5) > 64
                    || !(sp.level().getBlockEntity(a.pos()) instanceof MachineAccess.Holder be)) return;
            MachineAccess acc = be.access();
            if (!acc.can(sp, be.owner(), MachineAccess.USE)) return;
            boolean admin = acc.can(sp, be.owner(), MachineAccess.ADMIN);
            String name = a.name().trim().toLowerCase(Locale.ROOT);
            if (admin && a.op() != OPEN && !name.isEmpty() && name.length() <= 32) {
                String key = (a.kind() == 1 ? "g:" : "p:") + name;
                if (a.op() == SET && a.kind() == 1 && TeamData.get(sp.server).team(name) == null) return;
                if (a.op() == SET) acc.set(key, a.level() >= MachineAccess.ADMIN ? MachineAccess.ADMIN : MachineAccess.USE);
                else acc.remove(key);
                be.setChanged();
            }
            String owner = be.owner() == null ? "-" : sp.server.getProfileCache().get(be.owner()).map(p -> p.getName()).orElse(be.owner().toString());
            List<String> keys = new ArrayList<>();
            List<Integer> levels = new ArrayList<>();
            for (Map.Entry<String, Integer> e : acc.grants().entrySet()) {
                keys.add(e.getKey());
                levels.add(e.getValue());
            }
            PacketDistributor.sendToPlayer(sp, new State(a.pos(), owner, admin, keys, levels));
        });
    }
}
