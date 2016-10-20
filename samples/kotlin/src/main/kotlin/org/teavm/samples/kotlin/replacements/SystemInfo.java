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
package org.teavm.samples.kotlin.replacements;

import com.intellij.openapi.util.text.StringUtil;

public final class SystemInfo {
    public static final boolean isWindows = false;
    public static final boolean isMac = false;
    public static final boolean isOS2 = false;
    public static final boolean isLinux = false;
    public static final boolean isFreeBSD = false;
    public static final boolean isSolaris = false;
    public static final boolean isUnix = false;
    public static final String OS_NAME = "JS";
    public static final boolean is32Bit = true;
    public static final boolean isOracleJvm = false;
    public static final boolean isSunJvm = false;
    public static final boolean isAppleJvm = false;
    public static final String JAVA_VERSION = "1.6";
    public static final String JAVA_RUNTIME_VERSION = "1.6";
    public static final boolean isXWindow = false;
    public static final boolean isFileSystemCaseSensitive = true;

    private SystemInfo() {
    }

    public static boolean isJavaVersionAtLeast(String v) {
        return StringUtil.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
    }
}
