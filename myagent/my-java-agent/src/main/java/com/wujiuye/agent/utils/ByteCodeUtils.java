package com.wujiuye.agent.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字节码工具类 - 提供字节码操作相关的辅助方法
 * 
 * 【类的作用】
 * 这个类提供了两个常用的字节码操作工具方法：
 * 1. savaToFile(): 将字节码保存为.class文件（用于调试）
 * 2. getParamDescriptors(): 从方法描述符中解析参数类型
 * 
 * 【使用场景】
 * - 调试ASM字节码转换结果
 * - 解析方法签名信息
 * - 字节码分析和处理
 */
public class ByteCodeUtils {

    /**
     * 将字节码保存为.class文件
     * 
     * 【方法作用】
     * 将ASM生成的字节码写入磁盘的.class文件，
     * 可以用反编译工具（如JD-GUI）查看生成的代码。
     * 
     * 【为什么需要保存？】
     * 1. 验证字节码转换是否正确
     * 2. 用反编译工具查看插桩后的代码
     * 3. 学习ASM转换的效果
     * 4. 调试字节码问题
     * 
     * @param className 类名（使用.分隔的格式）
     *                  例如："com.wujiuye.demo.UserService"
     *                  注意：不是内部名称格式（/分隔）
     * 
     * @param byteCode 类的字节码数组
     *                 这是JVM的.class文件内容
     * 
     * @throws IOException 文件写入异常
     * 
     * 【文件保存位置】
     * 文件保存在/tmp目录下，文件名：className.class
     * 例如：/tmp/com.wujiuye.demo.UserService.class
     * 
     * 【执行流程】
     * 1. 创建File对象：/tmp/className.class
     * 2. 如果文件已存在，先删除
     * 3. 创建新文件
     * 4. 使用FileOutputStream写入字节码
     * 5. 自动关闭流（try-with-resources）
     */
    public static void savaToFile(String className, byte[] byteCode) throws IOException {
        // 创建文件对象，路径：/tmp/className.class
        File file = new File("/tmp/" + className + ".class");
        
        /**
         * 文件操作逻辑
         * 
         * 条件说明：
         * - !file.exists(): 文件不存在
         * - file.delete(): 删除已存在的文件
         * - file.createNewFile(): 创建新文件
         * 
         * 这个条件确保：
         * 1. 如果文件不存在，直接创建
         * 2. 如果文件存在，先删除再创建（覆盖旧文件）
         */
        if ((!file.exists() || file.delete()) && file.createNewFile()) {
            /**
             * try-with-resources语法
             * 
             * 自动关闭FileOutputStream，避免资源泄漏。
             * 等效于：
             * FileOutputStream fos = null;
             * try {
             *     fos = new FileOutputStream(file);
             *     fos.write(byteCode);
             * } finally {
             *     if (fos != null) fos.close();
             * }
             */
            try (FileOutputStream fos = new FileOutputStream(file)) {
                // 将字节码写入文件
                fos.write(byteCode);
            }
        }
    }

    /**
     * 根据方法描述符获取参数类型描述符数组
     * 
     * 【方法作用】
     * 从方法描述符中提取所有参数的类型描述符。
     * 
     * 【什么是方法描述符？】
     * 方法描述符是JVM用来表示方法签名的字符串格式：
     * 格式：(参数类型)返回类型
     * 
     * 例如：
     * - "()V" 表示 void method()
     * - "(I)V" 表示 void method(int)
     * - "(Ljava/lang/String;I)Ljava/util/Map;" 表示 Map method(String, int)
     * - "([I[J)V" 表示 void method(int[], long[])
     * 
     * 【类型描述符对照表】
     * - Z: boolean
     * - B: byte
     * - C: char
     * - S: short
     * - I: int
     * - J: long
     * - F: float
     * - D: double
     * - L全限定名;: 引用类型，如Ljava/lang/String;
     * - [: 数组，如[I表示int[]，[[Ljava/lang/String;表示String[][]
     * 
     * @param methodDescriptor 方法描述符
     *                         例如："(Ljava/lang/String;I)Ljava/util/Map;"
     * 
     * @return 参数类型描述符数组
     *         例如：["Ljava/lang/String;", "I"]
     *         如果没有参数，返回null
     * 
     * 【正则表达式说明】
     * 正则：(L.*?;|\[{0,2}L.*?;|[ZCBSIFJD]|\[{0,2}[ZCBSIFJD]{1})
     * 
     * 匹配规则：
     * 1. L.*?; - 引用类型，如Ljava/lang/String;
     * 2. \[{0,2}L.*?; - 数组引用类型，如[Ljava/lang/String;
     * 3. [ZCBSIFJD] - 基本类型，如I、J、Z等
     * 4. \[{0,2}[ZCBSIFJD]{1} - 数组基本类型，如[I、[[J等
     * 
     * 【执行流程】
     * 1. 截取括号内的部分："(Ljava/lang/String;I)"
     * 2. 使用正则表达式匹配所有参数类型
     * 3. 将匹配结果存入List
     * 4. 转换为数组返回
     */
    public static String[] getParamDescriptors(String methodDescriptor) {
        List<String> paramDescriptors = new ArrayList<>();
        
        /**
         * 使用正则表达式匹配参数类型
         * 
         * methodDescriptor.substring(0, methodDescriptor.lastIndexOf(')') + 1)
         * 截取从开始到)的部分，例如："(Ljava/lang/String;I)"
         * 
         * 正则表达式会匹配括号内的所有类型描述符。
         */
        Matcher matcher = Pattern.compile("(L.*?;|\\[{0,2}L.*?;|[ZCBSIFJD]|\\[{0,2}[ZCBSIFJD]{1})")
                .matcher(methodDescriptor.substring(0, methodDescriptor.lastIndexOf(')') + 1));
        
        // 遍历所有匹配结果
        while (matcher.find()) {
            // group(1)获取第一个捕获组，即参数类型描述符
            paramDescriptors.add(matcher.group(1));
        }
        
        // 如果没有参数，返回null
        if (paramDescriptors.isEmpty()) {
            return null;
        }
        
        // 转换为数组返回
        return paramDescriptors.toArray(new String[0]);
    }

}
