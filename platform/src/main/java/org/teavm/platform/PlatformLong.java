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

    public double toNumber() {
        double lo = lo();
        double hi = hi();
        if (lo < 0) {
            lo += 0x100000000L;
        }
        return 0x100000000L * hi + lo;
    }

    public PlatformLong add(PlatformLong other) {
        if (hi() == (lo() >> 31) && other.hi() == (other.lo() >> 31)) {
            return fromNumber(lo() + other.lo());
        } else if (Math.abs(hi()) < MAX_NORMAL && Math.abs(other.hi()) < MAX_NORMAL) {
            return fromNumber(toNumber() + other.toNumber());
        }

        short thisLolo = (short) lo();
        short thisLohi = (short) (lo() >>> 16);
        short thisHilo = (short) hi();
        short thisHihi = (short) (hi() >>> 16);
        short otherLolo = (short) other.lo();
        short otherLohi = (short) (other.lo() >>> 16);
        short otherHilo = (short) other.hi();
        short otherHihi = (short) (other.hi() >>> 16);

        int lolo = thisLolo + otherLolo;
        int lohi = thisLohi + otherLohi + (lolo >> 16);
        int hilo = thisHilo + otherHilo + (lohi >> 16);
        int hihi = thisHihi + otherHihi + (hilo >> 16);
        return create((lolo & 0xFFFF) | ((lohi & 0xFFFF) << 16), (hilo & 0xFFFF) | ((hihi & 0xFFFF) << 16));
    }

    public PlatformLong inc() {
        int lo = lo() + 1;
        int hi = hi();
        if (lo == 0) {
            hi++;
        }
        return create(lo, hi);
    }

    public PlatformLong dec() {
        int lo = lo() - 1;
        int hi = hi();
        if (lo == -1) {
            hi--;
        }
        return create(lo, hi);
    }

    public PlatformLong neg() {
        return create(~lo(), ~hi()).inc();
    }

    public PlatformLong sub(PlatformLong other) {
        if (hi() == (other.lo() >> 31) && hi() == (other.lo() >> 31)) {
            return fromNumber(lo() - other.lo());
        }

        int thisLolo = lo() & 0xFFFF;
        int thisLohi = lo() >>> 16;
        int thisHilo = hi() & 0xFFFF;
        int thisHihi = hi() >>> 16;
        int otherLolo = other.lo() & 0xFFFF;
        int otherLohi = other.lo() >>> 16;
        int otherHilo = other.hi() & 0xFFFF;
        int otherHihi = other.hi() >>> 16;

        int lolo = thisLolo - otherLolo;
        int lohi = thisLohi - otherLohi + (lolo >> 16);
        int hilo = thisHilo - otherHilo + (lohi >> 16);
        int hihi = thisHihi - otherHihi + (hilo >> 16);
        return create((lolo & 0xFFFF) | ((lohi & 0xFFFF) << 16), (hilo & 0xFFFF) | ((hihi & 0xFFFF) << 16));
    }

    public int compare(PlatformLong other) {
        int r = hi() - other.hi();
        if (r != 0) {
            return r;
        }
        r = (lo() >>> 1) - (other.lo() >>> 1);
        if (r != 0) {
            return r;
        }
        return (lo() & 1) - (other.lo() & 1);
    }

    public boolean isPositive() {
        return (hi() & 0x80000000) == 0;
    }

    public boolean isNegative() {
        return (hi() & 0x80000000) != 0;
    }

    private PlatformLong mul(PlatformLong b) {
        PlatformLong a = this;

        boolean positive = a.isNegative() == b.isNegative();
        if (a.isNegative()) {
            a = a.neg();
        }
        if (b.isNegative()) {
            b = b.neg();
        }
        int alolo = a.lo() & 0xFFFF;
        int alohi = a.lo() >>> 16;
        int ahilo = a.hi() & 0xFFFF;
        int ahihi = a.hi() >>> 16;
        int blolo = b.lo() & 0xFFFF;
        int blohi = b.lo() >>> 16;
        int bhilo = b.hi() & 0xFFFF;
        int bhihi = b.hi() >>> 16;

        int lolo;
        int lohi;
        int hilo;
        int hihi;
        lolo = alolo * blolo;
        lohi = lolo >>> 16;
        lohi = (lohi & 0xFFFF) + alohi * blolo;
        hilo = lohi >>> 16;
        lohi = (lohi & 0xFFFF) + alolo * blohi;
        hilo = hilo + (lohi >>> 16);
        hihi = hilo >>> 16;
        hilo = (hilo & 0xFFFF) + ahilo * blolo;
        hihi = hihi + (hilo >>> 16);
        hilo = (hilo & 0xFFFF) + alohi * blohi;
        hihi = hihi + (hilo >>> 16);
        hilo = (hilo & 0xFFFF) + alolo * bhilo;
        hihi = hihi + (hilo >>> 16);
        hihi = hihi + ahihi * blolo + ahilo * blohi + alohi * bhilo + alolo * bhihi;
        PlatformLong result = create((lolo & 0xFFFF) | (lohi << 16), (hilo & 0xFFFF) | (hihi << 16));
        return positive ? result : result.neg();
    }

    @JSBody(params = { "lo", "hi", "constructor" }, script = "return constructor(lo, hi);")
    private static native PlatformLong create(int lo, int hi, JSObject constructor);

    @JSBody(params = {}, script = ""
            + "return function Long(lo, hi) {"
            + "    this.lo = lo;"
            + "    this.hi = hi;"
            + "}")
    private static native JSObject createConstructor();
}
