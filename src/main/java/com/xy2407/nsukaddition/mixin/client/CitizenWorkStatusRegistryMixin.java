package com.xy2407.nsukaddition.mixin.client;

import client.cn.kafei.simukraft.client.renderer.CitizenWorkStatusDisplayRegistry;
import com.xy2407.nsukaddition.common.city.TourismConstants;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在状态显示注册表中注册旅游/商队标签的高优先级条目，在custom_status_label(400)之前处理。 */
@Mixin(value = CitizenWorkStatusDisplayRegistry.class, remap = false)
public class CitizenWorkStatusRegistryMixin {

    private static final String SEPARATOR = "||";

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void nsuk$registerTouristAndCaravanLabels(CallbackInfo ci) {
        CitizenWorkStatusDisplayRegistry.register("nsuk_tourist_label", 450, context -> {
            String label = context.statusLabel();
            if (label == null || !label.startsWith(TourismConstants.TOURIST_STATUS_LABEL)) {
                return java.util.Optional.empty();
            }
            String clean = label.contains(SEPARATOR) ? label.substring(0, label.indexOf(SEPARATOR)) : label;
            return java.util.Optional.of(Component.translatable(clean).withStyle(style -> style.withColor(0x5555FF)));
        });

        CitizenWorkStatusDisplayRegistry.register("nsuk_caravan_leader_label", 450, context -> {
            String label = context.statusLabel();
            if (label == null || !label.startsWith(TourismConstants.CARAVAN_LEADER_STATUS)) {
                return java.util.Optional.empty();
            }
            String clean = label.contains(SEPARATOR) ? label.substring(0, label.indexOf(SEPARATOR)) : label;
            return java.util.Optional.of(Component.translatable(clean).withStyle(style -> style.withColor(0x5555FF)));
        });

        CitizenWorkStatusDisplayRegistry.register("nsuk_caravan_follower_label", 450, context -> {
            String label = context.statusLabel();
            if (label == null || !label.startsWith(TourismConstants.CARAVAN_FOLLOWER_STATUS)) {
                return java.util.Optional.empty();
            }
            String clean = label.contains(SEPARATOR) ? label.substring(0, label.indexOf(SEPARATOR)) : label;
            return java.util.Optional.of(Component.translatable(clean).withStyle(style -> style.withColor(0x5555FF)));
        });
    }
}
