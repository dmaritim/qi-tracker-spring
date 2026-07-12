package com.qitracker.controller;

import com.qitracker.dto.ProcessAreaDtos.ProcessAreaRequest;
import com.qitracker.dto.ProcessAreaDtos.ProcessAreaResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.ProcessAreaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/process-areas")
public class ProcessAreaController {

    private final ProcessAreaService processAreaService;

    public ProcessAreaController(ProcessAreaService processAreaService) {
        this.processAreaService = processAreaService;
    }

    @GetMapping
    public List<ProcessAreaResponse> list(@PathVariable Long projectId) {
        return processAreaService.list(projectId);
    }

    @PostMapping
    public ProcessAreaResponse create(@PathVariable Long projectId, @RequestBody ProcessAreaRequest req, Authentication authentication) {
        return processAreaService.create(projectId, CurrentUser.get(authentication), req);
    }

    @PutMapping("/{id}")
    public ProcessAreaResponse update(@PathVariable Long projectId, @PathVariable Long id,
                                       @RequestBody ProcessAreaRequest req, Authentication authentication) {
        return processAreaService.update(id, CurrentUser.get(authentication), req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long projectId, @PathVariable Long id, Authentication authentication) {
        processAreaService.delete(id, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }
}
