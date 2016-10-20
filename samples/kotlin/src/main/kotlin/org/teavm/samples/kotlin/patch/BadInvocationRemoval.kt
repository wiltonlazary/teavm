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

object BadInvocationRemoval : ClassHolderTransformer {
    val methodsToRemove = setOf<MethodReference>()
    val classesToRemove = setOf(
            "com.intellij.openapi.util.text.StringUtil\$MyHtml2Text",
            "com.intellij.util.concurrency.AtomicFieldUpdater"
    )

    override fun transformClass(cls: ClassHolder, innerSource: ClassReaderSource, diagnostics: Diagnostics) {
        for (method in cls.methods) {
            method.program?.let { transformProgram(it) }
            method.modifiers -= ElementModifier.SYNCHRONIZED
        }
    }

    private fun shouldRemove(method: MethodReference): Boolean {
        return method in methodsToRemove || method.className in classesToRemove
    }

    private fun transformProgram(program: Program) {
        val indexes = 0 until program.basicBlockCount()
        for (block in indexes.map { program.basicBlockAt(it) }) {
            for ((index, instruction) in block.instructions.toList().withIndex()) {
                when (instruction) {
                    is InvokeInstruction -> {
                        val method = instruction.method
                        if (shouldRemove(method)) {
                            block.instructions[index] = createReplacement(instruction.receiver, method.returnType).apply {
                                location = instruction.location
                            }
                        }
                    }
                    is InitClassInstruction -> {
                        if (instruction.className in classesToRemove) {
                            block.instructions[index] = EmptyInstruction().apply {
                                location = instruction.location
                            }
                        }
                    }
                    is ConstructInstruction -> {
                        if (instruction.type in classesToRemove) {
                            block.instructions[index] = NullConstantInstruction().apply {
                                location = instruction.location
                                receiver = instruction.receiver
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createReplacement(receiver: Variable?, type: ValueType) = when (type) {
        is ValueType.Primitive -> when (type.kind!!) {
            PrimitiveType.BOOLEAN,
            PrimitiveType.BYTE,
            PrimitiveType.SHORT,
            PrimitiveType.CHARACTER,
            PrimitiveType.INTEGER -> IntegerConstantInstruction().apply { this.receiver = receiver }
            PrimitiveType.LONG -> LongConstantInstruction().apply { this.receiver = receiver }
            PrimitiveType.FLOAT -> FloatConstantInstruction().apply { this.receiver = receiver }
            PrimitiveType.DOUBLE -> DoubleConstantInstruction().apply { this.receiver = receiver }
        }
        is ValueType.Void -> EmptyInstruction()
        else -> NullConstantInstruction().apply { this.receiver = receiver }
    }
}
