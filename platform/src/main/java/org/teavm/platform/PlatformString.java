/*
 *  Copyright 2015 Alexey Andreev.
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

import java.util.HashMap;
import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArray;

public abstract class PlatformString implements JSObject {
    private static JSArray<PlatformObject> pool;

    private static Map<String, String> internMap = new HashMap<>();

    public abstract PlatformString toUpperCase();

    public abstract PlatformString toLowerCase();

    public abstract int charCodeAt(int index);

    @JSProperty
    public abstract int getLength();

    @JSBody(params = "other", script = "return this + other;")
    public native PlatformString concat(PlatformString other);

    public static PlatformObject getFromPool(int index) {
        return pool.get(index);
    }

    public static void initPool(JSArray<PlatformString> strings) {
        int length = strings.getLength();
        pool = JSArray.create(length);
        for (int i = 0; i < length; ++i) {
            pool.set(i, Platform.getPlatformObject(intern(strings.get(i).asJavaString())));
        }
    }

    public static String intern(String str) {
        String result = internMap.get(str);
        if (result == null) {
            result = str;
            internMap.put(str, str);
        }
        return result;
    }

    @JSBody(params = "charCode", script = "return String.fromCharCode(charCode)")
    public static native PlatformString fromCharCode(int charCode);

    public final String asJavaString() {
        char[] array = new char[getLength()];
        for (int i = 0; i < array.length; ++i) {
            array[i] = (char) charCodeAt(i);
        }
        return new String(array);
    }

    public static PlatformString fromJavaString(String javaString) {
        PlatformString result = empty();
        for (int i = 0; i < javaString.length(); ++i) {
            result = result.concat(fromCharCode(javaString.charAt(i)));
        }
        return result;
    }

    @JSBody(params = {}, script = "return \"\";")
    private static native PlatformString empty();
}
