package com.xy2407.nsukaddition.client.renderer;

import client.cn.kafei.simukraft.client.renderer.CitizenOverheadStatusRegistry;
import client.cn.kafei.simukraft.client.renderer.CitizenWorkStatusDisplayRegistry;
import com.xy2407.nsukaddition.common.city.TourismConstants;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;

/** 游客市民头顶状态渲染器，覆盖工作状态和饥饿状态的显示逻辑。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class TouristStatusRenderer {

    private TouristStatusRenderer() {
    }

    public static void register() {

        CitizenOverheadStatusRegistry.register("work_status",
                CitizenOverheadStatusRegistry.PRIORITY_WORK_STATUS,
                TouristStatusRenderer::workStatus);

        CitizenOverheadStatusRegistry.register("hunger",
                CitizenOverheadStatusRegistry.PRIORITY_HUNGER,
                TouristStatusRenderer::hunger);
    }

    private static Optional<CitizenOverheadStatusRegistry.StatusLine> workStatus(CitizenEntity entity) {
        if (isTourist(entity)) {
            return Optional.of(new CitizenOverheadStatusRegistry.StatusLine(
                    Component.translatable(TourismConstants.TOURIST_STATUS_LABEL),
                    0x40E0FF,
                    0.02F));
        }
        return Optional.of(new CitizenOverheadStatusRegistry.StatusLine(
                CitizenWorkStatusDisplayRegistry.resolve(entity),
                0xFFFF00,
                0.02F));
    }

    private static Optional<CitizenOverheadStatusRegistry.StatusLine> hunger(CitizenEntity entity) {
        if (isTourist(entity)) {
            return Optional.empty();
        }
        return Optional.of(new CitizenOverheadStatusRegistry.StatusLine(
                Component.translatable(entity.getHungerLevelKey()),
                0xFFFF00,
                0.02F));
    }

    private static boolean isTourist(CitizenEntity entity) {
        return TourismConstants.TOURIST_STATUS_LABEL.equals(entity.getStatusLabel());
    }
}
