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
package org.teavm.llvm.runtime;

public final class LLVM {
    private LLVM() {
    }

    public static native void println(int number);

    public static void print(String string) {
        byte[] bytes = string.getBytes();
        for (int i = 0; i < string.length(); ++i) {
            bytes[i] = (byte) string.charAt(i);
        }
        byte[] zeroTerminated = new byte[bytes.length + 1];
        for (int i = 0; i < bytes.length; ++i) {
            zeroTerminated[i] = bytes[i];
        }
        putChars(zeroTerminated);
    }

    public static void println(String string) {
        print(string);
        print("\n");
    }

    private static void putChars(byte[] data) {
        int last = 0;
        for (int i = 0; i < data.length - 1; ++i) {
            if (data[i] == 0) {
                puts(data, last);
                putChar((byte) 0);
                last = i + 1;
            }
        }
        puts(data, last);
    }

    private static native void puts(byte[] data, int start);

    private static native void putChar(byte c);
}
