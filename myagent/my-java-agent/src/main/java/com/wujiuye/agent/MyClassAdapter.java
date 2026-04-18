package com.wujiuye.agent;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

/**
 * 类适配器 - 负责遍历类的方法并应用方法适配器
 * 
 * 【类的作用】
 * 这个类继承ClassVisitor，是ASM访问者模式中的核心组件之一。
 * 它负责：
 * 1. 访问类的每个方法
 * 2. 过滤不需要转换的方法（私有、抽象、构造方法等）
 * 3. 对需要转换的方法，包装成MyMethodAdapter进行字节码修改
 * 
 * 【ClassVisitor是什么？】
 * ClassVisitor是ASM提供的类访问器接口，采用访问者模式。
 * 当ClassReader解析类时，会按顺序调用ClassVisitor的方法：
 * - visit(): 访问类定义
 * - visitField(): 访问字段
 * - visitMethod(): 访问方法
 * - visitEnd(): 访问结束
 * 
 * 【访问者模式的工作原理】
 * ClassReader（解析字节码）
 *   ↓ 调用visitMethod()
 * MyClassAdapter（过滤和路由）
 *   ↓ 返回MyMethodAdapter
 * MyMethodAdapter（修改方法字节码）
 *   ↓ 转发给
 * ClassWriter（生成新字节码）
 * 
 * 【方法过滤规则】
 * 不转换以下方法：
 * 1. 私有方法（ACC_PRIVATE）
 * 2. 抽象方法（ACC_ABSTRACT）
 * 3. native方法（ACC_NATIVE）
 * 4. 桥接方法（ACC_BRIDGE）
 * 5. 合成方法（ACC_SYNTHETIC）
 * 6. 构造方法（<init>）
 * 7. 静态初始化方法（<clinit>）
 */
public class MyClassAdapter extends ClassVisitor {

    /** 当前访问的类名（内部名称格式） */
    private String className;

    /**
     * 构造方法
     * 
     * 【参数说明】
     * @param className 类的内部名称，如"com/wujiuye/demo/UserService"
     * @param classWriter ClassWriter实例，用于生成新字节码
     * 
     * 【super(ASM6, classWriter)的作用】
     * 设置ASM API版本和下一个访问器。
     * ASM6表示使用ASM 6的API，classWriter是最终生成字节码的访问器。
     * 所有访问事件最终都会转发给classWriter。
     */
    public MyClassAdapter(String className, ClassWriter classWriter) {
        super(ASM6, classWriter);
        this.className = className;
    }

    /**
     * 访问方法时调用
     * 
     * 【调用时机】
     * 当ClassReader解析到类的方法时，会调用这个方法。
     * 每个方法都会调用一次。
     * 
     * 【参数详解】
     * @param access 方法的访问标志
     *               这是一个int值，每个bit代表一个修饰符
     *               例如：ACC_PUBLIC(0x0001), ACC_PRIVATE(0x0002), ACC_STATIC(0x0008)
     *               可以用位运算检查：(access & ACC_PUBLIC) != 0 表示是public方法
     * 
     * @param name 方法名
     *             例如："queryUser", "<init>", "<clinit>"
     *             <init>表示构造方法
     *             <clinit>表示静态初始化方法
     * 
     * @param descriptor 方法描述符
     *                   格式：(参数类型)返回类型
     *                   例如："(Ljava/lang/String;I)Ljava/util/Map;"
     *                   表示：(String, int) -> Map
     *                   
     *                   类型描述符：
     *                   - Z: boolean
     *                   - B: byte
     *                   - C: char
     *                   - S: short
     *                   - I: int
     *                   - J: long
     *                   - F: float
     *                   - D: double
     *                   - L全限定名;: 引用类型，如Ljava/lang/String;
     *                   - [: 数组，如[I表示int[]
     * 
     * @param signature 泛型签名
     *                  如果方法有泛型，这里会有签名信息
     *                  普通方法为null
     * 
     * @param exceptions 方法声明抛出的异常数组
     *                   例如：["java/lang/Exception"]
     *                   如果没有声明异常，为null
     * 
     * @return MethodVisitor 方法访问器
     *         - 返回null: 表示忽略该方法及其子元素
     *         - 返回MethodVisitor: 用于访问方法代码
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        /**
         * 不对私有方法注入
         * 
         * 【为什么不转换私有方法？】
         * 1. 私有方法只在类内部调用，外部无法调用
         * 2. 监控私有方法的意义不大
         * 3. 避免过度监控影响性能
         * 
         * 【位运算说明】
         * ACC_PRIVATE = 0x0002
         * (access & ACC_PRIVATE) != 0 表示有private修饰符
         */
        if ((access & ACC_PRIVATE) != 0) {
            // 返回super.visitMethod表示不修改该方法，直接转发
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        
        /**
         * 不对抽象、native等方法注入
         * 
         * 【为什么不转换这些方法？】
         * - ACC_ABSTRACT: 抽象方法没有方法体，无法插入代码
         * - ACC_NATIVE: native方法由C/C++实现，没有Java字节码
         * - ACC_BRIDGE: 桥接方法是编译器自动生成的，用于泛型擦除
         * - ACC_SYNTHETIC: 合成方法是编译器自动生成的，不是源代码中的
         * 
         * 【桥接方法示例】
         * class Parent<T> { void set(T t) {} }
         * class Child extends Parent<String> { void set(String s) {} }
         * 编译器会生成桥接方法：void set(Object o) { set((String)o); }
         */
        if ((access & ACC_ABSTRACT) != 0
                || (access & ACC_NATIVE) != 0
                || (access & ACC_BRIDGE) != 0
                || (access & ACC_SYNTHETIC) != 0) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        
        /**
         * 不对类实例初始化方法注入
         * 
         * 【什么是<init>和<clinit>？】
         * - <init>: 实例构造方法，创建对象时执行
         * - <clinit>: 静态初始化方法，类加载时执行一次
         * 
         * 【为什么不转换？】
         * 1. 构造方法和静态初始化方法的执行时机特殊
         * 2. 在这些方法中插入监控代码可能导致问题
         * 3. 我们只关心业务方法的调用
         */
        if ("<init>".equals(name) || "<clinit>".equals(name)) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        
        /**
         * 获取原始方法访问器
         * 
         * super.visitMethod()会返回一个MethodVisitor，
         * 这个访问器会将方法信息传递给ClassWriter。
         */
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        /**
         * 包装成MyMethodAdapter
         * 
         * MyMethodAdapter继承MethodVisitor，负责：
         * 1. 在方法开始处插入before监控代码
         * 2. 在方法返回处插入after监控代码
         * 3. 在异常处理处插入error监控代码
         * 
         * 【装饰器模式】
         * MyMethodAdapter包装了原始的mv，
         * 所有方法访问事件先经过MyMethodAdapter处理，
         * 然后再转发给mv，最终到达ClassWriter。
         */
        return new MyMethodAdapter(className, access, name, descriptor, mv);
    }

}
