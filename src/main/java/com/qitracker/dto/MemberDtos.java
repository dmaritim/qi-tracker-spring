package com.qitracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class MemberDtos {
    public record AddMemberRequest(String email) {}
    public record MemberResponse(Long id, Long userId, String name, String email,
                                  @JsonProperty("isCreator") boolean isCreator, Instant addedAt) {}
}