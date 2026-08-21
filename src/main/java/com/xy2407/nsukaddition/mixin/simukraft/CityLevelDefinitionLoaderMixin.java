package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.CityUpgradeRequirement;
import common.cn.kafei.simukraft.city.CityLevelDefinition;
import common.cn.kafei.simukraft.city.CityLevelDefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 等级定义数据源替换：
 * 官方 2.2.0 升级系统从数据包(city_levels.json)读 12 级定义，这里改为返回 xy's_nsuk 的 5 级制
 * （CityLevel + CityUpgradeRequirement 硬编码数值）。官方 UI / 网络包 / 异步升级 / chunk 认领限制
 * 全部读取本数据，从而：
 * 1. 城市等级复用 xy 5 级制（聚落/村庄/城镇/城邦/都市）；
 * 2. unlockedEnclaves 恒为 0 → 官方 CityClaimService 拒绝一切非邻接区块认领，禁用飞地；
 * 3. unlockedChunks 使用 xy 的 maxChunks（与 CityClaimServiceMixin 的认领上限一致）。
 */
@Mixin(value = CityLevelDefinitionLoader.class, remap = false)
public abstract class CityLevelDefinitionLoaderMixin {

    private static final int XY_UPGRADE_DURATION_TICKS = 1_200;

    @Inject(method = "definitions", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$xyDefinitions(CallbackInfoReturnable<List<CityLevelDefinition>> cir) {
        cir.setReturnValue(buildXyDefinitions());
    }

    private static List<CityLevelDefinition> buildXyDefinitions() {
        List<CityLevelDefinition> defs = new ArrayList<>();
        for (CityLevel cl : CityLevel.values()) {
            CityUpgradeRequirement req = CityUpgradeRequirement.forTargetLevel(cl);
            int level = cl.level();
            String displayName = cl.displayName();
            int chunks = cl.maxChunks();
            double funds = req == null ? 0.0D : req.requiredFunds();
            int population = req == null ? 0 : req.requiredPopulation();
            int logs = req == null ? 0 : req.requiredLogs();
            int stone = req == null ? 0 : req.requiredStone();

            List<CityLevelDefinition.ItemRequirement> items = new ArrayList<>();
            if (logs > 0) {
                items.add(new CityLevelDefinition.ItemRequirement(
                        null,
                        ResourceLocation.parse("minecraft:logs"),
                        logs,
                        ResourceLocation.parse("minecraft:oak_log"),
                        "橡木原木"));
            }
            if (stone > 0) {
                items.add(new CityLevelDefinition.ItemRequirement(
                        ResourceLocation.parse("minecraft:cobblestone"), stone));
            }

            int duration = level == CityLevelDefinition.MIN_LEVEL ? 0 : XY_UPGRADE_DURATION_TICKS;
            defs.add(new CityLevelDefinition(
                    level, displayName, funds, population, chunks, 0, List.copyOf(items), duration));
        }
        return List.copyOf(defs);
    }
}