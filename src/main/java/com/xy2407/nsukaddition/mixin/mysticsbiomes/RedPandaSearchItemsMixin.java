package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.RedPanda;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** 优化红熊猫拾取物扫描：getNearbyItems 加 40tick 冷却缓存，避免激活期间每 tick 全量实体扫描。 */
@Mixin(targets = "com.mysticsbiomes.common.entity.RedPanda$SearchForItemsGoal")
public abstract class RedPandaSearchItemsMixin {

    @Shadow
    @Final
    private RedPanda redPanda;

    @Shadow
    @Final
    private Predicate<ItemEntity> HOLDABLE_ITEMS;

    @Unique
    private int xy2407$lastScanTick = -1;
    @Unique
    private List<ItemEntity> xy2407$cachedItems = List.of();

    /**
     * @reason 原实现 start/tick 激活期间每 tick 执行 8 格半径 ItemEntity 全量扫描；
     * 改为 40tick 冷却缓存，缓存列表以不可变副本返回，避免外部引用污染。
     */
    @Overwrite
    private List<ItemEntity> getNearbyItems() {
        int tick = this.redPanda.tickCount;
        if (tick - this.xy2407$lastScanTick < 80) {
            return this.xy2407$cachedItems;
        }
        this.xy2407$lastScanTick = tick;
        this.xy2407$cachedItems = List.copyOf(this.redPanda.level()
                .getEntitiesOfClass(ItemEntity.class,
                        this.redPanda.getBoundingBox().inflate(8.0, 8.0, 8.0), this.HOLDABLE_ITEMS));
        return this.xy2407$cachedItems;
    }
}