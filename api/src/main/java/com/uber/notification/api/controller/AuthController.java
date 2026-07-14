package com.uber.notification.api.controller;

import com.uber.notification.api.dto.auth.AuthResponse;
import com.uber.notification.api.dto.auth.LoginRequest;
import com.uber.notification.api.dto.auth.RegisterRequest;
import com.uber.notification.api.security.JwtService;
import com.uber.notification.application.usecase.AuthenticateUserUseCase;
import com.uber.notification.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final JwtService jwtService;

    public AuthController(AuthenticateUserUseCase authenticateUserUseCase, JwtService jwtService) {
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authenticateUserUseCase.register(request.email(), request.password(), request.displayName());
        String token = jwtService.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user, token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authenticateUserUseCase.authenticate(request.email(), request.password());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(toResponse(user, token));
    }

    private AuthResponse toResponse(User user, String token) {
        return new AuthResponse(
                token, user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }
}
