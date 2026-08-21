package com.xy2407.nsukaddition.common.foreigntrade;

import net.minecraft.core.BlockPos;

/** 外贸控制箱界面视图数据，用于服务端→客户端的网络传输。 */
public record ForeignTradeBoxView(
        BlockPos boxPos,
        boolean running,
        String statusKey,
        String statusText,
        String selectedTradeId
) {
    public static ForeignTradeBoxView empty(BlockPos pos) {
        return new ForeignTradeBoxView(pos, false,
                "gui.xy2407_nsuk_addition.foreign_trade.status.idle", "", "");
    }
}
