#include <sys/system_properties.h>
#include <cstring>
#include <cstdlib>
#include <limits>
#include <mutex>
#include <string>
#include <unordered_map>
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
static char device_model[PROP_VALUE_MAX] = "Pixel 6";
static pthread_mutex_t device_model_mutex = PTHREAD_MUTEX_INITIALIZER;
static std::unordered_map<std::string, std::string> custom_build_props;
static std::mutex custom_build_props_mutex;

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

static size_t copy_device_model(char *value) {
    pthread_mutex_lock(&device_model_mutex);
    size_t length = strnlen(device_model, PROP_VALUE_MAX - 1);
    memcpy(value, device_model, length);
    value[length] = '\0';
    pthread_mutex_unlock(&device_model_mutex);
    return length;
}

static bool copy_custom_build_prop(const char *name, char *value) {
    if (name == nullptr || value == nullptr) return false;
    std::lock_guard<std::mutex> lock(custom_build_props_mutex);
    auto entry = custom_build_props.find(name);
    if (entry == custom_build_props.end()) return false;
    size_t length = entry->second.size();
    memcpy(value, entry->second.c_str(), length);
    value[length] = '\0';
    return true;
}

static const char *system_property_for_build_prop(const char *name) {
    if (strcmp(name, "deviceName") == 0) return "ro.product.marketname";
    if (strcmp(name, "manufacturer") == 0) return "ro.product.manufacturer";
    if (strcmp(name, "brand") == 0) return "ro.product.brand";
    if (strcmp(name, "model") == 0) return "ro.product.model";
    if (strcmp(name, "product") == 0) return "ro.product.name";
    if (strcmp(name, "device") == 0) return "ro.product.device";
    if (strcmp(name, "board") == 0) return "ro.product.board";
    if (strcmp(name, "radio") == 0) return "gsm.version.baseband";
    if (strcmp(name, "bootloader") == 0) return "ro.bootloader";
    if (strcmp(name, "fingerprint") == 0) return "ro.build.fingerprint";
    if (strcmp(name, "build") == 0) return "ro.build.id";
    if (strcmp(name, "host") == 0) return "ro.build.host";
    if (strcmp(name, "time") == 0) return "ro.build.date.utc";
    if (strcmp(name, "release") == 0) return "ro.build.version.release";
    if (strcmp(name, "sdkInt") == 0) return "ro.build.version.sdk";
    return nullptr;
}

static const char *build_field_for_build_prop(const char *name) {
    if (strcmp(name, "manufacturer") == 0) return "MANUFACTURER";
    if (strcmp(name, "brand") == 0) return "BRAND";
    if (strcmp(name, "model") == 0 || strcmp(name, "deviceName") == 0) return "MODEL";
    if (strcmp(name, "product") == 0) return "PRODUCT";
    if (strcmp(name, "device") == 0) return "DEVICE";
    if (strcmp(name, "board") == 0) return "BOARD";
    if (strcmp(name, "bootloader") == 0) return "BOOTLOADER";
    if (strcmp(name, "fingerprint") == 0) return "FINGERPRINT";
    if (strcmp(name, "build") == 0) return "ID";
    if (strcmp(name, "host") == 0) return "HOST";
    return nullptr;
}

static bool set_build_string_field(JNIEnv *env, jclass buildClass,
                                   const char *fieldName, const char *value) {
    if (fieldName == nullptr || value == nullptr) return true;
    jfieldID field = env->GetStaticFieldID(buildClass, fieldName, "Ljava/lang/String;");
    if (field == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    jstring stringValue = env->NewStringUTF(value);
    if (stringValue == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    env->SetStaticObjectField(buildClass, field, stringValue);
    bool success = !env->ExceptionCheck();
    if (!success) env->ExceptionClear();
    env->DeleteLocalRef(stringValue);
    return success;
}

static bool set_version_string_field(JNIEnv *env, jclass versionClass,
                                     const char *fieldName, const char *value) {
    if (fieldName == nullptr || value == nullptr) return true;
    jfieldID field = env->GetStaticFieldID(versionClass, fieldName, "Ljava/lang/String;");
    if (field == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    jstring stringValue = env->NewStringUTF(value);
    if (stringValue == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    env->SetStaticObjectField(versionClass, field, stringValue);
    bool success = !env->ExceptionCheck();
    if (!success) env->ExceptionClear();
    env->DeleteLocalRef(stringValue);
    return success;
}

static bool set_version_int_field(JNIEnv *env, jclass versionClass,
                                  const char *fieldName, jint value) {
    if (fieldName == nullptr) return true;
    jfieldID field = env->GetStaticFieldID(versionClass, fieldName, "I");
    if (field == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    env->SetStaticIntField(versionClass, field, value);
    bool success = !env->ExceptionCheck();
    if (!success) env->ExceptionClear();
    return success;
}

int my_system_property_get(const char *name, char *value) {
    if (copy_custom_build_prop(name, value)) {
        LOGD("[build props] %s overridden", name);
        return static_cast<int>(strlen(value));
    }
    if (strcmp(name, "ro.build.fingerprint") == 0) {
        size_t length = copy_build_fingerprint(value);
        LOGD("[spoof] %s overridden", name);
        return static_cast<int>(length);
    }
    if (strcmp(name, "ro.product.model") == 0) {
        size_t length = copy_device_model(value);
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
            && copy_custom_build_prop(name, value)) {
        length = static_cast<int>(strlen(value));
        LOGD("[build props] %s overridden", name);
    } else if (length >= 0 && name != nullptr && value != nullptr
            && strcmp(name, "ro.build.fingerprint") == 0) {
        length = static_cast<int>(copy_build_fingerprint(value));
        LOGD("[spoof] %s overridden", name);
    } else if (length >= 0 && name != nullptr && value != nullptr
            && strcmp(name, "ro.product.model") == 0) {
        length = static_cast<int>(copy_device_model(value));
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
    char customValue[PROP_VALUE_MAX];
    if (copy_custom_build_prop(name, customValue)) {
        context->callback(context->cookie, name, customValue, serial);
        return;
    }
    if (name != nullptr && strcmp(name, "ro.build.fingerprint") == 0) {
        char fingerprint[PROP_VALUE_MAX];
        copy_build_fingerprint(fingerprint);
        context->callback(context->cookie, name, fingerprint, serial);
        return;
    }
    if (name != nullptr && strcmp(name, "ro.product.model") == 0) {
        char model[PROP_VALUE_MAX];
        copy_device_model(model);
        context->callback(context->cookie, name, model, serial);
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

extern "C" JNIEXPORT jboolean JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_setDeviceModel(
        JNIEnv *env, jclass, jstring model) {
    const char *requested = model == nullptr ? nullptr : env->GetStringUTFChars(model, nullptr);
    if (model != nullptr && requested == nullptr) return JNI_FALSE;

    size_t length = requested == nullptr ? 0 : strlen(requested);
    if (length == 0 || length >= PROP_VALUE_MAX) {
        if (requested != nullptr) env->ReleaseStringUTFChars(model, requested);
        return JNI_FALSE;
    }
    for (size_t i = 0; i < length; i++) {
        if (requested[i] < 0x20 || requested[i] > 0x7e) {
            if (requested != nullptr) env->ReleaseStringUTFChars(model, requested);
            return JNI_FALSE;
        }
    }

    pthread_mutex_lock(&device_model_mutex);
    memcpy(device_model, requested, length);
    device_model[length] = '\0';
    pthread_mutex_unlock(&device_model_mutex);
    env->ReleaseStringUTFChars(model, requested);

    jclass buildClass = env->FindClass("android/os/Build");
    if (buildClass == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    }
    jfieldID modelField = env->GetStaticFieldID(buildClass, "MODEL", "Ljava/lang/String;");
    if (modelField == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(buildClass);
        return JNI_FALSE;
    }
    pthread_mutex_lock(&device_model_mutex);
    jstring activeValue = env->NewStringUTF(device_model);
    pthread_mutex_unlock(&device_model_mutex);
    if (activeValue == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(buildClass);
        return JNI_FALSE;
    }
    env->SetStaticObjectField(buildClass, modelField, activeValue);
    bool success = !env->ExceptionCheck();
    if (!success) env->ExceptionClear();
    env->DeleteLocalRef(activeValue);
    env->DeleteLocalRef(buildClass);
    if (success) {
        LOGD("Build.MODEL override applied length=%zu", length);
    } else {
        LOGD("Build.MODEL field update failed");
    }
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_setBuildPropsNative(
        JNIEnv *env, jclass, jobjectArray propertyNames, jobjectArray values) {
    if (propertyNames == nullptr || values == nullptr) return JNI_FALSE;
    jsize nameCount = env->GetArrayLength(propertyNames);
    jsize valueCount = env->GetArrayLength(values);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return JNI_FALSE;
    }
    if (nameCount != valueCount || nameCount > 32) return JNI_FALSE;

    std::unordered_map<std::string, std::string> requestedValues;
    std::unordered_map<std::string, std::string> nextProperties;
    for (jsize index = 0; index < nameCount; index++) {
        jstring nameString = static_cast<jstring>(env->GetObjectArrayElement(propertyNames, index));
        jstring valueString = static_cast<jstring>(env->GetObjectArrayElement(values, index));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            if (nameString != nullptr) env->DeleteLocalRef(nameString);
            if (valueString != nullptr) env->DeleteLocalRef(valueString);
            return JNI_FALSE;
        }
        if (nameString == nullptr) {
            if (valueString != nullptr) env->DeleteLocalRef(valueString);
            return JNI_FALSE;
        }

        const char *nameChars = env->GetStringUTFChars(nameString, nullptr);
        const char *valueChars = valueString == nullptr ? nullptr : env->GetStringUTFChars(valueString, nullptr);
        if (nameChars == nullptr || (valueString != nullptr && valueChars == nullptr)) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (nameChars != nullptr) env->ReleaseStringUTFChars(nameString, nameChars);
            if (valueChars != nullptr) env->ReleaseStringUTFChars(valueString, valueChars);
            env->DeleteLocalRef(nameString);
            if (valueString != nullptr) env->DeleteLocalRef(valueString);
            return JNI_FALSE;
        }

        std::string name(nameChars);
        std::string value(valueChars == nullptr ? "" : valueChars);
        env->ReleaseStringUTFChars(nameString, nameChars);
        if (valueChars != nullptr) env->ReleaseStringUTFChars(valueString, valueChars);
        env->DeleteLocalRef(nameString);
        if (valueString != nullptr) env->DeleteLocalRef(valueString);

        bool knownName = system_property_for_build_prop(name.c_str()) != nullptr
                || strcmp(name.c_str(), "osVersion") == 0;
        if (!knownName || value.size() >= PROP_VALUE_MAX) return JNI_FALSE;
        for (unsigned char character : value) {
            if (character < 0x20 || character > 0x7e) return JNI_FALSE;
        }
        if (!value.empty()) requestedValues[name] = value;
    }

    jint sdkIntValue = 0;
    auto sdkInt = requestedValues.find("sdkInt");
    if (sdkInt != requestedValues.end()) {
        char *end = nullptr;
        long long parsedSdkInt = strtoll(sdkInt->second.c_str(), &end, 10);
        if (end == sdkInt->second.c_str() || *end != '\0' || parsedSdkInt < 1
                || parsedSdkInt > std::numeric_limits<jint>::max()) {
            return JNI_FALSE;
        }
        sdkIntValue = static_cast<jint>(parsedSdkInt);
    }

    auto deviceName = requestedValues.find("deviceName");
    auto model = requestedValues.find("model");
    for (const auto &entry : requestedValues) {
        if (entry.first == "osVersion") continue;
        const char *propertyName = system_property_for_build_prop(entry.first.c_str());
        if (propertyName == nullptr) continue;

        if (entry.first == "deviceName") {
            nextProperties[propertyName] = entry.second;
            if (model == requestedValues.end()) {
                nextProperties["ro.product.model"] = entry.second;
            }
        } else if (entry.first == "time") {
            char *end = nullptr;
            long long milliseconds = strtoll(entry.second.c_str(), &end, 10);
            if (end == entry.second.c_str() || *end != '\0' || milliseconds < 0) {
                return JNI_FALSE;
            }
            nextProperties[propertyName] = std::to_string(milliseconds / 1000);
        } else {
            nextProperties[propertyName] = entry.second;
        }
    }

    jclass buildClass = env->FindClass("android/os/Build");
    if (buildClass == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    }
    bool success = true;
    for (const auto &entry : requestedValues) {
        const char *fieldName = build_field_for_build_prop(entry.first.c_str());
        if (fieldName != nullptr && entry.first != "deviceName") {
            success = set_build_string_field(env, buildClass, fieldName, entry.second.c_str()) && success;
        }
    }
    auto release = requestedValues.find("release");
    if (release != requestedValues.end()) {
        jclass versionClass = env->FindClass("android/os/Build$VERSION");
        if (versionClass == nullptr || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            success = false;
        } else {
            success = set_version_string_field(env, versionClass, "RELEASE", release->second.c_str()) && success;
            env->DeleteLocalRef(versionClass);
        }
    }
    if (sdkInt != requestedValues.end()) {
        jclass versionClass = env->FindClass("android/os/Build$VERSION");
        if (versionClass == nullptr || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            success = false;
        } else {
            success = set_version_int_field(env, versionClass, "SDK_INT", sdkIntValue) && success;
            env->DeleteLocalRef(versionClass);
        }
    }
    if (model == requestedValues.end() && deviceName != requestedValues.end()) {
        success = set_build_string_field(env, buildClass, "MODEL", deviceName->second.c_str()) && success;
    }
    auto buildTime = requestedValues.find("time");
    if (buildTime != requestedValues.end()) {
        char *end = nullptr;
        long long milliseconds = strtoll(buildTime->second.c_str(), &end, 10);
        if (end == buildTime->second.c_str() || *end != '\0' || milliseconds < 0) {
            success = false;
        } else {
            jfieldID timeField = env->GetStaticFieldID(buildClass, "TIME", "J");
            if (timeField == nullptr || env->ExceptionCheck()) {
                if (env->ExceptionCheck()) env->ExceptionClear();
                success = false;
            } else {
                env->SetStaticLongField(buildClass, timeField, static_cast<jlong>(milliseconds));
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    success = false;
                }
            }
        }
    }
    env->DeleteLocalRef(buildClass);
    if (!success) return JNI_FALSE;

    {
        std::lock_guard<std::mutex> lock(custom_build_props_mutex);
        custom_build_props.swap(nextProperties);
    }
    LOGD("Applied %zu custom build properties", requestedValues.size());
    return JNI_TRUE;
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
