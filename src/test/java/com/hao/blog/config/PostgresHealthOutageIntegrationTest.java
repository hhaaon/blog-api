package com.hao.blog.config;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.testsupport.RedisTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.hikari.connection-timeout=1000"
})
@AutoConfigureMockMvc
@Import({
        PostgresTestConfiguration.class,
        RedisTestConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresHealthOutageIntegrationTest {

    private static final Duration READINESS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private final MockMvc mockMvc;
    private final GenericContainer<?> postgresContainer;

    @Autowired
    PostgresHealthOutageIntegrationTest(
            MockMvc mockMvc,
            ApplicationContext applicationContext
    ) {
        this.mockMvc = mockMvc;

        Object containerBean =
                applicationContext.getBean("postgresContainer");

        if (!(containerBean instanceof GenericContainer<?> container)) {
            throw new IllegalStateException(
                    "Bean 'postgresContainer' is not a Testcontainers GenericContainer: "
                            + containerBean.getClass().getName()
            );
        }

        this.postgresContainer = container;
    }

    @Test
    void postgresOutage_keepsLivenessUp_butMakesReadinessDown()
            throws Exception {

        // Baseline: application successfully started with PostgreSQL available.
        assertTrue(postgresContainer.isRunning());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Simulate a runtime PostgreSQL outage.
        postgresContainer.stop();

        assertFalse(postgresContainer.isRunning());

        // Readiness should eventually notice that the required DB is unavailable.
        awaitReadinessDown();

        // PostgreSQL being unavailable must not make the JVM "dead".
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void awaitReadinessDown() throws Exception {
        Instant deadline = Instant.now().plus(READINESS_TIMEOUT);

        Integer lastStatus = null;
        String lastBody = null;

        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(
                            get("/actuator/health/readiness")
                    )
                    .andReturn();

            lastStatus = result.getResponse().getStatus();
            lastBody = result.getResponse().getContentAsString();

            if (lastStatus == 503
                    && lastBody.contains("\"status\":\"DOWN\"")) {
                return;
            }

            Thread.sleep(POLL_INTERVAL.toMillis());
        }

        fail(
                "Readiness did not become DOWN within "
                        + READINESS_TIMEOUT
                        + ". Last HTTP status="
                        + lastStatus
                        + ", last body="
                        + lastBody
        );
    }
}