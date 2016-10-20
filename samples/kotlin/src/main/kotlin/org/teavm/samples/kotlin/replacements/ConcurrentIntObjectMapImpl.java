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
package org.teavm.samples.kotlin.replacements;

import com.intellij.util.containers.ConcurrentIntObjectMap;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class ConcurrentIntObjectMapImpl<V> implements ConcurrentIntObjectMap<V> {
    private Map<Integer, V> backingMap = new HashMap<Integer, V>();

    @NotNull
    @Override
    public V cacheOrGet(int i, @NotNull V t) {
        V v = get(i);
        if (v != null) {
            return v;
        }

        V prev = putIfAbsent(i, t);
        return prev == null ? t : prev;
    }

    @Override
    public boolean remove(int i, @NotNull V v) {
        V old = backingMap.get(i);
        if (old == v) {
            backingMap.remove(i);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean replace(int i, @NotNull V v, @NotNull V v1) {
        V old = backingMap.get(i);
        if (old == v) {
            backingMap.put(i, v1);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public V put(int i, @NotNull V v) {
        return backingMap.put(i, v);
    }

    @Override
    public V get(int i) {
        return backingMap.get(i);
    }

    @Override
    public V remove(int i) {
        return backingMap.remove(i);
    }

    @Override
    public boolean containsKey(int i) {
        return backingMap.containsKey(i);
    }

    @Override
    public void clear() {
        backingMap.clear();
    }

    @NotNull
    @Override
    public Iterable<IntEntry<V>> entries() {
        return () -> new Iterator<IntEntry<V>>() {
            private Iterator<Map.Entry<Integer, V>> backingIterator = backingMap.entrySet().iterator();

            @Override
            public boolean hasNext() {
                return backingIterator.hasNext();
            }

            @Override
            public IntEntry<V> next() {
                Map.Entry<Integer, V> backingEntry = backingIterator.next();
                return new IntEntryImpl<>(backingEntry.getKey(), backingEntry.getValue());
            }
        };
    }

    @NotNull
    @Override
    public int[] keys() {
        int[] result = new int[backingMap.keySet().size()];
        int i = 0;
        for (Integer integer : backingMap.keySet()) {
            result[i++] = integer;
        }
        return result;
    }

    @Override
    public int size() {
        return backingMap.size();
    }

    @Override
    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @NotNull
    @Override
    public Enumeration<V> elements() {
        return null;
    }

    @NotNull
    @Override
    public Collection<V> values() {
        return null;
    }

    @Override
    public boolean containsValue(@NotNull V v) {
        return false;
    }

    @Override
    public V putIfAbsent(int i, @NotNull V v) {
        V old = backingMap.get(i);
        if (old == null) {
            backingMap.put(i, v);
            return null;
        } else {
            return old;
        }
    }

    private static class IntEntryImpl<V> implements IntEntry<V> {
        private int key;
        private V value;

        IntEntryImpl(int key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int getKey() {
            return key;
        }

        @NotNull
        @Override
        public V getValue() {
            return value;
        }
    }
}
