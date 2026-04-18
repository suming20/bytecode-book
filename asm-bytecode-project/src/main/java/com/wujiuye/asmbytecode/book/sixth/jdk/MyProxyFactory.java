package com.wujiuye.asmbytecode.book.sixth.jdk;

import org.objectweb.asm.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static org.objectweb.asm.Opcodes.*;

/**
 * 动态代理类工厂 - 使用ASM字节码框架动态生成代理类
 *
 * 【背景知识】
 * ASM是一个Java字节码操作框架，可以直接在字节码层面创建、修改类。
 * Java的字节码是JVM执行的指令集，类似于汇编语言。
 * <p>
 * 【核心概念】
 * 1. ClassWriter: ASM提供的类写入器，用于生成字节码
 * 2. MethodVisitor: 方法访问器，用于生成方法的字节码指令
 * 3. 操作码(Opcodes): JVM指令，如ALOAD(加载引用)、INVOKEVIRTUAL(调用虚方法)等
 * 4. 操作数栈: JVM执行字节码时使用的栈结构，所有操作都基于栈
 *
 * 【本类的作用】
 * 动态创建一个代理类，类似于JDK的Proxy.newProxyInstance()，
 * 但这里是手动用ASM字节码生成，而不是用反射API。
 * 代理类会实现指定的接口，并将方法调用转发给InvocationHandler。
 */
public class MyProxyFactory {

    /**
     * 将Class数组转换为ASM需要的内部名称数组
     * 
     * 【为什么要转换？】
     * ASM使用"内部名称"格式来表示类名，规则是：
     * - java.lang.String -> "java/lang/String" (用/代替.)
     * - 这是JVM字节码中使用的标准格式
     * 
     * @param classes Java的Class对象数组，如[Runnable.class, Serializable.class]
     * @return ASM格式的内部名称数组，如["java/lang/Runnable", "java/io/Serializable"]
     */
    private static String[] getInternalNames(Class<?>[] classes) {
        String[] names = new String[classes.length];
        for (int i = 0; i < names.length; i++) {
            // Type.getInternalName()会将Class转换为ASM内部名称格式
            names[i] = Type.getInternalName(classes[i]);
        }
        return names;
    }

    /**
     * 创建代理类的字节码
     * 
     * 【方法作用】
     * 动态生成一个代理类的字节码数组(byte[])，这个类会：
     * 1. 继承MyProxy类（提供InvocationHandler支持）
     * 2. 实现传入的所有接口
     * 3. 实现接口方法，转发给InvocationHandler处理
     * 
     * 【参数说明】
     * @param className 要生成的代理类的全限定名，如"com/example/MyProxy0"
     *                  注意：这里使用/而不是.，因为这是JVM内部名称格式
     * @param interfaces 代理类要实现的接口数组，如[Runnable.class]
     * 
     * @return 生成的代理类的字节码数组，可以用ClassLoader加载
     * 
     * 【执行流程】
     * 1. 创建ClassWriter（字节码写入器）
     * 2. 定义类的基本信息（版本、访问标志、父类、接口）
     * 3. 生成构造方法
     * 4. 生成接口方法的实现
     * 5. 生成静态代码块（初始化Method对象）
     * 6. 返回字节码
     */
    public static byte[] createProxyClass(String className, Class<?>[] interfaces) {
        /**
         * 创建ClassWriter - ASM的核心类，用于生成字节码
         * 
         * 【参数说明】ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES
         * - COMPUTE_MAXS: 自动计算方法的操作数栈大小和局部变量表大小
         *   否则需要手动计算，非常复杂！
         * - COMPUTE_FRAMES: 自动计算栈帧（StackMapTable），JDK 1.6+必需
         *   这是JVM验证字节码安全性的数据结构
         * 
         * 【为什么用这两个标志？】
         * 手动计算栈大小极其复杂，需要考虑每条指令对栈的影响。
         * 使用这两个标志可以让ASM自动计算，大大简化开发。
         */
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        
        /**
         * 定义类的基本信息
         * 
         * 【参数详解】
         * 1. Opcodes.V1_8: 类版本号，表示生成Java 8兼容的字节码
         *    其他选项：V1_7, V1_9, V10等
         * 
         * 2. ACC_PUBLIC: 访问标志，表示这是一个public类
         *    其他常用标志：ACC_PRIVATE, ACC_FINAL, ACC_ABSTRACT等
         * 
         * 3. className: 类的内部名称，如"com/example/MyProxy0"
         *    注意使用/而不是.，这是JVM的标准格式
         * 
         * 4. null: 泛型签名，普通类不需要，设为null
         *    如果有泛型如<T>，需要提供签名描述
         * 
         * 5. Type.getInternalName(MyProxy.class): 父类的内部名称
         *    表示生成的代理类继承MyProxy类
         *    转换为："com/wujiuye/asmbytecode/book/sixth/jdk/MyProxy"
         * 
         * 6. getInternalNames(interfaces): 实现的接口数组
         *    如：["java/lang/Runnable"]
         */
        cw.visit(Opcodes.V1_8, ACC_PUBLIC, className, null,
                Type.getInternalName(MyProxy.class),
                getInternalNames(interfaces));
        
        // 添加构造方法，调用父类MyProxy的构造方法
        // 代理类需要接收InvocationHandler参数并传递给父类
        createInitMethod(cw);
        
        // 为每个接口生成方法实现
        // 每个接口方法都会转发给InvocationHandler.invoke()
        for (Class<?> interfaceClass : interfaces) {
            implInterfaceMethod(cw, className, interfaceClass);
        }
        
        // 添加静态代码块（<clinit>方法）
        // 用于初始化静态字段，缓存Method对象（类似反射的Method缓存）
        addStaticBlock(cw, className, interfaces);
        
        // 结束类的定义，必须调用！
        cw.visitEnd();
        
        // 返回生成的字节码数组
        return cw.toByteArray();
    }

    /**
     * 添加静态代码块（类初始化方法）
     * 
     * 【什么是<clinit>？】
 * <clinit>是JVM的类初始化方法（class initialization），
     * 在类第一次被加载时自动执行，且只执行一次。
     * Java中的静态代码块（static {}）和静态字段初始化都会被编译到这个方法的。
     * 
     * 【这个方法的作用】
     * 为每个接口方法创建对应的Method对象缓存，存储在静态字段中。
     * 例如：Runnable.run() 方法会生成：
     * - 静态字段：private static Method _Runnable_0
     * - 初始化代码：_Runnable_0 = Class.forName("Runnable").getMethod("run")
     * 
     * 【为什么要缓存Method对象？】
     * 因为InvocationHandler.invoke()需要Method对象作为参数。
     * 如果每次调用都反射获取Method，性能会很差。
     * 所以在类加载时一次性获取并缓存，后续直接使用。
     * 
     * @param cw ClassWriter，用于添加方法
     * @param className 代理类名
     * @param interfaces 要实现的接口数组
     */
    private static void addStaticBlock(ClassWriter cw, String className, Class<?>[] interfaces) {
        /**
         * 创建<clinit>方法（静态初始化方法）
         * 
         * 【参数详解】
         * 1. ACC_STATIC: 访问标志，表示这是静态方法
         * 
         * 2. "<clinit>": 方法名，JVM规定的类初始化方法名
         *    注意：不是"<init>"（那是实例构造方法）
         * 
         * 3. "()V": 方法描述符，表示方法签名
         *    格式：(参数类型)返回类型
         *    () 表示无参数
         *    V 表示void返回类型
         *    其他示例："(I)V"表示(int)void，"(Ljava/lang/String;)I"表示(String)int
         * 
         * 4. null: 泛型签名，不需要
         * 
         * 5. null: 异常列表，<clinit>不声明抛出的异常
         */
        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V",
                null, null);
        
        // 标记方法代码开始，必须调用！
        mv.visitCode();
        
        // 遍历所有接口，为每个方法创建Method缓存
        for (Class cla : interfaces) {
            // 获取接口的所有public方法
            Method[] methods = cla.getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];

                // 生成静态字段名，格式：_接口名_方法索引
                // 例如：Runnable的第一个方法 -> "_Runnable_0"
                String fieldName = "_" + cla.getSimpleName() + "_" + i;
                
                /**
                 * 【字节码指令序列】生成等效于以下Java代码的字节码：
                 * _Runnable_0 = Class.forName("java.lang.Runnable")
                 *                        .getMethod("run", new Class[0]);
                 * 
                 * 【步骤1】调用Class.forName()获取Class对象
                 * 
                 * visitLdcInsn(): 将常量加载到操作数栈
                 * - 这里加载的是字符串常量cla.getName()，如"java.lang.Runnable"
                 * - 执行后，栈顶是："java.lang.Runnable"
                 */
                mv.visitLdcInsn(cla.getName());
                
                /**
                 * 【步骤2】调用Class.forName()静态方法
                 * 
                 * visitMethodInsn(): 调用方法指令
                 * 
                 * 【参数详解】
                 * 1. INVOKESTATIC: 调用静态方法的操作码
                 *    其他选项：
                 *    - INVOKEVIRTUAL: 调用实例方法（最常用）
                 *    - INVOKESPECIAL: 调用构造方法、私有方法、super方法
                 *    - INVOKEINTERFACE: 调用接口方法
                 * 
                 * 2. Type.getInternalName(Class.class): 类名，"java/lang/Class"
                 * 
                 * 3. "forName": 方法名
                 * 
                 * 4. "(Ljava/lang/String;)Ljava/lang/Class;": 方法描述符
                 *    (Ljava/lang/String;) 表示参数是String
                 *    Ljava/lang/Class; 表示返回Class对象
                 *    L...; 表示引用类型，必须以;结尾
                 * 
                 * 5. false: 是否是接口方法，Class不是接口所以是false
                 * 
                 * 【执行效果】
                 * 栈变化：
                 * 执行前：["java.lang.Runnable"]
                 * 执行后：[Class对象]  (消耗了String，压入了Class对象)
                 */
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Class.class),
                        "forName",
                        "(Ljava/lang/String;)Ljava/lang/Class;",
                        false);

                /**
                 * 【步骤3】准备调用Class.getMethod()的参数
                 * getMethod需要两个参数：
                 * 1. 方法名（String）
                 * 2. 参数类型数组（Class[]）
                 * 
                 * 先准备第一个参数：方法名
                 */
                mv.visitLdcInsn(method.getName());
                
                /**
                 * 【步骤4】准备第二个参数：参数类型数组
                 * 
                 * 如果方法没有参数（如Runnable.run()），直接压入null
                 * 如果有参数，需要创建Class[]数组并填充
                 */
                Class[] methodParamTypes = method.getParameterTypes();
                if (methodParamTypes.length == 0) {
                    // ACONST_NULL: 将null引用压入栈
                    mv.visitInsn(ACONST_NULL);
                } else {
                    /**
                     * 【步骤4.1】将数组长度压入栈
                     * 
                     * JVM提供了快捷指令加载0-5的整数：
                     * - ICONST_0: 压入0
                     * - ICONST_1: 压入1
                     * - ICONST_2: 压入2
                     * - ICONST_3: 压入3
                     * - ICONST_4: 压入4
                     * - ICONST_5: 压入5
                     * 
                     * 超过5的整数需要使用BIPUSH指令
                     */
                    switch (methodParamTypes.length) {
                        case 1:
                            mv.visitInsn(ICONST_1);
                            break;
                        case 2:
                            mv.visitInsn(ICONST_2);
                            break;
                        case 3:
                            mv.visitInsn(ICONST_3);
                            break;
                        default:
                            // BIPUSH: 压入一个byte范围的整数（-128到127）
                            mv.visitVarInsn(BIPUSH, methodParamTypes.length);
                    }
                    
                    /**
                     * 【步骤4.2】创建数组
                     * 
                     * ANEWARRAY: 创建引用类型数组
                     * 参数：Type.getInternalName(Class.class) = "java/lang/Class"
                     * 
                     * 【执行效果】
                     * 栈变化：
                     * 执行前：[数组长度]
                     * 执行后：[新创建的数组引用]
                     * 
                     * 等效Java代码：new Class[数组长度]
                     */
                    mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Class.class));
                    
                    /**
                     * 【步骤4.3】为数组的每个元素赋值
                     * 
                     * 需要循环执行：array[index] = Class.forName(参数类型名)
                     * 
                     * 【关键指令】DUP
                     * DUP指令复制栈顶元素，因为AASTORE会消耗数组引用
                     * 如果不DUP，第一次赋值后数组引用就没了，后续无法继续赋值
                     * 
                     * 数组赋值的字节码模式：
                     * DUP              // 复制数组引用
                     * index            // 压入索引
                     * value            // 压入值
                     * AASTORE          // 执行 array[index] = value
                     */
                    for (int index = 0; index < methodParamTypes.length; index++) {
                        // DUP: 复制栈顶的数组引用
                        mv.visitInsn(DUP);
                        
                        // 压入数组索引（0, 1, 2, 3...）
                        switch (index) {
                            case 0:
                                mv.visitInsn(ICONST_0);
                                break;
                            case 1:
                                mv.visitInsn(ICONST_1);
                                break;
                            case 2:
                                mv.visitInsn(ICONST_2);
                                break;
                            case 3:
                                mv.visitInsn(ICONST_3);
                                break;
                            default:
                                // 注意：这里原代码有个bug，应该用index而不是i
                                mv.visitVarInsn(BIPUSH, index);
                                break;
                        }
                        
                        // 压入参数类型名，如"java.lang.String"
                        mv.visitLdcInsn(methodParamTypes[index].getName());
                        
                        // 调用Class.forName()获取参数的Class对象
                        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Class.class),
                                "forName",
                                "(Ljava/lang/String;)Ljava/lang/Class;",
                                false);
                        
                        /**
                         * AASTORE: 存储引用到数组
                         * 
                         * 【执行效果】
                         * 消耗栈顶3个元素：array, index, value
                         * 执行：array[index] = value
                         * 
                         * AA表示：Array of Reference (引用类型数组)
                         * 其他类型：IASTORE(int), FASTORE(float)等
                         */
                        mv.visitInsn(AASTORE);
                    }
                }
                
                /**
                 * 【步骤5】调用Class.getMethod()获取Method对象
                 * 
                 * 此时栈中应该有：
                 * - Class对象（fromName返回的）
                 * - 方法名（String）
                 * - 参数类型数组（Class[]）
                 * 
                 * INVOKEVIRTUAL: 调用实例方法
                 * 方法描述符：(String, Class[]) -> Method
                 */
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Class.class),
                        "getMethod",
                        "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                
                /**
                 * 【步骤6】将Method对象存储到静态字段
                 * 
                 * PUTSTATIC: 设置静态字段的值
                 * 
                 * 【参数详解】
                 * 1. className: 类名，如"com/example/MyProxy0"
                 * 2. fieldName: 字段名，如"_Runnable_0"
                 * 3. Type.getDescriptor(Method.class): 字段类型描述符
                 *    返回："Ljava/lang/reflect/Method;"
                 * 
                 * 【执行效果】
                 * 消耗栈顶的Method对象，赋值给静态字段
                 * 等效Java代码：_Runnable_0 = Method对象
                 */
                mv.visitFieldInsn(PUTSTATIC, className, fieldName, Type.getDescriptor(Method.class));
            }
        }
        
        // RETURN: void方法的返回指令
        mv.visitInsn(RETURN);
        
        /**
         * visitMaxs: 设置方法的操作数栈大小和局部变量表大小
         * 
         * 【参数说明】
         * 1. 第一个参数：操作数栈最大深度
         * 2. 第二个参数：局部变量表大小
         * 
         * 【为什么是(1, 1)？】
         * 因为创建ClassWriter时使用了COMPUTE_MAXS标志，
         * ASM会自动计算正确的值，这里填什么都会被覆盖。
         * 但必须调用这个方法，否则字节码不完整。
         */
        mv.visitMaxs(1, 1);
        
        // 标记方法定义结束，必须调用！
        mv.visitEnd();
    }

    /**
     * 创建代理类的构造方法
     * 
     * 【生成的Java代码等效于】
     * public MyProxy0(InvocationHandler h) {
     *     super(h);  // 调用父类MyProxy的构造方法
     * }
     * 
     * 【为什么需要构造方法？】
     * 代理类继承自MyProxy，MyProxy需要InvocationHandler参数。
     * 所以代理类必须有一个构造方法接收这个参数并传递给父类。
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
         * 3. "(Ljava/lang/reflect/InvocationHandler;)V": 方法描述符
         *    (Ljava/lang/reflect/InvocationHandler;) 表示参数是InvocationHandler
         *    V 表示返回void（构造方法没有返回值）
         * 
         * 4. null: 泛型签名，不需要
         * 
         * 5. null: 异常列表，不声明抛出异常
         */
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>",
                "(Ljava/lang/reflect/InvocationHandler;)V",
                null, null);
        
        // 标记方法代码开始
        mv.visitCode();
        
        /**
         * 【字节码指令序列】生成等效于 super(h) 的字节码
         * 
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
         * 静态方法没有this，所以位置0是第一个参数。实例方法的位置0是this
         */
        mv.visitVarInsn(ALOAD, 0);
        
        /**
         * 【步骤2】加载InvocationHandler参数
         * 
         * ALOAD 1: 加载局部变量表第1个位置的引用类型值
         * 这就是构造方法的参数h
         */
        mv.visitVarInsn(ALOAD, 1);
        
        /**
         * 【步骤3】调用父类构造方法
         * 
         * INVOKESPECIAL: 调用特殊方法（构造方法、私有方法、super方法）
         * 
         * 【为什么用INVOKESPECIAL而不是INVOKEVIRTUAL？】
         * - 构造方法必须用INVOKESPECIAL，这是JVM规范
         * - INVOKESPECIAL不会进行动态绑定，直接调用指定类的方法
         * - INVOKEVIRTUAL会进行动态绑定，可能调用子类重写的方法
         * 
         * 【参数详解】
         * 1. Type.getInternalName(MyProxy.class): 父类名
         * 2. "<init>": 构造方法名
         * 3. "(Ljava/lang/reflect/InvocationHandler;)V": 方法描述符
         * 4. false: 是否是接口方法
         * 
         * 【执行效果】
         * 消耗栈顶的this和h，调用MyProxy.<init>(h)
         * 等效Java代码：super(h)
         */
        mv.visitMethodInsn(INVOKESPECIAL,
                Type.getInternalName(MyProxy.class),
                "<init>",
                "(Ljava/lang/reflect/InvocationHandler;)V", false);
        
        // RETURN: void方法的返回指令
        mv.visitInsn(RETURN);
        
        // 设置栈大小和局部变量表大小（COMPUTE_MAXS会自动计算）
        mv.visitMaxs(2, 2);
        
        // 标记方法定义结束
        mv.visitEnd();
    }

    /**
     * 实现接口的所有方法
     * 
     * 【这个方法的作用】
     * 为接口的每个方法：
     * 1. 添加一个静态字段（用于缓存Method对象）
     * 2. 生成方法实现（转发给InvocationHandler.invoke()）
     * 
     * 【生成的Java代码等效于】
     * // 静态字段
     * private static Method _Runnable_0;
     * 
     * // 方法实现
     * public void run() throws Exception {
     *     try {
     *         // 调用InvocationHandler.invoke()
     *         super.h.invoke(this, _Runnable_0, null);
     *     } catch (Exception e) {
     *         throw e;
     *     }
     * }
     * 
     * @param cw ClassWriter
     * @param className 代理类名
     * @param interfaceClass 要实现的接口
     */
    private static void implInterfaceMethod(ClassWriter cw, String className, Class<?> interfaceClass) {
        Method[] methods = interfaceClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            
            /**
             * 【步骤1】添加静态字段，用于缓存Method对象
             * 
             * 【参数详解】
             * 1. ACC_PRIVATE | ACC_STATIC: 访问标志
             *    - ACC_PRIVATE: 私有字段
             *    - ACC_STATIC: 静态字段
             *    可以用|组合多个标志
             * 
             * 2. "_Runnable_0": 字段名
             * 
             * 3. Type.getDescriptor(Method.class): 字段类型描述符
             *    返回："Ljava/lang/reflect/Method;"
             *    L表示引用类型，必须以;结尾
             * 
             * 4. null: 泛型签名，不需要
             * 
             * 5. null: 初始值，静态字段在<clinit>中初始化，这里不需要
             * 
             * 【等效Java代码】
             * private static Method _Runnable_0;
             */
            cw.visitField(ACC_PRIVATE | ACC_STATIC, "_" + interfaceClass.getSimpleName() + "_" + i,
                    Type.getDescriptor(Method.class),
                    null, null);

            /**
             * 【步骤2】生成方法实现
             * 
             * 【参数详解】
             * 1. ACC_PUBLIC: public访问级别
             * 
             * 2. method.getName(): 方法名，如"run"
             * 
             * 3. Type.getMethodDescriptor(method): 自动生成方法描述符
             *    例如：Runnable.run() -> "()V"
             *         ActionListener.actionPerformed(ActionEvent) -> "(Ljava/awt/event/ActionEvent;)V"
             * 
             * 4. null: 泛型签名
             * 
             * 5. new String[]{Type.getInternalName(Exception.class)}: 声明抛出的异常
             *    等效于Java的throws Exception
             *    因为InvocationHandler.invoke()可能抛出异常
             */
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, method.getName(),
                    Type.getMethodDescriptor(method),
                    null,
                    new String[]{Type.getInternalName(Exception.class)});
            
            // 标记方法代码开始
            mv.visitCode();

            /**
             * 【Try-Catch块的定义】
             * 
             * JVM的异常处理基于标签（Label）范围：
             * - from: Try块开始位置
             * - to: Try块结束位置
             * - target: Catch块开始位置
             * - exceptionType: 捕获的异常类型
             * 
             * 等效Java代码：
             * try {
             *     // from 到 to 之间的代码
             * } catch (Exception e) {
             *     // target 位置的代码
             *     throw e;
             * }
             */
            Label from = new Label();
            Label to = new Label();
            Label target = new Label();

            // 标记Try块开始
            mv.visitLabel(from);

            /**
             * 【步骤3】获取InvocationHandler对象（父类的h字段）
             * 
             * 【指令序列】
             * ALOAD 0: 加载this引用
             * GETFIELD: 获取字段值
             * 
             * GETFIELD参数：
             * 1. Type.getInternalName(MyProxy.class): 类名
             * 2. "h": 字段名（MyProxy类中存储InvocationHandler的字段）
             * 3. Type.getDescriptor(InvocationHandler.class): 字段类型
             * 
             * 【执行效果】
             * 消耗this，压入this.h（InvocationHandler对象）
             * 栈：[InvocationHandler]
             */
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD,
                    Type.getInternalName(MyProxy.class),
                    "h",
                    Type.getDescriptor(InvocationHandler.class));

            /**
             * 【步骤4】准备InvocationHandler.invoke()的三个参数
             * 
             * invoke方法签名：
             * Object invoke(Object proxy, Method method, Object[] args)
             * 
             * 此时栈中已有：[InvocationHandler]
             * 还需要压入：this, Method对象, 参数数组
             */
            
            // 第一个参数：this（代理对象本身）
            mv.visitVarInsn(ALOAD, 0);
            
            // 第二个参数：Method对象（从静态字段获取）
            mv.visitFieldInsn(GETSTATIC,
                    className,
                    "_" + interfaceClass.getSimpleName() + "_" + i,
                    Type.getDescriptor(Method.class));
            
            // 第三个参数：方法参数数组（Object[]）
            int paramCount = method.getParameterCount();
            if (paramCount == 0) {
                // 没有参数，直接压入null
                mv.visitInsn(ACONST_NULL);
            } else {
                /**
                 * 【步骤4.1】创建Object数组
                 * 
                 * 先将数组长度压入栈
                 */
                switch (paramCount) {
                    case 1:
                        mv.visitInsn(ICONST_1);
                        break;
                    case 2:
                        mv.visitInsn(ICONST_2);
                        break;
                    case 3:
                        mv.visitInsn(ICONST_3);
                        break;
                    default:
                        mv.visitVarInsn(BIPUSH, paramCount);
                }
                
                // 创建Object[]数组
                mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
                
                /**
                 * 【步骤4.2】为数组元素赋值
                 * 
                 * 将当前方法的每个参数放入数组
                 * 等效Java代码：
                 * Object[] args = new Object[paramCount];
                 * args[0] = param1;
                 * args[1] = param2;
                 * ...
                 * 
                 * 【注意】局部变量表的索引
                 * 位置0: this
                 * 位置1: 第一个参数
                 * 位置2: 第二个参数
                 * ...
                 * 所以循环从index=1开始，对应第一个方法参数
                 */
                for (int index = 1; index <= paramCount; index++) {
                    // DUP: 复制数组引用（AASTORE会消耗它）
                    mv.visitInsn(DUP);
                    
                    // 压入数组索引（0, 1, 2...）
                    switch (index - 1) {
                        case 0:
                            mv.visitInsn(ICONST_0);
                            break;
                        case 1:
                            mv.visitInsn(ICONST_1);
                            break;
                        case 2:
                            mv.visitInsn(ICONST_2);
                            break;
                        case 3:
                            mv.visitInsn(ICONST_3);
                            break;
                        default:
                            mv.visitVarInsn(BIPUSH, index - 1);
                            break;
                    }
                    
                    // 加载方法参数（从局部变量表）
                    // 暂不考虑基本数据类型，只处理引用类型
                    mv.visitVarInsn(ALOAD, index);
                    
                    // 存储到数组：array[index-1] = param
                    mv.visitInsn(AASTORE);
                }
            }

            /**
             * 【步骤5】调用InvocationHandler.invoke()方法
             * 
             * INVOKEINTERFACE: 调用接口方法
             * 
             * 【为什么用INVOKEINTERFACE？】
             * 因为InvocationHandler是接口，必须用INVOKEINTERFACE调用。
             * 这是JVM规范，调用接口方法必须用这个指令。
             * 
             * 【参数详解】
             * 1. Type.getInternalName(InvocationHandler.class): 接口名
             * 2. "invoke": 方法名
             * 3. "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"
             *    方法描述符：(Object, Method, Object[]) -> Object
             *    [ 表示数组
             * 4. true: 是接口方法
             * 
             * 【执行效果】
             * 消耗栈顶4个元素：InvocationHandler, this, Method, Object[]
             * 压入返回值：Object
             * 栈：[Object]
             */
            mv.visitMethodInsn(INVOKEINTERFACE,
                    Type.getInternalName(InvocationHandler.class),
                    "invoke",
                    "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
                    true);

            /**
             * 【步骤6】添加return指令
             * 
             * 根据方法的返回类型，生成不同的返回指令：
             * - void: RETURN
             * - int: IRETURN（需要先将Object转为int）
             * - 引用类型: ARETURN（需要CHECKCAST类型转换）
             * 
             * 这个函数会处理类型转换和返回
             */
            addReturnInstruc(mv, method.getReturnType());

            // 标记Try块结束
            mv.visitLabel(to);
            
            // 标记Catch块开始
            mv.visitLabel(target);
            
            /**
             * 【Catch块代码】抛出异常
             * 
             * ATHROW: 抛出栈顶的异常对象
             * 
             * 此时栈顶应该是catch捕获的Exception对象
             */
            mv.visitInsn(ATHROW);
            
            /**
             * 【注册Try-Catch块】
             * 
             * 告诉JVM：
             * 从from到to之间的代码，如果抛出Exception类型异常，
             * 就跳转到target位置处理
             */
            mv.visitTryCatchBlock(from, to, target, Type.getInternalName(Exception.class));

            /**
             * 【栈帧信息】
             * 
             * visitFrame: 设置栈帧状态（StackMapTable）
             * 
             * F_FULL: 完整的栈帧描述
             * 参数：
             * - 0: 局部变量数量（这里用null表示）
             * - null: 局部变量类型数组
             * - 0: 操作数栈元素数量
             * - null: 操作数栈元素类型数组
             * 
             * 这是JDK 1.6+要求的验证信息，COMPUTE_FRAMES会自动计算，
             * 但某些情况下需要手动提供。
             */
            mv.visitFrame(F_FULL, 0, null, 0, null);
            
            // 设置栈大小和局部变量表大小
            mv.visitMaxs(1, 1);
            
            // 标记方法定义结束
            mv.visitEnd();
        }
    }

    /**
     * 添加return指令和必要的类型转换
     * 
     * 【背景知识】
     * JVM的返回指令根据返回类型不同而不同：
     * - RETURN: void类型
     * - IRETURN: int, byte, short, char, boolean
     * - LRETURN: long
     * - FRETURN: float
     * - DRETURN: double
     * - ARETURN: 引用类型（Object, String等）
     * 
     * 【问题】
     * InvocationHandler.invoke()返回的是Object类型，
     * 但接口方法可能返回int、void等具体类型。
     * 所以需要进行类型转换。
     * 
     * @param mv MethodVisitor
     * @param returnType 方法的返回类型
     */
    private static void addReturnInstruc(MethodVisitor mv, Class returnType) {
        if (returnType == void.class) {
            /**
             * void类型：直接返回
             * 
             * 注意：invoke()返回的Object被丢弃了
             * 等效Java代码：
             * super.h.invoke(...);
             * return;
             */
            mv.visitInsn(RETURN);
        } else if (returnType == int.class) {
            /**
             * int类型：需要两次转换
             * 
             * 【步骤1】CHECKCAST: 类型检查并转换
             * 将Object转换为Integer（包装类）
             * 
             * CHECKCAST: 检查栈顶对象是否是指定类型，如果是则通过，否则抛异常
             * 不会改变栈顶元素，只是验证类型
             * 
             * 【步骤2】调用Integer.intValue()
             * 将Integer对象转换为int基本类型
             * 
             * 【步骤3】IRETURN: 返回int
             * 
             * 等效Java代码：
             * return ((Integer) super.h.invoke(...)).intValue();
             */
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Integer.class));
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Integer.class),
                    "intValue",
                    "()I", false);
            mv.visitInsn(IRETURN);
        }
        // .... 其他基本类型（long, float, double等）的处理类似
        else {
            /**
             * 引用类型：CHECKCAST后直接返回
             * 
             * 【步骤1】CHECKCAST: 将Object转换为目标类型
             * 例如：返回String类型，就CHECKCAST "java/lang/String"
             * 
             * 【步骤2】ARETURN: 返回引用类型
             * 
             * 等效Java代码：
             * return (String) super.h.invoke(...);
             */
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType));
            mv.visitInsn(ARETURN);
        }
    }

}

