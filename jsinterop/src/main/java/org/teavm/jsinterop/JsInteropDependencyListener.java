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
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class JsInteropDependencyListener extends AbstractDependencyListener {
    @Override
    public void classReached(DependencyAgent agent, String className, CallLocation location) {
        ClassReader cls = agent.getClassSource().get(className);
        if (JsInteropUtil.isExportedToJs(cls)) {
            exposeClass(agent, cls, location);
        }
    }

    private void exposeClass(DependencyAgent agent, ClassReader cls, CallLocation location) {
        List<MethodReader> methodsAndConstructors = new ArrayList<>();
        methodsAndConstructors.addAll(JsInteropUtil.getJsMethods(cls));
        methodsAndConstructors.addAll(JsInteropUtil.getJsConstructors(cls));
        for (MethodReader method : methodsAndConstructors) {
            MethodDependency methodDep = agent.linkMethod(method.getReference(), location);
            exposeMethodParameters(agent, methodDep);
            methodDep.use();
        }
    }

    private void exposeMethodParameters(DependencyAgent agent, MethodDependency methodDep) {
        for (int i = 1; i < methodDep.getParameterCount(); ++i) {
            exposeInputType(agent, methodDep.getReference().parameterType(i - 1), methodDep.getVariable(i));
        }
        exposeOutputType(agent, methodDep.getReference().getReturnType(), methodDep.getResult());
    }

    private void exposeInputType(DependencyAgent agent, ValueType type, DependencyNode node) {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.String":
                    agent.linkMethod(new MethodReference(String.class, "<init>", char[].class, void.class),
                            null).use();
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Boolean":
                    agent.linkMethod(new MethodReference(Boolean.class, "valueOf", boolean.class,
                            Boolean.class), null).use();
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Byte":
                    agent.linkMethod(new MethodReference(Byte.class, "valueOf", byte.class, Byte.class), null).use();
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Short":
                    agent.linkMethod(new MethodReference(Short.class, "valueOf", short.class, Short.class),
                            null).use();
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Integer":
                    agent.linkMethod(new MethodReference(Integer.class, "valueOf", int.class, Integer.class),
                            null).use();
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Float":
                    agent.linkMethod(new MethodReference(Float.class, "valueOf", float.class, Float.class),
                            null).use();
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Double":
                    agent.linkMethod(new MethodReference(Double.class, "valueOf", double.class, Double.class),
                            null).use();
                    node.propagate(agent.getType(className));
                    break;
            }
        }
    }

    private void exposeOutputType(DependencyAgent agent, ValueType type, DependencyNode node) {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            switch (className) {
                case "java.lang.String":
                    node.propagate(agent.getType(className));
                    break;
                case "java.lang.Boolean":
                    exposeOutputWrapper(agent, node, boolean.class, Boolean.class);
                    break;
                case "java.lang.Byte":
                    exposeOutputWrapper(agent, node, byte.class, Byte.class);
                    break;
                case "java.lang.Short":
                    exposeOutputWrapper(agent, node, short.class, Short.class);
                    break;
                case "java.lang.Integer":
                    exposeOutputWrapper(agent, node, int.class, Integer.class);
                    break;
                case "java.lang.Float":
                    exposeOutputWrapper(agent, node, float.class, Float.class);
                    break;
                case "java.lang.Double":
                    exposeOutputWrapper(agent, node, double.class, Double.class);
                    break;
            }
        }
    }

    private void exposeOutputWrapper(DependencyAgent agent, DependencyNode node, Class<?> primitiveType,
            Class<?> wrapperType) {
        MethodDependency methodDep = agent.linkMethod(new MethodReference(wrapperType,
                primitiveType.getName() + "Value", primitiveType), null);
        methodDep.getVariable(0).propagate(agent.getType(wrapperType.getName()));
        methodDep.use();
        node.propagate(agent.getType(wrapperType.getName()));
    }
}
