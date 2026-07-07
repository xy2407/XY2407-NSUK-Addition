package com.xy2407.nsukaddition.mixin.client.xaero;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.compat.xaero.NsukXaeroWorldMapIntegration;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 修改 WorldMapSession，在地图会话初始化时注入矿石矿脉高亮器注册。 */
@Pseudo
@OnlyIn(Dist.CLIENT)
@Mixin(value = WorldMapSession.class, remap = false)
public abstract class MixinWorldMapSession {

    @Shadow
    private MapProcessor mapProcessor;

    @Inject(method = "init", at = @At("RETURN"), remap = false, require = 0)
    private void nsuk$registerVeinHighlighter(ClientPacketListener connection, long biomeZoomSeed, CallbackInfo callbackInfo) {
        try {
            if (this.mapProcessor == null) return;
            HighlighterRegistry registry = this.mapProcessor.getHighlighterRegistry();
            if (registry == null) return;
            Field highlightersField = HighlighterRegistry.class.getDeclaredField("highlighters");
            highlightersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<AbstractHighlighter> highlighters = (List<AbstractHighlighter>) highlightersField.get(registry);
            List<AbstractHighlighter> mutableHighlighters = new ArrayList<>(highlighters);
            NsukXaeroWorldMapIntegration.registerHighlighter(mutableHighlighters);
            highlightersField.set(registry, Collections.unmodifiableList(mutableHighlighters));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            NsukAddition.LOGGER.warn("NsukAddition: Failed to register Xaero ore vein highlighter.", exception);
        }
    }
}
