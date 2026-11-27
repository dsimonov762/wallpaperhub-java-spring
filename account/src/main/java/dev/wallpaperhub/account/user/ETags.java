package dev.wallpaperhub.account.user;

import dev.wallpaperhub.platform.problem.AppException;

/** The ETag of a resource is its {@code version} as a quoted integer: {@code "3"} (common.yaml, header ETag). */
final class ETags {

    private ETags() {}

    /**
     * The version in an {@code If-Match} header, or {@code null} when there is no header or it is {@code *} (any version).
     * A header that is not a quoted integer can never match: {@code request.version-conflict}.
     */
    static Long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw versionConflict();
        }
    }

    static AppException versionConflict() {
        return AppException.conflict("request.version-conflict");
    }
}
