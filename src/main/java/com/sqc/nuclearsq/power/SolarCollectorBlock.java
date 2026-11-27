package com.sqc.nuclearsq.power;

import com.sqc.nuclearsq.registry.PowerRegistry;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SolarCollectorBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(5, 0, 0, 11, 4, 16),
            Block.box(0, 0, 5, 16, 4, 11), Block.box(7, 4, 7, 9, 10, 9), Block.box(0, 10, 0, 16, 12, 16));
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 4);
    public static final int SINGLE = 4;
    public static final IntegerProperty PORT = IntegerProperty.create("port", 0, 3);
    private static final double[][] BIG = {{2, 0, 2, 30, 4, 30}, {5, 0, 0, 11, 4, 32}, {21, 0, 0, 27, 4, 32}, {0, 0, 5, 32, 4, 11},
            {0, 0, 21, 32, 4, 27}, {14, 4, 14, 18, 14, 18}, {0, 14, 0, 32, 16, 32}};
    private static final VoxelShape[] PART_SHAPES = new VoxelShape[4];

    static {
        for (int p = 0; p < 4; p++) {
            VoxelShape s = Shapes.empty();
            int ox = dx(p) * 16, oz = dz(p) * 16;
            for (double[] b : BIG) {
                double x0 = Math.max(b[0] - ox, 0), x1 = Math.min(b[3] - ox, 16), z0 = Math.max(b[2] - oz, 0), z1 = Math.min(b[5] - oz, 16);
                if (x0 < x1 && z0 < z1) s = Shapes.or(s, Block.box(x0, b[1], z0, x1, b[4], z1));
            }
            PART_SHAPES[p] = s;
        }
    }

    public SolarCollectorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PART, SINGLE).setValue(PORT, 0));
    }

    public static int dx(int part) { return part & 1; }

    public static int dz(int part) { return part >> 1; }

    public static boolean outputs(BlockState state, Direction d) {
        int p = state.getValue(PART);
        return p == SINGLE || p == state.getValue(PORT) && d == (dz(p) == 0 ? Direction.NORTH : Direction.SOUTH);
    }

    public static BlockPos master(BlockPos pos, BlockState state) {
        int p = state.getValue(PART);
        return p == SINGLE ? pos : pos.offset(-dx(p), 0, -dz(p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, PORT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Direction f = ctx.getHorizontalDirection(), r = f.getClockWise();
        BlockPos min = new BlockPos(Math.min(pos.getX(), pos.getX() + f.getStepX() + r.getStepX()), pos.getY(),
                Math.min(pos.getZ(), pos.getZ() + f.getStepZ() + r.getStepZ()));
        Level level = ctx.getLevel();
        for (int p = 0; p < 4; p++) {
            BlockPos q = min.offset(dx(p), 0, dz(p));
            if (q.equals(pos)) continue;
            if (!level.getWorldBorder().isWithinBounds(q) || !level.getBlockState(q).canBeReplaced(ctx)) return null;
        }
        int part = (pos.getX() - min.getX()) + 2 * (pos.getZ() - min.getZ());
        return defaultBlockState().setValue(PART, part).setValue(PORT, part);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) == SINGLE) return;
        BlockPos m = master(pos, state);
        for (int p = 0; p < 4; p++) {
            BlockPos q = m.offset(dx(p), 0, dz(p));
            if (!q.equals(pos)) level.setBlock(q, state.setValue(PART, p), 3);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        int p = state.getValue(PART);
        if (p != SINGLE) {
            Direction xd = dx(p) == 0 ? Direction.EAST : Direction.WEST, zd = dz(p) == 0 ? Direction.SOUTH : Direction.NORTH;
            int want = dir == xd ? p ^ 1 : dir == zd ? p ^ 2 : -1;
            if (want >= 0 && !(other.is(this) && other.getValue(PART) == want)) return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        int p = state.getValue(PART);
        if (!level.isClientSide && !player.isCreative() && p != SINGLE && p != 0) {
            BlockPos m = master(pos, state);
            if (level.getBlockState(m).is(this)) level.destroyBlock(m, true, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int p = state.getValue(PART);
        return p == SINGLE ? SHAPE : PART_SHAPES[p];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(master(pos, state)) instanceof SolarCollectorBlockEntity be) {
            int d = be.distance();
            Component route = d >= SolarCollectorBlockEntity.NO_ROUTE ? Component.translatable("message.nuclearsq.solar_collector.no_route")
                    : d == 0 ? Component.translatable("message.nuclearsq.solar_collector.direct")
                    : Component.translatable("message.nuclearsq.solar_collector.route", d);
            player.displayClientMessage(Component.translatable("message.nuclearsq.solar_collector.status", be.rate(), be.peak(), be.delivered(), route, be.stored(), be.capacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.nuclearsq.solar_collector_sqc.tip", SolarCollectorBlockEntity.peakPerTick())
                .withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarCollectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != PowerRegistry.SOLAR_COLLECTOR_BE.get()) return null;
        return (lvl, pos, st, be) -> ((SolarCollectorBlockEntity) be).serverTick();
    }
}
