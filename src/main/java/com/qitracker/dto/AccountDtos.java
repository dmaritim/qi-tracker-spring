package com.qitracker.dto;

public class AccountDtos {
    public record UpdateAccountRequest(String name, String email) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}