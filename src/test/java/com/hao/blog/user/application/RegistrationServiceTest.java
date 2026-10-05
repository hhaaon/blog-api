package com.hao.blog.user.application;

import com.hao.blog.user.domain.User;
import com.hao.blog.user.domain.UserRepository;
import com.hao.blog.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void register_normalizesEmail_encodesPassword_assignsUserRole_andSavesUser() {
        when(passwordEncoder.encode("secret123"))
                .thenReturn("ENCODED");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = registrationService.register(
                "  Alice@Example.COM  ",
                "secret123"
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals(
                "alice@example.com",
                savedUser.email()
        );

        assertEquals(
                "ENCODED",
                savedUser.passwordHash()
        );

        assertEquals(
                UserRole.USER,
                savedUser.role()
        );

        assertSame(savedUser, result);
    }
}