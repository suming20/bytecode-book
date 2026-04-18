package com.wujiuye.asmbytecode.book.sixth.cglib;

import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * CGLIB子类代理工厂 - 使用ASM字节码框架动态生成子类代理
 * 
 * 【CGLIB vs JDK动态代理的区别】
 * - JDK动态代理：基于接口，只能代理实现了接口的类
 * - CGLIB代理：基于继承，通过继承目标类生成子类来实现代理
 *   所以CGLIB可以代理没有实现接口的类，但不能代理final类
 * 
 * 【核心原理】
 * 1. 动态创建一个类，继承自目标类（superclass）
 * 2. 覆写目标类的所有非final方法
 * 3. 在覆写的方法中，将调用转发给MethodInterceptor
 * 4. MethodInterceptor决定是否调用父类（原始）方法
 * 
 * 【与JDK代理的对比】
 * JDK代理：InvocationHandler.invoke(proxy, method, args)
 * CGLIB代理：MethodInterceptor.intercept(proxy, method, methodName, args)
 * 多了一个methodName参数，并且method是代理方法的Method，不是原始方法
 * 
 * 【ASM字节码基础】
 * - ClassWriter: 生成字节码的核心类
 * - MethodVisitor: 生成方法字节码指令
 * - 操作码(Opcodes): JVM指令，如ALOAD(加载引用)、INVOKEVIRTUAL(调用虚方法)
 * - 操作数栈: JVM执行字节码时使用的栈，所有操作基于栈
 */
public class SubclassProxyFactory {

    /**
     * 不需要拦截的方法列表
     * 
     * 【为什么排除这些方法？】
     * - wait/notify/notifyAll: Object的同步方法，不应该被拦截
     * - equals/toString/hashCode: Object的基础方法，拦截可能导致问题
     * - getClass: 获取类信息的方法，不应该被修改
     * 
     * 这些是Object类的核心方法，CGLIB默认不拦截它们。
     */
    private static final List<String> EXCLUDE_METHOD = Arrays.asList("wait", "equals",
            "toString", "hashCode", "getClass", "notify", "notifyAll");

    /**
     * 创建代理类的字节码
     * 
     * 【方法作用】
     * 动态生成一个子类代理类的字节码，这个类会：
     * 1. 继承指定的父类（superclass）
     * 2. 覆写父类的所有非final、非排除方法
     * 3. 将方法调用转发给MyMethodInterceptor处理
     * 4. 提供调用父类原始方法的代理方法
     * 
     * 【参数说明】
     * @param className 要生成的代理类的全限定名（内部名称格式）
     *                  如："com/wujiuye/HttpRequestTemplateImpl$Proxy"
     *                  注意：使用/而不是.，这是JVM内部名称格式
     * @param superclass 要代理的父类，如HttpRequestTemplateImpl.class
     * 
     * @return 生成的代理类的字节码数组，可以用ClassLoader加载
     * 
     * 【执行流程】
     * 1. 创建ClassWriter
     * 2. 定义类的基本信息（继承superclass）
     * 3. 生成构造方法（接收MyMethodInterceptor参数）
     * 4. 获取需要拦截的方法列表
     * 5. 生成静态代码块（初始化Method对象）
     * 6. 生成调用父类方法的代理方法
     * 7. 覆写父类的方法（转发给拦截器）
     * 8. 返回字节码
     */
    public static byte[] createProxyClass(String className, Class<?> superclass) {
        /**
         * 创建ClassWriter
         * 
         * 【参数说明】
         * - COMPUTE_MAXS: 自动计算方法的操作数栈大小和局部变量表大小
         * - COMPUTE_FRAMES: 自动计算栈帧（StackMapTable），JDK 1.6+必需
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
         * 1. Opcodes.V1_8: Java 8兼容的字节码
         * 2. ACC_PUBLIC: public类
         * 3. className: 类的内部名称，如"com/example/MyClass"
         * 4. null: 泛型签名，不需要
         * 5. Type.getInternalName(superclass): 父类的内部名称
         *    表示生成的代理类继承superclass
         * 6. null: 实现的接口数组，CGLIB代理只继承不实现接口
         */
        cw.visit(Opcodes.V1_8, ACC_PUBLIC, className, null,
                Type.getInternalName(superclass),
                null);
        
        // 创建构造方法，接收MyMethodInterceptor参数
        createInitMethod(cw, className, superclass);
        
        // 获取需要拦截的方法（排除Object的核心方法和final方法）
        Method[] methods = getProxyMethod(superclass);
        if (methods.length > 0) {
            // 添加静态代码块，生成通过反射获取Method的字节码
            addStaticBlock(cw, className, superclass, methods);
            // 覆写父类的方法，将调用转发给拦截器
            overrideMethods(cw, className, superclass, methods);
        }
        
        // 结束类的定义，必须调用！
        cw.visitEnd();
        
        // 返回生成的字节码数组
        return cw.toByteArray();
    }

    /**
     * 创建代理类的构造方法
     * 
     * 【生成的Java代码等效于】
     * public class Proxy extends TargetClass {
     *     private MyMethodInterceptor h;  // 拦截器字段
     *     
     *     public Proxy(MyMethodInterceptor h) {
     *         super();  // 调用父类的无参构造
     *         this.h = h;  // 保存拦截器引用
     *     }
     * }
     * 
     * 【为什么需要这个构造方法？】
     * 代理类需要接收MyMethodInterceptor参数并保存，
     * 这样在覆写的方法中才能调用拦截器的intercept方法。
     * 
     * @param cw ClassWriter，用于添加方法
     * @param className 代理类名
     * @param superclass 父类
     */
    private static void createInitMethod(ClassWriter cw, String className, Class<?> superclass) {
        /**
         * 创建<init>方法（实例构造方法）
         * 
         * 【参数详解】
         * 1. ACC_PUBLIC: public访问级别
         * 
         * 2. "<init>": 构造方法名，JVM规定
         * 
         * 3. "(" + Type.getDescriptor(MyMethodInterceptor.class) + ")V": 方法描述符
         *    动态拼接为："(Lcom/wujiuye/.../MyMethodInterceptor;)V"
         *    L...; 表示引用类型
         *    V 表示void返回（构造方法无返回值）
         * 
         * 4. null: 泛型签名
         * 5. null: 异常列表
         */
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>",
                "(" + Type.getDescriptor(MyMethodInterceptor.class) + ")V",
                null, null);
        
        // 标记方法代码开始
        mv.visitCode();

        /**
         * 【步骤1】调用父类的无参构造方法
         * 
         * ALOAD 0: 加载this引用（局部变量表位置0）
         * 
         * INVOKESPECIAL: 调用特殊方法（构造方法、私有方法、super方法）
         * 
         * 【为什么用INVOKESPECIAL？】
         * - 构造方法必须用INVOKESPECIAL，这是JVM规范
         * - 不会进行动态绑定，直接调用指定类的方法
         * 
         * 【参数】
         * - Type.getInternalName(superclass): 父类名
         * - "<init>": 构造方法名
         * - "()V": 无参构造
         * - false: 不是接口方法
         */
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL,
                Type.getInternalName(superclass),
                "<init>", "()V", false);

        /**
         * 【步骤2】添加字段h（存储拦截器）
         * 
         * 【参数详解】
         * 1. ACC_PRIVATE: 私有字段
         * 2. "h": 字段名
         * 3. Type.getDescriptor(MyMethodInterceptor.class): 字段类型
         * 4. null: 泛型签名
         * 5. null: 初始值（在构造方法中赋值）
         * 
         * 【等效Java代码】
         * private MyMethodInterceptor h;
         */
        cw.visitField(ACC_PRIVATE, "h", Type.getDescriptor(MyMethodInterceptor.class),
                null, null);

        /**
         * 【步骤3】为字段h赋值
         * 
         * ALOAD 0: 加载this
         * ALOAD 1: 加载构造方法参数h（位置1，因为位置0是this）
         * 
         * PUTFIELD: 设置实例字段的值
         * 【参数】
         * - className: 类名
         * - "h": 字段名
         * - Type.getDescriptor(...): 字段类型
         * 
         * 【执行效果】
         * 消耗栈顶的this和h，执行this.h = h
         */
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, className, "h",
                Type.getDescriptor(MyMethodInterceptor.class));

        // RETURN: void方法返回
        mv.visitInsn(RETURN);
        
        // 设置栈大小和局部变量表大小（COMPUTE_MAXS会自动计算）
        mv.visitMaxs(2, 2);
        
        // 标记方法定义结束
        mv.visitEnd();
    }

    /**
     * 获取允许覆写的方法列表
     * 
     * 【过滤规则】
     * 1. 排除Object的核心方法（wait、equals等）
     * 2. 排除final修饰的方法（final方法不能被覆写）
     * 
     * 【为什么用getMethods()？】
     * getMethods()返回所有public方法，包括：
     * - 当前类声明的public方法
     * - 父类继承的public方法
     * 这正是我们需要的，因为代理类要拦截所有public方法。
     * 
     * @param superclass 父类
     * @return 需要拦截的方法数组
     */
    private static Method[] getProxyMethod(Class<?> superclass) {
        // 获取superclass类的所有public方法，包括superclass的父类中的public方法
        Method[] methods = superclass.getMethods();
        List<Method> methodList = new ArrayList<>(methods.length);
        for (Method method : methods) {
            // 过滤不需要覆写的方法
            if (EXCLUDE_METHOD.contains(method.getName())) {
                continue;
            }
            /**
             * 跳过final修饰的方法
             * 
             * 【为什么检查final？】
             * final方法不能被子类覆写，这是Java语言规范。
             * 如果尝试覆写final方法，JVM会抛出错误。
             * 
             * 【位运算说明】
             * method.getModifiers()返回一个int，每个bit代表一个修饰符
             * Modifier.FINAL是常量值0x0010
             * (modifiers & Modifier.FINAL) == Modifier.FINAL 表示有final修饰符
             */
            if ((method.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
                continue;
            }
            methodList.add(method);
        }
        return methodList.toArray(new Method[]{});
    }

    /**
     * 添加静态代码块，为代理类添加静态字段
     * 
     * 【什么是<clinit>？】
     * <clinit>是JVM的类初始化方法，在类第一次被加载时自动执行，且只执行一次。
     * Java中的静态代码块（static {}）和静态字段初始化都会被编译到这个方法的。
     * 
     * 【这个方法的作用】
     * 为每个需要拦截的方法：
     * 1. 创建静态字段（缓存Method对象）
     * 2. 生成调用父类方法的代理方法（如doGet_0）
     * 3. 初始化静态字段（通过反射获取Method对象）
     * 
     * 【为什么要缓存Method对象？】
     * 因为拦截器的intercept方法需要Method对象作为参数。
     * 如果每次调用都反射获取Method，性能会很差。
     * 所以在类加载时一次性获取并缓存，后续直接使用。
     * 
     * @param cw ClassWriter
     * @param className 代理类名
     * @param superclass 父类
     * @param methods 需要拦截的方法数组
     */
    private static void addStaticBlock(ClassWriter cw, String className,
                                       Class<?> superclass, Method[] methods) {
        /**
         * 创建<clinit>方法（静态初始化方法）
         * 
         * 【参数详解】
         * 1. ACC_STATIC: 静态方法
         * 2. "<clinit>": 类初始化方法名
         * 3. "()V": 无参数，返回void
         * 4. null: 泛型签名
         * 5. null: 异常列表
         */
        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V",
                null, null);
        
        // 标记方法代码开始
        mv.visitCode();
        
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];

            /**
             * 生成字段名，格式：_方法名_索引
             * 例如：doGet方法的第一个实例 -> "_doGet_0"
             * 
             * 【为什么加索引？】
             * 避免重载方法的字段名相同。
             * 例如：doGet()和doGet(String)是两个不同的方法，
             * 但方法名都是doGet，所以需要索引区分。
             */
            String fieldName = "_" + method.getName() + "_" + i;
            
            /**
             * 【步骤1】添加静态字段
             * 
             * 【参数详解】
             * 1. ACC_PRIVATE | ACC_STATIC: 私有静态字段
             * 2. fieldName: 字段名，如"_doGet_0"
             * 3. Type.getDescriptor(Method.class): 字段类型"Ljava/lang/reflect/Method;"
             * 4. null: 泛型签名
             * 5. null: 初始值（在<clinit>中初始化）
             * 
             * 【等效Java代码】
             * private static Method _doGet_0;
             */
            cw.visitField(ACC_PRIVATE | ACC_STATIC, fieldName, Type.getDescriptor(Method.class), null, null);

            /**
             * 【步骤2】生成调用父类方法的代理方法
             * 
             * 这个方法名是：方法名_索引，如"doGet_0"
             * 这个方法的作用是：直接调用父类的原始方法，不经过拦截器
             * 拦截器可以通过调用这个方法来执行原始方法
             * 
             * 【等效Java代码】
             * public final void doGet_0(HttpRequest request) {
             *     super.doGet(request);  // 直接调用父类方法
             * }
             */
            addCallSuperclassMethod(cw, superclass, method.getName() + "_" + i, method);

            /**
             * 【步骤3】调用Class.forName()获取代理类的Class对象
             * 
             * visitLdcInsn(): 将常量加载到操作数栈
             * - 这里加载的是字符串常量className（将/替换为.）
             * - className是内部名称格式（用/），forName需要Java格式（用.）
             * - 执行后，栈顶是：Class对象
             */
            mv.visitLdcInsn(className.replace("/", "."));
            
            /**
             * 调用Class.forName()静态方法
             * 
             * 【参数详解】
             * 1. INVOKESTATIC: 调用静态方法
             * 2. Type.getInternalName(Class.class): "java/lang/Class"
             * 3. "forName": 方法名
             * 4. "(Ljava/lang/String;)Ljava/lang/Class;": 方法描述符
             * 5. false: 不是接口方法
             */
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Class.class),
                    "forName",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    false);

            /**
             * 【步骤4】准备调用Class.getMethod()的参数
             * getMethod需要两个参数：
             * 1. 方法名（String）
             * 2. 参数类型数组（Class[]）
             * 
             * 注意：这里的方法名是"方法名_索引"，如"doGet_0"
             * 因为要获取的是代理类生成的代理方法，不是原始方法
             */
            mv.visitLdcInsn(method.getName() + "_" + i);
            
            /**
             * 【步骤5】准备第二个参数：参数类型数组
             * 
             * 如果方法没有参数，直接压入null
             * 如果有参数，需要创建Class[]数组并填充
             */
            Class[] methodParamTypes = method.getParameterTypes();
            if (methodParamTypes.length == 0) {
                // ACONST_NULL: 将null引用压入栈
                mv.visitInsn(ACONST_NULL);
            } else {
                /**
                 * 【步骤5.1】将数组长度压入栈
                 * 
                 * JVM提供了快捷指令加载0-5的整数：
                 * - ICONST_0到ICONST_5
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
                 * 【步骤5.2】创建数组
                 * 
                 * ANEWARRAY: 创建引用类型数组
                 * 参数：Type.getInternalName(Class.class) = "java/lang/Class"
                 * 
                 * 等效Java代码：new Class[数组长度]
                 */
                mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Class.class));
                
                /**
                 * 【步骤5.3】为数组的每个元素赋值
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
                     * 消耗栈顶3个元素：array, index, value
                     * 执行：array[index] = value
                     * 
                     * AA表示：Array of Reference (引用类型数组)
                     */
                    mv.visitInsn(AASTORE);
                }
            }
            
            /**
             * 【步骤6】调用Class.getMethod()获取Method对象
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
             * 【步骤7】将Method对象存储到静态字段
             * 
             * PUTSTATIC: 设置静态字段的值
             * 
             * 【参数详解】
             * 1. className: 类名
             * 2. fieldName: 字段名，如"_doGet_0"
             * 3. Type.getDescriptor(Method.class): 字段类型描述符
             * 
             * 等效Java代码：_doGet_0 = Method对象
             */
            mv.visitFieldInsn(PUTSTATIC, className, fieldName, Type.getDescriptor(Method.class));
        }
        
        // RETURN: void方法的返回指令
        mv.visitInsn(RETURN);
        
        // 设置栈大小和局部变量表大小（COMPUTE_MAXS会自动计算）
        mv.visitMaxs(1, 1);
        
        // 标记方法定义结束
        mv.visitEnd();
    }

    /**
     * 生成调用父类方法的子类代理方法
     * 
     * 【这个方法的作用】
     * 生成一个final方法，直接调用父类的原始方法，不经过拦截器。
     * 拦截器可以通过调用这个方法来执行原始方法。
     * 
     * 【生成的Java代码等效于】
     * public final void doGet_0(HttpRequest request) {
     *     super.doGet(request);  // 直接调用父类方法
     * }
     * 
     * 【为什么需要这个方法？】
     * CGLIB的拦截器需要能够调用原始方法。
     * 在JDK代理中，可以用method.invoke()直接调用。
     * 但CGLIB是继承关系，如果直接调用method.invoke()会导致递归调用拦截器。
     * 所以生成一个final方法，直接调用super，绕过拦截器。
     * 
     * 【为什么是final？】
     * 防止子类再次覆写这个代理方法，保证调用父类方法的语义不被破坏。
     * 
     * @param cw ClassWriter
     * @param superclass 父类
     * @param methodName 代理方法名，如"doGet_0"
     * @param method 原始方法
     */
    private static void addCallSuperclassMethod(ClassWriter cw, Class<?> superclass, String methodName, Method method) {
        /**
         * 创建代理方法
         * 
         * 【参数详解】
         * 1. ACC_PUBLIC | ACC_FINAL: public且final
         *    - ACC_PUBLIC: 公开访问
         *    - ACC_FINAL: 不允许子类覆写
         * 
         * 2. methodName: 方法名，如"doGet_0"
         * 
         * 3. Type.getMethodDescriptor(method): 方法描述符
         *    例如："(Lcom/wujiuye/HttpRequest;)V"
         * 
         * 4. null: 泛型签名
         * 5. null: 异常列表
         */
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_FINAL, methodName,
                Type.getMethodDescriptor(method),
                null, null);
        
        // 标记方法代码开始
        mv.visitCode();
        
        /**
         * 【步骤1】加载this引用
         * 
         * ALOAD 0: 加载局部变量表第0个位置的引用（this）
         */
        mv.visitVarInsn(ALOAD, 0);
        
        /**
         * 【步骤2】加载方法参数
         * 
         * 将当前方法的所有参数加载到操作数栈，准备调用父类方法。
         * 
         * 【局部变量表结构】
         * 位置0: this
         * 位置1: 第一个参数
         * 位置2: 第二个参数
         * ...
         * 
         * 【注意】long和double类型占用两个局部变量位置！
         * 所以需要根据参数类型选择不同的加载指令：
         * - ILOAD: 加载int类型（也包括byte、short、char、boolean）
         * - LLOAD: 加载long类型
         * - FLOAD: 加载float类型
         * - DLOAD: 加载double类型
         * - ALOAD: 加载引用类型（Object、String等）
         */
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0) {
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> paramType = paramTypes[i];
                if (paramType == int.class) {
                    // ILOAD: 加载int类型参数
                    mv.visitVarInsn(ILOAD, i + 1);
                } else if (paramType == long.class) {
                    // LLOAD: 加载long类型参数
                    mv.visitVarInsn(LLOAD, i + 1);
                }
                //.... 其他基本类型（float、double等）的处理类似
                else {
                    // ALOAD: 加载引用类型参数
                    mv.visitVarInsn(ALOAD, i + 1);
                }
            }
        }
        
        /**
         * 【步骤3】调用父类的方法
         * 
         * INVOKESPECIAL: 调用特殊方法（构造方法、私有方法、super方法）
         * 
         * 【为什么用INVOKESPECIAL？】
         * - 调用super方法必须用INVOKESPECIAL
         * - 不会进行动态绑定，直接调用指定类的方法
         * - 如果用INVOKEVIRTUAL，会调用当前类的覆写方法，导致无限递归
         * 
         * 【参数】
         * 1. Type.getInternalName(superclass): 父类名
         * 2. method.getName(): 原始方法名，如"doGet"（注意不是"doGet_0"）
         * 3. Type.getMethodDescriptor(method): 方法描述符
         * 4. false: 不是接口方法
         * 
         * 【执行效果】
         * 消耗栈顶的this和所有参数，调用super.doGet(...)
         */
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(superclass),
                method.getName(), Type.getMethodDescriptor(method), false);
        
        /**
         * 【步骤4】生成return指令
         * 
         * 根据方法的返回类型，生成不同的返回指令：
         * - void: RETURN
         * - int: IRETURN
         * - 引用类型: ARETURN
         */
        addReturnInstruc(mv, method.getReturnType());

        // 设置栈大小和局部变量表大小
        mv.visitMaxs(1, 1);
        
        // 标记方法定义结束
        mv.visitEnd();
    }

    /**
     * 覆写父类的方法，拦截方法的执行，交给方法拦截器处理
     * 
     * 【这个方法的作用】
     * 为每个需要拦截的方法生成覆写实现，将方法调用转发给MyMethodInterceptor。
     * 
     * 【生成的Java代码等效于】
     * public void doGet(HttpRequest request) throws Exception {
     *     try {
     *         // 调用拦截器的intercept方法
     *         return this.h.intercept(
     *             this,                    // 代理对象
     *             _doGet_0,               // 代理方法的Method对象
     *             "doGet",                // 原始方法名
     *             new Object[]{request}   // 参数数组
     *         );
     *     } catch (Exception e) {
     *         throw e;
     *     }
     * }
     * 
     * @param cw ClassWriter
     * @param className 代理类名
     * @param superclass 父类
     * @param methods 需要拦截的方法数组
     */
    private static void overrideMethods(ClassWriter cw, String className,
                                        Class<?> superclass, Method[] methods) {
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            
            /**
             * 创建覆写方法
             * 
             * 【参数详解】
             * 1. ACC_PUBLIC: public访问级别
             * 
             * 2. method.getName(): 方法名，如"doGet"
             * 
             * 3. Type.getMethodDescriptor(method): 方法描述符
             *    例如："(Lcom/wujiuye/HttpRequest;)V"
             * 
             * 4. null: 泛型签名
             * 
             * 5. new String[]{Type.getInternalName(Exception.class)}: 声明抛出的异常
             *    等效于Java的throws Exception
             *    因为拦截器的intercept方法可能抛出异常
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
             * 【步骤1】获取拦截器对象（字段h）
             * 
             * ALOAD 0: 加载this
             * 
             * GETFIELD: 获取实例字段的值
             * 【参数】
             * 1. className: 类名
             * 2. "h": 字段名
             * 3. Type.getDescriptor(MyMethodInterceptor.class): 字段类型
             * 
             * 【执行效果】
             * 消耗this，压入this.h（MyMethodInterceptor对象）
             * 栈：[MyMethodInterceptor]
             */
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD,
                    className,
                    "h",
                    Type.getDescriptor(MyMethodInterceptor.class));

            /**
             * 【步骤2】准备MyMethodInterceptor.intercept()的四个参数
             * 
             * intercept方法签名：
             * Object intercept(Object obj, Method method, String methodName, Object[] args)
             * 
             * 此时栈中已有：[MyMethodInterceptor]
             * 还需要压入：this, Method对象, 方法名, 参数数组
             */
            
            /**
             * 第一个参数：this（代理对象本身）
             * 
             * ALOAD 0: 加载this
             * 
             * CHECKCAST: 类型检查并转换为父类类型
             * 【为什么需要CHECKCAST？】
             * 虽然this已经是代理类实例（也是父类子类型），
             * 但intercept方法声明的第一个参数是父类类型，
             * 所以需要显式类型转换，确保类型安全。
             */
            mv.visitVarInsn(ALOAD, 0);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(superclass));
            
            /**
             * 第二个参数：Method对象（从静态字段获取）
             * 
             * GETSTATIC: 获取静态字段的值
             * 【参数】
             * 1. className: 类名
             * 2. "_doGet_0": 静态字段名
             * 3. Type.getDescriptor(Method.class): 字段类型
             * 
             * 【注意】这里获取的是代理方法的Method，不是原始方法
             * 代理方法如doGet_0，可以直接调用父类方法
             */
            mv.visitFieldInsn(GETSTATIC,
                    className,
                    "_" + method.getName() + "_" + i,
                    Type.getDescriptor(Method.class));

            /**
             * 第三个参数：原始方法名
             * 
             * visitLdcInsn: 将字符串常量压入栈
             * 例如："doGet"
             * 
             * 【为什么需要方法名？】
             * 因为第二个参数是代理方法的Method，方法名是"doGet_0"。
             * 拦截器需要知道原始方法名，所以单独传递。
             */
            mv.visitLdcInsn(method.getName());

            // 第四个参数，将当前方法的参数构造成数组
            int paramCount = method.getParameterCount();
            if (paramCount == 0) {
                mv.visitInsn(ACONST_NULL);
            } else {
                // 数组大小
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
                // 创建数组
                mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
                // 为数组元素赋值
                for (int index = 1; index <= paramCount; index++) {
                    mv.visitInsn(DUP);
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
                            mv.visitVarInsn(BIPUSH, i - 1);
                            break;
                    }
                    // 暂不考虑参数类型为基本数据类型的情况
                    mv.visitVarInsn(ALOAD, index);
                    mv.visitInsn(AASTORE);
                }
            }

            /**
             * 第四个参数：方法参数数组（Object[]）
             * 
             * 将当前方法的所有参数打包成Object数组
             */
            int paramCount2 = method.getParameterCount();
            if (paramCount2 == 0) {
                // 没有参数，直接压入null
                mv.visitInsn(ACONST_NULL);
            } else {
                /**
                 * 【步骤4.1】创建Object数组
                 * 
                 * 先将数组长度压入栈
                 */
                switch (paramCount2) {
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
                        mv.visitVarInsn(BIPUSH, paramCount2);
                }
                
                // 创建Object[]数组
                mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
                
                /**
                 * 【步骤4.2】为数组元素赋值
                 * 
                 * 将当前方法的每个参数放入数组
                 * 等效Java代码：
                 * Object[] args = new Object[paramCount2];
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
                for (int index = 1; index <= paramCount2; index++) {
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
             * 【步骤5】调用MyMethodInterceptor.intercept()方法
             * 
             * INVOKEINTERFACE: 调用接口方法
             * 
             * 【为什么用INVOKEINTERFACE？】
             * 因为MyMethodInterceptor是接口，必须用INVOKEINTERFACE调用。
             * 这是JVM规范，调用接口方法必须用这个指令。
             * 
             * 【参数详解】
             * 1. Type.getInternalName(MyMethodInterceptor.class): 接口名
             * 2. "intercept": 方法名
             * 3. "(Ljava/lang/Object;Ljava/lang/reflect/Method;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"
             *    方法描述符：(Object, Method, String, Object[]) -> Object
             *    [ 表示数组
             * 4. true: 是接口方法
             * 
             * 【执行效果】
             * 消耗栈顶5个元素：MyMethodInterceptor, this, Method, methodName, Object[]
             * 压入返回值：Object
             * 栈：[Object]
             */
            mv.visitMethodInsn(INVOKEINTERFACE,
                    Type.getInternalName(MyMethodInterceptor.class),
                    "intercept",
                    "(Ljava/lang/Object;Ljava/lang/reflect/Method;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                    true);

            /**
             * 【步骤6】添加return指令
             * 
             * 根据方法的返回类型，生成不同的返回指令：
             * - void: RETURN
             * - int: IRETURN（需要先将Object转为int）
             * - 引用类型: ARETURN（需要CHECKCAST类型转换）
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
             * 这是JDK 1.6+要求的验证信息，COMPUTE_FRAMES会自动计算。
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
     * MyMethodInterceptor.intercept()返回的是Object类型，
     * 但被代理的方法可能返回int、void等具体类型。
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
             * 注意：intercept()返回的Object被丢弃了
             * 等效Java代码：
             * this.h.intercept(...);
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
             * return ((Integer) this.h.intercept(...)).intValue();
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
             * return (String) this.h.intercept(...);
             */
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType));
            mv.visitInsn(ARETURN);
        }
    }

}
