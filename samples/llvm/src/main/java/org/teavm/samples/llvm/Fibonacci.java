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
package org.teavm.samples.llvm;

import java.math.BigInteger;

public class Fibonacci {
    private Fibonacci() {
    }

    private static void test() {
        try {
            test2();
            System.out.println("Exception should have been thrown");
        } catch (IllegalStateException e) {
            System.out.println("Exception caught");
        }
    }

    private static void test2() {
        throw new IllegalStateException();
    }

    public static void main(String[] args) {
        test();
        BigInteger result = BigInteger.ONE;
        for (int j = 0; j < 100; ++j) {
            long start = System.currentTimeMillis();

            for (int k = 0; k < 5000; ++k) {
                BigInteger a = BigInteger.ZERO;
                BigInteger b = BigInteger.ONE;
                for (int i = 0; i < 1000; ++i) {
                    BigInteger c = a.add(b);
                    a = b;
                    b = c;
                }
                result = a;
            }

            long end = System.currentTimeMillis();

            System.out.println("Operation took " + (end - start) + " milliseconds");
        }
        System.out.println(result);
    }
}
