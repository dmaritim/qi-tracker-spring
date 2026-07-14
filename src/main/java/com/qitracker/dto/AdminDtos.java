package com.qitracker.dto;

import java.time.Instant;

public class AdminDtos {
    public record AdminUserResponse(
        Long id, String name, String email, String role, Instant createdAt, long projectsCreated
    ) {}

    public record UpdateRoleRequest(String role) {}

    public record AdminStats(long totalUsers, long totalProjects, long totalIndicators, long totalEntries) {}
}