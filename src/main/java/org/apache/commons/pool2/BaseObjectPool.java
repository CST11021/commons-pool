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

/**
 * {@link ObjectPool}的简单基础实现。
 * 可选操作被实现为不执行任何操作，返回指示其不受支持的值或引发{@link UnsupportedOperationException}。
 *
 * 此类旨在实现线程安全
 *
 * @param <T> Type of element pooled in this pool.
 * @since 2.0
 */
public abstract class BaseObjectPool<T> extends BaseObject implements ObjectPool<T> {

    private volatile boolean closed = false;

    // 抽象方法，交由子类实现

    @Override
    public abstract T borrowObject() throws Exception;

    @Override
    public abstract void returnObject(T obj) throws Exception;

    @Override
    public abstract void invalidateObject(T obj) throws Exception;



    // 此基本实现中不支持的实现

    @Override
    public int getNumIdle() {
        return -1;
    }

    @Override
    public int getNumActive() {
        return -1;
    }

    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addObject() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This affects the behavior of <code>isClosed</code> and
     * <code>assertOpen</code>.
     * </p>
     */
    @Override
    public void close() {
        closed = true;
    }

    /**
     * Has this pool instance been closed.
     *
     * @return <code>true</code> when this pool has been closed.
     */
    public final boolean isClosed() {
        return closed;
    }

    /**
     * Throws an <code>IllegalStateException</code> when this pool has been
     * closed.
     *
     * @throws IllegalStateException when this pool has been closed.
     * @see #isClosed()
     */
    protected final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("closed=");
        builder.append(closed);
    }
}
