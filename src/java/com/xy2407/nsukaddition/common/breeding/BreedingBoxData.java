package com.xy2407.nsukaddition.common.breeding;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** 繁殖控制箱数据，存储位置、配方、运行状态与进度信息。 */
@SuppressWarnings("null")
public final class BreedingBoxData {
    private final BlockPos boxPos;
    private String buildingId = "";
    private String definitionId = "";
    private String selectedRecipeId = "";
    private boolean running;
    private String statusKey = "";
    private String statusText = "";
    private int progressTicks;
    private int cooldownTicks;
    private String workState = "";
    private long updatedAt;

    public BreedingBoxData(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
    }

    public BlockPos boxPos() {
        return boxPos;
    }

    public String buildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId != null ? buildingId : "";
    }

    public String definitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId != null ? definitionId : "";
    }

    public String selectedRecipeId() {
        return selectedRecipeId;
    }

    public void setSelectedRecipeId(String selectedRecipeId) {
        this.selectedRecipeId = selectedRecipeId != null ? selectedRecipeId : "";
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String statusKey() {
        return statusKey;
    }

    public void setStatusKey(String statusKey) {
        this.statusKey = statusKey != null ? statusKey : "";
    }

    public String statusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText != null ? statusText : "";
    }

    public int progressTicks() {
        return progressTicks;
    }

    public void setProgressTicks(int progressTicks) {
        this.progressTicks = Math.max(0, progressTicks);
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    public String workState() {
        return workState;
    }

    public void setWorkState(String workState) {
        this.workState = workState != null ? workState : "";
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        tag.putString("BuildingId", buildingId);
        tag.putString("DefinitionId", definitionId);
        tag.putString("SelectedRecipeId", selectedRecipeId);
        tag.putBoolean("Running", running);
        tag.putString("StatusKey", statusKey);
        tag.putString("StatusText", statusText);
        tag.putInt("ProgressTicks", progressTicks);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putString("WorkState", workState);
        tag.putLong("UpdatedAt", updatedAt);
        return tag;
    }

    public static BreedingBoxData fromTag(CompoundTag tag) {
        BreedingBoxData data = new BreedingBoxData(BlockPos.of(tag.getLong("BoxPos")));
        data.buildingId = tag.getString("BuildingId");
        data.definitionId = tag.getString("DefinitionId");
        data.selectedRecipeId = tag.getString("SelectedRecipeId");
        data.running = tag.getBoolean("Running");
        data.statusKey = tag.getString("StatusKey");
        data.statusText = tag.getString("StatusText");
        data.progressTicks = Math.max(0, tag.getInt("ProgressTicks"));
        data.cooldownTicks = Math.max(0, tag.getInt("CooldownTicks"));
        data.workState = tag.getString("WorkState");
        data.updatedAt = tag.getLong("UpdatedAt");
        return data;
    }
}
