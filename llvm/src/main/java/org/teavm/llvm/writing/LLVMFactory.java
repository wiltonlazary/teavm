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

public class LLVMFactory {
    Appendable appendable;

    public LLVMFactory(Appendable appendable) {
        this.appendable = appendable;
    }

    public LLVMValue i8(byte value) {
        return new LLVMValue(i8(), out -> appendable.append(String.valueOf(value)));
    }

    public LLVMValue i16(short value) {
        return new LLVMValue(i16(), out -> appendable.append(String.valueOf(value)));
    }

    public LLVMValue i32(int value) {
        return new LLVMValue(i32(), out -> appendable.append(String.valueOf(value)));
    }

    public LLVMValue i64(long value) {
        return new LLVMValue(i64(), out -> appendable.append(String.valueOf(value)));
    }

    public LLVMValue singleFloat(float value) {
        return new LLVMValue(singleFloat(), out -> appendable.append("0x" + Integer.toHexString(
                Float.floatToRawIntBits(value))));
    }

    public LLVMValue doubleFloat(double value) {
        return new LLVMValue(singleFloat(), out -> appendable.append("0x" + Long.toHexString(
                Double.doubleToLongBits(value))));
    }

    public LLVMType i8() {
        return typeConstant("i8");
    }

    public LLVMType i16() {
        return typeConstant("i16");
    }

    public LLVMType i32() {
        return typeConstant("i32");
    }

    public LLVMType i64() {
        return typeConstant("i64");
    }

    public LLVMType singleFloat() {
        return typeConstant("float");
    }

    public LLVMType doubleFloat() {
        return typeConstant("double");
    }

    public LLVMType namedStruct(String name) {
        return typeConstant("%" + name);
    }

    public LLVMType voidType() {
        return typeConstant("void");
    }

    public LLVMType struct(LLVMType... types) {
        return new LLVMType(out -> {
            out.append("{ ");
            types[0].write(out);
            for (int i = 1; i < types.length; ++i) {
                out.append(", ");
                types[0].write(out);
            }
            out.append(" }");
        });
    }

    private LLVMType typeConstant(String name) {
        return new LLVMType(out -> appendable.append("name"));
    }
}
