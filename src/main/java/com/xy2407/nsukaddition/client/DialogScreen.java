package com.xy2407.nsukaddition.client;

import com.mojang.blaze3d.platform.Lighting;
import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import com.xy2407.nsukaddition.common.network.dialog.DialogNpcOptionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** 城市对话NPC对话框：渐变背景、逐字多行正文、左侧半身模型、右侧可滚动对话选项并支持点击发送动作。 */
public class DialogScreen extends Screen {

    private static final int GRADIENT_TOP = 0x00000000;
    private static final int GRADIENT_BOTTOM = 0xFF000000;
    private static final int OPTION_NORMAL = 0xFFDDDDDD;
    private static final int OPTION_HOVER = 0xFFFFAA00;
    private static final int OPTION_LINE_H = 14;
    private static final long MS_PER_CHAR = 35L;

    private static boolean open = false;
    private static DialogNpcEntity DIALOG_STAND;

    private final String title;
    private final String body;
    private final List<String> options;
    private final List<String> actions;
    private final int entityId;
    private long textStartMs = 0L;
    private int scrollOffset = 0;

    private DialogScreen(String title, String body, List<String> options, List<String> actions, int entityId) {
        super(Component.literal("dialog"));
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.options = options == null ? List.of() : options;
        this.actions = actions == null ? List.of() : actions;
        this.entityId = entityId;
    }

    public static void open(String title, String body, List<String> options, List<String> actions, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreen(new DialogScreen(title, body, options, actions, entityId)));
        }
    }

    public static boolean isOpen() {
        return open;
    }

    private String dialogName() {
        return "城市管家";
    }

    @Override
    public void onClose() {
        boolean wasOpen = open;
        open = false;
        // 无论 ESC 或点击离开，都通知服务端恢复管家的巡逻移动
        if (wasOpen) {
            PacketDistributor.sendToServer(new DialogNpcOptionPacket(this.entityId, "leave"));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        open = true;
        if (textStartMs <= 0L) {
            textStartMs = System.currentTimeMillis();
        }
        int width = this.width;
        int height = this.height;
        gg.fillGradient(0, 0, width, height, GRADIENT_TOP, GRADIENT_BOTTOM);

        float cs = Math.max(1.0F, Math.min(1.8F, width / 900F));
        // 正文顶部上方：显示对话实体名称
        gg.drawCenteredString(this.font, "[" + dialogName() + "]", width / 2,
                (int) (height * 0.67F), 0xFFFFFFFF);
        float bodyY = height * 0.74F;
        String typed = typedBody();
        if (!typed.isEmpty()) {
            int designMaxW = Math.max(20, (int) (width * 0.48F / cs));
            List<FormattedCharSequence> lines = splitBody(typed, designMaxW);
            gg.pose().pushPose();
            gg.pose().translate(width / 2F, bodyY, 0.0D);
            gg.pose().scale(cs, cs, 1.0F);
            for (int i = 0; i < lines.size(); i++) {
                gg.drawString(this.font, lines.get(i), -this.font.width(lines.get(i)) / 2,
                        i * this.font.lineHeight, 0xFFFFEEFF, false);
            }
            gg.pose().popPose();
        }

        renderOptions(gg, mouseX, mouseY, width, height, cs);
        renderModel(gg);
    }

    private void renderOptions(GuiGraphics gg, int mouseX, int mouseY, int width, int height, float cs) {
        int optX = (int) (width * 0.80F);
        int optTop = (int) (height * 0.74F);
        int availH = height - optTop - 8;
        int lineH = (int) (OPTION_LINE_H * cs);
        int maxVisible = Math.max(1, availH / lineH);
        int maxOffset = Math.max(0, options.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
        int shown = Math.min(maxVisible, options.size() - scrollOffset);
        for (int i = 0; i < shown; i++) {
            int idx = scrollOffset + i;
            int oy = optTop + i * lineH;
            int textWidth = this.font.width("[" + options.get(idx) + "]");
            int color = isHit(mouseX, mouseY, optX, oy, optX + textWidth, lineH) ? OPTION_HOVER : OPTION_NORMAL;
            gg.drawString(this.font, "[" + options.get(idx) + "]", optX, oy, color, false);
        }
    }

    private boolean isHit(int mouseX, int mouseY, int x, int y, int right, int lineH) {
        return mouseX >= x && mouseX <= right && mouseY >= y && mouseY < y + lineH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(deltaY));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        float cs = Math.max(1.0F, Math.min(1.8F, this.width / 900F));
        int optX = (int) (this.width * 0.80F);
        int optTop = (int) (this.height * 0.74F);
        int availH = this.height - optTop - 8;
        int lineH = (int) (OPTION_LINE_H * cs);
        int maxVisible = Math.max(1, availH / lineH);
        int shown = Math.min(maxVisible, options.size() - scrollOffset);
        for (int i = 0; i < shown; i++) {
            int idx = scrollOffset + i;
            int oy = optTop + i * lineH;
            int textWidth = this.font.width("[" + options.get(idx) + "]");
            if (isHit((int) mouseX, (int) mouseY, optX, oy, optX + textWidth, lineH)) {
                if (idx < actions.size()) {
                    String action = actions.get(idx);
                    if ("leave".equals(action)) {
                        onClose();
                    } else if ("shop_open".equals(action)) {
                        // 先完整关闭对话框（setScreen(null)），再请求服务端打开商业UI，避免 LDLib 容器在被占用的 Modal screen 上打开被顶掉
                        onClose();
                        PacketDistributor.sendToServer(new DialogNpcOptionPacket(entityId, action));
                    } else if (action.startsWith("url:")) {
                        // 客户端直接打开链接，无需通知服务端
                        net.minecraft.Util.getPlatform().openUri(action.substring(4));
                    } else if (!action.isEmpty()) {
                        PacketDistributor.sendToServer(new DialogNpcOptionPacket(entityId, action));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String typedBody() {
        if (body.isEmpty()) {
            return "";
        }
        long ms = System.currentTimeMillis() - textStartMs;
        int chars = Math.max(0, (int) (ms / MS_PER_CHAR));
        return chars >= body.length() ? body : body.substring(0, chars);
    }

    /** 按 \n 切成逻辑段落，再对每段按像素宽度折行并合并，确保手动换行与自动折行均正确生效。 */
    private List<FormattedCharSequence> splitBody(String text, int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            lines.addAll(this.font.split(legacyComponent(para), maxWidth));
        }
        return lines;
    }

    /** 将含 § 颜色/格式码的旧式字符串解析为带样式的组件，供正文着色渲染。 */
    private static Component legacyComponent(String text) {
        MutableComponent root = Component.literal("");
        StringBuilder sb = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                ChatFormatting fmt = ChatFormatting.getByCode(text.charAt(i + 1));
                if (fmt != null) {
                    if (sb.length() > 0) {
                        root.append(Component.literal(sb.toString()).withStyle(style));
                        sb.setLength(0);
                    }
                    style = style.applyFormat(fmt);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        if (sb.length() > 0) {
            root.append(Component.literal(sb.toString()).withStyle(style));
        }
        return root;
    }

    private void renderModel(GuiGraphics gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        // 常驻单例渲染实体：不走每帧 new，经正常实体渲染管线(EMF/贴图/头不透明)，位置角度由矩阵固定、实体不tick不动
        if (DIALOG_STAND == null) {
            DIALOG_STAND = new DialogNpcEntity(DialogNpcEntity.TYPE, mc.level);
            DIALOG_STAND.setPos(0.0D, 0.0D, 0.0D);
            DIALOG_STAND.setCustomName(Component.literal("城市管家"));
        }
        DialogNpcEntity dummy = DIALOG_STAND;
        // 反向侧身：面朝右侧（180-30=150）
        dummy.setYRot(150.0F);
        dummy.setYBodyRot(150.0F);
        dummy.setYHeadRot(150.0F);
        // 用世界时间推进动画计时：让 jem/眼睛等实体动画平滑播放，位置由矩阵固定不动
        dummy.tickCount = (int) (mc.level.getGameTime());

        int modelX = (int) (this.width * 0.18F);
        int modelY = this.height;
        float cs2 = Math.max(1.0F, Math.min(1.8F, this.width / 900F));
        float size = 78.0F * cs2 * 1.1F;

        gg.pose().pushPose();
        gg.pose().translate(modelX, modelY, 50.0D);
        gg.pose().scale(size, size, -size);
        gg.pose().translate(0.0D, 0.6D, 0.0D);
        gg.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI));

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Quaternionf camOld = new Quaternionf(dispatcher.cameraOrientation());

        Lighting.setupForEntityInInventory();
        dispatcher.overrideCameraOrientation(new Quaternionf().rotateY((float) Math.PI));
        dispatcher.setRenderShadow(false);
        dispatcher.render(dummy, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, gg.pose(), gg.bufferSource(), 15728880);
        gg.flush();
        Lighting.setupFor3DItems();

        dispatcher.overrideCameraOrientation(camOld);
        dispatcher.setRenderShadow(true);
        gg.pose().popPose();
    }
}