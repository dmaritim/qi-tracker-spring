package com.qitracker.controller;

import com.qitracker.domain.Project;
import com.qitracker.domain.User;
import com.qitracker.dto.ProjectDtos.ProjectRequest;
import com.qitracker.dto.ProjectDtos.ProjectResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.ProjectAccessService;
import com.qitracker.service.ProjectService;
import com.qitracker.service.ReportSchedulerService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectAccessService accessService;
    private final ReportSchedulerService reportSchedulerService;

    public ProjectController(ProjectService projectService, ProjectAccessService accessService,
                              ReportSchedulerService reportSchedulerService) {
        this.projectService = projectService;
        this.accessService = accessService;
        this.reportSchedulerService = reportSchedulerService;
    }

    @GetMapping
    public List<ProjectResponse> list(Authentication authentication) {
        return projectService.listAll(CurrentUser.get(authentication));
    }

    @PostMapping
    public ProjectResponse create(@RequestBody ProjectRequest req, Authentication authentication) {
        return projectService.create(CurrentUser.get(authentication), req);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @RequestBody ProjectRequest req, Authentication authentication) {
        return projectService.update(id, CurrentUser.get(authentication), req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, Authentication authentication) {
        projectService.delete(id, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }

    /** Lets the creator trigger the PDF-report email immediately instead of waiting for the schedule. */
    @PostMapping("/{id}/send-report")
    public Map<String, Object> sendReportNow(@PathVariable Long id, Authentication authentication) {
        User requester = CurrentUser.get(authentication);
        Project project = projectService.getOrThrow(id);
        accessService.requireCreator(project, requester);
        try {
            reportSchedulerService.sendReport(project.getId());
        } catch (Exception e) {
            throw ApiException.badRequest("Could not send the report: " + e.getMessage());
        }
        return Map.of("ok", true);
    }
}
