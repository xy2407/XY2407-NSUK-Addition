package com.xy2407.nsukaddition.common.foreigntrade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** 外贸控制箱服务层，处理外贸盒子状态与拆除逻辑。 */
@SuppressWarnings("null")
public final class ForeignTradeControlBoxService {

    private ForeignTradeControlBoxService() {}

    public static ForeignTradeBoxView buildView(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return ForeignTradeBoxView.empty(pos);

        ForeignTradeSqliteStorage.ForeignTradeBoxData data = ForeignTradeSqliteStorage.load(level, pos);
        boolean running = data != null && data.running();
        String statusKey = data != null ? data.statusKey() : "gui.xy2407_nsuk_addition.foreign_trade.status.idle";
        String statusText = data != null ? data.statusText() : "";
        String selectedTradeId = data != null ? data.selectedTradeId() : "";

        return new ForeignTradeBoxView(pos, running, statusKey, statusText, selectedTradeId);
    }

    public static void toggleRunning(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        ForeignTradeSqliteStorage.ForeignTradeBoxData data = ForeignTradeSqliteStorage.load(level, pos);
        boolean wasRunning = data != null && data.running();
        boolean nowRunning = !wasRunning;
        String statusKey = nowRunning
                ? "gui.xy2407_nsuk_addition.foreign_trade.status.running"
                : "gui.xy2407_nsuk_addition.foreign_trade.status.paused";
        ForeignTradeSqliteStorage.save(level, pos, nowRunning, statusKey, "", data != null ? data.selectedTradeId() : "");
    }

    public static void selectTrade(ServerLevel level, BlockPos pos, String tradeId) {
        if (level == null || pos == null) return;
        ForeignTradeSqliteStorage.ForeignTradeBoxData data = ForeignTradeSqliteStorage.load(level, pos);
        boolean running = data != null && data.running();
        String statusKey = data != null ? data.statusKey() : "gui.xy2407_nsuk_addition.foreign_trade.status.idle";
        ForeignTradeSqliteStorage.save(level, pos, running,
                "gui.xy2407_nsuk_addition.foreign_trade.status.trade_selected", "", tradeId);
    }

    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        ForeignTradeSqliteStorage.delete(level, pos);
    }
}
