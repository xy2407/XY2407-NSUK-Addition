package com.xy2407.nsukaddition.common.network.citycore;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.citycore.CityCoreRotationUtil;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 城市核心图纸旋转同步包，客户端按 R 后发送旋转值到服务端写入物品 NBT。 */
public record CityCoreRotatePacket(int rotationDegrees) implements CustomPacketPayload {

    public static final Type<CityCoreRotatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "city_core_rotate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreRotatePacket> STREAM_CODEC =
            StreamCodec.of(CityCoreRotatePacket::encode, CityCoreRotatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CityCoreRotatePacket packet) {
        buffer.writeInt(packet.rotationDegrees());
    }

    private static CityCoreRotatePacket decode(RegistryFriendlyByteBuf buffer) {
        return new CityCoreRotatePacket(buffer.readInt());
    }

    public static void handle(CityCoreRotatePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int rotation = Math.floorMod(packet.rotationDegrees(), 360);
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (main.is(ModBlocks.CITY_CORE_PLACER.get())) {
            CityCoreRotationUtil.setRotation(main, rotation);
        } else if (off.is(ModBlocks.CITY_CORE_PLACER.get())) {
            CityCoreRotationUtil.setRotation(off, rotation);
        }
    }
}
