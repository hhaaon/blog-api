package com.hao.blog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityProbeController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPosts_withoutAuthentication_isAllowed() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk());
    }

    @Test
    void postPosts_withoutAuthentication_withValidCsrf_isRejected() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void postPosts_withAuthenticationAndValidCsrf_isAllowed() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(user("alice"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void postPosts_withAuthenticationButWithoutCsrf_isRejected() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(user("alice")))
                .andExpect(status().isForbidden());
    }
}