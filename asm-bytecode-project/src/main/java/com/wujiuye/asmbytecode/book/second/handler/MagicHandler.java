package com.wujiuye.asmbytecode.book.second.handler;

import com.wujiuye.asmbytecode.book.second.type.ClassFile;
import com.wujiuye.asmbytecode.book.second.type.U4;

import java.nio.ByteBuffer;

/**
 * 魔数占四个字节，它只是用来确定这个文件是否是一个class文件。魔数固定值为0xCAFEBABE，这个值永远不会改变。
 */
public class MagicHandler implements BaseByteCodeHandler {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public void read(ByteBuffer codeBuf, ClassFile classFile) throws Exception {
        // 连续读取4个字节，读取魔术字段
        classFile.setMagic(new U4(codeBuf.get(), codeBuf.get(), codeBuf.get(), codeBuf.get()));
        if (!"0xCAFEBABE".equalsIgnoreCase(classFile.getMagic().toHexString())) {
            throw new Exception("这不是一个Class文件");
        }
    }

}
