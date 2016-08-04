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
package org.teavm.platform;

import org.teavm.javascript.spi.Remove;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

public abstract class PlatformException implements JSObject {
    @Remove
    private PlatformException() {
    }

    @JSBody(params = "exception", script = ""
            + "var err = ex.$jsException;\n"
            + "if (!err) {\n"
            + "    var err = new Error(\"Java exception thrown\");\n"
            + "    err.$javaException = ex;\n"
            + "    ex.$jsException = err;\n"
            + "}\n"
            + "return err;")
    public static native PlatformException get(PlatformObject exception);

    @JSBody(params = {}, script = "throw this;")
    public native void raise();

    public static void raise(PlatformObject exception) {
        get(exception).raise();
    }
}
