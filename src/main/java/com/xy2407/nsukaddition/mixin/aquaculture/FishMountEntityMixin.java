package com.xy2407.nsukaddition.mixin.aquaculture;

import com.li64.tide.data.fishing.FishData;
import com.teammetallurgy.aquaculture.api.AquacultureAPI;
import com.teammetallurgy.aquaculture.entity.FishMountEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** 修改 FishMountEntity，扩展鱼类展示板以支持 Tide 模组的鱼类。 */
@Mixin(FishMountEntity.class)
public class FishMountEntityMixin {

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, remap = false)
    public void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        FishMountEntity fishMount = (FishMountEntity) (Object) this;
        ItemStack heldStack = player.getItemInHand(hand);

        if (!fishMount.level().isClientSide) {
            if (fishMount.getDisplayedItem().isEmpty()) {
                Item heldItem = heldStack.getItem();

                boolean isAquacultureFish = AquacultureAPI.FISH_DATA.getFish().contains(heldItem);
                boolean isTideFish = false;
                if (!isAquacultureFish) {
                    isTideFish = isTideFish(heldItem);
                }

                if (isAquacultureFish || isTideFish) {
                    ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(heldItem);
                    EntityType<?> entityType = null;

                    if (BuiltInRegistries.ENTITY_TYPE.containsKey(itemKey)) {
                        entityType = BuiltInRegistries.ENTITY_TYPE.get(itemKey);
                    } else {
                        String path = itemKey.getPath();

                        if (path.endsWith("_bucket")) {
                            ResourceLocation altKey = ResourceLocation.fromNamespaceAndPath(
                                    itemKey.getNamespace(), path.substring(0, path.length() - 7));
                            if (BuiltInRegistries.ENTITY_TYPE.containsKey(altKey)) {
                                entityType = BuiltInRegistries.ENTITY_TYPE.get(altKey);
                            }
                        }

                        if (entityType == null) {
                            ResourceLocation altKey = ResourceLocation.fromNamespaceAndPath(
                                    itemKey.getNamespace(), path + "_fish");
                            if (BuiltInRegistries.ENTITY_TYPE.containsKey(altKey)) {
                                entityType = BuiltInRegistries.ENTITY_TYPE.get(altKey);
                            }
                        }
                    }

                    if (entityType != null) {
                        fishMount.setDisplayedItem(heldStack);
                        if (!player.getAbilities().instabuild) {
                            heldStack.shrink(1);
                        }
                        cir.setReturnValue(InteractionResult.CONSUME);
                    }
                }
            } else {
                cir.setReturnValue(InteractionResult.CONSUME);
            }
        }
    }

    private boolean isTideFish(Item item) {
        try {
            Optional<FishData> fishDataOp = FishData.get(item);
            if (fishDataOp.isPresent()) {
                FishData fishData = fishDataOp.get();
                return fishData.associatedMods().contains("tide") || fishData.associatedMods().isEmpty();
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
