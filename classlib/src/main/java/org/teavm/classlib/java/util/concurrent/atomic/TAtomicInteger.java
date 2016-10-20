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
import java.util.function.IntUnaryOperator;
import org.teavm.interop.Sync;

public class TAtomicInteger extends Number implements Serializable {
    private int value;

    public TAtomicInteger(int value) {
        this.value = value;
    }

    public TAtomicInteger() {
        this(0);
    }

    @Sync
    public final int get() {
        return value;
    }

    @Sync
    public final void set(int newValue) {
        value = newValue;
    }

    @Sync
    public final void lazySet(int newValue) {
        value = newValue;
    }

    @Sync
    public final int getAndSet(int newValue) {
        int oldValue = value;
        value = newValue;
        return oldValue;
    }

    @Sync
    public final boolean compareAndSet(int expect, int update) {
        if (value == expect) {
            value = update;
            return true;
        } else {
            return false;
        }
    }

    @Sync
    public final boolean weakCompareAndSet(int expect, int update) {
        return compareAndSet(expect, update);
    }

    @Sync
    public final int getAndIncrement() {
        return value++;
    }

    @Sync
    public final int getAndDecrement() {
        return value--;
    }

    @Sync
    public final int getAndAdd(int delta) {
        int result = value;
        value += delta;
        return result;
    }

    @Sync
    public final int incrementAndGet() {
        return ++value;
    }

    @Sync
    public final int decrementAndGet() {
        return --value;
    }

    @Sync
    public final int addAndGet(int delta) {
        value += delta;
        return value;
    }

    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int initial;
        int newValue;
        do {
            initial = value;
            newValue = updateFunction.applyAsInt(value);
        } while (compareAndSet(initial, newValue));
        return initial;
    }

    public final int updateAndGet(IntUnaryOperator updateFunction) {
        int initial;
        int newValue;
        do {
            initial = value;
            newValue = updateFunction.applyAsInt(value);
        } while (compareAndSet(initial, newValue));
        return newValue;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    @Override
    @Sync
    public int intValue() {
        return value;
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
