package com.wujiuye.asmbytecode.book.sixth.jdk;

import com.wujiuye.asmbytecode.book.fifth.util.ByteCodeUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实现JDK动态代理的核心类
 * 类似于JDK自带的java.lang.reflect.Proxy类
 */
public class MyProxy {

    /**
     * 代理处理器，用于处理代理对象的方法调用
     * 所有代理对象的方法调用都会被转发到这个处理器的invoke方法
     */
    protected InvocationHandler h;

    /**
     * 代理类计数器，用于生成唯一的代理类名称
     * 使用AtomicInteger保证线程安全
     */
    private final static AtomicInteger PROXY_CNT = new AtomicInteger(0);

    /**
     * 私有构造方法，防止直接实例化
     */
    private MyProxy() {

    }

    /**
     * 受保护的构造方法，供代理类调用
     * @param h 代理处理器，用于处理方法调用
     */
    protected MyProxy(InvocationHandler h) {
        this.h = h;
    }

    /**
     * 创建代理实例的静态方法
     * @param interfaces 要实现的接口数组
     * @param h 代理处理器，用于处理方法调用
     * @return 代理实例
     * @throws Exception 可能的异常，如类加载失败、构造方法调用失败等
     */
    public static Object newProxyInstance(Class<?>[] interfaces, InvocationHandler h) throws Exception {
        // 生成代理类名称，格式为com/sun/proxy/$ProxyN，其中N是自增的数字
        String proxyClassName = "com/sun/proxy/$Proxy" + PROXY_CNT.getAndIncrement();
        // 使用MyProxyFactory创建代理类的字节码
        byte[] proxyClassByteCode = MyProxyFactory.createProxyClass(proxyClassName, interfaces);
        // 使用自定义的类加载器加载代理类
        Class<?> proxyClass = ByteCodeUtils.loadClass(proxyClassName, proxyClassByteCode);
        // 获取代理类的构造方法，参数为InvocationHandler
        Constructor<?> constructor = proxyClass.getConstructor(InvocationHandler.class);
        // 创建代理实例并返回
        return constructor.newInstance(h);
    }

}
