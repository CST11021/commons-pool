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

import java.io.Closeable;
import java.util.NoSuchElementException;

/**
 * A pooling simple interface.
 * <p>
 * Example of use:
 * </p>
 * <pre style="border:solid thin; padding: 1ex;"
 * > Object obj = <code style="color:#00C">null</code>;
 *
 * <code style="color:#00C">try</code> {
 * obj = pool.borrowObject();
 * <code style="color:#00C">try</code> {
 * <code style="color:#0C0">//...use the object...</code>
 * } <code style="color:#00C">catch</code>(Exception e) {
 * <code style="color:#0C0">// invalidate the object</code>
 * pool.invalidateObject(obj);
 * <code style="color:#0C0">// do not return the object to the pool twice</code>
 * obj = <code style="color:#00C">null</code>;
 * } <code style="color:#00C">finally</code> {
 * <code style="color:#0C0">// make sure the object is returned to the pool</code>
 * <code style="color:#00C">if</code>(<code style="color:#00C">null</code> != obj) {
 * pool.returnObject(obj);
 * }
 * }
 * } <code style="color:#00C">catch</code>(Exception e) {
 * <code style="color:#0C0">// failed to borrow an object</code>
 * }</pre>
 * <p>
 * See {@link BaseObjectPool} for a simple base implementation.
 * </p>
 *
 * @param <T> Type of element pooled in this pool.
 * @see PooledObjectFactory
 * @see KeyedObjectPool
 * @see BaseObjectPool
 * @since 2.0
 */
public interface ObjectPool<T> extends Closeable {

    /**
     * 创建一个对象，并放到对象池
     *
     * @throws Exception
     * @throws IllegalStateException
     * @throws UnsupportedOperationException
     */
    void addObject() throws Exception, IllegalStateException, UnsupportedOperationException;

    /**
     * 创建指定数量的对象，并放到对象池
     *
     * @param count the number of idle objects to add.
     * @throws Exception when {@link ObjectPool#addObject()} fails.
     * @since 2.8.0
     */
    default void addObjects(final int count) throws Exception {
        for (int i = 0; i < count; i++) {
            addObject();
        }
    }

    /**
     * 从该池获取一个对象实例
     *
     * Instances returned from this method will have been either newly created with {@link PooledObjectFactory#makeObject} or will be a previously
     * idle object and have been activated with {@link PooledObjectFactory#activateObject} and then validated with {@link PooledObjectFactory#validateObject}.
     * </p>
     * <p>
     * By contract, clients <strong>must</strong> return the borrowed instance
     * using {@link #returnObject}, {@link #invalidateObject}, or a related
     * method as defined in an implementation or sub-interface.
     * </p>
     * <p>
     * The behaviour of this method when the pool has been exhausted
     * is not strictly specified (although it may be specified by
     * implementations).
     * </p>
     *
     * @return an instance from this pool.
     * @throws IllegalStateException  after {@link #close close} has been called on this pool.
     * @throws Exception              when {@link PooledObjectFactory#makeObject} throws an
     *                                exception.
     * @throws NoSuchElementException when the pool is exhausted and cannot or will not return
     *                                another instance.
     */
    T borrowObject() throws Exception, NoSuchElementException, IllegalStateException;

    /**
     * 使池中的该对象实例无效
     *
     * @param obj a {@link #borrowObject borrowed} instance to be disposed.
     * @throws Exception if the instance cannot be invalidated
     */
    void invalidateObject(T obj) throws Exception;

    /**
     * 将实例放回到池中。根据合同，必须使用{@link #borrowObject()}或实现或子接口中定义的相关方法来获取obj。
     *
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     * @throws IllegalStateException if an attempt is made to return an object to the pool that
     *                               is in any state other than allocated (i.e. borrowed).
     *                               Attempting to return an object more than once or attempting
     *                               to return an object that was never borrowed from the pool
     *                               will trigger this exception.
     * @throws Exception             if an instance cannot be returned to the pool
     */
    void returnObject(T obj) throws Exception;

    /**
     * 清除池中空闲的所有对象，释放任何关联的资源（可选操作）。清除的空闲对象必须为{@link PooledObjectFactory#destroyObject(PooledObject)}
     *
     * @throws UnsupportedOperationException if this implementation does not support the operation
     * @throws Exception                     if the pool cannot be cleared
     */
    void clear() throws Exception, UnsupportedOperationException;

    /**
     * 获得被借出对象数量，如果该池处于非活动状态，则为-1
     *
     * @return the number of instances currently borrowed from this pool.
     */
    int getNumActive();

    /**
     * 当前在该池中空闲的实例数，如果该池处于非活动状态，则为-1
     *
     * @return the number of instances currently idle in this pool.
     */
    int getNumIdle();

    /**
     * Closes this pool, and free any resources associated with it.
     * <p>
     * Calling {@link #addObject} or {@link #borrowObject} after invoking this
     * method on a pool will cause them to throw an {@link IllegalStateException}.
     * </p>
     * <p>
     * Implementations should silently fail if not all resources can be freed.
     * </p>
     */
    @Override
    void close();

}
