@teavm.printf.format = private constant [4 x i8] c"%d\0A\00", align 1
@teavm.exceptionOccurred = private constant [26 x i8] c"Exception occurred in %s\0A\00", align 1
@teavm.buffer = private global [4096 x i8] zeroinitializer
@teavm.bufferPos = private global i32 0

%teavm.Object = type {
    i32              ; header
}

%teavm.Array = type {
    %teavm.Object,   ; parent
    i32,             ; size
    %itable*         ; reference to class
}

%teavm.Fields = type {
    %itable*,        ; parent class
    i64,             ; number of fields
    i32*             ; pointer to array of offsets
}

%timespec = type { i32, i64 }

define private i32 @teavm.cmp.i32(i32 %a, i32 %b) {
    %less = icmp slt i32 %a, %b
    br i1 %less, label %whenLess, label %checkGreater
whenLess:
    ret i32 -1
checkGreater:
    %greater = icmp sgt i32 %a, %b
    br i1 %greater, label %whenGreater, label %whenEq
whenGreater:
    ret i32 1
whenEq:
    ret i32 0
}

define private i32 @teavm.cmp.i64(i64 %a, i64 %b) {
    %less = icmp slt i64 %a, %b
    br i1 %less, label %whenLess, label %checkGreater
whenLess:
    ret i32 -1
checkGreater:
    %greater = icmp sgt i64 %a, %b
    br i1 %greater, label %whenGreater, label %whenEq
whenGreater:
    ret i32 1
whenEq:
    ret i32 0
}

define private i32 @teavm.cmp.float(float %a, float %b) {
    %less = fcmp olt float %a, %b
    br i1 %less, label %whenLess, label %checkGreater
whenLess:
    ret i32 -1
checkGreater:
    %greater = fcmp ogt float %a, %b
    br i1 %greater, label %whenGreater, label %whenEq
whenGreater:
    ret i32 1
whenEq:
    ret i32 0
}

define private i32 @teavm.cmp.double(double %a, double %b) {
    %less = fcmp olt double %a, %b
    br i1 %less, label %whenLess, label %checkGreater
whenLess:
    ret i32 -1
checkGreater:
    %greater = fcmp ogt double %a, %b
    br i1 %greater, label %whenGreater, label %whenEq
whenGreater:
    ret i32 1
whenEq:
    ret i32 0
}

%teavm.stackFrame = type { i32, i32, %teavm.stackFrame* }
%teavm.stackRoots = type { i64, i8*** }
@teavm.stackTop = global %teavm.stackFrame* null

define %teavm.stackFrame* @teavm_getStackTop() {
    %ptr = load %teavm.stackFrame*, %teavm.stackFrame** @teavm.stackTop
    ret %teavm.stackFrame* %ptr
}
define %teavm.stackRoots* @teavm_getStackRoots() {
    ret %teavm.stackRoots* @teavm.stackRoots
}

define void @method$org.teavm.llvm.runtime.LLVM.V7_printlnI(i32 %value) {
    call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([4 x i8], [4 x i8]* @teavm.printf.format, i32 0, i32 0), i32 %value)
    ret void
}

define i32 @method$java.lang.Object.I8_identity(i8* %object) {
    %identity = ptrtoint i8* %object to i32
    ret i32 %identity
}
define i8* @method$java.lang.Class.L15_java.lang.Class9_charClass() {
    ret i8* null
}
define i8* @method$java.lang.Class.L15_java.lang.Class8_intClass() {
    ret i8* null
}
define i8* @method$java.lang.Class.L15_java.lang.Class9_longClass() {
    ret i8* null
}
define void @method$org.teavm.llvm.runtime.LLVM.V4_putsABI(i8* %buffer, i32 %offset) {
    %array = bitcast i8* %buffer to %teavm.Array*
    %arrayData = getelementptr %teavm.Array, %teavm.Array* %array, i32 1
    %arrayBytes = bitcast %teavm.Array* %arrayData to i8*
    %adjustedOffset = add i32 %offset, 1
    %start = getelementptr i8, i8* %arrayBytes, i32 %adjustedOffset
    call i32 @puts(i8* %start)
    ret void
}
define void @method$org.teavm.llvm.runtime.LLVM.V7_putCharB(i32 %char) {
    call i32 @putchar(i32 %char)
    ret void
}
define void @method$java.lang.ConsoleOutputStreamStderr.V5_writeI(i8* %this, i32 %char) {
    call i32 @putchar(i32 %char)
    ret void
}
define void @method$java.lang.ConsoleOutputStreamStdout.V5_writeI(i8* %this, i32 %char) {
    call i32 @putchar(i32 %char)
    ret void
}
define i64 @method$java.lang.System.L17_currentTimeMillis() {
    call void @initializer$java.lang.System()
    %result = call i64 @teavm_currentTimeMillis()
    ret i64 %result
}

define i1 @teavm.instanceOf(i8* %object, %itable* %type) {
    ret i1 1
}

@teavm.Array = global %teavm.Array zeroinitializer, align 8
@teavm.booleanArray = global %itable zeroinitializer, align 8
@teavm.byteArray = global %itable zeroinitializer, align 8
@teavm.shortArray = global %itable zeroinitializer, align 8
@teavm.charArray = global %itable zeroinitializer, align 8
@teavm.intArray = global %itable zeroinitializer, align 8
@teavm.longArray = global %itable zeroinitializer, align 8
@teavm.floatArray = global %itable zeroinitializer, align 8
@teavm.doubleArray = global %itable zeroinitializer, align 8

define %teavm.Array* @teavm_Array() {
    ret %teavm.Array* @teavm.Array
}
define %itable* @teavm_booleanArray() {
    ret %itable* @teavm.booleanArray
}
define %itable* @teavm_byteArray() {
    ret %itable* @teavm.byteArray
}
define %itable* @teavm_shortArray() {
    ret %itable* @teavm.shortArray
}
define %itable* @teavm_charArray() {
     ret %itable* @teavm.charArray
}
define %itable* @teavm_intArray() {
    ret %itable* @teavm.intArray
}
define %itable* @teavm_longArray() {
    ret %itable* @teavm.longArray
}
define %itable* @teavm_floatArray() {
    ret %itable* @teavm.floatArray
}
define %itable* @teavm_doubleArray() {
    ret %itable* @teavm.doubleArray
}

%teavm.ExceptionBuffer = type { [ 16 x i32 ], i8* }

@teavm.exceptionTable = global [ 32 x %teavm.ExceptionBuffer ] zeroinitializer
@teavm.exceptionDepth = global i32 0

define i32 @teavm.enterException() {
    %current = load i32, i32* @teavm.exceptionDepth
    %next = add i32 %current, 1
    store i32 %next, i32* @teavm.exceptionDepth
    ret i32 %current
}
define void @teavm.leaveException() {
    %current = load i32, i32* @teavm.exceptionDepth
    %next = sub i32 %current, 1
    store i32 %next, i32* @teavm.exceptionDepth
    ret void
}
define void @teavm.throwException() {
    %current = load i32, i32* @teavm.exceptionDepth
    %buffer = getelementptr [32 x %teavm.ExceptionBuffer], [32 x %teavm.ExceptionBuffer]* @teavm.exceptionTable, i32 0, i32 %current
    call void @longjmp(%teavm.ExceptionBuffer* %buffer, i32 1)
    ret void
}
define i8* @teavm.catchException() {
    %current = call i32 @teavm.enterException()
    %buffer = getelementptr [32 x %teavm.ExceptionBuffer], [32 x %teavm.ExceptionBuffer]* @teavm.exceptionTable, i32 0, i32 %current
    %result = call i32 @setjmp(%teavm.ExceptionBuffer* %buffer)
    %caught = icmp ne i32 %result, 0
    br i1 %caught, label %throw, label %continue
throw:
    %exceptionPtr = getelementptr %teavm.ExceptionBuffer, %teavm.ExceptionBuffer* %buffer, i32 0, i32 1
    %exception = load i8*, i8** %exceptionPtr
    ret i8* %exception
continue:
    ret i8* null
}

declare void @exit(i32)
declare i32 @printf(i8*, ...)
declare i8* @malloc(i32)
declare i8* @memcpy(i8*, i8*, i32)
declare i8* @memset(i8*, i32, i32)
declare i32 @puts(i8*)
declare i32 @putchar(i32)
declare i32 @setjmp(%teavm.ExceptionBuffer*)
declare void @longjmp(%teavm.ExceptionBuffer*, i32)
declare i32 @clock_gettime(i32, %timespec*)
declare i8* @teavm_alloc(i32)
declare i8* @teavm_objectArrayAlloc(i32, i8, i32)
declare i8* @teavm_booleanArrayAlloc(i32)
declare i8* @teavm_byteArrayAlloc(i32)
declare i8* @teavm_shortArrayAlloc(i32)
declare i8* @teavm_charArrayAlloc(i32)
declare i8* @teavm_intArrayAlloc(i32)
declare i8* @teavm_longArrayAlloc(i32)
declare i8* @teavm_floatArrayAlloc(i32)
declare i8* @teavm_doubleArrayAlloc(i32)
declare i64 @teavm_currentTimeMillis()