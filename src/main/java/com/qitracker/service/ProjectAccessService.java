package com.qitracker.service;

import com.qitracker.domain.Project;
import com.qitracker.domain.User;
import com.qitracker.domain.UserRole;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessService(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    public boolean isCreator(Project project, User user) {
        return user.getRole() == UserRole.ADMIN || project.getCreator().getId().equals(user.getId());
    }

    public boolean isMember(Project project, User user) {
        return isCreator(project, user) || projectMemberRepository.existsByProjectIdAndUserId(project.getId(), user.getId());
    }

    /** Project details, indicators, data elements, process areas, membership, deletion: creator (or admin) only. */
    public void requireCreator(Project project, User user) {
        if (!isCreator(project, user)) {
            throw ApiException.forbidden("Only the project creator can make this change.");
        }
    }

    /** Logging entries: assigned members and the creator. */
    public void requireMember(Project project, User user) {
        if (!isMember(project, user)) {
            throw ApiException.forbidden("You need to be assigned to this project to log entries for it.");
        }
    }

    // Viewing a project's dashboard is open to any signed-in user, so there is no check for that here.
}
