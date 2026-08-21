package com.xy2407.nsukaddition.mixin.client.simukraft;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.CityUpgradeRequirement;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 在官方城市升级需求区追加 xy's_nsuk 建筑需求（农场/牧场/商业/工厂/矿场），整体 UI 保持 simukraft 官方样式。
 *  官方 CityUpgradePanelFactory 为包私有类，无法在源码中直接引用，故用 targets 字符串形式定位（运行时注入不受 Java 访问限制）。 */
@Mixin(targets = "client.cn.kafei.simukraft.client.city.CityUpgradePanelFactory", remap = false)
public abstract class CityUpgradePanelFactoryMixin {

    @Shadow
    private static Label line(Component text) {
        throw new AssertionError();
    }

    @Shadow
    private static UIElement metricRow(Component text, boolean satisfied) {
        throw new AssertionError();
    }

    @Inject(method = "requirements", at = @At("RETURN"), remap = false)
    private static void nsuk$appendBuildingRequirements(
            CityCoreOpenResponsePacket packet,
            CityCoreOpenResponsePacket.UpgradeTarget target,
            boolean nextLevel,
            CallbackInfoReturnable<UIElement> cir) {
        CityUpgradeRequirement req = CityUpgradeRequirement.forTargetLevel(CityLevel.fromLevel(target.level()));
        if (req == null) {
            return;
        }
        SidebarDataSnapshot snap = SidebarDataSnapshot.get();
        UIElement details = cir.getReturnValue();

        Button submit = null;
        List<UIElement> children = new ArrayList<>(details.getChildren());
        if (nextLevel) {
            for (UIElement child : children) {
                if (child instanceof Button button) {
                    submit = button;
                    break;
                }
            }
        }
        details.clearAllChildren();
        for (UIElement child : children) {
            if (child != submit) {
                details.addChild(child);
            }
        }

        details.addChild(line(Component.translatable("gui.xy2407_nsuk_addition.city_upgrade.requirements")));
        addBuildingRow(details, "gui.xy2407_nsuk_addition.city_upgrade.requirement.farms",
                snap.farmCount(), req.requiredFarms());
        addBuildingRow(details, "gui.xy2407_nsuk_addition.city_upgrade.requirement.ranches",
                snap.ranchCount(), req.requiredRanches());
        addBuildingRow(details, "gui.xy2407_nsuk_addition.city_upgrade.requirement.shops",
                snap.shopCount(), req.requiredShops());
        addBuildingRow(details, "gui.xy2407_nsuk_addition.city_upgrade.requirement.factories",
                snap.factoryCount(), req.requiredFactories());
        addBuildingRow(details, "gui.xy2407_nsuk_addition.city_upgrade.requirement.mines",
                snap.mineCount(), req.requiredMines());

        if (submit != null) {
            if (!buildingsMet(snap, req)) {
                submit.disabled();
            }
            details.addChild(submit);
        }
    }

    private static void addBuildingRow(UIElement details, String key, int current, int required) {
        if (required <= 0) {
            return;
        }
        details.addChild(metricRow(Component.translatable(key, current, required), current >= required));
    }

    private static boolean buildingsMet(SidebarDataSnapshot snap, CityUpgradeRequirement req) {
        return snap.farmCount() >= req.requiredFarms()
                && snap.ranchCount() >= req.requiredRanches()
                && snap.shopCount() >= req.requiredShops()
                && snap.factoryCount() >= req.requiredFactories()
                && snap.mineCount() >= req.requiredMines();
    }

    @Inject(method = "countPlayerItems", at = @At("RETURN"), cancellable = true, remap = false)
    private static void nsuk$addWarehouseUpgradeMaterials(
            CityCoreOpenResponsePacket.UpgradeItem requirement,
            CallbackInfoReturnable<Integer> cir) {
        int warehouse = upgradeWarehouseCount(requirement);
        if (warehouse > 0) {
            cir.setReturnValue(cir.getReturnValue() + warehouse);
        }
    }

    private static int upgradeWarehouseCount(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        if (requirement == null) {
            return 0;
        }
        SidebarDataSnapshot snap = SidebarDataSnapshot.get();
        if (requirement.isTag() && ResourceLocation.parse("minecraft:logs").equals(requirement.itemTag())) {
            return snap.reserveCount(MaterialCategoryRegistry.UPGRADE_LOGS_KEY);
        }
        if (requirement.itemId() != null && ResourceLocation.parse("minecraft:cobblestone").equals(requirement.itemId())) {
            return snap.reserveCount(MaterialCategoryRegistry.UPGRADE_STONE_KEY);
        }
        return 0;
    }
}
