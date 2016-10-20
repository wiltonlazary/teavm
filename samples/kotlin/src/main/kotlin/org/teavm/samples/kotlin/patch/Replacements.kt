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
import org.teavm.samples.kotlin.replacements.*

object Replacements : ClassHolderTransformer {
    val classNameMap = mapOf(
            "com.intellij.openapi.util.SystemInfo" to SystemInfo::class.java.name,
            "org.fusesource.jansi.internal.CLibrary" to CLibrary::class.java.name,
            "com.intellij.util.containers.ConcurrentWeakValueIntObjectHashMap" to
                    ConcurrentIntObjectMapImpl::class.java.name,
            "com.intellij.util.containers.LockFreeCopyOnWriteArrayList" to "java.util.ArrayList",
            "kotlin.SafePublicationLazyImpl" to SimpleLazy::class.java.name,
            "com.intellij.util.containers.ConcurrentWeakHashMap" to ConcurrentHashMap::class.java.name,
            "java.util.concurrent.ConcurrentHashMap" to ConcurrentHashMap::class.java.name
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
            for (instruction in block.instructions) {
                when (instruction) {
                    is InvokeInstruction -> {
                        val mappedClass = classNameMap[instruction.method.className]
                        if (mappedClass != null) {
                            instruction.method = MethodReference(mappedClass, instruction.method.descriptor)
                        }
                    }
                    is GetFieldInstruction -> {
                        val mappedClass = classNameMap[instruction.field.className]
                        if (mappedClass != null) {
                            instruction.field = FieldReference(mappedClass, instruction.field.fieldName)
                        }
                    }
                    is PutFieldInstruction -> {
                        val mappedClass = classNameMap[instruction.field.className]
                        if (mappedClass != null) {
                            instruction.field = FieldReference(mappedClass, instruction.field.fieldName)
                        }
                    }
                    is InitClassInstruction -> {
                        val mappedClass = classNameMap[instruction.className]
                        if (mappedClass != null) {
                            instruction.className = mappedClass
                        }
                    }
                    is ConstructInstruction -> {
                        val mappedClass = classNameMap[instruction.type]
                        if (mappedClass != null) {
                            instruction.type = mappedClass
                        }
                    }
                }
            }
        }
    }
}
