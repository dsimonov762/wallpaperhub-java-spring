package dev.wallpaperhub.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Put this on the application class of a service instead of {@code @SpringBootApplication}: it is that annotation plus the
 * platform (request id, problem+json, JWT validation, management port).
 *
 * <p>It is an {@code @Import} of the application and not an auto-configuration on purpose: the security filter chain must be
 * defined before Spring Boot decides whether it needs its default one, and user configuration is processed first.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootApplication
@Import(PlatformConfiguration.class)
public @interface PlatformApplication {}
