package com.xy2407.nsukaddition.client.network.vein;

import com.xy2407.nsukaddition.client.compat.xaero.NsukXaeroWorldMapIntegration;
import com.xy2407.nsukaddition.client.vein.OreVeinClientCache;
import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 矿脉发现响应包的客户端处理，将新矿脉写入缓存并刷新地图高亮。 */
@OnlyIn(Dist.CLIENT)
public final class OreVeinDiscoveryClientHandler {

    private OreVeinDiscoveryClientHandler() {}

    public static void handle(OreVeinDiscoveryResponsePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;

            Level level = minecraft.level;
            OreVeinClientCache.getInstance().addVeins(level.dimension(), payload.veins());

            OreVeinClientCache.getInstance().saveToDisk();
            NsukXaeroWorldMapIntegration.refreshVeinHighlights();
        });
    }
}
