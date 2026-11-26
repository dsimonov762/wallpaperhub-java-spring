package dev.wallpaperhub.platform.management;

/**
 * A dependency whose state decides {@code /health/ready} (M2): the service is ready only when every check is. Implement it for the
 * database, the cache, the broker; declare the implementation as a bean. It runs on every probe, so it must be fast and must not throw.
 */
public interface ReadinessCheck {

    String name();

    boolean ready();
}
