package com.qitracker.controller;

import com.qitracker.dto.AccountDtos.ChangePasswordRequest;
import com.qitracker.dto.AccountDtos.UpdateAccountRequest;
import com.qitracker.security.CurrentUser;
import com.qitracker.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody UpdateAccountRequest req, Authentication authentication) {
        return Map.of("user", accountService.updateAccount(CurrentUser.get(authentication), req));
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest req, Authentication authentication) {
        accountService.changePassword(CurrentUser.get(authentication), req);
        return Map.of("ok", true);
    }
}