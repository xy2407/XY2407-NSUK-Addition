package com.xy2407.nsukaddition.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 模组键位映射定义与注册，管理侧边栏快捷键。 */
@OnlyIn(Dist.CLIENT)
public final class ModKeyMappings {

    public static final String CATEGORY = "key.categories.xy2407_nsuk_addition";

    public static final KeyMapping OPEN_SIDEBAR = new KeyMapping(
            "key.xy2407_nsuk_addition.open_sidebar",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    private ModKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SIDEBAR);
    }
}
