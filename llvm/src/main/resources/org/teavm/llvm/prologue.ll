%teavm.Object = type {
    i32              ; header
}

%teavm.Array = type {
    %teavm.Object,   ; parent
    %teavm.Class *,  ; reference to class
    i8               ; degree
}

%teavm.Class = type {
    i32              ; size
}

%teavm.PrimitiveArray = type {
    %teavm.Object,   ; parent
    i8               ; degree
}