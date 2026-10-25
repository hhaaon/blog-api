package com.hao.blog.config;

import com.hao.blog.post.application.PostView;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig implements CachingConfigurer {

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler();
    }

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            CacheProperties properties
    ) {
        RedisCacheConfiguration postConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(properties.ttl())
                        .disableCachingNullValues()
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new JacksonJsonRedisSerializer<>(
                                                        PostView.class
                                                )
                                        )
                        );

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration("posts", postConfig)
                .transactionAware()
                .build();
    }

}
