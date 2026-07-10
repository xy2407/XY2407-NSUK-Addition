package com.xy2407.nsukaddition.common.network.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import com.xy2407.nsukaddition.server.vein.OreVeinDiscoveryService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/** 矿脉发现请求包，由客户端发送指定区块坐标以查询矿脉信息。 */
public record OreVeinDiscoveryRequestPacket(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "ore_vein_discovery_request");
    public static final Type<OreVeinDiscoveryRequestPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OreVeinDiscoveryRequestPacket> STREAM_CODEC =
            StreamCodec.of(OreVeinDiscoveryRequestPacket::encode, OreVeinDiscoveryRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, OreVeinDiscoveryRequestPacket p) {
        buf.writeInt(p.chunkX);
        buf.writeInt(p.chunkZ);
    }

    public static OreVeinDiscoveryRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new OreVeinDiscoveryRequestPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(OreVeinDiscoveryRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();

            Map<Long, OreVeinType> veins = OreVeinDiscoveryService.discover(
                    player, level, packet.chunkX, packet.chunkZ());
            String oreName = "";
            if (!veins.isEmpty()) {
                OreVeinType type = veins.values().iterator().next();
                oreName = type.displayName();
                PacketDistributor.sendToPlayer(player, new OreVeinDiscoveryResponsePacket(veins, oreName));
            }

            player.sendSystemMessage(Component.literal(
                    veins.isEmpty()
                            ? "该区块没有矿脉 (" + packet.chunkX + ", " + packet.chunkZ + ")"
                            : "发现 " + oreName + " 矿脉，共 " + veins.size() + " 个区块"
            ).withStyle(veins.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        });
    }
}
