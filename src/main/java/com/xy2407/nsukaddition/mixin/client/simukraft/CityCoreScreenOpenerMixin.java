package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.city.CityCoreScreenOpener;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import com.xy2407.nsukaddition.client.foreigntrade.DiplomacyClientCache;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;
import com.xy2407.nsukaddition.common.network.foreigntrade.DiplomacyDataRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.EstablishDiplomacyRequestPacket;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修改 CityCoreScreenOpener：升级面板使用官方 UI，仅在菜单中添加核心迁移与外交选项。 */
@Mixin(CityCoreScreenOpener.class)
public abstract class CityCoreScreenOpenerMixin {

    private static CityCoreOpenResponsePacket nsuk$currentPacket;

    @Shadow
    private static UIElement basePanel() { throw new AssertionError(); }

    @Shadow
    private static Label line(Component component) { throw new AssertionError(); }

    @Shadow
    private static Button contentButton(String key, Runnable action) { throw new AssertionError(); }

    @Shadow
    private static UIElement contentSpacer() { throw new AssertionError(); }

    @Shadow
    private static ScrollerView scrollable(UIElement child) { throw new AssertionError(); }

    @Shadow
    private static Button menuButton(String key, Runnable action) { throw new AssertionError(); }

    @Inject(method = "open", at = @At("HEAD"), remap = false)
    private static void nsuk$capturePacket(CityCoreOpenResponsePacket packet, CallbackInfo ci) {
        nsuk$currentPacket = packet;
    }

    @Inject(method = "menuColumn", at = @At("RETURN"), remap = false)
    private static void nsuk$addMoveMenuButton(CallbackInfoReturnable<UIElement> cir) {
        CityCoreOpenResponsePacket packet = nsuk$currentPacket;
        if (packet == null) return;

        if (packet.hasCity() && !packet.canManageCity()) {
            UIElement menu = cir.getReturnValue();
            var children = menu.getChildren();
            if (children.size() >= 2) {
                var copy = new java.util.ArrayList<>(children);
                menu.clearAllChildren();
                var spacer = copy.remove(copy.size() - 1);
                var closeBtn = copy.remove(copy.size() - 1);
                for (var child : copy) menu.addChild(child);
                menu.addChild(menuButton("gui.xy2407_nsuk_addition.foreign_trade.establish_menu", () -> {
                    PacketDistributor.sendToServer(new DiplomacyDataRequestPacket());
                    ClientInfoToast.show(
                            Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.establish_title"),
                            Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.opening_panel"),
                            "info");
                    try {
                        Class<?> openerClass = Class.forName("client.cn.kafei.simukraft.client.city.CityCoreScreenOpener");
                        java.lang.reflect.Field f = openerClass.getDeclaredField("activeWindow");
                        f.setAccessible(true);
                        Object window = f.get(null);
                        if (window != null) {
                            java.lang.reflect.Method m = window.getClass().getDeclaredMethod(
                                    "openTab", String.class, String.class, UIElement.class);
                            m.setAccessible(true);
                            m.invoke(window, "diplomacy",
                                    "gui.xy2407_nsuk_addition.foreign_trade.establish_menu",
                                    nsuk$createDiplomacyPanel(packet));
                        }
                    } catch (Exception e) {
                        NsukAddition.LOGGER.error("Failed to open diplomacy panel", e);
                    }
                }));
                menu.addChild(spacer);
                menu.addChild(closeBtn);
            }
            return;
        }

        if (!packet.hasCity() || packet.permissionLevel() != CityPermissionLevel.MAYOR) return;

        UIElement menu = cir.getReturnValue();
        var children = menu.getChildren();
        if (children.size() >= 2) {
            var copy = new java.util.ArrayList<>(children);
            menu.clearAllChildren();
            var spacer = copy.remove(copy.size() - 1);
            var closeBtn = copy.remove(copy.size() - 1);
            for (var child : copy) menu.addChild(child);
            menu.addChild(menuButton("gui.xy2407_nsuk_addition.city_core_move.menu", () -> {
                Minecraft.getInstance().setScreen(null);
                CityCoreMovePreview.enter(packet.pos(), packet.cityId());
            }));
            menu.addChild(spacer);
            menu.addChild(closeBtn);
        }
    }

    private static UIElement nsuk$createDiplomacyPanel(CityCoreOpenResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.establish_title")));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.establish_desc", packet.cityName())));
        panel.addChild(contentSpacer());
        int posX = packet.pos().getX();
        int posZ = packet.pos().getZ();
        boolean alreadyEstablished = false;
        for (DiplomacyRelation rel : DiplomacyClientCache.getRelations()) {
            if (rel.posX() == posX && rel.posZ() == posZ) {
                alreadyEstablished = true;
                break;
            }
        }
        if (alreadyEstablished) {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.already_established")));
        } else {
            panel.addChild(contentButton("gui.xy2407_nsuk_addition.foreign_trade.confirm_establish", () -> {
                PacketDistributor.sendToServer(new EstablishDiplomacyRequestPacket(packet.cityId(), posX, posZ));
                ClientInfoToast.show(
                        Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.establish_title"),
                        Component.translatable("gui.xy2407_nsuk_addition.foreign_trade.establish_toast", packet.cityName()),
                        "info");
            }));
        }
        return scrollable(panel);
    }
}
