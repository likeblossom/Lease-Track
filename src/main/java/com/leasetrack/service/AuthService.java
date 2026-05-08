package com.leasetrack.service;

import com.leasetrack.dto.request.LoginRequest;
import com.leasetrack.dto.response.LoginResponse;
import com.leasetrack.security.JwtService;
import com.leasetrack.security.UserPrincipal;
import com.leasetrack.security.UserPrincipalService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserPrincipalService userPrincipalService;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserPrincipalService userPrincipalService,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userPrincipalService = userPrincipalService;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()));
        UserPrincipal userPrincipal = (UserPrincipal) userPrincipalService.loadUserByUsername(request.email());
        return new LoginResponse(jwtService.generateToken(userPrincipal), "Bearer", jwtService.getExpirationSeconds());
    }
}
