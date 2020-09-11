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

/**
 * A simple "struct" encapsulating the configuration for a
 * {@link GenericObjectPool}.
 *
 * <p>
 * This class is not thread-safe; it is only intended to be used to provide
 * attributes used when creating a pool.
 * </p>
 *
 * @param <T> Type of element pooled.
 * @since 2.0
 */
public class GenericObjectPoolConfig<T> extends BaseObjectPoolConfig<T> {

    public static final int DEFAULT_MAX_TOTAL = 8;
    public static final int DEFAULT_MAX_IDLE = 8;
    public static final int DEFAULT_MIN_IDLE = 0;


    /** 表示池中最多能够存在的对象，包括借出去后还未还回来的 */
    private int maxTotal = DEFAULT_MAX_TOTAL;
    /** 池中最多能够存在的空闲对象，即所有未被借出去的 */
    private int maxIdle = DEFAULT_MAX_IDLE;
    /**
     * 池中至少应该存在的空闲对象，假如，最小空闲数为1，最大总数为10，那么当一个线程从池中借对象，而池中只有一个空闲对象的时候，池会在创建一个对象，并借出一个对象，从而保证池中最小空闲数为1
     *
     * minIdle属性是指pool中最少有多少个空闲对象。该属性主要是在evict函数中调用。封装成EvictionConfig对象后，调用EvictionPolicy中的evict方法来判断是否需要回收当前测试的对象。
     *
     * evict函数是有一个定时任务定时去调用的。所以pool中一般会维持minIdle个闲置对象。所以如果当前pool中闲置对象的数量小于minIdle，pool并不会创建新的对象。minIdle只是用来回收对象的时候进行判断。
     *
     * */
    private int minIdle = DEFAULT_MIN_IDLE;


    // getter and setter ...

    public int getMaxTotal() {
        return maxTotal;
    }
    public void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }
    public int getMaxIdle() {
        return maxIdle;
    }
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }
    public int getMinIdle() {
        return minIdle;
    }
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    @SuppressWarnings("unchecked")
    @Override
    public GenericObjectPoolConfig<T> clone() {
        try {
            return (GenericObjectPoolConfig<T>) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(); // Can't happen
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", maxTotal=");
        builder.append(maxTotal);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
    }
}
