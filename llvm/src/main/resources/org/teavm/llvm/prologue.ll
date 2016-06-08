@teavm.printf.format = private constant [4 x i8] c"%d\0A\00", align 1

%teavm.Object = type {
    i8*              ; header
}

%teavm.Array = type {
    %teavm.Object,   ; parent
    i32,             ; size
    i8,              ; degree
    %teavm.Class *   ; reference to class
}

%teavm.Class = type {
    i32              ; size
}

%teavm.PrimitiveArray = type {
    %teavm.Object,   ; parent
    i32,             ; size
    i8               ; degree
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

define void @method$org.teavm.llvm.TestClass.V6_printfI(i32 %value) {
    call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([4 x i8], [4 x i8]* @teavm.printf.format, i32 0, i32 0), i32 %value)
    ret void
}

declare void @exit(i32)
declare i32 @printf(i8*, ...)
declare i8* @malloc(i32)
