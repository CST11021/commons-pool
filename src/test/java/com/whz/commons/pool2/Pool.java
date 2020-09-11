package com.whz.commons.pool2;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.io.Closeable;
import java.util.NoSuchElementException;

public abstract class Pool<T> implements Closeable {

    /**
     * 使用的时候需要调用 borrowObject 获取一个对象，使用完以后需要调用 returnObject 归还对象，或者调用 invalidateObject 将这个对象标记为不可再用
     */
    protected GenericObjectPool<T> internalPool;

    public Pool(final GenericObjectPoolConfig poolConfig, PooledObjectFactory<T> factory) {
        if (this.internalPool != null) {
            try {
                closeInternalPool();
            } catch (Exception e) {
            }
        }

        this.internalPool = new GenericObjectPool<>(factory, poolConfig);
    }


    // 获取、归还和标记对象为不可用

    /**
     * 添加相应数量的对象到池里
     *
     * @param count
     */
    public void addObjects(int count) {
        try {
            for (int i = 0; i < count; i++) {
                this.internalPool.addObject();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error trying to add idle objects", e);
        }
    }
    /**
     * 从对象池获取一个对象
     *
     * @return
     */
    public T getResource() {
        try {
            // 从对象池获取一个对象
            return internalPool.borrowObject();
        } catch (NoSuchElementException nse) {
            // The exception was caused by an exhausted pool
            if (null == nse.getCause()) {
                throw new RuntimeException("Could not get a resource since the pool is exhausted", nse);
            }
            // Otherwise, the exception was caused by the implemented activateObject() or ValidateObject()
            throw new RuntimeException("Could not get a resource from the pool", nse);
        } catch (Exception e) {
            throw new RuntimeException("Could not get a resource from the pool", e);
        }
    }
    /**
     * 归还对象
     *
     * @param resource
     */
    protected void returnResourceObject(final T resource) {
        if (resource == null) {
            return;
        }

        try {
            internalPool.returnObject(resource);
        } catch (Exception e) {
            throw new RuntimeException("Could not return the resource to the pool", e);
        }
    }
    /**
     * 归还对象
     *
     * @param resource
     */
    protected void returnResource(final T resource) {
        if (resource != null) {
            returnResourceObject(resource);
        }
    }
    /**
     * 标记对象为不可用
     *
     * @param resource
     */
    protected void returnBrokenResource(final T resource) {
        if (resource != null) {
            returnBrokenResourceObject(resource);
        }
    }
    /**
     * 标记对象为不可用
     *
     * @param resource
     */
    protected void returnBrokenResourceObject(final T resource) {
        try {
            internalPool.invalidateObject(resource);
        } catch (Exception e) {
            throw new RuntimeException("Could not return the broken resource to the pool", e);
        }
    }


    // 返回对象池中的统计信息

    /**
     * 返回当前从该池借用的实例数
     *
     * @return 当前从该池借用的实例数，如果该池处于非活动状态，则为-1
     */
    public int getNumActive() {
        if (poolInactive()) {
            return -1;
        }

        return this.internalPool.getNumActive();
    }
    /**
     * 返回此池中当前空闲的实例数
     *
     * @return 当前在该池中空闲的实例数，如果该池处于非活动状态，则为-1
     */
    public int getNumIdle() {
        if (poolInactive()) {
            return -1;
        }

        return this.internalPool.getNumIdle();
    }
    /**
     * 返回当前等待该池中的资源被阻止的线程数的估计值
     *
     * @return 等待中的线程数，如果池处于非活动状态，则为-1
     */
    public int getNumWaiters() {
        if (poolInactive()) {
            return -1;
        }

        return this.internalPool.getNumWaiters();
    }
    /**
     * 返回线程从该池获取资源所花费的平均等待时间
     *
     * @return 如果池处于非活动状态，则平均等待时间（以毫秒为单位）为-1
     */
    public long getMeanBorrowWaitTimeMillis() {
        if (poolInactive()) {
            return -1;
        }

        return this.internalPool.getMeanBorrowWaitTimeMillis();
    }
    /**
     * 返回线程从该池获取资源所花费的最大等待时间（就是从池里获取对象，如果池里的对象不够以一直等待，如果等了一段时间是没有就不等了）
     *
     * @return 如果池处于非活动状态，则最大等待时间（以毫秒为单位）为-1
     */
    public long getMaxBorrowWaitTimeMillis() {
        if (poolInactive()) {
            return -1;
        }

        return this.internalPool.getMaxBorrowWaitTimeMillis();
    }
    /**
     * 判断对象池是否被关闭
     *
     * @return
     */
    private boolean poolInactive() {
        return this.internalPool == null || this.internalPool.isClosed();
    }


    @Override
    public void close() {
        destroy();
    }

    public boolean isClosed() {
        return this.internalPool.isClosed();
    }

    public void destroy() {
        closeInternalPool();
    }

    protected void closeInternalPool() {
        try {
            internalPool.close();
        } catch (Exception e) {
            throw new RuntimeException("Could not destroy the pool", e);
        }
    }

}