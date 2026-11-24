package com.clony.proxyengine;

import android.content.Context;
import android.net.DnsResolver;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Looper;
import android.system.ErrnoException;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Starts one NekoBox sing-box instance inside each Clony virtual process. */
public final class NekoProxyEngine {
    private static final String TAG = "ClonyNekoProxy";
    private static final int START_TIMEOUT_MS = 4000;
    private static final int START_WORKER_TIMEOUT_MS = START_TIMEOUT_MS + 6000;
    private static final ExecutorService START_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ClonyNekoProxyStart");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService DNS_EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "ClonyNekoDns");
        thread.setDaemon(true);
        return thread;
    });

    private static Object boxInstance;
    private static int localPort;
    private static boolean coreInitialized;

    private NekoProxyEngine() { }

    /** Starts sing-box and returns the local SOCKS5 inbound port for Clony's socket hook. */
    public static int start(
            Context context,
            int proxyType,
            String server,
            int serverPort,
            String username,
            String password
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return startOnCurrentThread(context, proxyType, server, serverPort, username, password);
        }

        FutureTask<Integer> startup = new FutureTask<>(() ->
                startOnCurrentThread(context, proxyType, server, serverPort, username, password));
        try {
            START_EXECUTOR.execute(startup);
            return startup.get(START_WORKER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("NekoBox proxy core could not start for this clone.", cause);
        } catch (TimeoutException error) {
            startup.cancel(true);
            throw new IllegalStateException("NekoBox proxy core startup timed out.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            startup.cancel(true);
            throw new IllegalStateException("NekoBox proxy core startup was interrupted.", error);
        }
    }

    private static synchronized int startOnCurrentThread(
            Context context,
            int proxyType,
            String server,
            int serverPort,
            String username,
            String password
    ) {
        if (proxyType != 1 && proxyType != 2) {
            throw new IllegalArgumentException("A SOCKS5 or HTTP proxy is required.");
        }
        if (server == null || server.trim().isEmpty() || serverPort < 1 || serverPort > 65535) {
            throw new IllegalArgumentException("The saved proxy endpoint is invalid.");
        }
        if (boxInstance != null) return localPort;

        Context appContext = context.getApplicationContext();
        try {
            Class<?> libcore = Class.forName("libcore.Libcore", true, NekoProxyEngine.class.getClassLoader());
            Object platform = createInterface("libcore.BoxPlatformInterface", new PlatformHandler());
            Object nb4a = createInterface("libcore.NB4AInterface", new Nb4aHandler());
            Object localDns = createInterface("libcore.LocalDNSTransport", new LocalDnsHandler());

            if (!coreInitialized) {
                String files = appContext.getFilesDir().getAbsolutePath() + "/";
                String cache = appContext.getCacheDir().getAbsolutePath() + "/";
                invokeStatic(libcore, "initCore", new Object[]{
                        appContext.getPackageName() + ":clone:" + android.os.Process.myPid(),
                        cache,
                        files,
                        files,
                        128,
                        false,
                        nb4a,
                        platform,
                        localDns
                });
                coreInitialized = true;
            }

            localPort = reserveLocalPort();
            String config = buildConfig(proxyType, server, serverPort, username, password, localPort).toString();
            boxInstance = invokeStatic(libcore, "newSingBoxInstance", new Object[]{config, localDns});
            invokeInstance(boxInstance, "start", new Object[0]);
            awaitLocalListener(localPort);

            Log.i(TAG, "event=engine_start result=success inbound=mixed_socks5 outbound="
                    + (proxyType == 1 ? "socks5" : "http") + " endpoint_or_credentials_logged=false");
            return localPort;
        } catch (Throwable error) {
            closeBoxQuietly();
            localPort = 0;
            Throwable cause = error.getCause() == null ? error : error.getCause();
            Log.e(TAG, "event=engine_start result=failure stage=sing_box_init error_type="
                    + cause.getClass().getSimpleName() + " endpoint_or_credentials_logged=false");
            throw new IllegalStateException("NekoBox proxy core could not start for this clone.");
        }
    }

    private static JSONObject buildConfig(
            int proxyType,
            String server,
            int serverPort,
            String username,
            String password,
            int inboundPort
    ) throws Exception {
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("level", "warn"));

        JSONArray dnsServers = new JSONArray()
                .put(new JSONObject().put("address", "rcode://success").put("tag", "dns-block"))
                .put(new JSONObject()
                        .put("address", "local")
                        .put("tag", "dns-local")
                        .put("detour", "direct"))
                .put(new JSONObject()
                        .put("address", "https://223.5.5.5/dns-query")
                        .put("tag", "dns-direct")
                        .put("detour", "direct")
                        .put("address_resolver", "dns-local"))
                .put(new JSONObject()
                        .put("address", "https://dns.google/dns-query")
                        .put("tag", "dns-remote")
                        .put("address_resolver", "dns-direct"));
        JSONArray dnsRules = new JSONArray().put(new JSONObject()
                .put("outbound", new JSONArray().put("any"))
                .put("server", "dns-direct"));
        root.put("dns", new JSONObject()
                .put("servers", dnsServers)
                .put("rules", dnsRules)
                .put("final", "dns-remote")
                .put("independent_cache", true));

        root.put("inbounds", new JSONArray().put(new JSONObject()
                .put("type", "mixed")
                .put("tag", "clony-local-in")
                .put("listen", "127.0.0.1")
                .put("listen_port", inboundPort)));

        JSONObject proxy = new JSONObject()
                .put("type", proxyType == 1 ? "socks" : "http")
                .put("tag", "proxy")
                .put("server", server)
                .put("server_port", serverPort);
        if (proxyType == 1) proxy.put("version", "5");
        if (username != null && !username.isEmpty()) proxy.put("username", username);
        if (password != null && !password.isEmpty()) proxy.put("password", password);

        JSONArray outbounds = new JSONArray()
                .put(proxy)
                .put(new JSONObject().put("type", "direct").put("tag", "direct"));
        root.put("outbounds", outbounds);

        JSONArray routeRules = new JSONArray()
                .put(new JSONObject()
                        .put("protocol", new JSONArray().put("dns"))
                        .put("action", "hijack-dns"))
                .put(new JSONObject()
                        .put("port", new JSONArray().put(53))
                        .put("action", "hijack-dns"));
        root.put("route", new JSONObject()
                .put("rules", routeRules)
                .put("final", "proxy")
                .put("auto_detect_interface", true));
        return root;
    }

    private static Object createInterface(String name, InvocationHandler handler) throws Exception {
        Class<?> contract = Class.forName(name, true, NekoProxyEngine.class.getClassLoader());
        return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, handler);
    }

    private static Object invokeStatic(Class<?> owner, String name, Object[] args) throws Exception {
        Method method = findMethod(owner, name, args.length);
        return invoke(method, null, args);
    }

    private static Object invokeInstance(Object owner, String name, Object[] args) throws Exception {
        Method method = findMethod(owner.getClass(), name, args.length);
        return invoke(method, owner, args);
    }

    private static Method findMethod(Class<?> owner, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object invoke(Method method, Object owner, Object[] args) throws Exception {
        try {
            return method.invoke(owner, args);
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw error;
        }
    }

    private static int reserveLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void awaitLocalListener(int port) throws IOException {
        long deadline = android.os.SystemClock.elapsedRealtime() + START_TIMEOUT_MS;
        IOException lastError = null;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 250);
                return;
            } catch (IOException error) {
                lastError = error;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while starting the proxy core.");
                }
            }
        }
        throw lastError == null ? new IOException("The proxy core listener did not start.") : lastError;
    }

    private static void closeBoxQuietly() {
        if (boxInstance == null) return;
        try {
            invokeInstance(boxInstance, "close", new Object[0]);
        } catch (Exception ignored) {
            // The virtual process is already failing startup and will be terminated.
        }
        boxInstance = null;
    }

    private abstract static class Handler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("toString".equals(name)) return getClass().getSimpleName();
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];
            return call(name, args == null ? new Object[0] : args);
        }

        abstract Object call(String method, Object[] args) throws Throwable;
    }

    private static final class Nb4aHandler extends Handler {
        @Override
        Object call(String method, Object[] args) {
            if ("useOfficialAssets".equals(method)) return false;
            return null;
        }
    }

    private static final class PlatformHandler extends Handler {
        @Override
        Object call(String method, Object[] args) {
            switch (method) {
                case "autoDetectInterfaceControl":
                    return null;
                case "openTun":
                    throw new IllegalStateException("Clony uses a local proxy inbound, not a device-wide VPN.");
                case "useProcFS":
                    return false;
                case "findConnectionOwner":
                case "uidByPackageName":
                    return 0;
                case "packageNameByUid":
                    return "android";
                case "wifiState":
                    return "unknown,unknown";
                default:
                    return null;
            }
        }
    }

    private static final class LocalDnsHandler extends Handler {
        @Override
        Object call(String method, Object[] args) {
            switch (method) {
                case "raw":
                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
                case "networkHandle":
                    return 0L;
                case "lookup":
                    lookup(args[0], String.valueOf(args[1]), String.valueOf(args[2]));
                    return null;
                case "exchange":
                    exchange(args[0], (byte[]) args[1]);
                    return null;
                default:
                    return null;
            }
        }

        private void lookup(Object exchangeContext, String network, String domain) {
            DNS_EXECUTOR.execute(() -> {
                try {
                    List<String> addresses = new ArrayList<>();
                    for (InetAddress address : InetAddress.getAllByName(domain)) {
                        if (network.endsWith("4") && !(address instanceof Inet4Address)) continue;
                        if (network.endsWith("6") && !(address instanceof Inet6Address)) continue;
                        addresses.add(address.getHostAddress());
                    }
                    if (addresses.isEmpty()) {
                        callContext(exchangeContext, "errorCode", 3);
                    } else {
                        callContext(exchangeContext, "success", join(addresses));
                    }
                } catch (UnknownHostException error) {
                    callContext(exchangeContext, "errorCode", 3);
                } catch (Exception error) {
                    callContext(exchangeContext, "errnoCode", 113);
                }
            });
        }

        @SuppressWarnings("NewApi")
        private void exchange(Object exchangeContext, byte[] request) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                callContext(exchangeContext, "errnoCode", 95);
                return;
            }
            DnsResolver.getInstance().rawQuery(
                    null,
                    request,
                    DnsResolver.FLAG_NO_RETRY,
                    DNS_EXECUTOR,
                    new CancellationSignal(),
                    new DnsResolver.Callback<byte[]>() {
                        @Override
                        public void onAnswer(byte[] answer, int rcode) {
                            callContext(exchangeContext, "rawSuccess", answer);
                        }

                        @Override
                        public void onError(DnsResolver.DnsException error) {
                            Throwable cause = error.getCause();
                            int errno = cause instanceof ErrnoException
                                    ? ((ErrnoException) cause).errno : 113;
                            callContext(exchangeContext, "errnoCode", errno);
                        }
                    });
        }
    }

    private static void callContext(Object context, String methodName, Object value) {
        if (context == null) return;
        try {
            Method method = context.getClass().getMethod(methodName, value.getClass());
            method.invoke(context, value);
        } catch (Exception ignored) {
            Log.w(TAG, "Could not complete a local DNS callback.");
        }
    }

    private static void callContext(Object context, String methodName, int value) {
        if (context == null) return;
        try {
            Method method = context.getClass().getMethod(methodName, int.class);
            method.invoke(context, value);
        } catch (Exception ignored) {
            Log.w(TAG, "Could not complete a local DNS callback.");
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        return result.toString();
    }
}
