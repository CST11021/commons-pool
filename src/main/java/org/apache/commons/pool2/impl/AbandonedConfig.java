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
import org.apache.commons.pool2.TrackedUse;
import org.apache.commons.pool2.UsageTracking;

import java.io.PrintWriter;

/**
 * 用于删除废弃对象的配置设置
 *
 * @since 2.0
 */
public class AbandonedConfig {

    /** 表示在调用{@link ObjectPool#borrowObject()}方法时，如果池子中空闲的对象较少，而被借出去的对象较多时，是否要移除那些被借出去很久都没有归还或者一直在池子里很久都没有再被借出去的对象 */
    private boolean removeAbandonedOnBorrow = false;
    /** 该时间表示那些被借走的，但是超过了很长时间没有被归还的对象或者放在池子里很长时间没有被借出的对象，该配置配合{@link #removeAbandonedOnBorrow}使用 */
    private int removeAbandonedTimeout = 300;
    /** 表示是否对驱逐器测试通过后那些废弃进行删除 */
    private boolean removeAbandonedOnMaintenance = false;

    /** 表示每次对象的方法调用，是否要创建调用的堆栈对象 */
    private boolean useUsageTracking = false;
    /**
     * logAbandoned为true时，useUsageTracking也为true时，那么回收被遗弃的对象时，就会打印该对象最后一次的调用堆栈信息了,
     * 如果useUsageTracking为true，即便是logAbandoned为false，那么每次对象的方法调用，一样还是会创建调用堆栈对象。只不过最终被回收时不会打印输出。
     */
    private boolean logAbandoned = false;
    /** 当logAbandoned为true时，该配置表示是否要记录完整的堆栈跟踪，如果是false，则可以使用更快的方法来记录仅包含类数据的堆栈跟踪。 */
    private boolean requireFullStackTrace = true;
    /** 这是一个日志输出器，用来定义调用堆栈日志的输出行为（输出到控制台、文件等）。默认输出到控制台。如果logAbandoned为false，就不会有输出行为。*/
    private PrintWriter logWriter = new PrintWriter(System.out);



    // getter and setter ...

    public boolean getRemoveAbandonedOnBorrow() {
        return this.removeAbandonedOnBorrow;
    }
    public void setRemoveAbandonedOnBorrow(final boolean removeAbandonedOnBorrow) {
        this.removeAbandonedOnBorrow = removeAbandonedOnBorrow;
    }
    /**
     * <p>Flag to remove abandoned objects if they exceed the
     * removeAbandonedTimeout when pool maintenance (the "evictor")
     * runs.</p>
     *
     * <p>The default value is false.</p>
     *
     * <p>If set to true, abandoned objects are removed by the pool
     * maintenance thread when it runs.  This setting has no effect
     * unless maintenance is enabled by setting
     * {@link GenericObjectPool#getTimeBetweenEvictionRunsMillis() timeBetweenEvictionRunsMillis}
     * to a positive number.</p>
     *
     * @return true if abandoned objects are to be removed by the evictor
     */
    public boolean getRemoveAbandonedOnMaintenance() {
        return this.removeAbandonedOnMaintenance;
    }
    /**
     * <p>Flag to remove abandoned objects if they exceed the
     * removeAbandonedTimeout when pool maintenance runs.</p>
     *
     * @param removeAbandonedOnMaintenance true means abandoned objects will be
     *                                     removed by pool maintenance
     * @see #getRemoveAbandonedOnMaintenance
     */
    public void setRemoveAbandonedOnMaintenance(final boolean removeAbandonedOnMaintenance) {
        this.removeAbandonedOnMaintenance = removeAbandonedOnMaintenance;
    }
    /**
     * <p>Timeout in seconds before an abandoned object can be removed.</p>
     *
     * <p>The time of most recent use of an object is the maximum (latest) of
     * {@link TrackedUse#getLastUsed()} (if this class of the object implements
     * TrackedUse) and the time when the object was borrowed from the pool.</p>
     *
     * <p>The default value is 300 seconds.</p>
     *
     * @return the abandoned object timeout in seconds
     */
    public int getRemoveAbandonedTimeout() {
        return this.removeAbandonedTimeout;
    }
    /**
     * <p>Sets the timeout in seconds before an abandoned object can be
     * removed</p>
     *
     * <p>Setting this property has no effect if
     * {@link #getRemoveAbandonedOnBorrow() removeAbandonedOnBorrow} and
     * {@link #getRemoveAbandonedOnMaintenance() removeAbandonedOnMaintenance}
     * are both false.</p>
     *
     * @param removeAbandonedTimeout new abandoned timeout in seconds
     * @see #getRemoveAbandonedTimeout()
     */
    public void setRemoveAbandonedTimeout(final int removeAbandonedTimeout) {
        this.removeAbandonedTimeout = removeAbandonedTimeout;
    }
    /**
     * Flag to log stack traces for application code which abandoned
     * an object.
     * <p>
     * Defaults to false.
     * Logging of abandoned objects adds overhead for every object created
     * because a stack trace has to be generated.
     *
     * @return boolean true if stack trace logging is turned on for abandoned
     * objects
     */
    public boolean getLogAbandoned() {
        return this.logAbandoned;
    }
    /**
     * Sets the flag to log stack traces for application code which abandoned
     * an object.
     *
     * @param logAbandoned true turns on abandoned stack trace logging
     * @see #getLogAbandoned()
     */
    public void setLogAbandoned(final boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }
    /**
     * Indicates if full stack traces are required when {@link #getLogAbandoned() logAbandoned}
     * is true. Defaults to true. Logging of abandoned objects requiring a full stack trace will
     * generate an entire stack trace to generate for every object created. If this is disabled,
     * a faster but less informative stack walking mechanism may be used if available.
     *
     * @return true if full stack traces are required for logging abandoned connections, or false
     * if abbreviated stack traces are acceptable
     * @see CallStack
     * @since 2.5
     */
    public boolean getRequireFullStackTrace() {
        return requireFullStackTrace;
    }
    /**
     * Sets the flag to require full stack traces for logging abandoned connections when enabled.
     *
     * @param requireFullStackTrace indicates whether or not full stack traces are required in
     *                              abandoned connection logs
     * @see CallStack
     * @see #getRequireFullStackTrace()
     * @since 2.5
     */
    public void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        this.requireFullStackTrace = requireFullStackTrace;
    }
    /**
     * Returns the log writer being used by this configuration to log
     * information on abandoned objects. If not set, a PrintWriter based on
     * System.out with the system default encoding is used.
     *
     * @return log writer in use
     */
    public PrintWriter getLogWriter() {
        return logWriter;
    }
    /**
     * Sets the log writer to be used by this configuration to log
     * information on abandoned objects.
     *
     * @param logWriter The new log writer
     */
    public void setLogWriter(final PrintWriter logWriter) {
        this.logWriter = logWriter;
    }
    /**
     * If the pool implements {@link UsageTracking}, should the pool record a
     * stack trace every time a method is called on a pooled object and retain
     * the most recent stack trace to aid debugging of abandoned objects?
     *
     * @return <code>true</code> if usage tracking is enabled
     */
    public boolean getUseUsageTracking() {
        return useUsageTracking;
    }
    /**
     * If the pool implements {@link UsageTracking}, configure whether the pool
     * should record a stack trace every time a method is called on a pooled
     * object and retain the most recent stack trace to aid debugging of
     * abandoned objects.
     *
     * @param useUsageTracking A value of <code>true</code> will enable
     *                         the recording of a stack trace on every use
     *                         of a pooled object
     */
    public void setUseUsageTracking(final boolean useUsageTracking) {
        this.useUsageTracking = useUsageTracking;
    }

    /**
     * @since 2.4.3
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AbandonedConfig [removeAbandonedOnBorrow=");
        builder.append(removeAbandonedOnBorrow);
        builder.append(", removeAbandonedOnMaintenance=");
        builder.append(removeAbandonedOnMaintenance);
        builder.append(", removeAbandonedTimeout=");
        builder.append(removeAbandonedTimeout);
        builder.append(", logAbandoned=");
        builder.append(logAbandoned);
        builder.append(", logWriter=");
        builder.append(logWriter);
        builder.append(", useUsageTracking=");
        builder.append(useUsageTracking);
        builder.append("]");
        return builder.toString();
    }
}
