package com.qitracker.controller;

import com.qitracker.dto.IndicatorDtos.IndicatorRequest;
import com.qitracker.dto.IndicatorDtos.IndicatorResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.IndicatorService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class IndicatorController {

    private final IndicatorService indicatorService;

    public IndicatorController(IndicatorService indicatorService) {
        this.indicatorService = indicatorService;
    }

    @GetMapping("/api/projects/{projectId}/indicators")
    public List<IndicatorResponse> list(@PathVariable Long projectId) {
        return indicatorService.listForProject(projectId);
    }

    @PostMapping("/api/projects/{projectId}/indicators")
    public IndicatorResponse create(@PathVariable Long projectId, @RequestBody IndicatorRequest req, Authentication authentication) {
        return indicatorService.create(projectId, CurrentUser.get(authentication), req);
    }

    @PutMapping("/api/indicators/{id}")
    public IndicatorResponse update(@PathVariable Long id, @RequestBody IndicatorRequest req, Authentication authentication) {
        return indicatorService.update(id, CurrentUser.get(authentication), req);
    }

    @DeleteMapping("/api/indicators/{id}")
    public Map<String, Object> delete(@PathVariable Long id, Authentication authentication) {
        indicatorService.delete(id, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }
}
