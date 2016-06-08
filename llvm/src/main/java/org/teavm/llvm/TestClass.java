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
package org.teavm.llvm;

public class TestClass {
    private TestClass() {
    }

    abstract static class Base {
        abstract int getX();
    }

    static class A extends Base {
        int x = 23;

        @Override
        int getX() {
            return x;
        }
    }

    static class B extends Base {
        @Override
        int getX() {
            return 42;
        }
    }

    public static void main(String[] args) {
        int a = 0;
        int b = 1;
        for (int i = 0; i < 20; ++i) {
            printf(a);
            int c = a + b;
            a = b;
            b = c;
        }

        A aa = new A();
        printf(getX(aa));
        printf(getX(new B()));
    }

    private static int getX(Base a) {
        return a.getX();
    }

    private static native void printf(int a);
}
