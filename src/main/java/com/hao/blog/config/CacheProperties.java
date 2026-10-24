package com.hao.blog.config;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties (
        @DefaultValue("5m")
        @DurationMin(nanos = 0, inclusive = false)
        Duration ttl
) {
}
