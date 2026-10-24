package com.hao.blog.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CachePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CacheProperties.class)
    static class TestConfig {
    }

    @Test
    void defaultTtl_contextStartsWithFiveMinutes() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CacheProperties.class);

            CacheProperties properties =
                    context.getBean(CacheProperties.class);

            assertThat(properties.ttl())
                    .isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    void validOverride_contextStartsWithConfiguredTtl() {
        contextRunner
                .withPropertyValues("app.cache.ttl=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheProperties.class);

                    CacheProperties properties =
                            context.getBean(CacheProperties.class);

                    assertThat(properties.ttl())
                            .isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void zeroTtl_contextFailsValidation() {
        contextRunner
                .withPropertyValues("app.cache.ttl=0s")
                .run(context -> {
                    assertThat(context).hasFailed();

                    assertThat(context)
                            .getFailure()
                            .hasRootCauseInstanceOf(
                                    BindValidationException.class
                            );
                });
    }
}