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
class JSStringValue extends JSValue {
    private String value;

    public JSStringValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public JSValueType getType() {
        return JSValueType.STRING;
    }

    @Override
    public JSObject asJSObject() {
        return (JSObject) JS.getAccessor().call("unmarshallPrimitive", value);
    }

    @Override
    public boolean isWrapped() {
        return true;
    }

    @Override
    public Object asObject() {
        return value;
    }
}
