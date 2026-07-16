package com.xy2407.nsukaddition.common.network.clientbound;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxViewUpdatePacket;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 采矿控制箱界面桥接，解耦公共包对客户端 MiningControlBoxUiRoot 的直接依赖。 */
public final class MiningControlBoxUiBridge {

    private static Consumer<MiningControlBoxOpenResponsePacket> openRefreshHandler = p -> {};
    private static Consumer<MiningControlBoxViewUpdatePacket> viewUpdateHandler = p -> {};
    private static BiFunction<Player, MiningControlBoxOpenResponsePacket, ModularUI> clientUIFactory = (player, packet) -> null;

    private MiningControlBoxUiBridge() {}

    public static void install(Consumer<MiningControlBoxOpenResponsePacket> openRefresh,
                               Consumer<MiningControlBoxViewUpdatePacket> viewUpdate,
                               BiFunction<Player, MiningControlBoxOpenResponsePacket, ModularUI> uiFactory) {
        openRefreshHandler = openRefresh != null ? openRefresh : p -> {};
        viewUpdateHandler = viewUpdate != null ? viewUpdate : p -> {};
        clientUIFactory = uiFactory != null ? uiFactory : (player, packet) -> null;
    }

    public static void reset() {
        openRefreshHandler = p -> {};
        viewUpdateHandler = p -> {};
        clientUIFactory = (player, packet) -> null;
    }

    public static void refreshActive(MiningControlBoxOpenResponsePacket p) {
        openRefreshHandler.accept(p);
    }

    public static void refreshActive(MiningControlBoxViewUpdatePacket p) {
        viewUpdateHandler.accept(p);
    }

    public static ModularUI createClientUI(Player player, MiningControlBoxOpenResponsePacket packet) {
        return clientUIFactory.apply(player, packet);
    }
}
