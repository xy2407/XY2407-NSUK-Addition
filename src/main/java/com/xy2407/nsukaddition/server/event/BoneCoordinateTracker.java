package com.xy2407.nsukaddition.server.event;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** 骨头坐标工具：蹲着右键标记原点，普通右键输出相对坐标到聊天框。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class BoneCoordinateTracker {

    private static final Map<UUID, BlockPos> ORIGINS = new ConcurrentHashMap<>();

    private BoneCoordinateTracker() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Items.BONE)) return;
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        UUID playerId = player.getUUID();

        if (player.isShiftKeyDown()) {
            BlockPos existing = ORIGINS.get(playerId);
            if (existing != null && existing.equals(pos)) {
                ORIGINS.remove(playerId);
                player.displayClientMessage(Component.literal("§c[坐标工具] 原点已解除"), false);
            } else {
                ORIGINS.put(playerId, pos);
                player.displayClientMessage(Component.literal("§a[坐标工具] 原点已标记: " + pos.toShortString()), false);
            }
        } else {
            BlockPos origin = ORIGINS.get(playerId);
            if (origin == null) {
                player.displayClientMessage(Component.literal("§c[坐标工具] 请先蹲着右键方块标记原点"), false);
            } else {
                int dx = pos.getX() - origin.getX();
                int dy = pos.getY() - origin.getY();
                int dz = pos.getZ() - origin.getZ();
                player.displayClientMessage(Component.literal("§b[坐标工具] 相对坐标: " + dx + ", " + dy + ", " + dz), false);
            }
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
