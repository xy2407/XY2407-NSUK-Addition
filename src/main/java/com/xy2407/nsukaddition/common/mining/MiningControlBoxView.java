package com.xy2407.nsukaddition.common.mining;

import net.minecraft.core.BlockPos;

/** 挖矿控制盒的视图数据记录，用于向客户端同步展示状态。 */
public record MiningControlBoxView(
        BlockPos boxPos,
        boolean hasWorker,
        String workerName,
        int currentYLevel,
        boolean running,
        int workTicks,
        int maxWorkTicks,
        String statusKey,
        String statusText
) {}
