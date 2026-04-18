package com.wujiuye.agent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

/**
 * Java Agent入口类 - JVM代理的核心类
 * 
 * 【什么是Java Agent？】
 * Java Agent是一种特殊的Java程序，可以在JVM运行时修改字节码。
 * 它类似于一个"插件"，可以在不修改源代码的情况下，动态改变类的行为。
 * 
 * 【Java Agent的两种启动方式】
 * 1. premain: 在应用程序main方法之前执行（启动时加载）
 *    - 需要在启动时添加参数：-javaagent:agent.jar
 *    - 适用于：启动时就需要插桩的场景
 * 
 * 2. agentmain: 在应用程序运行后动态加载（运行时加载）
 *    - 使用Attach API动态加载到运行中的JVM进程
 *    - 适用于：运行时动态监控、热修复等场景
 * 
 * 【Instrumentation是什么？】
 * Instrumentation是JVM提供的字节码操作接口，核心功能：
 * 1. addTransformer: 注册类转换器，在类加载时转换字节码
 * 2. retransformClasses: 重新转换已加载的类
 * 3. getAllLoadedClasses: 获取所有已加载的类
 * 4. redefineClasses: 重新定义类（不允许添加/删除字段）
 * 
 * 【使用场景】
 * - APM监控（如SkyWalking、Pinpoint）
 * - 热修复
 * - 代码覆盖率统计
 * - 性能分析
 * - 动态代理增强
 */
public class MyJavaAgent {

    /**
     * premain方法 - JVM启动时执行
     * 
     * 【执行时机】
     * 在应用程序的main方法执行之前，JVM会先调用这个方法。
     * 
     * 【参数说明】
     * @param agentOps 代理参数，通过-javaagent参数传递
     *                 例如：-javaagent:agent.jar=key=value
     *                 agentOps就是"key=value"
     * 
     * @param instrumentation JVM提供的字节码操作接口
     * 
     * 【使用方式】
     * 需要在启动时添加JVM参数：
     * -javaagent:/path/to/my-java-agent.jar
     * 
     * 【执行流程】
     * 1. JVM启动
     * 2. 调用premain方法
     * 3. 注册类转换器（BusinessClassFileTransformer）
     * 4. 后续加载的类都会经过转换器处理
     * 5. 执行应用程序的main方法
     */
//    public static void premain(String agentOps, Instrumentation instrumentation) {
//        System.out.println("premain function run...");
//        // 注册类转换器，所有新加载的类都会经过这个转换器处理
//        instrumentation.addTransformer(new BusinessClassFileTransformer());
//    }

    /**
     * agentmain方法 - JVM运行时动态加载执行
     * 
     * 【执行时机】
     * 在应用程序运行过程中，通过Attach API动态加载Agent时执行。
     * 
     * 【参数说明】
     * @param agentOps 代理参数，通过loadAgent方法传递
     *                 例如：vm.loadAgent("agent.jar", "key=value")
     *                 agentOps就是"key=value"
     * 
     * @param instrumentation JVM提供的字节码操作接口
     * 
     * 【使用方式】
     * 不需要启动参数，通过Attach API动态加载：
     * VirtualMachine vm = VirtualMachine.attach(pid);
     * vm.loadAgent("/path/to/my-java-agent.jar");
     * 
     * 【执行流程】
     * 1. 通过Attach API连接到目标JVM进程
     * 2. 调用loadAgent加载Agent
     * 3. JVM调用agentmain方法
     * 4. 注册可重转换的类转换器
     * 5. 遍历所有已加载的类，触发重转换
     * 6. 移除转换器
     */
    public static void agentmain(String agentOps, Instrumentation instrumentation) {
        System.out.println("agentmain function run...");
        
        // 创建类转换器，用于修改字节码
        BusinessClassFileTransformer transformer = new BusinessClassFileTransformer();
        
        /**
         * 注册类转换器到Instrumentation
         * 
         * 【参数详解】
         * 1. transformer: 类转换器实例
         *    当类加载或重转换时，会调用transformer.transform()方法
         * 
         * 2. true/false: 是否可重转换（retransformable）
         *    - false: 不可重转换，只能转换新加载的类
         *    - true: 可重转换，可以转换已经加载的类
         * 
         * 【为什么这里用true？】
         * 因为agentmain是在运行时加载的，大部分类已经加载过了。
         * 需要设置true才能用retransformClasses()重新转换已加载的类。
         * 
         * 【注意】
         * 可重转换的转换器必须在MANIFEST.MF中声明：
         * Can-Retransform-Classes: true
         */
        instrumentation.addTransformer(transformer, true);

        /**
         * 获取所有已经加载的类
         * 
         * 返回的数组包含JVM中所有已加载的类，包括：
         * - Java核心类（java.lang.*等）
         * - 第三方库类
         * - 应用程序类
         * - 自定义类
         */
        Class<?>[] classs = instrumentation.getAllLoadedClasses();
        
        /**
         * 遍历所有已加载的类，触发重转换
         * 
         * 【为什么要遍历？】
         * 注册转换器后，只有新加载的类会自动转换。
         * 已经加载的类需要手动调用retransformClasses()才能转换。
         */
        for (Class<?> cla : classs) {
            /**
             * 过滤掉java与sun包下的类
             * 
             * 【为什么要过滤？】
             * 1. Java核心类不应该被修改，可能导致JVM不稳定
             * 2. 修改核心类可能违反JVM安全策略
             * 3. 我们只关心业务类的监控
             */
            if (cla.getName().startsWith("java")
                    || cla.getName().startsWith("sun")) {
                continue;
            }
            
            /**
             * 过滤掉与项目无关的类
             * 
             * com.intellij: IDEA的类
             * org.jetbrains: JetBrains的类
             * 这些是IDE相关的类，不需要监控
             */
            if (cla.getName().startsWith("com.intellij")
                    || cla.getName().startsWith("org.jetbrains")) {
                continue;
            }
            
            /**
             * 过滤掉数组类
             * 
             * 【为什么检查"["？】
             * JVM中数组类的名称以"["开头，例如：
             * - [Ljava.lang.String; 表示String[]
             * - [I 表示int[]
             * 数组类不需要转换
             */
            if (cla.getName().startsWith("[")) {
                continue;
            }
            
            /**
             * 排除Agent自身的类
             * 
             * 【为什么要排除？】
             * Agent自身的类不应该被转换，否则可能导致：
             * 1. 无限递归（转换Agent类时又触发转换）
             * 2. Agent功能异常
             */
            if (cla.getName().startsWith("com.wujiuye.agent")) {
                continue;
            }
            
            try {
                /**
                 * 重转换类
                 * 
                 * 【什么是重转换？】
                 * 重新加载类的字节码，触发已注册的转换器。
                 * 转换器会返回新的字节码，JVM会用新字节码替换旧的。
                 * 
                 * 【限制】
                 * 重转换不允许：
                 * 1. 添加或删除字段
                 * 2. 添加或删除方法
                 * 3. 修改方法签名
                 * 只允许修改方法体（添加/删除/修改指令）
                 * 
                 * 【为什么会抛出UnmodifiableClassException？】
                 * 当类不允许被重转换时抛出，例如：
                 * - 类是JVM内部类
                 * - 类已经被锁定
                 */
                instrumentation.retransformClasses(cla);
            } catch (UnmodifiableClassException e) {
                e.printStackTrace();
            }
        }
        
        // 完成后可将转换器移除，避免影响后续类加载
        instrumentation.removeTransformer(transformer);
    }

}
