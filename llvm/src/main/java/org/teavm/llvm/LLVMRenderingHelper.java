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
package org.teavm.llvm;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.PrimitiveType;
import org.teavm.model.ValueType;
import org.teavm.model.util.VariableType;

public final class LLVMRenderingHelper {
    private LLVMRenderingHelper() {

    }

    public static String mangleMethod(MethodReference method) {
        StringBuilder sb = new StringBuilder("method$" + method.getClassName() + ".");
        String name = mangleString(method.getName());
        sb.append(mangleType(method.getReturnType()));
        sb.append(name.length() + "_" + name);
        sb.append(Arrays.stream(method.getParameterTypes())
                .map(LLVMRenderingHelper::mangleType)
                .collect(Collectors.joining()));
        return sb.toString();
    }

    public static String mangleField(FieldReference field) {
        StringBuilder sb = new StringBuilder("field$" + field.getClassName() + ".");
        String name = mangleString(field.getFieldName());
        sb.append(name.length() + "_" + name);
        return sb.toString();
    }

    private static String mangleString(String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            switch (c) {
                case '$':
                case '.':
                case '-':
                    sb.append(c);
                    break;
                case '_':
                    sb.append("__");
                    break;
                default:
                    if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                        sb.append(c);
                    } else {
                        sb.append('_')
                                .append(Character.forDigit(c >>> 12, 16))
                                .append(Character.forDigit((c >>> 8) & 0xF, 16))
                                .append(Character.forDigit((c >>> 4) & 0xF, 16))
                                .append(Character.forDigit(c & 0xF, 16));
                    }
                    break;
            }
        }
        return sb.toString();
    }

    public static String mangleType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "Z";
                case BYTE:
                    return "B";
                case SHORT:
                    return "S";
                case CHARACTER:
                    return "C";
                case INTEGER:
                    return "I";
                case LONG:
                    return "L";
                case FLOAT:
                    return "F";
                case DOUBLE:
                    return "D";
            }
        } else if (type instanceof ValueType.Void) {
            return "V";
        } else if (type instanceof ValueType.Array) {
            return "A" + mangleType(((ValueType.Array) type).getItemType());
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            return "L" + className.length() + "_" + className;
        }
        throw new IllegalArgumentException("Don't know how to mangle " + type);
    }

    public static String defaultValue(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            return "0";
        }
        return "null";
    }

    public static String getJavaTypeName(PrimitiveType type) {
        switch (type) {
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case SHORT:
                return "short";
            case CHARACTER:
                return "char";
            case INTEGER:
                return "int";
            case LONG:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            default:
                throw new IllegalArgumentException("Unknown primitive type: " + type);
        }
    }

    public static String methodType(MethodDescriptor method) {
        StringBuilder sb = new StringBuilder();
        sb.append(renderType(method.getResultType())).append(" (i8*");
        for (int i = 0; i < method.parameterCount(); ++i) {
            sb.append(", ").append(renderType(method.parameterType(i)));
        }
        sb.append(")*");
        return sb.toString();
    }

    public static String renderType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "i32";
                case BYTE:
                    return "i32";
                case SHORT:
                case CHARACTER:
                    return "i32";
                case INTEGER:
                    return "i32";
                case LONG:
                    return "i64";
                case FLOAT:
                    return "float";
                case DOUBLE:
                    return "double";
            }
        } else if (type instanceof ValueType.Array) {
            return "i8*";
        } else if (type instanceof ValueType.Void) {
            return "void";
        } else if (type instanceof ValueType.Object) {
            return "i8*";
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    public static String renderType(VariableType type) {
        switch (type) {
            case INT:
                return "i32";
            case LONG:
                return "i64";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case BYTE_ARRAY:
            case CHAR_ARRAY:
            case SHORT_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
            case OBJECT_ARRAY:
            case OBJECT:
                return "i8*";
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    public static String renderItemType(VariableType type) {
        switch (type) {
            case BYTE_ARRAY:
                return "i8";
            case SHORT_ARRAY:
            case CHAR_ARRAY:
                return "i16";
            case INT_ARRAY:
                return "i32";
            case LONG_ARRAY:
                return "i64";
            case FLOAT_ARRAY:
                return "float";
            case DOUBLE_ARRAY:
                return "double";
            case OBJECT_ARRAY:
                return "i8*";
            default:
                throw new IllegalArgumentException("Not an array type: " + type);
        }
    }
}
