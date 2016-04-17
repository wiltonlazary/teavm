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
package org.teavm.platform;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSNumber;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Float64Array;
import org.teavm.jso.typedarrays.Int16Array;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint16Array;

public final class Runtime {
    private static int lastObjectId;
    private static final PrimitiveArrayFactory booleanArrayFactory;
    private static final PrimitiveArrayFactory byteArrayFactory;
    private static final PrimitiveArrayFactory charArrayFactory;
    private static final PrimitiveArrayFactory shortArrayFactory;
    private static final PrimitiveArrayFactory intArrayFactory;
    private static final PrimitiveArrayFactory floatArrayFactory;
    private static final PrimitiveArrayFactory doubleArrayFactory;

    private Runtime() {
    }

    static {
        if (typedArraysAvailable()) {
            booleanArrayFactory = Runtime::createBooleanTypedArray;
            byteArrayFactory = Runtime::createByteTypedArray;
            charArrayFactory = Runtime::createCharTypedArray;
            shortArrayFactory = Runtime::createShortTypedArray;
            intArrayFactory = Runtime::createIntTypedArray;
            floatArrayFactory = Runtime::createFloatTypedArray;
            doubleArrayFactory = Runtime::createDoubleTypedArray;
        } else {
            booleanArrayFactory = size -> createNumericUntypedArray(PlatformClass.BOOLEAN, size);
            byteArrayFactory = size -> createNumericUntypedArray(PlatformClass.BYTE, size);
            charArrayFactory = size -> createNumericUntypedArray(PlatformClass.CHAR, size);
            shortArrayFactory = size -> createNumericUntypedArray(PlatformClass.SHORT, size);
            intArrayFactory = size -> createNumericUntypedArray(PlatformClass.INT, size);
            floatArrayFactory = size -> createNumericUntypedArray(PlatformClass.FLOAT, size);
            doubleArrayFactory = size -> createNumericUntypedArray(PlatformClass.DOUBLE, size);
        }
    }

    public static int nextObjectId() {
        return lastObjectId++;
    }

    public static int compare(double a, double b) {
        return a > b ? 1 : a < b ? -1 : 0;
    }

    public static boolean isInstance(PlatformObject obj, PlatformClass cls) {
        return obj != null && isJavaObject(obj) && isAssignable(obj.getPlatformClass(), cls);
    }

    @JSBody(params = "obj", script = "return !!obj.constructor.$meta")
    private static native boolean isJavaObject(PlatformObject obj);

    public static boolean isAssignable(PlatformClass from, PlatformClass to) {
        if (from == to) {
            return true;
        }

        PlatformSequence<PlatformClass> supertypes = from.getMetadata().getSupertypes();
        for (int i = 0; i < supertypes.getLength(); ++i) {
            if (isAssignable(supertypes.get(i), to)) {
                return true;
            }
        }
        return false;
    }

    public static PlatformObject createArray(PlatformClass cls, int sz) {
        JSArray<JSObject> data = JSArray.create(sz);
        PlatformObject arr = wrapArray(cls, data);
        for (int i = 0; i < sz; ++i) {
            data.set(i,  null);
        }
        return arr;
    }

    private static PlatformObject wrapArray(PlatformClass cls, JSArrayReader<? extends JSObject> data) {
        return arrayClass(cls).newArrayInstance(data);
    }

    private static PlatformObject createLongArray(int sz) {
        JSArray<JSObject> data = JSArray.create(sz);
        PlatformObject arr = wrapArray(PlatformClass.LONG, data);
        for (int i = 0; i < sz; ++i) {
            data.set(0, PlatformLong.ZERO);
        }
        return arr;
    }

    public static PlatformClass arrayClass(PlatformClass cls) {
        if (cls.getMetadata().getArray() == null) {
            PlatformClass arrayCls = createArrayClass(newInstance(PlatformClass.OBJECT));
            PlatformClass.init(arrayCls);
            fillArrayClass(arrayCls, cls, PlatformClass.OBJECT);
            cls.getMetadata().setArray(arrayCls);
        }
        return cls.getMetadata().getArray();
    }

    @JSBody(params = "prototype", script = ""
            + "var result = function(data) {"
            + "    this.data = data;"
            + "    this.$id = javaMethods.get('org.teavm.platform.Runtime.nextObjectId()I').invoke();"
            + "};"
            + "result.prototype = prototype;"
            + "prototype.constructor = result;"
            + "return result;")
    private static native PlatformClass createArrayClass(JSObject prototype);

    @JSBody(params = { "arrayCls", "cls", "objCls" }, script = ""
            + "var meta = arrayCls.meta;"
            + "meta.item = cls;"
            + "meta.name = '[' + cls.$meta.binaryName;"
            + "meta.superclass = objCls;"
            + "meta.binaryName = meta.name;")
    private static native void fillArrayClass(PlatformClass arrayCls, PlatformClass cls, PlatformClass objCls);

    @JSBody(params = "constructor", script = "return new constructor();")
    private static native JSObject newInstance(JSObject constructor);

    public static PlatformObject createBooleanArray(int size) {
        return booleanArrayFactory.createArray(size);
    }

    public static PlatformObject createByteArray(int size) {
        return byteArrayFactory.createArray(size);
    }

    public static PlatformObject createCharArray(int size) {
        return charArrayFactory.createArray(size);
    }

    public static PlatformObject createShortArray(int size) {
        return shortArrayFactory.createArray(size);
    }

    public static PlatformObject createIntArray(int size) {
        return intArrayFactory.createArray(size);
    }

    public static PlatformObject createFloatArray(int size) {
        return floatArrayFactory.createArray(size);
    }

    public static PlatformObject createDoubleArray(int size) {
        return doubleArrayFactory.createArray(size);
    }

    private static PlatformObject createBooleanTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.BOOLEAN), Int8Array.create(size));
    }

    private static PlatformObject createCharTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.CHAR), Uint16Array.create(size));
    }

    private static PlatformObject createByteTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.BYTE), Int8Array.create(size));
    }

    private static PlatformObject createShortTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.SHORT), Int16Array.create(size));
    }

    private static PlatformObject createIntTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.INT), Int16Array.create(size));
    }

    private static PlatformObject createFloatTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.FLOAT), Float32Array.create(size));
    }

    private static PlatformObject createDoubleTypedArray(int size) {
        return createNumericTypedArray(arrayClass(PlatformClass.DOUBLE), Float64Array.create(size));
    }

    private static PlatformObject createNumericTypedArray(PlatformClass itemType, JSObject data) {
        return arrayClass(itemType).newArrayInstance(data);
    }

    private static PlatformObject createNumericUntypedArray(PlatformClass itemType, int size) {
        JSArray<JSNumber> data = JSArray.create(size);
        PlatformObject arr = wrapArray(arrayClass(itemType), data);
        for (int i = 0; i < size; ++i) {
            data.set(i, JSNumber.valueOf(0));
        }
        return arr;
    }

    @JSBody(params = {}, script = "return typeof this.ArrayBuffer !== 'undefined';")
    private static native boolean typedArraysAvailable();

    public static PlatformObject nullCheck(PlatformObject obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }

    public static PlatformObject createMultiArray(PlatformClass cls, JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createArray(cls, firstDim));
        }
        return createMultiArrayImpl(cls, arrays, dimensions);
    }

    public static PlatformObject createBooleanMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createByteArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.BYTE, arrays, dimensions);
    }

    public static PlatformObject createByteMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createBooleanArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.BOOLEAN, arrays, dimensions);
    }

    public static PlatformObject createShortMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createShortArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.SHORT, arrays, dimensions);
    }

    public static PlatformObject createCharMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createCharArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.CHAR, arrays, dimensions);
    }

    public static PlatformObject createIntMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createIntArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.INT, arrays, dimensions);
    }

    public static PlatformObject createLongMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createLongArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.LONG, arrays, dimensions);
    }

    public static PlatformObject createFloatMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createFloatArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.FLOAT, arrays, dimensions);
    }

    public static PlatformObject createDoubleMultiArray(JSArray<JSNumber> dimensions) {
        JSArray<PlatformObject> arrays = JSArray.create(primitiveArrayCount(dimensions));
        int firstDim = dimensions.get(0).intValue();
        for (int i = 0; i < arrays.getLength(); ++i) {
            arrays.set(i, createDoubleArray(firstDim));
        }
        return createMultiArrayImpl(PlatformClass.DOUBLE, arrays, dimensions);
    }

    private static PlatformObject createMultiArrayImpl(PlatformClass cls, JSArray<PlatformObject> arrays,
            JSArrayReader<JSNumber> dimensions) {
        int limit = arrays.getLength();
        for (int i = 1; i < dimensions.getLength(); ++i) {
            cls = arrayClass(cls);
            int dim = dimensions.get(i).intValue();
            int index = 0;
            int packedIndex = 0;
            while (index < limit) {
                JSArray<PlatformObject> data = JSArray.create(dim);
                PlatformObject arr = wrapArray(cls, data);
                for (int j = 0; j < dim; ++j) {
                    data.set(j, arrays.get(index++));
                }
                arrays.set(packedIndex++, arr);
            }
            limit = packedIndex;
        }
        return arrays.get(0);
    }

    private static int primitiveArrayCount(JSArrayReader<JSNumber> dimensions) {
        int val = dimensions.get(1).intValue();
        for (int i = 2; i < dimensions.getLength(); ++i) {
            val *= dimensions.get(i).intValue();
        }
        return val;
    }

    private interface PrimitiveArrayFactory {
        PlatformObject createArray(int size);
    }
}
