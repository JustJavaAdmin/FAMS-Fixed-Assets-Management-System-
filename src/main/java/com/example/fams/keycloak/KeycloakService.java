package com.example.fams.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final KeycloakFeignClient keycloakClient;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private static final String GRANT_TYPE = "client_credentials";

    private String token;
    private Instant tokenExpiry;

    private void setToken(Map<String, Object> tokenResponse) {
        Object accessToken = tokenResponse.get("access_token");
        Object expiresIn = tokenResponse.get("expires_in");

        if (accessToken != null && expiresIn != null) {
            this.token = "Bearer " + accessToken;
            this.tokenExpiry = Instant.now().plusSeconds(((Number) expiresIn).longValue());
        }
    }

    private boolean isTokenValid() {
        return token != null && tokenExpiry != null && tokenExpiry.isAfter(Instant.now());
    }

    private String getCurrentToken() {
        return this.token;
    }

    public String getAccessToken() {
        if (isTokenValid()) {
            return getCurrentToken();
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", GRANT_TYPE);

        Map<String, Object> tokenResponse = keycloakClient.getAccessToken(form);
        setToken(tokenResponse);

        return getCurrentToken();
    }
}
