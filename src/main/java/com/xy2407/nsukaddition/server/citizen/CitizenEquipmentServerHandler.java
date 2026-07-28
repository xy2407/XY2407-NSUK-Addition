package com.xy2407.nsukaddition.server.citizen;

import com.xy2407.nsukaddition.common.citizen.CitizenEquipmentService;
import com.xy2407.nsukaddition.common.citizen.CitizenEquipmentSqliteStorage;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** 服务端事件处理器，负责市民实体加载时恢复装备、死亡时保存/掉落装备。 */
@SuppressWarnings("null")
public final class CitizenEquipmentServerHandler {

    private CitizenEquipmentServerHandler() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof CitizenEntity citizen) || !(entity.level() instanceof ServerLevel level)) return;
        if (!citizen.isAlive() || citizen.isRemoved()) return;
        if (hasAnyArmor(citizen)) return;
        CitizenEquipmentService.applyToEntity(level, citizen);
    }

    private static boolean hasAnyArmor(CitizenEntity citizen) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !citizen.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity citizen) || !(citizen.level() instanceof ServerLevel level)) return;
        CitizenEquipmentService.dropEquipment(level, citizen);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        CitizenEquipmentSqliteStorage.clearServerCache(server);
    }
}
