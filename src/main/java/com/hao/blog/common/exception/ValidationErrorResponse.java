package com.hao.blog.common.exception;

import java.util.Map;

public record ValidationErrorResponse(
        String code,
        Map<String, String> errors
) {
}
