package com.qitracker.service;

import com.qitracker.domain.*;
import com.qitracker.dto.IndicatorDtos.ElementRequest;
import com.qitracker.dto.IndicatorDtos.ElementResponse;
import com.qitracker.dto.IndicatorDtos.IndicatorRequest;
import com.qitracker.dto.IndicatorDtos.IndicatorResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.IndicatorElementRepository;
import com.qitracker.repository.IndicatorRepository;
import com.qitracker.repository.ProcessAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IndicatorService {

    private final IndicatorRepository indicatorRepository;
    private final IndicatorElementRepository elementRepository;
    private final ProcessAreaRepository processAreaRepository;
    private final ProjectService projectService;
    private final ProjectAccessService accessService;

    public IndicatorService(IndicatorRepository indicatorRepository, IndicatorElementRepository elementRepository,
                             ProcessAreaRepository processAreaRepository, ProjectService projectService,
                             ProjectAccessService accessService) {
        this.indicatorRepository = indicatorRepository;
        this.elementRepository = elementRepository;
        this.processAreaRepository = processAreaRepository;
        this.projectService = projectService;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<IndicatorResponse> listForProject(Long projectId) {
        return indicatorRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(this::toResponse).toList();
    }

    public Indicator getOrThrow(Long id) {
        return indicatorRepository.findById(id).orElseThrow(() -> ApiException.notFound("Indicator not found."));
    }

    @Transactional
    public IndicatorResponse create(Long projectId, User requester, IndicatorRequest req) {
        Project project = projectService.getOrThrow(projectId);
        accessService.requireCreator(project, requester);
        validate(req);

        ProcessArea area = resolveProcessArea(project, req.processAreaId());

        Indicator indicator = Indicator.builder()
            .project(project)
            .processArea(area)
            .name(req.name().trim())
            .optimalDescription(req.optimal())
            .multiplier(req.multiplier() == null ? 1.0 : req.multiplier())
            .targetValue(req.targetValue())
            .unit(req.unit())
            .direction(parseDirection(req.direction()))
            .createdBy(requester)
            .build();
        indicator = indicatorRepository.save(indicator);

        List<IndicatorElement> elements = new ArrayList<>();
        elements.addAll(buildElements(indicator, ElementSection.NUMERATOR, req.numeratorElements()));
        elements.addAll(buildElements(indicator, ElementSection.DENOMINATOR, req.denominatorElements()));
        elementRepository.saveAll(elements);

        return toResponse(indicatorRepository.findById(indicator.getId()).orElseThrow());
    }

    @Transactional
    public IndicatorResponse update(Long id, User requester, IndicatorRequest req) {
        Indicator indicator = getOrThrow(id);
        accessService.requireCreator(indicator.getProject(), requester);
        validate(req);

        indicator.setName(req.name().trim());
        indicator.setOptimalDescription(req.optimal());
        indicator.setMultiplier(req.multiplier() == null ? 1.0 : req.multiplier());
        indicator.setTargetValue(req.targetValue());
        indicator.setUnit(req.unit());
        indicator.setDirection(parseDirection(req.direction()));
        indicator.setProcessArea(resolveProcessArea(indicator.getProject(), req.processAreaId()));
        indicatorRepository.save(indicator);

        reconcileElements(indicator, ElementSection.NUMERATOR, req.numeratorElements());
        reconcileElements(indicator, ElementSection.DENOMINATOR, req.denominatorElements());

        return toResponse(indicatorRepository.findById(indicator.getId()).orElseThrow());
    }

    @Transactional
    public void delete(Long id, User requester) {
        Indicator indicator = getOrThrow(id);
        accessService.requireCreator(indicator.getProject(), requester);
        indicatorRepository.delete(indicator);
    }

    /** Reconciles by element id: keep+update rows whose id was sent back, delete rows that were dropped,
     *  insert rows with no id. This preserves historical entry_values for elements that were only renamed
     *  or had their sign flipped, and only loses history for elements the user actually removed. */
    private void reconcileElements(Indicator indicator, ElementSection section, List<ElementRequest> incoming) {
        List<IndicatorElement> existing = elementRepository.findByIndicatorId(indicator.getId()).stream()
            .filter(e -> e.getSection() == section).toList();

        Set<Long> incomingIds = incoming == null ? Set.of() : incoming.stream()
            .map(ElementRequest::id).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        List<IndicatorElement> toDelete = existing.stream().filter(e -> !incomingIds.contains(e.getId())).toList();
        elementRepository.deleteAll(toDelete);

        int order = 0;
        for (ElementRequest er : (incoming == null ? List.<ElementRequest>of() : incoming)) {
            if (er.name() == null || er.name().isBlank()) continue;
            if (er.id() != null) {
                IndicatorElement existingEl = existing.stream().filter(e -> e.getId().equals(er.id())).findFirst().orElse(null);
                if (existingEl != null) {
                    existingEl.setName(er.name().trim());
                    existingEl.setSign(parseSign(er.sign()));
                    existingEl.setSortOrder(order++);
                    elementRepository.save(existingEl);
                    continue;
                }
            }
            IndicatorElement created = IndicatorElement.builder()
                .indicator(indicator).section(section).name(er.name().trim())
                .sign(parseSign(er.sign())).sortOrder(order++).build();
            elementRepository.save(created);
        }
    }

    private List<IndicatorElement> buildElements(Indicator indicator, ElementSection section, List<ElementRequest> incoming) {
        List<IndicatorElement> result = new ArrayList<>();
        int order = 0;
        if (incoming != null) {
            for (ElementRequest er : incoming) {
                if (er.name() == null || er.name().isBlank()) continue;
                result.add(IndicatorElement.builder()
                    .indicator(indicator).section(section).name(er.name().trim())
                    .sign(parseSign(er.sign())).sortOrder(order++).build());
            }
        }
        return result;
    }

    private ProcessArea resolveProcessArea(Project project, Long processAreaId) {
        if (processAreaId == null) return null;
        ProcessArea area = processAreaRepository.findById(processAreaId).orElse(null);
        if (area == null || !area.getProject().getId().equals(project.getId())) return null;
        return area;
    }

    private void validate(IndicatorRequest req) {
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Indicator name is required.");
        if (req.numeratorElements() == null || req.numeratorElements().stream().noneMatch(e -> e.name() != null && !e.name().isBlank())) {
            throw ApiException.badRequest("At least one numerator data element is required.");
        }
    }

    private Direction parseDirection(String s) {
        if (s == null) return Direction.higher;
        try { return Direction.valueOf(s.toLowerCase()); } catch (Exception e) { return Direction.higher; }
    }

    private ElementSign parseSign(String s) {
        if (s == null) return ElementSign.ADD;
        try { return ElementSign.valueOf(s.toUpperCase()); } catch (Exception e) { return ElementSign.ADD; }
    }

    @Transactional(readOnly = true)
    public IndicatorResponse toResponse(Indicator i) {
        List<IndicatorElement> all = elementRepository.findByIndicatorId(i.getId());
        List<ElementResponse> num = all.stream().filter(e -> e.getSection() == ElementSection.NUMERATOR)
            .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
            .map(e -> new ElementResponse(e.getId(), e.getName(), e.getSign().name().toLowerCase())).toList();
        List<ElementResponse> den = all.stream().filter(e -> e.getSection() == ElementSection.DENOMINATOR)
            .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
            .map(e -> new ElementResponse(e.getId(), e.getName(), e.getSign().name().toLowerCase())).toList();

        return new IndicatorResponse(
            i.getId(), i.getProject().getId(),
            i.getProcessArea() == null ? null : i.getProcessArea().getId(),
            i.getProcessArea() == null ? null : i.getProcessArea().getName(),
            i.getName(), i.getOptimalDescription(), num, den,
            i.getMultiplier(), i.getTargetValue(), i.getUnit(), i.getDirection().name(),
            i.getCreatedBy() == null ? null : i.getCreatedBy().getId(),
            i.getCreatedBy() == null ? null : i.getCreatedBy().getName(),
            i.getCreatedAt()
        );
    }
}
