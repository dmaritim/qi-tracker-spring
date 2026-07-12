package com.qitracker.dto;

import java.time.Instant;
import java.util.List;

public class EntryDtos {

    public record EntryValueDto(Long elementId, double amount) {}

    public record EntryRequest(String date, List<EntryValueDto> values, String note) {}

    public record EntryResponse(
        Long id, Long indicatorId, String date, List<EntryValueDto> values,
        double value, String note, Long createdById, String createdByName, Instant createdAt
    ) {}
}
