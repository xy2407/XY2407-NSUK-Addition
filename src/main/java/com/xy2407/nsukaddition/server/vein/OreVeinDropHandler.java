package com.xy2407.nsukaddition.server.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.vein.OreVeinChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.item.component.CustomData;

/** 矿脉掉落物处理器，在矿脉区域内将原石等掉落物标记为富矿。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class OreVeinDropHandler {

    public static final String RICH_ORE_TAG = "nsukaddition_rich_ore";

    private OreVeinDropHandler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        ItemStack rich = process((ServerLevel) event.getLevel(), itemEntity.blockPosition(), stack);
        if (rich != stack) {
            itemEntity.setItem(rich);
        }
    }

    public static ItemStack process(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || !isVeinSource(stack)) return stack;

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        OreVeinChunkData data = OreVeinDistributionService.getVeinAt(level, chunkX, chunkZ);
        if (data == null) return stack;

        ItemStack rich = stack.copyWithCount(stack.getCount());
        CompoundTag tag = new CompoundTag();
        tag.putString(RICH_ORE_TAG, data.oreType().id());
        rich.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return rich;
    }

    private static boolean isVeinSource(ItemStack stack) {
        return !stack.isEmpty() && (
                stack.is(Items.COBBLESTONE)
                        || stack.is(Items.STONE)
                        || stack.is(Items.DEEPSLATE)
                        || stack.is(Items.COBBLED_DEEPSLATE)
                        || stack.is(Items.NETHERRACK)
        );
    }
}
