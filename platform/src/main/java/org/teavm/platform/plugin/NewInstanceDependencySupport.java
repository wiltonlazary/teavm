/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.platform.plugin;

import org.teavm.dependency.*;
import org.teavm.model.*;
import org.teavm.platform.Platform;

public class NewInstanceDependencySupport extends AbstractDependencyListener {
    private DependencyAgent agent;

    @Override
    public void started(DependencyAgent agent) {
        this.agent = agent;
    }

    private void reachClass(DependencyAgent agent, DependencyType type, DependencyNode targetNode) {
        ClassReader cls = agent.getClassSource().get(type.getName());
        if (cls == null) {
            return;
        }
        if (cls.hasModifier(ElementModifier.ABSTRACT) || cls.hasModifier(ElementModifier.INTERFACE)) {
            return;
        }
        MethodReader method = cls.getMethod(new MethodDescriptor("<init>", void.class));
        if (method != null) {
            targetNode.propagate(type);
        }
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method, CallLocation location) {
        MethodReader reader = method.getMethod();
        if (reader.getOwnerName().equals(Platform.class.getName()) && reader.getName().equals("newInstanceImpl")) {
            MethodReference methodRef = reader.getReference();
            DependencyNode classNode = method.getVariable(1).getClassValueNode();
            DependencyNode resultNode = method.getResult();
            classNode.addConsumer(type -> reachClass(agent, type, resultNode));
            resultNode.addConsumer(type -> attachConstructor(agent, type.getName(), new CallLocation(methodRef)));
        }
    }

    private void attachConstructor(DependencyAgent checker, String type, CallLocation location) {
        MethodReference ref = new MethodReference(type, "<init>", ValueType.VOID);
        MethodDependency methodDep = checker.linkMethod(ref, location);
        methodDep.getVariable(0).propagate(checker.getType(type));
        methodDep.use();
    }
}
