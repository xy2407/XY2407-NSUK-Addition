package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket;

import java.util.function.Consumer;

/** 容器角色查询响应桥接，解耦公共包对客户端 ContainerRoleClientCache 的直接依赖。 */
public final class ContainerRoleBridge {

    private static Consumer<ContainerRoleResponsePacket> handler = p -> {};

    private ContainerRoleBridge() {}

    public static void install(Consumer<ContainerRoleResponsePacket> handler) {
        ContainerRoleBridge.handler = handler != null ? handler : p -> {};
    }

    public static void reset() {
        handler = p -> {};
    }

    public static void handle(ContainerRoleResponsePacket p) {
        handler.accept(p);
    }
}
