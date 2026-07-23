package com.example.fams.external;

import com.example.fams.keycloak.KeycloakService;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CompanyStructureFeignConfig {

    @Bean
    public RequestInterceptor companyStructureAuthInterceptor(KeycloakService keycloakService) {
        return template -> template.header("Authorization", keycloakService.getAccessToken());
    }

    @Bean
    public Retryer companyStructureRetryer(
            @Value("${fams.company-structure.retry-period-ms:200}") long periodMs,
            @Value("${fams.company-structure.retry-max-period-ms:1000}") long maxPeriodMs,
            @Value("${fams.company-structure.retry-max-attempts:3}") int maxAttempts
    ) {
        return new Retryer.Default(periodMs, maxPeriodMs, maxAttempts);
    }

    @Bean
    public Logger.Level companyStructureFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
