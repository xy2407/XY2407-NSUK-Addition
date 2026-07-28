package com.xy2407.nsukaddition.client.hud;

/** 根据屏幕尺寸和动画进度计算侧边栏弹窗的位置与透明度。 */
public final class PopupLayoutManager {

    public record Rect(int x, int y, int width, int height, float alpha) {
    }

    private static final int MIN_WIDTH = 180;

    private static final int MIN_HEIGHT = 200;

    private static final float VERTICAL_MARGIN_RATIO = 0.10f;

    private PopupLayoutManager() {
    }

    public static Rect compute(int screenW, int screenH, float animProgress) {
        int width = Math.max(MIN_WIDTH, screenW / 3);
        int marginV = Math.max(4, (int) (screenH * VERTICAL_MARGIN_RATIO));
        int height = Math.max(MIN_HEIGHT, screenH - marginV * 2);
        int x = 0;
        int y = marginV;

        int offsetX = (int) ((1f - animProgress) * (-width));

        float alpha = animProgress;
        return new Rect(x + offsetX, y, width, height, alpha);
    }
}
