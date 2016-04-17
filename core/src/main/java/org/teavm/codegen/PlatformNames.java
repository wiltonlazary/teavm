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
package org.teavm.codegen;

import static org.teavm.model.ValueType.object;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public final class PlatformNames {
    public static final String RUNTIME = "org.teavm.platform.Runtime";
    public static final String OBJECT = "org.teavm.platform.PlatformObject";
    public static final String CLASS = "org.teavm.platform.PlatformClass";
    public static final String EXCEPTION = "org.teavm.platform.PlatformException";

    public static final MethodReference RUNTIME_COMPARE = new MethodReference(RUNTIME, "compare", ValueType.DOUBLE,
            ValueType.DOUBLE, ValueType.INTEGER);
    public static final MethodReference RUNTIME_IS_INSTANCE = new MethodReference(RUNTIME, "isInstance",
            object(OBJECT), object(CLASS), ValueType.BOOLEAN);
    public static final MethodReference RUNTIME_CREATE_BOOLEAN_ARRAY = new MethodReference(RUNTIME,
            "createBooleanArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_BYTE_ARRAY = new MethodReference(RUNTIME,
            "createByteArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_SHORT_ARRAY = new MethodReference(RUNTIME,
            "createShortArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_CHAR_ARRAY = new MethodReference(RUNTIME,
            "createCharArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_INT_ARRAY = new MethodReference(RUNTIME,
            "createIntArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_LONG_ARRAY = new MethodReference(RUNTIME,
            "createLongArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_FLOAT_ARRAY = new MethodReference(RUNTIME,
            "createFloatArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_DOUBLE_ARRAY = new MethodReference(RUNTIME,
            "createDoubleArray", ValueType.INTEGER, object(OBJECT));
    public static final MethodReference RUNTIME_CREATE_ARRAY = new MethodReference(RUNTIME,
            "createArray", object(CLASS), ValueType.INTEGER, object(OBJECT));

    public static final FieldReference CLASS_VOID = new FieldReference(CLASS, "VOID");
    public static final FieldReference CLASS_BOOLEAN = new FieldReference(CLASS, "BOOLEAN");
    public static final FieldReference CLASS_BYTE = new FieldReference(CLASS, "BYTE");
    public static final FieldReference CLASS_SHORT = new FieldReference(CLASS, "SHORT");
    public static final FieldReference CLASS_CHAR = new FieldReference(CLASS, "CHAR");
    public static final FieldReference CLASS_INT = new FieldReference(CLASS, "INT");
    public static final FieldReference CLASS_LONG = new FieldReference(CLASS, "LONG");
    public static final FieldReference CLASS_FLOAT = new FieldReference(CLASS, "FLOAT");
    public static final FieldReference CLASS_DOUBLE = new FieldReference(CLASS, "DOUBLE");

    public static final MethodReference EXCEPTION_RAISE = new MethodReference(EXCEPTION, "raise", object(OBJECT),
            ValueType.VOID);

    private PlatformNames() {
    }
}
