package com.qitracker.service;

import com.qitracker.domain.Project;
import com.qitracker.domain.ProcessArea;
import com.qitracker.domain.User;
import com.qitracker.dto.ProcessAreaDtos.ProcessAreaRequest;
import com.qitracker.dto.ProcessAreaDtos.ProcessAreaResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.ProcessAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProcessAreaService {

    private final ProcessAreaRepository processAreaRepository;
    private final ProjectService projectService;
    private final ProjectAccessService accessService;

    public ProcessAreaService(ProcessAreaRepository processAreaRepository, ProjectService projectService, ProjectAccessService accessService) {
        this.processAreaRepository = processAreaRepository;
        this.projectService = projectService;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<ProcessAreaResponse> list(Long projectId) {
        return processAreaRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProcessAreaResponse create(Long projectId, User requester, ProcessAreaRequest req) {
        Project project = projectService.getOrThrow(projectId);
        accessService.requireCreator(project, requester);
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Process area name is required.");
        ProcessArea area = ProcessArea.builder()
            .project(project)
            .name(req.name().trim())
            .description(req.description())
            .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
            .build();
        return toResponse(processAreaRepository.save(area));
    }

    @Transactional
    public ProcessAreaResponse update(Long id, User requester, ProcessAreaRequest req) {
        ProcessArea area = processAreaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Process area not found."));
        accessService.requireCreator(area.getProject(), requester);
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Process area name is required.");
        area.setName(req.name().trim());
        area.setDescription(req.description());
        if (req.sortOrder() != null) area.setSortOrder(req.sortOrder());
        return toResponse(processAreaRepository.save(area));
    }

    @Transactional
    public void delete(Long id, User requester) {
        ProcessArea area = processAreaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Process area not found."));
        accessService.requireCreator(area.getProject(), requester);
        processAreaRepository.delete(area); // indicators.process_area_id -> ON DELETE SET NULL, so indicators survive as ungrouped
    }

    private ProcessAreaResponse toResponse(ProcessArea a) {
        return new ProcessAreaResponse(a.getId(), a.getProject().getId(), a.getName(), a.getDescription(), a.getSortOrder());
    }
}
