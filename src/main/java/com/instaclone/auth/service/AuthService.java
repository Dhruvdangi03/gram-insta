package com.instaclone.auth.service;

import com.instaclone.auth.dto.AuthResult;
import com.instaclone.auth.dto.AuthTokensResponse;
import com.instaclone.auth.dto.ForgotPasswordRequest;
import com.instaclone.auth.dto.LoginRequest;
import com.instaclone.auth.dto.RegisterRequest;
import com.instaclone.auth.dto.ResetPasswordRequest;
import com.instaclone.auth.dto.UserSummaryResponse;
import com.instaclone.common.exception.ConflictException;
import com.instaclone.common.exception.UnauthorizedException;
import com.instaclone.config.properties.EmailProperties;
import com.instaclone.config.properties.JwtProperties;
import com.instaclone.email.service.EmailService;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final EmailService emailService;
    private final EmailProperties emailProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            RefreshTokenStore refreshTokenStore,
            PasswordResetTokenStore passwordResetTokenStore,
            EmailService emailService,
            EmailProperties emailProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordResetTokenStore = passwordResetTokenStore;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.save(user);

        return issueTokens(user);
    }

    public AuthResult login(LoginRequest request) {
        User user = userRepository
                .findByUsernameOrEmail(request.usernameOrEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user);
    }

    public AuthResult refresh(String rawRefreshToken) {
        Long userId = refreshTokenStore
                .consume(rawRefreshToken)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        return issueTokens(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenStore.revoke(rawRefreshToken);
    }

    /** Always behaves identically whether or not the email is registered — an error response here
     * would let a caller enumerate registered accounts one guess at a time. */
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = passwordResetTokenStore.issue(user.getId());
            String resetUrl = emailProperties.appBaseUrl() + "/reset-password?token=" + token;
            emailService.send(
                    user.getEmail(),
                    "Reset your Instaclone password",
                    "Someone requested a password reset for your Instaclone account.\n\n"
                            + "If this was you, reset your password here (this link expires in 1 hour):\n"
                            + resetUrl
                            + "\n\nIf you didn't request this, you can safely ignore this email.");
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Long userId = passwordResetTokenStore
                .consume(request.token())
                .orElseThrow(() -> new UnauthorizedException("This reset link is invalid or has expired"));
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        refreshTokenStore.revokeAll(userId);
    }

    private AuthResult issueTokens(User user) {
        String accessToken = issueAccessToken(user);
        String refreshToken = refreshTokenStore.issue(user.getId());
        AuthTokensResponse response = new AuthTokensResponse(
                accessToken,
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds(),
                UserSummaryResponse.from(user));
        return new AuthResult(response, refreshToken);
    }

    private String issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("insta-clone")
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }
}
