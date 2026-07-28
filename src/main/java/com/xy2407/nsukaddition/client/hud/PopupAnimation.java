package com.xy2407.nsukaddition.client.hud;

import net.minecraft.Util;

/** 侧边栏弹出动画状态机，管理展开/收起的进度过渡。 */
public final class PopupAnimation {

    public enum State { IDLE, OPENING, SHOWN, CLOSING }

    private static final long DURATION_MS = 300L;

    private State state = State.IDLE;
    private float progress;
    private long lastFrameMs;

    public void toggle() {
        switch (state) {
            case IDLE, CLOSING -> { state = State.OPENING; lastFrameMs = Util.getMillis(); }
            case OPENING, SHOWN -> { state = State.CLOSING; lastFrameMs = Util.getMillis(); }
        }
    }

    public void updateFrame() {
        long nowMs = Util.getMillis();
        if (state == State.OPENING || state == State.CLOSING) {
            float delta = (float) (nowMs - lastFrameMs) / DURATION_MS;
            if (state == State.OPENING) {
                progress += delta;
                if (progress >= 1f) { progress = 1f; state = State.SHOWN; }
            } else {
                progress -= delta;
                if (progress <= 0f) { progress = 0f; state = State.IDLE; }
            }
        }
        lastFrameMs = nowMs;
    }

    public float progress() { return progress; }
    public boolean visible() { return state != State.IDLE; }
}
