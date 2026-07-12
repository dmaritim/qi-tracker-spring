package com.qitracker.dto;

public class ProcessAreaDtos {
    public record ProcessAreaRequest(String name, String description, Integer sortOrder) {}
    public record ProcessAreaResponse(Long id, Long projectId, String name, String description, int sortOrder) {}
}
