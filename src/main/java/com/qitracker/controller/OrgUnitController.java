package com.qitracker.controller;

import com.qitracker.dto.OrgUnitDtos.OrgUnitRequest;
import com.qitracker.dto.OrgUnitDtos.OrgUnitResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.OrgUnitService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org-units")
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    public OrgUnitController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @GetMapping
    public List<OrgUnitResponse> list() {
        return orgUnitService.listAll();
    }

    @PostMapping
    public OrgUnitResponse create(@RequestBody OrgUnitRequest req, Authentication authentication) {
        return orgUnitService.create(CurrentUser.get(authentication), req);
    }

    @PutMapping("/{uuid}")
    public OrgUnitResponse update(@PathVariable String uuid, @RequestBody OrgUnitRequest req, Authentication authentication) {
        return orgUnitService.update(uuid, CurrentUser.get(authentication), req);
    }

    @DeleteMapping("/{uuid}")
    public Map<String, Object> delete(@PathVariable String uuid, Authentication authentication) {
        orgUnitService.delete(uuid, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }
}