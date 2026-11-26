package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;


public class CrashMonitor {
    private static final String TAG = "CrashMonitor";
    private static boolean sIsInitialized = false;
    
    
    private static final AtomicInteger sTotalCrashes = new AtomicInteger(0);
    private static final AtomicInteger sJavaCrashes = new AtomicInteger(0);
    private static final AtomicInteger sNativeCrashes = new AtomicInteger(0);
    private static final AtomicInteger sRecoveredCrashes = new AtomicInteger(0);
    
    
    private static final Map<String, CrashInfo> sCrashHistory = new java.util.concurrent.ConcurrentHashMap<>();
    
    
    
    
    private static boolean sIsMonitoring = false;
    private static Handler sMainHandler;
    
    
    public static class CrashInfo {
        public final String crashType;
        public final String packageName;
        public final String errorMessage;
        public final String stackTrace;
        public final String diagnostics;
        public final long timestamp;
        public final boolean wasRecovered;
        
        public CrashInfo(String crashType, String packageName, String errorMessage, 
                        String stackTrace, String diagnostics, boolean wasRecovered) {
            this.crashType = crashType;
            this.packageName = packageName;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
            this.diagnostics = diagnostics;
            this.timestamp = System.currentTimeMillis();
            this.wasRecovered = wasRecovered;
        }
        
        @Override
        public String toString() {
            return "Crash[" + crashType + "] " + packageName + " - " + 
                   (wasRecovered ? "RECOVERED" : "FAILED") + " at " + 
                   new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
        }
    }
    
    
    public static void initialize() {
        if (sIsInitialized) {
            return;
        }
        
        try {
            Slog.d(TAG, "Initializing comprehensive crash monitoring system...");
            
            
            sMainHandler = new Handler(Looper.getMainLooper());
            
            

            
            
            installGlobalCrashHandlers();
            
            
            startMonitoring();
            
            sIsInitialized = true;
            Slog.d(TAG, "Crash monitoring system initialized successfully");
            
        } catch (Exception e) {
            Slog.e(TAG, "Failed to initialize crash monitoring: " + e.getMessage(), e);
        }
    }
    
    
    private static void installGlobalCrashHandlers() {
        try {
            
            // CrashHandler owns the only runtime exception chain.
            installSystemErrorHandler();
            
            Slog.d(TAG, "Global crash handlers installed");
            
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install global crash handlers: " + e.getMessage());
        }
    }
    
    
    private static void installSystemErrorHandler() {
        try {
            
            Slog.d(TAG, "System error handler prepared");
        } catch (Exception e) {
            Slog.w(TAG, "Failed to install system error handler: " + e.getMessage());
        }
    }
    
    
    private static void startMonitoring() {
        if (sIsMonitoring) {
            return;
        }
        
        try {
            sIsMonitoring = true;
            
            
            startPeriodicHealthChecks();
            
            
            startCrashPatternAnalysis();
            
            Slog.d(TAG, "Crash monitoring started");
            
        } catch (Exception e) {
            Slog.w(TAG, "Failed to start crash monitoring: " + e.getMessage());
        }
    }
    
    
    private static void startPeriodicHealthChecks() {
        if (sMainHandler != null) {
            sMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    performHealthCheck();
                    
                    sMainHandler.postDelayed(this, 30000);
                }
            }, 30000); 
        }
    }
    
    
    private static void startCrashPatternAnalysis() {
        if (sMainHandler != null) {
            sMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    analyzeCrashPatterns();
                    
                    sMainHandler.postDelayed(this, 60000);
                }
            }, 60000); 
        }
    }
    
    
    public static void handleCrash(String crashType, Thread thread, Throwable throwable) {
        try {
            sTotalCrashes.incrementAndGet();
            
            
            if (crashType.equals("JavaException")) {
                sJavaCrashes.incrementAndGet();
            } else if (crashType.equals("NativeCrash")) {
                sNativeCrashes.incrementAndGet();
            }
            
            
            CrashInfo crashInfo = createCrashInfo(crashType, thread, throwable);
            
            
            Slog.w(TAG, "Crash detected: " + crashInfo);
            
            
            String crashKey = crashType + "_" + System.currentTimeMillis();
            sCrashHistory.put(crashKey, crashInfo);
            
            
            writeCrashLog(crashInfo);
            
        } catch (Exception e) {
            Slog.e(TAG, "Error handling crash: " + e.getMessage());
        }
    }
    
    
    private static CrashInfo createCrashInfo(String crashType, Thread thread, Throwable throwable) {
        try {
            String packageName = getCurrentPackageName();
            String errorMessage = throwable != null ? throwable.getMessage() : "Unknown error";
            String stackTrace = getStackTrace(throwable);
            String diagnostics = getCrashDiagnostics(thread, throwable);
            
            return new CrashInfo(crashType, packageName, errorMessage, stackTrace, diagnostics, false);
            
        } catch (Exception e) {
            Slog.w(TAG, "Error creating crash info: " + e.getMessage());
            return new CrashInfo(crashType, "unknown", "Error creating crash info", "", "", false);
        }
    }
    
    
    private static String getCurrentPackageName() {
        try {
            return BActivityThread.getAppPackageName();
        } catch (Exception e) {
            try {
                Context context = BlackBoxCore.getContext();
                if (context != null) {
                    return context.getPackageName();
                }
            } catch (Exception ex) {
                
            }
            return "unknown";
        }
    }
    
    
    private static String getStackTrace(Throwable throwable) {
        if (throwable == null) return "";
        
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return "Error getting stack trace: " + e.getMessage();
        }
    }

    private static String getCrashDiagnostics(Thread thread, Throwable throwable) {
        StringBuilder out = new StringBuilder(2048);
        appendLine(out, "Process", BActivityThread.getAppProcessName());
        appendLine(out, "VirtualUserId", String.valueOf(BActivityThread.getUserId()));
        appendLine(out, "Pid", String.valueOf(android.os.Process.myPid()));
        appendLine(out, "Thread", thread == null ? null : thread.getName());

        Activity activity = VirtualRuntimeTrace.getCurrentActivity();
        Resources resources = null;
        ApplicationInfo appInfo = null;
        if (activity != null) {
            try {
                appendLine(out, "CurrentActivity", activity.getComponentName().flattenToShortString());
                resources = activity.getResources();
                appInfo = activity.getApplicationInfo();
            } catch (Throwable error) {
                appendLine(out, "ActivityDiagnosticError", error.toString());
            }
        }
        if (appInfo == null) {
            try {
                android.app.Application application = BActivityThread.getApplication();
                if (application != null) {
                    appInfo = application.getApplicationInfo();
                    if (resources == null) resources = application.getResources();
                }
            } catch (Throwable error) {
                appendLine(out, "ApplicationDiagnosticError", error.toString());
            }
        }

        if (appInfo != null) {
            appendLine(out, "ApplicationInfoPackage", appInfo.packageName);
            appendApkPath(out, "BaseApk", appInfo.sourceDir);
            String[] splitPaths = appInfo.splitSourceDirs;
            appendLine(out, "SplitApkCount", String.valueOf(splitPaths == null ? 0 : splitPaths.length));
            if (splitPaths != null) {
                for (int i = 0; i < splitPaths.length; i++) {
                    appendApkPath(out, "SplitApk[" + i + "]", splitPaths[i]);
                }
            }
        } else {
            appendLine(out, "ApplicationInfo", "unavailable");
        }

        appendCauseChain(out, throwable);
        appendMissingResourceDetails(out, resources, throwable);
        return out.toString();
    }

    private static void appendCauseChain(StringBuilder out, Throwable throwable) {
        int index = 0;
        for (Throwable cause = throwable; cause != null && index < 20; cause = cause.getCause(), index++) {
            appendLine(out, "Cause[" + index + "]", cause.getClass().getName() + ": " + cause.getMessage());
        }
    }

    private static void appendMissingResourceDetails(StringBuilder out, Resources resources,
                                                      Throwable throwable) {
        if (resources == null || throwable == null) return;
        Pattern resourceIdPattern = Pattern.compile("(?i)resource ID #?(0x[0-9a-f]+)");
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof Resources.NotFoundException)) continue;
            Matcher matcher = resourceIdPattern.matcher(String.valueOf(cause.getMessage()));
            if (!matcher.find()) {
                appendLine(out, "MissingResource", String.valueOf(cause.getMessage()));
                continue;
            }
            try {
                int resourceId = (int) Long.parseLong(matcher.group(1).substring(2), 16);
                appendLine(out, "MissingResourceId", matcher.group(1));
                appendLine(out, "MissingResourceName", resources.getResourceName(resourceId));
                appendLine(out, "MissingResourceType", resources.getResourceTypeName(resourceId));
                appendLine(out, "MissingResourceEntry", resources.getResourceEntryName(resourceId));

                TypedValue value = new TypedValue();
                resources.getValue(resourceId, value, true);
                appendLine(out, "MissingResourceAssetCookie", String.valueOf(value.assetCookie));
                appendLine(out, "MissingResourceFile", value.string == null
                        ? "<no file path in resource table>" : value.string.toString());
                appendAssetPaths(out, resources.getAssets(), value.assetCookie);
            } catch (Throwable error) {
                appendLine(out, "MissingResourceLookupError", error.toString());
            }
        }
    }

    private static void appendAssetPaths(StringBuilder out, AssetManager assets, int wantedCookie) {
        if (assets == null) return;
        try {
            Method getApkAssets = AssetManager.class.getDeclaredMethod("getApkAssets");
            getApkAssets.setAccessible(true);
            Object apkAssets = getApkAssets.invoke(assets);
            int count = Array.getLength(apkAssets);
            appendLine(out, "LoadedApkAssetCount", String.valueOf(count));
            for (int i = 0; i < count; i++) {
                Object apkAsset = Array.get(apkAssets, i);
                Method getAssetPath = apkAsset.getClass().getDeclaredMethod("getAssetPath");
                getAssetPath.setAccessible(true);
                String path = String.valueOf(getAssetPath.invoke(apkAsset));
                int cookie = i + 1;
                appendLine(out, "LoadedApkAsset[" + cookie + "]", path
                        + (cookie == wantedCookie ? " [RESOURCE COOKIE MATCH]" : ""));
            }
        } catch (Throwable error) {
            appendLine(out, "LoadedApkAssetPathsError", error.getClass().getName() + ": "
                    + error.getMessage());
        }
    }

    private static void appendApkPath(StringBuilder out, String label, String path) {
        if (path == null || path.isEmpty()) {
            appendLine(out, label, "<empty>");
            return;
        }
        File file = new File(path);
        appendLine(out, label, path + " exists=" + file.exists() + " readable=" + file.canRead()
                + " bytes=" + (file.isFile() ? file.length() : -1));
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        out.append(label).append(": ").append(value == null ? "unknown" : value).append('\n');
    }
    
    
    private static void writeCrashLog(CrashInfo crashInfo) {
        try {
            Context context = BlackBoxCore.getContext();
            if (context == null) return;
            
            File logDir = new File(context.getFilesDir(), "crash_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(crashInfo.timestamp));
            File logFile = new File(logDir, "crash_" + timestamp + ".log");
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
                writer.println("=== CRASH LOG ===");
                writer.println("Timestamp: " + new Date(crashInfo.timestamp));
                writer.println("Crash Type: " + crashInfo.crashType);
                writer.println("Package: " + crashInfo.packageName);
                writer.println("Error: " + crashInfo.errorMessage);
                writer.println("Recovered: " + crashInfo.wasRecovered);
                writer.println("=== PROCESS / RESOURCE DIAGNOSTICS ===");
                writer.println(crashInfo.diagnostics);
                writer.println("=== STACK TRACE ===");
                writer.println(crashInfo.stackTrace);
                writer.println("=== END ===");
            }
            
            Slog.d(TAG, "Crash log written to: " + logFile.getAbsolutePath());
            Slog.e(TAG, "Crash context diagnostics:\n" + crashInfo.diagnostics);
            
        } catch (Exception e) {
            Slog.w(TAG, "Failed to write crash log: " + e.getMessage());
        }
    }
    
    
    private static void performHealthCheck() {
        try {
            Slog.d(TAG, "Performing periodic health check...");
            
            
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            if (memoryUsagePercent > 80) {
                Slog.w(TAG, "High memory usage detected: " + String.format("%.1f%%", memoryUsagePercent));
                System.gc(); 
            }
            
            
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }
            
            int threadCount = rootGroup.activeCount();
            if (threadCount > 100) {
                Slog.w(TAG, "High thread count detected: " + threadCount);
            }
            
            Slog.d(TAG, "Health check completed - Memory: " + String.format("%.1f%%", memoryUsagePercent) + 
                   ", Threads: " + threadCount);
            
        } catch (Exception e) {
            Slog.w(TAG, "Error during health check: " + e.getMessage());
        }
    }
    
    
    private static void analyzeCrashPatterns() {
        try {
            if (sCrashHistory.isEmpty()) {
                return;
            }
            
            Slog.d(TAG, "Analyzing crash patterns...");
            
            
            Map<String, Integer> crashesByType = new HashMap<>();
            Map<String, Integer> crashesByPackage = new HashMap<>();
            
            for (CrashInfo crashInfo : sCrashHistory.values()) {
                
                crashesByType.put(crashInfo.crashType, 
                    crashesByType.getOrDefault(crashInfo.crashType, 0) + 1);
                
                
                crashesByPackage.put(crashInfo.packageName, 
                    crashesByPackage.getOrDefault(crashInfo.packageName, 0) + 1);
            }
            
            
            Slog.d(TAG, "Crash patterns by type: " + crashesByType);
            Slog.d(TAG, "Crash patterns by package: " + crashesByPackage);
            
            
            for (Map.Entry<String, Integer> entry : crashesByType.entrySet()) {
                if (entry.getValue() > 5) {
                    Slog.w(TAG, "High crash rate detected for type: " + entry.getKey() + 
                           " (" + entry.getValue() + " crashes)");
                }
            }
            
        } catch (Exception e) {
            Slog.w(TAG, "Error analyzing crash patterns: " + e.getMessage());
        }
    }
    
    
    public static String getCrashStats() {
        return "Crash Statistics:\n" +
               "Total Crashes: " + sTotalCrashes.get() + "\n" +
               "Java Crashes: " + sJavaCrashes.get() + "\n" +
               "Native Crashes: " + sNativeCrashes.get() + "\n" +
               "Recovered Crashes: " + sRecoveredCrashes.get() + "\n" +
               "Recovery Rate: " + String.format("%.1f%%", 
                   sTotalCrashes.get() > 0 ? 
                   (double) sRecoveredCrashes.get() / sTotalCrashes.get() * 100 : 0);
    }
    
    
    public static String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Crash Monitoring Status:\n");
        status.append("Initialized: ").append(sIsInitialized).append("\n");
        status.append("Monitoring: ").append(sIsMonitoring).append("\n");
        status.append("Crash History Size: ").append(sCrashHistory.size()).append("\n");
        status.append("\n").append(getCrashStats());
        
        return status.toString();
    }
    
    
    public static void clearCrashHistory() {
        sCrashHistory.clear();
        sTotalCrashes.set(0);
        sJavaCrashes.set(0);
        sNativeCrashes.set(0);
        sRecoveredCrashes.set(0);
        Slog.d(TAG, "Crash history cleared");
    }
    
    
    
    
}
