package com.qitracker.service;

import com.qitracker.domain.Project;
import com.qitracker.domain.User;
import com.qitracker.domain.UserRole;
import com.qitracker.dto.AdminDtos.AdminStats;
import com.qitracker.dto.AdminDtos.AdminUserResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.EntryRepository;
import com.qitracker.repository.IndicatorRepository;
import com.qitracker.repository.ProjectRepository;
import com.qitracker.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final IndicatorRepository indicatorRepository;
    private final EntryRepository entryRepository;

    public AdminService(UserRepository userRepository, ProjectRepository projectRepository,
                         IndicatorRepository indicatorRepository, EntryRepository entryRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.indicatorRepository = indicatorRepository;
        this.entryRepository = entryRepository;
    }

    private void requireAdmin(User requester) {
        if (requester.getRole() != UserRole.ADMIN) throw ApiException.forbidden("Admins only.");
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(User requester) {
        requireAdmin(requester);
        List<User> users = userRepository.findAllByOrderByCreatedAtAsc();
        List<Project> projects = projectRepository.findAll();
        Map<Long, Long> projectCountByCreator = projects.stream()
            .collect(Collectors.groupingBy(p -> p.getCreator().getId(), Collectors.counting()));

        return users.stream()
            .map(u -> new AdminUserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(),
                u.getCreatedAt(), projectCountByCreator.getOrDefault(u.getId(), 0L)))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminStats stats(User requester) {
        requireAdmin(requester);
        return new AdminStats(
            userRepository.count(), projectRepository.count(),
            indicatorRepository.count(), entryRepository.count()
        );
    }

    @Transactional
    public AdminUserResponse updateRole(Long userId, User requester, String roleStr) {
        requireAdmin(requester);
        UserRole newRole;
        try { newRole = UserRole.valueOf(roleStr.toUpperCase()); }
        catch (Exception e) { throw ApiException.badRequest("Invalid role."); }

        User target = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));

        if (target.getId().equals(requester.getId()) && newRole != UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw ApiException.badRequest("You're the only admin — promote someone else before removing your own admin access.");
        }

        target.setRole(newRole);
        User saved = userRepository.save(target);
        return new AdminUserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole().name(), saved.getCreatedAt(), 0);
    }

    @Transactional
    public void deleteUser(Long userId, User requester) {
        requireAdmin(requester);
        if (userId.equals(requester.getId())) throw ApiException.badRequest("You can't delete your own account here.");
        User target = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        try {
            userRepository.delete(target);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(
                "Can't delete " + target.getName() + " — they've created projects, indicators, or entries. " +
                "Reassign or remove those first.");
        }
    }
}