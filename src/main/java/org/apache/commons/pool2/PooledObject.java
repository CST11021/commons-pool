/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.pool2;

import org.apache.commons.pool2.impl.SecurityManagerCallStack;
import org.apache.commons.pool2.impl.ThrowableCallStack;

import java.io.PrintWriter;
import java.util.Deque;

/**
 * 该类包装了对象池中原始的对象实例，此类的实现必须是线程安全的。
 *
 * @param <T> 池中对象的类型
 * @since 2.0
 */
public interface PooledObject<T> extends Comparable<PooledObject<T>> {

    // 获取池对象的信息

    /**
     * 返回被包装的原始对象
     *
     * @return
     */
    T getObject();

    /**
     * 获取该对象的当前状态
     *
     * @return state
     */
    PooledObjectState getState();

    /**
     * 该原始对象的创建时间
     *
     * @return
     */
    long getCreateTime();

    /**
     * 返回该对象上次被借用的时间
     *
     * @return
     */
    long getLastBorrowTime();

    /**
     * 获取上一次借出去到现在的间隔时间（以毫秒为单位）
     *
     * @return
     */
    long getActiveTimeMillis();

    /**
     * 获取此对象被借用的次数
     *
     * @return -1 by default for old implementations prior to release 2.7.0.
     * @since 2.7.0
     */
    default long getBorrowedCount() {
        return -1;
    }

    /**
     * 返回该对象上次处于空闲状态距离现在的时间（以毫秒为单位）
     *
     * @return
     */
    long getIdleTimeMillis();

    /**
     * 获取该对象上次归还的时间
     *
     * @return The time the object was last returned
     */
    long getLastReturnTime();

    /**
     * 返回上次使用时间的一个估计值，如果Pooled Object实现了TrackedUse接口，那么返回值将是TrackedUse.getLastUsed()和getLastBorrowTime()的较大者，否则返回值和getLastBorrowTime()相等
     *
     * @return the last time this object was used
     */
    long getLastUsedTime();



    // 变更对象的生命周期


    /**
     * 尝试将池对象置于{@link PooledObjectState#EVICTION}状态，表示当前正在做驱逐测试
     *
     * @return 设置成功时返回true
     */
    boolean startEvictionTest();

    /**
     * 通知对象驱逐测试已结束
     *
     * @param idleQueue The queue of idle objects to which the object should be returned
     * @return Currently not used
     */
    boolean endEvictionTest(Deque<PooledObject<T>> idleQueue);

    /**
     * 当对象要被借出去前，会调用该方法，判断是否可以将该对象借出去，如果可以将对象标记为借出状态，并返回true（注意：只有对象是空闲状态的时候，才允许被标记）
     *
     * @return {@code true} if the original state was {@link PooledObjectState#IDLE IDLE}
     */
    boolean allocate();

    /**
     * 取消分配对象，并将其设置为{@link PooledObjectState#IDLE}（如果当前为{@link PooledObjectState#ALLOCATED}）。
     *
     * @return {@code true} if the state was {@link PooledObjectState#ALLOCATED ALLOCATED}
     */
    boolean deallocate();

    /**
     * 将对象置为{@link PooledObjectState#INVALID}无效状态
     */
    void invalidate();

    /**
     * 将该对象标记为已放弃
     */
    void markAbandoned();

    /**
     * 将对象标记为归还状态
     */
    void markReturning();

    /**
     * 记录最后一次使用该对象的时间，便于堆栈跟踪
     */
    void use();


    // Abandoned和堆栈跟踪

    /**
     * 设置是否记录对象使用的堆栈信息，可用于池泄漏时问题追溯
     *
     * @param logAbandoned The new configuration setting for abandonedobject tracking
     */
    void setLogAbandoned(boolean logAbandoned);

    /**
     * 堆栈的跟踪日志是否需要打印完整的方法调用，如果需要则使用{@link ThrowableCallStack}，否则{@link SecurityManagerCallStack}
     *
     * @param requireFullStackTrace the new configuration setting for abandoned object logging
     * @since 2.7.0
     */
    default void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        // noop
    }

    /**
     * 输出对象的调用堆栈
     *
     * @param writer The destination for the debug output
     */
    void printStackTrace(PrintWriter writer);








    @Override
    int compareTo(PooledObject<T> other);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    @Override
    String toString();

}
