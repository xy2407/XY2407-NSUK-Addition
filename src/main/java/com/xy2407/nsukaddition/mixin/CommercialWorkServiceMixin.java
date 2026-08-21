package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.commercial.CommercialBoxData;
import common.cn.kafei.simukraft.commercial.CommercialBoxManager;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.commercial.CommercialWorkService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** 商业员工自喂食时直接从附近容器取食，避免离开商店去买饭。新版本已不再用 isSelfFeeding 阻塞商店，此 Mixin 仅保留就近取食优化。 */
@Mixin(CommercialWorkService.class)
public abstract class CommercialWorkServiceMixin {
    private static final int FEED_RADIUS_XZ = 5;
    private static final int FEED_RADIUS_Y = 2;

    @Inject(
            method = "tickBox",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/commercial/CommercialStockService;restock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcommon/cn/kafei/simukraft/commercial/CommercialDefinition;)V", remap = false),
            remap = false
    )
    private static void nsuk$feedHungryWorker(ServerLevel level, CommercialBoxManager manager, CommercialBoxData data,
                                              @Coerce Object runtime, long gameTime, CallbackInfo ci) {
        CitizenData worker = CommercialControlBoxService.findAssignedWorker(level, data.boxPos());
        if (worker == null || !CitizenSelfFeedingService.isSelfFeeding(level, worker.uuid())) {
            return;
        }
        tryFeedFromContainers(level, data.boxPos(), worker);
    }

    @Unique
    private static void tryFeedFromContainers(ServerLevel level, BlockPos boxPos, CitizenData worker) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, worker.uuid());
        if (entity == null) return;

        for (BlockPos container : nearbyContainers(level, boxPos)) {
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                ItemStack stack = slot.stack();
                if (stack.isEmpty()) continue;

                FoodProperties props = stack.getFoodProperties(entity);
                if (props == null || props.nutrition() <= 0) continue;

                if (!GenericContainerAccess.consumeSingleItemAtSlot(level, container, slot.slot(), slot.access(), slot.side(), stack.getItem())) {
                    continue;
                }

                entity.setHunger(20.0D);
                level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
                return;
            }
        }
    }

    @Unique
    private static Set<BlockPos> nearbyContainers(ServerLevel level, BlockPos centerPos) {
        Set<BlockPos> containers = new LinkedHashSet<>();
        for (int dx = -FEED_RADIUS_XZ; dx <= FEED_RADIUS_XZ; dx++) {
            for (int dy = -FEED_RADIUS_Y; dy <= FEED_RADIUS_Y; dy++) {
                for (int dz = -FEED_RADIUS_XZ; dz <= FEED_RADIUS_XZ; dz++) {
                    BlockPos candidate = centerPos.offset(dx, dy, dz);
                    if (GenericContainerAccess.isContainer(level, candidate)) {
                        containers.add(GenericContainerAccess.canonicalContainerPos(level, candidate));
                    }
                }
            }
        }
        return containers;
    }
}
