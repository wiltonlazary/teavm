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

import java.util.ArrayList;
import java.util.List;
import jsinterop.annotations.JsConstructor;
import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.MemberReader;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;

public final class JsInteropUtil {
    private JsInteropUtil() {
    }

    public static List<MethodReader> getJsMethods(ClassReader cls) {
        List<MethodReader> methods = new ArrayList<>();
        if (isJsType(cls)) {
            for (MethodReader method : cls.getMethods()) {
                if (isAutoJsMember(method) || isJsMethod(method)) {
                    methods.add(method);
                }
            }
        } else {
            for (MethodReader method : cls.getMethods()) {
                if (isJsMethod(method)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    public static List<MethodReader> getJsConstructors(ClassReader cls) {
        List<MethodReader> constructors = new ArrayList<>();
        if (isJsType(cls)) {
            for (MethodReader method : cls.getMethods()) {
                if (isAutoJsConstructor(method) || isJsConstructor(method)) {
                    constructors.add(method);
                }
            }
        } else {
            for (MethodReader method : cls.getMethods()) {
                if (isJsConstructor(method)) {
                    constructors.add(method);
                }
            }
        }
        return constructors;
    }

    public static boolean isAutoJsMember(MemberReader member) {
        if (member.getLevel() != AccessLevel.PUBLIC && member.getLevel() != AccessLevel.PROTECTED) {
            return false;
        }
        if (member.getAnnotations().get(JsIgnore.class.getName()) != null) {
            return false;
        }
        if (member instanceof MethodHolder) {
            switch (member.getName()) {
                case "<init>":
                case "<clinit>":
                    return false;
            }
        }

        return true;
    }

    public static boolean isAutoJsConstructor(MethodReader method) {
        if (method.getLevel() != AccessLevel.PUBLIC && method.getLevel() != AccessLevel.PROTECTED) {
            return false;
        }
        if (method.getAnnotations().get(JsIgnore.class.getName()) != null) {
            return false;
        }

        return method.getName().equals("<init>");
    }

    public static boolean isJsMethod(MethodReader method) {
        return method.getAnnotations().get(JsMethod.class.getName()) != null;
    }

    public static boolean isJsConstructor(MethodReader method) {
        return method.getAnnotations().get(JsConstructor.class.getName()) != null;
    }

    public static boolean isJsType(ClassReader cls) {
        return cls.getAnnotations().get(JsType.class.getName()) != null;
    }

    public static boolean isNonNativeJsType(ClassReader cls) {
        AnnotationReader jsTypeAnnot = cls.getAnnotations().get(JsType.class.getName());
        if (jsTypeAnnot == null) {
            return false;
        }
        AnnotationValue nativeValue = jsTypeAnnot.getValue("native");
        return nativeValue == null || !nativeValue.getBoolean();
    }

    public static boolean isExportedToJs(ClassReader cls) {
        if (isNonNativeJsType(cls)) {
            return true;
        }

        for (MethodReader method : cls.getMethods()) {
            if (method.getName().equals("<init>")) {
                if (method.getAnnotations().get(JsConstructor.class.getName()) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    public static String getJsPackageName(ClassReader cls) {
        AnnotationReader annotation = cls.getAnnotations().get(JsType.class.getName());
        if (annotation != null) {
            AnnotationValue namespace = annotation.getValue("namespace");
            if (namespace != null) {
                String value = namespace.getString();
                return !value.equals(JsPackage.GLOBAL) ? value : "";
            }
        }

        String name = cls.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(0, dotIndex) : "";
    }

    public static String getJsClassName(ClassReader cls) {
        AnnotationReader annotation = cls.getAnnotations().get(JsType.class.getName());
        if (annotation != null) {
            AnnotationValue name = annotation.getValue("name");
            if (name != null) {
                return name.getString();
            }
        }

        String name = cls.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex + 1) : name;
    }
}
