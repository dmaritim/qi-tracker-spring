package com.qitracker.controller;

import com.qitracker.dto.EntryDtos.EntryRequest;
import com.qitracker.dto.EntryDtos.EntryResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.EntryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class EntryController {

    private final EntryService entryService;

    public EntryController(EntryService entryService) {
        this.entryService = entryService;
    }

    @GetMapping("/api/indicators/{indicatorId}/entries")
    public List<EntryResponse> list(@PathVariable Long indicatorId) {
        return entryService.listForIndicator(indicatorId);
    }

    @PostMapping("/api/indicators/{indicatorId}/entries")
    public EntryResponse create(@PathVariable Long indicatorId, @RequestBody EntryRequest req, Authentication authentication) {
        return entryService.create(indicatorId, CurrentUser.get(authentication), req);
    }

    @PutMapping("/api/entries/{id}")
    public EntryResponse update(@PathVariable Long id, @RequestBody EntryRequest req, Authentication authentication) {
        return entryService.update(id, CurrentUser.get(authentication), req);
    }

    @DeleteMapping("/api/entries/{id}")
    public Map<String, Object> delete(@PathVariable Long id, Authentication authentication) {
        entryService.delete(id, CurrentUser.get(authentication));
        return Map.of("ok", true);
    }
}
