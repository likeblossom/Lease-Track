package com.leasetrack.service;

import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.entity.UserInvitation;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.request.AcceptInvitationRequest;
import com.leasetrack.dto.request.CreateInvitationRequest;
import com.leasetrack.dto.request.LoginRequest;
import com.leasetrack.dto.request.RegisterRequest;
import com.leasetrack.dto.response.InvitationResponse;
import com.leasetrack.dto.response.LoginResponse;
import com.leasetrack.dto.response.UserResponse;
import com.leasetrack.exception.InvalidInvitationException;
import com.leasetrack.exception.UserRegistrationException;
import com.leasetrack.repository.UserInvitationRepository;
import com.leasetrack.repository.UserRepository;
import com.leasetrack.security.CurrentUserService;
import com.leasetrack.security.JwtService;
import com.leasetrack.security.UserPrincipal;
import com.leasetrack.security.UserPrincipalService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final long INVITATION_EXPIRATION_SECONDS = 7 * 24 * 60 * 60;

    private final AuthenticationManager authenticationManager;
    private final UserPrincipalService userPrincipalService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserInvitationRepository userInvitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserPrincipalService userPrincipalService,
            JwtService jwtService,
            UserRepository userRepository,
            UserInvitationRepository userInvitationRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.userPrincipalService = userPrincipalService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userInvitationRepository = userInvitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                normalizedEmail,
                request.password()));
        UserPrincipal userPrincipal = (UserPrincipal) userPrincipalService.loadUserByUsername(normalizedEmail);
        return new LoginResponse(jwtService.generateToken(userPrincipal), "Bearer", jwtService.getExpirationSeconds());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserRegistrationException("Email is already registered");
        }

        long userCount = userRepository.count();
        if (userCount == 0 && request.role() != UserRole.ADMIN) {
            throw new UserRegistrationException("The first registered user must be an admin");
        }
        if (userCount > 0 && (request.role() == UserRole.ADMIN || request.role() == UserRole.TENANT)) {
            throw new UserRegistrationException("This role requires an invitation");
        }

        return toResponse(createUser(normalizedEmail, request.password(), request.displayName(), request.role()));
    }

    @Transactional
    public InvitationResponse createInvitation(CreateInvitationRequest request) {
        User inviter = currentUserService.currentUser();
        assertCanInvite(inviter, request.role());

        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserRegistrationException("Email is already registered");
        }

        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant now = Instant.now(clock);

        UserInvitation invitation = new UserInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setEmail(normalizedEmail);
        invitation.setTokenHash(hashToken(token));
        invitation.setDisplayName(request.displayName());
        invitation.setRole(request.role());
        invitation.setInvitedByUserId(inviter.getId());
        invitation.setExpiresAt(now.plusSeconds(INVITATION_EXPIRATION_SECONDS));
        invitation.setCreatedAt(now);

        UserInvitation savedInvitation = userInvitationRepository.save(invitation);
        return new InvitationResponse(
                savedInvitation.getId(),
                savedInvitation.getEmail(),
                savedInvitation.getDisplayName(),
                savedInvitation.getRole(),
                savedInvitation.getExpiresAt(),
                token);
    }

    @Transactional
    public UserResponse acceptInvitation(AcceptInvitationRequest request) {
        Instant now = Instant.now(clock);
        UserInvitation invitation = userInvitationRepository.findByTokenHash(hashToken(request.token()))
                .orElseThrow(() -> new InvalidInvitationException("Invitation token is invalid"));

        if (invitation.getAcceptedAt() != null) {
            throw new InvalidInvitationException("Invitation has already been accepted");
        }
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new InvalidInvitationException("Invitation has expired");
        }
        if (userRepository.findByEmail(invitation.getEmail()).isPresent()) {
            throw new UserRegistrationException("Email is already registered");
        }

        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? invitation.getDisplayName()
                : request.displayName();
        if (displayName == null || displayName.isBlank()) {
            throw new UserRegistrationException("Display name is required");
        }

        User user = createUser(invitation.getEmail(), request.password(), displayName, invitation.getRole());
        invitation.setAcceptedAt(now);
        return toResponse(user);
    }

    private User createUser(String email, String password, String displayName, UserRole role) {
        Instant now = Instant.now(clock);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(now);
        return userRepository.save(user);
    }

    private void assertCanInvite(User inviter, UserRole invitedRole) {
        if (inviter.getRole() == UserRole.ADMIN) {
            return;
        }
        if ((inviter.getRole() == UserRole.LANDLORD || inviter.getRole() == UserRole.PROPERTY_MANAGER)
                && invitedRole == UserRole.TENANT) {
            return;
        }
        throw new AccessDeniedException("User cannot invite users with role " + invitedRole);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
