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
     * 上一次处于活动状态所花费的时间（以毫秒为单位）
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
     * Returns an estimate of the last time this object was used.  If the class
     * of the pooled object implements {@link TrackedUse}, what is returned is
     * the maximum of {@link TrackedUse#getLastUsed()} and
     * {@link #getLastBorrowTime()}; otherwise this method gives the same
     * value as {@link #getLastBorrowTime()}.
     *
     * @return the last time this object was used
     */
    long getLastUsedTime();

    /**
     * Attempts to place the pooled object in the
     * {@link PooledObjectState#EVICTION} state.
     *
     * @return <code>true</code> if the object was placed in the
     * {@link PooledObjectState#EVICTION} state otherwise
     * <code>false</code>
     */
    boolean startEvictionTest();

    /**
     * Called to inform the object that the eviction test has ended.
     *
     * @param idleQueue The queue of idle objects to which the object should be
     *                  returned
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
     * Sets the state to {@link PooledObjectState#INVALID INVALID}
     */
    void invalidate();

    /**
     * Is abandoned object tracking being used? If this is true the
     * implementation will need to record the stack trace of the last caller to
     * borrow this object.
     *
     * @param logAbandoned The new configuration setting for abandoned
     *                     object tracking
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
     * Marks the object as returning to the pool.
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
