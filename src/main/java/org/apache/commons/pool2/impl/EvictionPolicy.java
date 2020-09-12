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

/**
 * 用于进行驱逐测试的策略接口
 *
 * @param <T> the type of objects in the pool
 * @since 2.0
 */
public interface EvictionPolicy<T> {

    /**
     * 驱逐方法：调用此方法以测试是否应清除池中的空闲对象
     *
     * @param config    驱逐配置
     * @param underTest 本次要测试的池对象
     * @param idleCount 当前支持空闲对象的数量
     * @return <code>true</code> if the object should be evicted, otherwise
     * <code>false</code>
     */
    boolean evict(EvictionConfig config, PooledObject<T> underTest, int idleCount);
}
