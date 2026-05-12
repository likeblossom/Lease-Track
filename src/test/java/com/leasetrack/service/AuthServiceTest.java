package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.request.RegisterRequest;
import com.leasetrack.dto.response.UserResponse;
import com.leasetrack.exception.UserRegistrationException;
import com.leasetrack.repository.UserInvitationRepository;
import com.leasetrack.repository.UserRepository;
import com.leasetrack.security.CurrentUserService;
import com.leasetrack.security.JwtService;
import com.leasetrack.security.UserPrincipalService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-12T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserPrincipalService userPrincipalService;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInvitationRepository userInvitationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserService currentUserService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                userPrincipalService,
                jwtService,
                userRepository,
                userInvitationRepository,
                passwordEncoder,
                currentUserService,
                CLOCK);
    }

    @Test
    void registerAllowsOnlyLandlordForPublicSignupAfterBootstrap() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest(
                "Owner@Example.com",
                "password123",
                "Owner User",
                UserRole.LANDLORD));

        assertThat(response.email()).isEqualTo("owner@example.com");
        assertThat(response.displayName()).isEqualTo("Owner User");
        assertThat(response.role()).isEqualTo(UserRole.LANDLORD);
    }

    @Test
    void registerRejectsPropertyManagerForPublicSignupAfterBootstrap() {
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "manager@example.com",
                "password123",
                "Manager User",
                UserRole.PROPERTY_MANAGER)))
                .isInstanceOf(UserRegistrationException.class)
                .hasMessage("This role requires an invitation");

        verify(userRepository, never()).save(any(User.class));
    }
}
