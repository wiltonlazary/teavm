define void @teavm.init() {
    %nextObj = getelementptr %vtable.java.lang.Object, %vtable.java.lang.Object* null, i32 1
    %sz = ptrtoint %vtable.java.lang.Object* %nextObj to i32
    %src = bitcast %vtable.java.lang.Object* @vtable.java.lang.Object to i8*
    %dest = bitcast %teavm.Array* @teavm.Array to i8*
    call i8* @memcpy(i8* %dest, i8* %src, i32 %sz)
    ret void
}