package com.xy2407.nsukaddition.common.foreigntrade;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** 外贸控制箱服务层，处理启动/停止、雇佣/解雇等业务逻辑。 */
@SuppressWarnings("null")
public final class ForeignTradeControlBoxService {

    private ForeignTradeControlBoxService() {}

    public static ForeignTradeBoxView buildView(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return ForeignTradeBoxView.empty(pos);

        CitizenData worker = CitizenEmploymentService.findAssigned(
                level, ForeignTradeConstants.HIRE_SOURCE_TYPE, ForeignTradeConstants.HIRE_ROLE, pos).orElse(null);

        ForeignTradeSqliteStorage.ForeignTradeBoxData data = ForeignTradeSqliteStorage.load(level, pos);
        boolean running = data != null && data.running();
        String statusKey = data != null ? data.statusKey() : "gui.xy2407_nsuk_addition.foreign_trade.status.idle";
        String statusText = data != null ? data.statusText() : "";
        String selectedTradeId = data != null ? data.selectedTradeId() : "";

        boolean hasWorker = worker != null && !worker.dead();
        UUID workerId = hasWorker ? worker.uuid() : null;
        String workerName = hasWorker ? worker.name() : "";

        return new ForeignTradeBoxView(pos, running, statusKey, statusText, selectedTradeId,
                hasWorker, workerId, workerName);
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

    public static void hireWorker(ServerLevel level, UUID citizenId, BlockPos pos) {
        if (level == null || citizenId == null || pos == null) return;
        CitizenEmploymentService.hireForSource(level, citizenId,
                ForeignTradeConstants.HIRE_SOURCE_TYPE, ForeignTradeConstants.HIRE_ROLE, pos,
                "gui.xy2407_nsuk_addition.foreign_trade.status.running");
    }

    public static void fireWorker(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(ForeignTradeConstants.HIRE_SOURCE_TYPE, ForeignTradeConstants.HIRE_ROLE, pos),
                ForeignTradeConstants.HIRE_SOURCE_TYPE, ForeignTradeConstants.HIRE_ROLE, pos,
                "foreign_trade_fired");
        ForeignTradeSqliteStorage.save(level, pos, false,
                "gui.xy2407_nsuk_addition.foreign_trade.status.worker_fired", "", "");
    }

    public static void onRemoved(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        fireWorker(level, pos);
        ForeignTradeSqliteStorage.delete(level, pos);
    }
}
