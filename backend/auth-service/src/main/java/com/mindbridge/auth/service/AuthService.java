package com.mindbridge.auth.service;

import com.mindbridge.auth.dto.AuthResponse;
import com.mindbridge.auth.dto.LoginRequest;
import com.mindbridge.auth.dto.RegisterRequest;
import com.mindbridge.auth.jwt.JwtProvider;
import com.mindbridge.core.entity.OtpCode;
import com.mindbridge.core.entity.RefreshToken;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.OtpCodeRepository;
import com.mindbridge.core.repository.RefreshTokenRepository;
import com.mindbridge.core.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

/**
 * Authentication service — handles register, login, anonymous auth,
 * token refresh with rotation, and logout.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OtpCodeRepository otpCodeRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            JavaMailSender mailSender
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.mailSender = mailSender;
    }

    /** Register a new user with email + password */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName()
        );
        user = userRepository.save(user);

        return generateAuthResponse(user);
    }

    /** Login with email + password */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.getIsActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        return generateAuthResponse(user);
    }

    /** Create anonymous user session — no credentials required */
    @Transactional
    public AuthResponse anonymousLogin() {
        User user = User.anonymous();
        user = userRepository.save(user);
        return generateAuthResponse(user);
    }

    /** Refresh access token using refresh token rotation */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (!storedToken.isUsable()) {
            // Token reuse detected or expired — revoke ALL tokens for this user (security)
            refreshTokenRepository.revokeAllByUserId(storedToken.getUser().getId());
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        // Rotate: revoke old, issue new
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return generateAuthResponse(storedToken.getUser());
    }

    /** Logout — revoke all refresh tokens for the user */
    @Transactional
    public void logout(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElse(null);

        if (storedToken != null) {
            refreshTokenRepository.revokeAllByUserId(storedToken.getUser().getId());
        }
    }

    /** Generate and send OTP for passwordless login */
    @Transactional
    public String sendOtp(String email) {
        // Delete any existing OTPs for this email to invalidate old ones
        otpCodeRepository.deleteByEmail(email);

        // Generate 6-digit code
        String code = String.format("%06d", new Random().nextInt(999999));
        
        OtpCode otp = new OtpCode(email, code, Instant.now().plus(5, ChronoUnit.MINUTES));
        otpCodeRepository.save(otp);

        // Send actual email via JavaMailSender
        try {
            System.out.println("LOG: Attempting to send real OTP email to " + email + " using " + mailFrom);
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom("Mindbridge <" + mailFrom + ">");
            } else {
                message.setFrom("Mindbridge <noreply@mindbridge.ai>");
            }
            message.setTo(email);
            message.setSubject("Your MindBridge AI Login Code");
            message.setText("Welcome to MindBridge AI.\n\nYour 6-digit login code is: " + code + 
                "\n\nThis code will expire in 5 minutes. If you did not request this, please ignore this email.");
            
            mailSender.send(message);
            System.out.println("LOG: Successfully sent real OTP email to " + email);
        } catch (Exception e) {
            System.err.println("LOG ERROR: Failed to send real email to " + email);
            e.printStackTrace(); // Reveal full stack trace in logs for debugging
        }

        return code;
    }

    /** Verify OTP and login (or register if user does not exist) */
    @Transactional
    public AuthResponse verifyOtp(String email, String code, String fullName) {
        OtpCode otp = otpCodeRepository.findByEmailAndCode(email, code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP code"));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otpCodeRepository.delete(otp);
            throw new IllegalArgumentException("OTP code has expired");
        }

        // OTP is valid. Remove it so it cannot be reused.
        otpCodeRepository.delete(otp);

        // Find existing user or create a new one (On-the-fly signup)
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // New user via OTP. Fallback to extracting name from email if no full name provided
            String nameToUse = (fullName != null && !fullName.isBlank()) 
                    ? fullName 
                    : email.split("@")[0];
            
            User newUser = new User(
                    email, 
                    passwordEncoder.encode(UUID.randomUUID().toString()), // random impossible password
                    nameToUse
            );
            return userRepository.save(newUser);
        });
        
        if (!user.getIsActive()) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        return generateAuthResponse(user);
    }

    /** Load user by ID (for JWT filter) */
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    // --- Private helpers ---

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole()
        );

        String refreshTokenValue = jwtProvider.generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken(
                user, refreshTokenValue, jwtProvider.getRefreshTokenExpiry()
        );
        refreshTokenRepository.save(refreshToken);

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getIsAnonymous()
        );

        return new AuthResponse(accessToken, refreshTokenValue, userInfo);
    }
}
