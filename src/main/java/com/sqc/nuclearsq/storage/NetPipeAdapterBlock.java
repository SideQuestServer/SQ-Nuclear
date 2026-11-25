package com.sqc.nuclearsq.storage;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.data.DataDevice;
import com.sqc.nuclearsq.transport.WrenchItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class NetPipeAdapterBlock extends Block implements EntityBlock, DataDevice {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty PANEL = DirectionProperty.create("panel", Direction.Plane.HORIZONTAL);
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);

    public enum Mode implements StringRepresentable {
        DEMAND("demand"), STORE("store"), EXPORT("export");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Status implements StringRepresentable {
        OFF("off"), OK("ok"), WAIT("wait"), ERR("err");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public NetPipeAdapterBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PANEL, Direction.EAST)
                .setValue(MODE, Mode.DEMAND).setValue(STATUS, Status.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PANEL, MODE, STATUS);
    }

    public static Direction back(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    static Direction panelFor(Direction facing, Direction wanted) {
        if (facing.getAxis().isVertical()) return wanted.getAxis().isHorizontal() ? wanted : Direction.NORTH;
        return wanted.getAxis() != facing.getAxis() && wanted.getAxis().isHorizontal() ? wanted : facing.getClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace().getOpposite();
        Direction panel;
        if (facing.getAxis().isVertical()) {
            panel = ctx.getHorizontalDirection().getOpposite();
        } else {
            Direction cw = facing.getClockWise();
            Player p = ctx.getPlayer();
            BlockPos pos = ctx.getClickedPos();
            double dot = p == null ? 1 : (p.getX() - pos.getX() - 0.5) * cw.getStepX() + (p.getZ() - pos.getZ() - 0.5) * cw.getStepZ();
            panel = dot >= 0 ? cw : cw.getOpposite();
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PANEL, panel);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING))).setValue(PANEL, rot.rotate(state.getValue(PANEL)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(PANEL, mirror.mirror(state.getValue(PANEL)));
    }

    public static BlockState turned(BlockState state) {
        Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
        Direction f = state.getValue(FACING);
        int i = 0;
        while (order[i] != f) i++;
        Direction next = order[(i + 1) % order.length];
        return state.setValue(FACING, next).setValue(PANEL, panelFor(next, state.getValue(PANEL)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetPipeAdapterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != StorageRegistry.NET_PIPE_ADAPTER_BE.get()) return null;
        return (l, p, s, be) -> ((NetPipeAdapterBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem) || player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!WrenchItem.mayUse(level, pos, player)) return ItemInteractionResult.FAIL;
        level.setBlockAndUpdate(pos, turned(state));
        level.invalidateCapabilities(pos);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof NetPipeAdapterBlockEntity be) {
            NetPipeAdapterMenu.open(sp, be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
