package net.tfminecraft.tfmcweb;

/** Test-only boundary for the optional reflective realm API. */
public final class TFMCWeb {
    public static String realmId = "main";
    public static RuntimeException failure;
    public static String getRealmId() {
        if (failure != null) throw failure;
        return realmId;
    }
}
