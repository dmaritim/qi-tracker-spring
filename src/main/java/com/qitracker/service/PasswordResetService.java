package com.qitracker.service;

import com.qitracker.domain.PasswordResetToken;
import com.qitracker.domain.User;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.PasswordResetTokenRepository;
import com.qitracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.base-url}")
    private String baseUrl;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public void requestReset(String email) {
        // Always behave the same whether or not the account exists, so this endpoint can't be used to
        // find out which emails have accounts.
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email.toLowerCase().trim());
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken resetToken = PasswordResetToken.builder()
            .user(user)
            .token(token)
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .used(false)
            .build();
        tokenRepository.save(resetToken);

        String link = baseUrl + "/reset-password.html?token=" + token;
        String html = "<p>Hi " + escapeHtml(user.getName()) + ",</p>"
            + "<p>Someone requested a password reset for your QI Tracker account. If this was you, click below to choose a new password. This link expires in 1 hour.</p>"
            + "<p><a href=\"" + link + "\">Reset your password</a></p>"
            + "<p>If you didn't request this, you can ignore this email.</p>";
        emailService.sendHtml(user.getEmail(), "Reset your QI Tracker password", html);
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw ApiException.badRequest("Password must be at least 8 characters.");
        }
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
            .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired."));
        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("This reset link is invalid or has expired.");
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }
}
