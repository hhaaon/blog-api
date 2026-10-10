package com.hao.blog.user.api;

import com.hao.blog.user.domain.UserRole;

public record MeResponse (
        Long id,
        String email,
        UserRole role
){
}
