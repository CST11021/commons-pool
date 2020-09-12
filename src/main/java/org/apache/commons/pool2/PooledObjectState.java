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
    /** 已出借状态 */
    ALLOCATED,
    /** 在队列中，当前正进行驱逐测试：驱逐定时器（EvictionTimer）会定时选定一批对象进行驱逐测试，对于符合驱逐条件的对象，将会被对象池无情的驱逐出空闲空间，并丢弃到invalid空间 */
    EVICTION,

    /**
     * 如果对象刚好在做驱逐测试，而此时该对象又被选为要借出去对象，则此时会将对象状态设置为该状态，驱逐测试后，如果对象仍然可用，则保存在队列头部，后续可以优先被借出去
     */
    EVICTION_RETURN_TO_HEAD,

    /** 在队列中 正在被检查 */
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

    /** 对象不可用 即将/已经被销毁 */
    INVALID,

    /** 被放逐：对象出借时间太长，我们就把他们称作`流浪对象`，这些对象很有可能是那些用完不还的坏蛋们的杰作，也有可能是对象使用者出现了什么突发状况，比如网络连接超时时间设置长于放逐时间。总之，被放逐的对象是不允许再次回归到对象池中的，他们会被搁置到abandon空间，进而进入invalid空间再被gc掉以完成他们的使命 */
    ABANDONED,

    /** 返回对象池 */
    RETURNING
}
