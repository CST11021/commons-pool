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
import org.apache.commons.pool2.UsageTracking;

import java.io.PrintWriter;

/**
 * 获取和打印当前调用堆栈的策略。
 * 这主要用于{@linkplain UsageTracking}使用情况跟踪，以便不同的JVM和配置可以根据元数据需求使用更有效的策略来获取当前的调用堆栈。
 *
 * @see CallStackUtils
 * @since 2.4.3
 */
public interface CallStack {

    /**
     * 创建一个当前方法调用的堆栈
     */
    void fillInStackTrace();

    /**
     * 打印当前堆栈信息，并通过PrintWriter输出
     *
     * @param writer a PrintWriter to write the current stack trace to if available
     * @return true if a stack trace was available to print or false if nothing was printed
     */
    boolean printStackTrace(final PrintWriter writer);

    /**
     * 清除当前的堆栈跟踪快照。
     */
    void clear();
}
