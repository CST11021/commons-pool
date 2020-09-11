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

/**
 * Provides the possible states that a {@link PooledObject} may be in.
 *
 * @since 2.0
 */
public enum PooledObjectState {

    /** 在池中，处于空闲状态 */
    IDLE,

    /**
     * 已出借状态
     */
    ALLOCATED,

    /**
     * 在队列中，当前正在测试可能的驱逐。
     */
    EVICTION,

    /**
     * Not in the queue, currently being tested for possible eviction. An
     * attempt to borrow the object was made while being tested which removed it
     * from the queue. It should be returned to the head of the queue once
     * eviction testing completes.
     * TODO: Consider allocating object and ignoring the result of the eviction
     * test.
     */
    EVICTION_RETURN_TO_HEAD,

    /**
     * 在队列中 正在被检查
     */
    VALIDATION,

    /**
     * 验证结束要被分配：
     * 不在队列中，目前正在验证中。
     * 对象是在验证时借用的，并且由于已配置testOnBorrow，因此已将其从队列中删除并进行了预先分配。
     * 验证完成后应分配。
     */
    VALIDATION_PREALLOCATED,

    /**
     * 检查结束要放回队列头部：
     * 不在队列中，目前正在验证中。
     * 在先前进行逐出测试时，尝试借用该对象将其从队列中删除。
     * 验证完成后，应将其返回到队列的开头。
     */
    VALIDATION_RETURN_TO_HEAD,

    /**
     * 不可用 即将/已经被销毁
     */
    INVALID,

    /**
     * 被遗弃
     */
    ABANDONED,

    /**
     * 返回对象池
     */
    RETURNING
}
