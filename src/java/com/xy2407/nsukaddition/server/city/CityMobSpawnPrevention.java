package com.xy2407.nsukaddition.server.city;

import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** 城市领地区块内阻止敌对怪物生成，排除白名单实体。 */
public final class CityMobSpawnPrevention {

    private static final ResourceLocation MAID_ID = ResourceLocation.tryParse("touhou_little_maid:maid");

    private CityMobSpawnPrevention() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster)) return;

        ResourceLocation entityId = event.getEntity().getType().builtInRegistryHolder().key().location();
        if (MAID_ID != null && MAID_ID.equals(entityId)) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        ChunkPos chunkPos = new ChunkPos(event.getEntity().blockPosition());
        if (CityChunkManager.get(serverLevel).getChunkOwner(chunkPos.toLong()) != null) {
            event.setCanceled(true);
        }
    }
}
