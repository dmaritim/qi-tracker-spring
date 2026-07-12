package com.qitracker.service;

import com.qitracker.domain.Project;
import com.qitracker.domain.ProjectMember;
import com.qitracker.domain.User;
import com.qitracker.dto.MemberDtos.MemberResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.ProjectMemberRepository;
import com.qitracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ProjectAccessService accessService;

    public MemberService(ProjectMemberRepository projectMemberRepository, UserRepository userRepository,
                          ProjectService projectService, ProjectAccessService accessService) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(Long projectId, User requester) {
        Project project = projectService.getOrThrow(projectId);
        accessService.requireCreator(project, requester); // membership list is a management view, creator-only
        List<MemberResponse> result = new ArrayList<>();
        result.add(new MemberResponse(null, project.getCreator().getId(), project.getCreator().getName(),
            project.getCreator().getEmail(), true, project.getCreatedAt()));
        for (ProjectMember pm : projectMemberRepository.findByProjectId(projectId)) {
            result.add(new MemberResponse(pm.getId(), pm.getUser().getId(), pm.getUser().getName(),
                pm.getUser().getEmail(), false, pm.getAddedAt()));
        }
        return result;
    }

    @Transactional
    public MemberResponse add(Long projectId, User requester, String email) {
        Project project = projectService.getOrThrow(projectId);
        accessService.requireCreator(project, requester);
        if (email == null || email.isBlank()) throw ApiException.badRequest("Email is required.");
        User user = userRepository.findByEmailIgnoreCase(email.toLowerCase().trim())
            .orElseThrow(() -> ApiException.notFound("No account with that email. They need to create an account first."));
        if (user.getId().equals(project.getCreator().getId())) {
            throw ApiException.badRequest("This person already has full access as the project creator.");
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw ApiException.conflict("That person is already assigned to this project.");
        }
        ProjectMember member = ProjectMember.builder().project(project).user(user).build();
        member = projectMemberRepository.save(member);
        return new MemberResponse(member.getId(), user.getId(), user.getName(), user.getEmail(), false, member.getAddedAt());
    }

    @Transactional
    public void remove(Long projectId, User requester, Long userId) {
        Project project = projectService.getOrThrow(projectId);
        accessService.requireCreator(project, requester);
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }
}
