package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.common.network.rts.RtsNpcListPacket;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** RTS 专属 NPC 列表推送：每 10 tick 向在线玩家发送其所属城市的市民 UUID+职业，供客户端 RTS 归属判断(与 HUD 快照隔离)。 */
public final class RtsNpcSyncService {

    private static final long SYNC_INTERVAL = 10L;

    private RtsNpcSyncService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.getGameTime() % SYNC_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            var city = CityManager.get(level).getPlayerCity(player.getUUID());
            if (city.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new RtsNpcListPacket(null, List.of()));
                continue;
            }
            UUID cityId = city.get().cityId();
            List<RtsNpcListPacket.NpcEntry> npcs = new ArrayList<>();
            for (CitizenData c : CitizenService.listCitizensByCity(level, cityId)) {
                npcs.add(new RtsNpcListPacket.NpcEntry(c.uuid(),
                        c.jobType() != null ? c.jobType().name() : ""));
            }
            PacketDistributor.sendToPlayer(player, new RtsNpcListPacket(cityId, npcs));
        }
    }
}
