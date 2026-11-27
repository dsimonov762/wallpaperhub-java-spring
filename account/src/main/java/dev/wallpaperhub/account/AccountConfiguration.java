package dev.wallpaperhub.account;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccountConfiguration {

    /** One clock for the service, so that tests can fix the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
