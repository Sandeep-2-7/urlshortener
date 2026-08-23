package com.assignment.urlshortener.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrendingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void trending_returnsUrlsOrderedByRecentClickCount() throws Exception {
        String body1 = objectMapper.writeValueAsString(Map.of("originalUrl", "https://popular.com"));
        String res1 = mockMvc.perform(post("/api/urls").contentType("application/json").content(body1))
                .andReturn().getResponse().getContentAsString();
        String code1 = extractShortCode(res1);

        String body2 = objectMapper.writeValueAsString(Map.of("originalUrl", "https://lesspopular.com"));
        String res2 = mockMvc.perform(post("/api/urls").contentType("application/json").content(body2))
                .andReturn().getResponse().getContentAsString();
        String code2 = extractShortCode(res2);

        // code1 gets 3 clicks, code2 gets 1
        mockMvc.perform(get("/" + code1)).andExpect(status().isFound());
        mockMvc.perform(get("/" + code1)).andExpect(status().isFound());
        mockMvc.perform(get("/" + code1)).andExpect(status().isFound());
        mockMvc.perform(get("/" + code2)).andExpect(status().isFound());

        // Async click logging — brief wait for background thread to persist.
        // NOTE: fixed sleep is a pragmatic test shortcut, not best practice.
        // A more robust approach would use Awaitility's polling assertions
        // to avoid flaky tests under load.
        Thread.sleep(500);

        mockMvc.perform(get("/api/urls/trending").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shortCode", is(code1)))
                .andExpect(jsonPath("$[0].recentClicks", is(3)));
    }

    @Test
    void trending_respectsLimitParameter() throws Exception {
        mockMvc.perform(get("/api/urls/trending").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(2))));
    }

    private String extractShortCode(String jsonResponse) throws Exception {
        String shortUrl = objectMapper.readTree(jsonResponse).get("shortUrl").asText();
        return shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
    }
}