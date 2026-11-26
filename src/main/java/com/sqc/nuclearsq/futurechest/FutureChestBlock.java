package com.sqc.nuclearsq.futurechest;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FutureChestBlock extends BaseEntityBlock {
    public static final MapCodec<FutureChestBlock> CODEC = simpleCodec(FutureChestBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty UNFOLDED = BooleanProperty.create("unfolded");

    public FutureChestBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(UNFOLDED, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, UNFOLDED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState st, @Nullable LivingEntity by, ItemStack stack) {
        if (level.isClientSide || st.getValue(UNFOLDED)) return;
        Vec3 start = by != null ? by.getEyePosition().add(by.getLookAngle().scale(0.4)).subtract(0, 0.25, 0) : Vec3.atCenterOf(pos).add(0, 1.5, 0);
        if (level.getBlockEntity(pos) instanceof FutureChestBlockEntity be) be.startAnim(start);
        level.scheduleTick(pos, this, FutureChestBlockEntity.ANIM_TICKS);
        level.playSound(null, pos, SoundEvents.SNOWBALL_THROW, SoundSource.BLOCKS, 0.8f, 0.8f);
    }

    @Override
    protected void tick(BlockState st, ServerLevel level, BlockPos pos, RandomSource rnd) {
        finish(st, level, pos);
    }

    private void finish(BlockState st, Level level, BlockPos pos) {
        if (st.getValue(UNFOLDED)) return;
        level.setBlock(pos, st.setValue(UNFOLDED, true), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof FutureChestBlockEntity be) be.finishAnim();
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.2f);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!st.getValue(UNFOLDED)) {
            if (level.isClientSide || !(level.getBlockEntity(pos) instanceof FutureChestBlockEntity be)
                    || (be.animStart() != FutureChestBlockEntity.NONE && level.getGameTime() - be.animStart() <= FutureChestBlockEntity.ANIM_TICKS + 20)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            finish(st, level, pos);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof FutureChestBlockEntity be) {
                giveToPlayer(level, pos, be, player);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FutureChestBlockEntity be) {
            if (level.random.nextInt(100) == 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.nuclearsq.future_chest.cat"), true);
                level.playSound(null, pos, SoundEvents.CAT_AMBIENT, SoundSource.BLOCKS, 0.8f, 1.0f);
            }
            player.openMenu(be, buf -> buf.writeVarInt(be.capacity()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private ItemStack withContents(FutureChestBlockEntity be) {
        ItemStack drop = new ItemStack(this);
        drop.applyComponents(be.collectComponents());
        return drop;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState st, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FutureChestBlockEntity be && (!player.isCreative() || !be.isEmpty())) {
            giveToPlayer(level, pos, be, player);
        }
        return super.playerWillDestroy(level, pos, st, player);
    }

    private void giveToPlayer(Level level, BlockPos pos, FutureChestBlockEntity be, Player player) {
        ItemStack drop = withContents(be);
        ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, drop.copy());
        e.setNoPickUpDelay();
        boolean fits = player.getInventory().add(drop);
        if (fits) {
            level.addFreshEntity(e);
            player.take(e, 1);
            e.discard();
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 1.4f);
        } else {
            level.addFreshEntity(e);
        }
        be.clearContent();
        be.taken = true;
    }

    @Override
    protected java.util.List<ItemStack> getDrops(BlockState st, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        BlockEntity be = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof FutureChestBlockEntity c) return c.taken ? java.util.List.of() : java.util.List.of(withContents(c));
        return java.util.List.of(new ItemStack(this));
    }

    @Override
    protected VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState st) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState st) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState st, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FutureChestBlockEntity be ? AbstractContainerMenu.getRedstoneSignalFromContainer(be) : 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState st,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide || type != FutureChestRegistry.BLOCK_ENTITY.get() ? null : (l, p, s, be) -> ((FutureChestBlockEntity) be).serverTick();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new FutureChestBlockEntity(pos, st);
    }
}
