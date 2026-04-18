package com.wujiuye.asmbytecode.book.sixth.cglib;

import com.wujiuye.asmbytecode.book.fifth.util.ByteCodeUtils;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * CGLIB增强器 - 用于创建CGLIB代理对象
 * 
 * 【类的作用】
 * 这个类是CGLIB代理的入口类，类似于JDK的Proxy.newProxyInstance()。
 * 它负责：
 * 1. 设置要代理的父类
 * 2. 设置方法拦截器
 * 3. 生成代理类字节码并加载
 * 4. 创建代理对象实例
 * 
 * 【使用流程】
 * MyEnhancer enhancer = new MyEnhancer();
 * enhancer.setSuperclass(HttpRequestTemplateImpl.class);  // 设置父类
 * enhancer.setCallback(new MyInterceptor());              // 设置拦截器
 * Object proxy = enhancer.create();                       // 创建代理对象
 * 
 * 【与JDK Proxy的对比】
 * JDK: Proxy.newProxyInstance(classLoader, interfaces, handler)
 * CGLIB: MyEnhancer.create()
 * 
 * JDK基于接口，CGLIB基于继承（生成子类）
 */
public class MyEnhancer {

    /** 要代理的父类 */
    private Class<?> superclass;
    
    /** 方法拦截器 */
    private MyMethodInterceptor interceptor;

    /**
     * 设置要代理的父类
     * 
     * 【参数说明】
     * @param superclass 要代理的类，代理类会继承这个类
     * 
     * 【限制条件】
     * 1. 不能是接口：CGLIB基于继承，接口不能被继承
     * 2. 不能是Object类：Object是根类，代理它没有意义
     * 3. 不能是final类：final类不能被继承
     * 
     * 【为什么有这些限制？】
     * CGLIB的原理是生成目标类的子类，所以：
     * - 接口不能用extends继承
     * - final类不允许被继承
     * - Object类是所有类的父类，代理它无法获得具体方法
     */
    public void setSuperclass(Class<?> superclass) {
        if (superclass.isInterface()) {
            throw new RuntimeException("父类不能是接口！");
        }
        if (superclass == Object.class) {
            throw new RuntimeException("不能代理Object类！");
        }
        /**
         * 检查是否是final类
         * 
         * 【位运算说明】
         * superclass.getModifiers()返回一个int，每个bit代表一个修饰符
         * Modifier.FINAL是常量值0x0010
         * (modifiers & Modifier.FINAL) == Modifier.FINAL 表示有final修饰符
         */
        if ((superclass.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
            throw new RuntimeException("final类不允许继承！");
        }
        this.superclass = superclass;
    }

    /**
     * 设置方法拦截器
     * 
     * 【参数说明】
     * @param interceptor 方法拦截器，用于处理方法调用
     * 
     * 【拦截器的作用】
     * 当代理对象的方法被调用时，会转发给拦截器的intercept方法。
     * 拦截器可以：
     * 1. 执行前置逻辑（日志、权限检查）
     * 2. 决定是否调用原始方法
     * 3. 执行后置逻辑（性能监控、事务）
     * 4. 修改返回值
     */
    public void setCallback(MyMethodInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * 创建代理对象
     * 
     * 【执行流程】
     * 1. 检查是否设置了父类
     * 2. 如果拦截器为空，直接创建父类实例（无需代理）
     * 3. 生成代理类名：父类名 + "$Proxy"
     * 4. 调用SubclassProxyFactory生成代理类字节码
     * 5. 使用ByteCodeUtils加载代理类
     * 6. 通过反射创建代理对象实例
     * 
     * @return 代理对象实例，类型是父类类型，可以强转为具体类型
     * 
     * 【使用示例】
     * HttpRequestTemplateImpl proxy = (HttpRequestTemplateImpl) enhancer.create();
     * proxy.doGet(request);  // 这个调用会被拦截器拦截
     */
    public Object create() {
        if (superclass == null) {
            throw new RuntimeException("未设置父类！");
        }
        try {
            /**
             * 如果拦截器为空，无需创建代理类
             * 
             * 【为什么？】
             * 没有拦截器，代理就没有意义。
             * 直接返回父类实例即可。
             */
            if (interceptor == null) {
                return superclass.newInstance();
            }
            
            /**
             * 生成代理类名
             * 
             * Type.getInternalName(superclass): 获取父类的内部名称
             * 例如："com/wujiuye/HttpRequestTemplateImpl"
             * 
             * 代理类名："com/wujiuye/HttpRequestTemplateImpl$Proxy"
             * 使用$符号是Java内部类的命名约定，这里只是借用
             */
            String subclassName = Type.getInternalName(superclass) + "$Proxy";
            
            /**
             * 生成代理类字节码
             * 
             * SubclassProxyFactory.createProxyClass()会：
             * 1. 创建一个继承superclass的子类
             * 2. 覆写所有非final方法
             * 3. 将方法调用转发给拦截器
             * 4. 生成调用父类方法的代理方法
             */
            byte[] subclassByteCode = SubclassProxyFactory.createProxyClass(subclassName, superclass);
            
            /**
             * 加载代理类
             * 
             * ByteCodeUtils.loadClass()会：
             * 1. 创建自定义ClassLoader
             * 2. 使用defineClass()加载字节码
             * 3. 返回Class对象
             */
            Class<?> subclass = ByteCodeUtils.loadClass(subclassName, subclassByteCode);
            
            /**
             * 获取代理类的构造方法
             * 
             * 代理类的构造方法签名：
             * public Proxy(MyMethodInterceptor interceptor)
             * 
             * 所以需要传入interceptor参数
             */
            Constructor<?> constructor = subclass.getConstructor(MyMethodInterceptor.class);
            
            /**
             * 创建代理对象实例
             * 
             * constructor.newInstance(interceptor)会：
             * 1. 调用代理类的构造方法
             * 2. 传入interceptor参数
             * 3. 返回代理对象
             * 
             * 代理对象内部会保存interceptor引用，
             * 在方法调用时使用它进行拦截。
             */
            return constructor.newInstance(interceptor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
