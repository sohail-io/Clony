#include <sys/system_properties.h>
#include <cstring>
#include <pthread.h>
#include "./xdl.h"
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include "Dobby/dobby.h"


#define LOG_TAG "VirtualSpoof"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static const char DEFAULT_BUILD_FINGERPRINT[] =
        "google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys";
static char build_fingerprint[PROP_VALUE_MAX] =
        "google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys";
static pthread_mutex_t build_fingerprint_mutex = PTHREAD_MUTEX_INITIALIZER;

struct SpoofedProp {
    const char* key;
    const char* value;
};

SpoofedProp spoofed_props[] = {
        {"ro.product.model", "Pixel 6"},
        {"ro.product.brand", "google"},
        {"ro.product.manufacturer", "Google"},
        {"ro.product.device", "oriole"},
        {"ro.build.version.release", "12"},
        {"ro.build.version.security_patch", "2022-01-05"},
        {"ro.serialno", "1A2B3C4D5E6F"},
        {"ro.hardware", "qcom"},
        {"ro.boot.hardware", "qcom"},
        {"ro.product.board", "lahaina"},
        {"ro.product.cpu.abi", "arm64-v8a"},
        {"ro.build.type", "user"},
        {"ro.build.tags", "release-keys"},
        {"ro.kernel.qemu", "0"},
        {"ro.kernel.android.qemud", ""},
        {"ro.hardware.egl", "adreno"},
        {"ro.boot.qemu", "0"},
    {nullptr, nullptr} 
};


static int (*orig_system_property_get)(const char *name, char *value) = nullptr;
static int (*orig_system_property_read)(const prop_info *info, char *name, char *value) = nullptr;
typedef void (*SystemPropertyReadCallback)(void *cookie, const char *name,
                                           const char *value, uint32_t serial);
static void (*orig_system_property_read_callback)(
        const prop_info *info, SystemPropertyReadCallback callback, void *cookie) = nullptr;

static size_t copy_build_fingerprint(char *value) {
    pthread_mutex_lock(&build_fingerprint_mutex);
    size_t length = strnlen(build_fingerprint, PROP_VALUE_MAX - 1);
    memcpy(value, build_fingerprint, length);
    value[length] = '\0';
    pthread_mutex_unlock(&build_fingerprint_mutex);
    return length;
}

int my_system_property_get(const char *name, char *value) {
    if (strcmp(name, "ro.build.fingerprint") == 0) {
        size_t length = copy_build_fingerprint(value);
        LOGD("[spoof] %s overridden", name);
        return static_cast<int>(length);
    }

    for (int i = 0; spoofed_props[i].key != nullptr; ++i) {
        if (strcmp(name, spoofed_props[i].key) == 0) {
            strcpy(value, spoofed_props[i].value);
             LOGD("[spoof] %s = %s", name, value);
            return strlen(value);
        }
    }
    if (orig_system_property_get) {
        return orig_system_property_get(name, value);
    }
    value[0] = '\0';
    return 0;
}

int my_system_property_read(const prop_info *info, char *name, char *value) {
    if (orig_system_property_read == nullptr) return 0;
    int length = orig_system_property_read(info, name, value);
    if (length >= 0 && name != nullptr && value != nullptr
            && strcmp(name, "ro.build.fingerprint") == 0) {
        length = static_cast<int>(copy_build_fingerprint(value));
        LOGD("[spoof] %s overridden", name);
    }
    return length;
}

struct PropertyReadCallbackContext {
    SystemPropertyReadCallback callback;
    void *cookie;
};

static void property_read_callback_thunk(
        void *cookie, const char *name, const char *value, uint32_t serial) {
    PropertyReadCallbackContext *context =
            static_cast<PropertyReadCallbackContext *>(cookie);
    if (name != nullptr && strcmp(name, "ro.build.fingerprint") == 0) {
        char fingerprint[PROP_VALUE_MAX];
        copy_build_fingerprint(fingerprint);
        context->callback(context->cookie, name, fingerprint, serial);
        return;
    }
    context->callback(context->cookie, name, value, serial);
}

void my_system_property_read_callback(
        const prop_info *info, SystemPropertyReadCallback callback, void *cookie) {
    if (orig_system_property_read_callback == nullptr || callback == nullptr) return;
    PropertyReadCallbackContext context = {callback, cookie};
    orig_system_property_read_callback(info, property_read_callback_thunk, &context);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_setBuildFingerprint(
        JNIEnv *env, jclass, jstring fingerprint) {
    const char *requested = fingerprint == nullptr ? nullptr : env->GetStringUTFChars(fingerprint, nullptr);
    if (fingerprint != nullptr && requested == nullptr) return JNI_FALSE;

    size_t length = requested == nullptr ? 0 : strlen(requested);
    if (length >= PROP_VALUE_MAX) {
        if (requested != nullptr) env->ReleaseStringUTFChars(fingerprint, requested);
        return JNI_FALSE;
    }
    for (size_t i = 0; i < length; i++) {
        if (requested[i] < 0x21 || requested[i] > 0x7e) {
            if (requested != nullptr) env->ReleaseStringUTFChars(fingerprint, requested);
            return JNI_FALSE;
        }
    }

    pthread_mutex_lock(&build_fingerprint_mutex);
    if (length == 0) {
        memcpy(build_fingerprint, DEFAULT_BUILD_FINGERPRINT, sizeof(DEFAULT_BUILD_FINGERPRINT));
    } else {
        memcpy(build_fingerprint, requested, length);
        build_fingerprint[length] = '\0';
    }
    pthread_mutex_unlock(&build_fingerprint_mutex);
    if (requested != nullptr) env->ReleaseStringUTFChars(fingerprint, requested);

    // Build.FINGERPRINT is cached as a static final field on some Android versions.
    // Set the property first, then update the field for processes where Build was
    // initialized before the native hook was configured.
    jclass buildClass = env->FindClass("android/os/Build");
    if (buildClass == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    }
    jfieldID fingerprintField = env->GetStaticFieldID(
            buildClass, "FINGERPRINT", "Ljava/lang/String;");
    if (fingerprintField == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(buildClass);
        return JNI_FALSE;
    }
    // requested is released above, so build the Java string from the guarded native value.
    pthread_mutex_lock(&build_fingerprint_mutex);
    jstring activeValue = env->NewStringUTF(build_fingerprint);
    pthread_mutex_unlock(&build_fingerprint_mutex);
    if (activeValue == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(buildClass);
        return JNI_FALSE;
    }
    env->SetStaticObjectField(buildClass, fingerprintField, activeValue);
    bool success = !env->ExceptionCheck();
    if (!success) env->ExceptionClear();
    env->DeleteLocalRef(activeValue);
    env->DeleteLocalRef(buildClass);
    if (success) {
        LOGD("Build.FINGERPRINT override applied length=%zu", length);
    } else {
        LOGD("Build.FINGERPRINT field update failed");
    }
    return success ? JNI_TRUE : JNI_FALSE;
}

void install_property_get_hook() {
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    void* getTarget = xdl_dsym(handle, "__system_property_get", nullptr);
    if (getTarget) {
        if (DobbyHook(getTarget, (void*)my_system_property_get, (void**)&orig_system_property_get) == 0) {
            LOGD("Legacy property getter hook installed");
        } else {
            LOGD("Property getter hook failed");
        }
    }

    void* readTarget = xdl_dsym(handle, "__system_property_read", nullptr);
    if (readTarget) {
        if (DobbyHook(readTarget, (void*)my_system_property_read,
                      (void**)&orig_system_property_read) != 0) {
            LOGD("Property reader hook failed");
        }
    }

    void* callbackTarget = xdl_dsym(handle, "__system_property_read_callback", nullptr);
    if (callbackTarget) {
        if (DobbyHook(callbackTarget, (void*)my_system_property_read_callback,
                      (void**)&orig_system_property_read_callback) != 0) {
            LOGD("Property callback hook failed");
        }
    }
    xdl_close(handle);

}


__attribute__((constructor)) void init_virtual_spoof()
{
    install_property_get_hook();
    LOGD("VirtualSpoof: property hooks loaded");
}
