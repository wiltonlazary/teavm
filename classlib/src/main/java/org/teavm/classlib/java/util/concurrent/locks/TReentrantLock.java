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
package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import org.teavm.classlib.java.lang.TObject;

public class TReentrantLock implements TLock {
    public TReentrantLock() {
    }

    public TReentrantLock(boolean fair) {
    }

    @Override
    public void lock() {
        TObject.monitorEnter((TObject) (Object) this);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (!Thread.currentThread().isInterrupted()) {
            lock();
        }
    }

    @Override
    public boolean tryLock() {
        return TObject.tryLock((TObject) (Object) this);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() {
        TObject.monitorEnter((TObject) (Object) this);
    }
}
