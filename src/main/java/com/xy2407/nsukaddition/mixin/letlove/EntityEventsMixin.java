package com.xy2407.nsukaddition.mixin.letlove;

import com.chinaex123.letfishlove.capabilities.FishBreedingCap;
import com.chinaex123.letfishlove.capabilities.FishBreedingCapAttacher;
import com.chinaex123.letfishlove.entity.FishBreedGoal;
import com.chinaex123.letfishlove.entity.FishBreedingUtil;
import com.chinaex123.letfishlove.entity.FishLayRoeGoal;
import com.xy2407.nsukaddition.common.compat.LetFishLoveCompat;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 EntityEvents，为水生生物和潮汐模组岩浆鱼注入繁殖与交互能力。 */
@Mixin(com.chinaex123.letfishlove.event.EntityEvents.class)
public class EntityEventsMixin {

    @Inject(method = "onEntityJoin", at = @At("TAIL"), remap = false)
    private static void onEntityJoin(EntityJoinLevelEvent event, CallbackInfo ci) {
        if (!ModList.get().isLoaded("letfishlove")) return;
        Entity entity = event.getEntity();

        if (entity instanceof WaterAnimal fish) {
            if (!FishBreedingUtil.isBreedable(fish)) {
                FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
                if (cap == null) {
                    cap = new FishBreedingCap(fish);
                    FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
                }
                TagKey<Item> temptationItems = FishBreedingUtil.getTemptationItems(fish.getType());
                if (temptationItems != null) {
                    fish.goalSelector.addGoal(0, new TemptGoal(fish, 1.2D, Ingredient.of(temptationItems), false));
                }
                fish.goalSelector.addGoal(0, new FishBreedGoal(fish, 1.0D));
                fish.goalSelector.addGoal(0, new FishLayRoeGoal(fish));
            }
            return;
        }

        if (entity instanceof PathfinderMob lavaFish && isTideEntity(lavaFish)) {
            FishBreedingCap cap = new FishBreedingCap(null);
            FishBreedingCapAttacher.CAPABILITY_CACHE.put(lavaFish.getUUID(), cap);

            TagKey<Item> temptationItems = FishBreedingUtil.getTemptationItems(lavaFish.getType());
            if (temptationItems != null) {
                lavaFish.goalSelector.addGoal(0, new TemptGoal(lavaFish, 1.2D, Ingredient.of(temptationItems), false));
            }
            lavaFish.goalSelector.addGoal(0, new com.xy2407.nsukaddition.common.breeding.LavaFishBreedGoal(lavaFish, 1.0D));
            lavaFish.goalSelector.addGoal(0, new com.xy2407.nsukaddition.common.breeding.LavaFishLayRoeGoal(lavaFish));
        }
    }

    @Inject(method = "onEntityInteract", at = @At("TAIL"), remap = false)
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (!ModList.get().isLoaded("letfishlove")) return;
        if (event.isCanceled()) return;

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack itemInHand = player.getItemInHand(hand);
        Entity target = event.getTarget();
        Level level = event.getLevel();

        if (target instanceof WaterAnimal fish && !FishBreedingUtil.isBreedable(fish)) {
            TagKey<Item> temptationItems = FishBreedingUtil.getTemptationItems(fish.getType());
            if (!itemInHand.is(temptationItems)) return;

            FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
            if (cap == null) {
                cap = new FishBreedingCap(fish);
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
            }
            if (!cap.canFallInLove()) return;

            cap.setInLove(fish, player, level);
            if (!level.isClientSide()) {
                FishBreedingUtil.usePlayerItem(player, itemInHand);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            event.setCanceled(true);
            return;
        }

        if (target instanceof PathfinderMob lavaFish && isTideEntity(lavaFish)) {
            TagKey<Item> temptationItems = FishBreedingUtil.getTemptationItems(lavaFish.getType());
            if (!itemInHand.is(temptationItems)) return;

            FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(lavaFish.getUUID());
            if (cap == null) {
                cap = new FishBreedingCap(null);
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(lavaFish.getUUID(), cap);
            }
            if (!cap.canFallInLove()) return;

            LetFishLoveCompat.setInLove(lavaFish, player, level);
            if (!level.isClientSide()) {
                FishBreedingUtil.usePlayerItem(player, itemInHand);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            event.setCanceled(true);
        }
    }

    private static boolean isTideEntity(Entity entity) {
        return entity.getType().builtInRegistryHolder().key().location().toString().startsWith("tide:");
    }
}
