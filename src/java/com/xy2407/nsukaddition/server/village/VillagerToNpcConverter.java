package com.xy2407.nsukaddition.server.village;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/** 村民→NPC转换器：村民加入世界时替换为市民实体，并自动加入所在城市领地的城市。 */
public final class VillagerToNpcConverter {

    private VillagerToNpcConverter() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        event.setCanceled(true);

        BlockPos spawnPos = villager.blockPosition();
        float yRot = villager.getYRot();
        float xRot = villager.getXRot();

        level.getServer().execute(() -> {
            spawnNpcAt(level, spawnPos, yRot, xRot);
        });
    }

    private static void spawnNpcAt(ServerLevel level, BlockPos pos, float yRot, float xRot) {
        CitizenEntity npc = ModEntities.CITIZEN.get().create(level);
        if (npc == null) return;

        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yRot, xRot);
        npc.setPersistenceRequired();

        CitizenData data = CitizenService.ensureCitizen(level, npc);
        if (data == null) {
            npc.discard();
            return;
        }

        long chunkLong = new ChunkPos(pos).toLong();
        CityChunkManager chunkManager = CityChunkManager.get(level);
        UUID cityId = chunkManager.getChunkOwner(chunkLong);
        if (cityId != null) {
            CitizenService.setCity(level, data.uuid(), cityId);
        }

        level.addFreshEntity(npc);
    }
}
