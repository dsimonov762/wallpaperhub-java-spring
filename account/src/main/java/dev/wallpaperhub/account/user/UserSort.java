package dev.wallpaperhub.account.user;

import dev.wallpaperhub.platform.problem.AppException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;

/** The values of the {@code sort} parameter of {@code GET /users}; anything else is {@code pagination.sort-not-allowed}. */
final class UserSort {

    static final String DEFAULT = "createdAt,desc";
    private static final List<String> ALLOWED =
            List.of("createdAt,desc", "createdAt,asc", "username,asc", "username,desc");

    private UserSort() {}

    static Sort parse(String value) {
        String sort = value == null || value.isBlank() ? DEFAULT : value.strip();
        if (!ALLOWED.contains(sort)) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("field", sort.split(",", 2)[0]);
            args.put("allowed", String.join(", ", ALLOWED));
            throw new AppException(400, "validation-failed", "pagination.sort-not-allowed", args);
        }
        // sort is one of ALLOWED, so it has exactly one comma
        int comma = sort.indexOf(',');
        Sort.Direction direction = Sort.Direction.fromString(sort.substring(comma + 1));
        // the id makes the order total, like the index users_created_idx (created_at DESC, id)
        return Sort.by(new Sort.Order(direction, sort.substring(0, comma)), Sort.Order.asc("id"));
    }
}
