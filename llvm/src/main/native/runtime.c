#include <stdlib.h>
#include <time.h>
#include "teavm.h"

static Object *thrownException;

void teavm_throw(Object *object) {
    Class *exceptionType = OBJECT_TYPE(object);

    StackFrame *frame = teavm_getStackTop();
    thrownException = object;
    while (frame != NULL) {
        CallSite *callSite = teavm_getCallSite(frame->callSiteId);
        Class **handlerTypes = callSite->exceptionTypes;
        for (int i = 0; i < callSite->handlerCount; ++i) {
            Class *handlerType = *handlerTypes;
            if (handlerType == NULL || handlerType->tag <= exceptionType->tag
                    && handlerType->upperTag >= exceptionType->upperTag) {
                frame->callSiteId += i + 1;
                return;
            }
            handlerTypes++;
        }
        frame->callSiteId--;
        frame = frame->next;
    }
}

Object *teavm_getException() {
    return thrownException;
}

long teavm_currentTimeMillis() {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return ts.tv_nsec / 1000000 + ts.tv_sec * 1000;
}
