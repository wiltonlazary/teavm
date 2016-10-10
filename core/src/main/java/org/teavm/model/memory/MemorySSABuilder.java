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
package org.teavm.model.memory;

import java.util.Arrays;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.util.InstructionTransitionExtractor;

class MemorySSABuilder {
    private Program program;
    private Graph cfg;

    public MemorySSABuilder(Program program) {
        this.program = program;
    }

    public void build() {
        buildCFG();
    }

    private void buildCFG() {
        boolean[] entryPoints = new boolean[program.basicBlockCount()];
        Arrays.fill(entryPoints, true);
        InstructionTransitionExtractor transitionExtractor = new InstructionTransitionExtractor();
        GraphBuilder cfgBuilder = new GraphBuilder(program.basicBlockCount() + 1);

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            Instruction instruction = block.getLastInstruction();
            if (instruction != null) {
                instruction.acceptVisitor(transitionExtractor);
                BasicBlock[] successors = transitionExtractor.getTargets();
                if (successors != null) {
                    for (BasicBlock successor : successors) {
                        entryPoints[successor.getIndex()] = false;
                        cfgBuilder.addEdge(i + 1, successor.getIndex() + 1);
                    }
                }
            }
        }

        for (int i = 0; i < entryPoints.length; ++i) {
            if (entryPoints[i]) {
                cfgBuilder.addEdge(0, i + 1);
            }
        }

        cfg = cfgBuilder.build();
    }

    private void estimatePhis() {

    }
}
