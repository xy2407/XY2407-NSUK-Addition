package com.xy2407.nsukaddition.common.block;

import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxOpenRequestPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** 餐厅控制箱方块，处理交互打开界面及移除时的数据清理。 */
@SuppressWarnings("null")
public final class RestaurantControlBoxBlock extends Block {
    public RestaurantControlBoxBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.METAL));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            RestaurantControlBoxOpenRequestPacket.openFor(serverLevel, serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            RestaurantControlBoxService.onRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
