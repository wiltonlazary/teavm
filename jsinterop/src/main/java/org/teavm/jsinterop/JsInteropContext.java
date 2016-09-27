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

import java.util.HashMap;
import java.util.Map;
import jsinterop.annotations.JsType;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;

public class JsInteropContext {
    ClassReaderSource classSource;
    private Map<String, JsClass> cache = new HashMap<>();

    JsInteropContext() {
    }

    public JsClass getClass(String className) {
        return cache.computeIfAbsent(className, this::constructClass);
    }

    private JsClass constructClass(String className) {
        JsClass jsClass = new JsClass();
        jsClass.name = className;
        ClassReader cls = classSource.get(className);
        if (cls != null) {
            JsClass parent = cls.getParent() != null ? getClass(cls.getParent()) : null;
            if (parent != null) {
                jsClass.jsTypeCompatible = parent.jsTypeCompatible;
            }

            for (String interfaceName : cls.getInterfaces()) {
                JsClass jsInterface = getClass(interfaceName);
                if (jsInterface.jsTypeCompatible) {
                    jsClass.jsTypeCompatible = true;
                }
            }

            AnnotationReader jsTypeAnnotation = cls.getAnnotations().get(JsType.class.getName());
            if (jsTypeAnnotation != null) {
                jsClass.jsType = true;
                jsClass.jsTypeCompatible = true;

                AnnotationValue isNativeField = jsTypeAnnotation.getValue("isNative");
                jsClass.isNative = isNativeField != null && isNativeField.getBoolean();
            }
        }
        return jsClass;
    }
}
