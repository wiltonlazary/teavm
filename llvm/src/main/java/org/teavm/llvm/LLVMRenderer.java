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

import static org.teavm.llvm.LLVMRenderingHelper.defaultValue;
import static org.teavm.llvm.LLVMRenderingHelper.mangleField;
import static org.teavm.llvm.LLVMRenderingHelper.mangleMethod;
import static org.teavm.llvm.LLVMRenderingHelper.methodType;
import static org.teavm.llvm.LLVMRenderingHelper.renderType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.teavm.llvm.context.CallSite;
import org.teavm.llvm.context.LayoutProvider;
import org.teavm.llvm.context.StringPool;
import org.teavm.llvm.context.TagRegistry;
import org.teavm.llvm.context.VirtualTable;
import org.teavm.llvm.context.VirtualTableEntry;
import org.teavm.llvm.context.VirtualTableProvider;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class LLVMRenderer {
    public static final long GC_BLACK = 1L << 31;
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private LayoutProvider layoutProvider;
    private TagRegistry tagRegistry;
    private Appendable appendable;
    private StringPool stringPool = new StringPool();
    private List<CallSite> callSites = new ArrayList<>();

    public LLVMRenderer(ClassReaderSource classSource, VirtualTableProvider vtableProvider,
            LayoutProvider layoutProvider, TagRegistry tagRegistry, Appendable appendable) {
        this.classSource = classSource;
        this.vtableProvider = vtableProvider;
        this.layoutProvider = layoutProvider;
        this.tagRegistry = tagRegistry;
        this.appendable = appendable;
    }

    public void renderPrologue() throws IOException {
        renderResource("prologue.ll");
    }

    public void renderEpilogue() throws IOException {
        renderResource("epilogue.ll");
    }

    private void renderResource(String name) throws IOException {
        ClassLoader classLoader = LLVMRenderer.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream("org/teavm/llvm/" + name);
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

    private static class ObjectLayout {
        String className;
        List<String> fields;

        public ObjectLayout(String className, List<String> fields) {
            this.className = className;
            this.fields = fields;
        }
    }

    public void renderClasses(Collection<String> classNames) throws IOException {
        List<String> stackRoots = new ArrayList<>();
        List<ObjectLayout> layouts = new ArrayList<>();
        for (String className : classNames) {
            appendable.append("; class ").append(className).append("\n");

            Structure structure = new Structure("vtable." + className);
            ClassReader cls = classSource.get(className);
            boolean isTop = cls == null || cls.getParent() == null || cls.getParent().equals(cls.getName());
            if (!isTop) {
                structure.fields.add(new Field("%vtable." + cls.getParent(), "<parent>"));
            } else {
                structure.fields.add(new Field("%itable"));
            }
            emitVirtualTableEntries(vtableProvider.lookup(className), false, structure);
            if (structure.fields.isEmpty() && isTop) {
                structure.fields.add(new Field("%itable"));
            }
            renderStructure(structure);

            structure = new Structure("class." + className);
            structure.fields.add(new Field(!isTop ? "%class." + cls.getParent() : "%teavm.Object"));

            List<String> gcFields = new ArrayList<>();
            for (FieldReader field : cls.getFields()) {
                if (!field.hasModifier(ElementModifier.STATIC)) {
                    if (isReference(field.getType())) {
                        gcFields.add("i32 ptrtoint (i8** getelementptr (%class." + className + ", "
                                + "%class." + className + "* null, i32 0, i32 " + structure.fields.size() + ") "
                                + "to i32)");
                    }
                    structure.fields.add(new Field(renderType(field.getType()), field.getName()));
                }
            }
            renderStructure(structure);

            if (!gcFields.isEmpty()) {
                layouts.add(new ObjectLayout(className, gcFields));
            }
        }

        for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            for (MethodReader method : cls.getMethods()) {
                LLVMMethodRenderer methodRenderer = new LLVMMethodRenderer(appendable, classSource, stringPool,
                        layoutProvider, vtableProvider, tagRegistry, cs -> addCallSite(cs));
                methodRenderer.renderMethod(method);
            }

            renderClassInitializer(cls);
        }

        for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            VirtualTable vtable = vtableProvider.lookup(cls.getName());
            appendable.append("@vtable." + className + " = private global ");
            renderVirtualTableValues(cls, vtable, 0);
            appendable.append(", align 8\n");

            for (FieldReader field : cls.getFields()) {
                if (field.hasModifier(ElementModifier.STATIC)) {
                    String fieldRef = "@" + mangleField(field.getReference());
                    Object initialValue = field.getInitialValue();
                    String initialValueStr = initialValue != null ? initialValue.toString()
                            : defaultValue(field.getType());
                    appendable.append(fieldRef + " = private global " + renderType(field.getType()) + " "
                            + initialValueStr + "\n");
                    stackRoots.add(fieldRef);
                }
            }
        }

        for (ObjectLayout layout : layouts) {
            String className = layout.className;
            appendable.append("@fields." + className + " = private constant [" + layout.fields.size() + " x i32] [\n");
            for (int i = 0; i < layout.fields.size(); ++i) {
                if (i > 0) {
                    appendable.append(",\n");
                }
                appendable.append("    " + layout.fields.get(i));
            }
            appendable.append("\n]\n");
        }

        String stackRootDataType = "[" + stackRoots.size() + " x i8**]";
        appendable.append("@teavm.stackRoots = constant %teavm.stackRoots { i64 " + stackRoots.size() + ", "
                + " i8*** bitcast (" + stackRootDataType + "* @teavm.stackRootsData to i8***) }\n");
        appendable.append("@teavm.stackRootsData = private constant " + stackRootDataType + " [");
        for (int i = 0; i < stackRoots.size(); ++i) {
            if (i > 0) {
                appendable.append(",");
            }
            appendable.append("\n    i8** " + stackRoots.get(i));
        }
        appendable.append("]\n");

        renderCallSites();
    }

    private void renderCallSites() throws IOException {
        String callSitesArrayType = "[" + callSites.size() + " x %teavm.CallSite]";
        appendable.append("@teavm.callSiteArray = constant " + callSitesArrayType + " [");
        for (int i = 0; i < callSites.size(); ++i) {
            if (i > 0) {
                appendable.append(",");
            }
            appendable.append("\n");

            CallSite callSite = callSites.get(i);
            int exceptionTypeCount = callSite.getExceptionTypes().size();
            appendable.append("    %teavm.CallSite {\n");
            appendable.append("        i32 " + exceptionTypeCount + ",\n");
            if (exceptionTypeCount > 0) {
                appendable.append("        %teavm.Class** bitcast ([" + exceptionTypeCount + " x %teavm.Class*] "
                        + "@teavm.exceptionTypes." + i + " as %teavm.Class**)\n");
            } else {
                appendable.append("        %teavm.Class** null\n");
            }
            appendable.append("    }");
        }
        appendable.append("\n]\n");

        appendable.append("@teavm.callSites = constant %teavm.CallSite* bitcast (" + callSitesArrayType
                + " @teavm.callSiteArray to %teavm.CallSite*\n)");

        for (int i = 0; i < callSites.size(); ++i) {
            CallSite callSite = callSites.get(i);
            int exceptionTypeCount = callSite.getExceptionTypes().size();
            if (exceptionTypeCount == 0) {
                continue;
            }

            appendable.append("@teavm.exceptionTypes." + i + " = constant [" + exceptionTypeCount
                    + " x %teavm.Class*] [");
            for (int j = 0; j < exceptionTypeCount; ++j) {
                if (j > 0) {
                    appendable.append(", ");
                }
                appendable.append("%teavm.Class* ");
                String exceptionType = callSite.getExceptionTypes().get(j);
                if (exceptionType == null) {
                    appendable.append("null");
                } else {
                    appendable.append(" bitcast (%vtable." + exceptionType + "* @vtable." + exceptionType
                            + " as %teavm.Class*)");
                }
            }
            appendable.append("]\n");
        }
    }

    private void renderVirtualTableValues(ClassReader cls, VirtualTable vtable, int level) throws IOException {
        ClassReader vtableCls = classSource.get(vtable.getClassName());
        appendable.append("%vtable." + vtable.getClassName() + " {\n");

        boolean top = true;
        if (vtableCls.getParent() != null && !vtableCls.getParent().equals(vtableCls.getName())) {
            VirtualTable parentVtable = vtableProvider.lookup(vtableCls.getParent());
            if (parentVtable != null) {
                indent(level + 1);
                renderVirtualTableValues(cls, parentVtable, level + 1);
                top = false;
            }
        }
        if (top) {
            indent(level + 1);
            renderInterfaceTableValues(cls, level + 1);
        }

        for (VirtualTableEntry entry : vtable.getEntries()) {
            appendable.append(",\n");
            MethodReference implementation = findImplementation(new MethodReference(cls.getName(),
                    entry.getMethod().getDescriptor()));
            indent(level + 1);
            appendable.append(methodType(entry.getMethod().getDescriptor()) + " ");
            if (implementation == null) {
                appendable.append(" null");
            } else {
                appendable.append("@" + mangleMethod(implementation));
            }
        }

        appendable.append("\n");
        indent(level);
        appendable.append("}");
    }

    private void renderInterfaceTableValues(ClassReader cls, int level) throws IOException {
        int fieldCount = (int) cls.getFields().stream()
                .filter(field -> !field.hasModifier(ElementModifier.STATIC) && isReference(field.getType()))
                .count();
        appendable.append("%itable {\n");

        int tag = tagRegistry.getRanges(cls.getName()).stream().map(range -> range.lower)
                .min(Comparator.naturalOrder()).orElse(-1);
        int upperTag = tagRegistry.getRanges(cls.getName()).stream().map(range -> range.upper)
                .max(Comparator.naturalOrder()).orElse(-1);

        indent(level + 1);
        String dataType = "%class." + cls.getName();
        appendable.append("%teavm.Class {\n");
        indent(level + 2);
        appendable.append("i32 ptrtoint (" + dataType + "* getelementptr (" + dataType + ", "
                + dataType + "* null, i32 1) to i32),\n");
        indent(level + 2);
        appendable.append("i32 0,\n");
        indent(level + 2);
        appendable.append("i32 " + tag + ",\n");
        indent(level + 2);
        appendable.append("i32 " + upperTag + ",\n");
        indent(level + 2);
        appendable.append("i32 " + (tag ^ 0xAAAAAAAA) + ",\n");

        indent(level + 2);
        appendable.append("%teavm.Fields {\n");
        indent(level + 3);
        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            String parent = cls.getParent();
            appendable.append("%teavm.Class *bitcast (%vtable." + parent + "* @vtable." + parent
                    + " to %teavm.Class*),\n");
        } else {
            appendable.append("%teavm.Class *null,\n");
        }
        indent(level + 3);
        appendable.append("i64 " + fieldCount + ",\n");
        indent(level + 3);
        if (fieldCount > 0) {
            appendable.append("i32* bitcast ([" + fieldCount + " x i32]* @fields." + cls.getName() + " to i32*)\n");
        } else {
            appendable.append("i32* null\n");
        }
        indent(level + 2);
        appendable.append("}\n");
        indent(level + 1);
        appendable.append("}");

        for (VirtualTableEntry entry : vtableProvider.getInterfaceTable().getEntries()) {
            appendable.append(",\n");
            MethodReference implementation = findImplementation(new MethodReference(cls.getName(),
                    entry.getMethod().getDescriptor()));
            indent(level + 1);
            appendable.append(methodType(entry.getMethod().getDescriptor()) + " ");
            if (implementation == null) {
                appendable.append(" null");
            } else {
                appendable.append("@" + mangleMethod(implementation));
            }
        }

        appendable.append("\n");
        indent(level);
        appendable.append("}");
    }

    private boolean isReference(ValueType type) {
        return type instanceof ValueType.Object || type instanceof ValueType.Array;
    }

    private void renderClassInitializer(ClassReader cls) throws IOException {
        appendable.append("define private void @initializer$" + cls.getName() + "() {\n");
        MethodReader clinitMethod = cls.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID));
        if (clinitMethod != null) {
            String structType = "%vtable." + cls.getName();
            appendable.append("    %itableRef = bitcast " + structType + "* @vtable." + cls.getName()
                    + " to %teavm.Class*\n");
            appendable.append("    %flagsPtr = " + "getelementptr %teavm.Class, "
                    + "%teavm.Class* %itableRef, i32 0, i32 1\n");
            appendable.append("    %flags = load i32, i32* %flagsPtr\n");
            appendable.append("    %flag = lshr i32 %flags, 31\n");
            appendable.append("    %initialized = trunc i32 %flag to i1\n");
            appendable.append("    br i1 %initialized, label %skip, label %proceed\n");
            appendable.append("proceed:\n");
            appendable.append("    %newFlags = or i32 %flags, " + (1 << 31) + "\n");
            appendable.append("    store i32 %newFlags, i32* %flagsPtr\n");
            appendable.append("    call void @" + mangleMethod(clinitMethod.getReference()) + "()\n");
            appendable.append("    br label %skip\n");
            appendable.append("skip:\n");
        }
        appendable.append("    ret void;\n");
        appendable.append("}\n");
    }

    private void indent(int count) throws IOException {
        while (count-- > 0) {
            appendable.append("    ");
        }
    }

    private MethodReference findImplementation(MethodReference method) {
        ClassReader cls = classSource.get(method.getClassName());
        if (cls == null) {
            return null;
        }

        MethodReader methodReader = cls.getMethod(method.getDescriptor());
        if (methodReader != null && !methodReader.hasModifier(ElementModifier.ABSTRACT)) {
            return method;
        }

        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            return findImplementation(new MethodReference(cls.getParent(), method.getDescriptor()));
        }

        return null;
    }

    public void renderMain(MethodReference method) throws IOException {
        appendable.append("define i32 @main() {\n");
        appendable.append("    call void @teavm.init()\n");
        appendable.append("    call void @teavm_initGC()\n");
        appendable.append("    call void @teavm.initStringPool()\n");
        appendable.append("    call void @\"" + mangleMethod(method) + "\"(i8 *null)\n");
        appendable.append("    ret i32 0\n");
        appendable.append("}\n");
    }

    public void renderStringPool() throws IOException {
        if (stringPool.size() > 0) {
            for (int i = 0; i < stringPool.size(); ++i) {
                String str = stringPool.get(i);
                String charsType = "[ " + (str.length() + 1) + " x i16 ]";
                String dataType = "{ %teavm.Array, " + charsType + " }";
                String stringObjectHeader = "%teavm.Object zeroinitializer";
                String stringObjectContent = "%class.java.lang.String { %class.java.lang.Object { "
                        + stringObjectHeader + " }, "
                        + "i8* bitcast (" + dataType + "* @teavm.strdata." + i + " to i8*), i32 0 }";
                appendable.append("@teavm.str." + i + " = private global " + stringObjectContent + "\n");

                appendable.append("@teavm.strdata." + i + " = private global " + dataType + " "
                        + "{ %teavm.Array { %teavm.Object zeroinitializer"
                        + ", i32 " + str.length() + ", %teavm.Class* null }, "
                        + charsType  + " [ i16 0");
                for (int j = 0; j < str.length(); ++j) {
                    appendable.append(", i16 " + (int) str.charAt(j));
                }
                appendable.append(" ] }\n");
            }
        }

        appendable.append("define void @teavm.initStringPool() {\n");
        appendable.append("    %stringTag = " + tagConstant("%vtable.java.lang.String* @vtable.java.lang.String")
                + "\n");
        appendable.append("    %stringCheck = xor i32 %stringTag, -1\n");
        for (int i = 0; i < stringPool.size(); ++i) {
            appendable.append("    %str." + i + " = bitcast %class.java.lang.String* @teavm.str." + i
                    + " to %teavm.Object*\n");
            appendable.append("    %str.tagPtr." + i + " = getelementptr %teavm.Object, "
                    + "%teavm.Object* %str." + i + ", i32 0, i32 0\n");
            appendable.append("    store i32 %stringTag, i32* %str.tagPtr." + i + "\n");
            appendable.append("    %str.checkPtr." + i + " = getelementptr %teavm.Object, "
                    + "%teavm.Object* %str." + i + ", i32 0, i32 1\n");
            appendable.append("    store i32 %stringCheck, i32* %str.checkPtr." + i + "\n");
        }
        appendable.append("    ret void\n");
        appendable.append("}\n");
    }

    private static String tagConstant(String tag) {
        return "or i32 lshr (i32 ptrtoint (i8* bitcast (" + tag + " to i8*) to i32), i32 3), " + (int) GC_BLACK;
    }

    public void renderInterfaceTable() throws IOException {
        Structure structure = new Structure("itable");
        structure.fields.add(new Field("%teavm.Class", "class information"));
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

            structure.fields.add(new Field(methodType(method.getDescriptor()),
                    fqn ? method.toString() : method.getDescriptor().toString()));
        }
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

    private int addCallSite(CallSite callSite) {
        int id = callSites.size();
        callSites.add(callSite);
        return id;
    }

    private static class Structure {
        private final String name;
        private final List<Field> fields = new ArrayList<>();

        Structure(String name) {
            this.name = name;
        }
    }

    private static class Field {
        private final String type;
        private final String name;

        Field(String type, String name) {
            this.type = type;
            this.name = name;
        }

        Field(String type) {
            this(type, null);
        }
    }
}
