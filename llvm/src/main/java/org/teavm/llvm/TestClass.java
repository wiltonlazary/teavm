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

import org.teavm.llvm.runtime.LLVM;

public class TestClass {
    private TestClass() {
    }

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello, ").append("world!").append(111);
        LLVM.println(sb.toString());

        int a = 0;
        int b = 1;
        for (int i = 0; i < 20; ++i) {
            LLVM.println(a);
            int c = a + b;
            a = b;
            b = c;
        }

        LLVM.println(getX(new B(23)));
        LLVM.println(getX(new C()));
        LLVM.println(A.zzz);

        int[] array = { 12, 13, 7, 8 };
        LLVM.println(array.length);
        for (int i = 0; i < array.length; ++i) {
            LLVM.println(i);
            LLVM.println(array[i]);
        }

        A aa = new B(8888);
        LLVM.println(aa.hashCode());
        LLVM.println(new C().hashCode());
        LLVM.println(array.hashCode());

        I[] iarray = { new C(), () -> 55 };
        LLVM.println(iarray.length);
        for (I i : iarray) {
            LLVM.println(i.foo());
        }

        String str = "foobarbaz";
        LLVM.println(str.length());
        for (int i = 0; i < str.length(); ++i) {
            LLVM.println(str.charAt(i));
        }
    }

    private static int getX(A a) {
        return a.getX();
    }


    static abstract class A {
        static int zzz = 555;

        static {
            LLVM.println(678);
        }

        abstract int getX();
    }

    static class B extends A {
        private int x;

        public B(int x) {
            this.x = x;
        }

        @Override
        public int getX() {
            return x;
        }
    }

    static class C extends A implements I {
        @Override
        int getX() {
            return 42;
        }

        @Override
        public int foo() {
            return getX();
        }
    }

    interface I {
        int foo();
    }
}
