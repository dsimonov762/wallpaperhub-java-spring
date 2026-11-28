package dev.wallpaperhub.account.user;

/**
 * What the cache holds and the read endpoints serve: the profile of the User schema plus the {@code version} that becomes the
 * {@code ETag} (the schema has no field for it).
 */
record UserView(long version, UserResponse user) {

    static UserView of(UserEntity entity) {
        return new UserView(entity.getVersion(), UserResponse.of(entity));
    }
}
