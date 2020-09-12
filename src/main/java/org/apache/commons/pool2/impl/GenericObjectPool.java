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

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.PooledObjectState;
import org.apache.commons.pool2.SwallowedExceptionListener;
import org.apache.commons.pool2.TrackedUse;
import org.apache.commons.pool2.UsageTracking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 常用的对象池
 *
 * @param <T> Type of element pooled in this pool.
 * @see GenericKeyedObjectPool
 * @since 2.0
 */
public class GenericObjectPool<T> extends BaseGenericObjectPool<T> implements ObjectPool<T>, GenericObjectPoolMXBean, UsageTracking<T> {

    //--- JMX support ----------------------------------------------------------

    private volatile String factoryType = null;

    // --- configuration attributes --------------------------------------------

    private volatile int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;
    private volatile int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;
    private final PooledObjectFactory<T> factory;


    // --- internal attributes -------------------------------------------------

    /** 创建完后的池对象都会保存在这里 */
    private final Map<IdentityWrapper<T>, PooledObject<T>> allObjects = new ConcurrentHashMap<>();
    /** 调用{@link #create()}方法时，该计数器+1，无论是否创建成功 */
    private final AtomicLong createCount = new AtomicLong(0);
    /** 当向对象池添加一个对象实例前，该计数器+1，当创建完毕并放入池中后，该计数器-1 */
    private long makeObjectCount = 0;
    /** 创建对象时的锁 */
    private final Object makeObjectCountLock = new Object();
    /** 表示当前池子中空闲的对象 */
    private final LinkedBlockingDeque<PooledObject<T>> idleObjects;

    /** JMX specific attributes */
    private static final String ONAME_BASE = "org.apache.commons.pool2:type=GenericObjectPool,name=";

    /** 用于abandoned对象跟踪的其他配置属性 */
    private volatile AbandonedConfig abandonedConfig = null;


    /**
     * Creates a new <code>GenericObjectPool</code> using defaults from
     * {@link GenericObjectPoolConfig}.
     *
     * @param factory The object factory to be used to create object instances
     *                used by this pool
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory) {
        this(factory, new GenericObjectPoolConfig<T>());
    }
    /**
     * Creates a new <code>GenericObjectPool</code> using a specific
     * configuration.
     *
     * @param factory The object factory to be used to create object instances
     *                used by this pool
     * @param config  The configuration to use for this pool instance. The
     *                configuration is used by value. Subsequent changes to
     *                the configuration object will not be reflected in the
     *                pool.
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory, final GenericObjectPoolConfig<T> config) {

        super(config, ONAME_BASE, config.getJmxNamePrefix());

        if (factory == null) {
            jmxUnregister(); // tidy up
            throw new IllegalArgumentException("factory may not be null");
        }
        this.factory = factory;

        idleObjects = new LinkedBlockingDeque<>(config.getFairness());

        setConfig(config);
    }
    /**
     * Creates a new <code>GenericObjectPool</code> that tracks and destroys
     * objects that are checked out, but never returned to the pool.
     *
     * @param factory         The object factory to be used to create object instances
     *                        used by this pool
     * @param config          The base pool configuration to use for this pool instance.
     *                        The configuration is used by value. Subsequent changes to
     *                        the configuration object will not be reflected in the
     *                        pool.
     * @param abandonedConfig Configuration for abandoned object identification
     *                        and removal.  The configuration is used by value.
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory, final GenericObjectPoolConfig<T> config, final AbandonedConfig abandonedConfig) {
        this(factory, config);
        setAbandonedConfig(abandonedConfig);
    }


    // ObjectPool接口

    @Override
    public void addObject() throws Exception {
        // 确保池子没有关闭
        assertOpen();
        if (factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }

        final PooledObject<T> p = create();
        addIdleObject(p);
    }

    @Override
    public T borrowObject() throws Exception {
        return borrowObject(getMaxWaitMillis());
    }

    /**
     * 从池子里借用一个对象：
     *
     * 1、当池子中空闲的对象较少，而被借出去的对象较多时，会根据配置，看看是否要移除那些被借出去很久都没有归还或者一直在池子里很久都没有再被借出去的对象；
     * 2、当对象池耗尽时，根据 blockWhenExhausted 配置，看看是否要进行阻塞等待，如果配置不进行阻塞等待，则直接抛异常；
     * 3、对象借出去前，会对对象进行初始化操作
     * 4、对象借出去前，会进行检查，检查通过才允许被借出去，否则销毁对象
     * 5、从池中借用对象后更新统计信息
     *
     * @param borrowMaxWaitMillis   借用对象时的最长等待时间
     * @return object instance from the pool
     * @throws NoSuchElementException if an instance cannot be returned
     * @throws Exception              if an object instance cannot be returned due to an error
     */
    public T borrowObject(final long borrowMaxWaitMillis) throws Exception {
        // 确保池子没有关闭
        assertOpen();

        // 当池子中空闲的对象较少，而被借出去的对象较多时，会根据配置，看看是否要移除那些被借出去很久都没有归还或者一直在池子里很久都没有再被借出去的对象
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null
                && ac.getRemoveAbandonedOnBorrow()
                && (getNumIdle() < 2)
                && (getNumActive() > getMaxTotal() - 3)) {
            // 移除那些被借出去很久都没有归还或者一直在池子里很久都没有再被借出去的对象
            removeAbandoned(ac);
        }

        PooledObject<T> p = null;

        // 当对象池耗尽时（即达到“活动”对象的最大数量），调用roweObject()方法是否阻塞
        final boolean blockWhenExhausted = getBlockWhenExhausted();

        // 当对象池中对象不够用时，需要调用create()方法创建一个对象，然后再将该对象借出去，该值用于标记该场景下对象是否创建成功
        boolean create;
        final long waitTime = System.currentTimeMillis();

        while (p == null) {
            create = false;

            // 对象池中没有对象时，创建一个对象
            p = idleObjects.pollFirst();
            if (p == null) {
                p = create();
                if (p != null) {
                    create = true;
                }
            }

            // 当对象池耗尽时，根据 blockWhenExhausted 配置，看看是否要进行阻塞等待，如果配置不进行阻塞等待，则直接抛异常
            if (blockWhenExhausted) {

                // p == null 说明创建失败了（可能达到了池子的最大数量），则此时等待从池子获取对象
                if (p == null) {
                    if (borrowMaxWaitMillis < 0) {
                        p = idleObjects.takeFirst();
                    } else {
                        p = idleObjects.pollFirst(borrowMaxWaitMillis, TimeUnit.MILLISECONDS);
                    }
                }

                // 走到这里还获取不到对象，则抛异常，说明池子的对象数量不够用了
                if (p == null) {
                    throw new NoSuchElementException("Timeout waiting for idle object");
                }

            } else {
                if (p == null) {
                    throw new NoSuchElementException("Pool exhausted");
                }
            }

            // allocate()为true时，表示对象可以借出，并被标记为借出状态了，如果false表示对象不是空闲状态不允许被借出去
            if (!p.allocate()) {
                p = null;
            }


            if (p != null) {
                try {
                    // 对象借出去前对对象进行初始化操作
                    factory.activateObject(p);
                } catch (final Exception e) {
                    try {
                        destroy(p);
                    } catch (final Exception e1) {
                        // Ignore - activation failure is more important
                    }

                    p = null;
                    if (create) {
                        final NoSuchElementException nsee = new NoSuchElementException("Unable to activate object");
                        nsee.initCause(e);
                        throw nsee;
                    }
                }

                // 对象借出去前进行检查，检查通过才允许被借出去
                if (p != null && getTestOnBorrow()) {
                    boolean validate = false;
                    Throwable validationThrowable = null;
                    try {
                        validate = factory.validateObject(p);
                    } catch (final Throwable t) {
                        PoolUtils.checkRethrow(t);
                        validationThrowable = t;
                    }

                    // 如果检验不通过，则销毁对象，并报错
                    if (!validate) {
                        try {
                            destroy(p);
                            destroyedByBorrowValidationCount.incrementAndGet();
                        } catch (final Exception e) {
                            // Ignore - validation failure is more important
                        }

                        p = null;
                        if (create) {
                            final NoSuchElementException nsee = new NoSuchElementException("Unable to validate object");
                            nsee.initCause(validationThrowable);
                            throw nsee;
                        }
                    }
                }
            }
        }

        // 从池中借用对象后更新统计信息
        updateStatsBorrow(p, System.currentTimeMillis() - waitTime);

        return p.getObject();
    }

    /**
     * 归还对象到池子
     *
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     */
    @Override
    public void returnObject(final T obj) {

        final PooledObject<T> p = allObjects.get(new IdentityWrapper<>(obj));
        if (p == null) {
            if (!isAbandonedConfig()) {
                throw new IllegalStateException("Returned object not currently part of this pool");
            }
            // 对应已经在abandoned区域，或者被移除了
            return;
        }

        // 将对象标记为归还状态
        markReturningState(p);

        // 获取上一次借出去到现在的间隔时间（以毫秒为单位）
        final long activeTime = p.getActiveTimeMillis();

        // 在对象归还前进行检查测试，检查通过才能放回池子里
        if (getTestOnReturn() && !factory.validateObject(p)) {
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }

            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }

            updateStatsReturn(activeTime);
            return;
        }

        try {
            factory.passivateObject(p);
        } catch (final Exception e1) {
            swallowException(e1);
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }

            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }

            updateStatsReturn(activeTime);
            return;
        }

        // 将对象标记为空闲状态
        if (!p.deallocate()) {
            throw new IllegalStateException("Object has already been returned to this pool or is invalid");
        }

        // 获取最大空闲对象个数，如果当前>=最大限制，则不进行归还，直接销毁
        final int maxIdleSave = getMaxIdle();
        if (isClosed() || maxIdleSave > -1 && maxIdleSave <= idleObjects.size()) {
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
        } else {
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
            if (isClosed()) {
                // Pool closed while object was being added to idle objects.
                // Make sure the returned object is destroyed rather than left
                // in the idle object pool (which would effectively be a leak)
                clear();
            }
        }
        updateStatsReturn(activeTime);
    }

    /**
     * 使池中的该对象实例无效
     *
     * @throws Exception             if an exception occurs destroying the
     *                               object
     * @throws IllegalStateException if obj does not belong to this pool
     */
    @Override
    public void invalidateObject(final T obj) throws Exception {
        final PooledObject<T> p = allObjects.get(new IdentityWrapper<>(obj));
        if (p == null) {
            if (isAbandonedConfig()) {
                return;
            }
            throw new IllegalStateException("Invalidated object not currently part of this pool");
        }

        // 从池子移除并销毁对象
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                destroy(p);
            }
        }

        // 确保池中存在1个空闲实例
        ensureIdle(1, false);
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured
     * {@link PooledObjectFactory#destroyObject(PooledObject)} method on each
     * idle instance.
     * <p>
     * Implementation notes:
     * </p>
     * <ul>
     * <li>This method does not destroy or effect in any way instances that are
     * checked out of the pool when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being returned to the
     * idle instance pool, even during its execution. Additional instances may
     * be returned while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed
     * but notified via a {@link SwallowedExceptionListener}.</li>
     * </ul>
     */
    @Override
    public void clear() {
        PooledObject<T> p = idleObjects.poll();

        while (p != null) {
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }
            p = idleObjects.poll();
        }
    }

    /**
     * 创建对象实例，并包装为PooledObject，如果已经有{@link #getMaxTotal()}对象了或正在创建中对象可以达到最大值，则此方法返回null。
     *
     * @return The new wrapped pooled object
     * @throws Exception if the object factory's {@code makeObject} fails
     */
    private PooledObject<T> create() throws Exception {
        // 获取对象池的最大数量限制
        int localMaxTotal = getMaxTotal();
        if (localMaxTotal < 0) {
            localMaxTotal = Integer.MAX_VALUE;
        }

        final long localStartTimeMillis = System.currentTimeMillis();
        // 当对象池资源被用尽后，借用者的最大等待时间（单位为毫秒），默认值为-1：表示永远不超时，一直等待
        final long localMaxWaitTimeMillis = Math.max(getMaxWaitMillis(), 0);

        // create的作用如下：
        // - TRUE:  让工厂创建一个对象
        // - FALSE: 返回null
        // - null:  一直轮询，知道确定是否要创建一个对象
        Boolean create = null;
        while (create == null) {

            synchronized (makeObjectCountLock) {
                final long newCreateCount = createCount.incrementAndGet();

                // 该池当前已达到容量极限，或者正在制造足够的新对象以使其达到极限。
                if (newCreateCount > localMaxTotal) {
                    createCount.decrementAndGet();
                    if (makeObjectCount == 0) {
                        // 没有进行中的makeObject()调用，因此池已满。不要尝试创建新对象。返回并等待对象返回
                        create = Boolean.FALSE;
                    } else {
                        // 当前makeObject()方法正在被调用，这些调用可能会使池达到最大容量，这些调用也可能会失败，因此请等待它们完成，然后重新测试池是否达到容量极限
                        // wait(long)方法会一直阻塞当前线程，当对象创建完后会通过notifyAll()方法唤醒当前线程
                        makeObjectCountLock.wait(localMaxWaitTimeMillis);
                    }
                }
                // 池未满。创建一个新对象
                else {
                    makeObjectCount++;
                    create = Boolean.TRUE;
                }

            }

            // 如果设置了maxWaitTimeMillis，则当前线程等待时间是否超过了maxWaitTimeMillis，如果超过了该时间，则不再创建对象了
            if (create == null
                    && (localMaxWaitTimeMillis > 0
                    && System.currentTimeMillis() - localStartTimeMillis >= localMaxWaitTimeMillis)) {
                create = Boolean.FALSE;
            }
        }

        // create为false的时候返回空
        if (!create.booleanValue()) {
            return null;
        }


        // 以下逻辑开始创建一个对象，并添加到对象池

        final PooledObject<T> p;
        try {
            p = factory.makeObject();

            // 根据 BaseObjectPoolConfig#testOnCreate 配置，判断是否要检查创建的对象是否有效，如果无效则返回null
            if (getTestOnCreate() && !factory.validateObject(p)) {
                createCount.decrementAndGet();
                return null;
            }

        } catch (final Throwable e) {
            createCount.decrementAndGet();
            throw e;
        } finally {
            synchronized (makeObjectCountLock) {
                makeObjectCount--;
                makeObjectCountLock.notifyAll();
            }
        }

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getLogAbandoned()) {
            p.setLogAbandoned(true);
            p.setRequireFullStackTrace(ac.getRequireFullStackTrace());
        }

        // 创建成功后，计数器+1，并将对象放到池子里
        createdCount.incrementAndGet();
        allObjects.put(new IdentityWrapper<>(p.getObject()), p);
        return p;
    }

    /**
     * Adds the provided wrapped pooled object to the set of idle objects for
     * this pool. The object must already be part of the pool.  If {@code p}
     * is null, this is a no-op (no exception, but no impact on the pool).
     *
     * @param p The object to make idle
     * @throws Exception If the factory fails to passivate the object
     */
    private void addIdleObject(final PooledObject<T> p) throws Exception {
        if (p != null) {
            factory.passivateObject(p);
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
    }

    /**
     * 是否为移除废弃对象
     *
     * @return true if this pool is configured to detect and remove
     * abandoned objects
     */
    @Override
    public boolean isAbandonedConfig() {
        return abandonedConfig != null;
    }

    /**
     * Gets whether this pool identifies and logs any abandoned objects.
     *
     * @return {@code true} if abandoned object removal is configured for this
     * pool and removal events are to be logged otherwise {@code false}
     * @see AbandonedConfig#getLogAbandoned()
     */
    @Override
    public boolean getLogAbandoned() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getLogAbandoned();
    }

    /**
     * Gets whether a check is made for abandoned objects when an object is borrowed
     * from this pool.
     *
     * @return {@code true} if abandoned object removal is configured to be
     * activated by borrowObject otherwise {@code false}
     * @see AbandonedConfig#getRemoveAbandonedOnBorrow()
     */
    @Override
    public boolean getRemoveAbandonedOnBorrow() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnBorrow();
    }

    /**
     * Gets whether a check is made for abandoned objects when the evictor runs.
     *
     * @return {@code true} if abandoned object removal is configured to be
     * activated when the evictor runs otherwise {@code false}
     * @see AbandonedConfig#getRemoveAbandonedOnMaintenance()
     */
    @Override
    public boolean getRemoveAbandonedOnMaintenance() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnMaintenance();
    }

    /**
     * Obtains the timeout before which an object will be considered to be
     * abandoned by this pool.
     *
     * @return The abandoned object timeout in seconds if abandoned object
     * removal is configured for this pool; Integer.MAX_VALUE otherwise.
     * @see AbandonedConfig#getRemoveAbandonedTimeout()
     */
    @Override
    public int getRemoveAbandonedTimeout() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null ? ac.getRemoveAbandonedTimeout() : Integer.MAX_VALUE;
    }


    /**
     * Sets the base pool configuration.
     *
     * @param conf the new configuration to use. This is used by value.
     * @see GenericObjectPoolConfig
     */
    public void setConfig(final GenericObjectPoolConfig<T> conf) {
        super.setConfig(conf);
        setMaxIdle(conf.getMaxIdle());
        setMinIdle(conf.getMinIdle());
        setMaxTotal(conf.getMaxTotal());
    }

    /**
     * Sets the abandoned object removal configuration.
     *
     * @param abandonedConfig the new configuration to use. This is used by value.
     * @see AbandonedConfig
     */
    public void setAbandonedConfig(final AbandonedConfig abandonedConfig) {
        if (abandonedConfig == null) {
            this.abandonedConfig = null;
        } else {
            this.abandonedConfig = new AbandonedConfig();
            this.abandonedConfig.setLogAbandoned(abandonedConfig.getLogAbandoned());
            this.abandonedConfig.setLogWriter(abandonedConfig.getLogWriter());
            this.abandonedConfig.setRemoveAbandonedOnBorrow(abandonedConfig.getRemoveAbandonedOnBorrow());
            this.abandonedConfig.setRemoveAbandonedOnMaintenance(abandonedConfig.getRemoveAbandonedOnMaintenance());
            this.abandonedConfig.setRemoveAbandonedTimeout(abandonedConfig.getRemoveAbandonedTimeout());
            this.abandonedConfig.setUseUsageTracking(abandonedConfig.getUseUsageTracking());
            this.abandonedConfig.setRequireFullStackTrace(abandonedConfig.getRequireFullStackTrace());
        }
    }

    /**
     * 获得被借出对象数量，如果该池处于非活动状态，则为-1
     *
     * @return
     */
    @Override
    public int getNumActive() {
        return allObjects.size() - idleObjects.size();
    }

    /**
     * 当前在该池中空闲的实例数，如果该池处于非活动状态，则为-1
     *
     * @return
     */
    @Override
    public int getNumIdle() {
        return idleObjects.size();
    }

    /**
     * Closes the pool. Once the pool is closed, {@link #borrowObject()} will
     * fail with IllegalStateException, but {@link #returnObject(Object)} and
     * {@link #invalidateObject(Object)} will continue to work, with returned
     * objects destroyed on return.
     * <p>
     * Destroys idle instances in the pool by invoking {@link #clear()}.
     * </p>
     */
    @Override
    public void close() {
        if (isClosed()) {
            return;
        }

        synchronized (closeLock) {
            if (isClosed()) {
                return;
            }

            // Stop the evictor before the pool is closed since evict() calls
            // assertOpen()
            stopEvictor();

            closed = true;
            // This clear removes any idle objects
            clear();

            jmxUnregister();

            // Release any threads that were waiting for an object
            idleObjects.interuptTakeWaiters();
        }
    }

    /**
     * 连续激活此方法将按顺序检查对象，以从最旧到最年轻的顺序循环遍历对象
     */
    @Override
    public void evict() throws Exception {
        assertOpen();

        if (idleObjects.size() > 0) {

            // 表示当前要做驱逐测试的对象
            PooledObject<T> underTest = null;
            final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

            synchronized (evictionLock) {
                // 创建一个驱逐配置
                final EvictionConfig evictionConfig = new EvictionConfig(
                        getMinEvictableIdleTimeMillis(),
                        getSoftMinEvictableIdleTimeMillis(),
                        getMinIdle());

                // 表示是否要对池子里的空闲对象进行校验
                final boolean testWhileIdle = getTestWhileIdle();

                for (int i = 0, m = getNumTests(); i < m; i++) {
                    if (evictionIterator == null || !evictionIterator.hasNext()) {
                        evictionIterator = new EvictionIterator(idleObjects);
                    }

                    if (!evictionIterator.hasNext()) {
                        return;
                    }

                    // 获取要做驱逐测试的对象
                    try {
                        underTest = evictionIterator.next();
                    } catch (final NoSuchElementException nsee) {
                        // 在另一个线程中借用了对象。不要将此作为驱逐测试，因此减少i
                        i--;
                        evictionIterator = null;
                        continue;
                    }

                    // 将对象标记为 EVICTION 状态，表示当前坐在做驱逐测试
                    if (!underTest.startEvictionTest()) {
                        // 在另一个线程中借用了对象。不要将此作为驱逐测试，因此减少i
                        i--;
                        continue;
                    }

                    // 用户提供的驱逐策略可能引发各种疯狂的异常。
                    // 防止此类异常杀死驱逐线程。
                    boolean evict;
                    try {
                        evict = evictionPolicy.evict(evictionConfig, underTest, idleObjects.size());
                    } catch (final Throwable t) {
                        // 由于SwallowedExceptionListener使用Exception而不是Throwable，因此略为复杂
                        PoolUtils.checkRethrow(t);
                        swallowException(new Exception(t));
                        // 不要在错误情况下驱逐
                        evict = false;
                    }

                    // 如果驱逐测试通过，则销毁对象
                    if (evict) {
                        destroy(underTest);
                        destroyedByEvictorCount.incrementAndGet();
                    } else {

                        if (testWhileIdle) {

                            // 定时初始化对象，如果初始化失败，则销毁对象
                            boolean active = false;
                            try {
                                factory.activateObject(underTest);
                                active = true;
                            } catch (final Exception e) {
                                destroy(underTest);
                                destroyedByEvictorCount.incrementAndGet();
                            }

                            // 如果初始化成功，在检查下是否真的初始化成功，如果检查不通过，也销毁对象，否则在进行反初始化，让对象继续呆在池子里
                            if (active) {
                                if (!factory.validateObject(underTest)) {
                                    destroy(underTest);
                                    destroyedByEvictorCount.incrementAndGet();
                                } else {
                                    try {
                                        factory.passivateObject(underTest);
                                    } catch (final Exception e) {
                                        destroy(underTest);
                                        destroyedByEvictorCount.incrementAndGet();
                                    }
                                }
                            }
                        }

                        // 通知对象驱逐测试已结束
                        if (!underTest.endEvictionTest(idleObjects)) {
                            // TODO - May need to add code here once additional
                            // states are used
                        }
                    }
                }
            }
        }

        // 通过Abandoned配置，看看是否要对驱逐后那些废弃进行删除，如果需要则执行删除操作
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnMaintenance()) {
            // 移除那些被借出去很久都没有归还或者一直在池子里很久都没有再被借出去的对象，则会将该对象标记为废弃对象
            removeAbandoned(ac);
        }
    }

    /**
     * Tries to ensure that {@link #getMinIdle()} idle instances are available
     * in the pool.
     *
     * @throws Exception If the associated factory throws an exception
     * @since 2.4
     */
    public void preparePool() throws Exception {
        if (getMinIdle() < 1) {
            return;
        }
        ensureMinIdle();
    }

    @Override
    void ensureMinIdle() throws Exception {
        ensureIdle(getMinIdle(), true);
    }

    @Override
    public void use(final T pooledObject) {
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getUseUsageTracking()) {
            final PooledObject<T> wrapper = allObjects.get(new IdentityWrapper<>(pooledObject));
            wrapper.use();
        }
    }

    /**
     * Returns an estimate of the number of threads currently blocked waiting for
     * an object from the pool. This is intended for monitoring only, not for
     * synchronization control.
     *
     * @return The estimate of the number of threads currently blocked waiting
     * for an object from the pool
     */
    @Override
    public int getNumWaiters() {
        if (getBlockWhenExhausted()) {
            return idleObjects.getTakeQueueLength();
        }
        return 0;
    }

    /**
     * Returns the type - including the specific type rather than the generic -
     * of the factory.
     *
     * @return A string representation of the factory type
     */
    @Override
    public String getFactoryType() {
        // Not thread safe. Accept that there may be multiple evaluations.
        if (factoryType == null) {
            final StringBuilder result = new StringBuilder();
            result.append(factory.getClass().getName());
            result.append('<');
            final Class<?> pooledObjectType =
                    PoolImplUtils.getFactoryType(factory.getClass());
            result.append(pooledObjectType.getName());
            result.append('>');
            factoryType = result.toString();
        }
        return factoryType;
    }

    /**
     * Provides information on all the objects in the pool, both idle (waiting
     * to be borrowed) and active (currently borrowed).
     * <p>
     * Note: This is named listAllObjects so it is presented as an operation via
     * JMX. That means it won't be invoked unless the explicitly requested
     * whereas all attributes will be automatically requested when viewing the
     * attributes for an object in a tool like JConsole.
     * </p>
     *
     * @return Information grouped on all the objects in the pool
     */
    @Override
    public Set<DefaultPooledObjectInfo> listAllObjects() {
        final Set<DefaultPooledObjectInfo> result =
                new HashSet<>(allObjects.size());
        for (final PooledObject<T> p : allObjects.values()) {
            result.add(new DefaultPooledObjectInfo(p));
        }
        return result;
    }

    @Override
    public int getMaxIdle() {
        return maxIdle;
    }
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }
    @Override
    public int getMinIdle() {
        final int maxIdleSave = getMaxIdle();
        if (this.minIdle > maxIdleSave) {
            return maxIdleSave;
        }
        return minIdle;
    }
    public PooledObjectFactory<T> getFactory() {
        return factory;
    }





    /**
     * 销毁对象
     *
     * @param toDestroy The wrapped pooled object to destroy
     * @throws Exception If the factory fails to destroy the pooled object
     *                   cleanly
     */
    private void destroy(final PooledObject<T> toDestroy) throws Exception {
        toDestroy.invalidate();
        // 从空闲对象队列中移除
        idleObjects.remove(toDestroy);
        // 从对象池中移除
        allObjects.remove(new IdentityWrapper<>(toDestroy.getObject()));

        // 工厂销毁对象
        try {
            factory.destroyObject(toDestroy);
        } finally {
            destroyedCount.incrementAndGet();
            createCount.decrementAndGet();
        }
    }



    /**
     * 尝试确保池中存在{@code idleCount}个空闲实例。
     *
     * Creates and adds idle instances until either {@link #getNumIdle()} reaches {@code idleCount}
     * or the total number of objects (idle, checked out, or being created) reaches
     * {@link #getMaxTotal()}. If {@code always} is false, no instances are created unless
     * there are threads waiting to check out instances from the pool.
     * </p>
     *
     * @param idleCount the number of idle instances desired
     * @param always    true means create instances even if the pool has no threads waiting
     * @throws Exception if the factory's makeObject throws
     */
    private void ensureIdle(final int idleCount, final boolean always) throws Exception {
        if (idleCount < 1 || isClosed() || (!always && !idleObjects.hasTakeWaiters())) {
            return;
        }

        while (idleObjects.size() < idleCount) {
            final PooledObject<T> p = create();
            if (p == null) {
                // Can't create objects, no reason to think another call to
                // create will work. Give up.
                break;
            }
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
        if (isClosed()) {
            // Pool closed while object was being added to idle objects.
            // Make sure the returned object is destroyed rather than left
            // in the idle object pool (which would effectively be a leak)
            clear();
        }
    }

    /**
     * 返回池子中空闲对象的数量，以便逐出器遍历测试
     *
     * @return The number of objects to test for validity
     */
    private int getNumTests() {

        final int numTestsPerEvictionRun = getNumTestsPerEvictionRun();
        if (numTestsPerEvictionRun >= 0) {
            return Math.min(numTestsPerEvictionRun, idleObjects.size());
        }
        return (int) (Math.ceil(idleObjects.size() / Math.abs((double) numTestsPerEvictionRun)));
    }

    /**
     * 移除那些被借出去很久都没有归还或者一直在池子里很久都没有再被借出去的对象，则会将该对象标记为废弃对象；
     * 该方法一个是在{@link #borrowObject(long)}方法出被调用，另一处是驱逐器{@link #evict()}
     *
     * @param ac 用于识别废弃对象的配置
     */
    private void removeAbandoned(final AbandonedConfig ac) {
        // Generate a list of abandoned objects to remove
        final long now = System.currentTimeMillis();
        final long timeout = now - (ac.getRemoveAbandonedTimeout() * 1000L);

        final ArrayList<PooledObject<T>> remove = new ArrayList<>();
        final Iterator<PooledObject<T>> it = allObjects.values().iterator();
        while (it.hasNext()) {
            final PooledObject<T> pooledObject = it.next();
            synchronized (pooledObject) {
                // 这里的意思是：被借出去的对象很久都没有归还或者一直在池子里很久都没有再被借出去，则会将该对象标记为废弃对象
                if (pooledObject.getState() == PooledObjectState.ALLOCATED && pooledObject.getLastUsedTime() <= timeout) {
                    pooledObject.markAbandoned();
                    remove.add(pooledObject);
                }
            }
        }

        // 将要移除的对象标记为无效的，后续会被gc掉
        final Iterator<PooledObject<T>> itr = remove.iterator();
        while (itr.hasNext()) {
            final PooledObject<T> pooledObject = itr.next();
            if (ac.getLogAbandoned()) {
                pooledObject.printStackTrace(ac.getLogWriter());
            }

            try {
                invalidateObject(pooledObject.getObject());
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }



    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", factoryType=");
        builder.append(factoryType);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", allObjects=");
        builder.append(allObjects);
        builder.append(", createCount=");
        builder.append(createCount);
        builder.append(", idleObjects=");
        builder.append(idleObjects);
        builder.append(", abandonedConfig=");
        builder.append(abandonedConfig);
    }

}
