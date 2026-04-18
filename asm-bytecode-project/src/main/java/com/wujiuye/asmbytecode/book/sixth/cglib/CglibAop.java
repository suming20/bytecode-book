package com.wujiuye.asmbytecode.book.sixth.cglib;

import com.wujiuye.asmbytecode.book.sixth.HttpRequest;
import com.wujiuye.asmbytecode.book.sixth.HttpRequestTemplateImpl;
import net.sf.cglib.core.DebuggingClassWriter;

/**
 * CGLIB AOP示例 - 演示如何使用CGLIB实现AOP（面向切面编程）
 * 
 * 【AOP是什么？】
 * AOP（Aspect-Oriented Programming）面向切面编程，
 * 是一种编程思想，用于将与业务逻辑无关的通用功能（如日志、事务、性能监控）
 * 从业务代码中分离出来，统一处理。
 * 
 * 【CGLIB如何实现AOP？】
 * 1. 使用CGLIB生成目标类的子类（代理类）
 * 2. 拦截目标类的方法调用
 * 3. 在拦截器中添加前置/后置逻辑
 * 4. 调用原始方法
 * 
 * 【与Spring AOP的关系】
 * Spring AOP底层就是使用CGLIB（或JDK动态代理）实现的。
 * 当目标类实现了接口时，Spring使用JDK动态代理；
 * 当目标类没有实现接口时，Spring使用CGLIB。
 * 
 * 【本类的作用】
 * 演示两种CGLIB代理的使用方式：
 * 1. 使用CGLIB官方API（注释掉的代码）
 * 2. 使用自定义的MyEnhancer和MyMethodInterceptor（当前使用的代码）
 */
public class CglibAop {

    /**
     * 静态代码块 - 在类加载时执行
     * 
     * 【作用】
     * 设置CGLIB的调试选项，将生成的代理类class文件保存到本地磁盘。
     * 这样可以用反编译工具（如JD-GUI）查看生成的代理类代码，
     * 帮助理解CGLIB的工作原理。
     * 
     * 【DebuggingClassWriter.DEBUG_LOCATION_PROPERTY】
     * 这是CGLIB提供的系统属性，用于指定代理类文件的保存路径。
     * 设置后，CGLIB会将生成的代理类字节码写入指定目录。
     * 
     * 【查看生成的代理类】
     * 运行程序后，可以在/tmp目录下找到生成的class文件，
     * 文件名类似：HttpRequestTemplateImpl$EnhancerByCGLIB$xxxx.class
     */
    static {
        // 代理类class文件存入本地磁盘
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "/tmp");
    }

    /**
     * 主方法 - 程序入口
     * 
     * 【执行流程】
     * 1. 创建MyEnhancer（CGLIB增强器）
     * 2. 设置要代理的父类（HttpRequestTemplateImpl）
     * 3. 设置方法拦截器（HttpRequestMyMethodInterceptor）
     * 4. 创建代理对象
     * 5. 调用代理对象的方法（会被拦截器拦截）
     */
    public static void main(String[] args) {
        /**
         * 【方式一】使用CGLIB官方API（已注释）
         * 
         * Enhancer: CGLIB官方提供的增强器类
         * MethodInterceptor: CGLIB官方的方法拦截器接口
         * 
         * 这种方式直接使用CGLIB库，功能更强大，性能更好。
         * 
         * Enhancer enhancer = new Enhancer();
         * enhancer.setSuperclass(HttpRequestTemplateImpl.class);
         * enhancer.setCallback(new HttpRequestMethodInterceptor());
         * HttpRequestTemplateImpl requestTemplate = (HttpRequestTemplateImpl) enhancer.create();
         * HttpRequest request = new HttpRequest("http://127.0.0.1:8080/book/list", "GET");
         * requestTemplate.doGet(request);
         */

        /**
         * 【方式二】使用自定义的CGLIB实现（当前使用）
         * 
         * MyEnhancer: 自定义的增强器，内部使用ASM生成代理类
         * HttpRequestMyMethodInterceptor: 自定义的拦截器，统计方法耗时
         * 
         * 这种方式是为了学习CGLIB的原理，手动实现类似的功能。
         */
        
        // 创建增强器
        MyEnhancer enhancer = new MyEnhancer();
        
        // 设置要代理的父类
        // 代理类会继承HttpRequestTemplateImpl，并覆写其方法
        enhancer.setSuperclass(HttpRequestTemplateImpl.class);
        
        // 设置方法拦截器
        // 所有方法调用都会被这个拦截器拦截
        enhancer.setCallback(new HttpRequestMyMethodInterceptor());
        
        // 创建代理对象
        // 内部流程：
        // 1. 生成代理类字节码（SubclassProxyFactory.createProxyClass）
        // 2. 加载代理类（ByteCodeUtils.loadClass）
        // 3. 创建代理对象实例
        HttpRequestTemplateImpl proxyObj = (HttpRequestTemplateImpl) enhancer.create();
        
        // 创建HTTP请求对象
        HttpRequest request = new HttpRequest("http://127.0.0.1:8080/book/list", "GET");
        
        /**
         * 调用代理对象的方法
         * 
         * 【执行流程】
         * 1. 调用proxyObj.doGet(request)
         * 2. 实际执行的是代理类的doGet方法（覆写的方法）
         * 3. 代理类调用拦截器的intercept方法
         * 4. 拦截器记录开始时间
         * 5. 拦截器调用methodProxy.invoke()执行原始方法
         * 6. 原始方法执行完毕
         * 7. 拦截器计算并打印耗时
         * 8. 返回结果
         * 
         * 【输出示例】
         * doGet方法的执行耗时为123毫秒
         */
        proxyObj.doGet(request);
    }

}
