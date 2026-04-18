package com.wujiuye.agent;

import com.wujiuye.agent.utils.ByteCodeUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;

import static org.objectweb.asm.Opcodes.*;

/**
 * 类插桩工厂 - 负责执行字节码转换
 * 
 * 【类的作用】
 * 这个类是字节码转换的核心工厂，负责：
 * 1. 使用ASM读取原始类字节码
 * 2. 应用类适配器（MyClassAdapter）进行转换
 * 3. 生成修改后的字节码
 * 4. 保存转换后的类文件（用于调试）
 * 
 * 【ASM转换流程】
 * ClassReader（读取） -> ClassVisitor（访问/修改） -> ClassWriter（写入）
 * 
 * 这是一个典型的访问者模式（Visitor Pattern）：
 * 1. ClassReader解析字节码，按顺序调用ClassVisitor的方法
 * 2. ClassVisitor可以修改或转发这些调用
 * 3. ClassWriter接收调用，生成新的字节码
 * 
 * 【工作流程】
 * 1. 创建ClassReader读取原始字节码
 * 2. 创建ClassWriter用于生成新字节码
 * 3. 创建MyClassAdapter（ClassVisitor）进行方法级别的转换
 * 4. ClassReader.accept()触发访问过程
 * 5. ClassWriter.toByteArray()获取转换后的字节码
 */
public class ClassInstrumentationFactory {

    /**
     * 修改类的字节码
     * 
     * 【方法作用】
     * 对传入的类字节码进行转换，在方法中插入监控代码。
     * 
     * @param classfileBuffer 原始类字节码
     *                        这是JVM的.class文件内容
     * 
     * @return 修改后的字节码
     *         - 返回null: 表示不修改（如接口）
     *         - 返回byte[]: 转换后的字节码
     * 
     * 【执行流程】
     * 1. 创建ClassReader读取原始字节码
     * 2. 检查是否是接口，接口不需要转换
     * 3. 创建ClassWriter生成新字节码
     * 4. 创建MyClassAdapter进行方法转换
     * 5. 执行访问过程（reader.accept）
     * 6. 获取转换后的字节码
     * 7. 保存到文件（用于调试）
     * 8. 返回字节码
     */
    public static byte[] modifyClass(byte[] classfileBuffer) {
        /**
         * 创建ClassReader - ASM的类读取器
         * 
         * ClassReader负责：
         * 1. 解析.class文件的字节码
         * 2. 识别类的所有元素（字段、方法、注解等）
         * 3. 按顺序调用ClassVisitor的对应方法
         * 
         * 例如：
         * - 遇到类定义，调用visit()
         * - 遇到方法，调用visitMethod()
         * - 遇到字段，调用visitField()
         */
        ClassReader classReader = new ClassReader(classfileBuffer);
        
        /**
         * 过滤接口
         * 
         * 【为什么要过滤接口？】
         * 1. 接口没有方法体，只有方法签名
         * 2. 无法在接口方法中插入代码（Java 8之前）
         * 3. 即使Java 8有default方法，一般也不需要监控接口
         * 
         * 【位运算说明】
         * classReader.getAccess()返回类的访问标志
         * ACC_INTERFACE是接口的标志位（0x0200）
         * (access & ACC_INTERFACE) == ACC_INTERFACE 表示是接口
         */
        if ((classReader.getAccess() & ACC_INTERFACE) == ACC_INTERFACE) {
            return null;  // 接口不需要转换
        }
        
        /**
         * 创建ClassWriter - ASM的类写入器
         * 
         * 【参数说明】
         * COMPUTE_MAXS | COMPUTE_FRAMES:
         * - COMPUTE_MAXS: 自动计算操作数栈和局部变量表大小
         * - COMPUTE_FRAMES: 自动计算栈帧（StackMapTable）
         * 
         * 【为什么用这两个标志？】
         * 手动计算栈大小极其复杂，需要考虑每条指令对栈的影响。
         * 使用这两个标志可以让ASM自动计算，大大简化开发。
         */
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS
                | ClassWriter.COMPUTE_FRAMES);
        
        /**
         * 创建MyClassAdapter - 类访问器
         * 
         * MyClassAdapter继承ClassVisitor，负责：
         * 1. 访问类的每个方法
         * 2. 对符合条件的方法，包装成MyMethodAdapter
         * 3. MyMethodAdapter负责在方法中插入监控代码
         * 
         * 【参数说明】
         * 1. classReader.getClassName(): 类的内部名称，如"com/wujiuye/UserService"
         * 2. classWriter: 用于生成新字节码
         */
        MyClassAdapter classAdapter = new MyClassAdapter(
                classReader.getClassName(), classWriter);
        
        /**
         * 执行访问过程
         * 
         * accept方法会：
         * 1. 解析字节码
         * 2. 按顺序调用classAdapter的方法
         *    - visit() -> visitMethod() -> visitEnd()
         * 3. classAdapter可以修改或转发这些调用
         * 4. 最终classWriter生成新的字节码
         * 
         * 【第二个参数】
         * 0: 不跳过任何信息，读取所有内容
         * 其他选项：
         * - SKIP_CODE: 跳过方法代码
         * - SKIP_DEBUG: 跳过调试信息
         * - SKIP_FRAMES: 跳过栈帧
         */
        classReader.accept(classAdapter, 0);
        
        // 获取转换后的字节码
        byte[] bytes = classWriter.toByteArray();
        
        try {
            /**
             * 保存转换后的类文件到磁盘
             * 
             * 【为什么要保存？】
             * 用于调试和验证：
             * 1. 可以用反编译工具查看生成的代码
             * 2. 验证字节码转换是否正确
             * 3. 学习ASM转换的效果
             * 
             * 文件保存在/tmp目录下，可以用JD-GUI等工具查看
             */
            ByteCodeUtils.savaToFile(classReader.getClassName().replace("/", "."), bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 返回转换后的字节码，JVM会用这个字节码替代原始字节码
        return bytes;
    }

}
