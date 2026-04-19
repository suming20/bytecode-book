package com.wujiuye.asmbytecode.book.advanced;

import com.wujiuye.asmbytecode.book.fifth.util.ByteCodeUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * ASM字节码生成示例 - 演示如何使用ASM动态生成Java类
 * 
 * 【类的作用】
 * 这个类演示了如何使用ASM框架从零开始生成一个完整的Java类。
 * 生成的类等效于以下Java代码：
 * 
 * <pre>
 * package com.wujiuye;
 * 
 * import java.util.ArrayList;
 * import java.util.List;
 * 
 * public class Main {
 *     public Main() {
 *         super();  // 调用Object的构造方法
 *     }
 *     
 *     public static void main(String[] args) {
 *         // List list = new ArrayList<>();
 *         List list = new ArrayList<>();
 *         
 *         // list.add("hello word!");
 *         list.add("hello word!");
 *         
 *         // String str = list.get(0);
 *         String str = list.get(0);
 *     }
 * }
 * </pre>
 * 
 * 【ASM基础概念】
 * 1. ClassWriter: ASM提供的类写入器，用于生成字节码
 * 2. MethodVisitor: 方法访问器，用于生成方法的字节码指令
 * 3. 操作码(Opcodes): JVM指令，如ALOAD(加载引用)、NEW(创建对象)等
 * 4. 操作数栈: JVM执行字节码时使用的栈结构，所有操作都基于栈
 * 5. 局部变量表: 存储方法参数和局部变量的表格
 * 
 * 【字节码生成流程】
 * 1. 创建ClassWriter
 * 2. 定义类的基本信息（包名、类名、父类、接口）
 * 3. 生成构造方法（<init>）
 * 4. 生成main方法
 * 5. 保存为.class文件
 * 
 * 【运行生成的类】
 * 生成的类保存在/tmp/com.wujiuye.Main.class
 * 可以用java命令运行：java com.wujiuye.Main
 */
public class TMain {

    /**
     * 主方法 - 程序入口
     * 
     * 【执行流程】
     * 1. 创建ClassWriter（字节码写入器）
     * 2. 定义类的基本信息
     * 3. 生成构造方法
     * 4. 生成main方法
     * 5. 保存为.class文件
     * 
     * @param args 命令行参数（未使用）
     * @throws IOException 保存文件时可能抛出IO异常
     */
    public static void main(String[] args) throws IOException {
        /**
         * 创建ClassWriter - ASM的核心类，用于生成字节码
         * 
         * 【参数说明】ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
         * - COMPUTE_MAXS: 自动计算方法的操作数栈大小和局部变量表大小
         *   否则需要手动计算，非常复杂！
         * - COMPUTE_FRAMES: 自动计算栈帧（StackMapTable），JDK 1.6+必需
         *   这是JVM验证字节码安全性的数据结构
         * 
         * 【为什么用这两个标志？】
         * 手动计算栈大小极其复杂，需要考虑每条指令对栈的影响。
         * 使用这两个标志可以让ASM自动计算，大大简化开发。
         */
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        /**
         * 定义类的基本信息
         * 
         * 【参数详解】
         * 1. V1_8: 类版本号，表示生成Java 8兼容的字节码
         *    其他选项：V1_7, V1_9, V10等
         * 
         * 2. ACC_PUBLIC: 访问标志，表示这是一个public类
         *    其他常用标志：ACC_PRIVATE, ACC_FINAL, ACC_ABSTRACT等
         * 
         * 3. "com/wujiuye/Main": 类的内部名称
         *    注意使用/而不是.，这是JVM的标准格式
         *    等效于Java的：com.wujiuye.Main
         * 
         * 4. null: 泛型签名，普通类不需要，设为null
         *    如果有泛型如<T>，需要提供签名描述
         * 
         * 5. Type.getInternalName(Object.class): 父类的内部名称
         *    表示生成的类继承Object类
         *    转换为："java/lang/Object"
         *    所有Java类默认继承Object，所以这里显式指定
         * 
         * 6. null: 实现的接口数组
         *    这个类没有实现任何接口，所以为null
         *    如果要实现接口，如Runnable，可以传入：
         *    new String[]{Type.getInternalName(Runnable.class)}
         */
        cw.visit(V1_8, ACC_PUBLIC, "com/wujiuye/Main",
                null,
                Type.getInternalName(Object.class),
                null);
        
        // 生成构造方法（<init>）
        createInitMethod(cw);
        
        // 生成main方法
        createMainMethod(cw);
        
        /**
         * 保存生成的字节码到文件
         * 
         * cw.toByteArray(): 获取生成的字节码数组
         * ByteCodeUtils.savaToFile(): 将字节码写入/tmp目录的.class文件
         * 
         * 生成的文件路径：/tmp/com.wujiuye.Main.class
         * 可以用反编译工具（如JD-GUI）查看生成的代码
         */
        ByteCodeUtils.savaToFile("com.wujiuye.Main", cw.toByteArray());
    }

    /**
     * 创建类的构造方法
     * 
     * 【生成的Java代码等效于】
     * public Main() {
     *     super();  // 调用Object的无参构造方法
     * }
     * 
     * 【为什么需要构造方法？】
     * 每个Java类都必须有构造方法。
     * 如果没有显式定义，编译器会生成默认的无参构造方法。
     * 默认构造方法会调用父类的无参构造方法（super()）。
     * 
     * @param cw ClassWriter，用于添加方法
     */
    private static void createInitMethod(ClassWriter cw) {
        /**
         * 创建<init>方法（实例构造方法）
         * 
         * 【参数详解】
         * 1. ACC_PUBLIC: public访问级别
         * 
         * 2. "<init>": 构造方法名，JVM规定的实例构造方法名
         *    注意：不是"clinit"（那是静态初始化方法）
         * 
         * 3. "()V": 方法描述符，表示方法签名
         *    格式：(参数类型)返回类型
         *    () 表示无参数
         *    V 表示void返回类型（构造方法没有返回值）
         * 
         * 4. null: 泛型签名，不需要
         * 
         * 5. null: 异常列表，不声明抛出异常
         */
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        
        // 标记方法代码开始，必须调用！
        mv.visitCode();
        
        /**
         * 【步骤1】加载this引用
         * 
         * ALOAD 0: 加载局部变量表第0个位置的引用类型值
         * 
         * 【为什么是0？】
         * 实例方法的局部变量表：
         * - 位置0: this引用（所有实例方法都有）
         * - 位置1: 第一个参数
         * - 位置2: 第二个参数
         * - ...
         * 
         * 静态方法没有this，所以位置0是第一个参数。
         */
        mv.visitVarInsn(ALOAD, 0);
        
        /**
         * 【步骤2】调用父类（Object）的构造方法
         * 
         * INVOKESPECIAL: 调用特殊方法（构造方法、私有方法、super方法）
         * 
         * 【为什么用INVOKESPECIAL而不是INVOKEVIRTUAL？】
         * - 构造方法必须用INVOKESPECIAL，这是JVM规范
         * - INVOKESPECIAL不会进行动态绑定，直接调用指定类的方法
         * - INVOKEVIRTUAL会进行动态绑定，可能调用子类重写的方法
         * 
         * 【参数详解】
         * 1. Type.getInternalName(Object.class): 父类名，"java/lang/Object"
         * 2. "<init>": 构造方法名
         * 3. "()V": 无参构造方法
         * 4. false: 是否是接口方法，Object不是接口所以是false
         * 
         * 【执行效果】
         * 消耗栈顶的this引用，调用Object.<init>()
         * 等效Java代码：super()
         */
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        
        // RETURN: void方法的返回指令
        mv.visitInsn(RETURN);
        
        /**
         * visitFrame: 设置栈帧状态（StackMapTable）
         * 
         * 【参数说明】
         * 第一个参数0: 表示F_NEW（新的栈帧）
         * 第二个参数0: 局部变量数量（0表示不指定）
         * 第三个参数null: 局部变量类型数组
         * 第四个参数0: 操作数栈元素数量
         * 第五个参数null: 操作数栈元素类型数组
         * 
         * 这是JDK 1.6+要求的验证信息，COMPUTE_FRAMES会自动计算。
         */
        mv.visitFrame(0, 0, null, 0, null);
        
        /**
         * visitMaxs: 设置方法的操作数栈大小和局部变量表大小
         * 
         * 【参数说明】
         * 1. 第一个参数：操作数栈最大深度
         * 2. 第二个参数：局部变量表大小
         * 
         * 【为什么是(0, 0)？】
         * 因为创建ClassWriter时使用了COMPUTE_MAXS标志，
         * ASM会自动计算正确的值，这里填什么都会被覆盖。
         * 但必须调用这个方法，否则字节码不完整。
         */
        mv.visitMaxs(0, 0);
        
        // 标记方法定义结束，必须调用！
        mv.visitEnd();
    }

    /**
     * 创建main方法
     * 
     * 【生成的Java代码等效于】
     * public static void main(String[] args) {
     *     // List list = new ArrayList<>();
     *     List list = new ArrayList<>();
     *     
     *     // list.add("hello word!");
     *     list.add("hello word!");
     *     
     *     // String str = list.get(0);
     *     String str = list.get(0);
     * }
     * 
     * @param cw ClassWriter，用于添加方法
     */
    private static void createMainMethod(ClassWriter cw) {
        /**
         * 创建main方法
         * 
         * 【参数详解】
         * 1. ACC_PUBLIC | ACC_STATIC: 访问标志
         *    - ACC_PUBLIC: public访问级别
         *    - ACC_STATIC: 静态方法
         *    可以用|组合多个标志
         * 
         * 2. "main": 方法名
         * 
         * 3. "([Ljava/lang/String;)V": 方法描述符
         *    ([Ljava/lang/String;) 表示参数是String数组
         *    [ 表示数组
         *    Ljava/lang/String; 表示String类型
         *    V 表示void返回类型
         * 
         * 4. null: 泛型签名
         * 
         * 5. null: 异常列表
         */
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null, null);
        
        // 标记方法代码开始
        mv.visitCode();
        
        /**
         * 【字节码指令序列1】List list = new ArrayList<>();
         * 
         * 等效Java代码：List list = new ArrayList<>();
         * 
         * 【步骤1】创建ArrayList对象
         * 
         * NEW: 创建新对象
         * 参数：Type.getInternalName(ArrayList.class) = "java/util/ArrayList"
         * 
         * 【执行效果】
         * 在堆上分配内存，返回对象引用压入栈
         * 栈：[ArrayList引用]
         * 
         * 注意：NEW只是分配内存，还没有调用构造方法初始化！
         */
        mv.visitTypeInsn(NEW, Type.getInternalName(ArrayList.class));
        
        /**
         * 【步骤2】复制栈顶引用
         * 
         * DUP: 复制栈顶元素
         * 
         * 【为什么需要DUP？】
         * 因为INVOKESPECIAL会消耗栈顶的this引用，
         * 但我们需要保留一个引用用于后续的ASTORE（存储到局部变量表）。
         * 所以先复制一份，一份用于调用构造方法，一份用于存储。
         * 
         * 【执行效果】
         * 栈：[ArrayList引用, ArrayList引用]
         */
        mv.visitInsn(DUP);
        
        /**
         * 【步骤3】调用ArrayList的构造方法
         * 
         * INVOKESPECIAL: 调用构造方法
         * 
         * 【参数】
         * 1. Type.getInternalName(ArrayList.class): 类名
         * 2. "<init>": 构造方法名
         * 3. "()V": 无参构造方法
         * 4. false: 不是接口方法
         * 
         * 【执行效果】
         * 消耗栈顶的this引用，调用ArrayList.<init>()
         * 栈：[ArrayList引用]（还剩一个引用）
         */
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(ArrayList.class), "<init>", "()V", false);
        
        /**
         * 【步骤4】将ArrayList引用存储到局部变量表
         * 
         * ASTORE 1: 将栈顶的引用类型值存储到局部变量表位置1
         * 
         * 【为什么是位置1？】
         * 静态方法的局部变量表：
         * - 位置0: 第一个参数（args）
         * - 位置1: 第二个局部变量（list）
         * - 位置2: 第三个局部变量（str）
         * 
         * 静态方法没有this，所以位置0是第一个参数。
         * 
         * 【执行效果】
         * 消耗栈顶的ArrayList引用，存储到局部变量表位置1
         * 栈：[]（空）
         * 局部变量表：[args, list, ...]
         */
        mv.visitVarInsn(ASTORE, 1);

        /**
         * 【字节码指令序列2】list.add("hello word!");
         * 
         * 等效Java代码：list.add("hello word!");
         * 
         * 【步骤1】加载list引用
         * 
         * ALOAD 1: 从局部变量表位置1加载引用类型值
         * 
         * 【执行效果】
         * 栈：[list引用]
         */
        mv.visitVarInsn(ALOAD, 1);
        
        /**
         * 【步骤2】加载字符串常量
         * 
         * visitLdcInsn: 将常量加载到操作数栈
         * 这里加载的是字符串常量"hello word!"
         * 
         * 【执行效果】
         * 栈：[list引用, "hello word!"]
         */
        mv.visitLdcInsn("hello word!");
        
        /**
         * 【步骤3】调用List.add()方法
         * 
         * INVOKEINTERFACE: 调用接口方法
         * 
         * 【为什么用INVOKEINTERFACE？】
         * 因为List是接口，必须用INVOKEINTERFACE调用。
         * 这是JVM规范，调用接口方法必须用这个指令。
         * 
         * 【参数详解】
         * 1. Type.getInternalName(List.class): 接口名，"java/util/List"
         * 2. "add": 方法名
         * 3. "(Ljava/lang/String;)V": 方法描述符
         *    (Ljava/lang/String;) 表示参数是String
         *    V 表示void返回类型
         * 4. true: 是接口方法
         * 
         * 【执行效果】
         * 消耗栈顶的list引用和字符串，调用list.add("hello word!")
         * 栈：[]（空）
         */
        mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(List.class), "add", "(Ljava/lang/String;)V", true);

        /**
         * 【字节码指令序列3】String str = list.get(0);
         * 
         * 等效Java代码：String str = list.get(0);
         * 
         * 【步骤1】加载list引用
         * 
         * ALOAD 1: 从局部变量表位置1加载引用类型值
         * 
         * 【执行效果】
         * 栈：[list引用]
         */
        mv.visitVarInsn(ALOAD, 1);
        
        /**
         * 【步骤2】加载int常量0
         * 
         * ICONST_0: 将int常量0压入栈
         * 
         * JVM提供了快捷指令加载0-5的整数：
         * - ICONST_0: 压入0
         * - ICONST_1: 压入1
         * - ICONST_2: 压入2
         * - ICONST_3: 压入3
         * - ICONST_4: 压入4
         * - ICONST_5: 压入5
         * 
         * 【执行效果】
         * 栈：[list引用, 0]
         */
        mv.visitInsn(ICONST_0);
        
        /**
         * 【步骤3】调用List.get()方法
         * 
         * INVOKEINTERFACE: 调用接口方法
         * 
         * 【参数】
         * 1. Type.getInternalName(List.class): 接口名
         * 2. "get": 方法名
         * 3. "(I)Ljava/lang/String;": 方法描述符
         *    (I) 表示参数是int
         *    Ljava/lang/String; 表示返回String类型
         * 4. true: 是接口方法
         * 
         * 【执行效果】
         * 消耗栈顶的list引用和int索引，调用list.get(0)
         * 压入返回值（String引用）
         * 栈：[String引用]
         */
        mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(List.class), "get", "(I)Ljava/lang/String;", true);
        
        /**
         * 【步骤4】类型检查（CHECKCAST）
         * 
         * CHECKCAST: 检查栈顶对象是否是指定类型
         * 
         * 【为什么需要CHECKCAST？】
         * 因为List.get()的返回类型是Object（泛型擦除），
         * 但我们要赋值给String类型的变量。
         * 所以需要显式类型转换，确保类型安全。
         * 
         * 等效Java代码：String str = (String) list.get(0);
         * 
         * 【执行效果】
         * 检查栈顶对象是否是String类型，如果是则通过，否则抛出ClassCastException
         * 栈：[String引用]
         */
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(String.class));
        
        /**
         * 【步骤5】将String引用存储到局部变量表
         * 
         * ASTORE 2: 将栈顶的引用类型值存储到局部变量表位置2
         * 
         * 【执行效果】
         * 消耗栈顶的String引用，存储到局部变量表位置2
         * 栈：[]（空）
         * 局部变量表：[args, list, str]
         */
        mv.visitVarInsn(ASTORE, 2);

        // RETURN: void方法的返回指令
        mv.visitInsn(RETURN);
        
        // 设置栈帧信息（COMPUTE_FRAMES会自动计算）
        mv.visitFrame(0, 0, null, 0, null);
        
        // 设置栈大小和局部变量表大小（COMPUTE_MAXS会自动计算）
        mv.visitMaxs(0, 0);
        
        // 标记方法定义结束
        mv.visitEnd();
    }


}
