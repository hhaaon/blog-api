package com.hao.blog.user.api;

import com.hao.blog.user.application.RegistrationService;
import com.hao.blog.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(
            RegistrationService registrationService
    ) {
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
}
