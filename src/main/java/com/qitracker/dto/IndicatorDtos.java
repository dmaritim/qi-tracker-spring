package com.qitracker.dto;

import java.time.Instant;
import java.util.List;

public class IndicatorDtos {

    /** id is null for a brand-new data element; present when editing an existing one so its
     *  historical entry values stay linked to it instead of being deleted and recreated. */
    public record ElementRequest(Long id, String name, String sign) {}
    public record ElementResponse(Long id, String name, String sign) {}

    public record IndicatorRequest(
        String name, String optimal, Long processAreaId,
        List<ElementRequest> numeratorElements, List<ElementRequest> denominatorElements,
        Double multiplier, Double targetValue, String unit, String direction
    ) {}

    public record IndicatorResponse(
        Long id, Long projectId, Long processAreaId, String processAreaName,
        String name, String optimal,
        List<ElementResponse> numeratorElements, List<ElementResponse> denominatorElements,
        double multiplier, Double targetValue, String unit, String direction,
        Long createdById, String createdByName, Instant createdAt
    ) {}
}
