package com.hao.blog.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCacheErrorHandlerTest {

    private ResilientCacheErrorHandler handler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        handler = new ResilientCacheErrorHandler();

        cache = mock(Cache.class);

        when(cache.getName())
                .thenReturn("posts");
    }

    @Test
    void getFailure_isSuppressed() {
        RuntimeException exception =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                handler.handleCacheGetError(
                        exception,
                        cache,
                        42L
                )
        );
    }

    @Test
    void putFailure_isSuppressed() {
        RuntimeException exception =
                new RuntimeException("Redis unavailable");

        Object cachedValue =
                new Object();

        assertDoesNotThrow(() ->
                handler.handleCachePutError(
                        exception,
                        cache,
                        42L,
                        cachedValue
                )
        );
    }

    @Test
    void evictFailure_isSuppressed() {
        RuntimeException exception =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                handler.handleCacheEvictError(
                        exception,
                        cache,
                        42L
                )
        );
    }

    @Test
    void clearFailure_isSuppressed() {
        RuntimeException exception =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                handler.handleCacheClearError(
                        exception,
                        cache
                )
        );
    }
}