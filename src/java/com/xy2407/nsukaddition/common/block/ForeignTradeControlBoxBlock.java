package com.xy2407.nsukaddition.common.block;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConstants;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenRequestPacket;
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

/** 外贸控制方块，右键打开外贸管理界面。 */
@SuppressWarnings("null")
public final class ForeignTradeControlBoxBlock extends Block {
    public ForeignTradeControlBoxBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.METAL));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            ForeignTradeControlBoxOpenRequestPacket.openFor(serverLevel, serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
