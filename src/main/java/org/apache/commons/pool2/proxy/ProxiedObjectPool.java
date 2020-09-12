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
package org.apache.commons.pool2.proxy;

import java.util.NoSuchElementException;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.UsageTracking;

/**
 * Create a new object pool where the pooled objects are wrapped in proxies
 * allowing better control of pooled objects and in particular the prevention
 * of the continued use of an object by a client after that client returns the
 * object to the pool.
 *
 * @param <T> type of the pooled object
 * @since 2.0
 */
public class ProxiedObjectPool<T> implements ObjectPool<T> {

    /** 原始的对象池 */
    private final ObjectPool<T> pool;
    /** 用于创建代理对象，该对象对原始的池对象又包了一层 */
    private final ProxySource<T> proxySource;


    public ProxiedObjectPool(final ObjectPool<T> pool, final ProxySource<T> proxySource) {
        this.pool = pool;
        this.proxySource = proxySource;
    }


    // --------------------------------------------------- ObjectPool<T> methods

    @Override
    public void addObject() throws Exception, IllegalStateException, UnsupportedOperationException {
        pool.addObject();
    }

    /**
     * 从对象池借用一个对象，该对象被封装为代理对象返回
     *
     * @return
     * @throws Exception
     * @throws NoSuchElementException
     * @throws IllegalStateException
     */
    @SuppressWarnings("unchecked")
    @Override
    public T borrowObject() throws Exception, NoSuchElementException, IllegalStateException {
        UsageTracking<T> usageTracking = null;
        if (pool instanceof UsageTracking) {
            usageTracking = (UsageTracking<T>) pool;
        }
        final T pooledObject = pool.borrowObject();
        final T proxy = proxySource.createProxy(pooledObject, usageTracking);
        return proxy;
    }

    /**
     * 将代理对象归还回对象池，归还时，先通过 proxySource 获取原始的池对象，然后再进行归还
     *
     * @param proxy
     * @throws Exception
     */
    @Override
    public void returnObject(final T proxy) throws Exception {
        final T pooledObject = proxySource.resolveProxy(proxy);
        pool.returnObject(pooledObject);
    }

    /**
     * 将对象置为失效状态
     *
     * @param proxy
     * @throws Exception
     */
    @Override
    public void invalidateObject(final T proxy) throws Exception {
        final T pooledObject = proxySource.resolveProxy(proxy);
        pool.invalidateObject(pooledObject);
    }

    @Override
    public int getNumIdle() {
        return pool.getNumIdle();
    }

    @Override
    public int getNumActive() {
        return pool.getNumActive();
    }

    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        pool.clear();
    }

    @Override
    public void close() {
        pool.close();
    }

    /**
     * @since 2.4.3
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ProxiedObjectPool [pool=");
        builder.append(pool);
        builder.append(", proxySource=");
        builder.append(proxySource);
        builder.append("]");
        return builder.toString();
    }
}
