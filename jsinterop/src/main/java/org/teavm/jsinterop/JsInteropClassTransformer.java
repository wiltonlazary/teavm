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
import java.util.Collections;
import java.util.List;
import jsinterop.annotations.JsIgnore;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.InvokeInstruction;

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
            addJsConstructor(cls, diagnostics);
        }

        for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() != null) {
                processProgram(method.getReference(), method.getProgram(), diagnostics);
            }
        }
    }

    private void addJsConstructor(ClassHolder cls, Diagnostics diagnostics) {
        MethodHolder constructor = null;
        boolean originalReported = false;
        for (MethodHolder method : cls.getMethods().toArray(new MethodHolder[0])) {
            if (method.getName().equals("<init>")) {
                if (constructor == null) {
                    constructor = method;
                } else {
                    if (!originalReported) {
                        diagnostics.error(new CallLocation(constructor.getReference(), null),
                                "Duplicate constructor {{m0}}", constructor.getReference());
                        originalReported = true;
                    }
                    diagnostics.error(new CallLocation(method.getReference(), null),
                            "Duplicate constructor {{m0}}", method.getReference());
                }

                MethodHolder constructorMethod = new MethodHolder(getCorrespondingJsInit(cls.getName(),
                        method.getDescriptor()));
                constructorMethod.getModifiers().add(ElementModifier.STATIC);
                constructorMethod.getModifiers().add(ElementModifier.NATIVE);
                constructorMethod.getAnnotations().add(new AnnotationHolder(JsIgnore.class.getName()));
                cls.addMethod(constructorMethod);
            }
        }
    }

    private MethodDescriptor getCorrespondingJsInit(String className, MethodDescriptor method) {
        ValueType[] signature = method.getSignature();
        signature[signature.length - 1] = ValueType.object(className);
        return new MethodDescriptor("<jsinit>", signature);
    }

    private MethodReference getCorrespondingJsInit(MethodReference method) {
        return new MethodReference(method.getClassName(), getCorrespondingJsInit(method.getClassName(),
                method.getDescriptor()));
    }

    private void processProgram(MethodReference method, Program program, Diagnostics diagnostics) {
        List<List<TextLocation>> constructorCalls = new ArrayList<>();
        String[] constructedClasses = new String[program.variableCount()];
        constructorCalls.addAll(Collections.nCopies(program.variableCount(), null));
        boolean[] jsConstructor = new boolean[program.variableCount()];

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            for (Instruction insn : block.getInstructions()) {
                if (insn instanceof ConstructInstruction) {
                    ConstructInstruction construct = (ConstructInstruction) insn;
                    if (context.getClass(construct.getType()).isJsType()) {
                        int receiver = construct.getReceiver().getIndex();
                        jsConstructor[receiver] = true;
                    }
                    continue;
                }

                if (!(insn instanceof InvokeInstruction)) {
                    continue;
                }
                InvokeInstruction invoke = (InvokeInstruction) insn;
                if (!invoke.getMethod().getName().equals("<init>")) {
                    continue;
                }

                JsClass jsClass = context.getClass(invoke.getMethod().getClassName());
                if (!jsClass.isJsType()) {
                    continue;
                }

                int instance = invoke.getInstance().getIndex();
                constructedClasses[instance] = invoke.getMethod().getClassName();
                List<TextLocation> locations = constructorCalls.get(instance);
                if (locations == null) {
                    locations = new ArrayList<>();
                    constructorCalls.set(instance, locations);
                }
                locations.add(invoke.getLocation());
            }
        }

        for (int i = 0; i < constructorCalls.size(); ++i) {
            List<TextLocation> locations = constructorCalls.get(i);
            if (locations != null && locations.size() > 1) {
                jsConstructor[i] = false;
                for (TextLocation location : locations) {
                    diagnostics.error(new CallLocation(method, location), "Multiple <init> calls are prohibited "
                            + "for JsType: {{c0}}", constructedClasses[i]);
                }
            }
        }

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            List<Instruction> instructions = block.getInstructions();
            for (int j = 0; j < instructions.size(); ++j) {
                Instruction instruction = instructions.get(j);
                if (instruction instanceof ConstructInstruction) {
                    int receiver = ((ConstructInstruction) instruction).getReceiver().getIndex();
                    if (jsConstructor[receiver]) {
                        EmptyInstruction nop = new EmptyInstruction();
                        nop.setLocation(instruction.getLocation());
                        instructions.set(j, nop);
                    }
                } else if (instruction instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instruction;
                    if (invoke.getMethod().getName().equals("<init>")
                            && jsConstructor[invoke.getInstance().getIndex()]) {
                        invoke.setMethod(getCorrespondingJsInit(invoke.getMethod()));
                        invoke.setReceiver(invoke.getInstance());
                        invoke.setInstance(null);
                    }
                }
            }
        }
    }
}
