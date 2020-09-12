package com.whz.commons.pool2;

import org.apache.commons.pool2.impl.CallStack;
import org.apache.commons.pool2.impl.SecurityManagerCallStack;
import org.apache.commons.pool2.impl.ThrowableCallStack;
import org.junit.Test;

import java.io.PrintWriter;

/**
 * @Author: wanghz
 * @Date: 2020/9/12 2:20 PM
 */
public class CallStackTest {

    // 打印简单的调用堆栈

    @Test
    public void testSecurityManagerCallStack() {
        PrintWriter printWriter = new PrintWriter(System.out);

        CallStack stack = new SecurityManagerCallStack("测试！！！", true);
        stack.fillInStackTrace();
        stack.printStackTrace(printWriter);

        printWriter.flush();
    }

    // 打印详细的方法调用堆栈

    @Test
    public void testThrowableCallStack() {
        PrintWriter printWriter = new PrintWriter(System.out);

        CallStack stack = new ThrowableCallStack("测试！！！", true);
        stack.fillInStackTrace();
        stack.printStackTrace(printWriter);

        printWriter.flush();
    }

}
