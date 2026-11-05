package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
        public final long timestamp;
        public final boolean wasRecovered;
        
        public CrashInfo(String crashType, String packageName, String errorMessage, 
                        String stackTrace, boolean wasRecovered) {
            this.crashType = crashType;
            this.packageName = packageName;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
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
            
            return new CrashInfo(crashType, packageName, errorMessage, stackTrace, false);
            
        } catch (Exception e) {
            Slog.w(TAG, "Error creating crash info: " + e.getMessage());
            return new CrashInfo(crashType, "unknown", "Error creating crash info", "", false);
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
                writer.println("=== STACK TRACE ===");
                writer.println(crashInfo.stackTrace);
                writer.println("=== END ===");
            }
            
            Slog.d(TAG, "Crash log written to: " + logFile.getAbsolutePath());
            
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
