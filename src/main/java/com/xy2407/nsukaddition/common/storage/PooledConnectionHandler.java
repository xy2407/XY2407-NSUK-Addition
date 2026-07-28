package com.xy2407.nsukaddition.common.storage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

/** Connection动态代理，close()被拦截为空操作，实现连接复用。 */
public final class PooledConnectionHandler implements InvocationHandler {

    private final Connection real;

    private PooledConnectionHandler(Connection real) {
        this.real = real;
    }

    /** 创建Connection代理，close()不真正关闭。 */
    public static Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new PooledConnectionHandler(real)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("close")) {
            return null;
        }
        return method.invoke(real, args);
    }
}
