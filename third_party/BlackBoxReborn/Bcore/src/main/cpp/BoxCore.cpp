



#include "BoxCore.h"
#include "Log.h"
#include "IO.h"
#include <jni.h>
#include <pthread.h>
#include <cstring>
#include <new>
#include <JniHook/JniHook.h>
#include <Hook/VMClassLoaderHook.h>
#include <Hook/UnixFileSystemHook.h>
#include <Hook/FileSystemHook.h>
#include <Hook/BinderHook.h>
#include <Hook/DexFileHook.h>
#include <Hook/RuntimeHook.h>
#include "Utils/HexDump.h"
#include "hidden_api.h"

struct {
    JavaVM *vm;
    jclass NativeCoreClass;
    jmethodID getCallingUidId;
    jmethodID redirectPathString;
    jmethodID redirectPathFile;
    jmethodID loadEmptyDex;
    jmethodID loadEmptyDexL;
    int api_level;
} VMEnv;


JNIEnv *getEnv() {
    JNIEnv *env;
    VMEnv.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    return env;
}

JNIEnv *ensureEnvCreated() {
    JNIEnv *env = getEnv();
    if (env == NULL) {
        VMEnv.vm->AttachCurrentThread(&env, NULL);
    }
    return env;
}

int BoxCore::getCallingUid(JNIEnv *env, int orig) {
    env = ensureEnvCreated();
    return env->CallStaticIntMethod(VMEnv.NativeCoreClass, VMEnv.getCallingUidId, orig);
}

jstring BoxCore::redirectPathString(JNIEnv *env, jstring path) {
    env = ensureEnvCreated();
    return (jstring) env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.redirectPathString, path);
}

jobject BoxCore::redirectPathFile(JNIEnv *env, jobject path) {
    env = ensureEnvCreated();
    return env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.redirectPathFile, path);
}

jlongArray BoxCore::loadEmptyDex(JNIEnv *env) {
    env = ensureEnvCreated();
    return (jlongArray) env->CallStaticObjectMethod(VMEnv.NativeCoreClass, VMEnv.loadEmptyDex);
}

int BoxCore::getApiLevel() {
    return VMEnv.api_level;
}

JavaVM *BoxCore::getJavaVM() {
    return VMEnv.vm;
}

void nativeHook(JNIEnv *env) {
    BaseHook::init(env);
    UnixFileSystemHook::init(env);
    FileSystemHook::init();
    VMClassLoaderHook::init(env);

    BinderHook::init(env);
    DexFileHook::init(env);
}

void hideXposed(JNIEnv *env, jclass clazz) {
    ALOGD("set hideXposed");
    VMClassLoaderHook::hideXposed();
}

void init(JNIEnv *env, jobject clazz, jint api_level) {
    ALOGD("NativeCore init.");
    VMEnv.api_level = api_level;
    VMEnv.NativeCoreClass = (jclass) env->NewGlobalRef(env->FindClass(VMCORE_CLASS));
    VMEnv.getCallingUidId = env->GetStaticMethodID(VMEnv.NativeCoreClass, "getCallingUid", "(I)I");
    VMEnv.redirectPathString = env->GetStaticMethodID(VMEnv.NativeCoreClass, "redirectPath",
                                                      "(Ljava/lang/String;)Ljava/lang/String;");
    VMEnv.redirectPathFile = env->GetStaticMethodID(VMEnv.NativeCoreClass, "redirectPath",
                                                    "(Ljava/io/File;)Ljava/io/File;");
    VMEnv.loadEmptyDex = env->GetStaticMethodID(VMEnv.NativeCoreClass, "loadEmptyDex",
                                                "()[J");

    JniHook::InitJniHook(env, api_level);
}

void addIORule(JNIEnv *env, jclass clazz, jstring target_path,
               jstring relocate_path) {
    ALOGD("set addIORule");
    IO::addRule(env->GetStringUTFChars(target_path, JNI_FALSE),
                env->GetStringUTFChars(relocate_path, JNI_FALSE));
}

void enableIO(JNIEnv *env, jclass clazz) {
    ALOGD("set enableIO");
    IO::init(env);
    nativeHook(env);
}

bool disableHiddenApi(JNIEnv *env, jclass clazz) {
    ALOGD("set disableHiddenApi");
    if(!disable_hidden_api(env)){
        ALOGD("set disableHiddenApi Fail!!!");
        return false;
    }
    return true;
}

bool disableResourceLoading(JNIEnv *env, jclass clazz) {
    ALOGD("set disableResourceLoading");
    if(!disable_resource_loading()){
        ALOGD("set disableResourceLoading Fail!!!");
        return false;
    }
    return true;
}

// ART Offset Verifier — returns the raw ArtMethod* for a java.lang.reflect.Method.
//
// NOTE: FromReflectedMethod() returns an ENCODED jmethodID in modern ART (API 31+),
// not a raw ArtMethod* pointer. The real pointer is stored in the `artMethod` long
// field of java.lang.reflect.Executable. This is the same approach used by LSPosed,
// SandHook, and every serious ART hooking framework.
static jlong getArtMethodPtr(JNIEnv *env, jclass, jobject method) {
    if (method == nullptr) return 0;

    // Try java.lang.reflect.Executable.artMethod (API 26+)
    jclass executableClass = env->FindClass("java/lang/reflect/Executable");
    if (!executableClass) { env->ExceptionClear(); }

    // Fallback: java.lang.reflect.AbstractMethod (API < 26)
    if (!executableClass) {
        executableClass = env->FindClass("java/lang/reflect/AbstractMethod");
        if (!executableClass) { env->ExceptionClear(); return 0; }
    }

    jfieldID artMethodField = env->GetFieldID(executableClass, "artMethod", "J");
    if (!artMethodField) {
        env->ExceptionClear();
        ALOGE("getArtMethodPtr: artMethod field not found");
        return 0;
    }

    return env->GetLongField(method, artMethodField);
}

// ART Offset Verifier — reads 4 raw bytes from a native address.
// Used to scan ArtMethod fields and locate access_flags / dex_code_item_offset.
// Only called with pointers obtained from getArtMethodPtr — not arbitrary memory.
static jint readNativeInt(JNIEnv *env, jclass, jlong address) {
    if (address == 0) return 0;
    return *reinterpret_cast<jint *>(address);
}

// Only the listed Binder caches and Activity's read-only FragmentController
// are exposed. JNI field resolution occurs on a new
// attached thread, where ART does not attribute a filtered lookup to an app
// Java stack. This is still a normal checked JNI lookup: missing/blocked fields
// return failure; no ART offsets or raw object memory are inspected or modified.
enum class FrameworkServiceKind { Permission, Vibrator, Fragments };

struct FrameworkServiceAccess {
    JavaVM *vm;
    jobject manager;
    jobject service;
    FrameworkServiceKind kind;
    bool write;
    jobject result;
    bool success;
};

static void accessFrameworkService(JNIEnv *env, FrameworkServiceAccess *request) {
    const char *ownerName;
    const char *interfaceName;
    const char *signature;
    const char *fieldName;
    switch (request->kind) {
        case FrameworkServiceKind::Permission:
            ownerName = "android/permission/PermissionManager";
            interfaceName = "android/permission/IPermissionManager";
            signature = "Landroid/permission/IPermissionManager;";
            fieldName = "mPermissionManager";
            break;
        case FrameworkServiceKind::Vibrator:
            ownerName = "android/os/SystemVibratorManager";
            interfaceName = "android/os/IVibratorManagerService";
            signature = "Landroid/os/IVibratorManagerService;";
            fieldName = "mService";
            break;
        case FrameworkServiceKind::Fragments:
            if (request->write) return;
            ownerName = "android/app/Activity";
            interfaceName = "android/app/FragmentController";
            signature = "Landroid/app/FragmentController;";
            fieldName = "mFragments";
            break;
        default:
            return;
    }
    jclass owner = env->FindClass(ownerName);
    if (owner == nullptr || env->ExceptionCheck()) return;

    bool staticField = false;
    if (!env->IsInstanceOf(request->manager, owner)) {
        // Class<?> is accepted only for the one known ActivityThread cache.
        if (request->kind != FrameworkServiceKind::Permission) return;
        jclass activityThread = env->FindClass("android/app/ActivityThread");
        if (activityThread == nullptr || env->ExceptionCheck()
                || !env->IsSameObject(request->manager, activityThread)) return;
        owner = activityThread;
        fieldName = "sPermissionManager";
        staticField = true;
    }

    jfieldID field = staticField ? env->GetStaticFieldID(owner, fieldName, signature)
                               : env->GetFieldID(owner, fieldName, signature);
    if (field == nullptr || env->ExceptionCheck()) return;

    if (request->write) {
        jclass serviceClass = env->FindClass(interfaceName);
        if (serviceClass == nullptr || env->ExceptionCheck()
                || !env->IsInstanceOf(request->service, serviceClass)) return;
        if (staticField) env->SetStaticObjectField(owner, field, request->service);
        else env->SetObjectField(request->manager, field, request->service);
        if (env->ExceptionCheck()) return;
    }

    jobject actual = staticField ? env->GetStaticObjectField(owner, field)
                                : env->GetObjectField(request->manager, field);
    if (env->ExceptionCheck()) return;
    if (request->write) {
        request->success = env->IsSameObject(actual, request->service);
    } else if (actual != nullptr) {
        request->result = env->NewGlobalRef(actual);
        request->success = request->result != nullptr && !env->ExceptionCheck();
    }
}

static void *frameworkServiceWorker(void *opaque) {
    auto *request = static_cast<FrameworkServiceAccess *>(opaque);
    JNIEnv *env = nullptr;
    if (request->vm->AttachCurrentThread(&env, nullptr) != JNI_OK || env == nullptr) return nullptr;
    accessFrameworkService(env, request);
    if (env->ExceptionCheck()) {
        // A hidden or changed framework declaration is an explicit failed lookup.
        env->ExceptionClear();
        request->success = false;
    }
    request->vm->DetachCurrentThread();
    return nullptr;
}

static jobject frameworkServiceAccess(JNIEnv *env, jobject manager, jstring interfaceName,
                                      jobject service, bool write, bool *success) {
    *success = false;
    if (manager == nullptr || interfaceName == nullptr || VMEnv.vm == nullptr
            || (write && service == nullptr)) return nullptr;
    const char *name = env->GetStringUTFChars(interfaceName, nullptr);
    if (name == nullptr) return nullptr;
    const bool permission = std::strcmp(name, "android.permission.IPermissionManager") == 0;
    const bool vibrator = std::strcmp(name, "android.os.IVibratorManagerService") == 0;
    const bool fragments = std::strcmp(name, "android.app.FragmentController") == 0;
    env->ReleaseStringUTFChars(interfaceName, name);
    if ((!permission && !vibrator && !fragments) || (fragments && write)) return nullptr;
    const FrameworkServiceKind kind = permission ? FrameworkServiceKind::Permission
            : vibrator ? FrameworkServiceKind::Vibrator : FrameworkServiceKind::Fragments;

    // The build disables C++ exceptions. pthread reports creation failure as an
    // error code, unlike std::thread's throwing constructor in this configuration.
    auto *request = new (std::nothrow) FrameworkServiceAccess{
            VMEnv.vm, nullptr, nullptr, kind, write, nullptr, false};
    if (request == nullptr) return nullptr;
    request->manager = env->NewGlobalRef(manager);
    if (write && request->manager != nullptr) request->service = env->NewGlobalRef(service);
    if (request->manager == nullptr || (write && request->service == nullptr)) {
        if (request->manager != nullptr) env->DeleteGlobalRef(request->manager);
        if (request->service != nullptr) env->DeleteGlobalRef(request->service);
        delete request;
        return nullptr;
    }

    pthread_t worker;
    if (pthread_create(&worker, nullptr, frameworkServiceWorker, request) != 0) {
        env->DeleteGlobalRef(request->manager);
        if (request->service != nullptr) env->DeleteGlobalRef(request->service);
        delete request;
        return nullptr;
    }
    if (pthread_join(worker, nullptr) != 0) {
        // Keep references alive if the worker's lifetime cannot be established.
        // This exceptional failure must not expose freed JNI references to it.
        ALOGE("Framework Binder cache worker could not be joined");
        return nullptr;
    }

    *success = request->success;
    jobject result = request->success && request->result != nullptr
            ? env->NewLocalRef(request->result) : nullptr;
    if (request->result != nullptr) env->DeleteGlobalRef(request->result);
    env->DeleteGlobalRef(request->manager);
    if (request->service != nullptr) env->DeleteGlobalRef(request->service);
    delete request;
    return result;
}

static jobject getFrameworkService(JNIEnv *env, jclass, jobject manager, jstring interfaceName) {
    bool success = false;
    return frameworkServiceAccess(env, manager, interfaceName, nullptr, false, &success);
}

static jboolean setFrameworkService(JNIEnv *env, jclass, jobject manager, jstring interfaceName,
                                   jobject service) {
    bool success = false;
    frameworkServiceAccess(env, manager, interfaceName, service, true, &success);
    return success ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod gMethods[] = {
        {"disableHiddenApi",    "()Z",                               (void *) disableHiddenApi},
        {"disableResourceLoading", "()Z",                            (void *) disableResourceLoading},
        {"hideXposed",          "()V",                               (void *) hideXposed},
        {"addIORule",           "(Ljava/lang/String;Ljava/lang/String;)V", (void *) addIORule},
        {"enableIO",            "()V",                               (void *) enableIO},
        {"init",                "(I)V",                              (void *) init},
        {"getArtMethodPtr",     "(Ljava/lang/reflect/Method;)J",     (void *) getArtMethodPtr},
        {"readNativeInt",       "(J)I",                              (void *) readNativeInt},
        {"getFrameworkService", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", (void *) getFrameworkService},
        {"setFrameworkService", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Z", (void *) setFrameworkService},
};

int registerNativeMethods(JNIEnv *env, const char *className,
                          JNINativeMethod *gMethods, int numMethods) {
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

int registerNatives(JNIEnv *env) {
    if (!registerNativeMethods(env, VMCORE_CLASS, gMethods,
                               sizeof(gMethods) / sizeof(gMethods[0])))
        return JNI_FALSE;
    return JNI_TRUE;
}

void registerMethod(JNIEnv *jenv) {
    registerNatives(jenv);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    VMEnv.vm = vm;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_EVERSION;
    }
    registerMethod(env);
    return JNI_VERSION_1_6;
}
