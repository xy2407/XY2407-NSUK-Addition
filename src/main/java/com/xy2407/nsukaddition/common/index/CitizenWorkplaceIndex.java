package com.xy2407.nsukaddition.common.index;

import common.cn.kafei.simukraft.citizen.CitizenData;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 workplaceId → citizenUuid 反向索引（按维度分片），使岗位查询从 O(N) 全量扫描变为 O(1) 索引查找。
 * 索引独立存放在普通类中，Mixin 只做调用，避免 Mixin 规范对非 private 静态方法的限制。
 */
public final class CitizenWorkplaceIndex {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<UUID, UUID>> WORKPLACE_INDEX = new ConcurrentHashMap<>();
    private static volatile boolean indexReady = false;
    private static final String DEFAULT_DIM = "minecraft:overworld";

    private CitizenWorkplaceIndex() {
    }

    public static UUID indexGet(String dimension, UUID workplaceId) {
        if (dimension == null || workplaceId == null) {
            return null;
        }
        ConcurrentHashMap<UUID, UUID> bucket = WORKPLACE_INDEX.get(dimension);
        return bucket == null ? null : bucket.get(workplaceId);
    }

    public static void indexRemove(String dimension, UUID workplaceId, UUID citizenUuid) {
        if (workplaceId == null || citizenUuid == null) {
            return;
        }
        String dim = dimension == null || dimension.isBlank() ? DEFAULT_DIM : dimension;
        ConcurrentHashMap<UUID, UUID> bucket = WORKPLACE_INDEX.get(dim);
        if (bucket != null) {
            bucket.remove(workplaceId, citizenUuid);
        }
    }

    public static void indexPut(String dimension, UUID workplaceId, UUID citizenUuid) {
        if (workplaceId == null || citizenUuid == null) {
            return;
        }
        String dim = dimension == null || dimension.isBlank() ? DEFAULT_DIM : dimension;
        WORKPLACE_INDEX.computeIfAbsent(dim, ignored -> new ConcurrentHashMap<>()).put(workplaceId, citizenUuid);
    }

    public static void rebuildIndex(Collection<CitizenData> citizens) {
        reset();
        if (citizens != null) {
            for (CitizenData citizen : citizens) {
                if (citizen != null && citizen.workplaceId() != null) {
                    indexPut(citizen.dimensionId(), citizen.workplaceId(), citizen.uuid());
                }
            }
        }
        indexReady = true;
    }

    public static void reset() {
        WORKPLACE_INDEX.clear();
        indexReady = false;
    }

    public static boolean isIndexReady() {
        return indexReady;
    }
}