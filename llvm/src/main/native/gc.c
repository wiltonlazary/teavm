#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>

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
    int tag;
    FieldLayout fields;
} Class;

typedef struct {
    Object object;
    Class *elementType;
} Array;

typedef struct StackFrameStruct {
    int size;
    int reserved;
    struct StackFrameStruct *next;
} StackFrame;

typedef struct {
    int size;
    Object ***data;
} StackRoots;

static const int TRAVERSAL_STACK_SIZE = 512;

typedef struct TraversalStackStruct {
    int location;
    Object* data[TRAVERSAL_STACK_SIZE];
    struct TraversalStackStruct* next;
} TraversalStack;

extern StackFrame *teavm_getStackTop();
extern StackRoots* teavm_getStackRoots();
extern Class* teavm_Array();
extern Class* teavm_booleanArray();
extern Class* teavm_byteArray();
extern Class* teavm_shortArray();
extern Class* teavm_charArray();
extern Class* teavm_intArray();
extern Class* teavm_longArray();
extern Class* teavm_floatArray();
extern Class* teavm_doubleArray();
extern long teavm_currentTimeMillis();

static char *pool = NULL;
static char *limit = NULL;
static char *extra = NULL;
static char *mmapLimit = NULL;
static int pageSize;
static int objectCount = 0;
static int EMPTY_TAG = 0;
static int EMPTY_SHORT_TAG = 1;
static int END_TAG = -1;
static int GC_MARK = 1 << 31;
static int CLASS_SIZE_MASK = -1 ^ (1 << 31);
static long INITIAL_HEAP_SIZE = 256 * 1024;
static long HEAP_LIMIT = 1024 * 1024 * 1024;
static long MAX_GC_GROW = HEAP_LIMIT / 64;
static Object** objectsStart = NULL;
static Object** objects = NULL;
static TraversalStack* traversalStack;
static int objectsRemoved = 0;
static Object* currentObject;
static Object* currentLimit;
static int arrayTag;

static void *allocExtra(int size) {
    char *next = extra + size;
    if (next > mmapLimit) {
        long memoryRequested = (size / pageSize + 1) * pageSize;
        mmapLimit = mmap(mmapLimit, memoryRequested, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_FIXED);
    }
    char *result = extra;
    extra = next;
    return result;
}

static void freeExtra() {
    extra = limit;
}

static void growHeapBy(long size) {
    long memoryRequested = (size / pageSize + 1) * pageSize;
    mmap(mmapLimit, memoryRequested, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_FIXED);
    mmapLimit += memoryRequested;
    limit += memoryRequested;
    extra += memoryRequested;
}

static long getHeapSize() {
    return (long) limit - (long) pool;
}

static void growHeap() {
    long heapSize = getHeapSize();
    if (heapSize > MAX_GC_GROW) {
        heapSize = MAX_GC_GROW;
    }
    growHeapBy(heapSize);
}

void teavm_initGC() {
    pageSize = (int) sysconf(_SC_PAGESIZE);
    long alignedHeapSize = (INITIAL_HEAP_SIZE / pageSize) * pageSize;
    pool = (char *) mmap(NULL, alignedHeapSize, PROT_READ | PROT_WRITE, MAP_PRIVATE |  MAP_ANONYMOUS);
    limit = pool + alignedHeapSize;
    extra = limit;
    mmapLimit = limit;
    MAX_GC_GROW = (MAX_GC_GROW / pageSize) * pageSize;

    Object *root = (Object *) pool;
    int rootSize = poolSize - sizeof(Object);
    memset(root, 0, rootSize);
    root->tag = EMPTY_TAG;
    root->size = rootSize;
    currentLimit = (Object *) ((char *) root + rootSize);
    currentObject = root;

    Object *terminator = (Object *) (pool + root->size);
    terminator->tag = END_TAG;
    terminator->size = 0;

    objectsStart = (Object *) allocExtra(sizeof(Object*));
    objects = &objectsStart[0];
    objects[0] = root;
    objectCount = 1;
}

static int alignSize(int size) {
    return (((size - 1) >> 3) + 1) << 3;
}

static int arraySize(Array* array) {
    unsigned char *depthPtr = (unsigned char *) (array + 1);
    int depth = *depthPtr;
    int elementCount = array->object.size;
    int elemSize;
    if (depth == 0) {
        Class *elementType = array->elementType;
        if (elementType == teavm_booleanArray() || elementType == teavm_byteArray()) {
            elemSize = sizeof(char);
        } else if (elementType == teavm_shortArray() || elementType == teavm_charArray()) {
            elemSize = sizeof(short);
        } else if (elementType == teavm_intArray()) {
            elemSize = sizeof(int);
        } else if (elementType == teavm_longArray()) {
            elemSize = sizeof(long);
        } else if (elementType == teavm_floatArray()) {
            elemSize = sizeof(float);
        } else if (elementType == teavm_doubleArray()) {
            elemSize = sizeof(double);
        } else {
            elemSize = sizeof(Object *);
        }
    } else {
        elemSize = sizeof(Object *);
    }
    return alignSize((elementCount + 1) * elemSize + sizeof(Array));
}

static int objectSize(int tag, Object *object) {
    switch (tag) {
        case 0:
            return object->size;
        case 1:
            return sizeof(int);
        default: {
            if (tag == arrayTag) {
                return arraySize((Array *) object);
            } else {
                char *tagAddress = (char *) (long) (object->tag << 3);
                Class *cls = (Class *) tagAddress;
                return cls->size & CLASS_SIZE_MASK;
            }
        }
    }
}

static void pushObject(Object* object) {
    if (traversalStack->location >= TRAVERSAL_STACK_SIZE) {
        TraversalStack* next = allocExtra(sizeof(TraversalStack));
        next->next = traversalStack;
        traversalStack = next;
    }
    traversalStack->data[traversalStack->location++] = object;
}

static Object *popObject() {
    traversalStack->location--;
    if (traversalStack->location < 0) {
        if (traversalStack->next == NULL) {
            traversalStack->location = 0;
            return NULL;
        }
        TraversalStack *next = traversalStack->next;
        free(traversalStack);
        traversalStack = next;
        traversalStack->location--;
    }
    return traversalStack->data[traversalStack->location];
}

static void markObject(Object *object) {
    if (object == NULL) {
        return;
    }

    pushObject(object);
    while (1) {
        object = popObject();
        if (object == NULL) {
            break;
        }
        if ((object->tag & GC_MARK) != 0) {
            break;
        }
        object->tag = object->tag | GC_MARK;

        char *address = (char *) object;
        Class *cls = (Class *) (long) (object->tag << 3);
        while (cls != NULL) {
            int fieldCount = (int) cls->fields.count;
            int *offsets = &cls->fields.offsets[0];
            for (int i = 0; i < fieldCount; ++i) {
                Object **fieldRef = (Object **) (address + offsets[i]);
                Object *field = *fieldRef;
                if (field != NULL && (field->tag & GC_MARK) == 0) {
                    markObject(field);
                }
            }
            cls = cls->fields.parent;
        }
    }
}

static void mark() {
    traversalStack = allocExtra(traversalStack);
    traversalStack->next = NULL;
    traversalStack->location = 0;

    StackRoots *roots = teavm_getStackRoots();
    for (int i = 0; i < roots->size; ++i) {
        markObject(*roots->data[i]);
    }

    StackFrame *stack = teavm_getStackTop();
    while (stack != NULL) {
        Object **frameData = (Object **) (stack + 1);
        for (int i = 0; i < stack->size; ++i) {
            markObject(frameData[i]);
        }
        stack = stack->next;
    }

    freeExtra();
}

static int compareFreeChunks(const void* first, const void *second) {
    Object **a = (Object **) first;
    Object **b = (Object **) second;
    return (*a)->size - (*b)->size;
}

static void makeEmpty(Object *object, int size) {
    if (size == 0) {
        return;
    }
    if (size == sizeof(int)) {
        object->tag = EMPTY_SHORT_TAG;
    } else {
        object->tag = EMPTY_TAG;
        object->size = size;
    }
}

static void sweep() {
    objects = (Object **) extra;
    objectCount = 0;

    Object *object = (Object *) pool;
    Object *lastFreeSpace = NULL;
    long heapSize = getHeapSize();
    long reclaimedSpace = 0;

    while (object->tag != END_TAG) {
        int free = 0;
        int tag = object->tag;
        if (tag == 0 || tag == 1) {
            free = 1;
        } else {
            free = (tag & GC_MARK) == 0;
            if (!free) {
                tag &= (-1 ^ GC_MARK);
                object->tag = tag;
            }
        }

        if (free) {
            if (lastFreeSpace == NULL) {
                lastFreeSpace = object;
            }
        } else {
            if (lastFreeSpace != NULL) {
                int freeSize = (int) ((char *) object - (char *) lastFreeSpace);
                makeEmpty(lastFreeSpace, freeSize);
                allocExtra(sizeof(Object *));
                objects[objectCount++] = lastFreeSpace;
                lastFreeSpace = NULL;
                reclaimedSpace += freeSize;
            }
        }

        int size = objectSize(tag, object);
        char *address = (char *) object + size;
        object = (Object *) address;
    }

    if (lastFreeSpace != NULL) {
        int freeSize = (int) ((char *) object - (char *) lastFreeSpace);
        makeEmpty(lastFreeSpace, freeSize);
        allocExtra(sizeof(Object *));
        objects[objectCount++] = lastFreeSpace;
        reclaimedSpace += freeSize;
    }

    if (reclaimedSpace < heapSize / 2) {
        growHeap();
    }

    qsort(objects, objectCount, sizeof(Object *), &compareFreeChunks);
    if (objectCount > 0) {
        currentObject = objects[0];
        int size = currentObject->tag == 0 ? currentObject->size : sizeof(4);
        currentLimit = (Object *)((char *) currentObject + size);
        memset(currentObject, 0, size);
    } else {
        currentObject = NULL;
        currentLimit = NULL;
    }
    freeExtra();

    //printf("GC: free chunks: %d\n", objectCount);
    for (int i = 0; i < objectCount; ++i) {
        //printf("GC:   chunk %d contains %d bytes\n", i, objects[i]->size);
    }
}

static int collectGarbage() {
    long start = teavm_currentTimeMillis();
    //printf("GC: starting GC\n");
    objectsRemoved = 0;
    mark();
    sweep();
    long end = teavm_currentTimeMillis();
    //printf("GC: GC complete, in %ld ms\n", end - start);
    return 1;
}

static Object *findAvailableChunk(int size) {
    while (1) {
        char *next = (char *) currentObject + size;
        if (next + sizeof(Object) <= (char *) currentLimit || next == (char *) currentLimit) {
            return currentObject;
        }
        makeEmpty(currentObject, (int) ((char *) currentLimit - (char *) currentObject));
        --objectCount;
        ++objects;
        if (objectCount > 0) {
            currentObject = objects[0];
            currentLimit = (Object *) ((char *) currentObject + currentObject->size);
            memset(currentObject, 0, currentObject->size);
        } else {
            return NULL;
        }
    }
}

static Object *getAvailableChunk(int size) {
    Object *chunk = findAvailableChunk(size);
    if (chunk != NULL) {
        return chunk;
    }
    if (!collectGarbage()) {
        printf("Out of memory\n");
        exit(2);
    }
    chunk = findAvailableChunk(size);
    if (chunk == NULL) {
        printf("Out of memory\n");
        exit(2);
    }
    return chunk;
}

Object *teavm_alloc(int tag) {
    Class *cls = (Class *) ((long) tag << 3);
    int size = cls->size & CLASS_SIZE_MASK;
    char *next = ((char *) currentObject + size);
    Object *chunk = next + sizeof(Object) <= (char *) currentLimit ? currentObject : getAvailableChunk(size);
    currentObject = (Object *) ((char *) chunk + size);

    chunk->tag = tag;
    return chunk;
}

static Array *teavm_arrayAlloc(Class* cls, unsigned char depth, int arraySize, int elemSize) {
    int size = alignSize(sizeof(Array) + elemSize * (arraySize + 1));
    char *next = ((char *) currentObject + size);
    Object *chunk = next + sizeof(Object) <= (char *) currentLimit ? currentObject : getAvailableChunk(size);
    currentObject = (Object *) ((char *) chunk + size);

    Array *array = (Array *) chunk;
    array->object.tag = (int) teavm_Array() >> 3;
    array->object.size = arraySize;
    array->elementType = cls;
    unsigned char* depthPtr = (unsigned char *) (array + 1);
    depthPtr[0] = depth;

    return array;
}

Array *teavm_objectArrayAlloc(int tag, unsigned char depth, int size) {
    Class *cls = (Class *) (long) (tag << 3);
    return teavm_arrayAlloc(cls, depth, size, sizeof(Object *));
}
Array *teavm_booleanArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_booleanArray(), 0, size, sizeof(char));
}
Array *teavm_byteArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_byteArray(), 0, size, sizeof(char));
}
Array *teavm_shortArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_shortArray(), 0, size, sizeof(short));
}
Array *teavm_charArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_charArray(), 0, size, sizeof(short));
}
Array *teavm_intArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_intArray(), 0, size, sizeof(int));
}
Array *teavm_longArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_longArray(), 0, size, sizeof(long));
}
Array *teavm_floatArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_floatArray(), 0, size, sizeof(float));
}
Array *teavm_doubleArrayAlloc(int size) {
    return teavm_arrayAlloc(teavm_doubleArray(), 0, size, sizeof(double));
}
