package com.xy2407.nsukaddition.client.container;

import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket;
import net.minecraft.core.BlockPos;

public final class ContainerRoleClientCache {

    private static volatile ContainerRoleResponsePacket cached;
    private static volatile BlockPos lastQueriedPos;
    private static long lastUpdateTime;

    private ContainerRoleClientCache() {}

    public static void setResponse(ContainerRoleResponsePacket p) {
        cached = p;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static ContainerRoleResponsePacket getResponse() {
        long elapsed = System.currentTimeMillis() - lastUpdateTime;
        if (elapsed > 3000) {
            cached = null;
            lastQueriedPos = null;
        }
        return cached;
    }

    public static void setLastQueriedPos(BlockPos pos) {
        lastQueriedPos = pos;
    }

    public static BlockPos lastQueriedPos() {
        return lastQueriedPos;
    }

    public static void clearAll() {
        cached = null;
        lastQueriedPos = null;
        lastUpdateTime = 0;
    }
}
