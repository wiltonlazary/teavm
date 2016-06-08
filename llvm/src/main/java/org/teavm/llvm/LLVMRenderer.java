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
package org.teavm.llvm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.teavm.llvm.virtual.VirtualTable;
import org.teavm.llvm.virtual.VirtualTableEntry;
import org.teavm.llvm.virtual.VirtualTableProvider;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class LLVMRenderer {
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private Appendable appendable;

    public LLVMRenderer(ClassReaderSource classSource, VirtualTableProvider vtableProvider, Appendable appendable) {
        this.classSource = classSource;
        this.vtableProvider = vtableProvider;
        this.appendable = appendable;
    }

    public void renderPrologue() throws IOException {
        ClassLoader classLoader = LLVMRenderer.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream("org/teavm/llvm/prologue.ll");
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                appendable.append(line).append("\n");
            }
        }
    }

    public void renderClasses(Collection<String> classNames) throws IOException {
        for (String className : classNames) {
            appendable.append("; class ").append(className).append("\n");

            Structure structure = new Structure("vtable." + className);
            ClassReader cls = classSource.get(className);
            boolean isTop = cls == null || cls.getParent() == null || cls.getParent().equals(cls.getName());
            if (!isTop) {
                structure.fields.add(new Field("%vtable." + cls.getParent(), "<parent>"));
            }
            emitVirtualTableEntries(vtableProvider.lookup(className), false, structure);
            renderStructure(structure);

            structure = new Structure("class." + className);
            structure.fields.add(new Field(!isTop ? "%class." + cls.getParent() : "%teavm.Object"));

            for (FieldReader field : cls.getFields()) {
                if (!field.hasModifier(ElementModifier.STATIC)) {
                    structure.fields.add(new Field(renderType(field.getType()), field.getName()));
                }
            }
            renderStructure(structure);

            appendable.append("\n");
        }
    }

    public void renderInterfaceTable() throws IOException {
        Structure structure = new Structure("itable");
        emitVirtualTableEntries(vtableProvider.getInterfaceTable(), true, structure);
        renderStructure(structure);
        appendable.append("\n");
    }

    private void emitVirtualTableEntries(VirtualTable vtable, boolean fqn, Structure structure) {
        if (vtable == null) {
            return;
        }

        for (VirtualTableEntry entry : vtable.getEntries()) {
            MethodReference method = entry.getMethod();
            StringBuilder methodType = new StringBuilder();
            methodType.append(renderType(method.getReturnType())).append(" (");
            if (method.parameterCount() > 0) {
                methodType.append(renderType(method.parameterType(0)));
                for (int i = 1; i < method.parameterCount(); ++i) {
                    methodType.append(", ").append(renderType(method.parameterType(i)));
                }
            }
            methodType.append(") *");

            structure.fields.add(new Field(methodType.toString(),
                    fqn ? method.toString() : method.getDescriptor().toString()));
        }
    }

    private String renderType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "i1";
                case BYTE:
                    return "i8";
                case SHORT:
                case CHARACTER:
                    return "i16";
                case INTEGER:
                    return "i32";
                case LONG:
                    return "i64";
                case FLOAT:
                    return "float";
                case DOUBLE:
                    return "double";
            }
        } else if (type instanceof ValueType.Array) {
            return "%teavm.Array *";
        } else if (type instanceof ValueType.Void) {
            return "void";
        } else if (type instanceof ValueType.Object) {
            return "i8 *";
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    private void renderStructure(Structure structure) throws IOException {
        appendable.append("%").append(structure.name).append(" = type {");
        for (int i = 0; i < structure.fields.size() - 1; ++i) {
            Field field = structure.fields.get(i);
            appendable.append("\n    ");
            appendable.append(field.type).append(",");
            if (field.name != null) {
                appendable.append(" ;").append(field.name);
            }
        }
        Field lastField = structure.fields.get(structure.fields.size() - 1);
        appendable.append("\n    ");
        appendable.append(lastField.type);
        if (lastField.name != null) {
            appendable.append(" ;").append(lastField.name);
        }
        appendable.append("\n}\n");
    }

    private static class Structure {
        private final String name;
        private final List<Field> fields = new ArrayList<>();

        public Structure(String name) {
            this.name = name;
        }
    }

    private static class Field {
        private final String type;
        private final String name;

        public Field(String type, String name) {
            this.type = type;
            this.name = name;
        }

        public Field(String type) {
            this(type, null);
        }
    }
}
