package com.hao.blog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class ResilientCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ResilientCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        log.warn("Cache operation failed: operation={}, cache={}, key={}",
                "GET",
                cache.getName(),
                key,
                exception
                );
    }

    @Override
    public void handleCachePutError(
            RuntimeException exception,
            Cache cache,
            Object key,
            Object value
    ) {
        log.warn("Cache operation failed: operation={}, cache={}, key={}",
                "PUT",
                cache.getName(),
                key,
                exception
        );
    }

    @Override
    public void handleCacheEvictError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        log.warn("Cache operation failed: operation={}, cache={}, key={}",
                "EVICT",
                cache.getName(),
                key,
                exception
        );
    }

    @Override
    public void handleCacheClearError(
            RuntimeException exception,
            Cache cache
    ) {
        log.warn("Cache operation failed: operation={}, cache={}",
                "CLEAR",
                cache.getName(),
                exception
        );
    }
}
