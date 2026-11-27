package dev.wallpaperhub.platform.web;

import dev.wallpaperhub.platform.problem.AppException;

/** E5 and README 5.2: a page size above 100 is refused with {@code pagination.size-too-large}, never clamped. */
public final class Pagination {

    public static final int MAX_SIZE = 100;

    private Pagination() {}

    public static void requireValidSize(int size) {
        if (size > MAX_SIZE) {
            throw AppException.validation("pagination.size-too-large", "max", MAX_SIZE);
        }
    }
}
