package com.wujiuye.asmbytecode.book.sixth.cglib;

import java.lang.reflect.Method;

/**
 * 方法拦截器接口 - CGLIB代理的核心接口
 * 
 * 【接口作用】
 * 这个接口定义了方法拦截的回调方法。
 * 当代理对象的方法被调用时，会转发给拦截器的intercept方法处理。
 * 
 * 【与JDK的InvocationHandler对比】
 * JDK: Object invoke(Object proxy, Method method, Object[] args)
 * CGLIB: Object intercept(Object obj, Method method, String methodName, Object[] args)
 * 
 * 【区别】
 * 1. CGLIB多了一个methodName参数（原始方法名）
 * 2. CGLIB的method参数是代理方法的Method，不是原始方法
 *    代理方法如doGet_0，可以直接调用父类方法
 * 3. JDK的proxy是代理对象，CGLIB的obj也是代理对象（但类型是父类）
 * 
 * 【使用场景】
 * 实现这个接口，在intercept方法中：
 * 1. 执行前置逻辑（如日志、权限检查）
 * 2. 调用method.invoke(obj, args)执行原始方法
 * 3. 执行后置逻辑（如性能监控、事务提交）
 */
public interface MyMethodInterceptor {

    /**
     * 方法拦截回调
     * 
     * 【调用时机】
     * 当代理对象的任何方法被调用时，会自动调用这个intercept方法。
     * 
     * 【参数详解】
     * @param obj 代理类实例（子类对象）
     *            类型是父类类型，可以调用父类的方法
     *            例如：HttpRequestTemplateImpl的代理对象
     * 
     * @param method 代理方法的Method对象
     *               注意：这不是原始方法的Method，而是代理类生成的代理方法（如doGet_0）
     *               这个代理方法是final的，直接调用super.doGet()
     *               使用method.invoke(obj, args)可以调用父类的原始方法，不会再次触发拦截
     *               
     *               【为什么这样设计？】
     *               如果用原始方法的Method.invoke()，会再次触发拦截器，导致无限递归。
     *               所以CGLIB生成了一个final代理方法，直接调用super，绕过拦截器。
     * 
     * @param methodName 被代理的原始方法名称
     *                   例如："doGet"
     *                   因为method.getName()获取到的是代理方法名（如"doGet_0"）
     *                   所以单独提供原始方法名，方便拦截器使用
     * 
     * @param objects 方法执行参数数组
     *                例如：[HttpRequest对象]
     *                如果方法没有参数，这个值为null
     * 
     * @return 方法执行的返回值
     *         必须与被代理方法的返回类型兼容
     *         如果是void方法，返回null
     * 
     * @throws Throwable 可以抛出任何异常
     * 
     * 【使用示例】
     * public Object intercept(Object obj, Method method, String methodName, Object[] args) {
     *     System.out.println("方法执行前：" + methodName);
     *     Object result = method.invoke(obj, args);  // 调用父类原始方法
     *     System.out.println("方法执行后：" + methodName);
     *     return result;
     * }
     */
    Object intercept(Object obj, Method method, String methodName, Object[] objects) throws Throwable;

}
