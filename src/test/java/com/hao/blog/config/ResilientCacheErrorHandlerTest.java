package com.hao.blog.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCacheErrorHandlerTest {

    private ResilientCacheErrorHandler errorHandler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        errorHandler = new ResilientCacheErrorHandler();

        cache = mock(Cache.class);
        when(cache.getName()).thenReturn("posts");
    }

    @Test
    void cacheGetFailure_isSuppressed() {
        RuntimeException failure =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                errorHandler.handleCacheGetError(
                        failure,
                        cache,
                        123L
                )
        );
    }

    @Test
    void cachePutFailure_isSuppressed() {
        RuntimeException failure =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                errorHandler.handleCachePutError(
                        failure,
                        cache,
                        123L,
                        "do-not-log-this-value"
                )
        );
    }

    @Test
    void cacheEvictFailure_isSuppressed() {
        RuntimeException failure =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                errorHandler.handleCacheEvictError(
                        failure,
                        cache,
                        123L
                )
        );
    }

    @Test
    void cacheClearFailure_isSuppressed() {
        RuntimeException failure =
                new RuntimeException("Redis unavailable");

        assertDoesNotThrow(() ->
                errorHandler.handleCacheClearError(
                        failure,
                        cache
                )
        );
    }
}