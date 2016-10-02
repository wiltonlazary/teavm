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
        Map<String, MemberReader> names = new HashMap<>();
        Set<MemberReader> collisionReported = new HashSet<>();
        Set<MemberReader> members = new HashSet<>();
        members.addAll(JsInteropUtil.getJsMethods(cls));
        members.addAll(JsInteropUtil.getJsConstructors(cls));

        for (MemberReader member : members) {
            if (!JsInteropUtil.isAutoJsMember(member)) {
                continue;
            }
            MemberReader existingMember = names.get(member.getName());
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

    private void reportCollision(MemberReader member, Diagnostics diagnostics) {
        if (member instanceof MethodReader) {
            MethodReference methodRef = ((MethodReader) member).getReference();
            CallLocation location = new CallLocation(methodRef);
            diagnostics.error(location, "JS name collision detected on method {{m0}}", methodRef);
        } else if (member instanceof FieldReader) {
            FieldReference fieldRef = ((FieldReader) member).getReference();
            diagnostics.error(null, "JS name collision detected on file {{f0}}", fieldRef);
        }
    }
}
