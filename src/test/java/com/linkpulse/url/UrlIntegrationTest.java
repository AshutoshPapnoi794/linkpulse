
package com.linkpulse.url;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class UrlIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Set<String> emailsToClean =
            ConcurrentHashMap.newKeySet();

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
    void shouldCreateCustomAliasAndListUrls() throws Exception {

        String token = registerAndGetToken();

        HttpResponse<String> created = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl",
                        "https://github.com/spring-projects/spring-boot",
                        "customAlias",
                        "Spring-Boot"
                ),
                token
        );

        assertEquals(201, created.statusCode());

        JsonNode body = objectMapper.readTree(created.body());

        assertEquals(
                "spring-boot",
                body.path("shortCode").asText()
        );

        assertEquals(
                "https://github.com/spring-projects/spring-boot",
                body.path("originalUrl").asText()
        );

        assertTrue(
                body.path("shortUrl").asText()
                        .endsWith("/spring-boot")
        );

        assertTrue(body.path("active").asBoolean());

        // Verify dashboard listing.
        HttpResponse<String> listing = request(
                "GET",
                "/api/v1/urls?page=0&size=10",
                null,
                token
        );

        assertEquals(200, listing.statusCode());

        JsonNode listingBody =
                objectMapper.readTree(listing.body());

        assertEquals(
                1,
                listingBody.path("totalElements").asLong()
        );

        assertEquals(
                "spring-boot",
                listingBody.path("content")
                        .get(0)
                        .path("shortCode")
                        .asText()
        );
    }

    @Test
    void differentUsersCannotAccessEachOthersUrls()
            throws Exception {

        String ownerToken = registerAndGetToken();
        String otherToken = registerAndGetToken();

        HttpResponse<String> created = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl",
                        "https://example.com/private-resource"
                ),
                ownerToken
        );

        assertEquals(201, created.statusCode());

        JsonNode body = objectMapper.readTree(created.body());

        long urlId = body.path("id").asLong();

        // Other user attempts to retrieve the URL.
        HttpResponse<String> getResponse = request(
                "GET",
                "/api/v1/urls/" + urlId,
                null,
                otherToken
        );

        assertEquals(404, getResponse.statusCode());

        // Other user attempts to deactivate the URL.
        HttpResponse<String> deleteResponse = request(
                "DELETE",
                "/api/v1/urls/" + urlId,
                null,
                otherToken
        );

        assertEquals(404, deleteResponse.statusCode());

        // Original owner can still retrieve it.
        HttpResponse<String> ownerResponse = request(
                "GET",
                "/api/v1/urls/" + urlId,
                null,
                ownerToken
        );

        assertEquals(200, ownerResponse.statusCode());
    }

    @Test
    void duplicateCustomAliasShouldReturnConflict()
            throws Exception {

        String token = registerAndGetToken();

        HttpResponse<String> first = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl", "https://example.com/first",
                        "customAlias", "my-alias"
                ),
                token
        );

        assertEquals(201, first.statusCode());

        HttpResponse<String> second = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl", "https://example.com/second",
                        "customAlias", "MY-ALIAS"
                ),
                token
        );

        assertEquals(409, second.statusCode());
    }

    @Test
    void invalidUrlShouldBeRejected() throws Exception {

        String token = registerAndGetToken();

        HttpResponse<String> response = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl", "javascript:alert(1)"
                ),
                token
        );

        assertEquals(400, response.statusCode());
    }

    @Test
    void shouldUpdateExpirationAndDeactivateUrl()
            throws Exception {

        String token = registerAndGetToken();

        HttpResponse<String> created = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl", "https://example.com"
                ),
                token
        );

        assertEquals(201, created.statusCode());

        long urlId = objectMapper.readTree(created.body())
                .path("id")
                .asLong();

        Instant expiration = Instant.now().plusSeconds(3600);

        HttpResponse<String> updated = request(
                "PATCH",
                "/api/v1/urls/" + urlId,
                Map.of("expiresAt", expiration.toString()),
                token
        );

        assertEquals(200, updated.statusCode());

        assertEquals(
                expiration.toEpochMilli(),
                Instant.parse(
                        objectMapper.readTree(updated.body())
                                .path("expiresAt")
                                .asString()
                ).toEpochMilli()
        );

        HttpResponse<String> deleted = request(
                "DELETE",
                "/api/v1/urls/" + urlId,
                null,
                token
        );

        assertEquals(204, deleted.statusCode());

        HttpResponse<String> retrieved = request(
                "GET",
                "/api/v1/urls/" + urlId,
                null,
                token
        );

        assertEquals(200, retrieved.statusCode());

        assertFalse(
                objectMapper.readTree(retrieved.body())
                        .path("active")
                        .asBoolean()
        );
    }

    @Test
    void urlCreationShouldRequireAuthentication()
            throws Exception {

        HttpResponse<String> response = request(
                "POST",
                "/api/v1/urls",
                Map.of("originalUrl", "https://example.com"),
                null
        );

        assertEquals(401, response.statusCode());
    }

    private String registerAndGetToken() throws Exception {

        String email = "url-test-" + UUID.randomUUID()
                + "@example.com";

        emailsToClean.add(email);

        HttpResponse<String> response = request(
                "POST",
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", "LinkPulseTest#2026"
                ),
                null
        );

        assertEquals(201, response.statusCode());

        return objectMapper.readTree(response.body())
                .path("accessToken")
                .asString();
    }

    private HttpResponse<String> request(
            String method,
            String path,
            Object body,
            String token
    ) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create(
                        "http://localhost:" + port + path
                )
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
}
