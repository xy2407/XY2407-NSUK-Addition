package com.xy2407.nsukaddition.server.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryRequestPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 矿脉事件处理器，监听右键木棍触发矿脉探测请求。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class OreVeinEventHandler {

    private OreVeinEventHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.getItemStack().is(Items.STICK)) return;
        if (event.getEntity().isShiftKeyDown()) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                && level.dimension() != net.minecraft.world.level.Level.NETHER) return;

        ChunkPos chunk = event.getEntity().chunkPosition();
        PacketDistributor.sendToServer(new OreVeinDiscoveryRequestPacket(chunk.x, chunk.z));
    }
}
