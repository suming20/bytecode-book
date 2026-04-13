package com.wujiuye.asmbytecode.book.second.handler;

import com.wujiuye.asmbytecode.book.second.type.ClassFile;
import com.wujiuye.asmbytecode.book.second.type.CpInfo;
import com.wujiuye.asmbytecode.book.second.type.U1;
import com.wujiuye.asmbytecode.book.second.type.U2;
import com.wujiuye.asmbytecode.book.second.type.cp.CONSTANT_Long_info;

import java.nio.ByteBuffer;

/**
 * 解析常量池
 * Java虚拟机执行字节码指令依赖常量池表中的常量信息，如创建对象的new指令，需要指定对象的类型描述符，
 * new指令的操作数的值为该类型描述符对应的常量在常量池中的索引，虚拟机将根据常量信息到方法区寻找对应的class元数据。
 */
public class ConstantPoolHandler implements BaseByteCodeHandler {

    @Override
    public int order() {
        return 2;
    }

    @Override
    public void read(ByteBuffer codeBuf, ClassFile classFile) throws Exception {
        U2 cpLen = new U2(codeBuf.get(), codeBuf.get());
        classFile.setConstant_pool_count(cpLen);
        int cpInfoLeng = cpLen.toInt() - 1;
        classFile.setConstant_pool(new CpInfo[cpInfoLeng]);
        for (int i = 0; i < cpInfoLeng; i++) {
            U1 tag = new U1(codeBuf.get());
            CpInfo cpInfo = CpInfo.newCpInfo(tag);
            cpInfo.read(codeBuf);
            // System.out.println("#" + (i + 1) + ":" + cpInfo);
            classFile.getConstant_pool()[i] = cpInfo;
            if (cpInfo instanceof CONSTANT_Long_info) {
                i++; // jump n+2
            }
        }
    }

}
