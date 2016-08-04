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

import org.teavm.jso.JSObject;

public class PlatformLong implements JSObject {
    public static final PlatformLong ZERO = create(0, 0);
    private static final double MAX_NORMAL = 0b1_000_000_000_000_000_000;
    public final int lo;
    public final int hi;

    public PlatformLong(int lo, int hi) {
        this.lo = lo;
        this.hi = hi;
    }

    public static PlatformLong create(int lo, int hi) {
        return new PlatformLong(lo, hi);
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
        double lo = this.lo;
        double hi = this.hi;
        if (lo < 0) {
            lo += 0x100000000L;
        }
        return 0x100000000L * hi + lo;
    }

    public int toInt() {
        return lo;
    }

    public PlatformLong add(PlatformLong other) {
        if (hi == (lo >> 31) && other.hi == (other.lo >> 31)) {
            return fromNumber(lo + other.lo);
        } else if (Math.abs(hi) < MAX_NORMAL && Math.abs(other.hi) < MAX_NORMAL) {
            return fromNumber(toNumber() + other.toNumber());
        }

        short thisLL = (short) lo;
        short thisLH = (short) (lo >>> 16);
        short thisHL = (short) hi;
        short thisHH = (short) (hi >>> 16);
        short otherLL = (short) other.lo;
        short otherLH = (short) (other.lo >>> 16);
        short otherHL = (short) other.hi;
        short otherHH = (short) (other.hi >>> 16);

        int resultLL = thisLL + otherLL;
        int resultLH = thisLH + otherLH + (resultLL >> 16);
        int resultHL = thisHL + otherHL + (resultLH >> 16);
        int resultHH = thisHH + otherHH + (resultHL >> 16);
        return create((resultLL & 0xFFFF) | ((resultLH & 0xFFFF) << 16),
                (resultHL & 0xFFFF) | ((resultHH & 0xFFFF) << 16));
    }

    public PlatformLong inc() {
        int lo = this.lo + 1;
        int hi = this.hi;
        if (lo == 0) {
            hi++;
        }
        return create(lo, hi);
    }

    public PlatformLong dec() {
        int lo = this.lo - 1;
        int hi = this.hi;
        if (lo == -1) {
            hi--;
        }
        return create(lo, hi);
    }

    public PlatformLong neg() {
        return create(~lo, ~hi).inc();
    }

    public PlatformLong sub(PlatformLong other) {
        if (hi == (other.lo >> 31) && hi == (other.lo >> 31)) {
            return fromNumber(lo - other.lo);
        }

        int thisLL = lo & 0xFFFF;
        int thisLH = lo >>> 16;
        int thisHL = hi & 0xFFFF;
        int thisHH = hi >>> 16;
        int otherLL = other.lo & 0xFFFF;
        int otherLH = other.lo >>> 16;
        int otherHL = other.hi & 0xFFFF;
        int otherHH = other.hi >>> 16;

        int resultLL = thisLL - otherLL;
        int resultLH = thisLH - otherLH + (resultLL >> 16);
        int resultHL = thisHL - otherHL + (resultLH >> 16);
        int resultHH = thisHH - otherHH + (resultHL >> 16);
        return create((resultLL & 0xFFFF) | ((resultLH & 0xFFFF) << 16),
                (resultHL & 0xFFFF) | ((resultHH & 0xFFFF) << 16));
    }

    public int compare(PlatformLong other) {
        int r = hi - other.hi;
        if (r != 0) {
            return r;
        }
        r = (lo >>> 1) - (other.lo >>> 1);
        if (r != 0) {
            return r;
        }
        return (lo & 1) - (other.lo & 1);
    }

    public boolean isPositive() {
        return (hi & 0x80000000) == 0;
    }

    public boolean isNegative() {
        return (hi & 0x80000000) != 0;
    }

    public PlatformLong mul(PlatformLong other) {
        PlatformLong a = this;

        boolean positive = a.isNegative() == other.isNegative();
        if (a.isNegative()) {
            a = a.neg();
        }
        if (other.isNegative()) {
            other = other.neg();
        }
        int thisLL = a.lo & 0xFFFF;
        int thisLH = a.lo >>> 16;
        int thisHL = a.hi & 0xFFFF;
        int thisHH = a.hi >>> 16;
        int otherLL = other.lo & 0xFFFF;
        int otherLH = other.lo >>> 16;
        int otherHL = other.hi & 0xFFFF;
        int otherHH = other.hi >>> 16;

        int resultLL = thisLL * otherLL;
        int resultLH = resultLL >>> 16;
        resultLH = (resultLH & 0xFFFF) + thisLH * otherLL;
        int resultHL = resultLH >>> 16;
        resultLH = (resultLH & 0xFFFF) + thisLL * otherLH;
        resultHL = resultHL + (resultLH >>> 16);
        int resultHH = resultHL >>> 16;
        resultHL = (resultHL & 0xFFFF) + thisHL * otherLL;
        resultHH = resultHH + (resultHL >>> 16);
        resultHL = (resultHL & 0xFFFF) + thisLH * otherLH;
        resultHH = resultHH + (resultHL >>> 16);
        resultHL = (resultHL & 0xFFFF) + thisLL * otherHL;
        resultHH = resultHH + (resultHL >>> 16);
        resultHH = resultHH + thisHH * otherLL + thisHL * otherLH + thisLH * otherHL + thisLL * otherHH;
        PlatformLong result = create((resultLL & 0xFFFF) | (resultLH << 16), (resultHL & 0xFFFF) | (resultHH << 16));
        return positive ? result : result.neg();
    }

    public PlatformLong div(PlatformLong other) {
        if (Math.abs(hi) < MAX_NORMAL && Math.abs(other.hi) < MAX_NORMAL) {
            return fromNumber(toNumber() / other.toNumber());
        }
        return performDivision(this, other).quotient;
    }

    public PlatformLong rem(PlatformLong other) {
        if (Math.abs(hi) < MAX_NORMAL && Math.abs(other.hi) < MAX_NORMAL) {
            return fromNumber(toNumber() % other.toNumber());
        }
        return performDivision(this, other).remainder;
    }

    private static PlatformLongDivisionResult performDivision(PlatformLong a, PlatformLong b) {
        if (b.lo == 0 && b.hi == 0) {
            throw new ArithmeticException("Division by zero");
        }
        boolean positive = a.isNegative() == b.isNegative();
        if (a.isNegative()) {
            a = a.neg();
        }
        if (b.isNegative()) {
            b = b.neg();
        }

        PlatformExtLong x = new PlatformExtLong(a.lo, a.hi, 0);
        PlatformExtLong y = new PlatformExtLong(b.lo, b.hi, 0);
        PlatformExtLong q = x.divBy(y);

        PlatformLong remainder = create(x.lo, x.hi);
        PlatformLong quotient = create(q.lo, q.hi);
        if (!positive) {
            remainder = remainder.neg();
            quotient = quotient.neg();
        }
        return new PlatformLongDivisionResult(remainder, quotient);
    }

    public PlatformLong and(PlatformLong other) {
        return create(lo & other.lo, hi & other.hi);
    }

    public PlatformLong or(PlatformLong other) {
        return create(lo | other.lo, hi | other.hi);
    }

    public PlatformLong xor(PlatformLong other) {
        return create(lo ^ other.lo, hi ^ other.hi);
    }

    public PlatformLong shl(int count) {
        count &= 63;
        if (count == 0) {
            return this;
        } else if (count < 32) {
            return create(lo << count, (lo >>> (32 - count)) | (hi << count));
        } else if (count == 32) {
            return create(0, lo);
        } else {
            return create(0, lo << (count - 32));
        }
    }

    public PlatformLong shr(int count) {
        count &= 63;
        if (count == 0) {
            return this;
        } else if (count < 32) {
            return create((lo >>> count) | (hi << (32 - count)), hi >> count);
        } else if (count == 32) {
            return create(hi, hi >> 31);
        } else {
            return create(hi >> (count - 32), hi >> 31);
        }
    }

    public PlatformLong shru(int count) {
        count &= 63;
        if (count == 0) {
            return this;
        } else if (count < 32) {
            return create((lo >>> count) | (hi << (32 - count)), hi >>> count);
        } else if (count == 32) {
            return create(hi, 0);
        } else {
            return create(hi >>> (count - 32), 0);
        }
    }
}
