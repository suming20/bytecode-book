package com.wujiuye.agent;


import com.wujiuye.agent.utils.ByteCodeUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

/**
 * 方法适配器 - 负责在方法中插入监控代码（插桩）
 * 
 * 【类的作用】
 * 这个类继承MethodVisitor，是字节码插桩的核心类。
 * 它负责在方法的三个关键位置插入监控代码：
 * 1. 方法开始：调用CallLogAspect.before()记录方法调用信息
 * 2. 方法返回：调用CallLogAspect.after()记录返回值
 * 3. 方法异常：调用CallLogAspect.error()记录异常信息
 * 
 * 【MethodVisitor是什么？】
 * MethodVisitor是ASM提供的方法访问器接口。
 * 当访问方法时，会按顺序调用MethodVisitor的方法：
 * - visitCode(): 方法代码开始
 * - visitXxxInsn(): 访问每条字节码指令
 * - visitMaxs(): 设置栈大小和局部变量表大小
 * - visitEnd(): 方法访问结束
 * 
 * 【插桩原理】
 * 原始方法：
 *   public Map queryUser(String username, Integer age) {
 *       Map result = new HashMap<>();
 *       return result;
 *   }
 * 
 * 插桩后：
 *   public Map queryUser(String username, Integer age) {
 *       CallLogAspect.before(...);  // 插入的监控代码
 *       try {
 *           Map result = new HashMap<>();
 *           CallLogAspect.after(..., result);  // 插入的监控代码
 *           return result;
 *       } catch (Throwable e) {
 *           CallLogAspect.error(..., e);  // 插入的监控代码
 *           throw e;
 *       }
 *   }
 * 
 * 【ASM指令插入技巧】
 * 1. visitCode(): 在方法开始处插入代码
 * 2. visitInsn(RETURN等): 在返回指令前插入代码
 * 3. visitMaxs(): 在方法结束前插入try-catch块
 * 
 * 【注意事项】
 * 1. 不能破坏原有的操作数栈平衡
 * 2. 需要正确处理不同返回类型
 * 3. 需要正确处理基本类型参数的装箱
 * 4. long和double类型占用两个局部变量位置
 */
public class MyMethodAdapter extends MethodVisitor {

    /** 类名（内部名称格式） */
    private String className;
    
    /** 是否是静态方法 */
    private boolean isStaticMethod = false;
    
    /** 方法名 */
    private String methodName;
    
    /** 方法描述符 */
    private String descriptor;
    
    /** 参数类型描述符数组 */
    private String[] paramDescriptors;

    /**
     * 构造方法
     * 
     * 【参数说明】
     * @param className 类名，如"com/wujiuye/demo/UserService"
     * @param access 方法访问标志，用于判断是否是静态方法
     * @param methodName 方法名，如"queryUser"
     * @param descriptor 方法描述符，如"(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;"
     * @param methodVisitor 下一个方法访问器，用于转发方法访问事件
     */
    public MyMethodAdapter(String className,
                           int access,
                           String methodName,
                           String descriptor,
                           MethodVisitor methodVisitor) {
        super(ASM6, methodVisitor);
        
        /**
         * 判断是否是静态方法
         * 
         * 【为什么要判断？】
         * 因为静态方法没有this引用，局部变量表的索引不同：
         * - 实例方法：位置0是this，参数从位置1开始
         * - 静态方法：没有this，参数从位置0开始
         * 
         * 【位运算说明】
         * ACC_STATIC = 0x0008
         * (access & ACC_STATIC) == ACC_STATIC 表示是静态方法
         */
        if ((access & ACC_STATIC) == ACC_STATIC) {
            isStaticMethod = true;
        }
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        
        /**
         * 解析方法描述符，获取参数类型描述符数组
         * 
         * 例如："(Ljava/lang/String;I)V" -> ["Ljava/lang/String;", "I"]
         * 
         * ByteCodeUtils.getParamDescriptors()使用正则表达式解析描述符，
         * 提取括号内的参数类型。
         */
        this.paramDescriptors = ByteCodeUtils.getParamDescriptors(descriptor);
    }

    /**
     * Try-Catch块的标签
     * 
     * JVM的异常处理基于标签范围：
     * - from: Try块开始位置
     * - to: Try块结束位置
     * - target: Catch块开始位置
     * 
     * 这三个标签定义了异常处理的范围。
     */
    private Label from = new Label(),
            to = new Label(),
            target = new Label();

    /**
     * 方法代码开始访问时调用
     * 
     * 【调用时机】
     * 在方法的所有指令之前调用，是插入方法开始监控代码的最佳位置。
     * 
     * 【插入的监控代码】
     * 等效于在方法开始处添加：
     * CallLogAspect.before(className, methodName, descriptor, params);
     * 
     * 【执行流程】
     * 1. 压入className、methodName、descriptor三个参数
     * 2. 创建参数数组（将局部变量表中的参数装箱为Object[]）
     * 3. 调用CallLogAspect.before()
     * 4. 标记Try块开始
     */
    @Override
    public void visitCode() {
        super.visitCode();
        
        /**
         * 插入埋点代码，调用CallLogAspect的before方法
         * 
         * before方法签名：
         * void before(String className, String methodName, String descriptor, Object[] params)
         * 
         * 【步骤1】压入前三个参数（类名、方法名、描述符）
         * 
         * visitLdcInsn(): 将常量加载到操作数栈
         * - 第一次调用后，栈：[className]
         * - 第二次调用后，栈：[className, methodName]
         * - 第三次调用后，栈：[className, methodName, descriptor]
         */
        this.visitLdcInsn(this.className);
        this.visitLdcInsn(this.methodName);
        this.visitLdcInsn(this.descriptor);
        /**
         * 【步骤2】准备第四个参数：参数数组（Object[]）
         * 
         * 如果方法没有参数，直接压入null
         * 如果有参数，需要创建Object[]数组并将局部变量表中的参数装箱
         */
        if (paramDescriptors == null) {
            // ACONST_NULL: 将null引用压入栈
            this.visitInsn(ACONST_NULL);
        } else {
            /**
             * 【步骤2.1】将数组长度压入栈
             * 
             * JVM提供了快捷指令加载0-5的整数：
             * - ICONST_0到ICONST_5
             * 超过5的整数需要使用BIPUSH指令
             */
            if (paramDescriptors.length >= 4) {
                mv.visitVarInsn(BIPUSH, paramDescriptors.length);
            } else {
                switch (paramDescriptors.length) {
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
                        mv.visitInsn(ICONST_0);
                }
            }
            
            /**
             * 【步骤2.2】创建Object数组
             * 
             * ANEWARRAY: 创建引用类型数组
             * 参数：Type.getInternalName(Object.class) = "java/lang/Object"
             * 
             * 等效Java代码：new Object[paramDescriptors.length]
             */
            mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));

            /**
             * 【步骤2.3】为数组元素赋值
             * 
             * 需要将局部变量表中的方法参数取出，装箱为Object，存入数组。
             * 
             * 【局部变量表索引计算】
             * - 非静态方法：位置0是this，参数从位置1开始
             * - 静态方法：没有this，参数从位置0开始
             * 
             * 【注意】long和double类型占用两个局部变量位置！
             */
            int localIndex = isStaticMethod ? 0 : 1;
            
            // 遍历所有参数类型描述符
            for (int i = 0; i < paramDescriptors.length; i++) {
                /**
                 * DUP: 复制栈顶的数组引用
                 * 
                 * 【为什么需要DUP？】
                 * 因为AASTORE会消耗数组引用，
                 * 如果不DUP，第一次赋值后数组引用就没了，
                 * 后续无法继续赋值。
                 */
                mv.visitInsn(DUP);
                
                /**
                 * 压入数组索引（0, 1, 2, 3...）
                 */
                switch (i) {
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
                        mv.visitVarInsn(BIPUSH, i);
                }
                
                /**
                 * 【步骤2.4】根据参数类型，加载参数并装箱为Object
                 * 
                 * 基本类型需要调用包装类的valueOf()方法转换为Object。
                 * 引用类型直接加载即可。
                 * 
                 * 【类型描述符对照表】
                 * - Z: boolean -> Boolean.valueOf()
                 * - C: char -> Character.valueOf()
                 * - B: byte -> Byte.valueOf()
                 * - S: short -> Short.valueOf()
                 * - I: int -> Integer.valueOf()
                 * - F: float -> Float.valueOf()
                 * - J: long -> Long.valueOf()
                 * - D: double -> Double.valueOf()
                 * - L...;: 引用类型，直接ALOAD
                 * - [...: 数组类型，直接ALOAD
                 */
                String type = paramDescriptors[i];
                if ("Z".equals(type)) {
                    // boolean类型：ILOAD加载，Boolean.valueOf装箱
                    mv.visitVarInsn(ILOAD, localIndex++);
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Boolean.class),
                            "valueOf",
                            "(Z)Ljava/lang/Boolean;", false);
                } else if ("C".equals(type)) {
                    // char类型：ILOAD加载，Character.valueOf装箱
                    mv.visitVarInsn(ILOAD, localIndex++);
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Character.class),
                            "valueOf",
                            "(C)Ljava/lang/Character;", false);
                } else if ("B".equals(type)) {
                    // byte类型：ILOAD加载，Byte.valueOf装箱
                    mv.visitVarInsn(ILOAD, localIndex++);
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Byte.class),
                            "valueOf",
                            "(B)Ljava/lang/Byte;", false);
                } else if ("S".equals(type)) {
                    // short类型：ILOAD加载，Short.valueOf装箱
                    mv.visitVarInsn(ILOAD, localIndex++);
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Short.class),
                            "valueOf",
                            "(S)Ljava/lang/Short;", false);
                } else if ("I".equals(type)) {
                    // int类型：ILOAD加载，Integer.valueOf装箱
                    mv.visitVarInsn(ILOAD, localIndex++);
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Integer.class),
                            "valueOf",
                            "(I)Ljava/lang/Integer;", false);
                } else if ("F".equals(type)) {
                    // float类型：FLOAD加载，Float.valueOf装箱
                    mv.visitVarInsn(FLOAD, localIndex++);
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Float.class),
                            "valueOf",
                            "(F)Ljava/lang/Float;", false);
                } else if ("J".equals(type)) {
                    /**
                     * long类型：LLOAD加载，Long.valueOf装箱
                     * 
                     * 【注意】long类型占用两个局部变量位置！
                     * 所以localIndex += 2，而不是++
                     */
                    mv.visitVarInsn(LLOAD, localIndex);
                    localIndex += 2;
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Long.class),
                            "valueOf",
                            "(J)Ljava/lang/Long;", false);
                } else if ("D".equals(type)) {
                    /**
                     * double类型：DLOAD加载，Double.valueOf装箱
                     * 
                     * 【注意】double类型占用两个局部变量位置！
                     */
                    mv.visitVarInsn(DLOAD, localIndex);
                    localIndex += 2;
                    mv.visitMethodInsn(INVOKESTATIC,
                            Type.getInternalName(Double.class),
                            "valueOf",
                            "(D)Ljava/lang/Double;", false);
                } else {
                    // 数组或对象引用类型：直接ALOAD加载
                    mv.visitVarInsn(ALOAD, localIndex++);
                }
                
                /**
                 * AASTORE: 存储引用到数组
                 * 
                 * 消耗栈顶3个元素：array, index, value
                 * 执行：array[index] = value
                 */
                mv.visitInsn(AASTORE);
            }
        }
        
        /**
         * 【步骤3】调用CallLogAspect.before()方法
         * 
         * 此时栈中应该有：
         * - className (String)
         * - methodName (String)
         * - descriptor (String)
         * - params (Object[])
         * 
         * INVOKESTATIC: 调用静态方法
         * 方法描述符：(String, String, String, Object[]) -> void
         */
        this.visitMethodInsn(INVOKESTATIC,
                Type.getInternalName(CallLogAspect.class),
                "before",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V",
                false);

        // 标记Try块开始
        this.visitLabel(from);
    }

    /**
     * 记录下一个可用的局部变量索引
     * 
     * 【作用】
     * 用于在返回指令前保存返回值到局部变量表。
     * 需要找到一个未被使用的局部变量位置。
     */
    private int nextLocalIndex = 0;

    /**
     * 访问局部变量指令时调用
     * 
     * 【调用时机】
     * 当方法中有ILOAD、ALOAD等局部变量访问指令时调用。
     * 
     * 【作用】
     * 跟踪局部变量表的使用情况，记录最大的局部变量索引。
     * 这样在插入返回值监控代码时，可以找到一个未被使用的局部变量位置。
     * 
     * @param opcode 操作码，如ILOAD、ALOAD等
     * @param var 局部变量索引
     */
    @Override
    public void visitVarInsn(int opcode, int var) {
        super.visitVarInsn(opcode, var);
        
        /**
         * 更新nextLocalIndex
         * 
         * 【为什么区分单slot和双slot？】
         * JVM局部变量表中：
         * - int、float、reference类型占用1个slot
         * - long、double类型占用2个slot
         * 
         * 所以需要记录最大的使用位置，避免覆盖。
         */
        if (opcode == ILOAD
                || opcode == FLOAD
                || opcode == ALOAD
                || opcode == ISTORE
                || opcode == FSTORE
                || opcode == ASTORE) {
            // 单slot类型：下一个位置是var + 1
            if (var > nextLocalIndex) {
                nextLocalIndex = var + 1;
            }
        } else if (opcode == LLOAD
                || opcode == DLOAD
                || opcode == LSTORE
                || opcode == DSTORE) {
            // 双slot类型：下一个位置是var + 2
            if (var + 1 > nextLocalIndex) {
                nextLocalIndex = var + 2;
            }
        }
    }

    /**
     * 访问无操作数指令时调用
     * 
     * 【调用时机】
     * 当方法中有RETURN、ATHROW等无操作数指令时调用。
     * 
     * 【作用】
     * 在返回指令（RETURN、IRETURN等）之前插入after监控代码。
     * 
     * 【关键注意事项】
     * 不要造成死递归调用！
     * 例如：插入的监控代码中如果调用了方法，
     * 而该方法又触发了visitInsn，可能导致无限递归。
     * 
     * @param opcode 操作码，如RETURN、IRETURN、ARETURN等
     */
    @Override
    public void visitInsn(int opcode) {
        // 保存当前最大的局部变量索引，用于保存返回值
        int li = nextLocalIndex;
        
        switch (opcode) {
            case RETURN:
                /**
                 * void方法返回 - 无返回值
                 * 
                 * 插入的监控代码等效于：
                 * CallLogAspect.after(className, methodName, descriptor, null);
                 * 
                 * 【步骤】
                 * 1. 压入className、methodName、descriptor
                 * 2. 压入null（void方法没有返回值）
                 * 3. 调用CallLogAspect.after()
                 */
                this.visitLdcInsn(this.className);
                this.visitLdcInsn(this.methodName);
                this.visitLdcInsn(this.descriptor);
                // null入栈
                this.visitInsn(ACONST_NULL);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(CallLogAspect.class),
                        "after",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false);
                break;
                
            case IRETURN:
                /**
                 * int方法返回 - 返回int类型值
                 * 
                 * 插入的监控代码等效于：
                 * int temp = <返回值>;
                 * CallLogAspect.after(className, methodName, descriptor, Integer.valueOf(temp));
                 * return temp;
                 * 
                 * 【步骤】
                 * 1. DUP: 复制栈顶的int值
                 * 2. ISTORE: 将一份保存到局部变量表
                 * 3. 压入className、methodName、descriptor
                 * 4. ILOAD: 从局部变量表加载返回值
                 * 5. Integer.valueOf: 装箱为Integer
                 * 6. 调用CallLogAspect.after()
                 * 7. ILOAD: 再次加载返回值（留给IRETURN）
                 */
                this.visitInsn(DUP);
                this.visitVarInsn(ISTORE, li);

                this.visitLdcInsn(this.className);
                this.visitLdcInsn(this.methodName);
                this.visitLdcInsn(this.descriptor);
                // 将返回值由int类型转为Integer类型
                this.visitVarInsn(ILOAD, li);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(Integer.class),
                        "valueOf",
                        "(I)Ljava/lang/Integer;", false);
                // 调用埋点方法
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(CallLogAspect.class),
                        "after",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false);
                break;
                
            case FRETURN:
                /**
                 * float方法返回 - 返回float类型值
                 * 
                 * 步骤同IRETURN，只是使用FSTORE/FLOAD和Float.valueOf
                 */
                this.visitInsn(DUP);
                this.visitVarInsn(FSTORE, li);

                this.visitLdcInsn(this.className);
                this.visitLdcInsn(this.methodName);
                this.visitLdcInsn(this.descriptor);

                this.visitVarInsn(FLOAD, li);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(Float.class),
                        "valueOf",
                        "(F)Ljava/lang/Float;", false);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(CallLogAspect.class),
                        "after",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false);
                break;
                
            case LRETURN:
                /**
                 * long方法返回 - 返回long类型值
                 * 
                 * 【注意】long类型占用两个slot，需要使用DUP2
                 * 
                 * DUP2: 复制栈顶的两个值（long/double占用两个栈位置）
                 */
                this.visitInsn(DUP2);
                this.visitVarInsn(LSTORE, li);

                this.visitLdcInsn(this.className);
                this.visitLdcInsn(this.methodName);
                this.visitLdcInsn(this.descriptor);

                this.visitVarInsn(LLOAD, li);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(Long.class),
                        "valueOf",
                        "(J)Ljava/lang/Long;", false);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(CallLogAspect.class),
                        "after",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false);
                break;
                
            case DRETURN:
                /**
                 * double方法返回 - 返回double类型值
                 * 
                 * 【注意】double类型占用两个slot，需要使用DUP2
                 */
                this.visitInsn(DUP2);
                this.visitVarInsn(DSTORE, li);

                this.visitLdcInsn(this.className);
                this.visitLdcInsn(this.methodName);
                this.visitLdcInsn(this.descriptor);

                this.visitVarInsn(DLOAD, li);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(Double.class),
                        "valueOf",
                        "(D)Ljava/lang/Double;", false);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(CallLogAspect.class),
                        "after",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false);
                break;
                
            case ARETURN:
                /**
                 * 引用类型方法返回 - 返回Object、String等引用类型
                 * 
                 * 插入的监控代码等效于：
                 * Object temp = <返回值>;
                 * CallLogAspect.after(className, methodName, descriptor, temp);
                 * return temp;
                 * 
                 * 【步骤】
                 * 1. DUP: 复制栈顶的引用
                 * 2. ASTORE: 将一份保存到局部变量表
                 * 3. 压入className、methodName、descriptor
                 * 4. ALOAD: 从局部变量表加载返回值
                 * 5. 调用CallLogAspect.after()
                 * 6. ALOAD: 再次加载返回值（留给ARETURN）
                 */
                this.visitInsn(DUP);
                this.visitVarInsn(ASTORE, li);

                this.visitLdcInsn(this.className);
                this.visitLdcInsn(this.methodName);
                this.visitLdcInsn(this.descriptor);
                // 从局部变量表加载返回值
                this.visitVarInsn(ALOAD, li);
                this.visitMethodInsn(INVOKESTATIC,
                        Type.getInternalName(CallLogAspect.class),
                        "after",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false);
                break;
        }
        
        /**
         * 调用原始的返回指令
         * 
         * super.visitInsn(opcode)会执行原始的RETURN/IRETURN/ARETURN等指令。
         * 必须在插入监控代码后调用，否则方法无法正常返回。
         */
        super.visitInsn(opcode);
    }

    /**
     * 访问方法最大栈深度和局部变量表大小时调用
     * 
     * 【调用时机】
     * 在方法的所有指令访问完毕后调用，是插入try-catch块的最佳位置。
     * 
     * 【为什么在这里插入catch块？】
     * 1. visitMaxs是方法访问的最后一个回调
     * 2. 此时所有指令都已访问完毕，可以定义完整的try范围
     * 3. 需要在visitEnd之前注册try-catch块
     * 
     * 【关于COMPUTE_MAXS的说明】
     * 由于我们在创建ClassWriter对象时，添加了COMPUTE_MAXS标志，
     * 在ClassWriter的visitMethod方法的实现中，会创建一个MethodWriter，
     * 会将COMPUTE_MAXS标志传递给这个MethodWriter对象，
     * 而在MethodWriter的visitMaxs方法中，如果有COMPUTE_MAXS标志，
     * 则会自动帮我们计算出新的局部变量表和操作数栈的大小。
     * 
     * 因此，在覆写visitMaxs方法时，不必修改局部变量表和操作数栈的大小，
     * 直接传递给super.visitMaxs即可。
     * 
     * 我们需要在visitMaxs中插入catch块代码，需要添加一个局部变量来保存catch住的异常，
     * 在执行完埋点方法后，将catch住的异常从局部变量表中加载到操作数栈顶，
     * 以执行athrow指令将异常抛出。
     *
     * @param maxStack 操作数栈最大深度（COMPUTE_MAXS会自动计算）
     * @param maxLocals 局部变量表大小（COMPUTE_MAXS会自动计算）
     */
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        /**
         * 标记Try块结束和Catch块开始
         * 
         * from: Try块开始位置（在visitCode中标记）
         * to: Try块结束位置（这里标记）
         * target: Catch块开始位置（这里标记）
         */
        this.visitLabel(to);
        this.visitLabel(target);

        /**
         * Catch块代码 - 处理异常
         * 
         * 插入的监控代码等效于：
         * catch (Throwable e) {
         *     CallLogAspect.error(className, methodName, descriptor, e);
         *     throw e;
         * }
         * 
         * 【步骤1】将异常对象保存到局部变量表
         * 
         * ASTORE: 存储引用类型到局部变量表
         * 
         * 【为什么用maxLocals + 1？】
         * maxLocals是方法原有的局部变量表大小，
         * 使用maxLocals + 1可以找到一个未被使用的位置。
         * 注意：这里没有考虑long/double占用两个slot的情况，
         * 但因为是catch块，异常对象是Throwable引用类型，只占一个slot。
         */
        this.visitVarInsn(ASTORE, maxLocals + 1);

        /**
         * 【步骤2】调用CallLogAspect.error()方法
         * 
         * 压入className、methodName、descriptor和异常对象
         * 调用error方法记录异常信息
         */
        this.visitLdcInsn(this.className);
        this.visitLdcInsn(this.methodName);
        this.visitLdcInsn(this.descriptor);
        this.visitVarInsn(ALOAD, maxLocals + 1);
        this.visitMethodInsn(INVOKESTATIC,
                Type.getInternalName(CallLogAspect.class),
                "error",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V",
                false);

        /**
         * 【步骤3】重新抛出异常
         * 
         * ALOAD: 从局部变量表加载异常对象
         * ATHROW: 抛出栈顶的异常对象
         * 
         * 这样调用者可以捕获到原始异常
         */
        this.visitVarInsn(ALOAD, maxLocals + 1);
        this.visitInsn(ATHROW);

        /**
         * 注册Try-Catch块
         * 
         * visitTryCatchBlock告诉JVM：
         * 从from到to之间的代码，如果抛出Throwable类型异常，
         * 就跳转到target位置处理
         * 
         * Type.getInternalName(Throwable.class) = "java/lang/Throwable"
         * 捕获所有异常（包括Exception和Error）
         */
        this.visitTryCatchBlock(from, to, target, Type.getInternalName(Throwable.class));
        
        // 调用父类方法，传递原始的maxStack和maxLocals
        // COMPUTE_MAXS会自动重新计算正确的值
        super.visitMaxs(maxStack, maxLocals);
    }

}
