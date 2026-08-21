package com.xy2407.nsukaddition.server.rts;

import common.cn.kafei.simukraft.entity.CitizenEntity;

/** RTS 任务接口，可扩展支持移动、攻击、建造等多种任务类型。 */
public interface RtsTask {

    /** 每 tick 执行任务逻辑。 */
    void tick(CitizenEntity citizen);

    /** 判断任务是否完成。 */
    boolean isComplete();

    /** 任务被取消时调用（市民脱离选中或被替换任务）。 */
    void onCancel();
}
