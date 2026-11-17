



#include <IO.h>
#include "Log.h"
#include "UnixFileSystemHook.h"
#import "JniHook/JniHook.h"
#include "BaseHook.h"
#include <string>
#include <string.h>

static std::string profileCropPath(JNIEnv *env, jstring path) {
    if (path == nullptr) return "<null>";
    const char *chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return "<unavailable>";
    std::string result(chars);
    env->ReleaseStringUTFChars(path, chars);
    return result;
}

static bool isProfileCropPath(const std::string &path) {
    return path.find("profileHeader") != std::string::npos ||
           path.find("sd_can_write") != std::string::npos;
}

static std::string profileCropFilePath(JNIEnv *env, jobject file) {
    if (file == nullptr) return "<null>";
    jclass fileClass = env->GetObjectClass(file);
    if (fileClass == nullptr) return "<unavailable>";
    jmethodID getPath = env->GetMethodID(fileClass, "getPath", "()Ljava/lang/String;");
    if (getPath == nullptr) return "<unavailable>";
    jstring path = (jstring) env->CallObjectMethod(file, getPath);
    return profileCropPath(env, path);
}


HOOK_JNI(jstring, canonicalize0, JNIEnv *env, jobject obj, jstring path) {
    jstring redirect = IO::redirectPath(env, path);
    return orig_canonicalize0(env, obj, redirect);
}


HOOK_JNI(jint, getBooleanAttributes0, JNIEnv *env, jobject obj, jstring abspath) {
    std::string requested = profileCropPath(env, abspath);
    jstring redirect = IO::redirectPath(env, abspath);
    std::string resolved = profileCropPath(env, redirect);
    jint result = orig_getBooleanAttributes0(env, obj, redirect);
    if (isProfileCropPath(requested) || isProfileCropPath(resolved)) {
        ALOGD("profile-crop-fs getBooleanAttributes requested=%s resolved=%s result=0x%x exception=%d",
              requested.c_str(), resolved.c_str(), result, env->ExceptionCheck());
    }
    return result;
}


HOOK_JNI(jlong, getLastModifiedTime0, JNIEnv *env, jobject obj, jobject path) {
    jobject redirect = IO::redirectPath(env, path);
    return orig_getLastModifiedTime0(env, obj, redirect);
}


HOOK_JNI(jboolean, setPermission0, JNIEnv *env, jobject obj, jobject file, jint access,
         jboolean enable, jboolean owneronly) {
    jobject redirect = IO::redirectPath(env, file);
    return orig_setPermission0(env, obj, redirect, access, enable, owneronly);
}


HOOK_JNI(jboolean, createFileExclusively0, JNIEnv *env, jobject obj, jstring path) {
    std::string requested = profileCropPath(env, path);
    jstring redirect = IO::redirectPath(env, path);
    std::string resolved = profileCropPath(env, redirect);
    jboolean result = orig_createFileExclusively0(env, obj, redirect);
    if (isProfileCropPath(requested) || isProfileCropPath(resolved)) {
        ALOGD("profile-crop-fs createFileExclusively requested=%s resolved=%s result=%d exception=%d",
              requested.c_str(), resolved.c_str(), result, env->ExceptionCheck());
    }
    return result;
}


HOOK_JNI(jobjectArray, list0, JNIEnv *env, jobject obj, jobject file) {
    jobject redirect = IO::redirectPath(env, file);
    return orig_list0(env, obj, redirect);
}


HOOK_JNI(jboolean, createDirectory0, JNIEnv *env, jobject obj, jobject path) {
    std::string requested = profileCropFilePath(env, path);
    jobject redirect = IO::redirectPath(env, path);
    std::string resolved = profileCropFilePath(env, redirect);
    jboolean result = orig_createDirectory0(env, obj, redirect);
    if (isProfileCropPath(requested) || isProfileCropPath(resolved)) {
        ALOGD("profile-crop-fs createDirectory requested=%s resolved=%s result=%d exception=%d",
              requested.c_str(), resolved.c_str(), result, env->ExceptionCheck());
    }
    return result;
}


HOOK_JNI(jboolean, setLastModifiedTime0, JNIEnv *env, jobject obj, jobject file, jobject time) {
    jobject redirect = IO::redirectPath(env, file);
    return orig_setLastModifiedTime0(env, obj, redirect, time);
}


HOOK_JNI(jboolean, setReadOnly0, JNIEnv *env, jobject obj, jobject file) {
    jobject redirect = IO::redirectPath(env, file);
    return orig_setReadOnly0(env, obj, redirect);
}


HOOK_JNI(jlong, getSpace0, JNIEnv *env, jobject obj, jobject file, jint t) {
    jobject redirect = IO::redirectPath(env, file);
    return orig_getSpace0(env, obj, redirect, t);
}

void UnixFileSystemHook::init(JNIEnv *env) {
    const char *className = "java/io/UnixFileSystem";

    try {
        JniHook::HookJniFun(env, className, "canonicalize0", "(Ljava/lang/String;)Ljava/lang/String;",
                            (void *) new_canonicalize0, (void **) (&orig_canonicalize0), false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook canonicalize0, continuing without it");
    }
    



    
    
    try {
        JniHook::HookJniFun(env, className, "getLastModifiedTime0", "(Ljava/io/File;)J",
                            (void *) new_getLastModifiedTime0, (void **) (&orig_getLastModifiedTime0),
                            false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook getLastModifiedTime0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "setPermission0", "(Ljava/io/File;IZZ)Z",
                            (void *) new_setPermission0, (void **) (&orig_setPermission0), false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook setPermission0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "createFileExclusively0", "(Ljava/lang/String;)Z",
                            (void *) new_createFileExclusively0,
                            (void **) (&orig_createFileExclusively0), false);
        ALOGD("profile-crop-fs hook method=createFileExclusively0 active=%d",
              orig_createFileExclusively0 != nullptr);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook createFileExclusively0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "list0", "(Ljava/io/File;)[Ljava/lang/String;",
                            (void *) new_list0, (void **) (&orig_list0), false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook list0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "createDirectory0", "(Ljava/io/File;)Z",
                            (void *) new_createDirectory0, (void **) (&orig_createDirectory0), false);
        ALOGD("profile-crop-fs hook method=createDirectory0 active=%d",
              orig_createDirectory0 != nullptr);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook createDirectory0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "setLastModifiedTime0", "(Ljava/io/File;J)Z",
                            (void *) new_setLastModifiedTime0, (void **) (&orig_setLastModifiedTime0),
                            false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook setLastModifiedTime0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "setReadOnly0", "(Ljava/io/File;)Z",
                            (void *) new_setReadOnly0, (void **) (&orig_setReadOnly0), false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook setReadOnly0, continuing without it");
    }
    
    try {
        JniHook::HookJniFun(env, className, "getSpace0", "(Ljava/io/File;I)J",
                            (void *) new_getSpace0, (void **) (&orig_getSpace0), false);
    } catch (...) {
        ALOGD("UnixFileSystemHook: Failed to hook getSpace0, continuing without it");
    }
}

