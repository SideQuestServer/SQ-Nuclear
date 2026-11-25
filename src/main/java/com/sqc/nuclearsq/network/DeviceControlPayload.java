package com.sqc.nuclearsq.network;

import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeviceControlPayload(int action, int value) implements CustomPacketPayload {
    public static final int SCRAM = 1;
    public static final int ROD_TARGET = 4, ROD_MODE = 5, ROD_CHANNEL = 6;
    public static final int ROD_LOCK = 7;
    public static final int BIG_ENABLE = 8, BIG_MANUAL = 9, BIG_DUMP = 10;
    public static final int RACK_TOGGLE = 11;
    public static final int BATTERY_PORT = 12, BATTERY_MODE = 13, BATTERY_PORT_DIR = 14;
    public static final int BIG_REPAIR = 15;
    public static final int BIG_BURN = 16;
    public static final int BATTERY_THRESH = 17;
    public static final int GATE_SET = 18;
    public static final int DOOR_OPEN = 19;
    public static final int AIRLOCK_DOOR = 20;
    public static final int RECIPE_LOCK = 21;
    public static final int RECIPE_FILL = 22;

    public static final Type<DeviceControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "device_control"));
    public static final StreamCodec<ByteBuf, DeviceControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DeviceControlPayload::action,
            ByteBufCodecs.VAR_INT, DeviceControlPayload::value,
            DeviceControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
