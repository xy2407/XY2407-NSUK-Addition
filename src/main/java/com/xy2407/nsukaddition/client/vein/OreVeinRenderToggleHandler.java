package com.xy2407.nsukaddition.client.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.compat.xaero.NsukVeinHighlighter;
import com.xy2407.nsukaddition.client.compat.xaero.NsukXaeroWorldMapIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 矿脉高亮渲染开关处理器，玩家手持木棍时切换矿脉地图高亮显示。 */
@OnlyIn(Dist.CLIENT)
public final class OreVeinRenderToggleHandler {

    private static boolean lastEnabled;

    private OreVeinRenderToggleHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(new OreVeinRenderToggleHandler());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        boolean enabled = isHoldingStick(player);
        NsukVeinHighlighter.setRenderEnabled(enabled);

        if (enabled != lastEnabled) {
            lastEnabled = enabled;
            NsukXaeroWorldMapIntegration.refreshVeinHighlights();
            NsukAddition.LOGGER.debug("NsukAddition: Ore vein highlight toggled by stick: {}", enabled);
        }
    }

    private static boolean isHoldingStick(Player player) {
        return isStick(player.getMainHandItem()) || isStick(player.getOffhandItem());
    }

    private static boolean isStick(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.STICK);
    }
}
