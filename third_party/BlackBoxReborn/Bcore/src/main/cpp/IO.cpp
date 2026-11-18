



#include "IO.h"
#include "Log.h"
#include <cstring>

jmethodID getAbsolutePathMethodId;

list<IO::RelocateInfo> relocate_rule;

char *replace(const char *str, const char *src, const char *dst) {
    if (str == nullptr || src == nullptr || dst == nullptr || src[0] == '\0') return nullptr;
    const size_t sourceLength = strlen(src);
    const size_t destinationLength = strlen(dst);
    const size_t inputLength = strlen(str);
    size_t matches = 0;
    const char *cursor = str;
    while ((cursor = strstr(cursor, src)) != nullptr) {
        matches++;
        cursor += sourceLength;
    }
    const size_t resultLength = inputLength - matches * sourceLength
            + matches * destinationLength;
    char *result = static_cast<char *>(malloc(resultLength + 1));
    if (result == nullptr) return nullptr;

    const char *input = str;
    char *output = result;
    const char *match;
    while ((match = strstr(input, src)) != nullptr) {
        size_t prefixLength = static_cast<size_t>(match - input);
        memcpy(output, input, prefixLength);
        output += prefixLength;
        memcpy(output, dst, destinationLength);
        output += destinationLength;
        input = match + sourceLength;
    }
    size_t suffixLength = strlen(input);
    memcpy(output, input, suffixLength);
    output += suffixLength;
    *output = '\0';
    return result;
}

const char *IO::redirectPath(const char *__path) {
    
    if (strstr(__path, "resource-cache")) {
        ALOGD("Blocking resource-cache path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "@idmap")) {
        ALOGD("Blocking idmap path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "systemui") && (strstr(__path, ".frro") || strstr(__path, "-accent-") || strstr(__path, "-dynamic-") || strstr(__path, "-neutral-"))) {
        ALOGD("Blocking systemui problematic path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "data@resource-cache@")) {
        ALOGD("Blocking data@resource-cache@ pattern: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, ".frro")) {
        ALOGD("Blocking .frro file: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "systemui")) {
        ALOGD("Blocking systemui path: %s", __path);
        return "/dev/null";
    }

    list<IO::RelocateInfo>::iterator iterator;
    for (iterator = relocate_rule.begin(); iterator != relocate_rule.end(); ++iterator) {
        IO::RelocateInfo info = *iterator;
        if (strstr(__path, info.targetPath.c_str()) && !strstr(__path, "/blackbox/")) {
            char *ret = replace(__path, info.targetPath.c_str(), info.relocatePath.c_str());
            
            return ret;
        }
    }
    return __path;
}

jstring IO::redirectPath(JNIEnv *env, jstring path) {




    return BoxCore::redirectPathString(env, path);
}

jobject IO::redirectPath(JNIEnv *env, jobject path) {






    return BoxCore::redirectPathFile(env, path);
}

void IO::addRule(const char *targetPath, const char *relocatePath) {
    if (targetPath == nullptr || relocatePath == nullptr) return;
    for (IO::RelocateInfo &existing : relocate_rule) {
        if (existing.targetPath == targetPath) {
            existing.relocatePath = relocatePath;
            return;
        }
    }
    IO::RelocateInfo info{};
    info.targetPath = targetPath;
    info.relocatePath = relocatePath;
    relocate_rule.push_back(info);
}

void IO::removeRule(const char *targetPath) {
    if (targetPath == nullptr) return;
    for (auto iterator = relocate_rule.begin(); iterator != relocate_rule.end();) {
        if (iterator->targetPath == targetPath) {
            iterator = relocate_rule.erase(iterator);
        } else {
            ++iterator;
        }
    }
}

void IO::init(JNIEnv *env) {
    jclass tmpFile = env->FindClass("java/io/File");
    getAbsolutePathMethodId = env->GetMethodID(tmpFile, "getAbsolutePath", "()Ljava/lang/String;");
}
