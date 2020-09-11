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

import java.io.PrintWriter;
import java.util.Deque;

/**
 * 该类包装了对象池中原始的对象实例，此类的实现必须是线程安全的。
 *
 * @param <T> 池中对象的类型
 * @since 2.0
 */
public interface PooledObject<T> extends Comparable<PooledObject<T>> {

    /**
     * 返回被包装的原始对象
     *
     * @return
     */
    T getObject();

    /**
     * 该原始对象的创建时间
     *
     * @return
     */
    long getCreateTime();

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
     * 返回该对象上次被借用的时间
     *
     * @return
     */
    long getLastBorrowTime();

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

    /**
     * 尝试将池对象置于{@link PooledObjectState#EVICTION}状态
     *
     * @return 设置成功时返回true
     */
    boolean startEvictionTest();

    /**
     * Called to inform the object that the eviction test has ended.
     *
     * @param idleQueue The queue of idle objects to which the object should be returned
     * @return Currently not used
     */
    boolean endEvictionTest(Deque<PooledObject<T>> idleQueue);

    /**
     * 分配对象
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
     * 设置是否记录对象使用的堆栈信息，可用于池泄漏时问题追溯
     *
     * @param logAbandoned The new configuration setting for abandonedobject tracking
     */
    void setLogAbandoned(boolean logAbandoned);

    /**
     * Configures the stack trace generation strategy based on whether or not fully detailed stack traces are required.
     * When set to false, abandoned logs may only include caller class information rather than method names, line
     * numbers, and other normal metadata available in a full stack trace.
     *
     * @param requireFullStackTrace the new configuration setting for abandoned object logging
     * @since 2.7.0
     */
    default void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        // noop
    }

    /**
     * Record the current stack trace as the last time the object was used.
     */
    void use();

    /**
     * Prints the stack trace of the code that borrowed this pooled object and
     * the stack trace of the last code to use this object (if available) to
     * the supplied writer.
     *
     * @param writer The destination for the debug output
     */
    void printStackTrace(PrintWriter writer);

    /**
     * Returns the state of this object.
     *
     * @return state
     */
    PooledObjectState getState();

    /**
     * 将该对象标记为已放弃
     */
    void markAbandoned();

    /**
     * 将对象标记为归还状态
     */
    void markReturning();




    @Override
    int compareTo(PooledObject<T> other);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    @Override
    String toString();

}
