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
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MemberHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;

public class JsInteropClassTransformer implements ClassHolderTransformer {
    private JsInteropContext context = new JsInteropContext();

    public JsInteropContext getContext() {
        return context;
    }

    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        context.classSource = innerSource;
        JsClass jsClass = context.getClass(cls.getName());
        if (jsClass.isJsType()) {
            validateClass(cls, diagnostics);
        }
    }

    public void validateClass(ClassHolder cls, Diagnostics diagnostics) {
        Map<String, MemberHolder> names = new HashMap<>();
        Set<MemberHolder> collisionReported = new HashSet<>();
        Set<MemberHolder> members = new HashSet<>();
        members.addAll(cls.getMethods());
        members.addAll(cls.getFields());

        for (MemberHolder member : members) {
            if (!JsInteropUtil.isJsMember(member)) {
                continue;
            }
            MemberHolder existingMember = names.get(member.getName());
            if (existingMember != null) {
                if (collisionReported.add(existingMember)) {
                    reportCollision(existingMember, diagnostics);
                }
                reportCollision(member, diagnostics);
            } else {
                names.put(member.getName(), member);
            }
        }
    }

    private void reportCollision(MemberHolder member, Diagnostics diagnostics) {
        if (member instanceof MethodHolder) {
            MethodReference methodRef = ((MethodHolder) member).getReference();
            CallLocation location = new CallLocation(methodRef);
            diagnostics.error(location, "JS name collision detected on method {{m0}}", methodRef);
        } else if (member instanceof FieldHolder) {
            FieldReference fieldRef = ((FieldHolder) member).getReference();
            diagnostics.error(null, "JS name collision detected on file {{f0}}", fieldRef);
        }
    }
}
