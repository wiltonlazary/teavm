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
package org.teavm.llvm.virtual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

public class VirtualTableRegistry implements VirtualTableProvider {
    private ClassReaderSource classSource;
    private Map<MethodReference, VirtualTableEntry> entryMap = new LinkedHashMap<>();
    private Map<String, VirtualTable> virtualTables = new LinkedHashMap<>();
    private VirtualTable interfaceTable = new VirtualTable(null);

    public VirtualTableRegistry(ClassReaderSource classSource) {
        this.classSource = classSource;
    }

    public void fillClass(String className) {
        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return;
        }

        for (MethodReader method : cls.getMethods()) {
            addMethod(method.getReference());
        }
    }

    public void addMethod(MethodReference methodRef) {
        if (methodRef.getName().equals("<init>")) {
            return;
        }
        ClassReader cls = classSource.get(methodRef.getClassName());
        if (cls == null) {
            return;
        }
        MethodReader method = cls.getMethod(methodRef.getDescriptor());
        if (method.hasModifier(ElementModifier.STATIC) || method.getLevel() == AccessLevel.PRIVATE) {
            return;
        }

        for (MethodReference overriddenMethod : findOverriddenMethods(methodRef)) {
            if (lookup(overriddenMethod) == null) {
                cls = classSource.get(overriddenMethod.getClassName());
                if (cls != null) {
                    if (cls.hasModifier(ElementModifier.INTERFACE)) {
                        addInterfaceMethod(overriddenMethod);
                    } else {
                        addClassMethod(overriddenMethod);
                    }
                }
            }
        }
    }

    private void addClassMethod(MethodReference method) {
        if (entryMap.containsKey(method)) {
            throw new IllegalArgumentException("Method has already corresponding vtable entry: " + method);
        }
        VirtualTable vtable = virtualTables.computeIfAbsent(method.getClassName(), VirtualTable::new);
        VirtualTableEntry entry = new VirtualTableEntry(method, vtable.entries.size());
        vtable.entries.add(entry);
    }

    private void addInterfaceMethod(MethodReference method) {
        if (entryMap.containsKey(method)) {
            throw new IllegalArgumentException("Method has already corresponding vtable entry: " + method);
        }
        VirtualTableEntry entry = new VirtualTableEntry(method, interfaceTable.entries.size());
        interfaceTable.entries.add(entry);
    }

    @Override
    public VirtualTableEntry lookup(MethodReference method) {
        return entryMap.get(method);
    }

    @Override
    public VirtualTable lookup(String className) {
        return virtualTables.get(className);
    }

    @Override
    public VirtualTable getInterfaceTable() {
        return interfaceTable;
    }

    private List<MethodReference> findOverriddenMethods(MethodReference method) {
        Set<MethodReference> foundMethods = new HashSet<>();
        findOverriddenMethods(method, foundMethods);
        List<MethodReference> interfaceMethods = getInterfaceMethod(foundMethods);
        return !interfaceMethods.isEmpty() ? interfaceMethods : new ArrayList<>(foundMethods);
    }

    private boolean findOverriddenMethods(MethodReference methodRef, Set<MethodReference> overridden) {
        ClassReader cls = classSource.get(methodRef.getClassName());
        if (cls == null) {
            return false;
        }
        MethodReader method = cls.getMethod(methodRef.getDescriptor());

        boolean overrides = false;
        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            MethodReference parentMethod = new MethodReference(cls.getParent(), methodRef.getDescriptor());
            overrides |= findOverriddenMethods(parentMethod, overridden);
        }

        for (String iface : cls.getInterfaces()) {
            MethodReference ifaceMethod = new MethodReference(iface, methodRef.getDescriptor());
            overrides |= findOverriddenMethods(ifaceMethod, overridden);
        }

        if (!overrides && method != null) {
            overrides = true;
            overridden.add(methodRef);
        }

        return overrides;
    }

    private List<MethodReference> getInterfaceMethod(Collection<MethodReference> methods) {
        return methods.stream()
                .filter(method -> {
                    ClassReader cls = classSource.get(method.getClassName());
                    return cls != null && cls.hasModifier(ElementModifier.INTERFACE);
                })
                .collect(Collectors.toList());
    }
}
