package com.xy2407.nsukaddition.common.fluid;

import com.xy2407.nsukaddition.common.registry.ModMilkFluids;
import com.xy2407.nsukaddition.common.registry.ModMilkFluids.MilkType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** 木奶桶右键放置对应奶流体，返还木空桶（按物品 ID 匹配，无 Meadow 依赖）。 */
public final class WoodenMilkBucketPlacementService {
    private static final ResourceLocation WOODEN_BUCKET_ID = ResourceLocation.fromNamespaceAndPath("meadow", "wooden_bucket");

    private WoodenMilkBucketPlacementService() {
    }

    public static InteractionResultHolder<ItemStack> tryPourWoodenMilk(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        MilkType type = ModMilkFluids.milkTypeByBucket(itemStack);
        if (type == null) {
            return null;
        }
        Fluid sourceFluid = ModMilkFluids.ENTRIES.get(type).source().get();
        if (!(sourceFluid instanceof FlowingFluid flowing)) {
            return null;
        }
        BlockHitResult hit = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        }
        BlockPos clickedPos = hit.getBlockPos();
        BlockPos targetPos = targetPos(level, player, clickedPos, hit, flowing);
        if (!level.mayInteract(player, clickedPos) || !player.mayUseItemAt(targetPos, hit.getDirection(), itemStack)) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (!emptyMilkContents(player, level, targetPos, hit, flowing)) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, targetPos, itemStack);
        }
        player.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
        ItemStack resultStack = new ItemStack(BuiltInRegistries.ITEM.get(WOODEN_BUCKET_ID));
        return InteractionResultHolder.sidedSuccess(resultStack, level.isClientSide());
    }

    private static BlockPos targetPos(Level level, Player player, BlockPos clickedPos, BlockHitResult hit, FlowingFluid fluid) {
        BlockState clickedState = level.getBlockState(clickedPos);
        return canBlockContainFluid(player, level, clickedPos, clickedState, fluid)
                ? clickedPos : clickedPos.relative(hit.getDirection());
    }

    private static boolean emptyMilkContents(Player player, Level level, BlockPos pos, BlockHitResult hit, FlowingFluid fluid) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        boolean replaceable = state.canBeReplaced(fluid);
        boolean sameFluidNonSource = state.getFluidState().getType().isSame(fluid) && !state.getFluidState().isSource();
        boolean canPlace = state.isAir()
                || replaceable
                || sameFluidNonSource
                || block instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, state, fluid);
        if (!canPlace) {
            return hit != null && emptyMilkContents(player, level, hit.getBlockPos().relative(hit.getDirection()), null, fluid);
        }
        if (block instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, state, fluid)) {
            container.placeLiquid(level, pos, state, fluid.getSource(false));
            playEmptySound(player, level, pos);
            return true;
        }
        if (!level.isClientSide && replaceable && state.getFluidState().isEmpty()) {
            level.destroyBlock(pos, true);
        }
        if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11) && !state.getFluidState().isSource()) {
            return false;
        }
        playEmptySound(player, level, pos);
        return true;
    }

    private static boolean canBlockContainFluid(Player player, Level level, BlockPos pos, BlockState state, FlowingFluid fluid) {
        return state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, level, pos, state, fluid);
    }

    private static void playEmptySound(Player player, Level level, BlockPos pos) {
        level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }
}