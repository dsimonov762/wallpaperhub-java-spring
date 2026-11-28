package dev.wallpaperhub.account.user;

import dev.wallpaperhub.account.grpc.InternalCallInterceptor;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Spring Boot exposes every {@link BindableService} bean as a gRPC service. The service itself is deliberately not a bean (it would
 * be exposed without the check of the token): the bean is the service wrapped in {@link InternalCallInterceptor}.
 */
@Configuration(proxyBeanMethods = false)
class UserGrpcConfiguration {

    @Bean
    BindableService accountService(UserService users, JwtDecoder decoder) {
        AccountGrpcService service = new AccountGrpcService(users);
        InternalCallInterceptor interceptor = new InternalCallInterceptor(decoder);
        return () -> ServerInterceptors.intercept(service.bindService(), interceptor);
    }
}
