/*
 *  Copyright 2015 Alexey Andreev.
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
package org.teavm.jso.impl;

import java.io.IOException;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.model.MethodReference;

public class JSBodyGenerator implements Injector, Generator {
    @Override
    public void generate(InjectorContext context, MethodReference methodRef) throws IOException {
        JSBodyRepository emitterRepository = context.getService(JSBodyRepository.class);
        JSBodyEmitter emitter = emitterRepository.emitters.get(methodRef);
        emitter.emit(context);
    }

    @Override
    public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) throws IOException {
        JSBodyRepository emitterRepository = context.getService(JSBodyRepository.class);
        JSBodyEmitter emitter = emitterRepository.emitters.get(methodRef);
        emitter.emit(context, writer, methodRef);
    }
}
