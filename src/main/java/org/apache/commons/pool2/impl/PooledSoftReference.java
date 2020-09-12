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

import java.lang.ref.SoftReference;

/**
 * Extension of {@link DefaultPooledObject} to wrap pooled soft references.
 *
 * <p>This class is intended to be thread-safe.</p>
 *
 * @param <T> the type of the underlying object that the wrapped SoftReference
 *            refers to.
 * @since 2.0
 */
public class PooledSoftReference<T> extends DefaultPooledObject<T> {

    /** 被包装对象的引用 */
    private volatile SoftReference<T> reference;

    /**
     * Creates a new PooledSoftReference wrapping the provided reference.
     *
     * @param reference SoftReference to be managed by the pool
     */
    public PooledSoftReference(final SoftReference<T> reference) {
        super(null);  // Null the hard reference in the parent
        this.reference = reference;
    }

    /**
     * 返回被包装的原始对象
     *
     * @return
     */
    @Override
    public T getObject() {
        return reference.get();
    }

    public synchronized SoftReference<T> getReference() {
        return reference;
    }
    public synchronized void setReference(final SoftReference<T> reference) {
        this.reference = reference;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("Referenced Object: ");
        result.append(getObject().toString());
        result.append(", State: ");
        synchronized (this) {
            result.append(getState().toString());
        }
        return result.toString();
        // TODO add other attributes
        // TODO encapsulate state and other attribute display in parent
    }

}
