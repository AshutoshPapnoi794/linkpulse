
package com.linkpulse.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Set<String> emailsToClean =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @AfterEach
    void cleanup() {

        emailsToClean.forEach(email ->
                jdbcTemplate.update(
                        "DELETE FROM users WHERE email = ?",
                        email
                )
        );
    }

    @Test
    void registrationAndLoginShouldWork() throws Exception {

        String email = uniqueEmail();
        String password = "LinkPulseTest#2026";

        // Register
        HttpResponse<String> registration =
                register(email, password);

        assertEquals(201, registration.statusCode());

        JsonNode registrationBody =
                objectMapper.readTree(registration.body());

        String registrationToken =
                registrationBody.path("accessToken").asText();

        assertFalse(registrationToken.isBlank());

        assertEquals(
                "Bearer",
                registrationBody.path("tokenType").asText()
        );

        assertEquals(
                900,
                registrationBody.path("expiresInSeconds").asLong()
        );

        assertFalse(registrationBody.has("passwordHash"));

        // Login
        HttpResponse<String> login =
                request(
                        "POST",
                        "/api/v1/auth/login",
                        Map.of(
                                "email", email,
                                "password", password
                        ),
                        null
                );

        assertEquals(200, login.statusCode());

        JsonNode loginBody =
                objectMapper.readTree(login.body());

        String loginToken =
                loginBody.path("accessToken").asText();

        assertFalse(loginToken.isBlank());

        // Access protected current-user endpoint
        HttpResponse<String> profile =
                request(
                        "GET",
                        "/api/v1/auth/me",
                        null,
                        loginToken
                );

        assertEquals(200, profile.statusCode());

        JsonNode profileBody =
                objectMapper.readTree(profile.body());

        assertEquals(
                email,
                profileBody.path("email").asText()
        );

        assertEquals(
                "ROLE_USER",
                profileBody.path("role").asText()
        );

        assertTrue(
                profileBody.path("id").asLong() > 0
        );

        assertFalse(profileBody.has("passwordHash"));
    }

    @Test
    void protectedEndpointShouldRejectMissingAndInvalidTokens()
            throws Exception {

        HttpResponse<String> missingToken =
                request(
                        "GET",
                        "/api/v1/auth/me",
                        null,
                        null
                );

        assertEquals(401, missingToken.statusCode());

        HttpResponse<String> invalidToken =
                request(
                        "GET",
                        "/api/v1/auth/me",
                        null,
                        "invalid.token.value"
                );

        assertEquals(401, invalidToken.statusCode());
    }

    @Test
    void duplicateEmailShouldReturnConflict() throws Exception {

        String email = uniqueEmail();

        HttpResponse<String> first =
                register(email, "LinkPulseTest#2026");

        assertEquals(201, first.statusCode());

        HttpResponse<String> second =
                register(email, "LinkPulseTest#2026");

        assertEquals(409, second.statusCode());
    }

    @Test
    void incorrectPasswordShouldReturnUnauthorized()
            throws Exception {

        String email = uniqueEmail();

        HttpResponse<String> registration =
                register(email, "LinkPulseTest#2026");

        assertEquals(201, registration.statusCode());

        HttpResponse<String> login =
                request(
                        "POST",
                        "/api/v1/auth/login",
                        Map.of(
                                "email", email,
                                "password", "WrongPassword#2026"
                        ),
                        null
                );

        assertEquals(401, login.statusCode());
    }

    @Test
    void invalidRegistrationShouldReturnBadRequest()
            throws Exception {

        HttpResponse<String> response =
                request(
                        "POST",
                        "/api/v1/auth/register",
                        Map.of(
                                "email", "invalid-email",
                                "password", "short"
                        ),
                        null
                );

        assertEquals(400, response.statusCode());
    }

    private HttpResponse<String> register(
            String email,
            String password
    ) throws Exception {

        emailsToClean.add(email);

        return request(
                "POST",
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", password
                ),
                null
        );
    }

    private HttpResponse<String> request(
            String method,
            String path,
            Object body,
            String token
    ) throws Exception {

        HttpRequest.Builder builder =
                HttpRequest.newBuilder(
                        URI.create(baseUrl() + path)
                ).header("Accept", "application/json");

        if (token != null) {
            builder.header(
                    "Authorization",
                    "Bearer " + token
            );
        }

        if (body == null) {

            builder.method(
                    method,
                    HttpRequest.BodyPublishers.noBody()
            );

        } else {

            builder.header(
                    "Content-Type",
                    "application/json"
            );

            builder.method(
                    method,
                    HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)
                    )
            );
        }

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }
}
