package net.tfminecraft.tfmcweb.api;

/** Test-only shape of TFMCWeb's reflective transport contract. */
public final class ProvinceSystemGateway {
    public static Object result;
    public static RuntimeException failure;
    public static Object request(String method, String path, String body) { return reply(); }
    public static Object requestBytes(String method, String path, byte[] body, String contentType) { return reply(); }
    public static Object download(String path) { return reply(); }
    private static Object reply() {
        if (failure != null) throw failure;
        return result;
    }
}
