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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MemberReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

public class JsInteropClassTransformer implements ClassHolderTransformer {
    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        validateClass(cls, diagnostics);
    }

    public void validateClass(ClassHolder cls, Diagnostics diagnostics) {
        Set<MemberReader> members = new HashSet<>();
        members.addAll(JsInteropUtil.getJsMethods(cls));
        members.addAll(JsInteropUtil.getJsConstructors(cls));

        validateMembers(cls, false, diagnostics, members);
        validateMembers(cls, true, diagnostics, members);
    }

    private void validateMembers(ClassReader cls, boolean isStatic, Diagnostics diagnostics,
            Set<MemberReader> members) {
        Map<String, MemberReader> names = new HashMap<>();
        Set<MemberReader> collisionReported = new HashSet<>();

        boolean jsType = JsInteropUtil.isJsType(cls);
        for (MemberReader member : members) {
            if (member.hasModifier(ElementModifier.STATIC) != isStatic) {
                continue;
            }

            String name;
            boolean isExplicitlyExported;
            if (member instanceof MethodReader) {
                MethodReader method = (MethodReader) member;
                name = JsInteropUtil.getJsMethodName(method);
                isExplicitlyExported = JsInteropUtil.isJsMethod(method);
            } else if (member instanceof FieldReader) {
                FieldReader field = (FieldReader) member;
                name = JsInteropUtil.getJsFieldName(field);
                isExplicitlyExported = JsInteropUtil.isJsField(field);
            } else {
                continue;
            }

            if (!isExplicitlyExported && !(jsType && JsInteropUtil.isAutoJsMember(member))) {
                continue;
            }

            MemberReader existingMember = names.get(name);
            if (existingMember != null) {
                if (collisionReported.add(existingMember)) {
                    reportCollision(existingMember, name, diagnostics);
                }
                reportCollision(member, name, diagnostics);
            } else {
                names.put(name, member);
            }
        }

        PropertyCollisionReporter collisionReporter = new PropertyCollisionReporter(diagnostics);
        Map<String, JsInteropProperty> properties = JsInteropUtil.collectProperties(cls, isStatic, collisionReporter);
        for (JsInteropProperty property : properties.values()) {
            MemberReader existingMember = names.get(property.name);
            if (existingMember != null) {
                if (collisionReported.add(existingMember)) {
                    reportCollision(existingMember, property.name, diagnostics);
                }
            }
            if (property.getter != null) {
                reportCollision(property.getter, property.name, diagnostics);
            } else if (property.setter != null) {
                reportCollision(property.setter, property.name, diagnostics);
            }
        }
    }

    private class PropertyCollisionReporter implements PropertyCollisionConsumer {
        private Diagnostics diagnostics;
        private Set<MethodReader> collisionsReported = new HashSet<>();

        public PropertyCollisionReporter(Diagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public void accept(MethodReader first, MethodReader second, String propertyName, boolean isGetter) {
            reportCollision(first, propertyName, isGetter);
            reportCollision(second, propertyName, isGetter);
        }

        private void reportCollision(MethodReader method, String propertyName, boolean isGetter) {
            if (!collisionsReported.add(method)) {
                return;
            }
            CallLocation location = new CallLocation(method.getReference());
            String subject = isGetter ? "getters" : "setters";
            diagnostics.error(location, "Multiple " + subject + " declared for property '" + propertyName
                    + "': {{m0}}", method.getReference());
        }
    }

    private void reportCollision(MemberReader member, String name, Diagnostics diagnostics) {
        if (member instanceof MethodReader) {
            MethodReference methodRef = ((MethodReader) member).getReference();
            CallLocation location = new CallLocation(methodRef);
            diagnostics.error(location, "JS name collision (" + name + ") detected on method {{m0}}", methodRef);
        } else if (member instanceof FieldReader) {
            FieldReference fieldRef = ((FieldReader) member).getReference();
            diagnostics.error(null, "JS name collision (" + name + ") detected on field {{f0}}", fieldRef);
        }
    }
}
