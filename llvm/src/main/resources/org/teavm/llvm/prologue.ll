@teavm.printf.format = private constant [4 x i8] c"%d\0A\00", align 1

%teavm.Object = type {
    i8*              ; header
}

%teavm.Array = type {
    %teavm.Object,   ; parent
    i32,             ; size
    %itable *        ; reference to class
}

define i32 @teavm.cmp.i32(i32 %a, i32 %b) {
    %less = icmp slt i32 %a, %b
    br i1 %less, label %whenLess, label %checkGreater
whenLess:
    ret i32 -1
checkGreater:
    %greater = icmp sgt i32 %a, %b
    br i1 %less, label %whenGreater, label %whenEq
whenGreater:
    ret i32 1
whenEq:
    ret i32 0
}

define void @method$org.teavm.llvm.runtime.LLVM.V7_printlnI(i32 %value) {
    call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([4 x i8], [4 x i8]* @teavm.printf.format, i32 0, i32 0), i32 %value)
    ret void
}

define i32 @method$java.lang.Object.I8_identity(i8* %object) {
    %identity = ptrtoint i8* %object to i32
    ret i32 %identity
}

@teavm.Array = global %teavm.Array zeroinitializer

declare void @exit(i32)
declare i32 @printf(i8*, ...)
declare i8* @malloc(i32)
declare i8* @memcpy(i8*, i8*, i32)