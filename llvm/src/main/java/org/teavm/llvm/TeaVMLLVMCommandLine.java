/*
 *  Copyright 2016 konsoletyper.
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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMToolLog;

public final class TeaVMLLVMCommandLine {
    private TeaVMLLVMCommandLine() {
    }

    public static void main(String[] args) throws IOException {
        ClassLoader classLoader = TeaVMLLVMCommandLine.class.getClassLoader();
        ClasspathClassHolderSource classSource = new ClasspathClassHolderSource(classLoader);
        TeaVMLLVMEmitter emitter = new TeaVMLLVMEmitter(classLoader, classSource);
        emitter.installPlugins();
        emitter.setMainClassName(TestClass.class.getName());
        emitter.build(System.out);

        if (!emitter.getDiagnostics().getSevereProblems().isEmpty()) {
            System.out.println("/*");
            TeaVMProblemRenderer.describeProblems(emitter.getDiagnostics(),
                    emitter.getDependencyInfo().getCallGraph(), log);
            System.out.println("*/");
        } else {
            System.out.println("; no errors occurred");
            List<String> methods = emitter.getDependencyInfo().getReachableMethods().stream()
                    .map(Object::toString).sorted().collect(Collectors.toList());
            for (String method : methods) {
                System.out.println("; METHOD: " + method);
            }
        }

        System.out.println("; end of llvm IR file");
    }

    private static TeaVMToolLog log = new TeaVMToolLog() {
        @Override
        public void info(String text) {
            System.out.println("INFO:" + text);
        }

        @Override
        public void debug(String text) {
            System.out.println("DEBUG:" + text);
        }

        @Override
        public void warning(String text) {
            System.out.println("WARN:" + text);
        }

        @Override
        public void error(String text) {
            System.out.println("ERROR:" + text);
        }

        @Override
        public void info(String text, Throwable e) {
            System.out.println("INFO:" + text);
            e.printStackTrace();
        }

        @Override
        public void debug(String text, Throwable e) {
            System.out.println("DEBUG:" + text);
            e.printStackTrace();
        }

        @Override
        public void warning(String text, Throwable e) {
            System.out.println("WARNING:" + text);
            e.printStackTrace();
        }

        @Override
        public void error(String text, Throwable e) {
            System.out.println("ERROR:" + text);
            e.printStackTrace();
        }
    };
}
