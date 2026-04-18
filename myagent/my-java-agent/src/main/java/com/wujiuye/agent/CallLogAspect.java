package com.wujiuye.agent;

import com.alibaba.fastjson.JSON;

/**
 * 调用日志切面 - 被插入到方法中的监控代码
 * 
 * 【类的作用】
 * 这个类包含了三个静态方法，用于记录方法执行的日志信息。
 * 这些方法会被ASM字节码插桩工具插入到目标方法的三个关键位置：
 * 1. before(): 在方法开始处调用
 * 2. after(): 在方法正常返回前调用
 * 3. error(): 在方法抛出异常时调用
 * 
 * 【插桩效果】
 * 原始方法：
 *   public Map queryUser(String username, Integer age) {
 *       Map result = new HashMap<>();
 *       return result;
 *   }
 * 
 * 插桩后等效于：
 *   public Map queryUser(String username, Integer age) {
 *       CallLogAspect.before("UserService", "queryUser", "(...)", [username, age]);
 *       try {
 *           Map result = new HashMap<>();
 *           CallLogAspect.after("UserService", "queryUser", "(...)", result);
 *           return result;
 *       } catch (Throwable e) {
 *           CallLogAspect.error("UserService", "queryUser", "(...)", e);
 *           throw e;
 *       }
 *   }
 * 
 * 【为什么使用静态方法？】
 * 1. 静态方法可以直接调用，不需要实例化对象
 * 2. 字节码插桩时更方便（INVOKESTATIC指令）
 * 3. 避免创建额外的对象，减少性能开销
 * 
 * 【使用场景】
 * - 方法调用日志记录
 * - 性能监控（可以计算执行耗时）
 * - 异常追踪
 * - 参数和返回值审计
 */
public class CallLogAspect {

    /**
     * 方法执行前调用 - 记录方法调用信息
     * 
     * 【调用时机】
     * 在目标方法的第一条指令执行之前调用。
     * 
     * 【参数详解】
     * @param className 类名（内部名称格式）
     *                  例如："com/wujiuye/demo/UserService"
     * 
     * @param methodName 方法名
     *                   例如："queryUser"
     * 
     * @param descriptor 方法描述符
     *                   格式：(参数类型)返回类型
     *                   例如："(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;"
     *                   表示：(String, Integer) -> Map
     * 
     * @param params 方法参数数组
     *               例如：["wujiuye", 25]
     *               基本类型会被装箱为Object
     *               如果方法没有参数，这个值为null
     * 
     * 【输出示例】
     * 方法开始执行时间：1634567890123
     * 类名：com/wujiuye/demo/UserService
     * 方法名：queryUser
     * 方法描述符：(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;
     * 参数：["wujiuye",25]
     */
    public static void before(String className, String methodName,
                              String descriptor, Object[] params) {
        System.out.println("方法开始执行时间：" + System.currentTimeMillis());
        System.out.println("类名：" + className);
        System.out.println("方法名：" + methodName);
        System.out.println("方法描述符：" + descriptor);
        // 使用fastjson将参数序列化为JSON字符串，方便阅读
        System.out.println("参数：" + JSON.toJSONString(params));
    }

    /**
     * 方法执行异常时调用 - 记录异常信息
     * 
     * 【调用时机】
     * 当目标方法抛出Throwable类型的异常时调用。
     * 在catch块中执行，异常会被重新抛出。
     * 
     * 【参数详解】
     * @param className 类名
     * @param methodName 方法名
     * @param descriptor 方法描述符
     * 
     * @param throwable 捕获的异常对象
     *                  包含异常类型、消息、堆栈信息等
     * 
     * 【输出示例】
     * 方法执行出现异常时间：1634567890456
     * 类名：com/wujiuye/demo/UserService
     * 方法名：queryUser
     * 方法描述符：(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;
     * 异常信息：NullPointerException
     * 
     * 【注意】
     * 这个方法只记录异常信息，不会阻止异常传播。
     * 异常会被重新抛出，调用者仍然可以捕获处理。
     */
    public static void error(String className, String methodName,
                                 String descriptor, Throwable throwable) {
        System.out.println("方法执行出现异常时间：" + System.currentTimeMillis());
        System.out.println("类名：" + className);
        System.out.println("方法名：" + methodName);
        System.out.println("方法描述符：" + descriptor);
        // 只记录异常消息，不记录完整堆栈（避免日志过多）
        System.out.println("异常信息：" + throwable.getMessage());
    }

    /**
     * 方法执行完成后调用 - 记录返回值
     * 
     * 【调用时机】
     * 在目标方法返回之前调用（在return指令之前）。
     * 只有方法正常返回时才会调用，异常时不会调用。
     * 
     * 【参数详解】
     * @param className 类名
     * @param methodName 方法名
     * @param descriptor 方法描述符
     * 
     * @param returnValue 方法返回值
     *                    基本类型会被装箱为Object
     *                    void方法返回null
     * 
     * 【输出示例】
     * 方法执行完成时间：1634567890789
     * 类名：com/wujiuye/demo/UserService
     * 方法名：queryUser
     * 方法描述符：(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;
     * 返回值：{"age":25,"username":"wujiuye"}
     * 
     * 【应用场景】
     * 1. 记录方法返回值，用于调试
     * 2. 计算执行耗时（结合before的时间戳）
     * 3. 审计返回值是否符合预期
     */
    public static void after(String className, String methodName,
                             String descriptor, Object returnValue) {
        System.out.println("方法执行完成时间：" + System.currentTimeMillis());
        System.out.println("类名：" + className);
        System.out.println("方法名：" + methodName);
        System.out.println("方法描述符：" + descriptor);
        // 使用fastjson将返回值序列化为JSON字符串
        System.out.println("返回值：" + JSON.toJSONString(returnValue));
    }

}
