package com.qitracker.service;

import com.qitracker.domain.User;
import com.qitracker.domain.UserRole;
import com.qitracker.dto.AuthDtos;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.signup-code}")
    private String signupCode;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(AuthDtos.RegisterRequest req) {
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Name is required.");
        if (req.email() == null || req.email().isBlank()) throw ApiException.badRequest("Email is required.");
        if (req.password() == null || req.password().length() < 8) throw ApiException.badRequest("Password must be at least 8 characters.");
        if (!signupCode.equals(req.signupCode())) throw ApiException.forbidden("Incorrect team signup code.");

        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with that email already exists.");
        }

        boolean isFirstUser = userRepository.countBy() == 0;
        User user = User.builder()
            .name(req.name().trim())
            .email(email)
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(isFirstUser ? UserRole.ADMIN : UserRole.MEMBER)
            .build();
        return userRepository.save(user);
    }

    public AuthDtos.UserResponse toResponse(User user) {
        return new AuthDtos.UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
