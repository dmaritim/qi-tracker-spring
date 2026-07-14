package com.qitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class ProjectDtos {

    public record ProjectRequest(
        String name, String objectives, String startDate, String durationVal, String durationUnit,
        String baseline, String success, String reportingFrequency, String orgUnitUuid
    ) {}

    public record ProjectResponse(
        Long id, String name, String objectives, String startDate, String durationVal, String durationUnit,
        String baseline, String success, String reportingFrequency,
        Long creatorId, String creatorName,
        @JsonProperty("isCreator") boolean isCreator,
        @JsonProperty("isMember") boolean isMember,
        Instant lastReportSentAt, Instant createdAt, Instant updatedAt,
        DashboardDtos.SummaryInfo summary,
        String orgUnitUuid, String orgUnitName
    ) {}
}