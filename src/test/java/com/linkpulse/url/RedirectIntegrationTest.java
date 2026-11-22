
package com.linkpulse.url;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class RedirectIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private final Set<String> emailsToClean =
            ConcurrentHashMap.newKeySet();

    private final Set<String> codesToClean =
            ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanup() {

        emailsToClean.forEach(email ->
                jdbcTemplate.update(
                        "DELETE FROM users WHERE email = ?",
                        email
                )
        );

        codesToClean.forEach(code ->
                redisTemplate.delete(
                        "linkpulse:redirect:" + code
                )
        );
    }

    @Test
    void shouldRedirectAndRecordClicks() throws Exception {

        String token = registerAndGetToken();

        String alias = uniqueAlias();
        String destination = "https://example.com/article";

        long urlId = createUrl(
                token,
                alias,
                destination
        );

        // First request: Redis MISS, PostgreSQL lookup.
        HttpResponse<String> first = redirect(alias);

        assertEquals(302, first.statusCode());

        assertEquals(
                destination,
                first.headers()
                        .firstValue("Location")
                        .orElseThrow()
        );

        // The cache should now contain the URL.
        assertNotNull(
                redisTemplate.opsForValue().get(
                        "linkpulse:redirect:" + alias
                )
        );

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT click_count FROM short_urls WHERE id = ?",
                        Long.class,
                        urlId
                )
        );

        // Second request: Redis HIT.
        HttpResponse<String> second = redirect(alias);

        assertEquals(302, second.statusCode());

        assertEquals(
                2L,
                jdbcTemplate.queryForObject(
                        "SELECT click_count FROM short_urls WHERE id = ?",
                        Long.class,
                        urlId
                )
        );

        assertEquals(
                2L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM click_events WHERE short_url_id = ?",
                        Long.class,
                        urlId
                )
        );

        // Verify analytics.
        HttpResponse<String> analytics = request(
                "GET",
                "/api/v1/urls/" + urlId + "/analytics?days=7",
                null,
                token
        );

        assertEquals(200, analytics.statusCode());

        JsonNode analyticsBody =
                jsonMapper.readTree(analytics.body());

        assertEquals(
                2,
                analyticsBody.path("totalClicks").asLong()
        );

        long dailyTotal = 0;

        for (JsonNode day :
                analyticsBody.path("dailyClicks")) {

            dailyTotal += day.path("clicks").asLong();
        }

        assertEquals(2, dailyTotal);
    }

    @Test
    void deactivatedUrlShouldReturnGone() throws Exception {

        String token = registerAndGetToken();

        String alias = uniqueAlias();

        long urlId = createUrl(
                token,
                alias,
                "https://example.com/deactivated"
        );

        // Populate Redis cache first.
        assertEquals(302, redirect(alias).statusCode());

        HttpResponse<String> deletion = request(
                "DELETE",
                "/api/v1/urls/" + urlId,
                null,
                token
        );

        assertEquals(204, deletion.statusCode());

        // Cache should have been invalidated.
        assertNull(
                redisTemplate.opsForValue().get(
                        "linkpulse:redirect:" + alias
                )
        );

        HttpResponse<String> redirect = redirect(alias);

        assertEquals(410, redirect.statusCode());

        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT click_count FROM short_urls WHERE id = ?",
                        Long.class,
                        urlId
                )
        );
    }

    @Test
    void unknownShortCodeShouldReturnNotFound()
            throws Exception {

        HttpResponse<String> response =
                redirect(uniqueAlias());

        assertEquals(404, response.statusCode());
    }

    private String registerAndGetToken() throws Exception {

        String email =
                "redirect-" + UUID.randomUUID() + "@example.com";

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

        return jsonMapper.readTree(response.body())
                .path("accessToken")
                .asString();
    }

    private long createUrl(
            String token,
            String alias,
            String destination
    ) throws Exception {

        HttpResponse<String> response = request(
                "POST",
                "/api/v1/urls",
                Map.of(
                        "originalUrl", destination,
                        "customAlias", alias
                ),
                token
        );

        assertEquals(201, response.statusCode());

        return jsonMapper.readTree(response.body())
                .path("id")
                .asLong();
    }

    private HttpResponse<String> redirect(
            String alias
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/" + alias))
                .header(
                        "Referer",
                        "https://github.com/example/project"
                )
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
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
                            jsonMapper.writeValueAsString(body)
                    )
            );
        }

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String uniqueAlias() {

        String alias = "r" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 7);

        codesToClean.add(alias);

        return alias;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
