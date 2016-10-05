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
package org.teavm.jsinterop;

import java.io.IOException;
import java.util.Arrays;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class JsInteropConversion {
    private SourceWriter writer;

    public JsInteropConversion(SourceWriter writer) {
        this.writer = writer;
    }

    public void convertToJava(String varName, ValueType type) throws IOException {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.Boolean":
                    convertToJavaWrapper(varName, boolean.class, Boolean.class, () -> {
                        writer.append("(");
                        convertBooleanToJava(varName);
                        writer.append(")");
                    });
                    break;
                case "java.lang.Byte":
                    convertToJavaWrapper(varName, byte.class, Byte.class, () -> writer.append(varName));
                    break;
                case "java.lang.Short":
                    convertToJavaWrapper(varName, short.class, Short.class, () -> writer.append(varName));
                    break;
                case "java.lang.Character":
                    convertToJavaWrapper(varName, char.class, Character.class, () -> writer.append(varName));
                    break;
                case "java.lang.Integer":
                    convertToJavaWrapper(varName, int.class, Integer.class, () -> writer.append(varName));
                    break;
                case "java.lang.Float":
                    convertToJavaWrapper(varName, float.class, Float.class, () -> writer.append(varName));
                    break;
                case "java.lang.Double":
                    convertToJavaWrapper(varName, double.class, Double.class, () -> writer.append(varName));
                    break;
                case "java.lang.String":
                    convertNullableToJava(varName, () -> writer.append("$rt_str(" + varName + ")"));
                    break;
            }
        } else if (type == ValueType.BOOLEAN) {
            writer.append(varName).ws().append('=').ws();
            convertBooleanToJava(varName);
            writer.append(';').softNewLine();
        }
    }

    public void convertToJS(String varName, ValueType type) throws IOException {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.Boolean":
                    convertWrapperToJS(varName, boolean.class, Boolean.class, () -> convertBooleanToJS(varName));
                    break;
                case "java.lang.Byte":
                    convertWrapperToJS(varName, byte.class, Byte.class, () -> writer.append(varName));
                    break;
                case "java.lang.Short":
                    convertWrapperToJS(varName, short.class, Short.class, () -> writer.append(varName));
                    break;
                case "java.lang.Character":
                    convertWrapperToJS(varName, char.class, Character.class, () -> writer.append(varName));
                    break;
                case "java.lang.Integer":
                    convertWrapperToJS(varName, int.class, Integer.class, () -> writer.append(varName));
                    break;
                case "java.lang.Float":
                    convertWrapperToJS(varName, float.class, Float.class, () -> writer.append(varName));
                    break;
                case "java.lang.Double":
                    convertWrapperToJS(varName, double.class, Double.class, () -> writer.append(varName));
                    break;
                case "java.lang.String":
                    convertNullableToJS(varName, () -> writer.append("$rt_ustr(" + varName + ")"));
                    break;
            }
        }
    }

    private void convertToJavaWrapper(String varName, Class<?> primitiveClass, Class<?> wrapperClass, Fragment inner)
            throws IOException {
        convertNullableToJava(varName, () -> {
            MethodReference wrapperMethod = new MethodReference(wrapperClass, "valueOf", primitiveClass, wrapperClass);
            writer.appendMethodBody(wrapperMethod).append("(");
            inner.emit();
            writer.append(")");
        });
    }

    private void convertBooleanToJava(String varName) throws IOException {
        writer.append(varName).ws().append('?').ws().append('1').ws().append(':').ws().append('0');
    }

    private void convertNullableToJava(String varName, Fragment fragment) throws IOException {
        writer.append(varName).ws().append('=').ws()
                .append(varName).ws().append("!=").ws().append("null").ws().append("?").ws();
        fragment.emit();
        writer.ws().append(":").ws().append("null;").softNewLine();
    }

    private void convertWrapperToJS(String varName, Class<?> primitiveClass, Class<?> wrapperClass, Fragment inner)
            throws IOException {
        convertNullableToJS(varName, () -> {
            MethodReference wrapperMethod = new MethodReference(wrapperClass, primitiveClass.getName() + "Value",
                    primitiveClass);
            writer.appendMethodBody(wrapperMethod).append("(");
            inner.emit();
            writer.append(")");
        });
    }

    private void convertNullableToJS(String varName, Fragment fragment) throws IOException {
        writer.append(varName).ws().append('=').ws()
                .append(varName).ws().append("!==").ws().append("null").ws().append("?").ws();
        fragment.emit();
        writer.ws().append(":").ws().append("null;").softNewLine();
    }

    private void convertBooleanToJS(String varName) throws IOException {
        writer.append(varName).ws().append("!==").ws().append('0');
    }

    private interface Fragment {
        void emit() throws IOException;
    }

    public static boolean shouldWrap(MethodReader method) {
        return Arrays.stream(method.getSignature()).anyMatch(JsInteropConversion::shouldWrap);
    }

    public static boolean shouldWrap(ValueType type) {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.Boolean":
                case "java.lang.Byte":
                case "java.lang.Short":
                case "java.lang.Character":
                case "java.lang.Integer":
                case "java.lang.Float":
                case "java.lang.Double":
                case "java.lang.String":
                    return true;
            }
        } else if (type instanceof ValueType.Array) {
            return true;
        } else if (type == ValueType.BOOLEAN) {
            return true;
        }

        return false;
    }
}
