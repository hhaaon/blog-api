package com.hao.blog.user.persistence;

import com.hao.blog.testsupport.PostgresTestConfiguration;
import com.hao.blog.user.application.DuplicateEmailException;
import com.hao.blog.user.domain.User;
import com.hao.blog.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class JpaUserRepositoryAdapterTest {

    @Autowired
    private JpaUserRepositoryAdapter adapter;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @BeforeEach
    void cleanDatabase() {
        userJpaRepository.deleteAll();
    }

    @Test
    void save_validUser_persistsAndReturnsGeneratedId() {
        User user = new User(
                null,
                "alice@example.com",
                "ENCODED_PASSWORD",
                UserRole.USER
        );

        User saved = adapter.save(user);

        assertNotNull(saved.id());
        assertEquals("alice@example.com", saved.email());
        assertEquals("ENCODED_PASSWORD", saved.passwordHash());
        assertEquals(UserRole.USER, saved.role());

        Optional<User> loaded =
                adapter.findByEmail("alice@example.com");

        assertTrue(loaded.isPresent());
        assertEquals(saved.id(), loaded.get().id());
        assertEquals("alice@example.com", loaded.get().email());
        assertEquals("ENCODED_PASSWORD", loaded.get().passwordHash());
        assertEquals(UserRole.USER, loaded.get().role());
    }

    @Test
    void save_duplicateEmail_translatesToDuplicateEmailException() {
        User first = new User(
                null,
                "alice@example.com",
                "HASH_1",
                UserRole.USER
        );

        User second = new User(
                null,
                "alice@example.com",
                "HASH_2",
                UserRole.USER
        );

        adapter.save(first);

        assertThrows(
                DuplicateEmailException.class,
                () -> adapter.save(second)
        );
    }
}