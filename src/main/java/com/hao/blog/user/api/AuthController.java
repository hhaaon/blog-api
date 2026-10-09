package com.hao.blog.user.api;

import com.hao.blog.user.application.InvalidCredentialsException;
import com.hao.blog.user.application.RegistrationService;
import com.hao.blog.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final RegistrationService registrationService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            RegistrationService registrationService
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.registrationService = registrationService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterResponse>  register(
            @Valid @RequestBody RegisterRequest request
    ) {
        User registeredUser =
                registrationService.register(
                        request.email(),
                        request.password()
                );

        RegisterResponse response = new RegisterResponse(
                registeredUser.id(),
                registeredUser.email(),
                registeredUser.role()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UsernamePasswordAuthenticationToken authenticationToken =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.email(),
                        loginRequest.password()
                );

        Authentication authentication;

        try {
            authentication =
                    authenticationManager.authenticate(authenticationToken);
        } catch (BadCredentialsException exception) {
            throw new InvalidCredentialsException();
        }


        sessionAuthenticationStrategy.onAuthentication(
                authentication,
                request,
                response
        );

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);

        SecurityContextHolder.setContext(securityContext);

        securityContextRepository.saveContext(
                securityContext,
                request,
                response
        );

        return ResponseEntity.noContent().build();
    }
}
