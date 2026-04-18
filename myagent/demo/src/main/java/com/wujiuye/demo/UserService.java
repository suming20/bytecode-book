package com.wujiuye.demo;

import java.util.HashMap;
import java.util.Map;


/**
 * 用户服务类 - 用于演示Java Agent字节码插桩的目标类
 * 
 * 【类的作用】
 * 这个类是Java Agent插桩的目标类。
 * 当程序运行时，Java Agent会自动在这个类的方法中插入监控代码。
 * 
 * 【插桩效果】
 * 原始方法：
 *   public Map<String, Object> queryUser(String username, Integer age) {
 *       Map<String, Object> result = new HashMap<>();
 *       result.put("username", username);
 *       result.put("age", age);
 *       return result;
 *   }
 * 
 * 插桩后等效于：
 *   public Map<String, Object> queryUser(String username, Integer age) {
 *       // 插入的监控代码 - 方法开始
 *       CallLogAspect.before(
 *           "com/wujiuye/demo/UserService",
 *           "queryUser",
 *           "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;",
 *           new Object[]{username, age}
 *       );
 *       try {
 *           Map<String, Object> result = new HashMap<>();
 *           result.put("username", username);
 *           result.put("age", age);
 *           
 *           // 插入的监控代码 - 方法返回
 *           CallLogAspect.after(
 *               "com/wujiuye/demo/UserService",
 *               "queryUser",
 *               "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;",
 *               result
 *           );
 *           return result;
 *       } catch (Throwable e) {
 *           // 插入的监控代码 - 方法异常
 *           CallLogAspect.error(
 *               "com/wujiuye/demo/UserService",
 *               "queryUser",
 *               "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;",
 *               e
 *           );
 *           throw e;
 *       }
 *   }
 * 
 * 【运行输出示例】
 * 方法开始执行时间：1634567890123
 * 类名：com/wujiuye/demo/UserService
 * 方法名：queryUser
 * 方法描述符：(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;
 * 参数：["wujiuye",25]
 * 方法执行完成时间：1634567890125
 * 类名：com/wujiuye/demo/UserService
 * 方法名：queryUser
 * 方法描述符：(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;
 * 返回值：{"age":25,"username":"wujiuye"}
 */
public class UserService {

    /**
     * 查询用户信息
     * 
     * 【方法说明】
     * 这个方法会被Java Agent自动插桩，
     * 在方法开始、返回、异常时分别调用CallLogAspect的对应方法。
     * 
     * @param username 用户名
     * @param age 年龄
     * @return 包含用户信息的Map
     */
    public Map<String, Object> queryUser(String username, Integer age) {
        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("age", age);
        return result;
    }

}
