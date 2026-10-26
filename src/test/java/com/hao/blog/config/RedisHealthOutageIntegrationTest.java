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

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        PostgresTestConfiguration.class,
        RedisTestConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RedisHealthOutageIntegrationTest {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private final MockMvc mockMvc;
    private final GenericContainer<?> redisContainer;

    @Autowired
    RedisHealthOutageIntegrationTest(
            MockMvc mockMvc,
            ApplicationContext applicationContext
    ) {
        this.mockMvc = mockMvc;

        Object containerBean =
                applicationContext.getBean("redisContainer");

        if (!(containerBean instanceof GenericContainer<?> container)) {
            throw new IllegalStateException(
                    "Bean 'redisContainer' is not a Testcontainers GenericContainer: "
                            + containerBean.getClass().getName()
            );
        }

        this.redisContainer = container;
    }

    @Test
    void redisOutage_keepsLivenessAndReadinessUp_butMakesOverallHealthDown()
            throws Exception {

        /*
         * Baseline:
         * application starts with both required PostgreSQL
         * and optional Redis available.
         */
        assertTrue(redisContainer.isRunning());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        /*
         * Simulate a runtime Redis outage.
         *
         * Redis is optional for serving the required workload,
         * so losing it should degrade the application but should
         * not remove this instance from traffic.
         */
        redisContainer.stop();

        assertFalse(redisContainer.isRunning());

        /*
         * First wait until Actuator has actually observed Redis as unhealthy.
         *
         * This is important:
         * asserting readiness immediately after stop() could accidentally
         * pass before the Redis health contributor notices the outage.
         */
        awaitOverallHealthDown();

        /*
         * At this point Redis is observably unhealthy.
         *
         * But Redis is NOT part of our readiness group.
         */
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        /*
         * Redis failure is external to this JVM.
         * Restarting the application would not repair Redis.
         */
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void awaitOverallHealthDown() throws Exception {
        Instant deadline = Instant.now().plus(HEALTH_TIMEOUT);

        Integer lastStatus = null;
        String lastBody = null;

        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(
                            get("/actuator/health")
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
                "Overall health did not become DOWN within "
                        + HEALTH_TIMEOUT
                        + ". Last HTTP status="
                        + lastStatus
                        + ", last body="
                        + lastBody
        );
    }
}