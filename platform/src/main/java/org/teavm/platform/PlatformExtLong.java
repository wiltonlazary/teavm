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

class PlatformExtLong {
    public int lo;
    public int hi;
    public int sup;

    public PlatformExtLong(int lo, int hi, int sup) {
        this.lo = lo;
        this.hi = hi;
        this.sup = sup;
    }

    public void mulBy(int b) {
        int alolo = (lo & 0xFFFF) * b;
        int alohi = (lo >>> 16) * b;
        int ahilo = (hi & 0xFFFF) * b;
        int ahihi = (hi >>> 16) * b;
        int sup = this.sup * b;

        alohi = alohi + (alolo >>> 16);
        ahilo = ahilo + (alohi >>> 16);
        ahihi = ahihi + (ahilo >>> 16);
        sup = sup + (ahihi >>> 16);
        lo = (alolo & 0xFFFF) | (alohi << 16);
        hi = (ahilo & 0xFFFF) | (ahihi << 16);
        this.sup = sup & 0xFFFF;
    }

    public void add(PlatformExtLong b) {
        int alolo = lo & 0xFFFF;
        int alohi = lo >>> 16;
        int ahilo = hi & 0xFFFF;
        int ahihi = hi >>> 16;
        int blolo = lo & 0xFFFF;
        int blohi = lo >>> 16;
        int bhilo = hi & 0xFFFF;
        int bhihi = hi >>> 16;

        alolo = alolo + blolo;
        alohi = alohi + blohi + (alolo >> 16);
        ahilo = ahilo + bhilo + (alohi >> 16);
        ahihi = ahihi + bhihi + (ahilo >> 16);
        int sup = this.sup + b.sup + (ahihi >> 16);
        lo = (alolo & 0xFFFF) | (alohi << 16);
        hi = (ahilo & 0xFFFF) | (ahihi << 16);
        this.sup = sup;
    }
    
    public void sub(PlatformExtLong b) {
        int alolo = lo & 0xFFFF;
        int alohi = lo >>> 16;
        int ahilo = hi & 0xFFFF;
        int ahihi = hi >>> 16;
        int blolo = b.lo & 0xFFFF;
        int blohi = b.lo >>> 16;
        int bhilo = b.hi & 0xFFFF;
        int bhihi = b.hi >>> 16;

        alolo = alolo - blolo;
        alohi = alohi - blohi + (alolo >> 16);
        ahilo = ahilo - bhilo + (alohi >> 16);
        ahihi = ahihi - bhihi + (ahilo >> 16);
        int sup = this.sup - b.sup + (ahihi >> 16);
        lo = (alolo & 0xFFFF) | (alohi << 16);
        hi = (ahilo & 0xFFFF) | (ahihi << 16);
        this.sup = sup;
    }

    public void inc() {
        if (lo++ == 0) {
            if (hi++ == 0) {
                sup = (sup + 1) & 0xFFFF;
            }
        }
    }

    public void dec() {
        if (lo-- == -1) {
            if (hi-- == -1) {
                sup = (sup - 1) & 0xFFFF;
            }
        }
    }

    public int ucompare(PlatformExtLong b) {
        int r = sup - b.sup;
        if (r != 0) {
            return r;
        }
        r = (hi >>> 1) - (b.hi >>> 1);
        if (r != 0) {
            return r;
        }
        r = (hi & 1) - (b.hi & 1);
        if (r != 0) {
            return r;
        }
        r = (lo >>> 1) - (b.lo >>> 1);
        if (r != 0) {
            return r;
        }
        return (lo & 1) - (b.lo & 1);
    }

    private static int numOfLeadingZeroBits(int a) {
        int n = 0;
        int d = 16;
        while (d > 0) {
            if ((a >>> d) != 0) {
                a >>>= d;
                n += d;
            }
            d /= 2;
        }
        return 31 - n;
    }

    public void shl(int b) {
        if (b == 0) {
            return;
        }
        if (b < 32) {
            sup = ((hi >>> (32 - b)) | (sup << b)) & 0xFFFF;
            hi = (lo >>> (32 - b)) | (hi << b);
            lo <<= b;
        } else if (b == 32) {
            sup = hi & 0xFFFF;
            hi = lo;
            lo = 0;
        } else if (b < 64) {
            sup = ((lo >>> (64 - b)) | (hi << (b - 32))) & 0xFFFF;
            hi = lo << b;
            lo = 0;
        } else if (b == 64) {
            sup = lo & 0xFFFF;
            hi = 0;
            lo = 0;
        } else {
            sup = (lo << (b - 64)) & 0xFFFF;
            hi = 0;
            lo = 0;
        }
    }

    public void shr(int b) {
        if (b == 0) {
            return;
        }
        if (b == 32) {
            lo = hi;
            hi = sup;
            sup = 0;
        } else if (b < 32) {
            lo = (lo >>> b) | (hi << (32 - b));
            hi = (hi >>> b) | (sup << (32 - b));
            sup >>>= b;
        } else if (b == 64) {
            lo = sup;
            hi = 0;
            sup = 0;
        } else if (b < 64) {
            lo = (hi >>> (b - 32)) | (sup << (64 - b));
            hi = sup >>> (b - 32);
            sup = 0;
        } else {
            lo = sup >>> (b - 64);
            hi = 0;
            sup = 0;
        }
    }

    public PlatformExtLong divBy(PlatformExtLong b) {
        PlatformExtLong a = this;

        // Normalize divisor
        int bits = b.hi != 0 ? Integer.numberOfLeadingZeros(b.hi) : Integer.numberOfLeadingZeros(b.lo) + 32;
        int sz = 1 + bits / 16;
        int dividentBits = bits % 16;
        b.shl(bits);
        a.shl(dividentBits);
        PlatformExtLong q = new PlatformExtLong(0, 0, 0);
        while (sz-- > 0) {
            q.shl(16);
            int digitA = (a.hi >>> 16) + (0x10000 * a.sup);
            int digitB = b.hi >>> 16;
            int digit = digitA / digitB;
            PlatformExtLong t = b.copy();
            t.mulBy(digit);
            // Adjust q either down or up
            if (t.ucompare(a) >= 0) {
                while (t.ucompare(a) > 0) {
                    t.sub(b);
                    --digit;
                }
            } else {
                while (true) {
                    PlatformExtLong nextT = t.copy();
                    nextT.add(b);
                    if (nextT.ucompare(a) > 0) {
                        break;
                    }
                    t = nextT;
                    ++digit;
                }
            }
            a.sub(t);
            q.lo |= digit;
            a.shl(16);
        }
        a.shl(bits + 16);
        return q;
    }

    public PlatformExtLong copy() {
        return new PlatformExtLong(lo, hi, sup);
    }
}
