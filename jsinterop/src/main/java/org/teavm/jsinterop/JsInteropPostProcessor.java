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
import org.teavm.model.ClassReader;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.spi.AbstractRendererListener;

public class JsInteropPostProcessor extends AbstractRendererListener {
    private RenderingManager manager;
    private JsInteropContext jsInteropContext;

    public JsInteropPostProcessor(JsInteropContext jsInteropContext) {
        this.jsInteropContext = jsInteropContext;
    }

    @Override
    public void begin(RenderingManager manager, BuildTarget buildTarget) throws IOException {
        this.manager = manager;
    }

    @Override
    public void complete() throws IOException {
        SourceWriter writer = manager.getWriter();

        List<JsClass> jsClasses = getJsClasses();
        JsPackage rootPackage = buildRootPackage(jsClasses);
        for (JsPackage topLevelPackage : rootPackage.subpackages.values()) {
            renderPackage(writer, topLevelPackage, "");
        }
        writer.newLine();
        for (JsClass jsClass : jsClasses) {
            ClassReader cls = manager.getClassSource().get(jsClass.getName());
            renderClassWrappers(writer, cls, jsClass);
        }
        writer.newLine();
    }

    private List<JsClass> getJsClasses() {
        List<JsClass> jsClasses = new ArrayList<>();
        for (String className : manager.getClassSource().getClassNames()) {
            JsClass jsClass = jsInteropContext.getClass(className);
            if (jsClass.jsType && !jsClass.isNative) {
                jsClasses.add(jsClass);
            }
        }
        return jsClasses;
    }

    private JsPackage buildRootPackage(List<JsClass> jsClasses) {
        JsPackage jsPackage = new JsPackage(null);
        jsClasses.forEach(jsClass -> addClassToPackage(jsPackage, jsClass.getName()));
        return jsPackage;
    }

    private void addClassToPackage(JsPackage jsPackage, String className) {
        int index = 0;
        while (true) {
            int next = className.indexOf('.', index);
            if (next < 0) {
                break;
            }
            String packageName = className.substring(index, next);
            jsPackage = jsPackage.subpackages.computeIfAbsent(packageName, JsPackage::new);
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

    private void renderClassWrappers(SourceWriter writer, ClassReader cls, JsClass jsClass) throws IOException {
        writer.append(cls.getName()).ws().append("=").ws().appendClass(cls.getName()).append(";").newLine();
    }

    static class JsPackage {
        String name;
        Map<String, JsPackage> subpackages = new LinkedHashMap<>();

        public JsPackage(String name) {
            this.name = name;
        }
    }
}
