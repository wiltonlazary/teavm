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
package org.teavm.platform;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

public abstract class PlatformLong implements JSObject {
    private static JSObject constructor = createConstructor();
    public static final PlatformLong ZERO = create(0, 0);
    private static final double MAX_NORMAL = 0b1_000_000_000_000_000_000;

    @JSBody(params = {}, script = "return this.lo;")
    public native int lo();

    @JSBody(params = {}, script = "return this.hi;")
    public native int hi();

    public static PlatformLong create(int lo, int hi) {
        return create(lo, hi, constructor);
    }

    public static PlatformLong fromInt(int value) {
        return value > 0 ? create(value, 0) : create(value, -1);
    }

    public static PlatformLong fromNumber(double value) {
        if (value >= 0) {
            return create((int) value, (int) (value / 0x100000000L));
        } else {
            return create((int) value, -(int) (-value / 0x100000000L) - 1);
        }
    }

    public static double toNumber(PlatformLong value) {
        double lo = value.lo();
        double hi = value.hi();
        if (lo < 0) {
            lo += 0x100000000L;
        }
        return 0x100000000L * hi + lo;
    }

    public static PlatformLong add(PlatformLong a, PlatformLong b) {
        if (a.hi() == (a.lo() >> 31) && b.hi() == (b.lo() >> 31)) {
            return fromNumber(a.lo() + b.lo());
        } else if (Math.abs(a.hi()) < MAX_NORMAL && Math.abs(b.hi()) < MAX_NORMAL) {
            return fromNumber(toNumber(a) + toNumber(b));
        }

        short alolo = (short) a.lo();
        short alohi = (short) (a.lo() >>> 16);
        short ahilo = (short) a.hi();
        short ahihi = (short) (a.hi() >>> 16);
        short blolo = (short) b.lo();
        short blohi = (short) (b.lo() >>> 16);
        short bhilo = (short) b.hi();
        short bhihi = (short) (b.hi() >>> 16);

        int lolo = alolo + blolo;
        int lohi = alohi + blohi + (lolo >> 16);
        int hilo = ahilo + bhilo + (lohi >> 16);
        int hihi = ahihi + bhihi + (hilo >> 16);
        return create((lolo & 0xFFFF) | ((lohi & 0xFFFF) << 16), (hilo & 0xFFFF) | ((hihi & 0xFFFF) << 16));
    }

    @JSBody(params = { "lo", "hi", "constructor" }, script = "return constructor(lo, hi);")
    private static native PlatformLong create(int lo, int hi, JSObject constructor);

    @JSBody(params = {}, script = ""
            + "return function Long(lo, hi) {"
            + "    this.lo = lo;"
            + "    this.hi = hi;"
            + "")
    private static native JSObject createConstructor();
}
