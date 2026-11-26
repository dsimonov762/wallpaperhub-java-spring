package dev.wallpaperhub.platform;

import java.util.List;

import dev.wallpaperhub.platform.i18n.Messages;
import dev.wallpaperhub.platform.management.JwksReadiness;
import dev.wallpaperhub.platform.management.ManagementServer;
import dev.wallpaperhub.platform.management.ReadinessCheck;
import dev.wallpaperhub.platform.problem.ProblemExceptionHandler;
import dev.wallpaperhub.platform.problem.ProblemWriter;
import dev.wallpaperhub.platform.problem.Problems;
import dev.wallpaperhub.platform.security.JwtValidation;
import dev.wallpaperhub.platform.security.ProblemAccessDeniedHandler;
import dev.wallpaperhub.platform.security.ProblemAuthenticationEntryPoint;
import dev.wallpaperhub.platform.security.PublicPaths;
import dev.wallpaperhub.platform.security.UserContextFilter;
import dev.wallpaperhub.platform.web.PlatformRequestFilter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/** Everything {@link PlatformApplication} brings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformProperties.class)
@EnableWebSecurity
class PlatformConfiguration {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    Messages messages() {
        return Messages.load(JSON);
    }

    @Bean
    Problems problems(Messages messages) {
        return new Problems(messages);
    }

    @Bean
    ProblemWriter problemWriter(Problems problems) {
        return new ProblemWriter(problems, JSON);
    }

    @Bean
    ProblemExceptionHandler problemExceptionHandler(Problems problems) {
        return new ProblemExceptionHandler(problems);
    }

    /** A {@code Filter} bean is registered by Spring Boot with the order it declares. */
    @Bean
    PlatformRequestFilter platformRequestFilter() {
        return new PlatformRequestFilter();
    }

    @Bean
    JwksReadiness jwksReadiness(PlatformProperties properties) {
        return new JwksReadiness(properties.oidc().jwksUri());
    }

    @Bean
    ManagementServer managementServer(PlatformProperties properties, List<ReadinessCheck> checks) {
        return new ManagementServer(properties.management().port(), checks);
    }

    @Bean
    JwtDecoder jwtDecoder(PlatformProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.oidc().jwksUri()).build();
        decoder.setJwtValidator(JwtValidation.validator(properties.oidc().issuerUri(), properties.oidc().clientId()));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder decoder, ProblemWriter writer,
                                    ObjectProvider<PublicPaths> publicPaths) throws Exception {
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(writer);
        ProblemAccessDeniedHandler deniedHandler = new ProblemAccessDeniedHandler(writer);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    publicPaths.orderedStream()
                            .forEach(paths -> paths.patterns().forEach(pattern -> authorize.requestMatchers(pattern).permitAll()));
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(JwtValidation.converter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .addFilterAfter(new UserContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
