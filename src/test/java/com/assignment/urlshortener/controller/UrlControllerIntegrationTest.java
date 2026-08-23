package com.assignment.urlshortener.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shortenAndRedirect_fullFlow_worksEndToEnd() throws Exception {
        // Shorten
        String requestBody = objectMapper.writeValueAsString(
                Map.of("originalUrl", "https://example.com"));

        String response = mockMvc.perform(post("/api/urls")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String shortUrl = objectMapper.readTree(response).get("shortUrl").asText();
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);

        // Redirect
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void shorten_returnsBadRequest_whenUrlInvalid() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of("originalUrl", "not-a-url"));

        mockMvc.perform(post("/api/urls")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void redirect_returnsNotFound_whenShortCodeDoesNotExist() throws Exception {
        mockMvc.perform(get("/nonexistent99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void shorten_returnsConflict_whenCustomAliasAlreadyTaken() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("originalUrl", "https://first.com", "customAlias", "taken1"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isOk());

        String dupBody = objectMapper.writeValueAsString(
                Map.of("originalUrl", "https://second.com", "customAlias", "taken1"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(dupBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void deactivateUrl_evictsCacheAndBlocksRedirect() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://cachetest.com"));
        String response = mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString();
        String shortUrl = objectMapper.readTree(response).get("shortUrl").asText();
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);

        // Warm the cache
        mockMvc.perform(get("/" + shortCode)).andExpect(status().isFound());

        // Deactivate
        mockMvc.perform(patch("/api/urls/" + shortCode + "/deactivate"))
                .andExpect(status().isNoContent());

        // Must now be blocked, not served from stale cache
        mockMvc.perform(get("/" + shortCode)).andExpect(status().isGone());
    }
}
