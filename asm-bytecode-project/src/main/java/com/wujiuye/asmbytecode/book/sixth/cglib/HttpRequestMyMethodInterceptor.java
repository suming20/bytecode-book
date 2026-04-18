package com.wujiuye.asmbytecode.book.sixth.cglib;

import java.lang.reflect.Method;

/**
 * HTTP请求方法拦截器 - 自定义的CGLIB方法拦截器实现
 * 
 * 【类的作用】
 * 这个类实现了MyMethodInterceptor接口，用于拦截HTTP请求方法的调用。
 * 主要功能：统计方法执行耗时。
 * 
 * 【使用场景】
 * 性能监控：记录每个HTTP请求方法的执行时间，
 * 帮助发现慢方法，优化性能。
 * 
 * 【执行流程】
 * 1. 记录开始时间
 * 2. 调用原始方法（通过代理方法）
 * 3. 计算耗时并打印
 * 4. 返回结果
 */
public class HttpRequestMyMethodInterceptor implements MyMethodInterceptor {

    /**
     * 方法拦截实现
     * 
     * 【参数说明】
     * @param obj 代理对象实例（HttpRequestTemplateImpl的子类对象）
     * @param methodProxy 代理方法的Method对象（如doGet_0方法）
     *                    调用methodProxy.invoke(obj, objects)会执行父类的原始方法
     * @param methodName 原始方法名（如"doGet"）
     * @param objects 方法参数数组（如[HttpRequest对象]）
     * 
     * @return 原始方法的返回值
     * 
     * 【执行流程】
     * 1. 记录开始时间（System.currentTimeMillis()）
     * 2. 在try块中调用原始方法
     * 3. 在finally块中计算并打印耗时
     * 
     * 【为什么用try-finally？】
     * 确保即使方法抛出异常，也能打印耗时信息。
     * finally块无论是否发生异常都会执行。
     * 
     * 【关键点】
     * methodProxy.invoke(obj, objects) 调用的是代理方法（如doGet_0）
     * 代理方法内部会调用super.doGet()，直接执行父类方法，不会再次触发拦截。
     * 这样就避免了无限递归。
     */
    @Override
    public Object intercept(Object obj, Method methodProxy, String methodName, Object[] objects) throws Throwable {
        // 记录方法开始执行的毫秒时间戳
        long startMs = System.currentTimeMillis();
        try {
            // 使用代理类的代理方法调用父类的方法
            // methodProxy是代理方法（如doGet_0）的Method对象
            // 调用invoke会执行super.doGet()，绕过拦截器
            return methodProxy.invoke(obj, objects);
        } finally {
            // 计算方法执行耗时（当前时间 - 开始时间）
            long cntMs = System.currentTimeMillis() - startMs;
            // 打印方法名和耗时信息
            System.out.println(methodName + "方法的执行耗时为" + cntMs + "毫秒");
        }
    }

}
