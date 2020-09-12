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
 * 什么叫追踪？
 *  就是在池中某个对象的任何一个方法被调用时，都会创建一个调用堆栈快照。
 *  logAbandoned为true时，useUsageTracking也为true时，那么回收被遗弃的对象时，就会打印该对象最后一次的调用堆栈信息了。
 *  如果useUsageTracking为true，即便是logAbandoned为false，那么每次对象的方法调用，一样还是会创建调用堆栈对象。只不过最终被回收时不会打印输出。
 *  生产环境该属性也不建议设置为true。
 *
 * @param <T> The type of object provided by the pool.
 * @since 2.0
 */
public interface UsageTracking<T> {

    /**
     * 每次使用池对象来使池更好地跟踪借用对象时，都会调用此方法。
     *
     * @param pooledObject The object that is being used
     */
    void use(T pooledObject);
}
