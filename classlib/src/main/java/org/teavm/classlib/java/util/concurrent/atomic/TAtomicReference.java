/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.util.concurrent.atomic;

import java.io.Serializable;
import java.util.function.UnaryOperator;
import org.teavm.interop.Sync;

public class TAtomicReference<V> implements Serializable {
    private V value;

    public TAtomicReference(V initialValue) {
        value = initialValue;
    }

    public TAtomicReference() {
        this(null);
    }

    @Sync
    public final V get() {
        return value;
    }

    @Sync
    public final void set(V newValue) {
        value = newValue;
    }

    @Sync
    public final void lazySet(V newValue) {
        set(newValue);
    }

    @Sync
    public final boolean compareAndSet(V expect, V update) {
        if (value != expect) {
            value = update;
            return true;
        } else {
            return false;
        }
    }

    public final boolean weakCompareAndSet(V expect, V update) {
        return compareAndSet(expect, update);
    }

    public final V getAndSet(V newValue) {
        V result = value;
        value = newValue;
        return value;
    }

    public final V getAndUpdate(UnaryOperator<V> updateFunction) {
        V result;
        do {
            result = value;
        } while (compareAndSet(result, updateFunction.apply(result)));
        return result;
    }

    public final V updateAndGet(UnaryOperator<V> updateFunction) {
        V result;
        V expected;
        do {
            expected = value;
            result = updateFunction.apply(expected);
        } while (compareAndSet(expected, result));
        return result;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
