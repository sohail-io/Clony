package top.niunaijun.blackbox.core;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.CrashMonitor;
import top.niunaijun.blackbox.utils.Slog;

/** Records failures; uncaught exceptions cannot restore a failed Android main thread. */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler previous;
    private final ThreadLocal<Boolean> handling = new ThreadLocal<>();

    public static synchronized void create() {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CrashHandler)) new CrashHandler();
    }

    public CrashHandler() {
        this(Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public CrashHandler(Thread.UncaughtExceptionHandler previous) {
        this.previous = previous;
    }

    @Override public void uncaughtException(Thread thread, Throwable error) {
        if (Boolean.TRUE.equals(handling.get())) return;
        handling.set(true);
        try {
            try {
                CrashMonitor.handleCrash("JavaException", thread, error);
                Thread.UncaughtExceptionHandler observer = BlackBoxCore.get().getExceptionHandler();
                if (observer != null && observer != this && observer != previous) observer.uncaughtException(thread, error);
            } catch (Throwable recordingError) {
                Slog.e("CrashHandler", "Could not record uncaught exception", recordingError);
            }
            if (previous != null && previous != this) previous.uncaughtException(thread, error);
            else {
                Slog.e("CrashHandler", "Fatal exception without a platform handler", error);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        } finally {
            handling.remove();
        }
    }
}
