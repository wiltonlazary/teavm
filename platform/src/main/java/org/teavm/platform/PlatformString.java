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

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

public abstract class PlatformString implements JSObject {
    public abstract PlatformString toUpperCase();

    public abstract PlatformString toLowerCase();

    public abstract int charCodeAt(int index);

    @JSProperty
    public abstract int getLength();

    public static String asString(PlatformString string) {
        int length = string.getLength();
        char[] data = new char[length];
        for (int i = 0; i < length; ++i) {
            data[i] = (char) string.charCodeAt(i);
        }
        return new String(data);
    }

    @JSBody(params = "other", script = "return this + other;")
    public native PlatformString concat(PlatformString other);
}
