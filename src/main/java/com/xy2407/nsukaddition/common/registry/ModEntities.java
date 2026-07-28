package com.xy2407.nsukaddition.common.registry;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.SitEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 模组实体类型注册中心。 */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, NsukAddition.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SitEntity>> SIT_ENTITY =
            ENTITY_TYPES.register("sit_entity", () -> SitEntity.TYPE);

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
