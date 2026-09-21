package com.hao.blog.common.exception;

public record ErrorResponse(
        String code,
        String message
) {
}
