package com.xy2407.nsukaddition.server.building;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.BuildingTaskQueueStorage;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/** 建筑盒多任务排队服务：按盒维护 FIFO 队列，盒空闲时自动开工队首任务，任务带进度可续建。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class BuildingTaskQueueService {
    private static final ConcurrentMap<String, ConcurrentMap<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>>> QUEUES = new ConcurrentHashMap<>();
    private static final Set<String> RECOVERED = ConcurrentHashMap.newKeySet();

    private BuildingTaskQueueService() {
    }

    private static ConcurrentMap<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>> queueMap(ServerLevel level) {
        return QUEUES.computeIfAbsent(level.dimension().location().toString(), k -> new ConcurrentHashMap<>());
    }

    public static List<BuildingTaskData> runningTasks(ServerLevel level) {
        List<BuildingTaskData> result = new ArrayList<>();
        if (level == null) {
            return result;
        }
        try {
            Class<?> service = BuilderConstructionService.class;
            Field runtimesField = service.getDeclaredField("LEVEL_RUNTIMES");
            runtimesField.setAccessible(true);
            Object runtimes = runtimesField.get(null);
            String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
            Object runtime = ((Map<?, ?>) runtimes).get(key);
            if (runtime == null) {
                return result;
            }
            Field tasksField = runtime.getClass().getDeclaredField("tasksByCitizen");
            tasksField.setAccessible(true);
            for (Object taskRuntime : ((Map<?, ?>) tasksField.get(runtime)).values()) {
                Field taskField = taskRuntime.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                BuildingTaskData running = (BuildingTaskData) taskField.get(taskRuntime);
                if (running != null) {
                    result.add(running);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueService: 读取运行态任务失败", e);
        }
        return result;
    }

    public static BuildingTaskData findRunningTask(ServerLevel level, UUID taskId) {
        if (taskId == null) {
            return null;
        }
        for (BuildingTaskData t : runningTasks(level)) {
            if (taskId.equals(t.taskId())) {
                return t;
            }
        }
        return null;
    }

    public static boolean hasRunningTask(ServerLevel level, BlockPos buildBoxPos) {
        if (buildBoxPos == null) {
            return false;
        }
        try {
            Class<?> service = BuilderConstructionService.class;
            Field runtimesField = service.getDeclaredField("LEVEL_RUNTIMES");
            runtimesField.setAccessible(true);
            Object runtimes = runtimesField.get(null);
            String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
            Object runtime = ((Map<?, ?>) runtimes).get(key);
            if (runtime == null) {
                return false;
            }
            Field tasksField = runtime.getClass().getDeclaredField("tasksByCitizen");
            tasksField.setAccessible(true);
            for (Object taskRuntime : ((Map<?, ?>) tasksField.get(runtime)).values()) {
                Field taskField = taskRuntime.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                BuildingTaskData running = (BuildingTaskData) taskField.get(taskRuntime);
                if (running != null && buildBoxPos.equals(running.buildBoxPos())) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            UUID worker = CitizenService.findAssignedCitizen(level,
                    CitizenEmploymentService.workplaceId("build_box", "builder", buildBoxPos));
            if (worker == null) {
                return false;
            }
            BlockPos taskBox = BuilderConstructionService.findBuildBoxPos(level, worker);
            return taskBox != null && buildBoxPos.equals(taskBox);
        }
    }

    public static void enqueue(ServerLevel level, BuildingTaskData task) {
        if (level == null || task == null) {
            return;
        }
        if (task.citizenId() != null
                && BuildingTaskQueueStorage.countByCitizen(level, task.citizenId()) >= 256) {
            NsukAddition.LOGGER.warn("BuildingTaskQueueService: 建筑师 {} 任务已达上限(256),拒绝入队 {}", task.citizenId(), task.taskId());
            return;
        }
        ConcurrentLinkedDeque<BuildingTaskData> queue = queueMap(level)
                .computeIfAbsent(task.buildBoxPos().immutable(), k -> new ConcurrentLinkedDeque<>());
        queue.addLast(task);
        BuildingTaskQueueStorage.save(level, task);
        if (task.cityId() != null && BuildTaskTrackedState.getTrackedTask(level, task.cityId()) == null) {
            ensureTracking(level, task.cityId());
        }
    }

    private static void enqueueMemory(ServerLevel level, BuildingTaskData task) {
        if (level == null || task == null) {
            return;
        }
        queueMap(level).computeIfAbsent(task.buildBoxPos().immutable(), k -> new ConcurrentLinkedDeque<>())
                .addLast(task);
    }

    public static void ensureTracking(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return;
        }
        if (BuildTaskTrackedState.getTrackedTask(level, cityId) != null) {
            return;
        }
        List<BuildingTaskData> queued = BuildingTaskQueueStorage.loadQueuedByCity(level, cityId);
        if (queued.isEmpty()) {
            return;
        }
        BuildingTaskData next = queued.get(0);
        BuildTaskTrackedState.setTrackedTask(level, cityId, next.taskId());
        removeQueuedByTaskId(level, next.taskId());
        startTaskIfIdle(level, next.withStatus(BuildingTaskStatus.BUILDING));
    }

    public static void onTaskFinished(ServerLevel level, UUID cityId, UUID finishedTaskId) {
        if (level == null || cityId == null || finishedTaskId == null) {
            return;
        }
        UUID tracked = BuildTaskTrackedState.getTrackedTask(level, cityId);
        if (tracked != null && tracked.equals(finishedTaskId)) {
            BuildTaskTrackedState.clearTrackedTask(level, cityId);
            ensureTracking(level, cityId);
        }
    }

    public static void onCitizenTaskEnded(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        Optional<CitizenData> citizenOpt = CitizenService.findCitizen(level, citizenId);
        if (citizenOpt.isEmpty() || citizenOpt.get().cityId() == null) {
            return;
        }
        UUID cityId = citizenOpt.get().cityId();
        UUID tracked = BuildTaskTrackedState.getTrackedTask(level, cityId);
        if (tracked == null) {
            return;
        }
        BuildingTaskData t = BuildingTaskQueueStorage.loadByTaskId(level, tracked);
        if (t == null) {
            t = findRunningTask(level, tracked);
        }
        if (t == null || isFinished(t)) {
            BuildTaskTrackedState.clearTrackedTask(level, cityId);
            ensureTracking(level, cityId);
        }
    }

    public static void switchTrack(ServerLevel level, UUID cityId, UUID newTaskId) {
        if (level == null || cityId == null || newTaskId == null) {
            return;
        }
        if (newTaskId.equals(BuildTaskTrackedState.getTrackedTask(level, cityId))) {
            return;
        }
        UUID old = BuildTaskTrackedState.getTrackedTask(level, cityId);
        if (old != null) {
            BuildingTaskData active = BuildingTaskQueueStorage.loadByTaskId(level, old);
            if (active == null) {
                active = findRunningTask(level, old);
            }
            if (active != null && !isFinished(active)) {
                BuilderTaskControl.stopRuntime(level, active.citizenId());
                BuildingTaskData requeued = active.withStatus(BuildingTaskStatus.QUEUED);
                BuildingTaskQueueStorage.save(level, requeued);
                enqueueMemory(level, requeued);
            }
        }
        BuildTaskTrackedState.setTrackedTask(level, cityId, newTaskId);
        BuildingTaskData tracked = BuildingTaskQueueStorage.loadByTaskId(level, newTaskId);
        if (tracked == null) {
            tracked = findRunningTask(level, newTaskId);
        }
        if (tracked == null || isFinished(tracked)) {
            return;
        }
        if (!cityId.equals(tracked.cityId())) {
            return;
        }
        removeQueuedByTaskId(level, newTaskId);
        if (BuildTaskTrackedState.isPaused(level, cityId, tracked.citizenId())) {
            BuildTaskTrackedState.setResumed(level, cityId, tracked.citizenId());
        }
        BuildingTaskQueueStorage.deleteByTaskId(level, newTaskId);
        startTaskIfIdle(level, tracked.withStatus(BuildingTaskStatus.BUILDING));
    }

    private static void startTaskIfIdle(ServerLevel level, BuildingTaskData task) {
        if (task == null || task.citizenId() == null) {
            return;
        }
        if (hasRunningTask(level, task.buildBoxPos())) {
            return;
        }
        if (!level.getBlockState(task.buildBoxPos()).is(ModBlocks.BUILD_BOX.get())) {
            return;
        }
        UUID worker = CitizenService.findAssignedCitizen(level,
                CitizenEmploymentService.workplaceId("build_box", "builder", task.buildBoxPos()));
        if (worker == null) {
            return;
        }
        BuildingTaskData toStart = worker.equals(task.citizenId()) ? task : reAssignCitizen(task, worker);
        BuilderConstructionService.startTask(level, toStart.withStatus(BuildingTaskStatus.BUILDING));
        BuildingTaskQueueStorage.flushRunning(level, toStart.citizenId());
    }

    private static boolean isFinished(BuildingTaskData t) {
        if (t == null) {
            return true;
        }
        String st = t.status();
        return "completed".equalsIgnoreCase(st) || "interrupted".equalsIgnoreCase(st);
    }

    public static void removeQueued(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        ConcurrentMap<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>> dimQueues = QUEUES
                .get(level.dimension().location().toString());
        if (dimQueues == null) {
            return;
        }
        for (ConcurrentLinkedDeque<BuildingTaskData> queue : dimQueues.values()) {
            queue.removeIf(task -> citizenId.equals(task.citizenId()));
        }
        BuildingTaskQueueStorage.deleteByCitizen(level, citizenId);
    }

    public static void removeQueuedByTaskId(ServerLevel level, UUID taskId) {
        if (level == null || taskId == null) {
            return;
        }
        ConcurrentMap<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>> dimQueues = QUEUES
                .get(level.dimension().location().toString());
        if (dimQueues == null) {
            return;
        }
        for (ConcurrentLinkedDeque<BuildingTaskData> queue : dimQueues.values()) {
            queue.removeIf(task -> taskId.equals(task.taskId()));
        }
        BuildingTaskQueueStorage.deleteByTaskId(level, taskId);
    }

    public static void clearBoxQueue(ServerLevel level, BlockPos buildBoxPos) {
        if (level == null || buildBoxPos == null) {
            return;
        }
        ConcurrentLinkedDeque<BuildingTaskData> queue = queueMap(level).remove(buildBoxPos.immutable());
        if (queue == null) {
            return;
        }
        BuildingTaskQueueStorage.deleteByBoxPos(level, buildBoxPos);
    }

    private static final java.util.concurrent.ConcurrentMap<String, Long> LAST_RECOVER_CHECK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long RECOVER_CHECK_INTERVAL = 40L;

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RECOVERED.clear();
        LAST_RECOVER_CHECK.clear();
    }

    public static void maybeRecoverQueued(ServerLevel level) {
        if (level == null) {
            return;
        }
        String key = level.dimension().location().toString();
        if (RECOVERED.contains(key)) {
            return;
        }
        Long last = LAST_RECOVER_CHECK.get(key);
        long now = level.getGameTime();
        if (last != null && now - last < RECOVER_CHECK_INTERVAL) {
            return;
        }
        LAST_RECOVER_CHECK.put(key, now);
        List<BuildingTaskData> queued = loadQueued(level);
        if (queued.isEmpty()) {
            RECOVERED.add(key);
            return;
        }
        boolean hydrated = false;
        for (BuildingTaskData t : queued) {
            if (BuilderConstructionService.findBuildBoxPos(level, t.citizenId()) != null) {
                hydrated = true;
                break;
            }
        }
        if (!hydrated && level.getGameTime() < 200L) {
            return;
        }
        if (!RECOVERED.add(key)) {
            return;
        }
        for (BuildingTaskData t : queued) {
            removeRunningIfTask(level, t.citizenId(), t.taskId());
            enqueue(level, t);
        }
    }

    private static void removeRunningIfTask(ServerLevel level, UUID citizenId, UUID taskId) {
        try {
            Class<?> service = BuilderConstructionService.class;
            Field runtimesField = service.getDeclaredField("LEVEL_RUNTIMES");
            runtimesField.setAccessible(true);
            Object runtimes = runtimesField.get(null);
            String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
            Object runtime = ((Map<?, ?>) runtimes).get(key);
            if (runtime == null) {
                return;
            }
            Field tasksField = runtime.getClass().getDeclaredField("tasksByCitizen");
            tasksField.setAccessible(true);
            Map<?, ?> tasks = (Map<?, ?>) tasksField.get(runtime);
            Object taskRuntime = tasks.get(citizenId);
            if (taskRuntime == null) {
                return;
            }
            Field taskField = taskRuntime.getClass().getDeclaredField("task");
            taskField.setAccessible(true);
            BuildingTaskData running = (BuildingTaskData) taskField.get(taskRuntime);
            if (running != null && taskId.equals(running.taskId())) {
                tasks.remove(citizenId);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static List<BuildingTaskData> loadQueued(ServerLevel level) {
        List<BuildingTaskData> queued = new ArrayList<>();
        if (level == null) {
            return queued;
        }
        for (BuildingTaskData t : BuildingTaskQueueStorage.loadAll(level)) {
            if (BuildingTaskStatus.from(t.status()) == BuildingTaskStatus.QUEUED) {
                queued.add(t);
            }
        }
        return queued;
    }

    public static List<BuildingTaskData> allQueued(ServerLevel level) {
        List<BuildingTaskData> result = new ArrayList<>();
        if (level == null) {
            return result;
        }
        ConcurrentMap<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>> dimQueues = QUEUES
                .get(level.dimension().location().toString());
        if (dimQueues == null) {
            return result;
        }
        for (ConcurrentLinkedDeque<BuildingTaskData> queue : dimQueues.values()) {
            result.addAll(queue);
        }
        return result;
    }

    public static void tickQueues(ServerLevel level) {
        if (level == null) {
            return;
        }
        ConcurrentMap<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>> dimQueues = QUEUES
                .get(level.dimension().location().toString());
        if (dimQueues == null || dimQueues.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, ConcurrentLinkedDeque<BuildingTaskData>> entry : dimQueues.entrySet()) {
            BlockPos box = entry.getKey();
            ConcurrentLinkedDeque<BuildingTaskData> queue = entry.getValue();
            if (queue.isEmpty()) {
                continue;
            }
            if (hasRunningTask(level, box)) {
                continue;
            }
            if (!level.getBlockState(box).is(ModBlocks.BUILD_BOX.get())) {
                clearBoxQueue(level, box);
                continue;
            }
            UUID worker = CitizenService.findAssignedCitizen(level,
                    CitizenEmploymentService.workplaceId("build_box", "builder", box));
            if (worker == null) {
                continue;
            }
            UUID cityId = queue.peek().cityId();
            if (cityId == null) {
                continue;
            }
            UUID tracked = BuildTaskTrackedState.getTrackedTask(level, cityId);
            BuildingTaskData next = null;
            if (tracked != null) {
                for (BuildingTaskData t : queue) {
                    if (t.taskId().equals(tracked)) {
                        next = t;
                        break;
                    }
                }
            }
            if (next == null) {
                if (tracked == null) {
                    next = queue.peek();
                    BuildTaskTrackedState.setTrackedTask(level, cityId, next.taskId());
                } else {
                    BuildingTaskData dbTask = BuildingTaskQueueStorage.loadByTaskId(level, tracked);
                    if (dbTask == null) {
                        dbTask = findRunningTask(level, tracked);
                    }
                    if (dbTask == null || isFinished(dbTask)) {
                        BuildTaskTrackedState.clearTrackedTask(level, cityId);
                        next = queue.peek();
                        if (next == null) {
                            continue;
                        }
                        BuildTaskTrackedState.setTrackedTask(level, cityId, next.taskId());
                    } else if (BuildTaskTrackedState.isPaused(level, cityId, dbTask.citizenId())) {
                        continue;
                    } else if (findRunningTask(level, tracked) != null) {
                        continue;
                    } else {
                        BuildingTaskQueueStorage.deleteByTaskId(level, dbTask.taskId());
                        BuilderConstructionService.startTask(level, dbTask.withStatus(BuildingTaskStatus.BUILDING));
                        BuildingTaskQueueStorage.flushRunning(level, dbTask.citizenId());
                        continue;
                    }
                }
            }
            queue.remove(next);
            if (!worker.equals(next.citizenId())) {
                next = reAssignCitizen(next, worker);
            }
            BuildingTaskQueueStorage.deleteByTaskId(level, next.taskId());
            BuilderConstructionService.startTask(level, next.withStatus(BuildingTaskStatus.BUILDING));
            BuildingTaskQueueStorage.flushRunning(level, next.citizenId());
        }
    }

    private static BuildingTaskData reAssignCitizen(BuildingTaskData task, UUID worker) {
        return new BuildingTaskData(task.taskId(), worker, task.cityId(), task.dimensionId(), task.buildBoxPos(),
                task.category(), task.buildingFileName(), task.displayName(), task.amount(), task.structureFileName(),
                task.origin(), task.rotationDegrees(), task.currentBlockIndex(), task.totalBlocks(), task.status(),
                task.createdAt(), task.updatedAt(), task.poiDefinitions(), task.replaceWithAir());
    }
}