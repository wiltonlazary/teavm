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

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.jso.JSBody;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
@SkipJVM
public class ClassExportTest {
    @Test
    public void simple() {
        assertEquals("Foo", Foo.class.getSimpleName());
        assertEquals(23, testFoo());
    }

    @JSBody(params = {}, script = ""
            + "var Foo = org.teavm.jsinterop.Foo;"
            + "var instance = new Foo();"
            + "return instance.bar();")
    private static native int testFoo();

    @Test
    public void constructorCalled() {
        assertEquals("WithConstructor", WithConstructor.class.getSimpleName());
        assertEquals("1,2;3,null", callConstructor());
    }

    @JSBody(params = {}, script = ""
            + "var WithConstructor = org.teavm.jsinterop.WithConstructor;"
            + "var a = new WithConstructor(1, ',', 2);"
            + "var b = new WithConstructor(3, ',', null);"
            + "return a.test() + ';' + b.test();")
    private static native String callConstructor();

    @Test
    public void exportedByMembers() {
        assertEquals("WithExportedMembers", WithExportedMembers.class.getSimpleName());
        assertEquals(4, callExportedMembers());
    }

    @JSBody(params = {}, script = ""
            + "var WithExportedMembers = org.teavm.jsinterop.WithExportedMembers;"
            + "var a = new WithExportedMembers();"
            + "return a.test(true);")
    private static native int callExportedMembers();

    @Test
    public void exportedByName() {
        assertEquals("ExportedByName", ExportedByName.class.getSimpleName());
        assertEquals(23, callExportedByName());
    }

    @JSBody(params = {}, script = ""
            + "var Bar = foo.Bar;"
            + "var a = new Bar(23);"
            + "return a.getResult();")
    private static native int callExportedByName();
}
