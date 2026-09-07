package com.xy2407.nsukaddition.common.network.clientbound;

import java.util.List;

/** 对话框打开桥接，解耦公共包对客户端 DialogScreen 的直接依赖。 */
public final class DialogNpcOpenBridge {

    public interface Handler {
        void open(String title, String body, List<String> options, List<String> actions, int dialogEntityId);
    }

    private static Handler handler = (title, body, options, actions, dialogEntityId) -> {};

    private DialogNpcOpenBridge() {}

    public static void install(Handler handler) {
        DialogNpcOpenBridge.handler = handler != null ? handler : (title, body, options, actions, dialogEntityId) -> {};
    }

    public static void reset() {
        handler = (title, body, options, actions, dialogEntityId) -> {};
    }

    public static void open(String title, String body, List<String> options, List<String> actions, int dialogEntityId) {
        handler.open(title, body, options, actions, dialogEntityId);
    }
}