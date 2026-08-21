package com.xy2407.nsukaddition.common.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.List;

/**
 * 车万女仆餐厅服务员任务：女仆被餐厅雇佣后切换到此任务，代表"在餐厅工作"状态。
 * 实际取菜/上菜行为由服务端 MaidWaiterWorkService 驱动（与 SimU-Kraft 服务员同构），
 * 任务本身只负责身份标识与关闭随机闲逛，避免女仆乱跑。
 */
public final class RestaurantMaidWaiterTask implements IMaidTask {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "restaurant_waiter");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.BOWL);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Collections.emptyList();
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        return false;
    }

    @Override
    public boolean enableEating(EntityMaid maid) {
        return false;
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundEvents.VILLAGER_AMBIENT;
    }
}
