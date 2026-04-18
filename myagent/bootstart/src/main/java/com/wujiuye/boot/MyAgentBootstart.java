package com.wujiuye.boot;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java Agent启动器 - 用于动态加载Agent到运行中的JVM进程
 * 
 * 【类的作用】
 * 这个类提供了一个交互式工具，用于：
 * 1. 显示当前所有Java进程
 * 2. 让用户选择要加载Agent的目标进程
 * 3. 使用Attach API将Agent动态加载到目标进程
 * 
 * 【什么是Attach API？】
 * Attach API是JDK提供的工具API，允许一个JVM进程连接到另一个JVM进程。
 * 核心类：
 * - VirtualMachine: 表示一个JVM进程
 * - VirtualMachineDescriptor: JVM进程的描述符
 * 
 * 【Attach API的使用场景】
 * 1. 动态加载Agent（如本例）
 * 2. 获取JVM信息（堆内存、线程等）
 * 3. 触发GC、Dump堆等
 * 4. 性能分析工具（如jmap、jstack）
 * 
 * 【与premain的区别】
 * - premain: JVM启动时加载，需要-javaagent参数
 * - agentmain: JVM运行时加载，使用Attach API
 * 
 * 【使用流程】
 * 1. 运行目标Java程序（如DemoAppcliction）
 * 2. 运行MyAgentBootstart
 * 3. 选择目标进程ID
 * 4. Agent被加载到目标进程
 * 5. 目标进程开始输出监控日志
 * 
 * 【注意事项】
 * 1. 需要JDK（不是JRE），因为Attach API在tools.jar中
 * 2. 两个进程必须是同一个用户
 * 3. Windows和Linux的Attach机制不同
 */
public class MyAgentBootstart {

    /**
     * 显示当前所有Java进程
     * 
     * 【方法作用】
     * 列出系统中所有正在运行的Java进程，
     * 让用户选择要加载Agent的目标进程。
     * 
     * @return 进程ID到序号的映射
     *         例如：{1: "12345", 2: "12346"}
     *         key是用户输入的序号，value是进程ID
     * 
     * 【执行流程】
     * 1. 调用VirtualMachine.list()获取所有Java进程
     * 2. 遍历进程列表，打印信息
     * 3. 建立序号到PID的映射
     * 
     * 【VirtualMachine.list()返回什么？】
     * 返回所有Java进程的VirtualMachineDescriptor列表，
     * 每个Descriptor包含：
     * - id: 进程ID（PID）
     * - displayName: 进程显示名称（通常是main类名）
     */
    private static Map<Integer, String> showAllJavaProcess() {
        Map<Integer, String> pidMap = new HashMap<>();
        
        // 获取所有Java进程的描述符列表
        List<VirtualMachineDescriptor> list = VirtualMachine.list();
        
        int rows = 0;
        System.out.println("找到如下Java进程，请选择：");
        
        // 遍历所有Java进程
        for (VirtualMachineDescriptor vmd : list) {
            // 建立序号到PID的映射（序号从1开始）
            pidMap.put(++rows, vmd.id());
            
            /**
             * 打印进程信息
             * 
             * vmd.id(): 进程ID（PID）
             * vmd.displayName(): 进程显示名称
             * 
             * displayName示例：
             * - "com.wujiuye.demo.DemoAppcliction"
             * - "org.apache.catalina.startup.Bootstrap start"
             * 
             * split(" ")[0]: 只取第一个单词，去掉参数部分
             */
            System.out.println("[" + rows + "] " + vmd.id() + "\t" + vmd.displayName().split(" ")[0]);
        }
        return pidMap;
    }

    /**
     * 读取用户输入
     * 
     * 【方法作用】
     * 从标准输入读取一行文本，直到遇到回车符。
     * 
     * @return 用户输入的字符串
     * @throws IOException 读取输入时发生IO异常
     * 
     * 【执行流程】
     * 1. 创建字符缓冲区
     * 2. 逐个字符读取
     * 3. 遇到\r或\n时停止读取
     * 4. 返回读取的字符串
     * 
     * 【为什么不使用Scanner或BufferedReader？】
     * 可能是为了演示底层IO操作，
     * 或者避免引入额外的类。
     * 实际开发中建议使用Scanner或BufferedReader。
     */
    private static String readString() throws IOException {
        char[] inputBuf = new char[1024];  // 输入缓冲区，最大1024字符
        int leng = 0;  // 已读取的字符数
        while (true) {
            // 读取一个字符
            char ch = (char) System.in.read();
            
            // 遇到回车符或换行符，停止读取
            if (ch == '\r') {
                break;
            }
            if (ch == '\n') {
                break;
            }
            
            // 将字符存入缓冲区
            inputBuf[leng++] = ch;
        }
        // 返回读取的字符串
        return new String(inputBuf, 0, leng);
    }

    /**
     * 将Agent加载到目标JVM进程
     * 
     * 【方法作用】
     * 使用Attach API连接到目标JVM进程，
     * 并将Agent的jar包加载到该进程中。
     * 
     * 【执行流程】
     * 1. VirtualMachine.attach(pid): 连接到目标进程
     * 2. vm.loadAgent(jarPath): 加载Agent
     * 3. vm.detach(): 断开连接
     * 
     * @param pid JVM进程ID
     *            例如："12345"
     * 
     * @throws Exception 连接或加载过程中可能抛出异常
     * 
     * 【loadAgent的参数】
     * 1. jarPath: Agent的jar包路径
     *    必须是绝对路径，且jar包必须存在
     * 
     * 2. options: 传递给Agent的参数
     *    对应agentmain方法的agentOps参数
     *    这里传null，表示不传递参数
     * 
     * 【Agent加载后会发生什么？】
     * 1. JVM找到jar包中的MANIFEST.MF
     * 2. 读取Agent-Class属性，找到Agent类
     * 3. 调用Agent类的agentmain方法
     * 4. agentmain执行字节码转换逻辑
     * 5. 目标进程的方法开始被监控
     * 
     * 【MANIFEST.MF示例】
     * Agent-Class: com.wujiuye.agent.MyJavaAgent
     * Can-Retransform-Classes: true
     */
    private static void attachPid(String pid) throws Exception {
        VirtualMachine vm;
        
        /**
         * 连接到目标JVM进程
         * 
         * VirtualMachine.attach()会：
         * 1. 检查进程是否存在
         * 2. 建立通信通道
         * 3. 返回VirtualMachine对象
         * 
         * 如果进程不存在或无法连接，会抛出异常。
         */
        vm = VirtualMachine.attach(pid);
        System.out.println("attach pid：" + vm.id());
        try {
            /**
             * 加载Agent到目标进程
             * 
             * 【参数说明】
             * 1. jar包路径：必须是绝对路径
             *    注意：这个路径是目标进程所在机器的路径
             *    如果是远程进程，需要确保路径存在
             * 
             * 2. null: 不传递参数给agentmain方法
             * 
             * 【执行过程】
             * 1. JVM读取jar包的MANIFEST.MF
             * 2. 找到Agent-Class: com.wujiuye.agent.MyJavaAgent
             * 3. 加载MyJavaAgent类
             * 4. 调用agentmain(null, instrumentation)
             * 5. agentmain执行字节码转换
             */
            vm.loadAgent("/Users/wjy/MyProjects/ASM动态改写字节码从入门到实战/myagent/my-java-agent/target/my-java-agent-1.0-jar-with-dependencies.jar", null);
        } finally {
            /**
             * 断开与目标进程的连接
             * 
             * 必须在finally块中调用，确保即使发生异常也能断开连接。
             * 不调用detach会导致资源泄漏。
             */
            vm.detach();
        }
    }

    /**
     * 主方法 - 程序入口
     * 
     * 【执行流程】
     * 1. 显示所有Java进程
     * 2. 等待用户输入序号
     * 3. 解析用户输入
     * 4. 获取目标进程ID
     * 5. 加载Agent到目标进程
     * 
     * @param args 命令行参数（未使用）
     * @throws Exception 执行过程中可能抛出异常
     * 
     * 【使用示例】
     * 运行输出：
     * 找到如下Java进程，请选择：
     * [1] 12345    com.wujiuye.demo.DemoAppcliction
     * [2] 12346    org.apache.catalina.startup.Bootstrap
     * 
     * 用户输入：1
     * 
     * 然后Agent会被加载到进程12345，
     * 目标进程开始输出监控日志。
     */
    public static void main(String[] args) throws Exception {
        // 显示所有Java进程，获取进程列表
        Map<Integer, String> pidMap = showAllJavaProcess();
        
        // 读取用户输入的序号
        Integer inputId = Integer.parseInt(readString());
        
        // 根据序号获取目标进程ID
        String targetPid = pidMap.get(inputId);
        
        // 将Agent加载到目标进程
        attachPid(targetPid);
    }

}
