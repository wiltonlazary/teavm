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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingManager;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.backend.javascript.spi.AbstractRendererListener;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MemberReader;
import org.teavm.model.MethodReader;
import org.teavm.vm.BuildTarget;

public class JsInteropPostProcessor extends AbstractRendererListener {
    private RenderingManager manager;
    private JsInteropConversion conversion;

    @Override
    public void begin(RenderingManager manager, BuildTarget buildTarget) throws IOException {
        this.manager = manager;
        conversion = new JsInteropConversion(manager.getWriter());
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

        MethodReader constructor = findConstructor(cls);
        if (constructor != null) {
            renderConstructor(writer, constructor);
            writer.append(";").softNewLine();
            writer.append(fqn).append(".prototype").ws().append("=").ws()
                    .appendClass(cls.getName()).append(".prototype;").newLine();
        } else {
            writer.appendClass(cls.getName()).append(";").newLine();
        }

        boolean autoExport = JsInteropUtil.isJsType(cls);
        for (MethodReader method : cls.getMethods()) {
            if (autoExport) {
                if (!JsInteropUtil.isAutoJsMember(method)) {
                    continue;
                }
            } else {
                if (!JsInteropUtil.isJsMethod(method)) {
                    continue;
                }
            }

            renderTarget(writer, method, cls, fqn);
            writer.append(".").append(JsInteropUtil.getJsMethodName(method)).ws().append("=").ws();
            renderMethod(writer, method);
        }

        for (FieldReader field : cls.getFields()) {
            if (autoExport) {
                if (!JsInteropUtil.isAutoJsMember(field)) {
                    continue;
                }
            } else {
                if (!JsInteropUtil.isJsField(field)) {
                    continue;
                }
            }

            writer.append("Object.defineProperty(");
            renderTarget(writer, field, cls, fqn);
            String fieldName = JsInteropUtil.getJsFieldName(field);
            writer.append(",").ws().append("\"").append(RenderingUtil.escapeString(fieldName)).append("\"");
            writer.append(",").ws();
            renderField(writer, field);
            writer.append(");").softNewLine();
        }
    }

    private void renderTarget(SourceWriter writer, MemberReader member, ClassReader cls, String fqn)
            throws IOException {
        if (!member.hasModifier(ElementModifier.STATIC)) {
            writer.appendClass(cls.getName()).append(".prototype");
        } else {
            writer.append(fqn);
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
            conversion.convertToJava("p" + i, method.parameterType(i));
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
        if (!JsInteropConversion.shouldWrap(method)) {
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
                conversion.convertToJava("p" + i, method.parameterType(i));
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
            conversion.convertToJS("result", method.getResultType());

            writer.append("return result").softNewLine();
            writer.outdent().append("}");
        }
        writer.append(";").newLine();
    }

    private void renderField(SourceWriter writer, FieldReader field) throws IOException {
        writer.append("{").indent().softNewLine();

        writer.append("get:").ws().append("function()").ws().append("{").indent().softNewLine();
        if (field.hasModifier(ElementModifier.STATIC)) {
            writer.appendClass(field.getOwnerName()).append("_$callClinit();").softNewLine();
            writer.append("var result").ws().append("=").ws()
                    .append(writer.getNaming().getFullNameFor(field.getReference()))
                    .append(";").softNewLine();
        } else {
            writer.append("var result").ws().append("=").ws().append("this.")
                    .append(writer.getNaming().getNameFor(field.getReference()))
                    .append(";").softNewLine();
        }
        conversion.convertToJS("result", field.getType());
        writer.append("return result;").softNewLine();
        writer.outdent().append("}");

        if (!field.hasModifier(ElementModifier.FINAL)) {
            writer.append(",").softNewLine();
            writer.append("set:").ws().append("function(value)").ws().append("{").indent().softNewLine();
            conversion.convertToJava("value", field.getType());
            if (field.hasModifier(ElementModifier.STATIC)) {
                writer.appendClass(field.getOwnerName()).append("_$callClinit();").softNewLine();
                writer.append(writer.getNaming().getFullNameFor(field.getReference())).ws().append('=').ws()
                        .append("value;").softNewLine();
            } else {
                writer.append("this.").append(writer.getNaming().getNameFor(field.getReference())).ws()
                        .append('=').ws().append("value;").softNewLine();
            }
            writer.outdent().append("}");
        }

        writer.softNewLine().outdent().append("}");
    }

    static class JsPackage {
        String name;
        Map<String, JsPackage> subpackages = new LinkedHashMap<>();

        public JsPackage(String name) {
            this.name = name;
        }
    }
}
