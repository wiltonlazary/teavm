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

public class LLVMValue implements LLVMElement {
    private LLVMType type;
    private LLVMElement value;

    LLVMValue(LLVMType type, LLVMElement value) {
        this.type = type;
        this.value = value;
    }

    public LLVMType getType() {
        return type;
    }

    public LLVMElement getValue() {
        return value;
    }

    @Override
    public void write(Appendable out) throws IOException {
        type.write(out);
        out.append(' ');
        type.write(out);
    }

    public LLVMValue add(LLVMValue other) {
        return binary("add", other);
    }

    public LLVMValue fadd(LLVMValue other) {
        return binary("fadd", other);
    }

    public LLVMValue sub(LLVMValue other) {
        return binary("sub", other);
    }

    public LLVMValue fsub(LLVMValue other) {
        return binary("fsub", other);
    }

    public LLVMValue mul(LLVMValue other) {
        return binary("mul", other);
    }

    public LLVMValue fmul(LLVMValue other) {
        return binary("fmul", other);
    }

    public LLVMValue sdiv(LLVMValue other) {
        return binary("sdiv", other);
    }

    public LLVMValue udiv(LLVMValue other) {
        return binary("udiv", other);
    }

    public LLVMValue fdiv(LLVMValue other) {
        return binary("fdiv", other);
    }

    public LLVMValue srem(LLVMValue other) {
        return binary("srem", other);
    }

    public LLVMValue urem(LLVMValue other) {
        return binary("urem", other);
    }

    public LLVMValue frem(LLVMValue other) {
        return binary("frem", other);
    }

    public LLVMValue and(LLVMValue other) {
        return binary("and", other);
    }

    public LLVMValue or(LLVMValue other) {
        return binary("or", other);
    }

    public LLVMValue xor(LLVMValue other) {
        return binary("xor", other);
    }

    public LLVMValue shl(LLVMValue other) {
        return binary("shl", other);
    }

    public LLVMValue ashr(LLVMValue other) {
        return binary("ashr", other);
    }

    public LLVMValue lshr(LLVMValue other) {
        return binary("lshr", other);
    }

    public LLVMValue sext(LLVMType other) {
        return convert("sext", other);
    }

    public LLVMValue zext(LLVMType other) {
        return convert("zext", other);
    }

    public LLVMValue bitcast(LLVMType other) {
        return convert("bitcast", other);
    }

    public LLVMValue trunc(LLVMType other) {
        return convert("trunc", other);
    }

    public LLVMValue sitofp(LLVMType other) {
        return convert("sitofp", other);
    }

    public LLVMValue fptosi(LLVMType other) {
        return convert("fptosi", other);
    }

    public LLVMValue fpexpt(LLVMType other) {
        return convert("fpexpt", other);
    }

    public LLVMValue fptrunc(LLVMType other) {
        return convert("fptrunc", other);
    }

    private LLVMValue binary(String name, LLVMValue other) {
        return new LLVMValue(this.getType(), out -> {
            out.append(name + " ");
            write(out);
            out.append(", ");
            other.getValue().write(out);
        });
    }

    private LLVMValue convert(String name, LLVMType other) {
        return new LLVMValue(this.getType(), out -> {
            out.append(name + " ");
            write(out);
            out.append(" to ");
            other.write(out);
        });
    }
}
