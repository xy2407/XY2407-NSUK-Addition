package com.xy2407.nsukaddition.common.block;

import com.xy2407.nsukaddition.common.block.entity.DynamicRoeBlockEntity;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

/** 动态鱼卵方块，通过 BlockEntity 存储父鱼类型，孵化时生成对应鱼类实体。 */
public class DynamicRoeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.5D, 16.0D);

    public DynamicRoeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    @Override
    protected MapCodec<? extends DynamicRoeBlock> codec() {
        return simpleCodec(DynamicRoeBlock::new);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level, pos.below());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, Boolean.valueOf(fluidstate.getType() == Fluids.WATER));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, getHatchDelay(level.getRandom()));
        }
    }

    private int getHatchDelay(RandomSource random) {
        return 200;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DynamicRoeBlockEntity roeEntity) {
            String fishTypeName = roeEntity.getFishType();
            level.destroyBlock(pos, false);
            if (fishTypeName != null) {
                level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);
                spawnFish(level, pos, random, fishTypeName);
            }
        } else {
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.getType().equals(EntityType.FALLING_BLOCK)) {
            level.destroyBlock(pos, false);
        }
    }

    private static boolean mayPlaceOn(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    private void spawnFish(ServerLevel level, BlockPos pos, RandomSource random, String fishTypeName) {
        ResourceLocation fishTypeLoc = ResourceLocation.parse(fishTypeName);

        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(fishTypeLoc)) return;
        EntityType<?> fishType = BuiltInRegistries.ENTITY_TYPE.get(fishTypeLoc);

        int count = random.nextInt(1, 3);
        for (int i = 0; i < count; i++) {
            Entity entity = fishType.create(level);
            if (entity instanceof Mob mob) {
                double offsetX = random.nextDouble() * 0.6D - 0.3D;
                double offsetZ = random.nextDouble() * 0.6D - 0.3D;
                int yaw = random.nextInt(1, 361);
                mob.moveTo(
                    pos.getX() + 0.5D + offsetX,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D + offsetZ,
                    yaw, 0.0F
                );
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DynamicRoeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
