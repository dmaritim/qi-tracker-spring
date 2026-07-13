package com.qitracker.service;

import com.qitracker.domain.*;
import com.qitracker.dto.PdsaDtos.PdsaCycleRequest;
import com.qitracker.dto.PdsaDtos.PdsaCycleResponse;
import com.qitracker.dto.PdsaDtos.PdsaMarker;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.IndicatorRepository;
import com.qitracker.repository.PdsaCycleIndicatorRepository;
import com.qitracker.repository.PdsaCycleRepository;
import com.qitracker.repository.ProcessAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdsaCycleService {

    private final PdsaCycleRepository cycleRepository;
    private final PdsaCycleIndicatorRepository cycleIndicatorRepository;
    private final IndicatorRepository indicatorRepository;
    private final ProcessAreaRepository processAreaRepository;
    private final ProjectService projectService;
    private final ProjectAccessService accessService;

    public PdsaCycleService(PdsaCycleRepository cycleRepository, PdsaCycleIndicatorRepository cycleIndicatorRepository,
                             IndicatorRepository indicatorRepository, ProcessAreaRepository processAreaRepository,
                             ProjectService projectService, ProjectAccessService accessService) {
        this.cycleRepository = cycleRepository;
        this.cycleIndicatorRepository = cycleIndicatorRepository;
        this.indicatorRepository = indicatorRepository;
        this.processAreaRepository = processAreaRepository;
        this.projectService = projectService;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<PdsaCycleResponse> listForProject(Long projectId) {
        return cycleRepository.findByProjectIdOrderByStartDateDesc(projectId).stream()
            .map(this::toResponse)
            .toList();
    }

    /** Lightweight markers for every cycle in a project, keyed for chart annotation — used by the
     *  Dashboard endpoint, not exposed as its own route. */
    @Transactional(readOnly = true)
    public List<PdsaMarker> markersForProject(Long projectId) {
        List<PdsaCycle> cycles = cycleRepository.findByProjectIdOrderByStartDateDesc(projectId);
        if (cycles.isEmpty()) return List.of();
        List<Long> cycleIds = cycles.stream().map(PdsaCycle::getId).toList();
        List<PdsaCycleIndicator> links = cycleIndicatorRepository.findByCycleIdIn(cycleIds);

        return cycles.stream().map(c -> {
            List<Long> indicatorIds = links.stream()
                .filter(l -> l.getCycle().getId().equals(c.getId()))
                .map(l -> l.getIndicator().getId())
                .toList();
            return new PdsaMarker(c.getId(), c.getTitle(), c.getStartDate().toString(), indicatorIds);
        }).toList();
    }

    @Transactional
    public PdsaCycleResponse create(Long projectId, User requester, PdsaCycleRequest req) {
        Project project = projectService.getOrThrow(projectId);
        accessService.requireCreator(project, requester);
        validate(req);

        PdsaCycle cycle = PdsaCycle.builder()
            .project(project)
            .processArea(resolveProcessArea(project, req.processAreaId()))
            .title(req.title().trim())
            .planText(req.planText())
            .predictionText(req.predictionText())
            .doText(req.doText())
            .studyText(req.studyText())
            .actDecision(parseDecision(req.actDecision()))
            .actText(req.actText())
            .startDate(parseDate(req.startDate()))
            .endDate(req.endDate() == null || req.endDate().isBlank() ? null : parseDate(req.endDate()))
            .createdBy(requester)
            .build();
        cycle = cycleRepository.save(cycle);

        linkIndicators(cycle, project, req.indicatorIds());
        return toResponse(cycleRepository.findById(cycle.getId()).orElseThrow());
    }

    @Transactional
    public PdsaCycleResponse update(Long id, User requester, PdsaCycleRequest req) {
        PdsaCycle cycle = getOrThrow(id);
        accessService.requireCreator(cycle.getProject(), requester);
        validate(req);

        cycle.setProcessArea(resolveProcessArea(cycle.getProject(), req.processAreaId()));
        cycle.setTitle(req.title().trim());
        cycle.setPlanText(req.planText());
        cycle.setPredictionText(req.predictionText());
        cycle.setDoText(req.doText());
        cycle.setStudyText(req.studyText());
        cycle.setActDecision(parseDecision(req.actDecision()));
        cycle.setActText(req.actText());
        cycle.setStartDate(parseDate(req.startDate()));
        cycle.setEndDate(req.endDate() == null || req.endDate().isBlank() ? null : parseDate(req.endDate()));
        cycleRepository.save(cycle);

        cycleIndicatorRepository.deleteByCycleId(cycle.getId());
        linkIndicators(cycle, cycle.getProject(), req.indicatorIds());
        return toResponse(cycleRepository.findById(cycle.getId()).orElseThrow());
    }

    @Transactional
    public void delete(Long id, User requester) {
        PdsaCycle cycle = getOrThrow(id);
        accessService.requireCreator(cycle.getProject(), requester);
        cycleRepository.delete(cycle);
    }

    private PdsaCycle getOrThrow(Long id) {
        return cycleRepository.findById(id).orElseThrow(() -> ApiException.notFound("PDSA cycle not found."));
    }

    private void linkIndicators(PdsaCycle cycle, Project project, List<Long> indicatorIds) {
        if (indicatorIds == null) return;
        List<PdsaCycleIndicator> links = new ArrayList<>();
        for (Long indicatorId : indicatorIds) {
            Indicator indicator = indicatorRepository.findById(indicatorId).orElse(null);
            if (indicator == null || !indicator.getProject().getId().equals(project.getId())) continue; // ignore anything not in this project
            links.add(PdsaCycleIndicator.builder().cycle(cycle).indicator(indicator).build());
        }
        cycleIndicatorRepository.saveAll(links);
    }

    private ProcessArea resolveProcessArea(Project project, Long processAreaId) {
        if (processAreaId == null) return null;
        ProcessArea area = processAreaRepository.findById(processAreaId).orElse(null);
        if (area == null || !area.getProject().getId().equals(project.getId())) return null;
        return area;
    }

    private void validate(PdsaCycleRequest req) {
        if (req.title() == null || req.title().isBlank()) throw ApiException.badRequest("Give the cycle a short title.");
        if (req.startDate() == null || req.startDate().isBlank()) throw ApiException.badRequest("A start date is required.");
        try { LocalDate.parse(req.startDate()); } catch (Exception e) { throw ApiException.badRequest("Invalid start date."); }
    }

    private LocalDate parseDate(String s) {
        try { return LocalDate.parse(s); } catch (Exception e) { throw ApiException.badRequest("Invalid date."); }
    }

    private PdsaActDecision parseDecision(String s) {
        if (s == null) return PdsaActDecision.in_progress;
        try { return PdsaActDecision.valueOf(s.toLowerCase()); } catch (Exception e) { return PdsaActDecision.in_progress; }
    }

    private PdsaCycleResponse toResponse(PdsaCycle c) {
        List<PdsaCycleIndicator> links = cycleIndicatorRepository.findByCycleId(c.getId());
        List<Long> indicatorIds = links.stream().map(l -> l.getIndicator().getId()).toList();
        List<String> indicatorNames = links.stream().map(l -> l.getIndicator().getName()).toList();

        return new PdsaCycleResponse(
            c.getId(), c.getProject().getId(),
            c.getProcessArea() == null ? null : c.getProcessArea().getId(),
            c.getProcessArea() == null ? null : c.getProcessArea().getName(),
            c.getTitle(), c.getPlanText(), c.getPredictionText(), c.getDoText(), c.getStudyText(),
            c.getActDecision().name(), c.getActText(),
            c.getStartDate().toString(), c.getEndDate() == null ? null : c.getEndDate().toString(),
            indicatorIds, indicatorNames,
            c.getCreatedBy() == null ? null : c.getCreatedBy().getId(),
            c.getCreatedBy() == null ? null : c.getCreatedBy().getName(),
            c.getCreatedAt()
        );
    }
}