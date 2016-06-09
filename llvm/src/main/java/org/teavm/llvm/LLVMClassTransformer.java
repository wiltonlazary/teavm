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

import org.teavm.diagnostics.Diagnostics;
import org.teavm.llvm.annotations.LLVMNative;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.optimization.UnreachableBasicBlockEliminator;

public class LLVMClassTransformer implements ClassHolderTransformer {
    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        for (MethodHolder method : cls.getMethods()) {
            if (method.getAnnotations().get(LLVMNative.class.getName()) != null) {
                method.getModifiers().add(ElementModifier.NATIVE);
                method.setProgram(null);
            }
            Program program = method.getProgram();
            if (program != null) {
                for (int i = 0; i < program.basicBlockCount(); ++i) {
                    BasicBlock block = program.basicBlockAt(i);
                    block.getTryCatchBlocks().clear();
                    block.getTryCatchJoints().clear();
                }
                new UnreachableBasicBlockEliminator().optimize(program);
            }
        }
    }
}
