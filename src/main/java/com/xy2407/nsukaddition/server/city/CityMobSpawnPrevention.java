package com.xy2407.nsukaddition.server.city;

import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/** 城市领地区块内阻止敌对怪物生成，仅限玩家城市领地，排除白名单实体。 */
public final class CityMobSpawnPrevention {

    private static final ResourceLocation MAID_ID = ResourceLocation.tryParse("touhou_little_maid:maid");
    private static final UUID SYSTEM_MAYOR_ID = UUID.nameUUIDFromBytes("nsuk:village_city_mayor".getBytes());

    private CityMobSpawnPrevention() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster)) return;

        ResourceLocation entityId = event.getEntity().getType().builtInRegistryHolder().key().location();
        if (MAID_ID != null && MAID_ID.equals(entityId)) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        ChunkPos chunkPos = new ChunkPos(event.getEntity().blockPosition());
        UUID ownerCityId = CityChunkManager.get(serverLevel).getChunkOwner(chunkPos.toLong());
        if (ownerCityId == null) return;
        CityData city = CityService.findCity(serverLevel, ownerCityId).orElse(null);
        if (city != null && city.member(SYSTEM_MAYOR_ID)
                .filter(m -> m.permissionLevel() == CityPermissionLevel.MAYOR).isPresent()) {
            return;
        }
        event.setCanceled(true);
    }
}