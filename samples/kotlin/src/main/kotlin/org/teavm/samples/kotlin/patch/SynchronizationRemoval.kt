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

package org.teavm.samples.kotlin.patch

import org.teavm.diagnostics.Diagnostics
import org.teavm.model.*
import org.teavm.model.instructions.*
import java.util.concurrent.locks.Lock

class SynchronizationRemoval : ClassHolderTransformer {
    val methodsToRemove = setOf(
            MethodReference(Thread::class.java, "yield", Void.TYPE),
            MethodReference(Thread::class.java, "sleep", java.lang.Long.TYPE, Void.TYPE)
    )

    override fun transformClass(cls: ClassHolder, innerSource: ClassReaderSource, diagnostics: Diagnostics) {
        for (method in cls.methods) {
            method.program?.let { transformProgram(it) }
            method.modifiers -= ElementModifier.SYNCHRONIZED
        }
    }

    private fun transformProgram(program: Program) {
        val indexes = 0 until program.basicBlockCount()
        for (block in indexes.map { program.basicBlockAt(it) }) {
            for ((index, instruction) in block.instructions.toList().withIndex()) {
                fun replace() {
                    block.instructions[index] = EmptyInstruction().apply {
                        location = instruction.location
                    }
                }
                when (instruction) {
                    is MonitorEnterInstruction,
                    is MonitorExitInstruction -> replace()
                    is InvokeInstruction -> {
                        val method = instruction.method
                        when {
                            method in methodsToRemove -> replace()
                            method.className == Lock::class.java.name -> {
                                if (method.returnType == ValueType.VOID) {
                                    replace()
                                } else {
                                    block.instructions[index] = IntegerConstantInstruction().apply {
                                        constant = 0
                                        location = instruction.location
                                        receiver = instruction.receiver
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // do nothing
                    }
                }
            }
        }
    }
}