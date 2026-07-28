package com.xy2407.nsukaddition.common.block;

import com.xy2407.nsukaddition.common.colony.ColonyCreateService;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenRequestPacket;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 附属领地核心方块，右键打开管理界面，已绑定附属地的核心受保护不可破坏。 */
@SuppressWarnings("null")
public final class ColonyCoreBlock extends Block {

    public ColonyCoreBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F)
                .explosionResistance(3600000.0F).sound(SoundType.METAL));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && params.getLevel() instanceof ServerLevel serverLevel) {
            String dimId = serverLevel.dimension().location().toString();
            if (ColonySqliteStorage.loadColonyByCorePos(serverLevel, dimId, BlockPos.containing(origin).asLong()) != null) {
                return List.of();
            }
        }
        return super.getDrops(state, params);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            ColonyCoreOpenRequestPacket.openFor(serverLevel, serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!(level instanceof ServerLevel serverLevel) || newState.is(state.getBlock())) {
            return;
        }
        String dimId = serverLevel.dimension().location().toString();
        ColonyData colony = ColonySqliteStorage.loadColonyByCorePos(serverLevel, dimId, pos.asLong());
        if (colony == null) {
            return;
        }
        level.setBlock(pos, ModBlocks.COLONY_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.players().forEach(player -> {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) < 100.0D) {
                if (player instanceof ServerPlayer serverPlayer) {
                    InfoToastService.warning(serverPlayer, Component.translatable(
                            "message.xy2407_nsuk_addition.colony.core_protected"));
                }
            }
        });
    }
}
