package com.qitracker.service;

import com.qitracker.domain.Entry;
import com.qitracker.domain.Indicator;
import com.qitracker.domain.OrgUnit;
import com.qitracker.domain.Project;
import com.qitracker.domain.ReportingFrequency;
import com.qitracker.domain.User;
import com.qitracker.dto.DashboardDtos.SummaryInfo;
import com.qitracker.dto.ProjectDtos.ProjectRequest;
import com.qitracker.dto.ProjectDtos.ProjectResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.EntryRepository;
import com.qitracker.repository.IndicatorRepository;
import com.qitracker.repository.OrgUnitRepository;
import com.qitracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    // Seeded by the V5 migration — the fallback every project used to point at before this
    // feature existed, and still the default if a project is created without picking one.
    private static final String DEFAULT_ORG_UNIT_UUID = "00000000-0000-0000-0000-000000000000";

    private final ProjectRepository projectRepository;
    private final IndicatorRepository indicatorRepository;
    private final EntryRepository entryRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final ProjectAccessService accessService;
    private final ProjectSummaryService summaryService;

    public ProjectService(ProjectRepository projectRepository, IndicatorRepository indicatorRepository,
                           EntryRepository entryRepository, OrgUnitRepository orgUnitRepository,
                           ProjectAccessService accessService, ProjectSummaryService summaryService) {
        this.projectRepository = projectRepository;
        this.indicatorRepository = indicatorRepository;
        this.entryRepository = entryRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.accessService = accessService;
        this.summaryService = summaryService;
    }

    public Project getOrThrow(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project not found."));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listAll(User requester) {
        List<Project> projects = projectRepository.findAllByOrderByCreatedAtDesc();
        List<Long> projectIds = projects.stream().map(Project::getId).toList();

        // Two batch queries for everyone, instead of one dashboard-style fetch per project.
        List<Indicator> allIndicators = projectIds.isEmpty() ? List.of() : indicatorRepository.findByProjectIdIn(projectIds);
        List<Entry> allEntries = projectIds.isEmpty() ? List.of() : entryRepository.findByIndicatorProjectIdIn(projectIds);

        Map<Long, List<Indicator>> indicatorsByProject = new HashMap<>();
        for (Indicator i : allIndicators) {
            indicatorsByProject.computeIfAbsent(i.getProject().getId(), k -> new ArrayList<>()).add(i);
        }
        Map<Long, List<Entry>> entriesByProject = new HashMap<>();
        for (Entry e : allEntries) {
            entriesByProject.computeIfAbsent(e.getIndicator().getProject().getId(), k -> new ArrayList<>()).add(e);
        }

        return projects.stream()
            .map(p -> {
                SummaryInfo summary = summaryService.compute(p,
                    indicatorsByProject.getOrDefault(p.getId(), List.of()),
                    entriesByProject.getOrDefault(p.getId(), List.of()));
                return toResponse(p, requester, summary);
            })
            .toList();
    }

    @Transactional
    public ProjectResponse create(User creator, ProjectRequest req) {
        validate(req);
        Project project = Project.builder()
            .name(req.name().trim())
            .objectives(nullToEmpty(req.objectives()))
            .startDate(parseDate(req.startDate()))
            .durationVal(req.durationVal())
            .durationUnit(req.durationUnit() == null ? "months" : req.durationUnit())
            .baseline(nullToEmpty(req.baseline()))
            .successDefinition(nullToEmpty(req.success()))
            .reportingFrequency(parseFrequency(req.reportingFrequency()))
            .creator(creator)
            .orgUnit(resolveOrgUnit(req.orgUnitUuid()))
            .build();
        project = projectRepository.save(project);
        return toResponse(project, creator);
    }

    @Transactional
    public ProjectResponse update(Long id, User requester, ProjectRequest req) {
        Project project = getOrThrow(id);
        accessService.requireCreator(project, requester);
        validate(req);
        project.setName(req.name().trim());
        project.setObjectives(nullToEmpty(req.objectives()));
        project.setStartDate(parseDate(req.startDate()));
        project.setDurationVal(req.durationVal());
        project.setDurationUnit(req.durationUnit() == null ? "months" : req.durationUnit());
        project.setBaseline(nullToEmpty(req.baseline()));
        project.setSuccessDefinition(nullToEmpty(req.success()));
        project.setReportingFrequency(parseFrequency(req.reportingFrequency()));
        project.setOrgUnit(resolveOrgUnit(req.orgUnitUuid()));
        project.setUpdatedAt(Instant.now());
        project = projectRepository.save(project);
        return toResponse(project, requester);
    }

    @Transactional
    public void delete(Long id, User requester) {
        Project project = getOrThrow(id);
        accessService.requireCreator(project, requester);
        try {
            projectRepository.delete(project);
            projectRepository.flush();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Someone (likely this same user double-clicking) already deleted it — treat as success.
        }
    }

    /** Convenience overload — used where a summary hasn't been computed already (create/update),
     *  or isn't worth computing (a project response nested inside a bigger payload that already
     *  carries its own summary, like the Dashboard view). Summary comes back null in that case. */
    public ProjectResponse toResponse(Project p, User requester) {
        return toResponse(p, requester, null);
    }

    public ProjectResponse toResponse(Project p, User requester, SummaryInfo summary) {
        boolean isCreator = accessService.isCreator(p, requester);
        boolean isMember = accessService.isMember(p, requester);
        return new ProjectResponse(
            p.getId(), p.getName(), p.getObjectives(),
            p.getStartDate() == null ? null : p.getStartDate().toString(),
            p.getDurationVal(), p.getDurationUnit(), p.getBaseline(), p.getSuccessDefinition(),
            p.getReportingFrequency().name(),
            p.getCreator().getId(), p.getCreator().getName(),
            isCreator, isMember,
            p.getLastReportSentAt(), p.getCreatedAt(), p.getUpdatedAt(),
            summary,
            p.getOrgUnit() == null ? null : p.getOrgUnit().getUuid(),
            p.getOrgUnit() == null ? null : p.getOrgUnit().getName()
        );
    }

    private void validate(ProjectRequest req) {
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Project name is required.");
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    private ReportingFrequency parseFrequency(String s) {
        if (s == null) return ReportingFrequency.weekly;
        try { return ReportingFrequency.valueOf(s.toLowerCase()); } catch (Exception e) { return ReportingFrequency.weekly; }
    }

    private OrgUnit resolveOrgUnit(String orgUnitUuid) {
        String uuid = (orgUnitUuid == null || orgUnitUuid.isBlank()) ? DEFAULT_ORG_UNIT_UUID : orgUnitUuid;
        return orgUnitRepository.findById(uuid)
            .orElseThrow(() -> ApiException.badRequest("Selected org unit doesn't exist."));
    }
}