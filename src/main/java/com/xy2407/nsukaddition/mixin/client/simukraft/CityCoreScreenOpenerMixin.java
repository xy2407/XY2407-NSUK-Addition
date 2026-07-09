package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.city.CityCoreScreenOpener;
import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.CityUpgradeRequirement;
import com.xy2407.nsukaddition.common.network.city.CityUpgradeRequestPacket;
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

import java.util.Locale;

/** 修改 CityCoreScreenOpener，替换城市升级面板并在菜单中添加核心迁移选项。 */
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

    @Inject(method = "upgradePanel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$upgradePanel(CityCoreOpenResponsePacket packet, CallbackInfoReturnable<UIElement> cir) {
        UIElement panel = basePanel();

        CityLevel currentLevel = CityLevel.fromLevel(packet.cityLevel());
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.current_level", currentLevel.displayName())));

        if (currentLevel.isMax()) {
            panel.addChild(contentSpacer());
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.max_level")));
            cir.setReturnValue(scrollable(panel));
            return;
        }

        CityLevel nextLevel = currentLevel.next();
        CityUpgradeRequirement req = CityUpgradeRequirement.forCurrentLevel(currentLevel);
        if (req == null || nextLevel == null) {
            panel.addChild(contentSpacer());
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.unavailable")));
            cir.setReturnValue(scrollable(panel));
            return;
        }

        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.next_level", nextLevel.displayName())));
        panel.addChild(contentSpacer());

        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.requirements")));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.require_population",
                req.requiredPopulation())));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.require_logs",
                req.requiredLogs())));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.require_stone",
                req.requiredStone())));
        if (req.requiredFunds() > 0) {
            panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.require_funds",
                    String.format(Locale.ROOT, "%.2f", req.requiredFunds()))));
        }

        panel.addChild(contentSpacer());
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.progress")));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.progress_population",
                packet.cityPopulation(), req.requiredPopulation())));
        panel.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.progress_funds",
                String.format(Locale.ROOT, "%.2f", packet.funds()),
                String.format(Locale.ROOT, "%.2f", req.requiredFunds()))));

        panel.addChild(contentSpacer());
        panel.addChild(contentButton("gui.xy2407_nsuk_addition.city_upgrade.button", () ->
                PacketDistributor.sendToServer(new CityUpgradeRequestPacket(packet.pos(), packet.cityId()))
        ));

        cir.setReturnValue(scrollable(panel));
    }

    @Inject(method = "menuColumn", at = @At("RETURN"), remap = false)
    private static void nsuk$addMoveMenuButton(CallbackInfoReturnable<UIElement> cir) {
        CityCoreOpenResponsePacket packet = nsuk$currentPacket;
        if (packet == null) return;
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
}
