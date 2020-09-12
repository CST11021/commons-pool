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
package org.apache.commons.pool2.impl;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectState;
import org.apache.commons.pool2.TrackedUse;

import java.io.PrintWriter;
import java.util.Deque;

/**
 * This wrapper is used to track the additional information, such as state, for
 * the pooled objects.
 * <p>
 * This class is intended to be thread-safe.
 * </p>
 *
 * @param <T> the type of object in the pool
 * @since 2.0
 */
public class DefaultPooledObject<T> implements PooledObject<T> {

    /** 原始的对象引用 */
    private final T object;
    /** 默认的对象初始化状态是空闲的 */
    private PooledObjectState state = PooledObjectState.IDLE;
    /** 对象的创建时间 */
    private final long createTime = System.currentTimeMillis();
    /** 表示最后一次借出去的时间 */
    private volatile long lastBorrowTime = createTime;

    private volatile long lastUseTime = createTime;
    /** 表示最后一次归还的时间 */
    private volatile long lastReturnTime = createTime;
    /** 表示被借出去的次数 */
    private volatile long borrowedCount = 0;
    /**
     * logAbandoned为true时，useUsageTracking也为true时，那么回收被遗弃的对象时，就会打印该对象最后一次的调用堆栈信息了,
     * 如果useUsageTracking为true，即便是logAbandoned为false，那么每次对象的方法调用，一样还是会创建调用堆栈对象。只不过最终被回收时不会打印输出。
     */
    private volatile boolean logAbandoned = false;
    private volatile CallStack borrowedBy = NoOpCallStack.INSTANCE;
    private volatile CallStack usedBy = NoOpCallStack.INSTANCE;

    /**
     * Creates a new instance that wraps the provided object so that the pool can
     * track the state of the pooled object.
     *
     * @param object The object to wrap
     */
    public DefaultPooledObject(final T object) {
        this.object = object;
    }



    // 获取池对象的信息

    @Override
    public T getObject() {
        return object;
    }

    /**
     * Returns the state of this object.
     *
     * @return state
     */
    @Override
    public synchronized PooledObjectState getState() {
        return state;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 获取上一次借出去到现在的间隔时间（以毫秒为单位）
     *
     * @return
     */
    @Override
    public long getActiveTimeMillis() {
        // Take copies to avoid threading issues
        final long rTime = lastReturnTime;
        final long bTime = lastBorrowTime;

        if (rTime > bTime) {
            return rTime - bTime;
        }
        return System.currentTimeMillis() - bTime;
    }

    @Override
    public long getIdleTimeMillis() {
        final long elapsed = System.currentTimeMillis() - lastReturnTime;
        // elapsed may be negative if:
        // - another thread updates lastReturnTime during the calculation window
        // - System.currentTimeMillis() is not monotonic (e.g. system time is set back)
        return elapsed >= 0 ? elapsed : 0;
    }

    @Override
    public long getLastBorrowTime() {
        return lastBorrowTime;
    }

    @Override
    public long getLastReturnTime() {
        return lastReturnTime;
    }

    /**
     * Gets the number of times this object has been borrowed.
     *
     * @return The number of times this object has been borrowed.
     * @since 2.1
     */
    @Override
    public long getBorrowedCount() {
        return borrowedCount;
    }

    /**
     * Returns an estimate of the last time this object was used.  If the class
     * of the pooled object implements {@link TrackedUse}, what is returned is
     * the maximum of {@link TrackedUse#getLastUsed()} and
     * {@link #getLastBorrowTime()}; otherwise this method gives the same
     * value as {@link #getLastBorrowTime()}.
     *
     * @return the last time this object was used
     */
    @Override
    public long getLastUsedTime() {
        if (object instanceof TrackedUse) {
            return Math.max(((TrackedUse) object).getLastUsed(), lastUseTime);
        }
        return lastUseTime;
    }



    // 变更对象的生命周期

    @Override
    public synchronized boolean startEvictionTest() {
        if (state == PooledObjectState.IDLE) {
            state = PooledObjectState.EVICTION;
            return true;
        }

        return false;
    }

    @Override
    public synchronized boolean endEvictionTest(final Deque<PooledObject<T>> idleQueue) {
        if (state == PooledObjectState.EVICTION) {
            state = PooledObjectState.IDLE;
            return true;
        } else if (state == PooledObjectState.EVICTION_RETURN_TO_HEAD) {
            state = PooledObjectState.IDLE;
            // 将对象放在队列的最前面
            if (!idleQueue.offerFirst(this)) {
                // TODO - Should never happen
            }
        }

        return false;
    }

    /**
     * 当对象要被借出去前，会调用该方法，判断是否可以将该对象借出去，如果可以将对象标记为借出状态，并返回true（注意：只有对象是空闲状态的时候，才允许被标记）
     *
     * @return {@code true} if the original state was {@link PooledObjectState#IDLE IDLE}
     */
    @Override
    public synchronized boolean allocate() {
        if (state == PooledObjectState.IDLE) {
            state = PooledObjectState.ALLOCATED;
            // 设置最后借出的时间
            lastBorrowTime = System.currentTimeMillis();
            lastUseTime = lastBorrowTime;
            borrowedCount++;
            //
            if (logAbandoned) {
                borrowedBy.fillInStackTrace();
            }
            return true;
        } else if (state == PooledObjectState.EVICTION) {
            // TODO 无论如何分配，忽略驱逐测试
            state = PooledObjectState.EVICTION_RETURN_TO_HEAD;
            return false;
        }
        // TODO if validating and testOnBorrow == true then pre-allocate for performance
        return false;
    }

    /**
     * Deallocates the object and sets it {@link PooledObjectState#IDLE IDLE}
     * if it is currently {@link PooledObjectState#ALLOCATED ALLOCATED}.
     *
     * @return {@code true} if the state was {@link PooledObjectState#ALLOCATED ALLOCATED}
     */
    @Override
    public synchronized boolean deallocate() {
        if (state == PooledObjectState.ALLOCATED ||
                state == PooledObjectState.RETURNING) {
            state = PooledObjectState.IDLE;
            lastReturnTime = System.currentTimeMillis();
            borrowedBy.clear();
            return true;
        }

        return false;
    }

    /**
     * Sets the state to {@link PooledObjectState#INVALID INVALID}
     */
    @Override
    public synchronized void invalidate() {
        state = PooledObjectState.INVALID;
    }

    @Override
    public void use() {
        lastUseTime = System.currentTimeMillis();
        usedBy.fillInStackTrace();
    }

    /**
     * Marks the pooled object as abandoned.
     */
    @Override
    public synchronized void markAbandoned() {
        state = PooledObjectState.ABANDONED;
    }

    /**
     * 将对象标记为归还状态
     */
    @Override
    public synchronized void markReturning() {
        state = PooledObjectState.RETURNING;
    }



    // Abandoned和堆栈跟踪

    @Override
    public void printStackTrace(final PrintWriter writer) {
        boolean written = borrowedBy.printStackTrace(writer);
        written |= usedBy.printStackTrace(writer);
        if (written) {
            writer.flush();
        }
    }

    @Override
    public void setLogAbandoned(final boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    /**
     * Configures the stack trace generation strategy based on whether or not fully
     * detailed stack traces are required. When set to false, abandoned logs may
     * only include caller class information rather than method names, line numbers,
     * and other normal metadata available in a full stack trace.
     *
     * @param requireFullStackTrace the new configuration setting for abandoned object
     *                              logging
     * @since 2.5
     */
    @Override
    public void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        borrowedBy = CallStackUtils.newCallStack("'Pooled object created' " +
                        "yyyy-MM-dd HH:mm:ss Z 'by the following code has not been returned to the pool:'",
                true, requireFullStackTrace);
        usedBy = CallStackUtils.newCallStack("The last code to use this object was:",
                false, requireFullStackTrace);
    }




    @Override
    public int compareTo(final PooledObject<T> other) {
        final long lastActiveDiff = this.getLastReturnTime() - other.getLastReturnTime();
        if (lastActiveDiff == 0) {
            // Make sure the natural ordering is broadly consistent with equals
            // although this will break down if distinct objects have the same
            // identity hash code.
            // see java.lang.Comparable Javadocs
            return System.identityHashCode(this) - System.identityHashCode(other);
        }
        // handle int overflow
        return (int) Math.min(Math.max(lastActiveDiff, Integer.MIN_VALUE), Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("Object: ");
        result.append(object.toString());
        result.append(", State: ");
        synchronized (this) {
            result.append(state.toString());
        }
        return result.toString();
        // TODO add other attributes
    }
}
