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
package org.teavm.llvm.writing;

import java.io.IOException;

public class LLVMType implements LLVMElement {
    private LLVMElement innerWriter;

    LLVMType(LLVMElement innerWriter) {
        this.innerWriter = innerWriter;
    }

    public LLVMType ref() {
        return new LLVMType(out -> {
            innerWriter.write(out);
            out.append("*");
        });
    }

    public LLVMType arrayOf(int count) {
        return new LLVMType(out -> {
            out.append("[" + count + " x ");
            innerWriter.write(out);
            out.append("]");
        });
    }

    public LLVMValue array(LLVMValue... values) {
        return new LLVMValue(this, out -> {
            out.append("[");
            values[0].write(out);
            for (int i = 1; i < values.length; ++i) {
                out.append(", ");
                values[i].write(out);
            }
            out.append("]");
        });
    }

    public LLVMValue var(String name) {
        return new LLVMValue(this, out -> {
            out.append("%");
            out.append(name);
        });
    }

    public LLVMValue global(String name) {
        return new LLVMValue(this, out -> {
            out.append("%");
            out.append(name);
        });
    }

    public LLVMValue nullConstant() {
        return new LLVMValue(this, out -> out.append("null"));
    }

    public LLVMValue zero() {
        return new LLVMValue(this, out -> out.append("zeroinitializer"));
    }

    @Override
    public void write(Appendable out) throws IOException {
        innerWriter.write(out);
    }
}
