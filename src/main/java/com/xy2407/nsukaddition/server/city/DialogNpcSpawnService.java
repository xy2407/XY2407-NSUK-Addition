package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.Heightmap;

/** 玩家建城后在城市核心旁生成城市对话NPC，同一核心只生成一个。 */
public final class DialogNpcSpawnService {

    private static final String DISPLAY_NAME_KEY = "entity.xy2407_nsuk_addition.dialog_npc";

    private DialogNpcSpawnService() {}

    public static void spawnForCity(ServerLevel level, ServerPlayer player, BlockPos corePos) {
        if (level == null || corePos == null) return;
        for (DialogNpcEntity existing : level.getEntitiesOfClass(
                DialogNpcEntity.class, new AABB(corePos).inflate(24.0D))) {
            if (existing.getCoreOrigin() != null && existing.getCoreOrigin().distSqr(corePos) < 24.0D * 24.0D) {
                return;
            }
        }
        int px = corePos.getX() + 2;
        int pz = corePos.getZ() + 2;
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
        DialogNpcEntity npc = new DialogNpcEntity(DialogNpcEntity.TYPE, level);
        npc.moveTo(px + 0.5D, groundY, pz + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        npc.setCoreOrigin(corePos);
        npc.setCustomName(Component.translatable(DISPLAY_NAME_KEY));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    /** removeOrphans: 城市删除后清掉失去归属城市的管家，避免残留在原位置且对话异常。 */
    public static void removeOrphans(ServerLevel level) {
        if (level == null) return;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof DialogNpcEntity npc && npc.getCoreOrigin() != null
                    && CityService.findCityByCorePos(level, npc.getCoreOrigin()).isEmpty()) {
                npc.discard();
            }
        }
    }

    /** updateCoreForMove: 城市核心迁移后同步管家游走锚点，保证巡逻跟随新核心。 */
    public static void updateCoreForMove(ServerLevel level, BlockPos oldCorePos, BlockPos newCorePos) {
        if (level == null || oldCorePos == null || newCorePos == null) return;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof DialogNpcEntity npc && oldCorePos.equals(npc.getCoreOrigin())) {
                npc.setCoreOrigin(newCorePos.immutable());
            }
        }
    }
}