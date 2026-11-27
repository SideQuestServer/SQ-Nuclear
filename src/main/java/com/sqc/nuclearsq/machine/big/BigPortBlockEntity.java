package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.registry.BigMachineRegistry;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BigPortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;
    private String portName = "";
    private int signal;

    public BigPortBlockEntity(BlockPos pos, BlockState state) {
        super(BigMachineRegistry.PORT_BE.get(), pos, state);
    }

    public BigPortKind kind() {
        return ((BigPortBlock) getBlockState().getBlock()).kind();
    }

    public String portName() {
        return portName;
    }

    public int signal() {
        return signal;
    }

    public void link(@Nullable BlockPos controller, String name) {
        BlockPos next = controller == null ? null : controller.immutable();
        String n = controller == null ? "" : name;
        if (Objects.equals(controllerPos, next) && portName.equals(n)) return;
        controllerPos = next;
        portName = n;
        if (controller == null) setSignal(0);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    public void setSignal(int value) {
        if (value == signal) return;
        signal = value;
        setChanged();
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Nullable
    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Nullable
    public BigMachineBlockEntity findController() {
        if (level == null || controllerPos == null) return null;
        if (!com.sqc.nuclearsq.data.ChunkGuard.loaded(level, controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof BigMachineBlockEntity c && c.isFormed()) return c;
        return null;
    }

    @Nullable
    public com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity findTurbine() {
        if (level == null || controllerPos == null) return null;
        if (!com.sqc.nuclearsq.data.ChunkGuard.loaded(level, controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity t && t.isFormed() ? t : null;
    }

    public void sendInfo(Player player) {
        String k = "message.nuclearsq.big_port.";
        BigMachineBlockEntity c = findController();
        BigMachine.Port p = c == null ? null : c.machine().port(portName);
        Component title = Component.translatable("block.nuclearsq." + kind().blockId());
        if (p == null) {
            player.sendSystemMessage(Component.translatable(k + "title", title, Component.translatable(k + "loose")));
            player.sendSystemMessage(Component.translatable(k + "not_formed"));
            return;
        }
        String dir = c.battery() != null && p.kind() == BigPortKind.ENERGY ? (c.battery().isOutput(p) ? "out" : "in") : p.dir();
        player.sendSystemMessage(Component.translatable(k + "title", title, Component.translatable(k + "dir." + dir)));
        player.sendSystemMessage(Component.translatable(k + "of", Component.translatable(c.getBlockState().getBlock().getDescriptionId()),
                Component.translatable("port.nuclearsq." + c.machine().id + "." + p.name())));
        player.sendSystemMessage(c.portLine(p));
        Component to = targets(c.machine(), p);
        if (to != null) player.sendSystemMessage(to);
    }

    private static final int MAX_TARGETS = 6;

    @Nullable
    static Component targets(BigMachine self, BigMachine.Port p) {
        if (!p.kind().isFluid()) return null;
        String stem = p.name().replaceAll("_(in|out)(_\\d+)?$", "");
        java.util.List<Component> names = new java.util.ArrayList<>();
        for (BigMachine m : BigMachine.values()) {
            if (m == self) continue;
            for (BigMachine.Port q : m.ports) {
                if (q.kind() == p.kind() && q.name().replaceAll("_(in|out)(_\\d+)?$", "").equals(stem)
                        && (p.in() && q.out() || p.out() && q.in())) {
                    names.add(Component.translatable("block.nuclearsq." + m.controllerId()));
                    break;
                }
            }
        }
        if (names.isEmpty()) return null;
        net.minecraft.network.chat.MutableComponent list = Component.empty();
        for (int i = 0; i < Math.min(names.size(), MAX_TARGETS); i++) list = list.append(i == 0 ? names.get(i) : Component.literal(", ").append(names.get(i)));
        int more = names.size() - MAX_TARGETS;
        return Component.translatable("message.nuclearsq.big_port." + (p.in() && !p.out() ? "from" : p.out() && !p.in() ? "to" : "with"), list, more > 0 ? Component.translatable("message.nuclearsq.big_port.more", more) : Component.empty());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
        tag.putString("port", portName);
        tag.putInt("signal", signal);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
        portName = tag.getString("port");
        signal = tag.getInt("signal");
    }
}
