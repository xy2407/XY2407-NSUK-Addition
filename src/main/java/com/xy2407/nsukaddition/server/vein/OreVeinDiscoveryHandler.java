package com.xy2407.nsukaddition.server.vein;

import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryRequestPacket;
import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryResponsePacket;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/** 矿脉发现请求处理器，处理客户端的矿脉探测请求并返回结果。 */
public final class OreVeinDiscoveryHandler {

    private OreVeinDiscoveryHandler() {}

    public static void handle(OreVeinDiscoveryRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();

            Map<Long, OreVeinType> veins = OreVeinDiscoveryService.discover(player, level, packet.chunkX(), packet.chunkZ());
            String oreName = "";
            if (!veins.isEmpty()) {
                OreVeinType type = veins.values().iterator().next();
                oreName = type.displayName();
                PacketDistributor.sendToPlayer(player, new OreVeinDiscoveryResponsePacket(veins, oreName));
            }

            player.sendSystemMessage(Component.literal(
                    veins.isEmpty()
                            ? "该区块没有矿脉 (" + packet.chunkX() + ", " + packet.chunkZ() + ")"
                            : "发现 " + oreName + " 矿脉，共 " + veins.size() + " 个区块"
            ).withStyle(veins.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        });
    }
}
