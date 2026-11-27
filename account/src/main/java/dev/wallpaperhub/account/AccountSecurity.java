package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.security.PublicPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The operations of contracts/openapi/account.yaml that need no token. */
@Configuration(proxyBeanMethods = false)
class AccountSecurity {

    @Bean
    PublicPaths accountPublicPaths() {
        return new PublicPaths("/api/v1/registrations");
    }
}
