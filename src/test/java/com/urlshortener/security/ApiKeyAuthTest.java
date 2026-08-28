package com.urlshortener.security;

import com.jayway.jsonpath.JsonPath;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks for API-key enforcement on {@code POST /shorten} and that
 * the authenticated owner is stamped onto the stored record. Redirect and stats
 * must stay public.
 */
@SpringBootTest(properties = {
        "app.api-keys.acme=secret-acme",
        "app.api-keys.globex=secret-globex"
})
@AutoConfigureMockMvc
class ApiKeyAuthTest {

    private static final String JSON = "application/json";
    private static final String BODY = "{\"longUrl\":\"https://example.com/page\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    UrlRepository repository;

    @Test
    void rejectsRequestWithNoApiKey() throws Exception {
        mvc.perform(post("/shorten").contentType(JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void rejectsRequestWithUnknownApiKey() throws Exception {
        mvc.perform(post("/shorten").header("X-API-Key", "not-a-real-key").contentType(JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidKeyAndStampsOwnerOnRecord() throws Exception {
        String response = mvc.perform(post("/shorten")
                        .header("X-API-Key", "secret-acme").contentType(JSON).content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String code = JsonPath.read(response, "$.code");
        assertEquals("acme", repository.findByCode(code).orElseThrow().getOwnerId());
    }

    @Test
    void redirectEndpointStaysPublic() throws Exception {
        String response = mvc.perform(post("/shorten")
                        .header("X-API-Key", "secret-globex").contentType(JSON)
                        .content("{\"longUrl\":\"https://example.com/public\"}"))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(response, "$.code");

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/public"));
    }

    @Test
    void statsEndpointStaysPublic() throws Exception {
        String response = mvc.perform(post("/shorten")
                        .header("X-API-Key", "secret-acme").contentType(JSON).content(BODY))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(response, "$.code");

        mvc.perform(get("/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }
}
