package dev.vestige.waylight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Waylight (GDD §6.6): a lantern on a stone plinth. Lighting it is a discovery; once lit it is a
 * rest and travel point. Placed by level design (worldgen arrives in Milestone 6), never crafted,
 * and unbreakable outside creative.
 */
public class WaylightBlock extends BaseEntityBlock {
    public static final MapCodec<WaylightBlock> CODEC = simpleCodec(WaylightBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 3, 13, 5, 13), Block.box(7, 5, 7, 9, 14, 9), Block.box(4.5, 14, 4.5, 11.5, 23, 11.5));
    private static final VoxelShape COLLISION = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 3, 13, 5, 13),
            Block.box(6.5, 5, 6.5, 9.5, 23, 9.5));

    public WaylightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return COLLISION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaylightBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaylightBlockEntity be && stack.has(
                net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            be.setName(stack.getHoverName().getString());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            WaylightService.use(sp, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getServer() != null) {
            WaylightRegistry.get(level.getServer()).remove(net.minecraft.core.GlobalPos.of(level.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.15, z = pos.getZ() + 0.5;
        if (state.getValue(LIT)) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.15, y, z + (random.nextDouble() - 0.5) * 0.15, 0, 0.01, 0);
            }
            if (random.nextInt(10) == 0) {
                level.addParticle(ParticleTypes.WAX_OFF, x + (random.nextDouble() - 0.5) * 0.8, y + random.nextDouble() * 0.6,
                        z + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
            }
        } else if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0, 0.01, 0);
        }
    }
}
