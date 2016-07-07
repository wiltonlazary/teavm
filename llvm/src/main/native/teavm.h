typedef struct {
    int tag;
    int reserved;
    int size;
} Object;

struct ClassStruct;

typedef struct {
    struct ClassStruct* parent;
    long count;
    int *offsets;
} FieldLayout;

typedef struct ClassStruct {
    int size;
    int flags;
    int tag;
    int upperTag;
    int magic;
    FieldLayout fields;
} Class;

typedef struct {
    Object object;
    Class *elementType;
} Array;

typedef struct StackFrameStruct {
    int size;
    int callSiteId;
    struct StackFrameStruct *next;
} StackFrame;

typedef struct {
    int size;
    Object ***data;
} StackRoots;

typedef struct {
    int handlerCount;
    Class **exceptionTypes;
} CallSite;

extern StackFrame *teavm_getStackTop();
extern StackRoots *teavm_getStackRoots();
extern CallSite *teavm_getCallSite(int id);

extern Class *teavm_Array();
extern Class *teavm_booleanArray();
extern Class *teavm_byteArray();
extern Class *teavm_shortArray();
extern Class *teavm_charArray();
extern Class *teavm_intArray();
extern Class *teavm_longArray();
extern Class *teavm_floatArray();
extern Class *teavm_doubleArray();

#define FIND_CLASS(cls) ((Class *) (long) ((cls) << 3))
#define OBJECT_CLASS(object) FIND_CLASS(object->tag)