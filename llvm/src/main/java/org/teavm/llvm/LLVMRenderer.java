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
            if (structure.fields.isEmpty() && isTop) {
                structure.fields.add(new Field("i8*", "<stub>"));
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

            for (MethodReader method : cls.getMethods()) {
                emitMethod(method);
            }

            appendable.append("\n");
        }
    }

    public void renderMain(MethodReference method) throws IOException {
        appendable.append("define i32 @main() {\n");
        appendable.append("    call void @\"" + mangleMethod(method) + "\"(i8 *null)\n");
        appendable.append("    ret i32 0\n");
        appendable.append("}\n");
    }

    public void renderInterfaceTable() throws IOException {
        Structure structure = new Structure("itable");
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
            StringBuilder methodType = new StringBuilder();
            methodType.append(renderType(method.getReturnType())).append(" (");
            if (method.parameterCount() > 0) {
                methodType.append(renderType(method.parameterType(0)));
                for (int i = 1; i < method.parameterCount(); ++i) {
                    methodType.append(", ").append(renderType(method.parameterType(i)));
                }
            }
            methodType.append(")*");

            structure.fields.add(new Field(methodType.toString(),
                    fqn ? method.toString() : method.getDescriptor().toString()));
        }
    }

    private void emitMethod(MethodReader method) throws IOException {
        if (method.hasModifier(ElementModifier.NATIVE)) {
            return;
        }

        appendable.append("define ").append(renderType(method.getResultType())).append(" ");
        appendable.append("@").append(mangleMethod(method.getReference())).append("(");
        List<String> parameters = new ArrayList<>();
        if (!method.hasModifier(ElementModifier.STATIC)) {
            parameters.add("i8* %arg0");
            emitted.add("%0 = bitcast i8* %v0 to i8*");
        }
        for (int i = 0; i < method.parameterCount(); ++i) {
            String type = renderType(method.parameterType(i));
            parameters.add(type + " %v" + (i + 1));
        }
        appendable.append(parameters.stream().collect(Collectors.joining(", "))).append(") {\n");

        ProgramReader program = method.getProgram();
        if (program != null && program.basicBlockCount() > 0) {
            typeInferer = new TypeInferer();
            typeInferer.inferTypes(program, method.getReference());
            temporaryVariable = program.variableCount();
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
            switch (direction) {
                case TO_INTEGER:
                    switch (type) {
                        case BYTE:
                            emitted.add("%v" + receiver.getIndex() + " = sext i8 %v" + value.getIndex() + " to i32");
                            break;
                        case SHORT:
                            emitted.add("%v" + receiver.getIndex() + " = sext i16 %v" + value.getIndex() + " to i32");
                            break;
                        case CHARACTER:
                            emitted.add("%v" + receiver.getIndex() + " = zext i16 %v" + value.getIndex() + " to i32");
                            break;
                    }
                    break;
                case FROM_INTEGER:
                    switch (type) {
                        case BYTE:
                            emitted.add("%v" + receiver.getIndex() + " = trunc i32 %v" + value.getIndex() + " to i8");
                            break;
                        case SHORT:
                        case CHARACTER:
                            emitted.add("%v" + receiver.getIndex() + " = trunc i32 %v" + value.getIndex() + " to i16");
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

            emitted.add("%v" + tmp + " = icmp " + getLLVMOperation(cond) + " " + type
                    + " %v" + operand.getIndex() + ", " + second);
            emitted.add("br i1 %v" + tmp + ", label %b" + consequent.getIndex() + ", label %b"
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

            emitted.add("%v" + tmp + " = icmp " + op + " " + type + " %v" + first.getIndex()
                    + ", %v" + second.getIndex());
            emitted.add("br i1 %v" + tmp + ", label %b" + consequent.getIndex() + ", label %b"
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

        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {

        }

        @Override
        public void create(VariableReader receiver, String type) {
            String typeRef = "%class." + type + "*";
            String sizeOf = "ptrtoint " + typeRef + " (getelementptr " + typeRef + " null, i32 1) to i32";
            emitted.add("%v" + receiver.getIndex() + " = call i8* malloc(i32 " + sizeOf + ")");

            String objectRef = "bitcast i8* %v" + receiver.getIndex() + " to %teavm.Object*";
            String headerRef = "getelementptr inbounds i8*, %teavm.Object* %v" + receiver.getIndex()
                    + ", i32 0, i32 0";
            String vtableRef = "bitcast %vtable." + type + " (" + objectRef + ") @vtable." + type + "* to i8*";
            emitted.add("store i8*" + vtableRef + ", (" + headerRef + ")");
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {

        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {

        }

        @Override
        public void arrayLength(VariableReader receiver, VariableReader array) {
            String type = getLLVMArrayType(typeInferer.typeOf(array.getIndex())) + "*";
            String objectRef = "bitcast i8* %v" + array.getIndex() + " to " + type;
            String headerRef = "getelementptr inbounds i8*, %teavm.Object* (" + objectRef + "), i32 0, i32 1";
            emitted.add("%v" + receiver.getIndex() + " = load i32 " + headerRef);
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
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value) {
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            if (type == InvocationType.SPECIAL) {
                StringBuilder sb = new StringBuilder();
                if (receiver != null) {
                    sb.append("%v" + receiver.getIndex() + " = ");
                }
                sb.append("call " + renderType(method.getReturnType()) + " @" + mangleMethod(method) + "(");

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

    private static String getLLVMArrayType(VariableType type) {
        switch (type) {
            case BYTE_ARRAY:
            case SHORT_ARRAY:
            case CHAR_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
                return "%teavm.PrimitiveArray";
            case OBJECT_ARRAY:
                return "%teavm.Array";
            default:
                throw new IllegalArgumentException("Not an array: " + type);
        }
    }

    public static String mangleMethod(MethodReference method) {
        StringBuilder sb = new StringBuilder(method.getClassName() + ".");
        sb.append(mangleType(method.getReturnType()));
        sb.append(method.getName().length()).append(method.getName());
        sb.append(Arrays.stream(method.getParameterTypes())
                .map(LLVMRenderer::mangleType)
                .collect(Collectors.joining()));
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
            return "L" + className.length() + className;
        }
        throw new IllegalArgumentException("Don't know how to mangle " + type);
    }
}
