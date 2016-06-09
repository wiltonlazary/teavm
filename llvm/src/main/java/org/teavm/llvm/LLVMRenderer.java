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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.teavm.llvm.layout.LayoutProvider;
import org.teavm.llvm.virtual.VirtualTable;
import org.teavm.llvm.virtual.VirtualTableEntry;
import org.teavm.llvm.virtual.VirtualTableProvider;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.InstructionLocation;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.SwitchTableEntryReader;
import org.teavm.model.util.TypeInferer;
import org.teavm.model.util.VariableType;

public class LLVMRenderer {
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private LayoutProvider layoutProvider;
    private Appendable appendable;

    public LLVMRenderer(ClassReaderSource classSource, VirtualTableProvider vtableProvider,
            LayoutProvider layoutProvider, Appendable appendable) {
        this.classSource = classSource;
        this.vtableProvider = vtableProvider;
        this.layoutProvider = layoutProvider;
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

    public void renderClasses(Collection<String> classNames) throws IOException {
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

            for (FieldReader field : cls.getFields()) {
                if (!field.hasModifier(ElementModifier.STATIC)) {
                    structure.fields.add(new Field(renderType(field.getType()), field.getName()));
                }
            }
            renderStructure(structure);
        }

        for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            for (MethodReader method : cls.getMethods()) {
                renderMethod(method);
            }

            renderClassInitializer(cls);
        }

        for (String className : classNames) {
            ClassReader cls = classSource.get(className);

            VirtualTable vtable = vtableProvider.lookup(cls.getName());
            appendable.append("@vtable." + className + " = private global ");
            renderVirtualTableValues(cls, vtable, 1);
            appendable.append("\n");

            for (FieldReader field : cls.getFields()) {
                if (field.hasModifier(ElementModifier.STATIC)) {
                    Object initialValue = field.getInitialValue();
                    String initialValueStr = initialValue != null ? initialValue.toString()
                            : defaultValue(field.getType());
                    appendable.append("@" + mangleField(field.getReference()) + " = global "
                            + renderType(field.getType()) + " " + initialValueStr + "\n");
                }
            }
        }
    }

    private void renderVirtualTableValues(ClassReader cls, VirtualTable vtable, int level) throws IOException {
        ClassReader vtableCls = classSource.get(vtable.getClassName());
        indent(level);
        appendable.append("%vtable." + vtable.getClassName() + " {\n");

        boolean top = true;
        if (vtableCls.getParent() != null && !vtableCls.getParent().equals(vtableCls.getName())) {
            VirtualTable parentVtable = vtableProvider.lookup(vtableCls.getParent());
            if (parentVtable != null) {
                renderVirtualTableValues(cls, parentVtable, level + 1);
                top = false;
            }
        }
        if (top) {
            appendable.append("%itable ");
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
        appendable.append("{\n");

        indent(level + 1);
        appendable.append("i32 0");

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

    private void renderClassInitializer(ClassReader cls) throws IOException {
        appendable.append("define void @initializer$" + cls.getName() + "() {\n");
        MethodReader clinitMethod = cls.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID));
        if (clinitMethod != null) {
            String structType = "%vtable." + cls.getName();
            appendable.append("    %itableRef = bitcast " + structType + "* @vtable." + cls.getName()
                    + " to %itable*\n");
            appendable.append("    %flagsPtr = " + "getelementptr %itable, %itable* %itableRef, i32 0, i32 0\n");
            appendable.append("    %flags = load i32, i32* %flagsPtr\n");
            appendable.append("    %flag = lshr i32 %flags, 31\n");
            appendable.append("    %initialized = trunc i32 %flag to i1\n");
            appendable.append("    br i1 %initialized, label %skip, label %proceed\n");
            appendable.append("proceed:\n");
            appendable.append("    call void @" + mangleMethod(clinitMethod.getReference()) + "()\n");
            appendable.append("    %newFlags = or i32 %flags, " + (1 << 31) + "\n");
            appendable.append("    store i32 %newFlags, i32* %flagsPtr\n");
            appendable.append("    br label %skip\n");
            appendable.append("skip:");
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
        appendable.append("    call void @\"" + mangleMethod(method) + "\"(i8 *null)\n");
        appendable.append("    ret i32 0\n");
        appendable.append("}\n");
    }

    public void renderInterfaceTable() throws IOException {
        Structure structure = new Structure("itable");
        structure.fields.add(new Field("i32", "size"));
        emitVirtualTableEntries(vtableProvider.getInterfaceTable(), true, structure);
        if (structure.fields.isEmpty()) {
            structure.fields.add(new Field("i8*", "<stub>"));
        }
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

    private String methodType(MethodDescriptor method) {
        StringBuilder sb = new StringBuilder();
        sb.append(renderType(method.getResultType())).append(" (i8*");
        for (int i = 0; i < method.parameterCount(); ++i) {
            sb.append(", ").append(renderType(method.parameterType(i)));
        }
        sb.append(")*");
        return sb.toString();
    }

    private void renderMethod(MethodReader method) throws IOException {
        if (method.hasModifier(ElementModifier.NATIVE) || method.hasModifier(ElementModifier.ABSTRACT)) {
            return;
        }

        appendable.append("define ").append(renderType(method.getResultType())).append(" ");
        appendable.append("@").append(mangleMethod(method.getReference())).append("(");
        List<String> parameters = new ArrayList<>();
        if (!method.hasModifier(ElementModifier.STATIC)) {
            parameters.add("i8* %v0");
        }
        for (int i = 0; i < method.parameterCount(); ++i) {
            String type = renderType(method.parameterType(i));
            parameters.add(type + " %v" + (i + 1));
        }
        appendable.append(parameters.stream().collect(Collectors.joining(", "))).append(") {\n");

        ProgramReader program = method.getProgram();
        if (program != null && program.basicBlockCount() > 0) {
            if (method.hasModifier(ElementModifier.STATIC) && !method.getName().equals("<clinit>")
                    || method.getName().equals("<init>")) {
                appendable.append("    call void @initializer$" + method.getOwnerName() + "()\n");
                appendable.append("    br label %b0\n");
            }

            typeInferer = new TypeInferer();
            typeInferer.inferTypes(program, method.getReference());
            temporaryVariable = 0;
            for (int i = 0; i < program.basicBlockCount(); ++i) {
                BasicBlockReader block = program.basicBlockAt(i);
                appendable.append("b" + block.getIndex() + ":\n");

                for (PhiReader phi : block.readPhis()) {
                    String type = renderType(typeInferer.typeOf(phi.getReceiver().getIndex()));
                    appendable.append("    %v" + phi.getReceiver().getIndex() + " = phi " + type);
                    boolean first = true;
                    for (IncomingReader incoming : phi.readIncomings()) {
                        if (!first) {
                            appendable.append(", ");
                        }
                        first = false;
                        appendable.append("[ %v" + incoming.getValue().getIndex() + ", %b"
                                + incoming.getSource().getIndex() + "]");
                    }
                    appendable.append("\n");
                }

                for (int j = 0; j < block.instructionCount(); ++j) {
                    block.readInstruction(j, reader);
                    flushInstructions();
                }
            }
        }

        appendable.append("}\n");
    }

    private void flushInstructions() throws IOException {
        for (String emittedLine : emitted) {
            appendable.append("    " + emittedLine + "\n");
        }
        emitted.clear();
    }

    private String renderType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "i32";
                case BYTE:
                    return "i32";
                case SHORT:
                case CHARACTER:
                    return "i32";
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
            return "i8*";
        } else if (type instanceof ValueType.Void) {
            return "void";
        } else if (type instanceof ValueType.Object) {
            return "i8*";
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    private String renderType(VariableType type) {
        switch (type) {
            case INT:
                return "i32";
            case LONG:
                return "i64";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case BYTE_ARRAY:
            case CHAR_ARRAY:
            case SHORT_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
            case OBJECT_ARRAY:
            case OBJECT:
                return "i8*";
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

    private List<String> emitted = new ArrayList<>();
    private int temporaryVariable;
    private TypeInferer typeInferer;

    private InstructionReader reader = new InstructionReader() {
        @Override
        public void location(InstructionLocation location) {

        }

        @Override
        public void nop() {
        }

        @Override
        public void classConstant(VariableReader receiver, ValueType cst) {
            emitted.add("%v" + receiver.getIndex() + " = bitcast i8* null to i8 *");
        }

        @Override
        public void nullConstant(VariableReader receiver) {
            emitted.add("%v" + receiver.getIndex() + " = bitcast i8* null to i8 *");
        }

        @Override
        public void integerConstant(VariableReader receiver, int cst) {
            emitted.add("%v" + receiver.getIndex() + " = add i32 " + cst + ", 0");
        }

        @Override
        public void longConstant(VariableReader receiver, long cst) {
            emitted.add("%v" + receiver.getIndex() + " = add i64 " + cst + ", 0");
        }

        @Override
        public void floatConstant(VariableReader receiver, float cst) {
            emitted.add("%v" + receiver.getIndex() + " = add float " + cst + ", 0");
        }

        @Override
        public void doubleConstant(VariableReader receiver, double cst) {
            emitted.add("%v" + receiver.getIndex() + " = add double " + cst + ", 0");
        }

        @Override
        public void stringConstant(VariableReader receiver, String cst) {
            emitted.add("%v" + receiver.getIndex() + " = bitcast i8* null to i8*");
        }

        @Override
        public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second,
                NumericOperandType type) {
            StringBuilder sb = new StringBuilder();
            sb.append("%v" + receiver.getIndex() + " = ");
            boolean isFloat = type == NumericOperandType.FLOAT || type == NumericOperandType.DOUBLE;
            String typeStr = getLLVMType(type);

            switch (op) {
                case ADD:
                    sb.append(isFloat ? "fadd" : "add");
                    break;
                case SUBTRACT:
                    sb.append(isFloat ? "fsub" : "sub");
                    break;
                case MULTIPLY:
                    sb.append(isFloat ? "fmul" : "mul");
                    break;
                case DIVIDE:
                    sb.append(isFloat ? "fdiv" : "sdiv");
                    break;
                case MODULO:
                    sb.append(isFloat ? "frem" : "srem");
                    break;
                case AND:
                    sb.append("and");
                    break;
                case OR:
                    sb.append("or");
                    break;
                case XOR:
                    sb.append("xor");
                    break;
                case SHIFT_LEFT:
                    sb.append("shl");
                    break;
                case SHIFT_RIGHT:
                    sb.append("ashr");
                    break;
                case SHIFT_RIGHT_UNSIGNED:
                    sb.append("lshr");
                    break;
                case COMPARE:
                    sb.append("call i32 @teavm.cmp.");
                    sb.append(typeStr + "(" + typeStr + " %v" + first.getIndex() + ", "
                            + typeStr + " %v" + second.getIndex() + ")");
                    emitted.add(sb.toString());
                    return;
            }

            sb.append(" ").append(typeStr).append(" %v" + first.getIndex() + ", %v" + second.getIndex());
            emitted.add(sb.toString());
        }

        @Override
        public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
            emitted.add("%v" + receiver.getIndex() + " = sub " + getLLVMType(type) + " 0, %v" + receiver.getIndex());
        }

        @Override
        public void assign(VariableReader receiver, VariableReader assignee) {
            String type = renderType(typeInferer.typeOf(receiver.getIndex()));
            emitted.add("%v" + receiver.getIndex() + " = bitcast " + type + " %v" + assignee.getIndex()
                    + " to " + type);
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, ValueType targetType) {
            emitted.add("%v" + receiver.getIndex() + " = bitcast i8* %v" + value.getIndex() + " to i8*");
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType,
                NumericOperandType targetType) {
            switch (sourceType) {
                case INT:
                    switch (targetType) {
                        case INT:
                        case LONG:
                            emitted.add("%v" + receiver.getIndex() + " = sext i32 %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                        case FLOAT:
                        case DOUBLE:
                            emitted.add("%v" + receiver.getIndex() + " = sitofp i32 %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                    }
                    break;
                case LONG:
                    switch (targetType) {
                        case INT:
                        case LONG:
                            emitted.add("%v" + receiver.getIndex() + " = trunc i64 %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                        case FLOAT:
                        case DOUBLE:
                            emitted.add("%v" + receiver.getIndex() + " = sitofp i64 %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                    }
                    break;
                case FLOAT:
                    switch (targetType) {
                        case INT:
                        case LONG:
                            emitted.add("%v" + receiver.getIndex() + " = fptosi float %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                        case FLOAT:
                        case DOUBLE:
                            emitted.add("%v" + receiver.getIndex() + " = fpext float %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                    }
                    break;
                case DOUBLE:
                    switch (targetType) {
                        case INT:
                        case LONG:
                            emitted.add("%v" + receiver.getIndex() + " = fptosi double %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                        case FLOAT:
                        case DOUBLE:
                            emitted.add("%v" + receiver.getIndex() + " = fptrunc double %v" + value.getIndex() + " to "
                                    + getLLVMType(targetType));
                            break;
                    }
                    break;
            }
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type,
                CastIntegerDirection direction) {
            int tmp = temporaryVariable++;
            switch (direction) {
                case TO_INTEGER:
                    switch (type) {
                        case BYTE:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to i8");
                            emitted.add("%v" + receiver.getIndex() + " = sext i8 %v" + tmp + " to i32");
                            break;
                        case SHORT:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to 16");
                            emitted.add("%v" + receiver.getIndex() + " = sext i16 %v" + tmp + " to i32");
                            break;
                        case CHARACTER:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to 16");
                            emitted.add("%v" + receiver.getIndex() + " = zext i16 %v" + tmp + " to i32");
                            break;
                    }
                    break;
                case FROM_INTEGER:
                    switch (type) {
                        case BYTE:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to i8");
                            emitted.add("%v" + receiver.getIndex() + " = sext i8 %t" + tmp + " to i32");
                            break;
                        case SHORT:
                            emitted.add("%v" + tmp + " = trunc i32 %v" + value.getIndex() + " to i16");
                            emitted.add("%v" + receiver.getIndex() + " = sext i16 %t" + tmp + " to i32");
                            break;
                        case CHARACTER:
                            emitted.add("%v" + tmp + " = trunc i32 %v" + value.getIndex() + " to i16");
                            emitted.add("%v" + receiver.getIndex() + " = zext i16 %t" + tmp + " to i32");
                            break;
                    }
                    break;
            }
        }

        @Override
        public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent,
                BasicBlockReader alternative) {
            int tmp = temporaryVariable++;
            String type = "i32";
            String second = "0";
            if (cond == BranchingCondition.NULL || cond == BranchingCondition.NOT_NULL) {
                type = "i8*";
                second = "null";
            }

            emitted.add("%t" + tmp + " = icmp " + getLLVMOperation(cond) + " " + type
                    + " %v" + operand.getIndex() + ", " + second);
            emitted.add("br i1 %t" + tmp + ", label %b" + consequent.getIndex() + ", label %b"
                    + alternative.getIndex());
        }

        @Override
        public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second,
                BasicBlockReader consequent, BasicBlockReader alternative) {
            int tmp = temporaryVariable++;

            String type = "i32";
            String op;
            switch (cond) {
                case EQUAL:
                    op = "eq";
                    break;
                case NOT_EQUAL:
                    op = "ne";
                    break;
                case REFERENCE_EQUAL:
                    op = "eq";
                    type = "i8*";
                    break;
                case REFERENCE_NOT_EQUAL:
                    op = "ne";
                    type = "i8*";
                    break;
                default:
                    throw new IllegalArgumentException("Unknown condition: " + cond);
            }

            emitted.add("%t" + tmp + " = icmp " + op + " " + type + " %v" + first.getIndex()
                    + ", %v" + second.getIndex());
            emitted.add("br i1 %t" + tmp + ", label %b" + consequent.getIndex() + ", label %b"
                    + alternative.getIndex());
        }

        @Override
        public void jump(BasicBlockReader target) {
            emitted.add("br label %b" + target.getIndex());
        }

        @Override
        public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table,
                BasicBlockReader defaultTarget) {
            StringBuilder sb = new StringBuilder();
            sb.append("switch i32 %v" + condition.getIndex() + ", %b" + defaultTarget.getIndex() + " [");
            for (SwitchTableEntryReader entry : table) {
                sb.append(" i32 " + entry.getCondition() + ", label %b" + entry.getTarget().getIndex());
            }
            sb.append(" ]");
            emitted.add(sb.toString());
        }

        @Override
        public void exit(VariableReader valueToReturn) {
            if (valueToReturn == null) {
                emitted.add("ret void");
            } else {
                VariableType type = typeInferer.typeOf(valueToReturn.getIndex());
                emitted.add("ret " +  renderType(type) + " %v" + valueToReturn.getIndex());
            }
        }

        @Override
        public void raise(VariableReader exception) {
            emitted.add("call void @exit(i32 255)");
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
            int sizeOfVar = sizeOf("%teavm.Array", "1");

            String type = renderType(itemType);
            int adjustedSize = temporaryVariable++;
            emitted.add("%t" + adjustedSize + " = add i32 %v" + size.getIndex() + ", 1");
            int dataSizeVar = sizeOf(type, "%t" + adjustedSize);
            int byteCount = temporaryVariable++;
            emitted.add("%t" + byteCount + " = add i32 %t" + dataSizeVar + ", %t" + sizeOfVar);

            emitted.add("%v" + receiver.getIndex() + " = call i8* @malloc(i32 %t" + byteCount + ")");
            putTag(receiver, "teavm.Array");

            int header = temporaryVariable++;
            int sizePtr = temporaryVariable++;
            emitted.add("%t" + header + " = bitcast i8* %v" + receiver.getIndex() + " to %teavm.Array*");
            emitted.add("%t" + sizePtr + " = getelementptr %teavm.Array, %teavm.Array* %t" + header
                    + ", i32 0, i32 1");
            emitted.add("store i32 %v" + size.getIndex() + ", i32* %t" + sizePtr);
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {

        }

        @Override
        public void create(VariableReader receiver, String type) {
            String typeRef = "%class." + type;
            int sizeOfVar = sizeOf(typeRef, "1");
            emitted.add("%v" + receiver.getIndex() + " = call i8* @malloc(i32 %t" + sizeOfVar + ")");
            putTag(receiver, "vtable." + type);
        }

        private int sizeOf(String typeRef, String count) {
            int temporaryPointer = temporaryVariable++;
            int sizeOfVar = temporaryVariable++;
            emitted.add("%t" + temporaryPointer + " = getelementptr " + typeRef + ", " + typeRef
                    + "* null, i32 " + count);
            emitted.add("%t" + sizeOfVar + " = ptrtoint " + typeRef + "* %t" + temporaryPointer + " to i32");
            return sizeOfVar;
        }

        private void putTag(VariableReader object, String type) {
            int objectRef = temporaryVariable++;
            int headerFieldRef = temporaryVariable++;
            int vtableRef = temporaryVariable++;
            emitted.add("%t" + objectRef + " = bitcast i8* %v" + object.getIndex() + " to %teavm.Object*");
            emitted.add("%t" + headerFieldRef + " = getelementptr inbounds %teavm.Object, %teavm.Object* %t"
                    + objectRef + ", i32 0, i32 0");
            String headerType = "%" + type;
            emitted.add("%t" + vtableRef + " = bitcast " + headerType + "* @" + type + " to i8*");
            emitted.add("store i8* %t" + vtableRef + ", i8** %t" + headerFieldRef);
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
            String valueTypeRef = renderType(fieldType);
            if (instance == null) {
                emitted.add("%v" + receiver.getIndex() + " = load " + valueTypeRef + ", "
                        + valueTypeRef + "* @" + mangleField(field));
            } else {
                int typedInstance = temporaryVariable++;
                int pointer = temporaryVariable++;
                String typeRef = "%class." + field.getClassName();
                emitted.add("%t" + typedInstance + " = bitcast i8* %v" + instance.getIndex() + " to " + typeRef + "*");
                emitted.add("%t" + pointer + " = getelementptr " + typeRef + ", " + typeRef + "* "
                        + "%t" + typedInstance + ", i32 0, i32 " + layoutProvider.getIndex(field));
                emitted.add("%v" + receiver.getIndex() + " = load " + valueTypeRef + ", "
                        + valueTypeRef + "* %t" + pointer);
            }
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
            String valueTypeRef = renderType(fieldType);
            if (instance == null) {
                emitted.add("store " + valueTypeRef + " %v" + value.getIndex() + ", "
                        + valueTypeRef + "* @" + mangleField(field));
            } else {
                int typedInstance = temporaryVariable++;
                int pointer = temporaryVariable++;
                String typeRef = "%class." + field.getClassName();
                emitted.add("%t" + typedInstance + " = bitcast i8* %v" + instance.getIndex() + " to " + typeRef + "*");
                emitted.add("%t" + pointer + " = getelementptr " + typeRef + ", " + typeRef + "* "
                        + "%t" + typedInstance + ", i32 0, i32 " + layoutProvider.getIndex(field));
                emitted.add("store " + valueTypeRef + " %v" + value.getIndex() + ", "
                        + valueTypeRef + "* %t" + pointer);
            }
        }

        @Override
        public void arrayLength(VariableReader receiver, VariableReader array) {
            int objectRef = temporaryVariable++;
            int headerRef = temporaryVariable++;
            emitted.add("%t" + objectRef + " = bitcast i8* %v" + array.getIndex() + " to %teavm.Array*");
            emitted.add("%t" + headerRef + " = getelementptr %teavm.Array, %teavm.Array* %t"
                    + objectRef + ", i32 0, i32 1");
            emitted.add("%v" + receiver.getIndex() + " = load i32, i32* %t" + headerRef);
        }

        @Override
        public void cloneArray(VariableReader receiver, VariableReader array) {

        }

        @Override
        public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
            emitted.add("%v" + receiver.getIndex() + " = bitcast i8* %v" + array.getIndex() + " to i8*");
        }

        @Override
        public void getElement(VariableReader receiver, VariableReader array, VariableReader index) {
            String type = renderType(typeInferer.typeOf(receiver.getIndex()));
            int elementRef = getArrayElementReference(array, index, type);
            emitted.add("%v" + receiver.getIndex() + " = load " + type + ", " + type + "* %t" + elementRef);
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value) {
            String type = renderType(typeInferer.typeOf(value.getIndex()));
            int elementRef = getArrayElementReference(array, index, type);
            emitted.add("store " + type + " %v" + value.getIndex() + ", " + type + "* %t" + elementRef);
        }

        private int getArrayElementReference(VariableReader array, VariableReader index, String type) {
            int objectRef = temporaryVariable++;
            int dataRef = temporaryVariable++;
            int typedDataRef = temporaryVariable++;
            int adjustedIndex = temporaryVariable++;
            int elementRef = temporaryVariable++;
            emitted.add("%t" + objectRef + " = bitcast i8* %v" + array.getIndex() + " to %teavm.Array*");
            emitted.add("%t" + dataRef + " = getelementptr %teavm.Array, %teavm.Array* %t"
                    + objectRef + ", i32 1");
            emitted.add("%t" + typedDataRef + " = bitcast %teavm.Array* %t" + dataRef + " to " + type + "*");
            emitted.add("%t" + adjustedIndex + " = add i32 %v" + index.getIndex() + ", 1");
            emitted.add("%t" + elementRef + " = getelementptr " + type + ", " + type + "* %t" + typedDataRef
                    + ", i32 %t" + adjustedIndex);

            return elementRef;
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            StringBuilder sb = new StringBuilder();
            if (receiver != null) {
                sb.append("%v" + receiver.getIndex() + " = ");
            }

            if (type == InvocationType.SPECIAL) {
                sb.append("call " + renderType(method.getReturnType()) + " @" + mangleMethod(method) + "(");
            } else {
                VirtualTableEntry entry = vtableProvider.lookup(method);
                String className = entry.getVirtualTable().getClassName();
                String typeRef = "%vtable." + className;
                int objectRef = temporaryVariable++;
                int headerFieldRef = temporaryVariable++;
                int vtableRef = temporaryVariable++;
                int vtableTypedRef = temporaryVariable++;
                emitted.add("%t" + objectRef + " = bitcast i8* %v" + instance.getIndex() + " to %teavm.Object*");
                emitted.add("%t" + headerFieldRef + " = getelementptr inbounds %teavm.Object, %teavm.Object* %t"
                        + objectRef + ", i32 0, i32 0");
                emitted.add("%t" + vtableRef + " = load i8*, i8** %t" + headerFieldRef);
                emitted.add("%t" + vtableTypedRef + " = bitcast i8* %t" + vtableRef + " to " + typeRef + "*");

                int functionRef = temporaryVariable++;
                emitted.add("%t" + functionRef + " = getelementptr inbounds " + typeRef + ", "
                        + typeRef + "* %t" + vtableTypedRef + ", i32 0, i32 " + (entry.getIndex() + 1));
                int function = temporaryVariable++;
                String methodType = methodType(method.getDescriptor());
                emitted.add("%t" + function + " = load " + methodType + ", " + methodType + "* %t" + functionRef);

                sb.append("call " + renderType(method.getReturnType()) + " %t" + function + "(");
            }

            List<String> argumentStrings = new ArrayList<>();
            if (instance != null) {
                argumentStrings.add("i8* %v" + instance.getIndex());
            }
            for (int i = 0; i < arguments.size(); ++i) {
                argumentStrings.add(renderType(method.parameterType(i)) + " %v" + arguments.get(i).getIndex());
            }
            sb.append(argumentStrings.stream().collect(Collectors.joining(", ")) + ")");

            emitted.add(sb.toString());
        }

        @Override
        public void invokeDynamic(VariableReader receiver, VariableReader instance, MethodDescriptor method,
                List<? extends VariableReader> arguments, MethodHandle bootstrapMethod,
                List<RuntimeConstant> bootstrapArguments) {

        }

        @Override
        public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {

        }

        @Override
        public void initClass(String className) {
            emitted.add("call void @initializer$" + className + "()");
        }

        @Override
        public void nullCheck(VariableReader receiver, VariableReader value) {
        }

        @Override
        public void monitorEnter(VariableReader objectRef) {
        }

        @Override
        public void monitorExit(VariableReader objectRef) {
        }
    };

    private static String getLLVMType(NumericOperandType type) {
        switch (type) {
            case INT:
                return "i32";
            case LONG:
                return "i64";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
        }
        throw new IllegalArgumentException("Unknown operand type: " + type);
    }

    private static String getLLVMOperation(BranchingCondition cond) {
        switch (cond) {
            case EQUAL:
            case NULL:
                return "eq";
            case NOT_NULL:
            case NOT_EQUAL:
                return "ne";
            case GREATER:
                return "sgt";
            case GREATER_OR_EQUAL:
                return "sge";
            case LESS:
                return "slt";
            case LESS_OR_EQUAL:
                return "sle";
        }
        throw new IllegalArgumentException("Unsupported condition: " + cond);
    }

    public static String mangleMethod(MethodReference method) {
        StringBuilder sb = new StringBuilder("method$" + method.getClassName() + ".");
        String name = mangleString(method.getName());
        sb.append(mangleType(method.getReturnType()));
        sb.append(name.length() + "_" + name);
        sb.append(Arrays.stream(method.getParameterTypes())
                .map(LLVMRenderer::mangleType)
                .collect(Collectors.joining()));
        return sb.toString();
    }

    private static String mangleField(FieldReference field) {
        StringBuilder sb = new StringBuilder("field$" + field.getClassName() + ".");
        String name = mangleString(field.getFieldName());
        sb.append(name.length() + "_" + name);
        return sb.toString();
    }

    private static String mangleString(String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            switch (c) {
                case '$':
                case '.':
                case '-':
                    sb.append(c);
                    break;
                case '_':
                    sb.append("__");
                    break;
                default:
                    if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                        sb.append(c);
                    } else {
                        sb.append('_')
                                .append(Character.forDigit(c >>> 12, 16))
                                .append(Character.forDigit((c >>> 8) & 0xF, 16))
                                .append(Character.forDigit((c >>> 4) & 0xF, 16))
                                .append(Character.forDigit(c & 0xF, 16));
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static String mangleType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "Z";
                case BYTE:
                    return "B";
                case SHORT:
                    return "S";
                case CHARACTER:
                    return "C";
                case INTEGER:
                    return "I";
                case LONG:
                    return "L";
                case FLOAT:
                    return "F";
                case DOUBLE:
                    return "D";
            }
        } else if (type instanceof ValueType.Void) {
            return "V";
        } else if (type instanceof ValueType.Array) {
            return "A" + mangleType(((ValueType.Array) type).getItemType());
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            return "L" + className.length() + "_" + className;
        }
        throw new IllegalArgumentException("Don't know how to mangle " + type);
    }

    private static String defaultValue(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            return "0";
        }
        return "null";
    }
}
