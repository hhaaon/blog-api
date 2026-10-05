package com.hao.blog.user.domain;

public class User {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserRole role;

    public User(
            Long id,
            String email,
            String passwordHash,
            UserRole role
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserRole role() {
        return role;
    }
}
