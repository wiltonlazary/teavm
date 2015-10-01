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

import netscape.javascript.JSObject;

/**
 *
 * @author Alexey Andreev
 */
abstract class JSValue implements org.teavm.jso.JSObject {
    public abstract JSValueType getType();

    public abstract JSObject asJSObject();

    public abstract Object asObject();

    public abstract boolean isWrapped();

    public static JSValue from(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean) {
            return new JSBooleanValue((Boolean) value);
        } else if (value instanceof Number) {
            return new JSNumberValue((Number) value);
        } else if (value instanceof String) {
            return new JSStringValue((String) value);
        } else if (value instanceof JSObject) {
            return new JSReference((JSObject) value);
        } else {
            throw new IllegalArgumentException("Can't convert value of type " + value.getClass().getName()
                    + " to JS value");
        }
    }
}
