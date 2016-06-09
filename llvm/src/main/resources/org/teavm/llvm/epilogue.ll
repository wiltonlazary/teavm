define void @teavm.init() {
    %nextObj = getelementptr { i32, %vtable.java.lang.Object }, { i32, %vtable.java.lang.Object }* null, i32 1
    %sz = ptrtoint { i32, %vtable.java.lang.Object }* %nextObj to i32
    %src = bitcast { i32, %vtable.java.lang.Object }* @vtable.java.lang.Object to i8*
    %dest = bitcast { i32, %teavm.Array }* @teavm.Array to i8*
    call i8* @memcpy(i8* %dest, i8* %src, i32 %sz)
    ret void
}