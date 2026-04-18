package com.wujiuye.demo;


import java.util.Map;

/**
 * 演示应用程序 - 用于演示Java Agent的运行效果
 * 
 * 【类的作用】
 * 这个类是Java Agent的测试目标程序。
 * 它循环调用UserService.queryUser()方法，
 * 每次调用都会被Java Agent插桩，输出监控日志。
 * 
 * 【如何运行】
 * 需要添加-javaagent参数启动：
 * -javaagent:/path/to/my-java-agent-1.0-jar-with-dependencies.jar
 * 
 * 【完整启动命令示例】
 * java -javaagent:/Users/wjy/MyProjects/ASM动态改写字节码从入门到实战/myagent/my-java-agent/target/my-java-agent-1.0-jar-with-dependencies.jar \
 *      -cp demo.jar \
 *      com.wujiuye.demo.DemoAppcliction
 * 
 * 【运行效果】
 * 程序启动后，会每隔10秒调用一次queryUser方法，
 * 每次调用都会输出before和after日志。
 * 按Ctrl+C或中断信号可以停止程序。
 * 
 * 【执行流程】
 * 1. JVM启动，加载-javaagent指定的Agent
 * 2. 调用MyJavaAgent.premain()或agentmain()
 * 3. 注册BusinessClassFileTransformer
 * 4. 加载DemoAppcliction类时，触发转换
 * 5. 加载UserService类时，触发转换（插入监控代码）
 * 6. 执行main方法
 * 7. 循环调用queryUser，每次都会输出监控日志
 */
public class DemoAppcliction {

    /**
     * 主方法 - 程序入口
     * 
     * 【执行流程】
     * 1. 打印启动信息
     * 2. 创建UserService实例
     * 3. 进入循环，每隔10秒调用一次queryUser
     * 4. 打印返回结果
     * 5. 等待10秒
     * 6. 检查是否收到中断信号，如果没有则继续循环
     * 
     * @param args 命令行参数（未使用）
     * @throws InterruptedException 线程休眠被中断时抛出
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("main function runing...");
        
        // 创建UserService实例
        // 这个类已经被Java Agent插桩，方法调用会被监控
        UserService userService = new UserService();
        
        /**
         * 循环调用queryUser方法
         * 
         * !Thread.interrupted() 检查是否收到中断信号
         * - 返回true: 收到中断信号，退出循环
         * - 返回false: 未收到中断信号，继续循环
         * 
         * 这个循环会一直执行，直到：
         * 1. 用户按Ctrl+C发送中断信号
         * 2. 其他线程调用thread.interrupt()
         */
        while (!Thread.interrupted()) {
            /**
             * 调用queryUser方法
             * 
             * 【实际执行流程】
             * 1. 调用userService.queryUser("wujiuye", 25)
             * 2. 实际执行的是插桩后的方法：
             *    a. 调用CallLogAspect.before() - 输出方法开始日志
             *    b. 执行原始方法体
             *    c. 调用CallLogAspect.after() - 输出方法完成日志
             *    d. 返回结果
             * 3. 如果方法抛出异常：
             *    a. 调用CallLogAspect.error() - 输出异常日志
             *    b. 重新抛出异常
             */
            Map<String, Object> user = userService.queryUser("wujiuye", 25);
            
            // 打印查询结果
            System.out.println(user);
            
            // 休眠10秒（10000毫秒）
            // 休眠期间如果收到中断信号，会抛出InterruptedException
            Thread.sleep(10000);
        }
    }

}
