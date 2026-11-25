package com.sqc.nuclearsq.door;

import java.util.Locale;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.transport.WrenchItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class DoorPanelBlock extends Block implements EntityBlock {
    public enum Phase implements StringRepresentable {
        RAW, CLOSED, MOVING, OPEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<Phase> PHASE = EnumProperty.create("phase", Phase.class);
    public static final BooleanProperty MASTER = BooleanProperty.create("master");
    public static final BooleanProperty FLIP = BooleanProperty.create("flip");
    private static final VoxelShape X = box(5, 0, 0, 11, 16, 16), Y = box(0, 5, 0, 16, 11, 16), Z = box(0, 0, 5, 16, 16, 11);

    public enum Kind {
        HANGAR, AIRLOCK, VAULT, SECTIONAL, GRILLE, FOLDING, LAB, GLASS, SHIELD, COLD, CAGE, SLIDE, INDUSTRIAL, PERFORATED, SPEED, LIFT;

        public boolean up() { return this == HANGAR || this == SECTIONAL || this == GRILLE || this == INDUSTRIAL || this == PERFORATED || this == SPEED || this == LIFT; }
    }

    public final Kind kind;
    public final int ticksPerBlock;

    public DoorPanelBlock(Properties props, Kind kind, int ticksPerBlock) {
        super(props);
        this.kind = kind;
        this.ticksPerBlock = ticksPerBlock;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(PHASE, Phase.RAW).setValue(MASTER, false).setValue(FLIP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(AXIS, PHASE, MASTER, FLIP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState on = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()));
        if (on.is(this)) return defaultBlockState().setValue(AXIS, on.getValue(AXIS)).setValue(FLIP, on.getValue(FLIP));
        Direction look = ctx.getNearestLookingDirection();
        return defaultBlockState().setValue(AXIS, look.getAxis()).setValue(FLIP, look.getAxisDirection() == Direction.AxisDirection.POSITIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis normal = state.getValue(AXIS);
        if (state.getValue(PHASE) != Phase.OPEN || !kind.up()) return normal == Direction.Axis.X ? X : normal == Direction.Axis.Y ? Y : Z;
        Direction.Axis tall = normal == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
        return edge(level, pos, state, tall, 1) ? strip(normal, tall, 10, 16) : Shapes.empty();
    }

    private boolean edge(BlockGetter level, BlockPos pos, BlockState state, Direction.Axis axis, int step) {
        BlockState next = level.getBlockState(pos.relative(axis, step));
        return !next.is(this) || next.getValue(AXIS) != state.getValue(AXIS);
    }

    private static VoxelShape strip(Direction.Axis normal, Direction.Axis along, double a0, double a1) {
        double[] lo = {0, 0, 0}, hi = {16, 16, 16};
        lo[normal.ordinal()] = 5;
        hi[normal.ordinal()] = 11;
        lo[along.ordinal()] = a0;
        hi[along.ordinal()] = a1;
        return box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(PHASE) == Phase.OPEN || state.getValue(PHASE) == Phase.MOVING ? Shapes.empty() : getShape(state, level, pos, ctx);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MASTER) ? new DoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == DoorRegistry.CONTROLLER_BE.get() ? (l, p, s, be) -> ((DoorBlockEntity) be).tick() : null;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PHASE) == Phase.RAW ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.AIRLOCK && stack.getItem() instanceof WrenchItem) {
            if (!level.isClientSide) {
                DoorBlockEntity door = state.getValue(PHASE) == Phase.RAW ? null : DoorBlockEntity.controllerOf(level, pos);
                if (door == null) player.displayClientMessage(Component.translatable("message.nuclearsq.door.no_controller"), true);
                else if (door.mayUse(player)) player.displayClientMessage(door.toggleFlat(), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return stack.getItem() instanceof BlockItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            DoorBlockEntity door = state.getValue(PHASE) == Phase.RAW ? null : DoorBlockEntity.controllerOf(level, pos);
            if (door == null || door.mayUse(player)) {
                Component err = door == null ? Component.translatable("message.nuclearsq.door.no_controller") : door.toggle();
                if (err != null) player.displayClientMessage(err, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void leftClick(PlayerInteractEvent.LeftClickBlock e) {
        Level level = e.getLevel();
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !e.getEntity().isShiftKeyDown()
                || !(level.getBlockState(e.getPos()).getBlock() instanceof DoorPanelBlock)) return;
        e.setCanceled(true);
        DoorBlockEntity door = level.isClientSide ? null : DoorBlockEntity.of(level, e.getPos());
        if (door == null || !door.mayUse(e.getEntity())) return;
        door.claim(e.getEntity());
        door.adopt();
        if (door.panels().isEmpty()) e.getEntity().displayClientMessage(Component.translatable("message.nuclearsq.door.ragged"), true);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide || neighborBlock instanceof DoorPanelBlock) return;
        DoorBlockEntity door = level.hasNeighborSignal(pos) ? DoorBlockEntity.of(level, pos) : DoorBlockEntity.controllerOf(level, pos);
        if (door != null) {
            door.adopt();
            door.redstone();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DoorBlockEntity be) be.release();
            else if (!level.isClientSide) DoorBlockEntity.lost(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
