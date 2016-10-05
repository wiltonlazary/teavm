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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingManager;
import org.teavm.backend.javascript.spi.AbstractRendererListener;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.vm.BuildTarget;

public class JsInteropPostProcessor extends AbstractRendererListener {
    private RenderingManager manager;

    @Override
    public void begin(RenderingManager manager, BuildTarget buildTarget) throws IOException {
        this.manager = manager;
    }

    @Override
    public void complete() throws IOException {
        SourceWriter writer = manager.getWriter();

        List<String> jsClasses = getJsClasses();
        JsPackage rootPackage = buildRootPackage(jsClasses);
        for (JsPackage topLevelPackage : rootPackage.subpackages.values()) {
            renderPackage(writer, topLevelPackage, "");
        }
        writer.newLine();
        for (String jsClass : jsClasses) {
            ClassReader cls = manager.getClassSource().get(jsClass);
            renderClassWrappers(writer, cls);
        }
        writer.newLine();
    }

    private List<String> getJsClasses() {
        List<String> jsClasses = new ArrayList<>();
        for (String className : manager.getClassSource().getClassNames()) {
            ClassReader cls = manager.getClassSource().get(className);
            if (JsInteropUtil.isExportedToJs(cls)) {
                jsClasses.add(className);
            }
        }
        return jsClasses;
    }

    private JsPackage buildRootPackage(List<String> jsClasses) {
        JsPackage jsPackage = new JsPackage(null);
        jsClasses.forEach(jsClass -> addClassToPackage(jsPackage, jsClass));
        return jsPackage;
    }

    private void addClassToPackage(JsPackage jsPackage, String className) {
        ClassReader cls = manager.getClassSource().get(className);
        if (cls == null) {
            return;
        }

        String packageName = JsInteropUtil.getJsPackageName(cls);

        int index = 0;
        while (index < packageName.length()) {
            int next = packageName.indexOf('.', index);
            if (next < 0) {
                next = packageName.length();
            }
            String packagePart = packageName.substring(index, next);
            jsPackage = jsPackage.subpackages.computeIfAbsent(packagePart, JsPackage::new);
            index = next + 1;
        }
    }

    private void renderPackage(SourceWriter writer, JsPackage jsPackage, String prefix) throws IOException {
        if (prefix.isEmpty()) {
            writer.append("var " + jsPackage.name).ws().append("=").ws().append(jsPackage.name)
                    .ws().append("||").ws().append("{};").softNewLine();
            prefix = jsPackage.name;
        } else {
            String fqn = prefix + "." + jsPackage.name;
            writer.append(fqn).ws().append("=").ws().append(fqn).ws().append("||").ws().append("{};").softNewLine();
            prefix = fqn;
        }
        for (JsPackage subpackage : jsPackage.subpackages.values()) {
            renderPackage(writer, subpackage, prefix);
        }
    }

    private void renderClassWrappers(SourceWriter writer, ClassReader cls) throws IOException {
        String packageName = JsInteropUtil.getJsPackageName(cls);
        String className = JsInteropUtil.getJsClassName(cls);
        String fqn = !packageName.isEmpty() ? packageName + "." + className : className;

        writer.append(fqn).ws().append("=").ws();

        MethodReader contructor = findConstructor(cls);
        if (contructor != null) {
            renderConstructor(writer, contructor);
            writer.append(";").softNewLine();
            writer.append(fqn).append(".prototype").ws().append("=").ws()
                    .appendClass(cls.getName()).append(".prototype;").newLine();
        } else {
            writer.appendClass(cls.getName()).append(";").newLine();
        }

        for (MethodReader method : cls.getMethods()) {
            if (!JsInteropUtil.isAutoJsMember(method)) {
                continue;
            }
            if (!method.hasModifier(ElementModifier.STATIC)) {
                writer.appendClass(cls.getName()).append(".").append("prototype.");
            } else {
                writer.append(fqn).append(".");
            }
            writer.append(JsInteropUtil.getJsMethodName(method)).ws().append("=").ws();
            renderMethod(writer, method);
        }
    }

    private MethodReader findConstructor(ClassReader cls) {
        for (MethodReader method : cls.getMethods()) {
            if (method.getName().equals("<init>")) {
                return method;
            }
        }
        return null;
    }

    private void renderConstructor(SourceWriter writer, MethodReader method) throws IOException {
        writer.append("function(");
        for (int i = 0; i < method.parameterCount(); ++i) {
            if (i > 0) {
                writer.append(",").ws();
            }
            writer.append("p" + i);
        }
        writer.append(")").ws().append("{").indent().softNewLine();

        for (int i = 0; i < method.parameterCount(); ++i) {
            convertToJava(writer, "p" + i, method.parameterType(i));
        }

        writer.appendClass(method.getOwnerName()).append(".call(this);").softNewLine();
        writer.appendMethodBody(method.getReference()).append("(this");
        for (int i = 0; i < method.parameterCount(); ++i) {
            writer.append(',').ws();
            writer.append("p" + i);
        }
        writer.append(");").softNewLine();

        writer.outdent().append("}");
    }

    private void renderMethod(SourceWriter writer, MethodReader method) throws IOException {
        if (!shouldWrap(method)) {
            if (method.hasModifier(ElementModifier.STATIC)) {
                writer.appendMethodBody(method.getReference());
            } else {
                writer.appendClass(method.getOwnerName()).append(".prototype.").appendMethod(method.getDescriptor());
            }
        } else {
            writer.append("function(");
            for (int i = 0; i < method.parameterCount(); ++i) {
                if (i > 0) {
                    writer.append(",").ws();
                }
                writer.append("p" + i);
            }
            writer.append(")").ws().append("{").indent().softNewLine();

            List<String> arguments = new ArrayList<>();
            if (!method.hasModifier(ElementModifier.STATIC)) {
                arguments.add("this");
            }
            for (int i = 0; i < method.parameterCount(); ++i) {
                convertToJava(writer, "p" + i, method.parameterType(i));
                arguments.add("p" + i);
            }

            writer.append("var result").ws().append("=").ws().appendMethodBody(method.getReference()).append("(");
            for (int i = 0; i < arguments.size(); ++i) {
                if (i > 0) {
                    writer.append(',').ws();
                }
                writer.append(arguments.get(i));
            }
            writer.append(");").softNewLine();
            convertToJS(writer, "result", method.getResultType());

            writer.append("return result").softNewLine();
            writer.outdent().append("}");
        }
        writer.append(";").newLine();
    }

    private void convertToJava(SourceWriter writer, String varName, ValueType type) throws IOException {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.Boolean":
                    convertToJavaWrapper(writer, varName, boolean.class, Boolean.class, () -> {
                        writer.append("(");
                        convertBooleanToJava(writer, varName);
                        writer.append(")");
                    });
                    break;
                case "java.lang.Byte":
                    convertToJavaWrapper(writer, varName, byte.class, Byte.class, () -> writer.append(varName));
                    break;
                case "java.lang.Short":
                    convertToJavaWrapper(writer, varName, short.class, Short.class, () -> writer.append(varName));
                    break;
                case "java.lang.Character":
                    convertToJavaWrapper(writer, varName, char.class, Character.class, () -> writer.append(varName));
                    break;
                case "java.lang.Integer":
                    convertToJavaWrapper(writer, varName, int.class, Integer.class, () -> writer.append(varName));
                    break;
                case "java.lang.Float":
                    convertToJavaWrapper(writer, varName, float.class, Float.class, () -> writer.append(varName));
                    break;
                case "java.lang.Double":
                    convertToJavaWrapper(writer, varName, double.class, Double.class, () -> writer.append(varName));
                    break;
                case "java.lang.String":
                    convertNullableToJava(writer, varName, () -> writer.append("$rt_str(" + varName + ")"));
                    break;
            }
        } else if (type == ValueType.BOOLEAN) {
            writer.append(varName).ws().append('=').ws();
            convertBooleanToJava(writer, varName);
            writer.append(';').softNewLine();
        }
    }

    private void convertToJS(SourceWriter writer, String varName, ValueType type) throws IOException {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.Boolean":
                    convertWrapperToJS(writer, varName, boolean.class, Boolean.class,
                            () -> convertBooleanToJS(writer, varName));
                    break;
                case "java.lang.Byte":
                    convertWrapperToJS(writer, varName, byte.class, Byte.class, () -> writer.append(varName));
                    break;
                case "java.lang.Short":
                    convertWrapperToJS(writer, varName, short.class, Short.class, () -> writer.append(varName));
                    break;
                case "java.lang.Character":
                    convertWrapperToJS(writer, varName, char.class, Character.class, () -> writer.append(varName));
                    break;
                case "java.lang.Integer":
                    convertWrapperToJS(writer, varName, int.class, Integer.class, () -> writer.append(varName));
                    break;
                case "java.lang.Float":
                    convertWrapperToJS(writer, varName, float.class, Float.class, () -> writer.append(varName));
                    break;
                case "java.lang.Double":
                    convertWrapperToJS(writer, varName, double.class, Double.class, () -> writer.append(varName));
                    break;
                case "java.lang.String":
                    convertNullableToJS(writer, varName, () -> writer.append("$rt_ustr(" + varName + ")"));
                    break;
            }
        }
    }

    private void convertToJavaWrapper(SourceWriter writer, String varName, Class<?> primitiveClass,
            Class<?> wrapperClass, Fragment inner) throws IOException {
        convertNullableToJava(writer, varName, () -> {
            MethodReference wrapperMethod = new MethodReference(wrapperClass, "valueOf", primitiveClass, wrapperClass);
            writer.appendMethodBody(wrapperMethod).append("(");
            inner.emit();
            writer.append(")");
        });
    }

    private void convertBooleanToJava(SourceWriter writer, String varName) throws IOException {
        writer.append(varName).ws().append('?').ws().append('1').ws().append(':').ws().append('0');
    }

    private void convertNullableToJava(SourceWriter writer, String varName, Fragment fragment) throws IOException {
        writer.append(varName).ws().append('=').ws()
                .append(varName).ws().append("!=").ws().append("null").ws().append("?").ws();
        fragment.emit();
        writer.ws().append(":").ws().append("null;").softNewLine();
    }

    private void convertWrapperToJS(SourceWriter writer, String varName, Class<?> primitiveClass,
            Class<?> wrapperClass, Fragment inner) throws IOException {
        convertNullableToJS(writer, varName, () -> {
            MethodReference wrapperMethod = new MethodReference(wrapperClass, primitiveClass.getName() + "Value",
                    primitiveClass);
            writer.appendMethodBody(wrapperMethod).append("(");
            inner.emit();
            writer.append(")");
        });
    }

    private void convertNullableToJS(SourceWriter writer, String varName, Fragment fragment) throws IOException {
        writer.append(varName).ws().append('=').ws()
                .append(varName).ws().append("!==").ws().append("null").ws().append("?").ws();
        fragment.emit();
        writer.ws().append(":").ws().append("null;").softNewLine();
    }

    private void convertBooleanToJS(SourceWriter writer, String varName) throws IOException {
        writer.append(varName).ws().append("!==").ws().append('0');
    }

    private interface Fragment {
        void emit() throws IOException;
    }

    private boolean shouldWrap(MethodReader method) {
        return Arrays.stream(method.getSignature()).anyMatch(this::shouldWrap);
    }

    private boolean shouldWrap(ValueType type) {
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

    static class JsPackage {
        String name;
        Map<String, JsPackage> subpackages = new LinkedHashMap<>();

        public JsPackage(String name) {
            this.name = name;
        }
    }
}
