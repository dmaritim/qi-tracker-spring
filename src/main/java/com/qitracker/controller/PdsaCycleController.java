package com.qitracker.controller;

import com.qitracker.dto.PdsaDtos.PdsaCycleRequest;
import com.qitracker.dto.PdsaDtos.PdsaCycleResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.PdsaCycleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PdsaCycleController {

    private final PdsaCycleService pdsaCycleService;

    public PdsaCycleController(PdsaCycleService pdsaCycleService) {
        this.pdsaCycleService = pdsaCycleService;
    }

    @GetMapping("/api/projects/{projectId}/pdsa-cycles")
    public List<PdsaCycleResponse> list(@PathVariable Long projectId) {
        return pdsaCycleService.listForProject(projectId);
    }

    @PostMapping("/api/projects/{projectId}/pdsa-cycles")
    public PdsaCycleResponse create(@PathVariable Long projectId, @RequestBody PdsaCycleRequest req, Authentication authentication) {
        return pdsaCycleService.create(projectId, CurrentUser.get(authentication), req);
    }

    @PutMapping("/api/pdsa-cycles/{id}")
    public PdsaCycleResponse update(@PathVariable Long id, @RequestBody PdsaCycleRequest req, Authentication authentication) {
        return pdsaCycleService.update(id, CurrentUser.get(authentication), req);
    }

    @DeleteMapping("/api/pdsa-cycles/{id}")
    public Map<String, Object> delete(@PathVariable Long id, Authentication authentication) {
        pdsaCycleService.delete(id, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }
}