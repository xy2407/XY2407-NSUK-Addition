package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.city.CityCoreScreenOpener;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.CityUpgradeRequirement;
import com.xy2407.nsukaddition.common.network.city.CityUpgradeRequestPacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/** 修改 CityCoreScreenOpener，替换城市升级面板以展示自定义等级与升级需求。 */
@Mixin(CityCoreScreenOpener.class)
public abstract class CityCoreScreenOpenerMixin {

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
}
