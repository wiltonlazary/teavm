/*
 *  Copyright 2013 Alexey Andreev.
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
"use strict";

function $rt_assertNotNaN(value) {
    if (typeof value === 'number' && isNaN(value)) {
        throw "NaN";
    }
    return value;
}
var $rt_stdoutBuffer = "";
function $rt_putStdout(ch) {
    if (ch == 0xA) {
        if (console) {
            console.info($rt_stdoutBuffer);
        }
        $rt_stdoutBuffer = "";
    } else {
        $rt_stdoutBuffer += String.fromCharCode(ch);
    }
}
var $rt_stderrBuffer = "";
function $rt_putStderr(ch) {
    if (ch == 0xA) {
        if (console) {
            console.info($rt_stderrBuffer);
        }
        $rt_stderrBuffer = "";
    } else {
        $rt_stderrBuffer += String.fromCharCode(ch);
    }
}
function $rt_metadata(data) {
    for (var i = 0; i < data.length; i += 8) {
        var cls = data[i];
        cls.$meta = {};
        var m = cls.$meta;
        m.name = data[i + 1];
        m.binaryName = "L" + m.name + ";";
        var superclass = data[i + 2];
        m.superclass = superclass !== 0 ? superclass : null;
        m.supertypes = data[i + 3];
        if (m.superclass) {
            m.supertypes.push(m.superclass);
            cls.prototype = new m.superclass();
        } else {
            cls.prototype = {};
        }
        var flags = data[i + 4];
        m.enum = (flags & 1) != 0;
        m.primitive = false;
        m.item = null;
        cls.prototype.constructor = cls;
        cls.classObject = null;
        var clinit = data[i + 5];
        cls.$clinit = clinit !== 0 ? clinit : function() {};

        var names = data[i + 6];
        if (!(names instanceof Array)) {
            names = [names];
        }
        for (var j = 0; j < names.length; j = (j + 1) | 0) {
            window[names[j]] = (function(cls, name) {
                return function() {
                    var clinit = cls.$clinit;
                    cls.$clinit = function() {};
                    clinit();
                    return window[name].apply(window, arguments);
                }
            })(cls, names[j]);
        }

        var virtualMethods = data[i + 7];
        for (j = 0; j < virtualMethods.length; j += 2) {
            var name = virtualMethods[j];
            var func = virtualMethods[j + 1];
            if (typeof name === 'string') {
                name = [name];
            }
            for (var k = 0; k < name.length; ++k) {
                cls.prototype[name[k]] = func;
            }
        }

        cls.$array = null;
    }
}
function $rt_threadStarter(f) {
    return function() {
        var args = Array.prototype.slice.apply(arguments);
        $rt_startThread(function() {
            f.apply(this, args);
        });
    }
}
function $rt_mainStarter(f) {
    return function(args) {
        if (!args) {
            args = [];
        }
        var javaArgs = $rt_createArray($rt_objcls(), args.length);
        for (var i = 0; i < args.length; ++i) {
            javaArgs.data[i] = $rt_str(args[i]);
        }
        $rt_threadStarter(f)(javaArgs);
    };
}
var $rt_stringPool_instance;
function $rt_stringPool(strings) {
    $rt_stringPool_instance = new Array(strings.length);
    for (var i = 0; i < strings.length; ++i) {
        $rt_stringPool_instance[i] = $rt_intern($rt_str(strings[i]));
    }
}
function $rt_s(index) {
    return $rt_stringPool_instance[index];
}
function TeaVMThread(runner) {
    this.status = 3;
    this.stack = [];
    this.suspendCallback = null;
    this.runner = runner;
    this.attribute = null;
    this.completeCallback = null;
}
TeaVMThread.prototype.push = function() {
    for (var i = 0; i < arguments.length; ++i) {
        this.stack.push(arguments[i]);
    }
    return this;
};
TeaVMThread.prototype.s = TeaVMThread.prototype.push;
TeaVMThread.prototype.pop = function() {
    return this.stack.pop();
};
TeaVMThread.prototype.l = TeaVMThread.prototype.pop;
TeaVMThread.prototype.isResuming = function() {
    return this.status == 2;
};
TeaVMThread.prototype.isSuspending = function() {
    return this.status == 1;
};
TeaVMThread.prototype.suspend = function(callback) {
    this.suspendCallback = callback;
    this.status = 1;
};
TeaVMThread.prototype.start = function(callback) {
    if (this.status != 3) {
        throw new Error("Thread already started");
    }
    if ($rt_currentNativeThread !== null) {
        throw new Error("Another thread is running");
    }
    this.status = 0;
    this.completeCallback = callback ? callback : function(result) {
        if (result instanceof Error) {
            throw result;
        }
    };
    this.run();
};
TeaVMThread.prototype.resume = function() {
    if ($rt_currentNativeThread !== null) {
        throw new Error("Another thread is running");
    }
    this.status = 2;
    this.run();
};
TeaVMThread.prototype.run = function() {
    $rt_currentNativeThread = this;
    var result;
    try {
        result = this.runner();
    } catch (e) {
        result = e;
    } finally {
        $rt_currentNativeThread = null;
    }
    if (this.suspendCallback !== null) {
        var self = this;
        var callback = this.suspendCallback;
        this.suspendCallback = null;
        callback(function() {
            self.resume();
        });
    } else if (this.status === 0) {
        this.completeCallback(result);
    }
};
function $rt_suspending() {
    var thread = $rt_nativeThread();
    return thread != null && thread.isSuspending();
}
function $rt_resuming() {
    var thread = $rt_nativeThread();
    return thread != null && thread.isResuming();
}
function $rt_suspend(callback) {
    return $rt_nativeThread().suspend(callback);
}
function $rt_startThread(runner, callback) {
    new TeaVMThread(runner).start(callback);
}
var $rt_currentNativeThread = null;
function $rt_nativeThread() {
    return $rt_currentNativeThread;
}
function $rt_invalidPointer() {
    throw new Error("Invalid recorded state");
}

function $dbg_repr(obj) {
    return obj.toString ? obj.toString() : "";
}
function $dbg_class(obj) {
    if (obj instanceof Long) {
        return "long";
    }
    var cls = obj.constructor;
    var arrayDegree = 0;
    while (cls.$meta && cls.$meta.item) {
        ++arrayDegree;
        cls = cls.$meta.item;
    }
    var clsName = "";
    if (cls === $rt_booleancls()) {
        clsName = "boolean";
    } else if (cls === $rt_bytecls()) {
        clsName = "byte";
    } else if (cls === $rt_shortcls()) {
        clsName = "short";
    } else if (cls === $rt_charcls()) {
        clsName = "char";
    } else if (cls === $rt_intcls()) {
        clsName = "int";
    } else if (cls === $rt_longcls()) {
        clsName = "long";
    } else if (cls === $rt_floatcls()) {
        clsName = "float";
    } else if (cls === $rt_doublecls()) {
        clsName = "double";
    } else {
        clsName = cls.$meta ? cls.$meta.name : "@" + cls.name;
    }
    while (arrayDegree-- > 0) {
        clsName += "[]";
    }
    return clsName;
}

function Long_compare(a, b) {
    var r = a.hi - b.hi;
    if (r !== 0) {
        return r;
    }
    r = (a.lo >>> 1) - (b.lo >>> 1);
    if (r !== 0) {
        return r;
    }
    return (a.lo & 1) - (b.lo & 1);
}
function Long_isPositive(a) {
    return (a.hi & 0x80000000) === 0;
}
function Long_isNegative(a) {
    return (a.hi & 0x80000000) !== 0;
}
function Long_mul(a, b) {
    var positive = Long_isNegative(a) === Long_isNegative(b);
    if (Long_isNegative(a)) {
        a = Long_neg(a);
    }
    if (Long_isNegative(b)) {
        b = Long_neg(b);
    }
    var a_lolo = a.lo & 0xFFFF;
    var a_lohi = a.lo >>> 16;
    var a_hilo = a.hi & 0xFFFF;
    var a_hihi = a.hi >>> 16;
    var b_lolo = b.lo & 0xFFFF;
    var b_lohi = b.lo >>> 16;
    var b_hilo = b.hi & 0xFFFF;
    var b_hihi = b.hi >>> 16;

    var lolo = 0;
    var lohi = 0;
    var hilo = 0;
    var hihi = 0;
    lolo = (a_lolo * b_lolo) | 0;
    lohi = lolo >>> 16;
    lohi = ((lohi & 0xFFFF) + a_lohi * b_lolo) | 0;
    hilo = (hilo + (lohi >>> 16)) | 0;
    lohi = ((lohi & 0xFFFF) + a_lolo * b_lohi) | 0;
    hilo = (hilo + (lohi >>> 16)) | 0;
    hihi = hilo >>> 16;
    hilo = ((hilo & 0xFFFF) + a_hilo * b_lolo) | 0;
    hihi = (hihi + (hilo >>> 16)) | 0;
    hilo = ((hilo & 0xFFFF) + a_lohi * b_lohi) | 0;
    hihi = (hihi + (hilo >>> 16)) | 0;
    hilo = ((hilo & 0xFFFF) + a_lolo * b_hilo) | 0;
    hihi = (hihi + (hilo >>> 16)) | 0;
    hihi = (hihi + a_hihi * b_lolo + a_hilo * b_lohi + a_lohi * b_hilo + a_lolo * b_hihi) | 0;
    var result = new Long((lolo & 0xFFFF) | (lohi << 16), (hilo & 0xFFFF) | (hihi << 16));
    return positive ? result : Long_neg(result);
}
function Long_div(a, b) {
    if (Math.abs(a.hi) < Long_MAX_NORMAL && Math.abs(b.hi) < Long_MAX_NORMAL) {
        return Long_fromNumber(Long_toNumber(a) / Long_toNumber(b));
    }
    return Long_divRem(a, b)[0];
}
function Long_rem(a, b) {
    if (Math.abs(a.hi) < Long_MAX_NORMAL && Math.abs(b.hi) < Long_MAX_NORMAL) {
        return Long_fromNumber(Long_toNumber(a) % Long_toNumber(b));
    }
    return Long_divRem(a, b)[1];
}
function Long_divRem(a, b) {
    if (b.lo == 0 && b.hi == 0) {
        throw new Error("Division by zero");
    }
    var positive = Long_isNegative(a) === Long_isNegative(b);
    if (Long_isNegative(a)) {
        a = Long_neg(a);
    }
    if (Long_isNegative(b)) {
        b = Long_neg(b);
    }
    a = new LongInt(a.lo, a.hi, 0);
    b = new LongInt(b.lo, b.hi, 0);
    var q = LongInt_div(a, b);
    a = new Long(a.lo, a.hi);
    q = new Long(q.lo, q.hi);
    return positive ? [q, a] : [Long_neg(q), Long_neg(a)];
}
function Long_shiftLeft16(a) {
    return new Long(a.lo << 16, (a.lo >>> 16) | (a.hi << 16));
}
function Long_shiftRight16(a) {
    return new Long((a.lo >>> 16) | (a.hi << 16), a.hi >>> 16);
}
function Long_and(a, b) {
    return new Long(a.lo & b.lo, a.hi & b.hi);
}
function Long_or(a, b) {
    return new Long(a.lo | b.lo, a.hi | b.hi);
}
function Long_xor(a, b) {
    return new Long(a.lo ^ b.lo, a.hi ^ b.hi);
}
function Long_shl(a, b) {
    b &= 63;
    if (b == 0) {
        return a;
    } else if (b < 32) {
        return new Long(a.lo << b, (a.lo >>> (32 - b)) | (a.hi << b));
    } else if (b == 32) {
        return new Long(0, a.lo);
    } else {
        return new Long(0, a.lo << (b - 32));
    }
}
function Long_shr(a, b) {
    b &= 63;
    if (b == 0) {
        return a;
    } else if (b < 32) {
        return new Long((a.lo >>> b) | (a.hi << (32 - b)), a.hi >> b);
    } else if (b == 32) {
        return new Long(a.hi, a.hi >> 31);
    } else {
        return new Long((a.hi >> (b - 32)), a.hi >> 31);
    }
}
function Long_shru(a, b) {
    b &= 63;
    if (b == 0) {
        return a;
    } else if (b < 32) {
        return new Long((a.lo >>> b) | (a.hi << (32 - b)), a.hi >>> b);
    } else if (b == 32) {
        return new Long(a.hi, 0);
    } else {
        return new Long((a.hi >>> (b - 32)), 0);
    }
}
