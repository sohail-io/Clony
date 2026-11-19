#include "FileSystemHook.h"

#include "Dobby/dobby.h"
#include "IO.h"
#include "Log.h"

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <string.h>
#include <string>
#include <sys/stat.h>
#include <unordered_map>
#include <unistd.h>

namespace {

using OpenFn = int (*)(const char *, int, ...);
using OpenAtFn = int (*)(int, const char *, int, ...);
using OpenAtFixedFn = int (*)(int, const char *, int, mode_t);
using AccessFn = int (*)(const char *, int);
using FaccessAtFn = int (*)(int, const char *, int, int);
using StatFn = int (*)(const char *, struct stat *);
using FstatAtFn = int (*)(int, const char *, struct stat *, int);
using MkdirFn = int (*)(const char *, mode_t);
using MkdirAtFn = int (*)(int, const char *, mode_t);

OpenFn orig_open = nullptr;
OpenFn orig_open64 = nullptr;
OpenAtFn orig_openat = nullptr;
OpenAtFn orig_openat64 = nullptr;
OpenAtFixedFn orig___openat = nullptr;
AccessFn orig_access = nullptr;
FaccessAtFn orig_faccessat = nullptr;
StatFn orig_stat = nullptr;
StatFn orig_lstat = nullptr;
FstatAtFn orig_fstatat = nullptr;
MkdirFn orig_mkdir = nullptr;
MkdirAtFn orig_mkdirat = nullptr;

bool hooksInitialized = false;
std::unordered_map<void *, void *> hookedOriginals;

bool isProfileCropPath(const char *path) {
    return path != nullptr && (strstr(path, "profileHeader") != nullptr ||
                               strstr(path, "sd_can_write") != nullptr);
}

const char *resolveProfileCropPath(const char *requested, std::string *resolved) {
    if (!isProfileCropPath(requested)) return requested;
    *resolved = IO::redirectPathCopy(requested);
    return resolved->c_str();
}

void logPathResult(const char *operation, const char *requested, const char *resolved,
                   int result, int error) {
    if (!isProfileCropPath(requested) && !isProfileCropPath(resolved)) return;
    ALOGD("profile-crop-fs syscall=%s requested=%s resolved=%s result=%d errno=%d",
          operation, requested == nullptr ? "<null>" : requested,
          resolved == nullptr ? "<null>" : resolved, result, result < 0 ? error : 0);
}

bool openNeedsMode(int flags) {
    if ((flags & O_CREAT) != 0) return true;
#ifdef O_TMPFILE
    return (flags & O_TMPFILE) == O_TMPFILE;
#else
    return false;
#endif
}

int invokeOpen(OpenFn original, const char *path, int flags, mode_t mode, bool hasMode) {
    if (original == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    return hasMode ? original(path, flags, mode) : original(path, flags);
}

int invokeOpenAt(OpenAtFn original, int dirfd, const char *path, int flags,
                 mode_t mode, bool hasMode) {
    if (original == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    return hasMode ? original(dirfd, path, flags, mode) : original(dirfd, path, flags);
}

int new_open(const char *pathname, int flags, ...) {
    bool hasMode = openNeedsMode(flags);
    mode_t mode = 0;
    if (hasMode) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }

    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = invokeOpen(orig_open, resolved, flags, mode, hasMode);
    int error = errno;
    logPathResult("open", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_open64(const char *pathname, int flags, ...) {
    bool hasMode = openNeedsMode(flags);
    mode_t mode = 0;
    if (hasMode) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }

    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = invokeOpen(orig_open64, resolved, flags, mode, hasMode);
    int error = errno;
    logPathResult("open64", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_openat(int dirfd, const char *pathname, int flags, ...) {
    bool hasMode = openNeedsMode(flags);
    mode_t mode = 0;
    if (hasMode) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }

    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = invokeOpenAt(orig_openat, dirfd, resolved, flags, mode, hasMode);
    int error = errno;
    logPathResult("openat", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_openat64(int dirfd, const char *pathname, int flags, ...) {
    bool hasMode = openNeedsMode(flags);
    mode_t mode = 0;
    if (hasMode) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }

    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = invokeOpenAt(orig_openat64, dirfd, resolved, flags, mode, hasMode);
    int error = errno;
    logPathResult("openat64", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new___openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig___openat == nullptr
                 ? -1 : orig___openat(dirfd, resolved, flags, mode);
    int error = errno;
    if (orig___openat == nullptr) error = ENOSYS;
    logPathResult("__openat", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_access(const char *pathname, int mode) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_access == nullptr ? -1 : orig_access(resolved, mode);
    int error = errno;
    if (orig_access == nullptr) error = ENOSYS;
    logPathResult("access", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_faccessat == nullptr ? -1
                                           : orig_faccessat(dirfd, resolved, mode, flags);
    int error = errno;
    if (orig_faccessat == nullptr) error = ENOSYS;
    logPathResult("faccessat", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_stat(const char *pathname, struct stat *resultStat) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_stat == nullptr ? -1 : orig_stat(resolved, resultStat);
    int error = errno;
    if (orig_stat == nullptr) error = ENOSYS;
    logPathResult("stat", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_lstat(const char *pathname, struct stat *resultStat) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_lstat == nullptr ? -1 : orig_lstat(resolved, resultStat);
    int error = errno;
    if (orig_lstat == nullptr) error = ENOSYS;
    logPathResult("lstat", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_fstatat(int dirfd, const char *pathname, struct stat *resultStat, int flags) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_fstatat == nullptr ? -1
                                        : orig_fstatat(dirfd, resolved, resultStat, flags);
    int error = errno;
    if (orig_fstatat == nullptr) error = ENOSYS;
    logPathResult("fstatat", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_mkdir(const char *pathname, mode_t mode) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_mkdir == nullptr ? -1 : orig_mkdir(resolved, mode);
    int error = errno;
    if (orig_mkdir == nullptr) error = ENOSYS;
    logPathResult("mkdir", pathname, resolved, result, error);
    errno = error;
    return result;
}

int new_mkdirat(int dirfd, const char *pathname, mode_t mode) {
    std::string resolvedStorage;
    const char *resolved = resolveProfileCropPath(pathname, &resolvedStorage);
    int result = orig_mkdirat == nullptr ? -1 : orig_mkdirat(dirfd, resolved, mode);
    int error = errno;
    if (orig_mkdirat == nullptr) error = ENOSYS;
    logPathResult("mkdirat", pathname, resolved, result, error);
    errno = error;
    return result;
}

void *resolveSymbol(const char *name) {
    void *symbol = dlsym(RTLD_DEFAULT, name);
    return symbol != nullptr ? symbol : DobbySymbolResolver("libc.so", name);
}

bool hookSymbol(const char *name, void *replacement, void **original) {
    void *symbol = resolveSymbol(name);
    if (symbol == nullptr) {
        ALOGD("profile-crop-fs hook symbol=%s active=0 reason=not-found", name);
        return false;
    }

    auto existingHook = hookedOriginals.find(symbol);
    if (existingHook != hookedOriginals.end()) {
        if (original != nullptr) *original = existingHook->second;
        ALOGD("profile-crop-fs hook symbol=%s active=1 result=alias", name);
        return true;
    }

    int result = DobbyHook(symbol, replacement, original);
    bool active = result == 0 && original != nullptr && *original != nullptr;
    if (active) hookedOriginals.emplace(symbol, *original);
    ALOGD("profile-crop-fs hook symbol=%s active=%d result=%d", name, active, result);
    return active;
}

} // namespace

void FileSystemHook::init() {
    if (hooksInitialized) return;
    hooksInitialized = true;

    // Android 10+ implements Java File creation and metadata checks with libc
    // entry points. Redirect just TikTok's avatar crop paths here; all other
    // app file operations keep their original paths and behavior.
    hookSymbol("open", reinterpret_cast<void *>(new_open),
               reinterpret_cast<void **>(&orig_open));
    hookSymbol("open64", reinterpret_cast<void *>(new_open64),
               reinterpret_cast<void **>(&orig_open64));
    hookSymbol("openat", reinterpret_cast<void *>(new_openat),
               reinterpret_cast<void **>(&orig_openat));
    hookSymbol("openat64", reinterpret_cast<void *>(new_openat64),
               reinterpret_cast<void **>(&orig_openat64));
    hookSymbol("__openat", reinterpret_cast<void *>(new___openat),
               reinterpret_cast<void **>(&orig___openat));
    hookSymbol("access", reinterpret_cast<void *>(new_access),
               reinterpret_cast<void **>(&orig_access));
    hookSymbol("faccessat", reinterpret_cast<void *>(new_faccessat),
               reinterpret_cast<void **>(&orig_faccessat));
    hookSymbol("stat", reinterpret_cast<void *>(new_stat),
               reinterpret_cast<void **>(&orig_stat));
    hookSymbol("lstat", reinterpret_cast<void *>(new_lstat),
               reinterpret_cast<void **>(&orig_lstat));
    hookSymbol("fstatat", reinterpret_cast<void *>(new_fstatat),
               reinterpret_cast<void **>(&orig_fstatat));
    hookSymbol("mkdir", reinterpret_cast<void *>(new_mkdir),
               reinterpret_cast<void **>(&orig_mkdir));
    hookSymbol("mkdirat", reinterpret_cast<void *>(new_mkdirat),
               reinterpret_cast<void **>(&orig_mkdirat));
}
