package io.coredge.health.claims.service.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
public class KeycloakTokenService {

    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public String getToken() {

        String url = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("grant_type", "client_credentials");
        data.add("client_id", clientId);
        data.add("client_secret", clientSecret);
        data.add("audience", "operate-api");


        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(data, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);

        assert response.getBody() != null;
        return response.getBody().get("access_token").asText();
    }
}
