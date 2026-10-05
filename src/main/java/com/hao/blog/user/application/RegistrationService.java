package com.hao.blog.user.application;

import com.hao.blog.user.domain.User;
import com.hao.blog.user.domain.UserRepository;
import com.hao.blog.user.domain.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword) {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User(
                null,
                normalizedEmail,
                passwordHash,
                UserRole.USER
        );

        return userRepository.save(user);
    }

}
