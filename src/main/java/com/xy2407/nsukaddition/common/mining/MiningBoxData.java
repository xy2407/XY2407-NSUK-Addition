package com.xy2407.nsukaddition.common.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** 采矿控制箱的运行时数据模型，跟踪当前层高、工作进度与状态文本。 */
@SuppressWarnings("null")
public final class MiningBoxData {
    private final BlockPos boxPos;
    private int currentYLevel;
    private int workTicks;
    private boolean running;
    private String statusKey = "";
    private String statusText = "";
    private long updatedAt;

    public MiningBoxData(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
        this.currentYLevel = boxPos.getY();
    }

    public BlockPos boxPos() { return boxPos; }

    public int currentYLevel() { return currentYLevel; }
    public void setCurrentYLevel(int y) { this.currentYLevel = y; }

    public int workTicks() { return workTicks; }
    public void setWorkTicks(int ticks) { this.workTicks = Math.max(0, ticks); }

    public boolean running() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public String statusKey() { return statusKey; }
    public void setStatusKey(String key) { this.statusKey = key != null ? key : ""; }

    public String statusText() { return statusText; }
    public void setStatusText(String text) { this.statusText = text != null ? text : ""; }

    public long updatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = System.currentTimeMillis(); }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        tag.putInt("CurrentYLevel", currentYLevel);
        tag.putInt("WorkTicks", workTicks);
        tag.putBoolean("Running", running);
        tag.putString("StatusKey", statusKey);
        tag.putString("StatusText", statusText);
        tag.putLong("UpdatedAt", updatedAt);
        return tag;
    }

    public static MiningBoxData fromTag(CompoundTag tag) {
        MiningBoxData data = new MiningBoxData(BlockPos.of(tag.getLong("BoxPos")));
        data.currentYLevel = tag.contains("CurrentYLevel") ? tag.getInt("CurrentYLevel") : data.boxPos.getY();
        data.workTicks = Math.max(0, tag.getInt("WorkTicks"));
        data.running = tag.getBoolean("Running");
        data.statusKey = tag.getString("StatusKey");
        data.statusText = tag.getString("StatusText");
        data.updatedAt = tag.getLong("UpdatedAt");
        return data;
    }
}
