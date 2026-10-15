package net.tfminecraft.armourshop.pack.delete;

import java.util.List;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;

/** Active unused token codes, refreshed asynchronously with the deletion cache TTL. */
public final class ActiveTokenCodeCache {
    private static final GenerationalIdCache CACHE = new GenerationalIdCache(() -> {
        var result = ProvinceSystemClient.listActiveCodes();
        return result.ok
            ? ProvinceSystemClient.DeletableListResult.success(result.codes.stream()
                .map(entry -> entry.code).distinct().toList())
            : ProvinceSystemClient.DeletableListResult.fail(result.error);
    });

    private ActiveTokenCodeCache() {}

    public static List<String> snapshot() {
        return CACHE.snapshot();
    }

    public static void invalidate() {
        CACHE.invalidate();
    }
}
