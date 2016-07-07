#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>
#include <errno.h>
#include <execinfo.h>
#include "teavm.h"

#define TRAVERSAL_STACK_SIZE 512

typedef struct TraversalStackStruct {
    int location;
    Object* data[TRAVERSAL_STACK_SIZE];
    struct TraversalStackStruct* next;
} TraversalStack;

extern long teavm_currentTimeMillis();

static char *pool = NULL;
static char *limit = NULL;
static char *extra = NULL;
static char *mmapLimit = NULL;
static int pageSize;
static int objectCount = 0;
#define EMPTY_TAG 0
#define EMPTY_SHORT_TAG 1;
#define GC_MARK (1 << 31)
#define CLASS_SIZE_MASK (-1 ^ (1 << 31))
static long INITIAL_HEAP_SIZE = 256 * 1024;
static long HEAP_LIMIT = 1024 * 1024 * 1024;
#define SWEEP_PIECE_SIZE 16384
static long MAX_GC_GROW;
static Object** objects = NULL;
static TraversalStack* traversalStack;
static int objectsRemoved = 0;
static Object* currentObject;
static Object* currentLimit;
static int sweepPieceCount;
static unsigned short *sweepPieces;
static int arrayTag;

#define VALID_TAG(cls) (((cls)->tag ^ 0xAAAAAAAA) == cls->magic)

static void printStackTrace() {
    void *pointers[256];
    int stackTraceSize = backtrace(pointers, 256);
    backtrace_symbols_fd(pointers, stackTraceSize, 2);
}

static void *allocExtra(int size) {
    char *next = extra + size;
    if (next > mmapLimit) {
        long memoryRequested = (size / pageSize + 1) * pageSize;
        if (mmap(mmapLimit, memoryRequested, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED,
                -1, 0) == MAP_FAILED) {
            printStackTrace();
            printf("Could not allocate GC working memory (%ld bytes). Error %d\n", memoryRequested, errno);
            exit(2);
        }
        mmapLimit += memoryRequested;
    }
    char *result = extra;
    extra = next;
    return result;
}

static void freeExtra() {
    extra = limit;
}

static long growHeapBy(long size) {
    long memoryRequested = ((size - 1) / pageSize + 1) * pageSize;
    char *mmapResult = mmap(mmapLimit, memoryRequested, PROT_READ | PROT_WRITE,
            MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
    if (mmapResult == MAP_FAILED) {
        printf("Could not grow heap by %ld bytes. Error %d\n", memoryRequested, errno);
        printStackTrace();
        exit(2);
    }
    if (mmapResult != mmapLimit) {
        printf("Could not grow heap by %ld bytes.\n", memoryRequested, errno);
        printStackTrace();
        exit(2);
    }

    int extraSize = (int) ((long) extra - (long) limit);
    if (extraSize > 0) {
        memmove((void *) (limit + memoryRequested), (void *) limit, extraSize);
    }
    limit += memoryRequested;
    extra += memoryRequested;
    mmapLimit += memoryRequested;

#ifdef TEAVM_GC_TRACE
    printf("GC: heap grown by %ld bytes and now it's %ld bytes long\n", memoryRequested, (long) limit - (long) pool);
#endif

    return memoryRequested;
}

static long getHeapSize() {
    return (long) limit - (long) pool;
}

static int growHeap(int atLeastSize) {
    long heapSize = getHeapSize() / 8;
    long growBy = heapSize;
    if (growBy > MAX_GC_GROW) {
        growBy = MAX_GC_GROW;
    }
    if (growBy < atLeastSize) {
        growBy = atLeastSize;
    }
    return (int) growHeapBy(growBy);
}

void teavm_initGC() {
    pageSize = (int) sysconf(_SC_PAGESIZE);
    long alignedHeapSize = (INITIAL_HEAP_SIZE / pageSize) * pageSize;
    pool = (char *) mmap(sbrk(0), alignedHeapSize, PROT_READ | PROT_WRITE, MAP_PRIVATE |  MAP_ANONYMOUS, -1, 0);
    if (pool == MAP_FAILED) {
        printStackTrace();
        printf("Could not initialize heap. Error %d\n", errno);
        exit(2);
    }

    limit = pool + alignedHeapSize;
    extra = limit;
    mmapLimit = limit;
    MAX_GC_GROW = (HEAP_LIMIT / 64 / pageSize) * pageSize;

    Object *root = (Object *) pool;
    int rootSize = alignedHeapSize;
    root->tag = EMPTY_TAG;
    root->size = rootSize;
    currentLimit = (Object *) ((char *) root + rootSize);
    currentObject = root;

    objects = (Object **) allocExtra(sizeof(Object*));
    objects[0] = root;
    objectCount = 1;

    arrayTag = (int) ((long) teavm_Array() >> 3);
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
                Class *cls = OBJECT_CLASS(object);
#ifdef TEAVM_GC_ASSERT
                if (!VALID_TAG(cls)) {
                    printf("GC: not an object 2: %lx \n", (long) object);
                    printStackTrace();
                    exit(255);
                }
#endif
                return cls->size;
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

#ifdef TEAVM_GC_ASSERT
        if (object->tag != 0 && object->tag != 1 && object->tag != arrayTag) {
            Class *cls = OBJECT_CLASS(object);
            if (!VALID_TAG(cls)) {
                printf("GC: not an object: %lx \n", (long) object);
                printStackTrace();
                exit(255);
            }
        }
#endif
        if ((object->tag & GC_MARK) != 0) {
            break;
        }
        object->tag = object->tag | GC_MARK;

#ifdef TEAVM_GC_ASSERT
        if ((char *) object < pool || (char *) object >= limit) {
            printf("GC: violation 3: %lx <= %lx < %lx\n", (long) pool, (long) object, (long) limit);
            printStackTrace();
            exit(255);
        }
#endif

        long offset = (long) object - (long) pool;
        int pieceIndex = (int) (offset / SWEEP_PIECE_SIZE);
        unsigned short pieceOffset = (unsigned short) (offset % SWEEP_PIECE_SIZE);
        if (sweepPieces[pieceIndex] > pieceOffset) {
            sweepPieces[pieceIndex] = pieceOffset;
        }

        char *address = (char *) object;
        Class *cls = OBJECT_CLASS(object);
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
#ifdef TEAVM_GC_TRACE
    printf("GC: running mark\n");
#endif

    sweepPieceCount = (getHeapSize() / SWEEP_PIECE_SIZE / 4 + 1) * 4;
    sweepPieces = (unsigned short *) allocExtra(sizeof(unsigned short) * sweepPieceCount);
    memset(sweepPieces, -1, sizeof(unsigned short) * sweepPieceCount);
    char *sweepPiecesEnd = extra;

    traversalStack = (TraversalStack *) allocExtra(sizeof(TraversalStack));
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

    extra = sweepPiecesEnd;

#ifdef TEAVM_GC_TRACE
    printf("GC: mark complete\n");
#endif
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

static void sweep(int sizeToAllocate) {
#ifdef TEAVM_GC_TRACE
    printf("GC: running sweep\n");
#endif

    objects = (Object **) extra;
    objectCount = 0;

    Object *object = (Object *) pool;
    Object *lastFreeSpace = NULL;
    long heapSize = getHeapSize();
    long reclaimedSpace = 0;
    long maxFreeChunk = 0;
    int currentPieceIndex = 0;
    char *currentPieceEnd = pool + SWEEP_PIECE_SIZE;
    Object *nextNonFreeObject = NULL;

    while ((char *) object < limit) {
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

            if ((char *) object >= currentPieceEnd) {
                currentPieceIndex = (int) (((long) object - (long) pool) / SWEEP_PIECE_SIZE);
                if (sweepPieces[currentPieceIndex] == 0xFFFF) {
                    while (sweepPieces[currentPieceIndex] == 0xFFFF) {
                        if (++currentPieceIndex == sweepPieceCount) {
                            object = (Object *) limit;
                            goto endSweep;
                        }
                    }
                    object = (Object *) (pool + currentPieceIndex * SWEEP_PIECE_SIZE + sweepPieces[currentPieceIndex]);
                    currentPieceEnd = pool + (currentPieceIndex + 1) * (long) SWEEP_PIECE_SIZE;
                    continue;
                }
                currentPieceEnd = pool + (currentPieceIndex + 1) * (long) SWEEP_PIECE_SIZE;
            }
        } else {
            if (lastFreeSpace != NULL) {
                int freeSize = (int) ((char *) object - (char *) lastFreeSpace);
                makeEmpty(lastFreeSpace, freeSize);
                allocExtra(sizeof(Object *));
                objects[objectCount++] = lastFreeSpace;
                lastFreeSpace = NULL;
                reclaimedSpace += freeSize;
                if (maxFreeChunk < freeSize) {
                    maxFreeChunk = freeSize;
                }
            }
        }

        int size = objectSize(tag, object);
        char *address = (char *) object + size;
#ifdef TEAVM_GC_ASSERT
        if ((unsigned long) address > (unsigned long) limit) {
            printf("GC: violation2: %lx[%d] + %d vs %lx\n", (long) object, tag, size, (long) limit);
            printStackTrace();
            exit(255);
        }
        if (address < limit) {
            objectSize(((Object *) address)->tag, (Object *) address);
        }
#endif
        object = (Object *) address;
    }
    endSweep:

    if (lastFreeSpace != NULL) {
        int freeSize = (int) ((char *) object - (char *) lastFreeSpace);
        makeEmpty(lastFreeSpace, freeSize);
        allocExtra(sizeof(Object *));
        objects[objectCount++] = lastFreeSpace;
        reclaimedSpace += freeSize;
        if (maxFreeChunk < freeSize) {
            maxFreeChunk = freeSize;
        }
    }

    if (reclaimedSpace - sizeToAllocate < heapSize / 2 || maxFreeChunk < sizeToAllocate) {
        int growSize = growHeap(sizeToAllocate);
        if (lastFreeSpace == NULL) {
            lastFreeSpace = (Object *) (limit - growSize);
            makeEmpty(lastFreeSpace, growSize);
        } else {
            lastFreeSpace->size += growSize;
        }
        objects = (Object **) ((char *) objects + growSize);
    }

    qsort(objects, objectCount, sizeof(Object *), &compareFreeChunks);
    if (objectCount > 0) {
        currentObject = objects[0];
        int size = currentObject->tag == 0 ? currentObject->size : sizeof(4);
        currentLimit = (Object *)((char *) currentObject + size);
    } else {
        currentObject = NULL;
        currentLimit = NULL;
    }
    freeExtra();

#ifdef TEAVM_GC_TRACE
    printf("GC: sweep complete\n");
#endif
}

static int collectGarbage(int size) {
#ifdef TEAVM_GC_TRACE
    long start = teavm_currentTimeMillis();
    printf("GC: started\n");
#endif
    objectsRemoved = 0;
    mark();
    sweep(size);
#ifdef TEAVM_GC_TRACE
    long end = teavm_currentTimeMillis();
    printf("GC: complete in %ld ms\n", end - start);
#endif
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
    if (!collectGarbage(size + sizeof(Object))) {
        printStackTrace();
        printf("Out of memory\n");
        exit(2);
    }
    chunk = findAvailableChunk(size);
    if (chunk == NULL) {
        printStackTrace();
        printf("Out of memory\n");
        exit(2);
    }
    return chunk;
}

Object *teavm_alloc(int tag) {
    Class *cls = FIND_CLASS(tag);
    int size = cls->size & CLASS_SIZE_MASK;
    char *next = ((char *) currentObject + size);
    Object *chunk = next + sizeof(Object) <= (char *) currentLimit ? currentObject : getAvailableChunk(size);
    currentObject = (Object *) ((char *) chunk + size);

    memset((char *) chunk, 0, size);
    chunk->tag = tag;
    return chunk;
}

Array *teavm_cloneArray(Array *array) {
    int size = arraySize(array);
    char *next = ((char *) currentObject + size);
    Object *chunk = next + sizeof(Object) <= (char *) currentLimit ? currentObject : getAvailableChunk(size);
    currentObject = (Object *) ((char *) chunk + size);

    memcpy(chunk, array, size);
    return (Array *) chunk;
}

static Array *teavm_arrayAlloc(Class* cls, unsigned char depth, int arraySize, int elemSize) {
    int size = alignSize(sizeof(Array) + elemSize * (arraySize + 1));
    char *next = ((char *) currentObject + size);
    Object *chunk = next + sizeof(Object) <= (char *) currentLimit ? currentObject : getAvailableChunk(size);
    currentObject = (Object *) ((char *) chunk + size);

    Array *array = (Array *) chunk;
    memset((char *) array, 0, size);
    array->object.tag = arrayTag;
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
