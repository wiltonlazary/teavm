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
import java.util.function.LongUnaryOperator;
import org.teavm.interop.Sync;

public class TAtomicLong extends Number implements Serializable {
    private long value;

    public TAtomicLong(long value) {
        this.value = value;
    }

    public TAtomicLong() {
        this(0);
    }

    @Sync
    public final long get() {
        return value;
    }

    @Sync
    public final void set(long newValue) {
        value = newValue;
    }

    @Sync
    public final void lazySet(long newValue) {
        value = newValue;
    }

    @Sync
    public final long getAndSet(long newValue) {
        long oldValue = value;
        value = newValue;
        return oldValue;
    }

    @Sync
    public final boolean compareAndSet(long expect, long update) {
        if (value == expect) {
            value = update;
            return true;
        } else {
            return false;
        }
    }

    @Sync
    public final boolean weakCompareAndSet(long expect, long update) {
        return compareAndSet(expect, update);
    }

    @Sync
    public final long getAndIncrement() {
        return value++;
    }

    @Sync
    public final long getAndDecrement() {
        return value--;
    }

    @Sync
    public final long getAndAdd(long delta) {
        long result = value;
        value += delta;
        return result;
    }

    @Sync
    public final long incrementAndGet() {
        return ++value;
    }

    @Sync
    public final long decrementAndGet() {
        return --value;
    }

    @Sync
    public final long addAndGet(long delta) {
        value += delta;
        return value;
    }

    public final long getAndUpdate(LongUnaryOperator updateFunction) {
        long initial;
        long newValue;
        do {
            initial = value;
            newValue = updateFunction.applyAsLong(value);
        } while (compareAndSet(initial, newValue));
        return initial;
    }

    public final long updateAndGet(LongUnaryOperator updateFunction) {
        long initial;
        long newValue;
        do {
            initial = value;
            newValue = updateFunction.applyAsLong(value);
        } while (compareAndSet(initial, newValue));
        return newValue;
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }

    @Override
    @Sync
    public int intValue() {
        return (int) value;
    }

    @Override
    @Sync
    public long longValue() {
        return value;
    }

    @Override
    @Sync
    public float floatValue() {
        return value;
    }

    @Override
    @Sync
    public double doubleValue() {
        return value;
    }
}
