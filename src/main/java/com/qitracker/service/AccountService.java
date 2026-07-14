package com.qitracker.service;

import com.qitracker.domain.User;
import com.qitracker.dto.AccountDtos.ChangePasswordRequest;
import com.qitracker.dto.AccountDtos.UpdateAccountRequest;
import com.qitracker.dto.AuthDtos.UserResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse updateAccount(User requester, UpdateAccountRequest req) {
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Name is required.");
        if (req.email() == null || req.email().isBlank()) throw ApiException.badRequest("Email is required.");

        String newEmail = req.email().toLowerCase().trim();
        if (!newEmail.equals(requester.getEmail()) && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw ApiException.conflict("Another account already uses that email.");
        }

        requester.setName(req.name().trim());
        requester.setEmail(newEmail);
        User saved = userRepository.save(requester);
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole().name());
    }

    @Transactional
    public void changePassword(User requester, ChangePasswordRequest req) {
        if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), requester.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect.");
        }
        if (req.newPassword() == null || req.newPassword().length() < 8) {
            throw ApiException.badRequest("New password must be at least 8 characters.");
        }
        requester.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(requester);
    }
}