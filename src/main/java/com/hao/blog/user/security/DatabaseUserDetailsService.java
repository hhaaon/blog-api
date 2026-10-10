package com.hao.blog.user.security;

import com.hao.blog.user.domain.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        com.hao.blog.user.domain.User user =
                userRepository.findByEmail(normalizedEmail)
                        .orElseThrow(() ->
                                new UsernameNotFoundException(
                                        "Invalid credentials"
                                )
                        );

        return new AuthenticatedUser(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.role()
        );
    }
}