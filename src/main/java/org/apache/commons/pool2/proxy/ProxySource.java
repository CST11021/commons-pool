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

import org.apache.commons.pool2.UsageTracking;

import java.lang.reflect.Method;

/**
 * 代理池对象，提供了池对象的堆栈跟踪功能
 *
 * @param <T> type of the pooled object to be proxied
 * @since 2.0
 */
interface ProxySource<T> {

    /**
     * 创建一个代理对象，该代理对象提供了这样的一个能力：池对象的任何方法被调用时，都会触发UsageTracking#use()方法，进行堆栈跟踪，
     * 详细请参照：{@link BaseProxyHandler#doInvoke(Method, Object[])}
     *
     * @param pooledObject  被代理的池对象（即原始的池对象）
     * @param usageTracking 实例（如果有的话）（通常是对象池）将为此包装的对象提供使用情况跟踪信息
     * @return the new proxy object
     */
    T createProxy(T pooledObject, UsageTracking<T> usageTracking);

    /**
     * 获取原始的池对象
     *
     * @param proxy The proxy object
     * @return The pooled object wrapped by the given proxy
     */
    T resolveProxy(T proxy);
}
