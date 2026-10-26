package com.hao.blog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class ResilientCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ResilientCacheErrorHandler.class);

    private static final String CACHE_FAILURE_EVENT =
            "cache_operation_failed";

    private static final String REDACTED_KEY =
            "[REDACTED]";

    @Override
    public void handleCacheGetError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        logCacheFailure(
                "GET",
                exception,
                cache,
                key
        );

        /*
         * Intentionally suppress the cache exception.
         *
         * @Cacheable can then continue by executing the underlying
         * application method, which falls back to PostgreSQL.
         */
    }

    @Override
    public void handleCachePutError(
            RuntimeException exception,
            Cache cache,
            Object key,
            Object value
    ) {
        logCacheFailure(
                "PUT",
                exception,
                cache,
                key
        );

        /*
         * The source-of-truth operation has already succeeded.
         * Cache population failure must not turn that success into
         * an application failure.
         *
         * Never log "value": it may contain application/user data.
         */
    }

    @Override
    public void handleCacheEvictError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        logCacheFailure(
                "EVICT",
                exception,
                cache,
                key
        );

        /*
         * PostgreSQL remains the source of truth.
         *
         * An eviction failure may temporarily leave stale cache data,
         * but our current policy is graceful degradation rather than
         * failing an already-successful database operation.
         */
    }

    @Override
    public void handleCacheClearError(
            RuntimeException exception,
            Cache cache
    ) {
        logCacheFailure(
                "CLEAR",
                exception,
                cache,
                null
        );
    }

    private void logCacheFailure(
            String operation,
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        var event = log.atWarn()
                .addKeyValue("event", CACHE_FAILURE_EVENT)
                .addKeyValue("operation", operation)
                .addKeyValue("cache", cache.getName())
                .addKeyValue(
                        "exceptionClass",
                        exception.getClass().getName()
                );

        Object safeKey = safeKey(cache.getName(), key);

        if (safeKey != null) {
            event.addKeyValue("key", safeKey);
        }

        event.log(
                "Cache operation failed; continuing with degraded behavior"
        );
    }

    private Object safeKey(
            String cacheName,
            Object key
    ) {
        if (key == null) {
            return null;
        }

        /*
         * Explicit allowlist:
         *
         * The "posts" cache currently uses a numeric post ID.
         * Numeric IDs provide useful diagnostic context without
         * exposing request bodies, credentials, tokens, etc.
         *
         * Future cache keys are redacted by default.
         */
        if ("posts".equals(cacheName)
                && key instanceof Number) {
            return key;
        }

        return REDACTED_KEY;
    }
}