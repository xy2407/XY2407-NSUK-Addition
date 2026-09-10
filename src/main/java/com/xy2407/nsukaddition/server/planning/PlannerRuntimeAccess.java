package com.xy2407.nsukaddition.server.planning;

import common.cn.kafei.simukraft.planner.PlannerWorkService;
import common.cn.kafei.simukraft.planner.PlanningTaskData;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 规划任务运行时访问器：通过反射读取 PlannerWorkService 内存运行态的规划任务(镜像建筑任务读取方式)。
 */
public final class PlannerRuntimeAccess {

    private PlannerRuntimeAccess() {
    }

    public static List<PlanningTaskData> runningTasks(ServerLevel level) {
        List<PlanningTaskData> result = new ArrayList<>();
        if (level == null) {
            return result;
        }
        try {
            Field runtimesField = PlannerWorkService.class.getDeclaredField("LEVEL_RUNTIMES");
            runtimesField.setAccessible(true);
            Object runtimes = runtimesField.get(null);
            String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
            Object runtime = ((Map<?, ?>) runtimes).get(key);
            if (runtime == null) {
                return result;
            }
            Field tasksField = runtime.getClass().getDeclaredField("tasks");
            tasksField.setAccessible(true);
            for (Object taskRuntime : ((Map<?, ?>) tasksField.get(runtime)).values()) {
                Field taskField = taskRuntime.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                PlanningTaskData running = (PlanningTaskData) taskField.get(taskRuntime);
                if (running != null) {
                    result.add(running);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return result;
    }
}