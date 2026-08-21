package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.rts.path.SableDoorBridge;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.PathWaypoint;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CitizenDoorService 桥接：原版只读主世界方块，看不到 Sable 物理结构里（plot 远端坐标）的木门。
 * 在开门/关门入口处先行尝试 Sable 门；原逻辑对结构门读到空气本就空转，桥接成功即放行市民。
 * 桥接自带门登记表，与原版 openedDoors 完全独立，不会互相干扰。
 */
@Mixin(targets = "common.cn.kafei.simukraft.path.CitizenDoorService")
public abstract class CitizenDoorServiceSableMixin {

    @Inject(method = "tryOpenWoodenDoor", at = @At("HEAD"))
    private static void nsukaddition$tryOpenSableDoor(ServerLevel level, CitizenEntity citizen,
                                                      PathWaypoint waypoint, Map<?, ?> openedDoors, CallbackInfo ci) {
        if (level == null || citizen == null || waypoint == null) return;
        SableDoorBridge.tryOpenWoodenDoor(level, citizen, waypoint.blockPos());
    }

    @Inject(method = "processOpenedDoors", at = @At("HEAD"))
    private static void nsukaddition$processOpenedSableDoors(ServerLevel level, Map<?, ?> openedDoors,
                                                             Set<UUID> activeCitizenIds, CallbackInfo ci) {
        if (level == null) return;
        SableDoorBridge.processOpenedDoors(level, activeCitizenIds);
    }
}