package com.qitracker.controller;

import com.qitracker.dto.MemberDtos.AddMemberRequest;
import com.qitracker.dto.MemberDtos.MemberResponse;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberResponse> list(@PathVariable Long projectId, Authentication authentication) {
        return memberService.list(projectId, CurrentUser.get(authentication));
    }

    @PostMapping
    public MemberResponse add(@PathVariable Long projectId, @RequestBody AddMemberRequest req, Authentication authentication) {
        return memberService.add(projectId, CurrentUser.get(authentication), req.email());
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> remove(@PathVariable Long projectId, @PathVariable Long userId, Authentication authentication) {
        memberService.remove(projectId, CurrentUser.get(authentication), userId);
        return Map.of("ok", true);
    }
}
