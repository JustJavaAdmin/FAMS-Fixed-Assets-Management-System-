package com.example.fams.external;

import com.example.fams.keycloak.KeycloakService;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyStructureFeignConfigTest {

    @Test
    void authInterceptorAddsBearerToken() {
        KeycloakService keycloakService = mock(KeycloakService.class);
        when(keycloakService.getAccessToken()).thenReturn("Bearer test-token");

        CompanyStructureFeignConfig config = new CompanyStructureFeignConfig();
        RequestTemplate template = new RequestTemplate();

        config.companyStructureAuthInterceptor(keycloakService).apply(template);

        assertEquals("Bearer test-token", template.headers().get(HttpHeaders.AUTHORIZATION).iterator().next());
    }
}
