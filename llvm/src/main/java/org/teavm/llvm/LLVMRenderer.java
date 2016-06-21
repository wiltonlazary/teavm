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

import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectOpenHashMap;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.teavm.common.Graph;
import org.teavm.llvm.context.LayoutProvider;
import org.teavm.llvm.context.TagRegistry;
import org.teavm.llvm.context.VirtualTable;
import org.teavm.llvm.context.VirtualTableEntry;
import org.teavm.llvm.context.VirtualTableProvider;
import org.teavm.model.BasicBlock;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.Instruction;
import org.teavm.model.InstructionLocation;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.PrimitiveType;
import org.teavm.model.Program;
import org.teavm.model.ProgramReader;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TryCatchJointReader;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.SwitchTableEntryReader;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.LivenessAnalyzer;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.TypeInferer;
import org.teavm.model.util.VariableType;

public class LLVMRenderer {
    public static final long GC_BLACK = 1L << 31;
    private ClassReaderSource classSource;
    private VirtualTableProvider vtableProvider;
    private LayoutProvider layoutProvider;
    private TagRegistry tagRegistry;
    private Appendable appendable;
    private Map<String, Integer> stringIndexes = new HashMap<>();
    private List<String> stringPool = new ArrayList<>();

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
                renderMethod(method);
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

        int tag = tagRegistry.getRanges(cls.getName()).stream().map(range -> range.lower).findAny().orElse(-1);

        indent(level + 1);
        String dataType = "%class." + cls.getName();
        appendable.append("i32 ptrtoint (" + dataType + "* getelementptr (" + dataType + ", "
                + dataType + "* null, i32 1) to i32),\n");
        indent(level + 1);
        appendable.append("i32 " + tag + ",\n");
        indent(level + 1);
        appendable.append("%teavm.Fields {\n");
        indent(level + 2);
        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            String parent = cls.getParent();
            appendable.append("%itable *bitcast (%vtable." + parent + "* @vtable." + parent + " to %itable*),\n");
        } else {
            appendable.append("%itable *null,\n");
        }
        indent(level + 2);
        appendable.append("i64 " + fieldCount + ",\n");
        indent(level + 2);
        if (fieldCount > 0) {
            appendable.append("i32* bitcast ([" + fieldCount + " x i32]* @fields." + cls.getName() + " to i32*)\n");
        } else {
            appendable.append("i32* null\n");
        }
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
                    + " to %itable*\n");
            appendable.append("    %flagsPtr = " + "getelementptr %itable, %itable* %itableRef, i32 0, i32 0\n");
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
        appendable.append("    call void @teavm.initStringPool()\n");
        appendable.append("    call void @\"" + mangleMethod(method) + "\"(i8 *null)\n");
        appendable.append("    ret i32 0\n");
        appendable.append("}\n");
    }

    public void renderStringPool() throws IOException {
        if (!stringPool.isEmpty()) {
            for (int i = 0; i < stringPool.size(); ++i) {
                String str = stringPool.get(i);
                String charsType = "[ " + (str.length() + 1) + " x i16 ]";
                String dataType = "{ %teavm.Array, " + charsType + " }";
                String stringObjectHeader = "%teavm.Object { i32 0 }";
                String stringObjectContent = "%class.java.lang.String { %class.java.lang.Object { "
                        + stringObjectHeader + " }, "
                        + "i8* bitcast (" + dataType + "* @teavm.strdata." + i + " to i8*), i32 0 }";
                appendable.append("@teavm.str." + i + " = private global " + stringObjectContent + "\n");

                appendable.append("@teavm.strdata." + i + " = private global " + dataType + " "
                        + "{ %teavm.Array { %teavm.Object { i32 0 }"
                        + ", i32 " + str.length() + ", %itable* null }, "
                        + charsType  + " [ i16 0");
                for (int j = 0; j < str.length(); ++j) {
                    appendable.append(", i16 " + (int) str.charAt(j));
                }
                appendable.append(" ] }\n");
            }
        }

        appendable.append("define private void @teavm.initStringPool() {\n");
        appendable.append("    %stringTag = " + tagConstant("%vtable.java.lang.String* @vtable.java.lang.String")
                + "\n");
        for (int i = 0; i < stringPool.size(); ++i) {
            appendable.append("    %str." + i + " = bitcast %class.java.lang.String* @teavm.str." + i
                    + " to %teavm.Object*\n");
            appendable.append("    %str.tagPtr." + i + " = getelementptr %teavm.Object, "
                    + "%teavm.Object* %str." + i + ", i32 0, i32 0\n");
            appendable.append("    store i32 %stringTag, i32* %str.tagPtr." + i + "\n");
        }
        appendable.append("    ret void\n");
        appendable.append("}\n");
    }

    private static String tagConstant(String tag) {
        return "or i32 lshr (i32 ptrtoint (i8* bitcast (" + tag + " to i8*) to i32), i32 3), " + GC_BLACK;
    }

    public void renderInterfaceTable() throws IOException {
        Structure structure = new Structure("itable");
        structure.fields.add(new Field("i32", "size"));
        structure.fields.add(new Field("i32", "tag"));
        structure.fields.add(new Field("%teavm.Fields", "fieldLayout"));
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

        String methodName = method.getReference().toString();
        methodNameSize = methodName.length() + 1;
        methodNameVar = "@name$" + mangleMethod(method.getReference());
        appendable.append(methodNameVar + " = private constant [" + methodNameSize + " x i8] c\""
                + methodName + "\\00\"\n");

        appendable.append("define private ").append(renderType(method.getResultType())).append(" ");
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
            typeInferer = new TypeInferer();
            typeInferer.inferTypes(program, method.getReference());

            List<IntObjectMap<BitSet>> callSiteLiveIns = findCallSiteLiveIns(method);
            stackFrameSize = getStackFrameSize(callSiteLiveIns);
            returnBlocks.clear();
            returnVariables.clear();

            if (stackFrameSize > 0) {
                String stackType = "{ %teavm.stackFrame, [" + stackFrameSize + " x i8*] }";
                appendable.append("    %stack = alloca " + stackType + "\n");
                appendable.append("    %stackHeader = getelementptr " + stackType + ", " + stackType + "* %stack, "
                        + "i32 0, i32 0\n");
                appendable.append("    %stackNext = getelementptr %teavm.stackFrame, "
                        + "%teavm.stackFrame* %stackHeader, i32 0, i32 2\n");
                appendable.append("    %stackTop = load %teavm.stackFrame*, %teavm.stackFrame** @teavm.stackTop\n");
                appendable.append("    store %teavm.stackFrame* %stackTop, %teavm.stackFrame** %stackNext\n");
                appendable.append("    store %teavm.stackFrame* %stackHeader, %teavm.stackFrame** @teavm.stackTop\n");
                appendable.append("    %sizePtr = getelementptr %teavm.stackFrame, %teavm.stackFrame* %stackHeader, "
                        + "i32 0, i32 0\n");
                appendable.append("    store i32 " + stackFrameSize + ", i32* %sizePtr\n");
                appendable.append("    %stackData = getelementptr " + stackType + ", " + stackType + "* %stack, "
                        + "i32 0, i32 1\n");
            }

            if (method.hasModifier(ElementModifier.STATIC) && !method.getName().equals("<clinit>")
                    || method.getName().equals("<init>")) {
                appendable.append("    call void @initializer$" + method.getOwnerName() + "()\n");
            }
            appendable.append("    br label %b0\n");

            temporaryVariable = 0;
            currentReturnType = method.getResultType();
            List<List<TryCatchJointReader>> outputJoints = ProgramUtils.getOutputJoints(program);

            for (int i = 0; i < program.basicBlockCount(); ++i) {
                IntObjectMap<BitSet> blockLiveIns = callSiteLiveIns.get(i);
                BasicBlockReader block = program.basicBlockAt(i);
                currentLlvmBlock = "b" + block.getIndex();
                appendable.append(currentLlvmBlock + ":\n");

                joints = new HashMap<>();
                for (TryCatchJointReader joint : outputJoints.get(i)) {
                    for (VariableReader jointSource : joint.readSourceVariables()) {
                        joints.computeIfAbsent(jointSource, x -> new ArrayList<>()).add(joint.getReceiver());
                    }
                }

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

                expectingException = !block.readTryCatchBlocks().isEmpty();
                if (expectingException) {
                    appendable.append("    %exception" + i + " = call i8* @teavm.catchException()\n");
                    appendable.append("    %caught" + i + " = icmp ne i8* %exception, null\n");
                    appendable.append("    br i1 %caught" + i + ", label %lp" + i + ", label %b" + i + "\n");
                }
                for (int j = 0; j < block.instructionCount(); ++j) {
                    this.callSiteLiveIns = blockLiveIns.get(j);
                    updateShadowStack();
                    block.readInstruction(j, reader);
                    flushInstructions();
                }
                if (expectingException) {
                    appendable.append("lp" + i + ":\n");
                }
            }

            if (stackFrameSize > 0 && !returnBlocks.isEmpty()) {
                appendable.append("exit:\n");
                String returnType = renderType(method.getResultType());
                String returnVariable;
                if (!returnVariables.isEmpty()) {
                    if (returnVariables.size() == 1) {
                        returnVariable = returnVariables.get(0);
                    } else {
                        returnVariable = "%return";
                        StringBuilder sb = new StringBuilder();
                        sb.append("%return = phi " + returnType + " ");
                        for (int i = 0; i < returnVariables.size(); ++i) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append("[" + returnVariables.get(i) + ", %" + returnBlocks.get(i) + "]");
                        }
                        appendable.append("    " + sb + "\n");
                    }
                } else {
                    returnVariable = null;
                }
                appendable.append("    %stackRestore = load %teavm.stackFrame*, %teavm.stackFrame** %stackNext\n");
                appendable.append("    store %teavm.stackFrame* %stackRestore, "
                        + "%teavm.stackFrame** @teavm.stackTop;\n");
                if (method.getResultType() == ValueType.VOID) {
                    appendable.append("    ret void\n");
                } else {
                    appendable.append("    ret " + returnType + " " + returnVariable + "\n");
                }
            }
        }

        appendable.append("}\n");
    }

    private List<IntObjectMap<BitSet>> findCallSiteLiveIns(MethodReader method) {
        List<IntObjectMap<BitSet>> liveOut = new ArrayList<>();

        Program program = ProgramUtils.copy(method.getProgram());
        LivenessAnalyzer livenessAnalyzer = new LivenessAnalyzer();
        livenessAnalyzer.analyze(program);
        Graph cfg = ProgramUtils.buildControlFlowGraph(program);
        DefinitionExtractor defExtractor = new DefinitionExtractor();

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            IntObjectMap<BitSet> blockLiveIn = new IntObjectOpenHashMap<>();
            liveOut.add(blockLiveIn);
            BitSet currentLiveOut = new BitSet();
            for (int successor : cfg.outgoingEdges(i)) {
                currentLiveOut.or(livenessAnalyzer.liveIn(successor));
            }
            for (int j = block.getInstructions().size() - 1; j >= 0; --j) {
                Instruction insn = block.getInstructions().get(j);
                insn.acceptVisitor(defExtractor);
                for (Variable definedVar : defExtractor.getDefinedVariables()) {
                    currentLiveOut.clear(definedVar.getIndex());
                }
                if (insn instanceof InvokeInstruction || insn instanceof InitClassInstruction
                        || insn instanceof ConstructInstruction || insn instanceof ConstructArrayInstruction) {
                    BitSet csLiveIn = (BitSet) currentLiveOut.clone();
                    for (int v = csLiveIn.nextSetBit(0); v >= 0; v = csLiveIn.nextSetBit(v + 1)) {
                        if (!isReference(v)) {
                            csLiveIn.clear(v);
                        }
                    }
                    blockLiveIn.put(j, csLiveIn);
                }
            }
        }

        return liveOut;
    }

    private boolean isReference(int var) {
        VariableType liveType = typeInferer.typeOf(var);
        switch (liveType) {
            case BYTE_ARRAY:
            case CHAR_ARRAY:
            case SHORT_ARRAY:
            case INT_ARRAY:
            case FLOAT_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
            case OBJECT_ARRAY:
            case OBJECT:
                return true;
            default:
                return false;
        }
    }

    private int getStackFrameSize(List<IntObjectMap<BitSet>> liveIn) {
        int max = 0;
        for (IntObjectMap<BitSet> blockLiveOut : liveIn) {
            for (ObjectCursor<BitSet> callSiteLiveOutCursor : blockLiveOut.values()) {
                BitSet callSiteLiveOut = callSiteLiveOutCursor.value;
                max = Math.max(max, callSiteLiveOut.cardinality());
            }
        }
        return max;
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

    private String renderItemType(VariableType type) {
        switch (type) {
            case BYTE_ARRAY:
                return "i8";
            case SHORT_ARRAY:
            case CHAR_ARRAY:
                return "i16";
            case INT_ARRAY:
                return "i32";
            case LONG_ARRAY:
                return "i64";
            case FLOAT_ARRAY:
                return "float";
            case DOUBLE_ARRAY:
                return "double";
            case OBJECT_ARRAY:
                return "i8*";
            default:
                throw new IllegalArgumentException("Not an array type: " + type);
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

    private void updateShadowStack() {
        if (callSiteLiveIns != null && stackFrameSize > 0) {
            String stackType = "[" + stackFrameSize + " x i8*]";
            int cellIndex = 0;
            for (int i = callSiteLiveIns.nextSetBit(0); i >= 0; i = callSiteLiveIns.nextSetBit(i + 1)) {
                String stackCell = "%t" + temporaryVariable++;
                emitted.add(stackCell + " = getelementptr " + stackType + ", " + stackType + "* %stackData, "
                        + "i32 0, i32 " + cellIndex++);
                emitted.add("store i8* %v" + i + ", i8**" + stackCell);
            }
            while (cellIndex < stackFrameSize) {
                String stackCell = "%t" + temporaryVariable++;
                emitted.add(stackCell + " = getelementptr " + stackType + ", " + stackType + "* %stackData, "
                        + "i32 0, i32 " + cellIndex++);
                emitted.add("store i8* null, i8**" + stackCell);
            }
        }
    }

    private List<String> emitted = new ArrayList<>();
    private int temporaryVariable;
    private TypeInferer typeInferer;
    private ValueType currentReturnType;
    private Map<VariableReader, List<VariableReader>> joints;
    private boolean expectingException;
    private int methodNameSize;
    private String methodNameVar;
    private BitSet callSiteLiveIns;
    private int stackFrameSize;
    private List<String> returnVariables = new ArrayList<>();
    private List<String> returnBlocks = new ArrayList<>();
    private String currentLlvmBlock;

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
            String constantString = "0x" + Long.toHexString(Double.doubleToLongBits(cst));
            emitted.add("%v" + receiver.getIndex() + " = fadd float " + constantString + ", 0.0");
        }

        @Override
        public void doubleConstant(VariableReader receiver, double cst) {
            String constantString = "0x" + Long.toHexString(Double.doubleToLongBits(cst));
            emitted.add("%v" + receiver.getIndex() + " = fadd double " + constantString + ", 0.0");
        }

        @Override
        public void stringConstant(VariableReader receiver, String cst) {
            int index = stringIndexes.computeIfAbsent(cst, key -> {
                int result = stringPool.size();
                stringPool.add(key);
                return result;
            });

            emitted.add("%v" + receiver.getIndex() + " = bitcast %class.java.lang.String* @teavm.str."
                    + index + " to i8*");
        }

        @Override
        public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second,
                NumericOperandType type) {
            StringBuilder sb = new StringBuilder();
            sb.append("%v" + receiver.getIndex() + " = ");
            boolean isFloat = type == NumericOperandType.FLOAT || type == NumericOperandType.DOUBLE;
            String typeStr = getLLVMType(type);

            String secondString = "%v" + second.getIndex();
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
            if (type == NumericOperandType.LONG) {
                switch (op) {
                    case SHIFT_LEFT:
                    case SHIFT_RIGHT:
                    case SHIFT_RIGHT_UNSIGNED: {
                        int tmp = temporaryVariable++;
                        emitted.add("%t" + tmp + " = sext i32 " + secondString + " to i64");
                        secondString = "%t" + tmp;
                        break;
                    }
                    default:
                        break;
                }
            }

            sb.append(" ").append(typeStr).append(" %v" + first.getIndex() + ", " + secondString);
            emitted.add(sb.toString());
        }

        @Override
        public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
            emitted.add("%v" + receiver.getIndex() + " = sub " + getLLVMType(type) + " 0, %v" + operand.getIndex());
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
                    emitted.add("%v" + receiver.getIndex() + " = bitcast i32 %v" + value.getIndex() + " to i32");
                    break;
                case FROM_INTEGER:
                    switch (type) {
                        case BYTE:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to i8");
                            emitted.add("%v" + receiver.getIndex() + " = sext i8 %t" + tmp + " to i32");
                            break;
                        case SHORT:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to i16");
                            emitted.add("%t" + receiver.getIndex() + " = sext i16 %t" + tmp + " to i32");
                            break;
                        case CHARACTER:
                            emitted.add("%t" + tmp + " = trunc i32 %v" + value.getIndex() + " to i16");
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
            if (expectingException) {
                emitted.add("call void @teavm.leaveException()");
            }
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
            if (expectingException) {
                emitted.add("call void @teavm.leaveException()");
            }
            emitted.add("br i1 %t" + tmp + ", label %b" + consequent.getIndex() + ", label %b"
                    + alternative.getIndex());
        }

        @Override
        public void jump(BasicBlockReader target) {
            if (expectingException) {
                emitted.add("call void @teavm.leaveException()");
            }
            emitted.add("br label %b" + target.getIndex());
        }

        @Override
        public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table,
                BasicBlockReader defaultTarget) {
            StringBuilder sb = new StringBuilder();
            sb.append("switch i32 %v" + condition.getIndex() + ", label %b" + defaultTarget.getIndex() + " [");
            for (SwitchTableEntryReader entry : table) {
                sb.append(" i32 " + entry.getCondition() + ", label %b" + entry.getTarget().getIndex());
            }
            sb.append(" ]");
            emitted.add(sb.toString());
        }

        @Override
        public void exit(VariableReader valueToReturn) {
            if (expectingException) {
                emitted.add("call void @teavm.leaveException()");
            }
            if (valueToReturn == null) {
                if (stackFrameSize == 0) {
                    emitted.add("ret void");
                } else {
                    emitted.add("br label %exit");
                }
            } else {
                VariableType type = typeInferer.typeOf(valueToReturn.getIndex());
                String returnVar = "%v" + valueToReturn.getIndex();
                if (stackFrameSize == 0) {
                    emitted.add("ret " + renderType(type) + " " + returnVar);
                } else {
                    returnVariables.add(returnVar);
                    emitted.add("br label %exit");
                }
            }
            if (stackFrameSize > 0) {
                returnBlocks.add(currentLlvmBlock);
            }
        }

        @Override
        public void raise(VariableReader exception) {
            int tmp = temporaryVariable++;
            int methodName = temporaryVariable++;
            emitted.add("%t" + tmp + " = getelementptr [26 x i8], [26 x i8]* @teavm.exceptionOccurred, i32 0, i32 0");
            emitted.add("%t" + methodName + " = getelementptr [" + methodNameSize + " x i8], "
                    + "[" + methodNameSize + " x i8]* " + methodNameVar + ", i32 0, i32 0");
            emitted.add("call i32 (i8*, ...) @printf(i8* %t" + tmp + ", i8* %t" + methodName + ")");
            emitted.add("call void @exit(i32 255)");
            if (currentReturnType == ValueType.VOID) {
                emitted.add("ret void");
            } else {
                emitted.add("ret " + renderType(currentReturnType) + " " + defaultValue(currentReturnType));
            }
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
            if (itemType instanceof ValueType.Primitive) {
                String functionName = getJavaTypeName(((ValueType.Primitive) itemType).getKind());
                functionName = "@teavm_" + functionName + "ArrayAlloc";
                emitted.add("%v" + receiver.getIndex() + " = call i8* " + functionName
                        + "(i32 %v" + size.getIndex() + ")");
                return;
            }

            int depth = 0;
            while (itemType instanceof ValueType.Array) {
                ++depth;
                itemType = ((ValueType.Array) itemType).getItemType();
            }

            String itemTypeRef;
            if (itemType instanceof ValueType.Object) {
                String className = ((ValueType.Object) itemType).getClassName();
                itemTypeRef = "%vtable." + className + "* @vtable." + className;
            } else if (itemType instanceof ValueType.Primitive) {
                String primitiveName = getJavaTypeName(((ValueType.Primitive) itemType).getKind());
                itemTypeRef = "%itable* @teavm." + primitiveName + "Array";
            } else {
                throw new AssertionError("Type is not expected here: " + itemType);
            }

            String tag = "i32 lshr (i32 ptrtoint (" + itemTypeRef + " to i32), i32 3)";
            emitted.add("%v" + receiver.getIndex() + " = call i8* @teavm_objectArrayAlloc(" + tag
                    + ", i8 " + depth + ", i32 %v" + size.getIndex() + ")");
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {

        }

        private int arraySizeInBytes(String size, String type) {
            int sizeOfVar = sizeOf("%teavm.Array", "1");
            int adjustedSize = temporaryVariable++;
            emitted.add("%t" + adjustedSize + " = add i32 " + size + ", 1");
            int dataSizeVar = sizeOf(type, "%t" + adjustedSize);
            int byteCount = temporaryVariable++;
            emitted.add("%t" + byteCount + " = add i32 %t" + dataSizeVar + ", %t" + sizeOfVar);
            return byteCount;
        }

        @Override
        public void create(VariableReader receiver, String type) {
            String typeRef = "vtable." + type;
            String tag = "i32 lshr (i32 ptrtoint (%" + typeRef + "* @" + typeRef + " to i32), i32 3)";
            emitted.add("%v" + receiver.getIndex() + " = call i8* @teavm_alloc(" + tag + ")");
        }

        private int sizeOf(String typeRef, String count) {
            int temporaryPointer = temporaryVariable++;
            int sizeOfVar = temporaryVariable++;
            emitted.add("%t" + temporaryPointer + " = getelementptr " + typeRef + ", " + typeRef
                    + "* null, i32 " + count);
            emitted.add("%t" + sizeOfVar + " = ptrtoint " + typeRef + "* %t" + temporaryPointer + " to i32");
            return sizeOfVar;
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
            arrayLength(array, "%v" + receiver.getIndex());
        }

        private void arrayLength(VariableReader array, String target) {
            int objectRef = temporaryVariable++;
            int headerRef = temporaryVariable++;
            emitted.add("%t" + objectRef + " = bitcast i8* %v" + array.getIndex() + " to %teavm.Array*");
            emitted.add("%t" + headerRef + " = getelementptr %teavm.Array, %teavm.Array* %t"
                    + objectRef + ", i32 0, i32 1");
            emitted.add(target + " = load i32, i32* %t" + headerRef);
        }

        @Override
        public void cloneArray(VariableReader receiver, VariableReader array) {
            String type = renderItemType(typeInferer.typeOf(array.getIndex()));
            int length = temporaryVariable++;
            arrayLength(array, "%t" + length);
            int byteCount = arraySizeInBytes("%t" + length, type);
            emitted.add("%v" + receiver.getIndex() + " = call i8* @malloc(i32 %t" + byteCount + ")");
            emitted.add("call i8* @memcpy(i8* %v" + receiver.getIndex() + ", i8* %v" + array.getIndex() + ", "
                    + "i32 %t" + length + ")");
        }

        @Override
        public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
            emitted.add("%v" + receiver.getIndex() + " = bitcast i8* %v" + array.getIndex() + " to i8*");
        }

        @Override
        public void getElement(VariableReader receiver, VariableReader array, VariableReader index) {
            String type = renderType(typeInferer.typeOf(receiver.getIndex()));
            VariableType itemType = typeInferer.typeOf(array.getIndex());
            String itemTypeStr = renderItemType(itemType);
            int elementRef = getArrayElementReference(array, index, itemTypeStr);
            if (type.equals(itemTypeStr)) {
                emitted.add("%v" + receiver.getIndex() + " = load " + type + ", " + type + "* %t" + elementRef);
            } else {
                int tmp = temporaryVariable++;
                emitted.add("%t" + tmp + " = load " + itemTypeStr + ", " + itemTypeStr + "* %t" + elementRef);
                switch (itemType) {
                    case BYTE_ARRAY:
                        emitted.add("%v" + receiver.getIndex() + " = sext i8 %t" + tmp + " to i32");
                        break;
                    case SHORT_ARRAY:
                        emitted.add("%v" + receiver.getIndex() + " = sext i16 %t" + tmp + " to i32");
                        break;
                    case CHAR_ARRAY:
                        emitted.add("%v" + receiver.getIndex() + " = zext i16 %t" + tmp + " to i32");
                        break;
                    default:
                        throw new AssertionError("Should not get here");
                }
            }
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value) {
            String type = renderType(typeInferer.typeOf(value.getIndex()));
            VariableType itemType = typeInferer.typeOf(array.getIndex());
            String itemTypeStr = renderItemType(itemType);
            int elementRef = getArrayElementReference(array, index, itemTypeStr);
            String valueRef = "%v" + value.getIndex();
            if (!type.equals(itemTypeStr)) {
                int tmp = temporaryVariable++;
                emitted.add("%t" + tmp + " = trunc i32 " + valueRef + " to " + itemTypeStr);
                valueRef = "%t" + tmp;
            }
            emitted.add("store " + itemTypeStr + " " + valueRef + ", " + itemTypeStr + "* %t" + elementRef);
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

            String functionText;
            if (type == InvocationType.SPECIAL) {
                functionText = "@" + mangleMethod(method);
            } else {
                VirtualTableEntry entry = resolve(method);
                String className = entry.getVirtualTable().getClassName();
                String typeRef = className != null ? "%vtable." + className : "%itable";
                int objectRef = temporaryVariable++;
                int headerFieldRef = temporaryVariable++;
                int vtableTag = temporaryVariable++;
                int vtableRef = temporaryVariable++;
                int vtableTypedRef = temporaryVariable++;
                emitted.add("%t" + objectRef + " = bitcast i8* %v" + instance.getIndex() + " to %teavm.Object*");
                emitted.add("%t" + headerFieldRef + " = getelementptr inbounds %teavm.Object, %teavm.Object* %t"
                        + objectRef + ", i32 0, i32 0");
                emitted.add("%t" + vtableTag + " = load i32, i32* %t" + headerFieldRef);
                emitted.add("%t" + vtableRef + " = shl i32 %t" + vtableTag + ", 3");
                emitted.add("%t" + vtableTypedRef + " = inttoptr i32 %t" + vtableRef + " to " + typeRef + "*");

                int functionRef = temporaryVariable++;
                int vtableIndex = entry.getIndex() + 1;
                if (className == null) {
                    vtableIndex += 2;
                }
                emitted.add("%t" + functionRef + " = getelementptr inbounds " + typeRef + ", "
                        + typeRef + "* %t" + vtableTypedRef + ", i32 0, i32 " + vtableIndex);
                int function = temporaryVariable++;
                String methodType = methodType(method.getDescriptor());
                emitted.add("%t" + function + " = load " + methodType + ", " + methodType + "* %t" + functionRef);

                functionText = "%t" + function;
            }

            sb.append("call " + renderType(method.getReturnType()) + " " + functionText + "(");

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

        private VirtualTableEntry resolve(MethodReference method) {
            while (true) {
                VirtualTableEntry entry = vtableProvider.lookup(method);
                if (entry != null) {
                    return entry;
                }
                ClassReader cls = classSource.get(method.getClassName());
                if (cls == null || cls.getParent() == null || cls.getParent().equals(cls.getName())) {
                    break;
                }
                method = new MethodReference(cls.getParent(), method.getDescriptor());
            }
            return null;
        }

        @Override
        public void invokeDynamic(VariableReader receiver, VariableReader instance, MethodDescriptor method,
                List<? extends VariableReader> arguments, MethodHandle bootstrapMethod,
                List<RuntimeConstant> bootstrapArguments) {

        }

        @Override
        public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
            if (type instanceof ValueType.Object) {
                String className = ((ValueType.Object) type).getClassName();
                List<TagRegistry.Range> ranges = tagRegistry.getRanges(className);

                if (!ranges.isEmpty()) {
                    String headerRef = "%t" + temporaryVariable++;
                    emitted.add(headerRef + " = bitcast i8* %v" + value.getIndex() + " to %teavm.Object*");
                    String vtableRefRef = "%t" + temporaryVariable++;
                    emitted.add(vtableRefRef + " = getelementptr %teavm.Object, %teavm.Object* " + headerRef + ", "
                            + "i32 0, i32 0");
                    String vtableTag = "%t" + temporaryVariable++;
                    emitted.add(vtableTag + " = load i32, i32* " + vtableRefRef);
                    String vtableRef = "%t" + temporaryVariable++;
                    emitted.add(vtableRef + " = shl i32 " + vtableTag + ", 3");
                    String typedVtableRef = "%t" + temporaryVariable++;
                    emitted.add(typedVtableRef + " = inttoptr i32 " + vtableRef + " to %itable*");
                    String tagRef = "%t" + temporaryVariable++;
                    emitted.add(tagRef + " = getelementptr %itable, %itable* " + typedVtableRef + ", i32 0, i32 1");
                    String tag = "%t" + temporaryVariable++;
                    emitted.add(tag + " = load i32, i32* " + tagRef);

                    String trueLabel = "tb" + temporaryVariable++;
                    String finalLabel = "tb" + temporaryVariable++;
                    String next = null;
                    for (TagRegistry.Range range : ranges) {
                        String tmpLabel = "tb" + temporaryVariable++;
                        next = "tb" + temporaryVariable++;
                        String tmpLower = "%t" + temporaryVariable++;
                        String tmpUpper = "%t" + temporaryVariable++;
                        emitted.add(tmpLower + " = icmp slt i32 " + tag + ", " + range.lower);
                        emitted.add("br i1 " + tmpLower + ", label %" + next + ", label %" + tmpLabel);
                        emitted.add(tmpLabel + ":");
                        emitted.add(tmpUpper + " = icmp sge i32 " + tag + ", " + range.upper);
                        emitted.add("br i1 " + tmpUpper + ", label %" + next + ", label %" + trueLabel);
                        emitted.add(next + ":");
                    }

                    String falseVar = "%t" + temporaryVariable++;
                    emitted.add(falseVar + " = add i32 0, 0");
                    emitted.add("br label %" + finalLabel);

                    String trueVar = "%t" + temporaryVariable++;
                    emitted.add(trueLabel + ":");
                    emitted.add(trueVar + " = add i32 1, 0");
                    emitted.add("br label %" + finalLabel);

                    String phiVar = "%t" + temporaryVariable++;
                    emitted.add(finalLabel + ":");
                    emitted.add(phiVar + " = phi i32 [ " + trueVar + ", "
                            + "%" + trueLabel + " ], [ " + falseVar + ", %" + next + "]");
                    emitted.add("%v" + receiver.getIndex() + " = add i32 0, " + phiVar);

                    currentLlvmBlock = finalLabel;
                } else {
                    emitted.add("%v" + receiver.getIndex() + " = add i32 0, 0");
                }
            } else {
                emitted.add("%v" + receiver.getIndex() + " = add i32 1, 0");
            }
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

    private static String getJavaTypeName(PrimitiveType type) {
        switch (type) {
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case SHORT:
                return "short";
            case CHARACTER:
                return "char";
            case INTEGER:
                return "int";
            case LONG:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            default:
                throw new IllegalArgumentException("Unknown primitive type: " + type);
        }
    }
}
