package net.tfminecraft.armourshop.pack.delete;

import java.util.List;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;

/** Cached deletable submission ids for tab-complete (refreshed async). */
public final class DeletableSubmissionCache {
    private static final GenerationalIdCache CACHE =
        new GenerationalIdCache(ProvinceSystemClient::listDeletableSubmissionIds);

    private DeletableSubmissionCache() {}

    public static List<String> snapshot() {
        return CACHE.snapshot();
    }

    /** Call after a successful delete so tab-complete drops the id soon. */
    public static void invalidate() {
        CACHE.invalidate();
    }
}
