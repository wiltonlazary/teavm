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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;
import javafx.scene.web.WebEngine;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSBoolean;
import org.teavm.jso.core.JSNumber;
import org.teavm.jso.core.JSString;

/**
 *
 * @author Alexey Andreev
 */
final class JS {
    private static WebEngine webEngine;
    private static netscape.javascript.JSObject accessor;

    private JS() {
    }

    static WebEngine getWebEngine() {
        return webEngine;
    }

    public static netscape.javascript.JSObject getAccessor() {
        return accessor;
    }

    public static void setWebEngine(WebEngine webEngine) {
        if (JS.webEngine == webEngine) {
            return;
        }
        webEngine.executeScript(""
                + "$$JSO_ACCESS$$ = {"
                    + "unmarshallPrimitive : function(value) {"
                        + "return { value : value };"
                    + "},"
                    + "unmarshallUndefined : function(value) {"
                        + "return { value : undefined };"
                    + "},"
                    + "invoke : function(wrapper, method) {"
                        + "var args = Array.prototype.slice.call(arguments, 2);"
                        + "return wrapper.value.prototype[method].apply(wrapper.value, args);"
                    + "}"
                + "};");
        accessor = (netscape.javascript.JSObject) webEngine.executeScript("return $$JSO_ACCESS$$;");
    }

    public static JSObject marshall(byte value) {
        return new JSNumberValue(value);
    }

    public static JSObject marshall(short value) {
        return new JSNumberValue(value);
    }

    public static JSObject marshall(int value) {
        return new JSNumberValue(value);
    }

    public static JSObject marshall(char value) {
        return new JSNumberValue(value);
    }

    public static JSObject marshall(float value) {
        return new JSNumberValue(value);
    }

    public static JSObject marshall(double value) {
        return new JSNumberValue(value);
    }

    public static JSObject marshall(boolean value) {
        return new JSBooleanValue(value);
    }

    public static JSObject marshall(String value) {
        return new JSStringValue(value);
    }

    public static byte unmarshallByte(JSObject value) {
        return ((JSNumberValue) value).getValue().byteValue();
    }

    public static char unmarshallCharacter(JSObject value) {
        return (char) ((JSNumberValue) value).getValue().intValue();
    }

    public static short unmarshallShort(JSObject value) {
        return ((JSNumberValue) value).getValue().shortValue();
    }

    public static int unmarshallInt(JSObject value) {
        return ((JSNumberValue) value).getValue().intValue();
    }

    public static float unmarshallFloat(JSObject value) {
        return ((JSNumberValue) value).getValue().floatValue();
    }

    public static double unmarshallDouble(JSObject value) {
        return ((JSNumberValue) value).getValue().doubleValue();
    }

    public static boolean unmarshallBoolean(JSObject value) {
        return ((JSBooleanValue) value).getValue();
    }

    public static String unmarshallString(JSObject value) {
        return ((JSStringValue) value).getValue();
    }

    public static <T extends JSObject> JSArray<T> marshall(T[] array) {
        JSArray<T> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, array[i]);
        }
        return result;
    }

    public static <T extends JSObject> Function<T[], JSArray<T>> arrayMarshaller() {
        return JS::marshall;
    }

    public static <T extends JSObject, S> JSArray<T> map(S[] array, Function<S, T> f) {
        JSArray<T> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, f.apply(array[i]));
        }
        return result;
    }

    public static <T extends JSObject, S> Function<S[], JSArray<T>> arrayMapper(Function<S, T> f) {
        return array -> map(array, f);
    }

    public static JSArray<JSBoolean> marshall(boolean[] array) {
        JSArray<JSBoolean> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSBoolean.valueOf(array[i]));
        }
        return result;
    }

    public static Function<boolean[], JSArray<JSBoolean>> booleanArrayMarshaller() {
        return JS::marshall;
    }

    public static JSArray<JSNumber> marshall(byte[] array) {
        JSArray<JSNumber> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSNumber.valueOf(array[i]));
        }
        return result;
    }

    public static Function<byte[], JSArray<JSNumber>> byteArrayMarshaller() {
        return JS::marshall;
    }

    public static JSArray<JSNumber> marshall(short[] array) {
        JSArray<JSNumber> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSNumber.valueOf(array[i]));
        }
        return result;
    }

    public static Function<short[], JSArray<JSNumber>> shortArrayMarshaller() {
        return JS::marshall;
    }

    public static JSArray<JSNumber> marshall(char[] array) {
        JSArray<JSNumber> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSNumber.valueOf(array[i]));
        }
        return result;
    }

    public static Function<char[], JSArray<JSNumber>> charArrayMarshaller() {
        return JS::marshall;
    }

    public static JSArray<JSNumber> marshall(int[] array) {
        JSArray<JSNumber> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSNumber.valueOf(array[i]));
        }
        return result;
    }

    public static Function<int[], JSArray<JSNumber>> intArrayMashaller() {
        return JS::marshall;
    }

    public static JSArray<JSString> marshall(String[] array) {
        JSArray<JSString> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSString.valueOf(array[i]));
        }
        return result;
    }

    public static Function<String[], JSArray<JSString>> stringArrayMarshaller() {
        return JS::marshall;
    }

    public static JSArray<JSNumber> marshall(float[] array) {
        JSArray<JSNumber> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSNumber.valueOf(array[i]));
        }
        return result;
    }

    public static Function<float[], JSArray<JSNumber>> floatArrayMarshaller() {
        return JS::marshall;
    }

    public static JSArray<JSNumber> marshall(double[] array) {
        JSArray<JSNumber> result = JSArray.create(array.length);
        for (int i = 0; i < array.length; ++i) {
            result.set(i, JSNumber.valueOf(array[i]));
        }
        return result;
    }

    public static Function<double[], JSArray<JSNumber>> doubleArrayMarshaller() {
        return JS::marshall;
    }

    public static <T extends JSObject> T[] unmarshallArray(Class<T> type, JSArrayReader<T> array) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(type, array.getLength());
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i);
        }
        return result;
    }

    public static <T extends JSObject> Function<JSArrayReader<T>, T[]> arrayUnmarshaller(Class<T> type) {
        return array -> unmarshallArray(type, array);
    }

    public static <S extends JSObject, T> T[] unmapArray(Class<T> type, JSArrayReader<S> array, Function<S, T> f) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(type, array.getLength());
        for (int i = 0; i < result.length; ++i) {
            result[i] = f.apply(array.get(i));
        }
        return result;
    }

    public static <T, S extends JSObject> Function<JSArray<S>, T[]> arrayUnmapper(Class<T> type, Function<S, T> f) {
        return array -> unmapArray(type, array, f);
    }

    public static boolean[] unmarshallBooleanArray(JSArrayReader<JSBoolean> array) {
        boolean[] result = new boolean[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).booleanValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSBoolean>, boolean[]> booleanArrayUnmarshaller() {
        return JS::unmarshallBooleanArray;
    }

    public static byte[] unmarshallByteArray(JSArrayReader<JSNumber> array) {
        byte[] result = new byte[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).byteValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSNumber>, byte[]> byteArrayUnmarshaller() {
        return JS::unmarshallByteArray;
    }

    public static short[] unmarshallShortArray(JSArrayReader<JSNumber> array) {
        short[] result = new short[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).shortValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSNumber>, short[]> shortArrayUnmarshaller() {
        return JS::unmarshallShortArray;
    }

    public static int[] unmarshallIntArray(JSArrayReader<JSNumber> array) {
        int[] result = new int[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).intValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSNumber>, int[]> intArrayUnmarshaller() {
        return JS::unmarshallIntArray;
    }

    public static char[] unmarshallCharArray(JSArrayReader<JSNumber> array) {
        char[] result = new char[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).charValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSNumber>, char[]> charArrayUnmarshaller() {
        return JS::unmarshallCharArray;
    }

    public static float[] unmarshallFloatArray(JSArrayReader<JSNumber> array) {
        float[] result = new float[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).floatValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSNumber>, float[]> floatArrayUnmarshaller() {
        return JS::unmarshallFloatArray;
    }

    public static double[] unmarshallDoubleArray(JSArrayReader<JSNumber> array) {
        double[] result = new double[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).doubleValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSNumber>, double[]> doubleArrayUnmarshaller() {
        return JS::unmarshallDoubleArray;
    }

    public static String[] unmarshallStringArray(JSArrayReader<JSString> array) {
        String[] result = new String[array.getLength()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = array.get(i).stringValue();
        }
        return result;
    }

    public static Function<JSArrayReader<JSString>, String[]> stringArrayUnmarshaller() {
        return JS::unmarshallStringArray;
    }

    public static JSObject invoke(JSObject instance, JSObject method) {
        return invokeImpl(instance, method);
    }

    public static JSObject invoke(JSObject instance, JSObject method, JSObject a) {
        return invokeImpl(instance, method, a);
    }

    public static JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b) {
        return invokeImpl(instance, method, a, b);
    }

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g, JSObject h);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g, JSObject h, JSObject i);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g, JSObject h, JSObject i, JSObject j);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g, JSObject h, JSObject i, JSObject j, JSObject k);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g, JSObject h, JSObject i, JSObject j, JSObject k,
            JSObject l);

    public static native JSObject invoke(JSObject instance, JSObject method, JSObject a, JSObject b, JSObject c,
            JSObject d, JSObject e, JSObject f, JSObject g, JSObject h, JSObject i, JSObject j, JSObject k,
            JSObject l, JSObject m);

    private static JSObject invokeImpl(JSObject instance, JSObject method, JSObject... parameters) {
        JSValue instanceValue = (JSValue) instance;
        netscape.javascript.JSObject target = instanceValue.asJSObject();
        String methodName = unmarshallString(method);
        Object[] params = Arrays.stream(parameters).map(o -> (JSValue) o).map(JSValue::asObject).toArray();
        if (instanceValue.isWrapped()) {
            Object[] invokeParams = new Object[params.length + 1];
            System.arraycopy(params, 0, invokeParams, 2, params.length);
            invokeParams[0] = target;
            invokeParams[1] = methodName;
            return JSValue.from(accessor.call("invoke", invokeParams));
        } else {
            return JSValue.from(target.call(methodName, params));
        }
    }
}
