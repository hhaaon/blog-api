package com.hao.blog.user.api;

public record CsrfResponse(
        String token,
        String headerName
) {
}