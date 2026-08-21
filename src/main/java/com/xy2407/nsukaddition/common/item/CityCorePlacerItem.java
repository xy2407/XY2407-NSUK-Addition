package com.xy2407.nsukaddition.common.item;

import com.xy2407.nsukaddition.common.citycore.CityCorePlacementService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** 城市核心建造图纸：手持时投影建筑，右键在投影位置放置建筑并消耗。 */
public class CityCorePlacerItem extends Item {

    public CityCorePlacerItem() {
        super(new Item.Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player != null ? player.getItemInHand(hand) : ItemStack.EMPTY;
        if (!level.isClientSide && player != null) {
            CityCorePlacementService.place((ServerLevel) level, player, stack);
            consumeOne(player, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            ItemStack stack = player.getItemInHand(context.getHand());
            CityCorePlacementService.place((ServerLevel) context.getLevel(), player, stack);
            consumeOne(player, context.getHand());
        }
        return InteractionResult.SUCCESS;
    }

    private static void consumeOne(Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            stack.shrink(1);
        }
    }
}
