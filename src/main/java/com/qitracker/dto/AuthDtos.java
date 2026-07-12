package com.qitracker.dto;

public class AuthDtos {
    public record RegisterRequest(String name, String email, String password, String signupCode) {}
    public record LoginRequest(String email, String password) {}
    public record ForgotPasswordRequest(String email) {}
    public record ResetPasswordRequest(String token, String newPassword) {}
    public record UserResponse(Long id, String name, String email, String role) {}
}
