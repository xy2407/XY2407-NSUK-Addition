package com.xy2407.nsukaddition.common.block.entity;

import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 外贸控制方块实体，存储运行状态、绑定工人和当前配方等数据。 */
@SuppressWarnings("null")
public final class ForeignTradeControlBoxBlockEntity extends BlockEntity {

    private boolean running;
    private String statusKey;
    private String statusText;
    private String selectedTradeId;

    public ForeignTradeControlBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FOREIGN_TRADE_CONTROL_BOX_ENTITY.get(), pos, state);
        this.running = false;
        this.statusKey = "gui.xy2407_nsuk_addition.foreign_trade.status.idle";
        this.statusText = "";
        this.selectedTradeId = "";
    }

    public boolean running() { return running; }
    public void setRunning(boolean v) { running = v; setChanged(); }

    public String statusKey() { return statusKey; }
    public void setStatusKey(String v) { statusKey = v; setChanged(); }

    public String statusText() { return statusText; }
    public void setStatusText(String v) { statusText = v; setChanged(); }

    public String selectedTradeId() { return selectedTradeId; }
    public void setSelectedTradeId(String v) { selectedTradeId = v; setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Running", running);
        tag.putString("StatusKey", statusKey);
        tag.putString("StatusText", statusText);
        tag.putString("SelectedTradeId", selectedTradeId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        running = tag.getBoolean("Running");
        statusKey = tag.contains("StatusKey") ? tag.getString("StatusKey") : "gui.xy2407_nsuk_addition.foreign_trade.status.idle";
        statusText = tag.contains("StatusText") ? tag.getString("StatusText") : "";
        selectedTradeId = tag.contains("SelectedTradeId") ? tag.getString("SelectedTradeId") : "";
    }
}
