package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** RTS 服务端定时任务：驱动市民任务和玩家移动，并在服务器停止或玩家死亡时清理。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class RtsServerTick {

    private RtsServerTick() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RtsPlayerMoveService.tick(level);
            RtsFakePlayerSyncService.tick(level);
            RtsCitizenTaskManager.tick(level);
            RtsNpcSyncService.tick(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID ownerId = player.getUUID();
        RtsFakePlayerSyncService.clearPlayer(ownerId);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsFakePlayerEntity fake
                        && ownerId.equals(fake.getOwnerUUID())) {
                    fake.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID ownerId = player.getUUID();
        RtsFakePlayerSyncService.clearPlayer(ownerId);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsFakePlayerEntity fake
                        && ownerId.equals(fake.getOwnerUUID())) {
                    fake.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RtsPlayerMoveService.clearAll();
        RtsFakePlayerSyncService.clearAll();
        RtsCitizenTaskManager.clearAll();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsFakePlayerEntity fake) {
                    fake.discard();
                }
            }
        }
    }
}