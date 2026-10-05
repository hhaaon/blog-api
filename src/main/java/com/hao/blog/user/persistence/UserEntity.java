package com.hao.blog.user.persistence;

import com.hao.blog.user.domain.UserRole;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    protected  UserEntity() {
    }

    public UserEntity(
            String email,
            String passwordHash,
            UserRole role
    ) {
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
