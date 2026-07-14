package com.qitracker.dto;

import java.time.Instant;

public class OrgUnitDtos {
    public record OrgUnitRequest(String name, String shortName, String code, String parentUuid, Integer level) {}

    public record OrgUnitResponse(
        String uuid, String name, String shortName, String code,
        String parentUuid, String parentName, Integer level, Instant createdAt
    ) {}
}