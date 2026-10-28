#include <assert.h>
#include <bare-kit.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

typedef struct {
  jobject receiver;
  jmethodID method;
} bare_kit_callback_t;

// Set by the first worklet, as `JNI_GetCreatedJavaVMs()` needs API level 31.
static JavaVM *bare_kit__vm = NULL;

static JNIEnv *bare_kit__env(void) {
  // Bare Kit attaches the threads it calls back on to the JVM.
  JNIEnv *env;
  int err =
      (*bare_kit__vm)->GetEnv(bare_kit__vm, (void **)&env, JNI_VERSION_1_6);
  assert(err == JNI_OK);

  return env;
}

static bare_kit_callback_t *bare_kit__callback_create(JNIEnv *env,
                                                      jobject receiver,
                                                      const char *name,
                                                      const char *signature) {
  bare_kit_callback_t *callback = malloc(sizeof(bare_kit_callback_t));
  assert(callback != NULL);

  jclass class = (*env)->GetObjectClass(env, receiver);

  callback->receiver = (*env)->NewGlobalRef(env, receiver);
  callback->method = (*env)->GetMethodID(env, class, name, signature);
  assert(callback->method != NULL);

  (*env)->DeleteLocalRef(env, class);

  return callback;
}

static void bare_kit__callback_destroy(JNIEnv *env,
                                       bare_kit_callback_t *callback) {
  (*env)->DeleteGlobalRef(env, callback->receiver);

  free(callback);
}

// The callbacks catch what they throw, so anything pending is a bug that must
// not unwind into native code.
static void bare_kit__callback_call(JNIEnv *env, bare_kit_callback_t *callback,
                                    jint arg) {
  (*env)->CallVoidMethod(env, callback->receiver, callback->method, arg);

  if ((*env)->ExceptionCheck(env)) {
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
  }
}

static void bare_kit__throw(JNIEnv *env, int status) {
  jclass class =
      (*env)->FindClass(env, "to/holepunch/bare/kit/BareKitException$Failed");
  assert(class != NULL);

  jmethodID init = (*env)->GetMethodID(env, class, "<init>", "(I)V");
  assert(init != NULL);

  jthrowable exception = (*env)->NewObject(env, class, init, (jint)status);

  (*env)->Throw(env, exception);

  (*env)->DeleteLocalRef(env, exception);
  (*env)->DeleteLocalRef(env, class);
}

static inline void *bare_kit__pointer(jlong handle) {
  return (void *)(intptr_t)handle;
}

static inline jlong bare_kit__handle(void *pointer) {
  return (jlong)(intptr_t)pointer;
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_optimizeForMemory(
    JNIEnv *env, jclass class, jboolean enabled) {
  bare_worklet_optimize_for_memory(enabled);
}

JNIEXPORT void JNICALL
Java_to_holepunch_bare_kit_Native_teardown(JNIEnv *env, jclass class) {
  bare_worklet_teardown();
}

JNIEXPORT jlong JNICALL Java_to_holepunch_bare_kit_Native_workletCreate(
    JNIEnv *env, jclass class, jlong memory_limit, jobject context) {
  if (bare_kit__vm == NULL)
    (*env)->GetJavaVM(env, &bare_kit__vm);

  bare_worklet_options_t options = {
      .version = 0,
      .memory_limit = (size_t)memory_limit,
      .android =
          {
              .jvm = bare_kit__vm,
              .context = context,
          },
  };

  bare_worklet_t *worklet;
  int err = bare_worklet_create(&options, &worklet);

  if (err < 0) {
    bare_kit__throw(env, err);

    return 0;
  }

  return bare_kit__handle(worklet);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_workletDestroy(
    JNIEnv *env, jclass class, jlong handle) {
  bare_worklet_destroy(bare_kit__pointer(handle));
}

static void bare_kit__on_exit(int code, void *data) {
  JNIEnv *env = bare_kit__env();

  bare_kit_callback_t *callback = data;

  bare_kit__callback_call(env, callback, code);

  bare_kit__callback_destroy(env, callback);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_workletStart(
    JNIEnv *env, jclass class, jlong handle, jobject receiver, jstring filename,
    jbyteArray source, jobjectArray arguments) {
  int err;

  bare_worklet_t *worklet = bare_kit__pointer(handle);

  bare_kit_callback_t *exit =
      bare_kit__callback_create(env, receiver, "onExit", "(I)V");

  err = bare_worklet_on_exit(worklet, bare_kit__on_exit, exit);
  assert(err == 0);

  int argc = (*env)->GetArrayLength(env, arguments);

  jstring *args = calloc(argc, sizeof(jstring));
  const char **argv = calloc(argc + 1, sizeof(char *));

  for (int i = 0; i < argc; i++) {
    args[i] = (*env)->GetObjectArrayElement(env, arguments, i);
    argv[i] = (*env)->GetStringUTFChars(env, args[i], NULL);
  }

  const char *path = (*env)->GetStringUTFChars(env, filename, NULL);

  if (source == NULL) {
    err = bare_worklet_start(worklet, path, NULL, 0, argc, argv);
  } else {
    jbyte *bytes = (*env)->GetByteArrayElements(env, source, NULL);

    err = bare_worklet_start(worklet, path, bytes,
                             (size_t)(*env)->GetArrayLength(env, source), argc,
                             argv);

    (*env)->ReleaseByteArrayElements(env, source, bytes, JNI_ABORT);
  }

  (*env)->ReleaseStringUTFChars(env, filename, path);

  for (int i = 0; i < argc; i++) {
    (*env)->ReleaseStringUTFChars(env, args[i], argv[i]);
    (*env)->DeleteLocalRef(env, args[i]);
  }

  free(argv);
  free(args);

  if (err < 0) {
    bare_kit__callback_destroy(env, exit);

    bare_kit__throw(env, err);
  }
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_workletSuspend(
    JNIEnv *env, jclass class, jlong handle, jint linger) {
  bare_worklet_suspend(bare_kit__pointer(handle), linger);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_workletResume(
    JNIEnv *env, jclass class, jlong handle) {
  bare_worklet_resume(bare_kit__pointer(handle));
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_workletWakeup(
    JNIEnv *env, jclass class, jlong handle, jint deadline) {
  bare_worklet_wakeup(bare_kit__pointer(handle), deadline);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_workletTerminate(
    JNIEnv *env, jclass class, jlong handle) {
  bare_worklet_terminate(bare_kit__pointer(handle));
}

JNIEXPORT jlong JNICALL Java_to_holepunch_bare_kit_Native_ipcCreate(
    JNIEnv *env, jclass class, jlong worklet) {
  bare_ipc_t *ipc;
  int err = bare_ipc_create(bare_kit__pointer(worklet), &ipc);

  if (err < 0) {
    bare_kit__throw(env, err);

    return 0;
  }

  return bare_kit__handle(ipc);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_ipcClose(
    JNIEnv *env, jclass class, jlong handle) {
  bare_ipc_close(bare_kit__pointer(handle));
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_ipcEnd(JNIEnv *env,
                                                                jclass class,
                                                                jlong handle) {
  bare_ipc_end(bare_kit__pointer(handle));
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_ipcDestroy(
    JNIEnv *env, jclass class, jlong handle) {
  bare_ipc_destroy(bare_kit__pointer(handle));
}

JNIEXPORT jint JNICALL Java_to_holepunch_bare_kit_Native_ipcRead(
    JNIEnv *env, jclass class, jlong handle, jbyteArray buffer) {
  jsize len = (*env)->GetArrayLength(env, buffer);

  void *data = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);

  int read = bare_ipc_read(bare_kit__pointer(handle), data, (size_t)len);

  (*env)->ReleasePrimitiveArrayCritical(env, buffer, data, 0);

  return read;
}

JNIEXPORT jint JNICALL Java_to_holepunch_bare_kit_Native_ipcWrite(
    JNIEnv *env, jclass class, jlong handle, jbyteArray buffer, jint offset,
    jint len) {
  jbyte *data = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);

  int written =
      bare_ipc_write(bare_kit__pointer(handle), data + offset, (size_t)len);

  (*env)->ReleasePrimitiveArrayCritical(env, buffer, data, JNI_ABORT);

  return written;
}

JNIEXPORT jlong JNICALL Java_to_holepunch_bare_kit_Native_pollCreate(
    JNIEnv *env, jclass class, jlong ipc, jobject receiver) {
  bare_ipc_poll_t *poll;
  int err = bare_ipc_poll_create(bare_kit__pointer(ipc), &poll);

  if (err < 0) {
    bare_kit__throw(env, err);

    return 0;
  }

  bare_ipc_poll_set_data(
      poll, bare_kit__callback_create(env, receiver, "onPoll", "(I)V"));

  return bare_kit__handle(poll);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_pollDestroy(
    JNIEnv *env, jclass class, jlong handle) {
  bare_ipc_poll_t *poll = bare_kit__pointer(handle);

  bare_kit_callback_t *callback = bare_ipc_poll_get_data(poll);

  bare_ipc_poll_destroy(poll);

  bare_kit__callback_destroy(env, callback);
}

static void bare_kit__on_poll(bare_ipc_poll_t *poll, int events) {
  bare_kit__callback_call(bare_kit__env(), bare_ipc_poll_get_data(poll),
                          events);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_pollStart(
    JNIEnv *env, jclass class, jlong handle, jint events) {
  bare_ipc_poll_start(bare_kit__pointer(handle), events, bare_kit__on_poll);
}

JNIEXPORT void JNICALL Java_to_holepunch_bare_kit_Native_pollStop(
    JNIEnv *env, jclass class, jlong handle) {
  bare_ipc_poll_stop(bare_kit__pointer(handle));
}
