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

import java.io.IOException;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.backend.javascript.spi.InjectorProvider;
import org.teavm.model.MethodReference;

class ConstructorInjector implements InjectorProvider, Injector {
    @Override
    public Injector getFor(MethodReference method) {
        return method.getName().equals("<jsinit>") ? this : null;
    }

    @Override
    public void generate(InjectorContext context, MethodReference methodRef) throws IOException {
        SourceWriter writer = context.getWriter();
        writer.append("new ").appendClass(methodRef.getClassName()).append("(");
        for (int i = 0; i < methodRef.parameterCount(); ++i) {
            if (i > 0) {
                writer.append(",").ws();
            }
            context.writeExpr(context.getArgument(i), Precedence.min());
        }
        writer.append(")");
    }
}
