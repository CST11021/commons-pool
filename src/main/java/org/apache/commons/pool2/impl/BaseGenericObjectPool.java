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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.commons.pool2.BaseObject;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectState;
import org.apache.commons.pool2.SwallowedExceptionListener;

/**
 * 为{@link GenericObjectPool}和{@link GenericKeyedObjectPool}提供通用功能的基类.
 * 此类存在的主要原因是减少两个池实现之间的代码重复。
 *
 * @param <T> Type of element pooled in this pool. This class is intended to be thread-safe.
 * @since 2.0
 */
public abstract class BaseGenericObjectPool<T> extends BaseObject {

    // Constants

    /** 用于存储某些属性的历史数据的缓存的大小，以便可以计算滚动方式 */
    public static final int MEAN_TIMING_STATS_CACHE_SIZE = 100;

    private static final String EVICTION_POLICY_TYPE_NAME = EvictionPolicy.class.getName();

    /** 表示池子中的最大数量限制 */
    private volatile int maxTotal = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;
    /** 当对象池耗尽时（即达到“活动”对象的最大数量），调用roweObject()方法是否阻塞 */
    private volatile boolean blockWhenExhausted = BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED;
    /** 向对象池借用对象的最大等待时间 */
    private volatile long maxWaitMillis = BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS;
    /** 默认为true，即当池中有空闲可用的对象时，调用borrowObject方法会返回最近（后进）的实例, GenericObjectPool 提供了后进先出(LIFO)与先进先出(FIFO)两种行为模式的池。 */
    private volatile boolean lifo = BaseObjectPoolConfig.DEFAULT_LIFO;
    /** 当从池中获取资源或者将资源还回池中时 是否使用java.util.concurrent.locks.ReentrantLock.ReentrantLock 的公平锁机制,默认为false */
    private final boolean fairness;


    /** 表示向对象池添加对象前，是否需要进行检查，如果检查不通过，则不会将对象添加到池子里 */
    private volatile boolean testOnCreate = BaseObjectPoolConfig.DEFAULT_TEST_ON_CREATE;
    /** 表示从对象池借出对象前，是否需要进行检查，如果检查不通过，则不会将对象借出去 */
    private volatile boolean testOnBorrow = BaseObjectPoolConfig.DEFAULT_TEST_ON_BORROW;
    /** 表示从对象归还到池子前，是否需要进行检查 */
    private volatile boolean testOnReturn = BaseObjectPoolConfig.DEFAULT_TEST_ON_RETURN;
    /** 表示是否要对池子里的空闲对象进行校验 */
    private volatile boolean testWhileIdle = BaseObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;


    // 驱逐相关配置参数

    /** 驱逐器执行的间隔时间，>0才运行  */
    private volatile long timeBetweenEvictionRunsMillis = BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
    /** 驱逐器每次对池子中空闲对象做驱逐测试的个数 */
    private volatile int numTestsPerEvictionRun = BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    /** 池对象保持空闲状态的时长，超过了该值，会被进行驱逐测试 */
    private volatile long minEvictableIdleTimeMillis = BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    /** 软标准情况下，池对象保持空闲状态的时长，超过了该值，判断是否驱逐，软标准，自己设值一般比idleEvictTime小，结合minIdle使用 */
    private volatile long softMinEvictableIdleTimeMillis = BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    /** 用于进行驱逐测试的策略接口 */
    private volatile EvictionPolicy<T> evictionPolicy;
    private volatile long evictorShutdownTimeoutMillis = BaseObjectPoolConfig.DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS;


    // Internal (primarily state) attributes
    final Object closeLock = new Object();
    volatile boolean closed = false;
    /** 启动驱逐器的锁 */
    final Object evictionLock = new Object();
    private Evictor evictor = null;
    /** 驱逐器做驱逐测试时，用于迭代空闲对象的迭代器 */
    EvictionIterator evictionIterator = null;
    /**
     * Class loader for evictor thread to use since, in a JavaEE or similar
     * environment, the context class loader for the evictor thread may not have
     * visibility of the correct factory. See POOL-161. Uses a weak reference to
     * avoid potential memory leaks if the Pool is discarded rather than closed.
     */
    private final WeakReference<ClassLoader> factoryClassLoader;


    // Monitoring (primarily JMX) attributes
    private final ObjectName objectName;
    private final String creationStackTrace;
    private final AtomicLong borrowedCount = new AtomicLong(0);
    private final AtomicLong returnedCount = new AtomicLong(0);
    final AtomicLong createdCount = new AtomicLong(0);
    final AtomicLong destroyedCount = new AtomicLong(0);
    final AtomicLong destroyedByEvictorCount = new AtomicLong(0);
    final AtomicLong destroyedByBorrowValidationCount = new AtomicLong(0);
    private final StatsStore activeTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);
    private final StatsStore idleTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);
    private final StatsStore waitTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);
    private final AtomicLong maxBorrowWaitTimeMillis = new AtomicLong(0L);
    private volatile SwallowedExceptionListener swallowedExceptionListener = null;


    /**
     * Handles JMX registration (if required) and the initialization required for
     * monitoring.
     *
     * @param config        Pool configuration
     * @param jmxNameBase   The default base JMX name for the new pool unless
     *                      overridden by the config
     * @param jmxNamePrefix Prefix to be used for JMX name for the new pool
     */
    public BaseGenericObjectPool(final BaseObjectPoolConfig<T> config, final String jmxNameBase, final String jmxNamePrefix) {
        if (config.getJmxEnabled()) {
            this.objectName = jmxRegister(config, jmxNameBase, jmxNamePrefix);
        } else {
            this.objectName = null;
        }

        // Populate the creation stack trace
        this.creationStackTrace = getStackTrace(new Exception());

        // save the current TCCL (if any) to be used later by the evictor Thread
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            factoryClassLoader = null;
        } else {
            factoryClassLoader = new WeakReference<>(cl);
        }

        fairness = config.getFairness();
    }

    /**
     * Initializes the receiver with the given configuration.
     *
     * @param config Initialization source.
     */
    protected void setConfig(final BaseObjectPoolConfig<T> config) {
        setLifo(config.getLifo());
        setMaxWaitMillis(config.getMaxWaitMillis());
        setBlockWhenExhausted(config.getBlockWhenExhausted());
        setTestOnCreate(config.getTestOnCreate());
        setTestOnBorrow(config.getTestOnBorrow());
        setTestOnReturn(config.getTestOnReturn());
        setTestWhileIdle(config.getTestWhileIdle());
        setNumTestsPerEvictionRun(config.getNumTestsPerEvictionRun());
        setMinEvictableIdleTimeMillis(config.getMinEvictableIdleTimeMillis());
        setTimeBetweenEvictionRunsMillis(config.getTimeBetweenEvictionRunsMillis());
        setSoftMinEvictableIdleTimeMillis(config.getSoftMinEvictableIdleTimeMillis());
        final EvictionPolicy<T> policy = config.getEvictionPolicy();
        if (policy == null) {
            // Use the class name (pre-2.6.0 compatible)
            setEvictionPolicyClassName(config.getEvictionPolicyClassName());
        } else {
            // Otherwise, use the class (2.6.0 feature)
            setEvictionPolicy(policy);
        }
        setEvictorShutdownTimeoutMillis(config.getEvictorShutdownTimeoutMillis());
    }


    /**
     * Verifies that the pool is open.
     *
     * @throws IllegalStateException if the pool is closed.
     */
    final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    /**
     * 启动驱逐器，在延迟指定时间后开始对池子进行驱逐测试
     *
     * @param delay time in milliseconds before start and between eviction runs
     */
    final void startEvictor(final long delay) {
        synchronized (evictionLock) {
            EvictionTimer.cancel(evictor, evictorShutdownTimeoutMillis, TimeUnit.MILLISECONDS);
            evictor = null;
            evictionIterator = null;
            if (delay > 0) {
                evictor = new Evictor();
                EvictionTimer.schedule(evictor, delay, delay);
            }
        }
    }

    /**
     * 停止驱逐器
     */
    void stopEvictor() {
        startEvictor(-1L);
    }

    /**
     * 对池子中的空闲对象进行驱逐测试，如果测试通过，则丢弃到invalid空间
     *
     * @throws Exception when there is a problem evicting idle objects.
     */
    public abstract void evict() throws Exception;

    /**
     * 确保配置的最小空闲实例数在池中可用
     *
     * @throws Exception if an error occurs creating idle instances
     */
    abstract void ensureMinIdle() throws Exception;

    /**
     * Closes the pool, destroys the remaining idle objects and, if registered
     * in JMX, deregisters it.
     */
    public abstract void close();

    /**
     * Has this pool instance been closed.
     *
     * @return <code>true</code> when this pool has been closed.
     */
    public final boolean isClosed() {
        return closed;
    }






    // Monitoring (primarily JMX) related methods

    /**
     * Provides the name under which the pool has been registered with the
     * platform MBean server or <code>null</code> if the pool has not been
     * registered.
     *
     * @return the JMX name
     */
    public final ObjectName getJmxName() {
        return objectName;
    }

    /**
     * Provides the stack trace for the call that created this pool. JMX
     * registration may trigger a memory leak so it is important that pools are
     * deregistered when no longer used by calling the {@link #close()} method.
     * This method is provided to assist with identifying code that creates but
     * does not close it thereby creating a memory leak.
     *
     * @return pool creation stack trace
     */
    public final String getCreationStackTrace() {
        return creationStackTrace;
    }

    /**
     * The total number of objects successfully borrowed from this pool over the
     * lifetime of the pool.
     *
     * @return the borrowed object count
     */
    public final long getBorrowedCount() {
        return borrowedCount.get();
    }

    /**
     * The total number of objects returned to this pool over the lifetime of
     * the pool. This excludes attempts to return the same object multiple
     * times.
     *
     * @return the returned object count
     */
    public final long getReturnedCount() {
        return returnedCount.get();
    }

    /**
     * The total number of objects created for this pool over the lifetime of
     * the pool.
     *
     * @return the created object count
     */
    public final long getCreatedCount() {
        return createdCount.get();
    }

    /**
     * The total number of objects destroyed by this pool over the lifetime of
     * the pool.
     *
     * @return the destroyed object count
     */
    public final long getDestroyedCount() {
        return destroyedCount.get();
    }

    /**
     * The total number of objects destroyed by the evictor associated with this
     * pool over the lifetime of the pool.
     *
     * @return the evictor destroyed object count
     */
    public final long getDestroyedByEvictorCount() {
        return destroyedByEvictorCount.get();
    }

    /**
     * The total number of objects destroyed by this pool as a result of failing
     * validation during <code>borrowObject()</code> over the lifetime of the
     * pool.
     *
     * @return validation destroyed object count
     */
    public final long getDestroyedByBorrowValidationCount() {
        return destroyedByBorrowValidationCount.get();
    }

    /**
     * The mean time objects are active for based on the last {@link
     * #MEAN_TIMING_STATS_CACHE_SIZE} objects returned to the pool.
     *
     * @return mean time an object has been checked out from the pool among
     * recently returned objects
     */
    public final long getMeanActiveTimeMillis() {
        return activeTimes.getMean();
    }

    /**
     * The mean time objects are idle for based on the last {@link
     * #MEAN_TIMING_STATS_CACHE_SIZE} objects borrowed from the pool.
     *
     * @return mean time an object has been idle in the pool among recently
     * borrowed objects
     */
    public final long getMeanIdleTimeMillis() {
        return idleTimes.getMean();
    }

    /**
     * The mean time threads wait to borrow an object based on the last {@link
     * #MEAN_TIMING_STATS_CACHE_SIZE} objects borrowed from the pool.
     *
     * @return mean time in milliseconds that a recently served thread has had
     * to wait to borrow an object from the pool
     */
    public final long getMeanBorrowWaitTimeMillis() {
        return waitTimes.getMean();
    }

    /**
     * The maximum time a thread has waited to borrow objects from the pool.
     *
     * @return maximum wait time in milliseconds since the pool was created
     */
    public final long getMaxBorrowWaitTimeMillis() {
        return maxBorrowWaitTimeMillis.get();
    }

    /**
     * The number of instances currently idle in this pool.
     *
     * @return count of instances available for checkout from the pool
     */
    public abstract int getNumIdle();

    /**
     * The listener used (if any) to receive notifications of exceptions
     * unavoidably swallowed by the pool.
     *
     * @return The listener or <code>null</code> for no listener
     */
    public final SwallowedExceptionListener getSwallowedExceptionListener() {
        return swallowedExceptionListener;
    }
    /**
     * The listener used (if any) to receive notifications of exceptions
     * unavoidably swallowed by the pool.
     *
     * @param swallowedExceptionListener The listener or <code>null</code>
     *                                   for no listener
     */
    public final void setSwallowedExceptionListener(final SwallowedExceptionListener swallowedExceptionListener) {
        this.swallowedExceptionListener = swallowedExceptionListener;
    }
    /**
     * Swallows an exception and notifies the configured listener for swallowed
     * exceptions queue.
     *
     * @param swallowException exception to be swallowed
     */
    final void swallowException(final Exception swallowException) {
        final SwallowedExceptionListener listener = getSwallowedExceptionListener();

        if (listener == null) {
            return;
        }

        try {
            listener.onSwallowException(swallowException);
        } catch (final VirtualMachineError e) {
            throw e;
        } catch (final Throwable t) {
            // Ignore. Enjoy the irony.
        }
    }
    /**
     * 从池中借用对象后更新统计信息
     *
     * @param p        object borrowed from the pool
     * @param waitTime 借用过程花费的时间（以毫秒为单位）
     */
    final void updateStatsBorrow(final PooledObject<T> p, final long waitTime) {
        // 将对象被借出去的次数+1
        borrowedCount.incrementAndGet();
        // 获取该对象上次处于空闲状态距离现在的时间（以毫秒为单位），并放到队列中
        idleTimes.add(p.getIdleTimeMillis());
        // 添加用过程花费的时间
        waitTimes.add(waitTime);

        // lock-free optimistic-locking maximum
        long currentMax;
        do {
            currentMax = maxBorrowWaitTimeMillis.get();
            if (currentMax >= waitTime) {
                break;
            }
        } while (!maxBorrowWaitTimeMillis.compareAndSet(currentMax, waitTime));
    }
    /**
     * Updates statistics after an object is returned to the pool.
     *
     * @param activeTime the amount of time (in milliseconds) that the returning
     *                   object was checked out
     */
    final void updateStatsReturn(final long activeTime) {
        returnedCount.incrementAndGet();
        activeTimes.add(activeTime);
    }
    /**
     * 将对象标记为归还状态
     *
     * @param pooledObject instance to return to the keyed pool
     */
    protected void markReturningState(final PooledObject<T> pooledObject) {
        synchronized (pooledObject) {
            final PooledObjectState state = pooledObject.getState();
            if (state != PooledObjectState.ALLOCATED) {
                throw new IllegalStateException("Object has already been returned to this pool or is invalid");
            }

            // 将对象标记为归还状态
            pooledObject.markReturning();
        }
    }
    /**
     * Unregisters this pool's MBean.
     */
    final void jmxUnregister() {
        if (objectName != null) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(
                        objectName);
            } catch (final MBeanRegistrationException | InstanceNotFoundException e) {
                swallowException(e);
            }
        }
    }
    /**
     * Registers the pool with the platform MBean server.
     * The registered name will be
     * <code>jmxNameBase + jmxNamePrefix + i</code> where i is the least
     * integer greater than or equal to 1 such that the name is not already
     * registered. Swallows MBeanRegistrationException, NotCompliantMBeanException
     * returning null.
     *
     * @param config        Pool configuration
     * @param jmxNameBase   default base JMX name for this pool
     * @param jmxNamePrefix name prefix
     * @return registered ObjectName, null if registration fails
     */
    private ObjectName jmxRegister(final BaseObjectPoolConfig<T> config, final String jmxNameBase, String jmxNamePrefix) {
        ObjectName newObjectName = null;
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        int i = 1;
        boolean registered = false;
        String base = config.getJmxNameBase();
        if (base == null) {
            base = jmxNameBase;
        }
        while (!registered) {
            try {
                ObjectName objName;
                // Skip the numeric suffix for the first pool in case there is
                // only one so the names are cleaner.
                if (i == 1) {
                    objName = new ObjectName(base + jmxNamePrefix);
                } else {
                    objName = new ObjectName(base + jmxNamePrefix + i);
                }
                mbs.registerMBean(this, objName);
                newObjectName = objName;
                registered = true;
            } catch (final MalformedObjectNameException e) {
                if (BaseObjectPoolConfig.DEFAULT_JMX_NAME_PREFIX.equals(
                        jmxNamePrefix) && jmxNameBase.equals(base)) {
                    // Shouldn't happen. Skip registration if it does.
                    registered = true;
                } else {
                    // Must be an invalid name. Use the defaults instead.
                    jmxNamePrefix =
                            BaseObjectPoolConfig.DEFAULT_JMX_NAME_PREFIX;
                    base = jmxNameBase;
                }
            } catch (final InstanceAlreadyExistsException e) {
                // Increment the index and try again
                i++;
            } catch (final MBeanRegistrationException | NotCompliantMBeanException e) {
                // Shouldn't happen. Skip registration if it does.
                registered = true;
            }
        }
        return newObjectName;
    }
    /**
     * Gets the stack trace of an exception as a string.
     *
     * @param e exception to trace
     * @return exception stack trace as a string
     */
    private String getStackTrace(final Exception e) {
        // Need the exception in string form to prevent the retention of
        // references to classes in the stack trace that could trigger a memory
        // leak in a container environment.
        final Writer w = new StringWriter();
        final PrintWriter pw = new PrintWriter(w);
        e.printStackTrace(pw);
        return w.toString();
    }



    // getter and setter ...

    public final int getMaxTotal() {
        return maxTotal;
    }
    public final void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }
    public final boolean getBlockWhenExhausted() {
        return blockWhenExhausted;
    }
    public final void setBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }
    public final long getMaxWaitMillis() {
        return maxWaitMillis;
    }
    public final void setMaxWaitMillis(final long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }
    public final boolean getLifo() {
        return lifo;
    }
    public final void setLifo(final boolean lifo) {
        this.lifo = lifo;
    }
    public final boolean getFairness() {
        return fairness;
    }
    public final boolean getTestOnCreate() {
        return testOnCreate;
    }
    public final void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }
    public final boolean getTestOnBorrow() {
        return testOnBorrow;
    }
    public final void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }
    public final boolean getTestOnReturn() {
        return testOnReturn;
    }
    public final void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }
    public final boolean getTestWhileIdle() {
        return testWhileIdle;
    }
    public final void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }
    public final long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }
    public final void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        startEvictor(timeBetweenEvictionRunsMillis);
    }
    public final int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }
    public final void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }
    public final long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }
    public final void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }
    public final long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }
    public final void setSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }
    public EvictionPolicy<T> getEvictionPolicy() {
        return evictionPolicy;
    }
    public final String getEvictionPolicyClassName() {
        return evictionPolicy.getClass().getName();
    }
    public final void setEvictionPolicyClassName(final String evictionPolicyClassName, final ClassLoader classLoader) {
        // Getting epClass here and now best matches the caller's environment
        final Class<?> epClass = EvictionPolicy.class;
        final ClassLoader epClassLoader = epClass.getClassLoader();
        try {
            try {
                setEvictionPolicy(evictionPolicyClassName, classLoader);
            } catch (final ClassCastException | ClassNotFoundException e) {
                setEvictionPolicy(evictionPolicyClassName, epClassLoader);
            }
        } catch (final ClassCastException e) {
            throw new IllegalArgumentException("Class " + evictionPolicyClassName + " from class loaders [" +
                    classLoader + ", " + epClassLoader + "] do not implement " + EVICTION_POLICY_TYPE_NAME);
        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException |
                InvocationTargetException | NoSuchMethodException e) {
            final String exMessage = "Unable to create " + EVICTION_POLICY_TYPE_NAME + " instance of type " +
                    evictionPolicyClassName;
            throw new IllegalArgumentException(exMessage, e);
        }
    }
    public final void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        setEvictionPolicyClassName(evictionPolicyClassName, Thread.currentThread().getContextClassLoader());
    }
    public void setEvictionPolicy(final EvictionPolicy<T> evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }
    @SuppressWarnings("unchecked")
    private void setEvictionPolicy(final String className, final ClassLoader classLoader) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Class<?> clazz = Class.forName(className, true, classLoader);
        final Object policy = clazz.getConstructor().newInstance();
        this.evictionPolicy = (EvictionPolicy<T>) policy;
    }
    /**
     * Gets the timeout that will be used when waiting for the Evictor to
     * shutdown if this pool is closed and it is the only pool still using the
     * the value for the Evictor.
     *
     * @return The timeout in milliseconds that will be used while waiting for
     * the Evictor to shut down.
     */
    public final long getEvictorShutdownTimeoutMillis() {
        return evictorShutdownTimeoutMillis;
    }
    /**
     * Sets the timeout that will be used when waiting for the Evictor to
     * shutdown if this pool is closed and it is the only pool still using the
     * the value for the Evictor.
     *
     * @param evictorShutdownTimeoutMillis the timeout in milliseconds that
     *                                     will be used while waiting for the
     *                                     Evictor to shut down.
     */
    public final void setEvictorShutdownTimeoutMillis(final long evictorShutdownTimeoutMillis) {
        this.evictorShutdownTimeoutMillis = evictorShutdownTimeoutMillis;
    }



    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("maxTotal=");
        builder.append(maxTotal);
        builder.append(", blockWhenExhausted=");
        builder.append(blockWhenExhausted);
        builder.append(", maxWaitMillis=");
        builder.append(maxWaitMillis);
        builder.append(", lifo=");
        builder.append(lifo);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", testOnCreate=");
        builder.append(testOnCreate);
        builder.append(", testOnBorrow=");
        builder.append(testOnBorrow);
        builder.append(", testOnReturn=");
        builder.append(testOnReturn);
        builder.append(", testWhileIdle=");
        builder.append(testWhileIdle);
        builder.append(", timeBetweenEvictionRunsMillis=");
        builder.append(timeBetweenEvictionRunsMillis);
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", minEvictableIdleTimeMillis=");
        builder.append(minEvictableIdleTimeMillis);
        builder.append(", softMinEvictableIdleTimeMillis=");
        builder.append(softMinEvictableIdleTimeMillis);
        builder.append(", evictionPolicy=");
        builder.append(evictionPolicy);
        builder.append(", closeLock=");
        builder.append(closeLock);
        builder.append(", closed=");
        builder.append(closed);
        builder.append(", evictionLock=");
        builder.append(evictionLock);
        builder.append(", evictor=");
        builder.append(evictor);
        builder.append(", evictionIterator=");
        builder.append(evictionIterator);
        builder.append(", factoryClassLoader=");
        builder.append(factoryClassLoader);
        builder.append(", oname=");
        builder.append(objectName);
        builder.append(", creationStackTrace=");
        builder.append(creationStackTrace);
        builder.append(", borrowedCount=");
        builder.append(borrowedCount);
        builder.append(", returnedCount=");
        builder.append(returnedCount);
        builder.append(", createdCount=");
        builder.append(createdCount);
        builder.append(", destroyedCount=");
        builder.append(destroyedCount);
        builder.append(", destroyedByEvictorCount=");
        builder.append(destroyedByEvictorCount);
        builder.append(", destroyedByBorrowValidationCount=");
        builder.append(destroyedByBorrowValidationCount);
        builder.append(", activeTimes=");
        builder.append(activeTimes);
        builder.append(", idleTimes=");
        builder.append(idleTimes);
        builder.append(", waitTimes=");
        builder.append(waitTimes);
        builder.append(", maxBorrowWaitTimeMillis=");
        builder.append(maxBorrowWaitTimeMillis);
        builder.append(", swallowedExceptionListener=");
        builder.append(swallowedExceptionListener);
    }





    // 内部类

    /**
     * 驱逐器：对池子中的空闲对象进行驱逐测试，对于符合驱逐条件的对象，将会被对象池无情的驱逐出空闲空间，并丢弃到invalid空间。
     *
     * @see GenericKeyedObjectPool#setTimeBetweenEvictionRunsMillis
     */
    class Evictor implements Runnable {

        private ScheduledFuture<?> scheduledFuture;

        /**
         * Run pool maintenance.  Evict objects qualifying for eviction and then
         * ensure that the minimum number of idle instances are available.
         * Since the Timer that invokes Evictors is shared for all Pools but
         * pools may exist in different class loaders, the Evictor ensures that
         * any actions taken are under the class loader of the factory
         * associated with the pool.
         */
        @Override
        public void run() {
            final ClassLoader savedClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                if (factoryClassLoader != null) {
                    // Set the class loader for the factory
                    final ClassLoader cl = factoryClassLoader.get();
                    if (cl == null) {
                        // 该池已被取消引用，并且类加载器已添加GC。取消此计时器，以便也可以对池进行GC处理。
                        cancel();
                        return;
                    }
                    Thread.currentThread().setContextClassLoader(cl);
                }

                // Evict from the pool
                try {
                    evict();
                } catch (final Exception e) {
                    swallowException(e);
                } catch (final OutOfMemoryError oome) {
                    // 记录问题，但在错误可恢复的情况下让退出线程有机会继续操作
                    oome.printStackTrace(System.err);
                }

                // 驱逐完成后，要确保配置的最小空闲实例数在池中可用
                try {
                    ensureMinIdle();
                } catch (final Exception e) {
                    swallowException(e);
                }

            } finally {
                // Restore the previous CCL
                Thread.currentThread().setContextClassLoader(savedClassLoader);
            }
        }


        /**
         * Sets the scheduled future.
         *
         * @param scheduledFuture the scheduled future.
         */
        void setScheduledFuture(final ScheduledFuture<?> scheduledFuture) {
            this.scheduledFuture = scheduledFuture;
        }


        /**
         * Cancels the scheduled future.
         */
        void cancel() {
            scheduledFuture.cancel(false);
        }
    }

    /**
     * Maintains a cache of values for a single metric and reports
     * statistics on the cached values.
     */
    private class StatsStore {

        private final AtomicLong values[];
        private final int size;
        private int index;

        /**
         * Create a StatsStore with the given cache size.
         *
         * @param size number of values to maintain in the cache.
         */
        public StatsStore(final int size) {
            this.size = size;
            values = new AtomicLong[size];
            for (int i = 0; i < size; i++) {
                values[i] = new AtomicLong(-1);
            }
        }

        /**
         * Adds a value to the cache.  If the cache is full, one of the
         * existing values is replaced by the new value.
         *
         * @param value new value to add to the cache.
         */
        public synchronized void add(final long value) {
            values[index].set(value);
            index++;
            if (index == size) {
                index = 0;
            }
        }

        /**
         * Returns the mean of the cached values.
         *
         * @return the mean of the cache, truncated to long
         */
        public long getMean() {
            double result = 0;
            int counter = 0;
            for (int i = 0; i < size; i++) {
                final long value = values[i].get();
                if (value != -1) {
                    counter++;
                    result = result * ((counter - 1) / (double) counter) +
                            value / (double) counter;
                }
            }
            return (long) result;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("StatsStore [values=");
            builder.append(Arrays.toString(values));
            builder.append(", size=");
            builder.append(size);
            builder.append(", index=");
            builder.append(index);
            builder.append("]");
            return builder.toString();
        }
    }

    /**
     * 保存对空闲对象的引用，便于驱逐器遍历池子中的空闲对象
     */
    class EvictionIterator implements Iterator<PooledObject<T>> {

        private final Deque<PooledObject<T>> idleObjects;
        private final Iterator<PooledObject<T>> idleObjectIterator;

        /**
         * Create an EvictionIterator for the provided idle instance deque.
         *
         * @param idleObjects underlying deque
         */
        EvictionIterator(final Deque<PooledObject<T>> idleObjects) {
            this.idleObjects = idleObjects;

            if (getLifo()) {
                idleObjectIterator = idleObjects.descendingIterator();
            } else {
                idleObjectIterator = idleObjects.iterator();
            }
        }

        /**
         * Returns the idle object deque referenced by this iterator.
         *
         * @return the idle object deque
         */
        public Deque<PooledObject<T>> getIdleObjects() {
            return idleObjects;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return idleObjectIterator.hasNext();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PooledObject<T> next() {
            return idleObjectIterator.next();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            idleObjectIterator.remove();
        }

    }

    /**
     * 用于封装一个池对象，一次作为对象的唯一标识
     *
     * @param <T> type of objects in the pool
     */
    static class IdentityWrapper<T> {
        /**
         * Wrapped object
         */
        private final T instance;

        /**
         * Create a wrapper for an instance.
         *
         * @param instance object to wrap
         */
        public IdentityWrapper(final T instance) {
            this.instance = instance;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean equals(final Object other) {
            return other instanceof IdentityWrapper &&
                    ((IdentityWrapper) other).instance == instance;
        }

        /**
         * @return the wrapped object
         */
        public T getObject() {
            return instance;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("IdentityWrapper [instance=");
            builder.append(instance);
            builder.append("]");
            return builder.toString();
        }
    }



}
