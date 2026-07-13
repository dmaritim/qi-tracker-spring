package com.qitracker.dto;

import java.time.Instant;
import java.util.List;

public class PdsaDtos {

    public record PdsaCycleRequest(
        String title, Long processAreaId,
        String planText, String predictionText, String doText, String studyText,
        String actDecision, String actText,
        String startDate, String endDate,
        List<Long> indicatorIds
    ) {}

    public record PdsaCycleResponse(
        Long id, Long projectId, Long processAreaId, String processAreaName,
        String title, String planText, String predictionText, String doText, String studyText,
        String actDecision, String actText, String startDate, String endDate,
        List<Long> indicatorIds, List<String> indicatorNames,
        Long createdById, String createdByName, Instant createdAt
    ) {}

    /** Just enough to draw a vertical marker on an indicator's run chart — fetched as part of the
     *  dashboard response so the frontend doesn't need a second round trip. */
    public record PdsaMarker(Long id, String title, String startDate, List<Long> indicatorIds) {}
}