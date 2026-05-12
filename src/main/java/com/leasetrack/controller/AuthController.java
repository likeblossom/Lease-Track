package com.leasetrack.controller;

import com.leasetrack.dto.request.AcceptInvitationRequest;
import com.leasetrack.dto.request.CreateInvitationRequest;
import com.leasetrack.dto.request.LoginRequest;
import com.leasetrack.dto.request.RegisterRequest;
import com.leasetrack.dto.response.InvitationResponse;
import com.leasetrack.dto.response.LoginResponse;
import com.leasetrack.dto.response.UserResponse;
import com.leasetrack.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user and return a JWT access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a public landlord account")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a single-use user invitation")
    public InvitationResponse createInvitation(@Valid @RequestBody CreateInvitationRequest request) {
        return authService.createInvitation(request);
    }

    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept an invitation and create the invited user")
    public UserResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return authService.acceptInvitation(request);
    }
}
