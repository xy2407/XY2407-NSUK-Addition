package com.xy2407.nsukaddition.common.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

/**
 * 车万女仆扩展入口：注册餐厅服务员任务。
 * 该注解类仅由 maid 模组的 AnnotatedInstanceUtil 扫描实例化，maid 未安装时本类不会被加载。
 */
@LittleMaidExtension
public final class NsukMaidExtension implements ILittleMaid {

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new RestaurantMaidWaiterTask());
    }
}
