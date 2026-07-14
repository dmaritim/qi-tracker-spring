package com.qitracker.controller;

import com.qitracker.dto.AdminDtos.AdminStats;
import com.qitracker.dto.AdminDtos.AdminUserResponse;
import com.qitracker.dto.AdminDtos.UpdateRoleRequest;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.AdminService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers(Authentication authentication) {
        return adminService.listUsers(CurrentUser.get(authentication));
    }

    @GetMapping("/stats")
    public AdminStats stats(Authentication authentication) {
        return adminService.stats(CurrentUser.get(authentication));
    }

    @PutMapping("/users/{id}/role")
    public AdminUserResponse updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequest req, Authentication authentication) {
        return adminService.updateRole(id, CurrentUser.get(authentication), req.role());
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id, Authentication authentication) {
        adminService.deleteUser(id, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }
}