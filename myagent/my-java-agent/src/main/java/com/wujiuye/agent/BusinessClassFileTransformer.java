package com.wujiuye.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * 业务类文件转换器 - Java Agent的核心转换器
 * 
 * 【类的作用】
 * 这个类实现了ClassFileTransformer接口，是Java Agent中负责转换字节码的核心组件。
 * 当类被加载或重转换时，JVM会调用这个类的transform方法。
 * 
 * 【ClassFileTransformer接口】
 * 这是java.lang.instrument包提供的接口，用于转换类文件字节码。
 * 实现这个接口后，需要注册到Instrumentation：
 * instrumentation.addTransformer(new BusinessClassFileTransformer());
 * 
 * 【转换时机】
 * 1. 类首次加载时（defineClass之前）
 * 2. 调用retransformClasses()时
 * 3. 调用redefineClasses()时
 * 
 * 【工作流程】
 * 1. JVM准备加载一个类
 * 2. 调用所有已注册的transform方法
 * 3. transform返回新的字节码（或null表示不修改）
 * 4. JVM使用最终的字节码定义类
 */
public class BusinessClassFileTransformer implements ClassFileTransformer {

    /**
     * 类转换方法 - JVM在加载或重转换类时调用
     * 
     * 【调用时机】
     * 当类被加载或重转换时，JVM会按注册顺序调用所有转换器的这个方法。
     * 
     * 【参数详解】
     * @param loader 加载该类的ClassLoader
     *               可以通过loader加载依赖类
     *               如果是Bootstrap ClassLoader，这个值为null
     * 
     * @param className 类的内部名称（使用/分隔）
     *                  例如："com/wujiuye/demo/UserService"
     *                  注意：不是"com.wujiuye.demo.UserService"
     * 
     * @param classBeingRedefined 被重定义的类
     *                            - 首次加载时：null
     *                            - 重转换时：Class对象
     *                            可以通过这个参数判断是首次加载还是重转换
     * 
     * @param protectionDomain 类的保护域
     *                         包含类的权限信息，一般用不到
     * 
     * @param classfileBuffer 类的原始字节码
     *                        这是JVM读取的.class文件内容
     *                        可以修改这个字节码并返回新的字节码
     * 
     * @return 修改后的字节码数组
     *         - 返回null: 表示不修改该类，使用原始字节码
     *         - 返回byte[]: 使用返回的字节码替代原始字节码
     * 
     * 【执行流程】
     * 1. 过滤不需要转换的类（java.*、sun.*等）
     * 2. 过滤只转换目标包下的类（com/wujiuye）
     * 3. 调用ClassInstrumentationFactory.modifyClass()转换字节码
     * 4. 返回转换后的字节码
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        /**
         * 过滤掉不需要插桩的类
         * 
         * 【什么是插桩？】
         * 插桩（Instrumentation）是在代码中插入监控逻辑的过程。
         * 例如：在方法开始和结束时插入日志代码，用于监控方法执行。
         * 
         * 【为什么要过滤java和sun包？】
         * 1. Java核心类不应该被修改，可能导致JVM不稳定
         * 2. 修改核心类可能违反JVM安全策略
         * 3. 我们只关心业务类的监控
         * 4. 核心类数量庞大，转换会影响性能
         */
        if (className.startsWith("java")
                || className.startsWith("sun")) {
            return null;  // 返回null表示不修改该类
        }
        
        /**
         * 只转换com/wujiuye包下的类
         * 
         * 【为什么这样过滤？】
         * 1. 只监控目标业务类，避免影响其他类
         * 2. 减少不必要的字节码转换，提高性能
         * 3. 避免转换第三方库类，可能导致问题
         * 
         * 【注意】
         * className使用/分隔，不是.分隔
         * 例如："com/wujiuye/demo/UserService"
         */
        if (!className.startsWith("com/wujiuye")) {
            return null;  // 返回null表示不修改该类
        }
        
        // 打印转换信息，用于调试
        // className: 类名（内部名称格式）
        // classBeingRedefined: 如果是重转换，这里是Class对象；首次加载时为null
        System.err.println(className + "==>" + classBeingRedefined);
        
        /**
         * 调用字节码转换工厂方法
         * 
         * ClassInstrumentationFactory.modifyClass()会：
         * 1. 使用ASM读取原始字节码
         * 2. 遍历类的方法
         * 3. 在方法中插入监控代码（before/after/error）
         * 4. 返回修改后的字节码
         */
        return ClassInstrumentationFactory.modifyClass(classfileBuffer);
    }

}
