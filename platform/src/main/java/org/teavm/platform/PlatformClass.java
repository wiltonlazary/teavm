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

import static org.teavm.platform.Platform.getPlatformString;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

public abstract class PlatformClass implements JSObject {
    public static final PlatformClass BOOLEAN = createPrimitiveClass(getPlatformString("boolean"),
            getPlatformString("Z"));
    public static final PlatformClass BYTE = createPrimitiveClass(getPlatformString("byte"),
            getPlatformString("B"));
    public static final PlatformClass SHORT = createPrimitiveClass(getPlatformString("short"),
            getPlatformString("S"));
    public static final PlatformClass CHAR = createPrimitiveClass(getPlatformString("char"), getPlatformString("C"));
    public static final PlatformClass INT = createPrimitiveClass(getPlatformString("int"), getPlatformString("I"));
    public static final PlatformClass LONG = createPrimitiveClass(getPlatformString("long"), getPlatformString("J"));
    public static final PlatformClass FLOAT = createPrimitiveClass(getPlatformString("float"), getPlatformString("F"));
    public static final PlatformClass DOUBLE = createPrimitiveClass(getPlatformString("double"),
            getPlatformString("D"));
    public static final PlatformClass VOID = createPrimitiveClass(getPlatformString("void"), getPlatformString("V"));
    public static final PlatformClass OBJECT = Platform.getPlatformClass(Object.class);

    @JSProperty("$meta")
    public abstract PlatformClassMetadata getMetadata();

    @JSProperty("classObject")
    public abstract void setJavaClass(PlatformObject obj);

    @JSProperty("classObject")
    public abstract PlatformObject getJavaClass();

    @JSBody(params = "cls", script = ""
            + "cls.$meta = {"
            + "    supertypes: [],"
            + "    superclass: null,"
            + "    array: null,"
            + "    item: null,"
            + "    name: null,"
            + "    binaryName: null,"
            + "    enum: false,"
            + "    primitive: false"
            + "};")
    static native void init(PlatformClass cls);

    @JSBody(params = {}, script = "return function() {};")
    private static native PlatformClass createEmpty();

    private static PlatformClass createPrimitiveClass(PlatformString name, PlatformString binaryName) {
        PlatformClass cls = createEmpty();
        init(cls);
        fillPrimitive(cls, name, binaryName);
        return cls;
    }

    @JSBody(params = { "cls", "name", "binaryName" }, script = ""
            + "var meta = cls.$meta;"
            + "meta.primitive = true;"
            + "meta.name = name;\n"
            + "meta.binaryName = binaryName;\n"
            + "meta.enum = false;\n"
            + "meta.item = null;")
    private static native void fillPrimitive(PlatformClass cls, PlatformString name, PlatformString binaryName);

    @JSBody(params = "data", script = "return new this(data);")
    public native PlatformObject newArrayInstance(JSObject data);
}
