package dev.wallpaperhub.account.user;

import java.util.List;
import org.springframework.data.domain.Page;

/** {@code UserPage} of account.yaml: the pagination fields of README 5.2 and the items. */
public record UserPageResponse(List<UserResponse> items, int page, int size, long totalItems, int totalPages) {

    static UserPageResponse of(Page<UserEntity> page) {
        return new UserPageResponse(
                page.getContent().stream().map(UserResponse::of).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
