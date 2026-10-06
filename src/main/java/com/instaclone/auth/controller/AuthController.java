package com.instaclone.auth.controller;

import com.instaclone.auth.dto.AuthResult;
import com.instaclone.auth.dto.AuthTokensResponse;
import com.instaclone.auth.dto.ForgotPasswordRequest;
import com.instaclone.auth.dto.LoginRequest;
import com.instaclone.auth.dto.RegisterRequest;
import com.instaclone.auth.dto.ResetPasswordRequest;
import com.instaclone.auth.service.AuthService;
import com.instaclone.common.exception.UnauthorizedException;
import com.instaclone.config.properties.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

    // Referenced by AuthRateLimitFilter directly (same package) instead of being re-typed as
    // separate string literals there, so a renamed route fails that file to compile instead of
    // silently losing rate-limit protection.
    public static final String BASE_PATH = "/auth";
    public static final String REGISTER_PATH = "/register";
    public static final String LOGIN_PATH = "/login";
    public static final String FORGOT_PASSWORD_PATH = "/forgot-password";
    public static final String RESET_PASSWORD_PATH = "/reset-password";

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping(REGISTER_PATH)
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokensResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResult result = authService.register(request);
        setRefreshCookie(response, result.refreshToken());
        return result.tokens();
    }

    @PostMapping(LOGIN_PATH)
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.login(request);
        setRefreshCookie(response, result.refreshToken());
        return result.tokens();
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new UnauthorizedException("Missing refresh token");
        }
        AuthResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.refreshToken());
        return result.tokens();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearRefreshCookie(response);
    }

    @PostMapping(FORGOT_PASSWORD_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
    }

    @PostMapping(RESET_PASSWORD_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite(refreshCookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite(refreshCookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // Frontend (gram-insta-1.onrender.com) and backend (gram-insta.onrender.com) are different
    // hosts, so every call between them is cross-site: browsers never attach a SameSite=Lax
    // cookie to a cross-site fetch/XHR (only to a top-level navigation), so the refresh-token
    // cookie must be SameSite=None in that case. None is only honored by browsers when the
    // cookie is also Secure, so it's gated on the same flag that turns Secure on in production;
    // locally (http://localhost, cookieSecure=false) SameSite=None without Secure would just be
    // dropped, so Lax is correct there since frontend and backend share localhost.
    private String refreshCookieSameSite() {
        return jwtProperties.cookieSecure() ? "None" : "Lax";
    }
}
