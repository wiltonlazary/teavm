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
package org.teavm.jso.webview;

import org.teavm.jso.JSObject;

/**
 *
 * @author Alexey Andreev
 */
final class JS {
    private JS() {
    }

    public static JSObject wrap(byte value) {
        return new JSNumber(value);
    }

    public static JSObject wrap(short value) {
        return new JSNumber(value);
    }

    public static JSObject wrap(int value) {
        return new JSNumber(value);
    }

    public static JSObject wrap(char value) {
        return new JSNumber(value);
    }

    public static JSObject wrap(float value) {
        return new JSNumber(value);
    }

    public static JSObject wrap(double value) {
        return new JSNumber(value);
    }

    public static JSObject wrap(boolean value) {
        return new JSBoolean(value);
    }

    public static JSObject wrap(String value) {
        return new JSString(value);
    }

    public static byte unwrapByte(JSObject value) {
        return ((JSNumber) value).getValue().byteValue();
    }

    public static char unwrapCharacter(JSObject value) {
        return (char) ((JSNumber) value).getValue().intValue();
    }

    public static short unwrapShort(JSObject value) {
        return ((JSNumber) value).getValue().shortValue();
    }

    public static int unwrapInt(JSObject value) {
        return ((JSNumber) value).getValue().intValue();
    }

    public static float unwrapFloat(JSObject value) {
        return ((JSNumber) value).getValue().floatValue();
    }

    public static double unwrapDouble(JSObject value) {
        return ((JSNumber) value).getValue().doubleValue();
    }

    public static boolean unwrapBoolean(JSObject value) {
        return ((JSBoolean) value).getValue();
    }

    public static String unwrapString(JSObject value) {
        return ((JSString) value).getValue();
    }
}
