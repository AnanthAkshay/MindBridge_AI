package com.mindbridge.auth.controller;

import com.mindbridge.auth.dto.AuthResponse;
import com.mindbridge.auth.dto.LoginRequest;
import com.mindbridge.auth.dto.OtpSendRequest;
import com.mindbridge.auth.dto.OtpVerifyRequest;
import com.mindbridge.auth.dto.RefreshRequest;
import com.mindbridge.auth.dto.RegisterRequest;
import com.mindbridge.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        setRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/anonymous")
    public ResponseEntity<AuthResponse> anonymousLogin(HttpServletResponse response) {
        AuthResponse authResponse = authService.anonymousLogin();
        setRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthResponse authResponse = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        authService.sendOtp(request.email());
        return ResponseEntity.ok(Map.of(
            "message", "OTP sent successfully to " + request.email() + ". Please check your email."
        ));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletResponse httpResponse) {
        AuthResponse authResponse = authService.verifyOtp(request.email(), request.code(), request.fullName());
        setRefreshTokenCookie(httpResponse, authResponse.refreshToken());
        return ResponseEntity.ok(authResponse);
    }

    // --- Cookie Helpers ---

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // set to true in production over HTTPS
        cookie.setPath("/api/auth"); // Restrict cookie to auth endpoints
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days in seconds
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }
}
