package com.xy2407.nsukaddition.client.container;

import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket;
import net.minecraft.core.BlockPos;

/** 容器角色查询客户端缓存，存储服务端返回的角色信息和上次查询位置。 */
public final class ContainerRoleClientCache {

    private static final long RESPONSE_TIMEOUT_MS = 3000;

    private static ContainerRoleResponsePacket cached;
    private static BlockPos lastQueriedPos;
    private static long lastUpdateTime;

    private ContainerRoleClientCache() {}

    public static void setResponse(ContainerRoleResponsePacket p) {
        cached = p;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static ContainerRoleResponsePacket getResponse() {
        if (cached != null && System.currentTimeMillis() - lastUpdateTime > RESPONSE_TIMEOUT_MS) {
            cached = null;
        }
        return cached;
    }

    public static void setLastQueriedPos(BlockPos pos) {
        lastQueriedPos = pos;
    }

    public static BlockPos lastQueriedPos() {
        return lastQueriedPos;
    }

    public static void clearResponse() {
        cached = null;
        lastUpdateTime = 0;
    }

    public static void clearAll() {
        cached = null;
        lastQueriedPos = null;
        lastUpdateTime = 0;
    }
}
